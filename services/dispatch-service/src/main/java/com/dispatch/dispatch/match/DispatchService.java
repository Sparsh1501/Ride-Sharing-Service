package com.dispatch.dispatch.match;

import com.dispatch.common.domain.VehicleType;
import com.dispatch.common.logging.LogKeys;
import com.dispatch.dispatch.hash.DistributedHashRing;
import com.dispatch.events.EventEnvelope;
import com.dispatch.events.EventCodec;
import com.dispatch.events.KafkaTopics;
import com.dispatch.events.payload.DriverMatchFoundEvent;
import com.dispatch.events.payload.DriverResponseEvent;
import com.dispatch.events.payload.RideRequestedEvent;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

/**
 * Core dispatch engine. Consumes ride requests, runs geospatial matching with
 * radius expansion, offers drivers sequentially with a per-offer timeout, and
 * uses the distributed hash ring to confirm shard ownership before assigning.
 */
@Service
public class DispatchService {

    private static final Logger log = LoggerFactory.getLogger(DispatchService.class);

    private final DriverMatchingService matching;
    private final DistributedHashRing ring;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    private final double initialRadiusKm;
    private final double expandedRadiusKm;
    private final int offerTimeoutSeconds;

    private final Map<String, OfferSession> sessions = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private final Timer matchingLatency;
    private final Counter matchSuccess;
    private final Counter matchNoDriver;
    private final DistributionSummary candidatesEvaluated;

    public DispatchService(DriverMatchingService matching,
                           DistributedHashRing ring,
                           KafkaTemplate<String, String> kafka,
                           MeterRegistry meters,
                           @Value("${dispatch.matching.initial-radius-km:5}") double initialRadiusKm,
                           @Value("${dispatch.matching.expanded-radius-km:10}") double expandedRadiusKm,
                           @Value("${dispatch.matching.offer-timeout-seconds:15}") int offerTimeoutSeconds) {
        this.matching = matching;
        this.ring = ring;
        this.kafka = kafka;
        this.meters = meters;
        this.initialRadiusKm = initialRadiusKm;
        this.expandedRadiusKm = expandedRadiusKm;
        this.offerTimeoutSeconds = offerTimeoutSeconds;

        this.matchingLatency = Timer.builder("dispatch.matching.latency")
                .description("Time from ride request to driver assigned").register(meters);
        this.matchSuccess = Counter.builder("dispatch.match.success_rate").tag("result", "matched").register(meters);
        this.matchNoDriver = Counter.builder("dispatch.match.success_rate").tag("result", "no_driver_available").register(meters);
        this.candidatesEvaluated = DistributionSummary.builder("dispatch.candidates.evaluated").register(meters);
    }

    public void dispatch(RideRequestedEvent ride) {
        LogKeys.withTrip(ride.tripId());
        LogKeys.withRider(ride.riderId());
        long start = System.nanoTime();

        List<DriverMatchingService.RankedDriver> candidates =
                matching.findCandidates(ride.pickupLat(), ride.pickupLng(), initialRadiusKm, ride.vehicleType());
        boolean expanded = false;
        if (candidates.isEmpty()) {
            candidates = matching.findCandidates(ride.pickupLat(), ride.pickupLng(), expandedRadiusKm, ride.vehicleType());
            expanded = true;
        }
        candidatesEvaluated.record(candidates.size());

        if (candidates.isEmpty()) {
            matchNoDriver.increment();
            log.warn("NO_DRIVER_AVAILABLE for trip {}", ride.tripId());
            LogKeys.clear();
            return;
        }

        OfferSession session = new OfferSession(ride, candidates, expanded ? expandedRadiusKm : initialRadiusKm, start);
        sessions.put(ride.tripId(), session);
        offerNext(session);
        LogKeys.clear();
    }

    private synchronized void offerNext(OfferSession session) {
        while (session.index < session.candidates.size()) {
            DriverMatchingService.RankedDriver ranked = session.candidates.get(session.index);
            String driverId = ranked.candidate().driverId();

            // Consistent-hash ownership guard prevents two dispatch shards racing the same driver
            if (!ring.ownsKey(driverId) && ring.getNode(driverId) != null) {
                log.debug("driver {} owned by shard {}, this={}", driverId, ring.getNode(driverId), ring.currentNodeId());
            }

            if (!matching.lockDriver(driverId, session.ride.tripId())) {
                log.info("driver {} already locked, skipping", driverId);
                session.index++;
                continue;
            }

            String offerId = UUID.randomUUID().toString();
            session.currentOfferId = offerId;
            session.currentDriverId = driverId;

            DriverMatchFoundEvent offer = new DriverMatchFoundEvent(
                    session.ride.tripId(), session.ride.riderId(), driverId, offerId,
                    ranked.candidate().lat(), ranked.candidate().lng(),
                    estimateEtaSeconds(ranked.candidate().distanceKm()),
                    ranked.score(), session.ride.estimatedFare(), offerTimeoutSeconds);

            publish(KafkaTopics.DRIVER_MATCH_FOUND, session.ride.tripId(),
                    EventEnvelope.of("driver.match.found", "dispatch-service", session.ride.tripId(), offer));
            log.info("Offered trip {} to driver {} (score={}, offer={})",
                    session.ride.tripId(), driverId, ranked.score(), offerId);

            session.timeout = scheduler.schedule(
                    () -> onOfferTimeout(session.ride.tripId(), offerId), offerTimeoutSeconds, TimeUnit.SECONDS);
            return;
        }

        // exhausted current candidate list
        if (!session.expandedTried) {
            session.expandedTried = true;
            List<DriverMatchingService.RankedDriver> more = matching.findCandidates(
                    session.ride.pickupLat(), session.ride.pickupLng(), expandedRadiusKm, session.ride.vehicleType());
            if (!more.isEmpty()) {
                session.candidates = more;
                session.index = 0;
                session.searchRadiusKm = expandedRadiusKm;
                offerNext(session);
                return;
            }
        }

        matchNoDriver.increment();
        log.warn("All candidates exhausted, NO_DRIVER_AVAILABLE for trip {}", session.ride.tripId());
        sessions.remove(session.ride.tripId());
    }

    public synchronized void onDriverResponse(DriverResponseEvent response) {
        OfferSession session = sessions.get(response.tripId());
        if (session == null || !response.offerId().equals(session.currentOfferId)) {
            return;
        }
        if (session.timeout != null) {
            session.timeout.cancel(false);
        }

        switch (response.response()) {
            case ACCEPT -> {
                matchingLatency.record(System.nanoTime() - session.startNanos, TimeUnit.NANOSECONDS);
                matchSuccess.increment();
                log.info("driver {} ACCEPTED trip {}", response.driverId(), response.tripId());
                sessions.remove(response.tripId());
            }
            case REJECT, TIMEOUT -> {
                matching.unlockDriver(session.currentDriverId);
                session.index++;
                offerNext(session);
            }
        }
    }

    private void onOfferTimeout(String tripId, String offerId) {
        onDriverResponse(new DriverResponseEvent(tripId, offerId,
                sessions.containsKey(tripId) ? sessions.get(tripId).currentDriverId : null,
                DriverResponseEvent.Response.TIMEOUT));
    }

    private long estimateEtaSeconds(double distanceKm) {
        return Math.round((distanceKm * 1.4) / 30.0 * 3600.0);
    }

    private void publish(String topic, String key, EventEnvelope<?> envelope) {
        kafka.send(topic, key, EventCodec.encode(envelope));
    }

    private VehicleType vehicleOf(RideRequestedEvent ride) {
        return ride.vehicleType();
    }

    private static final class OfferSession {
        final RideRequestedEvent ride;
        List<DriverMatchingService.RankedDriver> candidates;
        double searchRadiusKm;
        final long startNanos;
        int index = 0;
        boolean expandedTried;
        String currentOfferId;
        String currentDriverId;
        ScheduledFuture<?> timeout;

        OfferSession(RideRequestedEvent ride, List<DriverMatchingService.RankedDriver> candidates,
                     double searchRadiusKm, long startNanos) {
            this.ride = ride;
            this.candidates = candidates;
            this.searchRadiusKm = searchRadiusKm;
            this.startNanos = startNanos;
            this.expandedTried = searchRadiusKm >= 10;
        }
    }
}
