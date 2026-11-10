package com.dispatch.webhook;

import com.dispatch.dto.DriverLocationUpdatedEvent;
import com.dispatch.dto.DriverMatchFoundEvent;
import com.dispatch.dto.EtaUpdatedEvent;
import com.dispatch.dto.SurgeUpdatedEvent;
import com.dispatch.dto.TripEventMessage;

import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

/**
 * Bridges Kafka domain events to STOMP topics for connected riders and drivers.
 */
@Component
public class NotificationListeners {

    private static final Logger log = LoggerFactory.getLogger(NotificationListeners.class);

    private final SimpMessagingTemplate messaging;
    private final StringRedisTemplate redis;

    public NotificationListeners(SimpMessagingTemplate messaging, StringRedisTemplate redis) {
        this.messaging = messaging;
        this.redis = redis;
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_MATCH_FOUND, groupId = "notification-service")
    public void onMatchFound(String message) {
        EventEnvelope<DriverMatchFoundEvent> env = EventCodec.decode(message, DriverMatchFoundEvent.class);
        DriverMatchFoundEvent offer = env.payload();

        redis.opsForValue().set("offer:trip:" + offer.offerId(), offer.tripId(), Duration.ofMinutes(2));
        redis.opsForValue().set("trip:rider:" + offer.tripId(), offer.riderId(), Duration.ofHours(2));

        messaging.convertAndSend("/topic/driver/" + offer.driverId() + "/ride-offer", offer);
        messaging.convertAndSend("/topic/rider/" + offer.riderId() + "/ride-status",
                Map.of("status", "DRIVER_ASSIGNED", "tripId", offer.tripId(),
                        "etaSeconds", offer.etaSeconds(), "driverId", offer.driverId()));
        log.info("Pushed ride offer {} to driver {}", offer.offerId(), offer.driverId());
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_LOCATION_UPDATED, groupId = "notification-service")
    public void onDriverLocation(String message) {
        EventEnvelope<DriverLocationUpdatedEvent> env = EventCodec.decode(message, DriverLocationUpdatedEvent.class);
        DriverLocationUpdatedEvent loc = env.payload();
        if (loc.currentTripId() == null || loc.currentTripId().isBlank()) {
            return;
        }
        String riderId = redis.opsForValue().get("trip:rider:" + loc.currentTripId());
        if (riderId != null) {
            messaging.convertAndSend("/topic/rider/" + riderId + "/driver-location",
                    Map.of("driverId", loc.driverId(), "lat", loc.lat(), "lng", loc.lng(),
                            "heading", loc.heading(), "speedKmh", loc.speedKmh()));
        }
    }

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "notification-service")
    public void onTripEvent(String message) {
        EventEnvelope<TripEventMessage> env = EventCodec.decode(message, TripEventMessage.class);
        TripEventMessage event = env.payload();
        String riderId = redis.opsForValue().get("trip:rider:" + event.tripId());

        Map<String, Object> payload = Map.of(
                "tripId", event.tripId(),
                "status", event.type().name(),
                "details", event.payload());
        if (riderId != null) {
            messaging.convertAndSend("/topic/rider/" + riderId + "/ride-status", payload);
        }
        Object driverId = event.payload().get("driverId");
        if (driverId != null) {
            messaging.convertAndSend("/topic/driver/" + driverId + "/trip-updates", payload);
        }
    }

    @KafkaListener(topics = KafkaTopics.TRIP_ETA_UPDATED, groupId = "notification-service")
    public void onEtaUpdated(String message) {
        EventEnvelope<EtaUpdatedEvent> env = EventCodec.decode(message, EtaUpdatedEvent.class);
        EtaUpdatedEvent eta = env.payload();
        if (eta.riderId() != null) {
            messaging.convertAndSend("/topic/rider/" + eta.riderId() + "/ride-status",
                    Map.of("status", "ETA_UPDATE", "tripId", eta.tripId(),
                            "etaSeconds", eta.etaSeconds(), "distanceMeters", eta.distanceMeters(),
                            "phase", eta.phase()));
        }
        messaging.convertAndSend("/topic/driver/" + eta.driverId() + "/trip-updates",
                Map.of("status", "ETA_UPDATE", "tripId", eta.tripId(), "etaSeconds", eta.etaSeconds()));
    }

    @KafkaListener(topics = KafkaTopics.SURGE_UPDATED, groupId = "notification-service")
    public void onSurge(String message) {
        EventEnvelope<SurgeUpdatedEvent> env = EventCodec.decode(message, SurgeUpdatedEvent.class);
        SurgeUpdatedEvent surge = env.payload();
        messaging.convertAndSend("/topic/city/" + surge.cityId() + "/surge",
                Map.of("geohash", surge.geohash(), "multiplier", surge.multiplier(),
                        "demand", surge.demandCount(), "supply", surge.supplyCount()));
    }
}
