package com.dispatch.service;

import com.dispatch.entity.TripEventEntity;
import com.dispatch.repository.TripEventRepository;

import com.dispatch.entity.TripEventType;
import com.dispatch.util.JsonUtils;
import com.dispatch.entity.TripEvent;
import com.dispatch.entity.TripState;
import com.dispatch.util.TripStateFolder;
import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.TripEventMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Append-only event store for trip lifecycle. State is never mutated directly:
 * every change appends an immutable event, the materialized {@link TripState}
 * is derived by folding the log, cached in Redis, and the new event is
 * published to {@code trip.events} for the query (read) side.
 */
@Service
public class TripEventStore {

    private static final Logger log = LoggerFactory.getLogger(TripEventStore.class);

    private final TripEventRepository repository;
    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final TripStateFolder folder = new TripStateFolder();

    public TripEventStore(TripEventRepository repository, StringRedisTemplate redis, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.redis = redis;
        this.kafka = kafka;
    }

    @Transactional
    public long appendEvent(String tripId, TripEventType type, Map<String, Object> payload) {
        long sequence = repository.countByTripId(tripId) + 1;
        repository.save(new TripEventEntity(tripId, sequence, type.name(), JsonUtils.toJson(payload)));

        // invalidate the cached materialized state
        redis.delete(stateKey(tripId));

        TripEventMessage message = new TripEventMessage(tripId, sequence, type, payload);
        EventEnvelope<TripEventMessage> envelope =
                EventEnvelope.of("trip.events", "trip-service", tripId, message);
        kafka.send(KafkaTopics.TRIP_EVENTS, tripId, EventCodec.encode(envelope));

        log.info("Appended {} seq={} for trip {}", type, sequence, tripId);
        return sequence;
    }

    public List<TripEvent> getEvents(String tripId) {
        return repository.findByTripIdOrderBySequenceNumberAsc(tripId).stream()
                .map(this::toDomain)
                .toList();
    }

    public TripState rehydrate(String tripId) {
        String cached = redis.opsForValue().get(stateKey(tripId));
        if (cached != null) {
            return JsonUtils.fromJson(cached, TripState.class);
        }
        TripState state = folder.rehydrate(tripId, getEvents(tripId));
        redis.opsForValue().set(stateKey(tripId), JsonUtils.toJson(state), Duration.ofMinutes(10));
        return state;
    }

    @SuppressWarnings("unchecked")
    private TripEvent toDomain(TripEventEntity entity) {
        Map<String, Object> payload = JsonUtils.fromJson(entity.getPayload(), Map.class);
        return new TripEvent(entity.getSequenceNumber(), TripEventType.valueOf(entity.getEventType()), payload);
    }

    private String stateKey(String tripId) {
        return "trip:state:" + tripId;
    }
}
