package com.dispatch.common.trip;

import com.dispatch.common.domain.TripStatus;
import com.dispatch.common.domain.VehicleType;

import java.time.Instant;

/**
 * Materialized trip state derived purely by folding the event log.
 * Immutable — every transition produces a new instance.
 */
public record TripState(
        String tripId,
        String riderId,
        String driverId,
        TripStatus status,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng,
        VehicleType vehicleType,
        double estimatedFare,
        double finalFare,
        double surgeMultiplier,
        double distanceKm,
        long etaSeconds,
        String cancelledBy,
        String cancellationReason,
        long lastSequence,
        Instant createdAt,
        Instant completedAt) {

    public static TripState empty(String tripId) {
        return new TripState(tripId, null, null, TripStatus.REQUESTED,
                0, 0, 0, 0, null, 0, 0, 1.0, 0, 0,
                null, null, 0, null, null);
    }
}
