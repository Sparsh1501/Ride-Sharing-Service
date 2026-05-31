package com.dispatch.common.eta;

import com.dispatch.common.geo.Haversine;

import java.time.DayOfWeek;
import java.time.LocalDateTime;

/**
 * ETA estimation using Haversine distance, a road-type multiplier and an
 * average speed that varies with the time of day.
 *
 * <pre>
 *   etaMinutes = (distanceKm * roadFactor) / avgSpeedKmh * 60
 * </pre>
 */
public class EtaCalculator {

    private final double roadFactorNormal;
    private final double roadFactorBusy;
    private final double roadFactorHighway;

    private static final double SPEED_PEAK_KMH = 18.0;
    private static final double SPEED_NORMAL_KMH = 30.0;
    private static final double SPEED_NIGHT_KMH = 45.0;

    public EtaCalculator() {
        this(1.4, 1.7, 1.2);
    }

    public EtaCalculator(double roadFactorNormal, double roadFactorBusy, double roadFactorHighway) {
        this.roadFactorNormal = roadFactorNormal;
        this.roadFactorBusy = roadFactorBusy;
        this.roadFactorHighway = roadFactorHighway;
    }

    public EtaResult estimate(double fromLat, double fromLng, double toLat, double toLng, LocalDateTime at) {
        double distanceKm = Haversine.distanceKm(fromLat, fromLng, toLat, toLng);
        double roadFactor = roadFactorFor(at);
        double avgSpeed = avgSpeedKmh(at);

        double etaMinutes = (distanceKm * roadFactor) / avgSpeed * 60.0;
        long etaSeconds = Math.round(etaMinutes * 60.0);
        double distanceMeters = distanceKm * 1000.0;
        return new EtaResult(etaSeconds, distanceMeters, roadFactor, avgSpeed);
    }

    public double avgSpeedKmh(LocalDateTime at) {
        int hour = at.getHour();
        boolean weekday = at.getDayOfWeek() != DayOfWeek.SATURDAY
                && at.getDayOfWeek() != DayOfWeek.SUNDAY;
        if (weekday && isPeak(hour)) {
            return SPEED_PEAK_KMH;
        }
        if (hour >= 22 || hour < 5) {
            return SPEED_NIGHT_KMH;
        }
        return SPEED_NORMAL_KMH;
    }

    public double roadFactorFor(LocalDateTime at) {
        int hour = at.getHour();
        boolean weekday = at.getDayOfWeek() != DayOfWeek.SATURDAY
                && at.getDayOfWeek() != DayOfWeek.SUNDAY;
        if (weekday && isPeak(hour)) {
            return roadFactorBusy;
        }
        if (hour >= 22 || hour < 5) {
            return roadFactorHighway;
        }
        return roadFactorNormal;
    }

    private boolean isPeak(int hour) {
        return (hour >= 8 && hour < 10) || (hour >= 18 && hour < 20);
    }

    public record EtaResult(long etaSeconds, double distanceMeters, double roadFactor, double avgSpeedKmh) {
    }
}
