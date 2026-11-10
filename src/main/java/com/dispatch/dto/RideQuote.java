package com.dispatch.dto;

public record RideQuote(double distanceKm, long etaSeconds, double surgeMultiplier, double estimatedFare, String geohash) {
}
