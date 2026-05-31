package com.dispatch.dispatch.kafka;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Ensures Kafka consumers are idempotent: the first time an eventId is seen we
 * set {@code event:processed:{eventId}} with NX and a 24h TTL. Duplicates are
 * rejected, so reprocessing on rebalance/redelivery is a no-op.
 */
@Component
public class IdempotencyGuard {

    private static final Duration TTL = Duration.ofSeconds(86400);
    private final StringRedisTemplate redis;

    public IdempotencyGuard(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean firstTime(String eventId) {
        // Namespaced per consumer group: the same eventId is legitimately
        // processed by multiple services, so dedup must be scoped to this one.
        Boolean set = redis.opsForValue().setIfAbsent("event:processed:dispatch-service:" + eventId, "1", TTL);
        return Boolean.TRUE.equals(set);
    }
}
