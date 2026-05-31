package com.dispatch.surge;

import com.dispatch.common.geo.GeoHash;
import com.dispatch.common.surge.SurgeCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Real-time surge engine. Demand and supply per geohash zone are tracked in
 * Redis sorted sets keyed by event timestamp, giving a 5-minute sliding window.
 */
@Service
public class SurgePricingService {

    private static final Logger log = LoggerFactory.getLogger(SurgePricingService.class);
    private static final long WINDOW_MS = 5 * 60 * 1000L;

    private final StringRedisTemplate redis;
    private final SurgeCalculator calculator;
    private final int precision;

    public SurgePricingService(StringRedisTemplate redis,
                               @Value("${surge.geohash-precision:5}") int precision,
                               @Value("${surge.max-multiplier:3.0}") double maxMultiplier) {
        this.redis = redis;
        this.precision = precision;
        this.calculator = new SurgeCalculator(maxMultiplier);
    }

    public String zoneFor(double lat, double lng) {
        return GeoHash.encode(lat, lng, precision);
    }

    public static final String ZONES_KEY = "surge:zones";

    public void recordRequest(double lat, double lng) {
        String zone = zoneFor(lat, lng);
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(requestsKey(zone), UUID.randomUUID().toString(), now);
        redis.opsForSet().add(ZONES_KEY, zone);
        prune(requestsKey(zone), now);
    }

    public void recordDriver(String driverId, double lat, double lng) {
        String zone = zoneFor(lat, lng);
        long now = System.currentTimeMillis();
        redis.opsForZSet().add(driversKey(zone), driverId, now);
        redis.opsForSet().add(ZONES_KEY, zone);
        prune(driversKey(zone), now);
    }

    public long demand(String zone) {
        prune(requestsKey(zone), System.currentTimeMillis());
        Long count = redis.opsForZSet().zCard(requestsKey(zone));
        return count == null ? 0 : count;
    }

    public long supply(String zone) {
        prune(driversKey(zone), System.currentTimeMillis());
        Long count = redis.opsForZSet().zCard(driversKey(zone));
        return count == null ? 0 : count;
    }

    public double currentMultiplier(String zone) {
        String cached = redis.opsForValue().get(multiplierKey(zone));
        return cached != null ? Double.parseDouble(cached) : 1.0;
    }

    public double compute(String zone) {
        return calculator.multiplier(demand(zone), supply(zone));
    }

    private void prune(String key, long now) {
        redis.opsForZSet().removeRangeByScore(key, 0, now - WINDOW_MS);
    }

    static String requestsKey(String zone) {
        return "surge:zone:" + zone + ":requests";
    }

    static String driversKey(String zone) {
        return "surge:zone:" + zone + ":drivers";
    }

    static String multiplierKey(String zone) {
        return "surge:multiplier:" + zone;
    }
}
