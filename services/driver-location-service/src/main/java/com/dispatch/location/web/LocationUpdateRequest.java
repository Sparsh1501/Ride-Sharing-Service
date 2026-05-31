package com.dispatch.location.web;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record LocationUpdateRequest(
        @Min(-90) @Max(90) double lat,
        @Min(-180) @Max(180) double lng,
        Double heading,
        Double speedKmh,
        String cityId,
        String currentTripId) {
}
