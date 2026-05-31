package com.dispatch.query.projection;

import com.dispatch.common.domain.TripEventType;
import com.dispatch.query.model.QueryTrip;
import com.dispatch.query.model.QueryTripRepository;
import com.dispatch.events.payload.TripEventMessage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * Denormalized read-model projection. Applies trip events to the
 * {@code query_trips} table. Idempotent per (tripId, eventType) because it only
 * advances fields, so replays during a rebuild converge to the same state.
 */
@Service
public class TripProjection {

    private final QueryTripRepository repository;

    public TripProjection(QueryTripRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void apply(TripEventMessage event) {
        QueryTrip trip = repository.findById(event.tripId())
                .orElseGet(() -> {
                    QueryTrip t = new QueryTrip();
                    t.setTripId(event.tripId());
                    t.setCreatedAt(Instant.now());
                    return t;
                });
        Map<String, Object> p = event.payload();

        switch (event.type()) {
            case TRIP_REQUESTED -> {
                trip.setRiderId(str(p, "riderId"));
                trip.setPickupLat(dbl(p, "pickupLat"));
                trip.setPickupLng(dbl(p, "pickupLng"));
                trip.setDropLat(dbl(p, "dropLat"));
                trip.setDropLng(dbl(p, "dropLng"));
                trip.setVehicleType(str(p, "vehicleType"));
                trip.setEstimatedFare(dbl(p, "estimatedFare"));
                trip.setStatus("SEARCHING");
            }
            case DRIVER_ASSIGNED -> {
                trip.setDriverId(str(p, "driverId"));
                trip.setStatus("DRIVER_ASSIGNED");
            }
            case DRIVER_REJECTED -> trip.setStatus("SEARCHING");
            case DRIVER_ARRIVED -> trip.setStatus("DRIVER_ARRIVED");
            case TRIP_STARTED -> trip.setStatus("TRIP_STARTED");
            case TRIP_COMPLETED -> {
                trip.setStatus("COMPLETED");
                trip.setFinalFare(dbl(p, "finalFare"));
                trip.setDistanceKm(dbl(p, "finalDistanceKm"));
                if (p.containsKey("surgeMultiplier")) {
                    trip.setSurgeMultiplier(dbl(p, "surgeMultiplier"));
                }
                trip.setCompletedAt(Instant.now());
                if (trip.getCreatedAt() != null) {
                    trip.setDurationMinutes(
                            (trip.getCompletedAt().toEpochMilli() - trip.getCreatedAt().toEpochMilli()) / 60000.0);
                }
            }
            case TRIP_CANCELLED -> {
                trip.setStatus("CANCELLED");
                trip.setCompletedAt(Instant.now());
            }
        }
        repository.save(trip);
    }

    private static String str(Map<String, Object> p, String k) {
        Object v = p.get(k);
        return v == null ? null : v.toString();
    }

    private static double dbl(Map<String, Object> p, String k) {
        Object v = p.get(k);
        if (v == null) {
            return 0.0;
        }
        return v instanceof Number n ? n.doubleValue() : Double.parseDouble(v.toString());
    }
}
