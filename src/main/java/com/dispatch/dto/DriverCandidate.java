package com.dispatch.dto;

import com.dispatch.entity.VehicleType;

public record DriverCandidate(
        String driverId,
        double lat,
        double lng,
        double distanceKm,
        double rating,
        double acceptanceRate,
        VehicleType vehicleType) {
}
