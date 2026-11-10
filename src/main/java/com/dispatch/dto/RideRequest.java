package com.dispatch.dto;

import com.dispatch.entity.VehicleType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
public record RideRequest(
        @NotBlank String riderId,
        @Min(-90) @Max(90) double pickupLat,
        @Min(-180) @Max(180) double pickupLng,
        @Min(-90) @Max(90) double dropLat,
        @Min(-180) @Max(180) double dropLng,
        @NotNull VehicleType vehicleType,
        String cityId) {
}
