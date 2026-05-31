package com.dispatch.common.trip;

import com.dispatch.common.domain.TripEventType;

import java.util.Map;

/**
 * A single immutable event in a trip's append-only log.
 */
public record TripEvent(
        long sequenceNumber,
        TripEventType type,
        Map<String, Object> payload) {
}
