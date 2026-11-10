package com.dispatch.service;

import com.dispatch.entity.DriverStatus;
import com.dispatch.util.Haversine;
import com.dispatch.util.LogKeys;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.DriverLocationUpdatedEvent;
import com.dispatch.dto.LocationUpdateRequest;
import com.dispatch.util.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Ingests driver location pings (~every 4s), maintains Redis GEO indexes and
 * driver state/location hashes, refreshes a 30s heartbeat TTL, keeps the last
 * 10 location points for heading/speed derivation, and publishes
 * {@code driver.location.updated} to Kafka.
 */
@Service
public class DriverLocationService {

    private static final Logger log = LoggerFactory.getLogger(DriverLocationService.class);

    public static final String GEO_ACTIVE = "geo:drivers:active";
    private static final Duration HEARTBEAT_TTL = Duration.ofSeconds(30);
    private static final int HISTORY_SIZE = 10;

    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;

    public DriverLocationService(StringRedisTemplate redis, KafkaTemplate<String, String> kafka) {
        this.redis = redis;
        this.kafka = kafka;
    }

    public DriverLocationUpdatedEvent updateLocation(String driverId, LocationUpdateRequest req) {
        LogKeys.withDriver(driverId);
        String cityId = req.cityId() == null ? "default" : req.cityId();

        Point point = new Point(req.lng(), req.lat());
        redis.opsForGeo().add(GEO_ACTIVE, point, driverId);
        redis.opsForGeo().add(geoCityKey(cityId), point, driverId);

        double[] derived = deriveHeadingAndSpeed(driverId, req);
        double heading = req.heading() != null ? req.heading() : derived[0];
        double speed = req.speedKmh() != null ? req.speedKmh() : derived[1];

        redis.opsForHash().putAll(locationKey(driverId), Map.of(
                "lat", String.valueOf(req.lat()),
                "lng", String.valueOf(req.lng()),
                "heading", String.valueOf(heading),
                "speed", String.valueOf(speed),
                "lastUpdated", Instant.now().toString()));

        pushHistory(driverId, req.lat(), req.lng());
        redis.opsForValue().set(heartbeatKey(driverId), "1", HEARTBEAT_TTL);

        if (Boolean.TRUE.equals(redis.hasKey(stateKey(driverId)))) {
            redis.opsForHash().put(stateKey(driverId), "lastSeen", Instant.now().toString());
        }

        DriverLocationUpdatedEvent event = new DriverLocationUpdatedEvent(
                driverId, cityId, req.lat(), req.lng(), heading, speed, req.currentTripId(), Instant.now());
        publish(event);
        log.info("driver location updated lat={} lng={} speed={}", req.lat(), req.lng(), speed);
        LogKeys.clear();
        return event;
    }

    public void registerDriver(String driverId, String vehicleType, double rating, String cityId) {
        redis.opsForHash().putAll(stateKey(driverId), Map.of(
                "status", DriverStatus.AVAILABLE.name(),
                "vehicleType", vehicleType,
                "rating", String.valueOf(rating),
                "cityId", cityId == null ? "default" : cityId,
                "currentRideId", ""));
        redis.opsForValue().set(heartbeatKey(driverId), "1", HEARTBEAT_TTL);
    }

    public void setStatus(String driverId, DriverStatus status) {
        redis.opsForHash().put(stateKey(driverId), "status", status.name());
        if (status == DriverStatus.OFFLINE) {
            redis.opsForGeo().remove(GEO_ACTIVE, driverId);
        }
    }

    public Map<Object, Object> getState(String driverId) {
        return redis.opsForHash().entries(stateKey(driverId));
    }

    public Map<Object, Object> getLocation(String driverId) {
        return redis.opsForHash().entries(locationKey(driverId));
    }

    /** Search nearby active drivers using GEORADIUS, nearest-first. */
    public List<RedisGeoCommands.GeoLocation<String>> nearby(double lat, double lng, double radiusKm) {
        var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending();
        var circle = new org.springframework.data.geo.Circle(
                new Point(lng, lat),
                new org.springframework.data.geo.Distance(radiusKm, org.springframework.data.geo.Metrics.KILOMETERS));
        var results = redis.opsForGeo().radius(GEO_ACTIVE, circle, args);
        return results == null ? List.of() : results.getContent().stream().map(c -> c.getContent()).toList();
    }

    private double[] deriveHeadingAndSpeed(String driverId, LocationUpdateRequest req) {
        List<String> history = redis.opsForList().range(historyKey(driverId), 0, 0);
        if (history == null || history.isEmpty()) {
            return new double[]{0.0, 0.0};
        }
        String[] last = history.get(0).split(",");
        double prevLat = Double.parseDouble(last[0]);
        double prevLng = Double.parseDouble(last[1]);
        long prevTs = Long.parseLong(last[2]);

        double distanceKm = Haversine.distanceKm(prevLat, prevLng, req.lat(), req.lng());
        double hours = Math.max((System.currentTimeMillis() - prevTs) / 3_600_000.0, 1e-6);
        double speed = distanceKm / hours;
        double heading = bearing(prevLat, prevLng, req.lat(), req.lng());
        return new double[]{heading, speed};
    }

    private void pushHistory(String driverId, double lat, double lng) {
        String entry = lat + "," + lng + "," + System.currentTimeMillis();
        redis.opsForList().leftPush(historyKey(driverId), entry);
        redis.opsForList().trim(historyKey(driverId), 0, HISTORY_SIZE - 1);
    }

    private double bearing(double lat1, double lng1, double lat2, double lng2) {
        double dLng = Math.toRadians(lng2 - lng1);
        double y = Math.sin(dLng) * Math.cos(Math.toRadians(lat2));
        double x = Math.cos(Math.toRadians(lat1)) * Math.sin(Math.toRadians(lat2))
                - Math.sin(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.cos(dLng);
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360;
    }

    private void publish(DriverLocationUpdatedEvent event) {
        EventEnvelope<DriverLocationUpdatedEvent> envelope =
                EventEnvelope.of("driver.location.updated", "driver-location-service", null, event);
        kafka.send(KafkaTopics.DRIVER_LOCATION_UPDATED, event.driverId(), JsonUtils.toJson(envelope));
    }

    static String geoCityKey(String cityId) {
        return "geo:drivers:city:" + cityId;
    }

    static String stateKey(String driverId) {
        return "driver:state:" + driverId;
    }

    static String locationKey(String driverId) {
        return "driver:location:" + driverId;
    }

    static String heartbeatKey(String driverId) {
        return "driver:heartbeat:" + driverId;
    }

    static String historyKey(String driverId) {
        return "driver:locationhistory:" + driverId;
    }
}
