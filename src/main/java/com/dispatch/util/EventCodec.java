package com.dispatch.util;

import com.dispatch.dto.EventEnvelope;

import com.dispatch.util.JsonUtils;
import com.fasterxml.jackson.databind.JavaType;

/**
 * Serializes/deserializes {@link EventEnvelope} instances to and from JSON
 * with the correct generic payload type.
 */
public final class EventCodec {

    private EventCodec() {
    }

    public static String encode(EventEnvelope<?> envelope) {
        return JsonUtils.toJson(envelope);
    }

    public static <T> EventEnvelope<T> decode(String json, Class<T> payloadType) {
        try {
            JavaType type = JsonUtils.mapper().getTypeFactory()
                    .constructParametricType(EventEnvelope.class, payloadType);
            return JsonUtils.mapper().readValue(json, type);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to decode envelope for " + payloadType, e);
        }
    }
}
