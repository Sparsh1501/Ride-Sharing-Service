package com.dispatch.trip.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class IdempotencyGuard {

    private static final Duration TTL = Duration.ofSeconds(86400);
    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean firstTime(String eventId) {
        // Namespaced per consumer group (the same eventId is processed by
        // several services), so dedup is scoped to trip-service only.
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent("event:processed:trip-service:" + eventId, "1", TTL));
    }
}
