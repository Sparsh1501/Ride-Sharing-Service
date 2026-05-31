package com.dispatch.events.payload;

import com.dispatch.common.domain.TripEventType;

import java.util.Map;

/**
 * Carries a single appended trip event onto the trip.events topic so the
 * query (read) side can build its materialized views.
 */
public record TripEventMessage(
        String tripId,
        long sequenceNumber,
        TripEventType type,
        Map<String, Object> payload) {
}
