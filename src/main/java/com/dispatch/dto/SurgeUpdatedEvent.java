package com.dispatch.dto;

import java.time.Instant;

public record SurgeUpdatedEvent(
        String geohash,
        String cityId,
        double multiplier,
        long demandCount,
        long supplyCount,
        Instant recordedAt) {
}
