package com.dispatch.events.payload;

public record EtaUpdatedEvent(
        String tripId,
        String driverId,
        String riderId,
        long etaSeconds,
        double distanceMeters,
        String phase) {
}
