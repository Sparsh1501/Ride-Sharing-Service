package com.dispatch.common.matching;

import com.dispatch.common.domain.VehicleType;

public record DriverCandidate(
        String driverId,
        double lat,
        double lng,
        double distanceKm,
        double rating,
        double acceptanceRate,
        VehicleType vehicleType) {
}
