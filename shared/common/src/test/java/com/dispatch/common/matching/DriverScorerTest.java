package com.dispatch.common.matching;

import com.dispatch.common.domain.VehicleType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DriverScorerTest {

    private final DriverScorer scorer = new DriverScorer();

    @Test
    void perfectDriverScoresNearOne() {
        DriverCandidate ideal = new DriverCandidate(
                "d1", 0, 0, 0.0, 5.0, 1.0, VehicleType.ECONOMY);
        double score = scorer.score(ideal, VehicleType.ECONOMY, 5.0);
        assertEquals(1.0, score, 1e-9);
    }

    @Test
    void closerDriverOutscoresFartherOne() {
        DriverCandidate near = new DriverCandidate("near", 0, 0, 0.5, 4.5, 0.9, VehicleType.ECONOMY);
        DriverCandidate far = new DriverCandidate("far", 0, 0, 4.5, 4.5, 0.9, VehicleType.ECONOMY);

        assertTrue(scorer.score(near, VehicleType.ECONOMY, 5.0)
                > scorer.score(far, VehicleType.ECONOMY, 5.0));
    }

    @Test
    void vehicleUpgradeScoresLowerThanExactMatch() {
        DriverCandidate exact = new DriverCandidate("e", 0, 0, 1.0, 4.5, 0.9, VehicleType.ECONOMY);
        DriverCandidate upgrade = new DriverCandidate("u", 0, 0, 1.0, 4.5, 0.9, VehicleType.PREMIUM);

        assertTrue(scorer.score(exact, VehicleType.ECONOMY, 5.0)
                > scorer.score(upgrade, VehicleType.ECONOMY, 5.0));
    }
}
