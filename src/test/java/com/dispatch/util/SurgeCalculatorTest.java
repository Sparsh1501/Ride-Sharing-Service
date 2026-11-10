package com.dispatch.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SurgeCalculatorTest {

    private final SurgeCalculator surge = new SurgeCalculator(3.0);

    @Test
    void noSurgeBelowThreshold() {
        assertEquals(1.0, surge.multiplierForRatio(0.5));
        assertEquals(1.0, surge.multiplierForRatio(1.49));
    }

    @Test
    void tierBoundaries() {
        assertEquals(1.5, surge.multiplierForRatio(1.5));
        assertEquals(1.5, surge.multiplierForRatio(1.99));
        assertEquals(2.0, surge.multiplierForRatio(2.0));
        assertEquals(2.0, surge.multiplierForRatio(2.49));
        assertEquals(2.5, surge.multiplierForRatio(2.5));
        assertEquals(2.5, surge.multiplierForRatio(3.0));
    }

    @Test
    void cappedAboveThree() {
        assertEquals(3.0, surge.multiplierForRatio(3.01));
        assertEquals(3.0, surge.multiplierForRatio(50.0));
    }

    @Test
    void tenRequestsThreeDriversTriggersSurge() {
        // 10 pending / 3 available => 3.33 ratio => capped 3.0x
        assertEquals(3.0, surge.multiplier(10, 3));
    }

    @Test
    void zeroDriversWithDemandIsMaxSurge() {
        assertEquals(3.0, surge.multiplier(5, 0));
    }
}
