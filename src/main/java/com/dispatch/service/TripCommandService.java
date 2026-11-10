package com.dispatch.service;

import com.dispatch.dto.CommandResult;

import com.dispatch.entity.TripEventType;
import com.dispatch.util.LogKeys;
import com.dispatch.entity.TripState;
import com.dispatch.service.TripEventStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Command side of CQRS. Each handler validates, appends an event to the store
 * and returns only an acknowledgement.
 */
@Service
public class TripCommandService {

    private static final Logger log = LoggerFactory.getLogger(TripCommandService.class);
    public static final String ACTIVE_TRIPS = "trips:active";

    private final TripEventStore store;
    private final StringRedisTemplate redis;

    public TripCommandService(TripEventStore store, StringRedisTemplate redis) {
        this.store = store;
        this.redis = redis;
    }

    public CommandResult requestRide(String tripId, Map<String, Object> payload) {
        LogKeys.withTrip(tripId);
        store.appendEvent(tripId, TripEventType.TRIP_REQUESTED, payload);
        return new CommandResult(tripId, "SEARCHING", "Trip requested");
    }

    public CommandResult assignDriver(String tripId, String driverId, double driverLat, double driverLng,
                                      long etaSeconds, double matchScore) {
        LogKeys.withTrip(tripId);
        LogKeys.withDriver(driverId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("driverId", driverId);
        payload.put("driverLat", driverLat);
        payload.put("driverLng", driverLng);
        payload.put("etaSeconds", etaSeconds);
        payload.put("matchScore", matchScore);
        store.appendEvent(tripId, TripEventType.DRIVER_ASSIGNED, payload);
        redis.opsForSet().add(ACTIVE_TRIPS, tripId);
        return new CommandResult(tripId, "DRIVER_ASSIGNED", "Driver " + driverId + " assigned");
    }

    public CommandResult driverRejected(String tripId, String driverId, String nextDriverId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("driverId", driverId);
        payload.put("rejectedAt", Instant.now().toString());
        payload.put("nextDriverId", nextDriverId);
        store.appendEvent(tripId, TripEventType.DRIVER_REJECTED, payload);
        return new CommandResult(tripId, "SEARCHING", "Driver rejected, retrying");
    }

    public CommandResult driverArrived(String tripId, String driverId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("driverId", driverId);
        payload.put("arrivedAt", Instant.now().toString());
        payload.put("waitStartedAt", Instant.now().toString());
        store.appendEvent(tripId, TripEventType.DRIVER_ARRIVED, payload);
        return new CommandResult(tripId, "DRIVER_ARRIVED", "Driver arrived at pickup");
    }

    public CommandResult startTrip(String tripId, double startLat, double startLng) {
        TripState state = store.rehydrate(tripId);
        Map<String, Object> payload = new HashMap<>();
        payload.put("driverId", state.driverId());
        payload.put("startedAt", Instant.now().toString());
        payload.put("startLat", startLat);
        payload.put("startLng", startLng);
        store.appendEvent(tripId, TripEventType.TRIP_STARTED, payload);
        return new CommandResult(tripId, "TRIP_STARTED", "Trip started");
    }

    public CommandResult completeTrip(String tripId, double endLat, double endLng,
                                      double finalDistanceKm, double finalFare, double surgeMultiplier) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("endLat", endLat);
        payload.put("endLng", endLng);
        payload.put("completedAt", Instant.now().toString());
        payload.put("finalDistanceKm", finalDistanceKm);
        payload.put("finalFare", finalFare);
        payload.put("surgeMultiplier", surgeMultiplier);
        store.appendEvent(tripId, TripEventType.TRIP_COMPLETED, payload);
        redis.opsForSet().remove(ACTIVE_TRIPS, tripId);
        return new CommandResult(tripId, "COMPLETED", "Trip completed");
    }

    public CommandResult cancelTrip(String tripId, String cancelledBy, String reason, double cancellationFee) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("cancelledBy", cancelledBy);
        payload.put("reason", reason);
        payload.put("cancelledAt", Instant.now().toString());
        payload.put("cancellationFee", cancellationFee);
        store.appendEvent(tripId, TripEventType.TRIP_CANCELLED, payload);
        redis.opsForSet().remove(ACTIVE_TRIPS, tripId);
        return new CommandResult(tripId, "CANCELLED", "Trip cancelled");
    }
}
