package com.dispatch.query.projection;

import com.dispatch.events.EventCodec;
import com.dispatch.events.EventEnvelope;
import com.dispatch.events.KafkaTopics;
import com.dispatch.events.payload.SurgeUpdatedEvent;
import com.dispatch.events.payload.TripEventMessage;
import com.dispatch.query.model.SurgeHistory;
import com.dispatch.query.model.SurgeHistoryRepository;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class QueryListeners {

    private final TripProjection projection;
    private final SurgeHistoryRepository surgeHistory;
    private final StringRedisTemplate redis;

    public QueryListeners(TripProjection projection, SurgeHistoryRepository surgeHistory, StringRedisTemplate redis) {
        this.projection = projection;
        this.surgeHistory = surgeHistory;
        this.redis = redis;
    }

    @KafkaListener(topics = KafkaTopics.TRIP_EVENTS, groupId = "query-service")
    public void onTripEvent(String message) {
        EventEnvelope<TripEventMessage> env = EventCodec.decode(message, TripEventMessage.class);
        if (!firstTime(env.eventId())) {
            return;
        }
        projection.apply(env.payload());
    }

    @KafkaListener(topics = KafkaTopics.SURGE_UPDATED, groupId = "query-service")
    public void onSurge(String message) {
        EventEnvelope<SurgeUpdatedEvent> env = EventCodec.decode(message, SurgeUpdatedEvent.class);
        if (!firstTime(env.eventId())) {
            return;
        }
        SurgeUpdatedEvent s = env.payload();
        surgeHistory.save(new SurgeHistory(s.geohash(), s.multiplier(), s.demandCount(), s.supplyCount(), s.recordedAt()));
    }

    private boolean firstTime(String eventId) {
        // Namespaced per consumer group so it does not collide with other
        // services that consume the same eventId.
        return Boolean.TRUE.equals(
                redis.opsForValue().setIfAbsent("event:processed:query-service:" + eventId, "1", Duration.ofSeconds(86400)));
    }
}
