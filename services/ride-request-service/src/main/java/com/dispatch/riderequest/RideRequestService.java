package com.dispatch.riderequest;

import com.dispatch.common.eta.EtaCalculator;
import com.dispatch.common.geo.GeoHash;
import com.dispatch.common.logging.LogKeys;
import com.dispatch.common.pricing.FareCalculator;
import com.dispatch.events.EventCodec;
import com.dispatch.events.EventEnvelope;
import com.dispatch.events.KafkaTopics;
import com.dispatch.events.payload.RideRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class RideRequestService {

    private static final Logger log = LoggerFactory.getLogger(RideRequestService.class);

    private final KafkaTemplate<String, String> kafka;
    private final SurgeClient surgeClient;
    private final EtaCalculator eta = new EtaCalculator();
    private final FareCalculator fares = new FareCalculator();

    public RideRequestService(KafkaTemplate<String, String> kafka, SurgeClient surgeClient) {
        this.kafka = kafka;
        this.surgeClient = surgeClient;
    }

    public RideQuote quote(RideRequest req) {
        var estimate = eta.estimate(req.pickupLat(), req.pickupLng(), req.dropLat(), req.dropLng(), LocalDateTime.now());
        double distanceKm = estimate.distanceMeters() / 1000.0;
        double etaMinutes = estimate.etaSeconds() / 60.0;
        double surge = surgeClient.currentMultiplier(req.pickupLat(), req.pickupLng());
        double fare = fares.finalFare(req.vehicleType(), distanceKm, etaMinutes, surge);
        String geohash = GeoHash.encode(req.pickupLat(), req.pickupLng(), 5);
        return new RideQuote(distanceKm, estimate.etaSeconds(), surge, fare, geohash);
    }

    public RideResponse requestRide(RideRequest req) {
        String tripId = "trip-" + UUID.randomUUID();
        LogKeys.withTrip(tripId);
        LogKeys.withRider(req.riderId());

        RideQuote quote = quote(req);
        RideRequestedEvent event = new RideRequestedEvent(
                tripId, req.riderId(), req.pickupLat(), req.pickupLng(),
                req.dropLat(), req.dropLng(), req.vehicleType(),
                req.cityId() == null ? "default" : req.cityId(),
                quote.estimatedFare(), quote.surgeMultiplier());

        EventEnvelope<RideRequestedEvent> envelope =
                EventEnvelope.of("ride.requested", "ride-request-service", tripId, event);
        kafka.send(KafkaTopics.RIDE_REQUESTED, tripId, EventCodec.encode(envelope));

        log.info("Ride requested fare={} surge={} eta={}s", quote.estimatedFare(), quote.surgeMultiplier(), quote.etaSeconds());
        LogKeys.clear();
        return new RideResponse(tripId, "SEARCHING", "Looking for nearby drivers", quote);
    }
}
