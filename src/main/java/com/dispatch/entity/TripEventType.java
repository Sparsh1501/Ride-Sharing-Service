package com.dispatch.entity;

/**
 * The full set of immutable, append-only trip lifecycle events.
 */
public enum TripEventType {
    TRIP_REQUESTED,
    DRIVER_ASSIGNED,
    DRIVER_ARRIVED,
    TRIP_STARTED,
    TRIP_COMPLETED,
    TRIP_CANCELLED,
    DRIVER_REJECTED
}
