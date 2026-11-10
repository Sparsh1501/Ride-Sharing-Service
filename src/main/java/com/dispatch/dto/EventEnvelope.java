package com.dispatch.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Standard envelope wrapping every domain event. The header fields
 * (eventId, eventType, timestamp, correlationId, sourceService) are present on
 * every message so consumers can deduplicate and trace correlated flows.
 *
 * @param <T> the concrete payload type
 */
public record EventEnvelope<T>(
        String eventId,
        String eventType,
        Instant timestamp,
        String correlationId,
        String sourceService,
        T payload) {

    public static <T> EventEnvelope<T> of(String eventType,
                                          String sourceService,
                                          String correlationId,
                                          T payload) {
        return new EventEnvelope<>(
                UUID.randomUUID().toString(),
                eventType,
                Instant.now(),
                correlationId != null ? correlationId : UUID.randomUUID().toString(),
                sourceService,
                payload);
    }
}
