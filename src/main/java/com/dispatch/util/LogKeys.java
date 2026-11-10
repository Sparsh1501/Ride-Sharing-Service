package com.dispatch.util;

import org.slf4j.MDC;

/**
 * Canonical MDC keys so structured JSON logs across every service carry the
 * same correlation fields (tripId, driverId, riderId, correlationId).
 */
public final class LogKeys {

    public static final String TRIP_ID = "tripId";
    public static final String DRIVER_ID = "driverId";
    public static final String RIDER_ID = "riderId";
    public static final String CORRELATION_ID = "correlationId";

    private LogKeys() {
    }

    public static void withTrip(String tripId) {
        if (tripId != null) {
            MDC.put(TRIP_ID, tripId);
        }
    }

    public static void withDriver(String driverId) {
        if (driverId != null) {
            MDC.put(DRIVER_ID, driverId);
        }
    }

    public static void withRider(String riderId) {
        if (riderId != null) {
            MDC.put(RIDER_ID, riderId);
        }
    }

    public static void withCorrelation(String correlationId) {
        if (correlationId != null) {
            MDC.put(CORRELATION_ID, correlationId);
        }
    }

    public static void clear() {
        MDC.clear();
    }
}
