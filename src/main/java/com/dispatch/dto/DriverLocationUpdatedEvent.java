package com.dispatch.dto;

import java.time.Instant;

public record DriverLocationUpdatedEvent(
        String driverId,
        String cityId,
        double lat,
        double lng,
        double heading,
        double speedKmh,
        String currentTripId,
        Instant updatedAt) {
}
