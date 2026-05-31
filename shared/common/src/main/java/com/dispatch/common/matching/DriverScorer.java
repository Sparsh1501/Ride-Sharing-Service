package com.dispatch.common.matching;

import com.dispatch.common.domain.VehicleType;

/**
 * Weighted driver match scoring.
 *
 * <pre>
 *   matchScore =
 *       0.40 * (1 - normalizedDistance)   // closer is better
 *     + 0.25 * normalizedRating           // higher rating is better
 *     + 0.20 * normalizedAcceptanceRate   // lower cancellation (higher acceptance) is better
 *     + 0.15 * vehicleTypeMatch           // 1.0 exact, 0.5 upgrade
 * </pre>
 */
public class DriverScorer {

    public static final double W_DISTANCE = 0.40;
    public static final double W_RATING = 0.25;
    public static final double W_ACCEPTANCE = 0.20;
    public static final double W_VEHICLE = 0.15;

    private static final double MAX_RATING = 5.0;

    public double score(DriverCandidate candidate, VehicleType requested, double searchRadiusKm) {
        double normalizedDistance = clamp01(candidate.distanceKm() / searchRadiusKm);
        double normalizedRating = clamp01(candidate.rating() / MAX_RATING);
        double normalizedAcceptance = clamp01(candidate.acceptanceRate());
        double vehicleMatch = requested.matchFactor(candidate.vehicleType());

        return W_DISTANCE * (1.0 - normalizedDistance)
                + W_RATING * normalizedRating
                + W_ACCEPTANCE * normalizedAcceptance
                + W_VEHICLE * vehicleMatch;
    }

    private double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }
}
