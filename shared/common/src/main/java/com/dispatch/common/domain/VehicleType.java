package com.dispatch.common.domain;

/**
 * Vehicle classes with their fare rates (INR).
 * baseFare = baseCharge + perKm * distanceKm + perMin * etaMin
 */
public enum VehicleType {
    ECONOMY(20.0, 12.0, 2.0, 1),
    PREMIUM(50.0, 20.0, 3.0, 2),
    AUTO(15.0, 8.0, 1.5, 0);

    private final double baseCharge;
    private final double pricePerKm;
    private final double pricePerMin;
    private final int tier;

    VehicleType(double baseCharge, double pricePerKm, double pricePerMin, int tier) {
        this.baseCharge = baseCharge;
        this.pricePerKm = pricePerKm;
        this.pricePerMin = pricePerMin;
        this.tier = tier;
    }

    public double baseCharge() {
        return baseCharge;
    }

    public double pricePerKm() {
        return pricePerKm;
    }

    public double pricePerMin() {
        return pricePerMin;
    }

    public int tier() {
        return tier;
    }

    /**
     * Vehicle type match factor used in driver scoring:
     * 1.0 for an exact match, 0.5 when the driver offers an upgrade tier, 0 otherwise.
     */
    public double matchFactor(VehicleType driverVehicle) {
        if (driverVehicle == this) {
            return 1.0;
        }
        return driverVehicle.tier > this.tier ? 0.5 : 0.0;
    }
}
