package com.dispatch.common.pricing;

import com.dispatch.common.domain.VehicleType;

/**
 * Fare computation (INR).
 *
 * <pre>
 *   baseFare  = baseCharge + perKm * distanceKm + perMin * etaMin
 *   finalFare = baseFare * surgeMultiplier
 * </pre>
 */
public class FareCalculator {

    public double baseFare(VehicleType vehicleType, double distanceKm, double etaMinutes) {
        return vehicleType.baseCharge()
                + vehicleType.pricePerKm() * distanceKm
                + vehicleType.pricePerMin() * etaMinutes;
    }

    public double finalFare(VehicleType vehicleType, double distanceKm, double etaMinutes, double surgeMultiplier) {
        double base = baseFare(vehicleType, distanceKm, etaMinutes);
        return round2(base * surgeMultiplier);
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
