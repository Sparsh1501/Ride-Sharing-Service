package com.dispatch.webhook;

import com.dispatch.util.IdempotencyGuard;

import com.dispatch.util.JsonUtils;
import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.DriverMatchFoundEvent;
import com.dispatch.dto.DriverResponseEvent;
import com.dispatch.dto.RideRequestedEvent;
import com.dispatch.service.TripCommandService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Builds the trip event log from the dispatch choreography:
 * ride.requested -> TRIP_REQUESTED, the accepted offer -> DRIVER_ASSIGNED,
 * a rejection/timeout -> DRIVER_REJECTED.
 */
@Component
public class TripListeners {

    private static final Logger log = LoggerFactory.getLogger(TripListeners.class);

    private final TripCommandService commands;
    private final IdempotencyGuard idempotency;
    private final StringRedisTemplate redis;

    public TripListeners(TripCommandService commands, IdempotencyGuard idempotency, StringRedisTemplate redis) {
        this.commands = commands;
        this.idempotency = idempotency;
        this.redis = redis;
    }

    @KafkaListener(topics = KafkaTopics.RIDE_REQUESTED, groupId = "trip-service")
    public void onRideRequested(String message) {
        EventEnvelope<RideRequestedEvent> env = EventCodec.decode(message, RideRequestedEvent.class);
        if (!idempotency.firstTime("trip-service", env.eventId())) {
            return;
        }
        RideRequestedEvent r = env.payload();
        Map<String, Object> payload = new HashMap<>();
        payload.put("riderId", r.riderId());
        payload.put("pickupLat", r.pickupLat());
        payload.put("pickupLng", r.pickupLng());
        payload.put("dropLat", r.dropLat());
        payload.put("dropLng", r.dropLng());
        payload.put("vehicleType", r.vehicleType().name());
        payload.put("estimatedFare", r.estimatedFare());
        commands.requestRide(r.tripId(), payload);
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_MATCH_FOUND, groupId = "trip-service")
    public void onMatchFound(String message) {
        EventEnvelope<DriverMatchFoundEvent> env = EventCodec.decode(message, DriverMatchFoundEvent.class);
        if (!idempotency.firstTime("trip-service", env.eventId())) {
            return;
        }
        DriverMatchFoundEvent offer = env.payload();
        // cache the latest offer so we can record DRIVER_ASSIGNED when accepted
        redis.opsForValue().set(offerKey(offer.tripId(), offer.offerId()),
                JsonUtils.toJson(offer), Duration.ofMinutes(2));
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_RESPONSE, groupId = "trip-service")
    public void onDriverResponse(String message) {
        EventEnvelope<DriverResponseEvent> env = EventCodec.decode(message, DriverResponseEvent.class);
        if (!idempotency.firstTime("trip-service", env.eventId())) {
            return;
        }
        DriverResponseEvent resp = env.payload();
        if (resp.response() == DriverResponseEvent.Response.ACCEPT) {
            String cached = redis.opsForValue().get(offerKey(resp.tripId(), resp.offerId()));
            if (cached == null) {
                log.warn("No cached offer for accepted trip {} offer {}", resp.tripId(), resp.offerId());
                return;
            }
            DriverMatchFoundEvent offer = JsonUtils.fromJson(cached, DriverMatchFoundEvent.class);
            commands.assignDriver(resp.tripId(), offer.driverId(), offer.driverLat(), offer.driverLng(),
                    offer.etaSeconds(), offer.matchScore());
        } else if (resp.response() == DriverResponseEvent.Response.REJECT) {
            commands.driverRejected(resp.tripId(), resp.driverId(), null);
        }
    }

    private String offerKey(String tripId, String offerId) {
        return "trip:offer:" + tripId + ":" + offerId;
    }
}
