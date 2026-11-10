package com.dispatch.util;

import com.dispatch.entity.TripEvent;
import com.dispatch.entity.TripState;

import com.dispatch.entity.TripStatus;
import com.dispatch.entity.VehicleType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Folds an ordered list of trip events left-to-right to derive the current
 * {@link TripState}. This is the heart of the event-sourced trip lifecycle:
 * state is never mutated directly, only rebuilt from the immutable log.
 */
public class TripStateFolder {

    public TripState rehydrate(String tripId, List<TripEvent> events) {
        Acc acc = new Acc();
        acc.tripId = tripId;
        for (TripEvent event : events) {
            apply(acc, event);
            acc.lastSequence = event.sequenceNumber();
        }
        return acc.toState();
    }

    private void apply(Acc s, TripEvent event) {
        Map<String, Object> p = event.payload();
        switch (event.type()) {
            case TRIP_REQUESTED -> {
                s.riderId = str(p, "riderId");
                s.pickupLat = dbl(p, "pickupLat");
                s.pickupLng = dbl(p, "pickupLng");
                s.dropLat = dbl(p, "dropLat");
                s.dropLng = dbl(p, "dropLng");
                s.vehicleType = vehicle(p, "vehicleType");
                s.estimatedFare = dbl(p, "estimatedFare");
                s.status = TripStatus.SEARCHING;
                s.createdAt = Instant.now();
            }
            case DRIVER_ASSIGNED -> {
                s.driverId = str(p, "driverId");
                s.etaSeconds = lng(p, "etaSeconds");
                s.status = TripStatus.DRIVER_ASSIGNED;
            }
            case DRIVER_REJECTED -> {
                // driver declined; stay searching, clear current candidate
                s.driverId = str(p, "nextDriverId");
                s.status = TripStatus.SEARCHING;
            }
            case DRIVER_ARRIVED -> s.status = TripStatus.DRIVER_ARRIVED;
            case TRIP_STARTED -> s.status = TripStatus.TRIP_STARTED;
            case TRIP_COMPLETED -> {
                s.finalFare = dbl(p, "finalFare");
                s.distanceKm = dbl(p, "finalDistanceKm");
                if (p.containsKey("surgeMultiplier")) {
                    s.surgeMultiplier = dbl(p, "surgeMultiplier");
                }
                s.status = TripStatus.COMPLETED;
                s.completedAt = Instant.now();
            }
            case TRIP_CANCELLED -> {
                s.cancelledBy = str(p, "cancelledBy");
                s.cancellationReason = str(p, "reason");
                s.status = TripStatus.CANCELLED;
                s.completedAt = Instant.now();
            }
            default -> throw new IllegalArgumentException("Unknown event type: " + event.type());
        }
    }

    private static String str(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? null : v.toString();
    }

    private static double dbl(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) {
            return 0.0;
        }
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
    }

    private static long lng(Map<String, Object> p, String key) {
        Object v = p.get(key);
        if (v == null) {
            return 0L;
        }
        return v instanceof Number n ? n.longValue() : Long.parseLong(v.toString());
    }

    private static VehicleType vehicle(Map<String, Object> p, String key) {
        Object v = p.get(key);
        return v == null ? null : VehicleType.valueOf(v.toString());
    }

    private static final class Acc {
        String tripId;
        String riderId;
        String driverId;
        TripStatus status = TripStatus.REQUESTED;
        double pickupLat;
        double pickupLng;
        double dropLat;
        double dropLng;
        VehicleType vehicleType;
        double estimatedFare;
        double finalFare;
        double surgeMultiplier = 1.0;
        double distanceKm;
        long etaSeconds;
        String cancelledBy;
        String cancellationReason;
        long lastSequence;
        Instant createdAt;
        Instant completedAt;

        TripState toState() {
            return new TripState(tripId, riderId, driverId, status, pickupLat, pickupLng,
                    dropLat, dropLng, vehicleType, estimatedFare, finalFare, surgeMultiplier,
                    distanceKm, etaSeconds, cancelledBy, cancellationReason, lastSequence,
                    createdAt, completedAt);
        }
    }
}
