package com.dispatch.util;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ensures Kafka consumers are idempotent. The first time an event id is seen
 * for a consumer, {@code event:processed:{consumer}:{eventId}} is set with NX
 * and a 24h TTL. The same event id is still delivered to other consumers.
 */
@Component
public class IdempotencyGuard {

    private static final Duration TTL = Duration.ofSeconds(86400);
    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean firstTime(String consumer, String eventId) {
        Boolean set = redis.opsForValue()
                .setIfAbsent("event:processed:" + consumer + ":" + eventId, "1", TTL);
        return Boolean.TRUE.equals(set);
    }
}
