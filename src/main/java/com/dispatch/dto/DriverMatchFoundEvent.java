package com.dispatch.dto;

public record DriverMatchFoundEvent(
        String tripId,
        String riderId,
        String driverId,
        String offerId,
        double driverLat,
        double driverLng,
        long etaSeconds,
        double matchScore,
        double estimatedFare,
        int offerTimeoutSeconds) {
}
