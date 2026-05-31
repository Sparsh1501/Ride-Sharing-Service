package com.dispatch.trip.eta;

import com.dispatch.common.eta.EtaCalculator;
import com.dispatch.common.trip.TripState;
import com.dispatch.events.EventCodec;
import com.dispatch.events.EventEnvelope;
import com.dispatch.events.KafkaTopics;
import com.dispatch.events.payload.EtaUpdatedEvent;
import com.dispatch.trip.command.TripCommandService;
import com.dispatch.trip.store.TripEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;

/**
 * Recalculates ETA for every active trip on a fixed interval and publishes
 * {@code trip.eta.updated} so the notification service can stream updates.
 */
@Component
public class EtaRecalculator {

    private static final Logger log = LoggerFactory.getLogger(EtaRecalculator.class);

    private final StringRedisTemplate redis;
    private final EtaCalculationService etaService;
    private final TripEventStore store;
    private final KafkaTemplate<String, String> kafka;

    public EtaRecalculator(StringRedisTemplate redis, EtaCalculationService etaService,
                           TripEventStore store, KafkaTemplate<String, String> kafka) {
        this.redis = redis;
        this.etaService = etaService;
        this.store = store;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${eta.recalculation-interval-seconds:30}000")
    public void recalculate() {
        Set<String> active = redis.opsForSet().members(TripCommandService.ACTIVE_TRIPS);
        if (active == null || active.isEmpty()) {
            return;
        }
        for (String tripId : active) {
            try {
                recalculateOne(tripId);
            } catch (Exception e) {
                log.warn("ETA recalculation failed for {}: {}", tripId, e.getMessage());
            }
        }
    }

    private void recalculateOne(String tripId) {
        TripState state = store.rehydrate(tripId);
        if (state.driverId() == null) {
            return;
        }
        Map<Object, Object> loc = redis.<Object, Object>opsForHash().entries("driver:location:" + state.driverId());
        if (loc.isEmpty()) {
            return;
        }
        double driverLat = Double.parseDouble(loc.get("lat").toString());
        double driverLng = Double.parseDouble(loc.get("lng").toString());

        boolean enRoute = switch (state.status()) {
            case TRIP_STARTED -> false;
            default -> true;
        };
        double targetLat = enRoute ? state.pickupLat() : state.dropLat();
        double targetLng = enRoute ? state.pickupLng() : state.dropLng();

        EtaCalculator.EtaResult result = etaService.estimate(driverLat, driverLng, targetLat, targetLng);
        EtaUpdatedEvent event = new EtaUpdatedEvent(tripId, state.driverId(), state.riderId(),
                result.etaSeconds(), result.distanceMeters(), enRoute ? "TO_PICKUP" : "TO_DROP");
        kafka.send(KafkaTopics.TRIP_ETA_UPDATED, tripId,
                EventCodec.encode(EventEnvelope.of("trip.eta.updated", "trip-service", tripId, event)));
    }
}
