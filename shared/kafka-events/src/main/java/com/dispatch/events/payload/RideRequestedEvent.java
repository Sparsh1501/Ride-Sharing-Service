package com.dispatch.events.payload;

import com.dispatch.common.domain.VehicleType;

public record RideRequestedEvent(
        String tripId,
        String riderId,
        double pickupLat,
        double pickupLng,
        double dropLat,
        double dropLng,
        VehicleType vehicleType,
        String cityId,
        double estimatedFare,
        double surgeMultiplier) {
}
