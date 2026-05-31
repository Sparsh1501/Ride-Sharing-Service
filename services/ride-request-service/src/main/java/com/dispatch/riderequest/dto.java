package com.dispatch.riderequest;

import com.dispatch.common.domain.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Container file for the small request/response DTOs of this service. */
final class dto {
    private dto() {
    }
}

record RideRequest(
        @NotBlank String riderId,
        @Min(-90) @Max(90) double pickupLat,
        @Min(-180) @Max(180) double pickupLng,
        @Min(-90) @Max(90) double dropLat,
        @Min(-180) @Max(180) double dropLng,
        @NotNull VehicleType vehicleType,
        String cityId) {
}

record RideQuote(double distanceKm, long etaSeconds, double surgeMultiplier, double estimatedFare, String geohash) {
}

record RideResponse(String tripId, String status, String message, RideQuote quote) {
}
