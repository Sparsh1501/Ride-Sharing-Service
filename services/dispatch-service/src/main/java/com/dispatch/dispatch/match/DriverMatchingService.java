package com.dispatch.dispatch.match;

import com.dispatch.common.domain.DriverStatus;
import com.dispatch.common.domain.VehicleType;
import com.dispatch.common.matching.DriverCandidate;
import com.dispatch.common.matching.DriverScorer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.geo.*;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.DefaultStringRedisConnection;
import org.springframework.data.redis.connection.StringRedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.*;

/**
 * Geospatial + weighted driver matching:
 * <ol>
 *   <li>GEORADIUS nearest-first within the search radius</li>
 *   <li>batch fetch driver state hashes in a single pipelined round trip</li>
 *   <li>filter AVAILABLE + vehicle type, score the top 20 candidates</li>
 *   <li>return the top 3 ranked candidates</li>
 * </ol>
 */
@Service
public class DriverMatchingService {

    private static final Logger log = LoggerFactory.getLogger(DriverMatchingService.class);
    private static final String GEO_ACTIVE = "geo:drivers:active";
    private static final int MAX_CANDIDATES = 20;
    private static final int TOP_N = 3;

    private final StringRedisTemplate redis;
    private final DriverScorer scorer = new DriverScorer();

    public DriverMatchingService(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public List<RankedDriver> findCandidates(double lat, double lng, double radiusKm, VehicleType requested) {
        var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs()
                .includeDistance().includeCoordinates().sortAscending().limit(MAX_CANDIDATES * 2);
        var circle = new Circle(new Point(lng, lat), new Distance(radiusKm, Metrics.KILOMETERS));

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = redis.opsForGeo().radius(GEO_ACTIVE, circle, args);
        if (results == null || results.getContent().isEmpty()) {
            return List.of();
        }

        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> nearby = results.getContent();
        List<String> ids = nearby.stream().limit(MAX_CANDIDATES).map(r -> r.getContent().getName()).toList();
        Map<String, Map<String, String>> states = batchFetchState(ids);

        List<RankedDriver> ranked = new ArrayList<>();
        for (GeoResult<RedisGeoCommands.GeoLocation<String>> r : nearby) {
            if (ranked.size() >= MAX_CANDIDATES) {
                break;
            }
            String driverId = r.getContent().getName();
            Map<String, String> state = states.get(driverId);
            if (state == null || state.isEmpty()) {
                continue;
            }
            if (!DriverStatus.AVAILABLE.name().equals(state.get("status"))) {
                continue;
            }
            VehicleType driverVehicle = parseVehicle(state.get("vehicleType"));
            if (driverVehicle == null || requested.matchFactor(driverVehicle) <= 0.0) {
                continue;
            }

            double distanceKm = r.getDistance().getValue();
            Point p = r.getContent().getPoint();
            DriverCandidate candidate = new DriverCandidate(
                    driverId, p.getY(), p.getX(), distanceKm,
                    parseDouble(state.get("rating"), 4.5),
                    parseDouble(state.get("acceptanceRate"), 0.9),
                    driverVehicle);
            double score = scorer.score(candidate, requested, radiusKm);
            ranked.add(new RankedDriver(candidate, score));
        }

        ranked.sort(Comparator.comparingDouble(RankedDriver::score).reversed());
        log.info("Evaluated {} candidates within {}km", ranked.size(), radiusKm);
        return ranked.stream().limit(TOP_N).toList();
    }

    /** Atomically lock a driver to a ride. Returns true if the lock was acquired. */
    public boolean lockDriver(String driverId, String rideId) {
        Boolean acquired = redis.opsForValue()
                .setIfAbsent("driver:lock:" + driverId, rideId, Duration.ofSeconds(20));
        return Boolean.TRUE.equals(acquired);
    }

    public void unlockDriver(String driverId) {
        redis.delete("driver:lock:" + driverId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, String>> batchFetchState(List<String> ids) {
        List<Object> raw = redis.executePipelined((RedisCallback<Object>) connection -> {
            StringRedisConnection conn = new DefaultStringRedisConnection(connection);
            for (String id : ids) {
                conn.hGetAll("driver:state:" + id);
            }
            return null;
        });
        Map<String, Map<String, String>> states = new HashMap<>();
        for (int i = 0; i < ids.size() && i < raw.size(); i++) {
            Object value = raw.get(i);
            if (value instanceof Map<?, ?> map) {
                states.put(ids.get(i), (Map<String, String>) map);
            }
        }
        return states;
    }

    private VehicleType parseVehicle(String value) {
        try {
            return value == null ? null : VehicleType.valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private double parseDouble(String value, double fallback) {
        try {
            return value == null ? fallback : Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public record RankedDriver(DriverCandidate candidate, double score) {
    }
}
