package com.dispatch.common.eta;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EtaCalculatorTest {

    private final EtaCalculator calc = new EtaCalculator();

    @Test
    void peakHourUsesSlowSpeedAndBusyFactor() {
        LocalDateTime peak = LocalDateTime.of(2026, 5, 28, 9, 0); // Thursday 9am
        assertEquals(18.0, calc.avgSpeedKmh(peak));
        assertEquals(1.7, calc.roadFactorFor(peak));
    }

    @Test
    void nightUsesFastSpeedAndHighwayFactor() {
        LocalDateTime night = LocalDateTime.of(2026, 5, 28, 23, 0);
        assertEquals(45.0, calc.avgSpeedKmh(night));
        assertEquals(1.2, calc.roadFactorFor(night));
    }

    @Test
    void normalDaytimeUsesNormalFactor() {
        LocalDateTime midday = LocalDateTime.of(2026, 5, 28, 14, 0);
        assertEquals(30.0, calc.avgSpeedKmh(midday));
        assertEquals(1.4, calc.roadFactorFor(midday));
    }

    @Test
    void etaMatchesFormula() {
        // two points ~1.11 km apart in latitude (~0.01 deg)
        LocalDateTime midday = LocalDateTime.of(2026, 5, 28, 14, 0);
        var result = calc.estimate(12.9000, 77.6000, 12.9100, 77.6000, midday);

        double distanceKm = result.distanceMeters() / 1000.0;
        double expectedMinutes = (distanceKm * 1.4) / 30.0 * 60.0;
        long expectedSeconds = Math.round(expectedMinutes * 60.0);

        assertEquals(expectedSeconds, result.etaSeconds());
        assertTrue(result.distanceMeters() > 1000 && result.distanceMeters() < 1200,
                "distance ~1.11km, was " + result.distanceMeters());
    }
}
