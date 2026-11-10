package com.dispatch.util;

import com.dispatch.entity.TripEvent;
import com.dispatch.entity.TripState;

import com.dispatch.entity.TripEventType;
import com.dispatch.entity.TripStatus;
import com.dispatch.entity.VehicleType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TripStateFolderTest {

    private final TripStateFolder folder = new TripStateFolder();

    @Test
    void rehydratesFinalStateFromEventLog() {
        List<TripEvent> events = new ArrayList<>();
        long seq = 0;
        events.add(new TripEvent(++seq, TripEventType.TRIP_REQUESTED, Map.of(
                "riderId", "rider-1",
                "pickupLat", 12.9716,
                "pickupLng", 77.5946,
                "dropLat", 12.9352,
                "dropLng", 77.6245,
                "vehicleType", "PREMIUM",
                "estimatedFare", 250.0)));
        events.add(new TripEvent(++seq, TripEventType.DRIVER_ASSIGNED, Map.of(
                "driverId", "driver-7",
                "etaSeconds", 300L,
                "matchScore", 0.91)));
        events.add(new TripEvent(++seq, TripEventType.DRIVER_ARRIVED, Map.of(
                "driverId", "driver-7")));
        events.add(new TripEvent(++seq, TripEventType.TRIP_STARTED, Map.of(
                "driverId", "driver-7")));
        events.add(new TripEvent(++seq, TripEventType.TRIP_COMPLETED, Map.of(
                "finalDistanceKm", 8.4,
                "finalFare", 268.0,
                "surgeMultiplier", 1.5)));

        TripState state = folder.rehydrate("trip-1", events);

        assertEquals("trip-1", state.tripId());
        assertEquals("rider-1", state.riderId());
        assertEquals("driver-7", state.driverId());
        assertEquals(TripStatus.COMPLETED, state.status());
        assertEquals(VehicleType.PREMIUM, state.vehicleType());
        assertEquals(250.0, state.estimatedFare());
        assertEquals(268.0, state.finalFare());
        assertEquals(8.4, state.distanceKm());
        assertEquals(1.5, state.surgeMultiplier());
        assertEquals(5, state.lastSequence());
    }

    @Test
    void rejectionKeepsTripSearchingAndAdvancesToNextDriver() {
        List<TripEvent> events = List.of(
                new TripEvent(1, TripEventType.TRIP_REQUESTED, Map.of(
                        "riderId", "rider-2", "vehicleType", "ECONOMY")),
                new TripEvent(2, TripEventType.DRIVER_ASSIGNED, Map.of("driverId", "d1")),
                new TripEvent(3, TripEventType.DRIVER_REJECTED, Map.of(
                        "driverId", "d1", "nextDriverId", "d2")));

        TripState state = folder.rehydrate("trip-2", events);

        assertEquals(TripStatus.SEARCHING, state.status());
        assertEquals("d2", state.driverId());
    }

    @Test
    void cancellationCapturesReason() {
        List<TripEvent> events = List.of(
                new TripEvent(1, TripEventType.TRIP_REQUESTED, Map.of(
                        "riderId", "rider-3", "vehicleType", "AUTO")),
                new TripEvent(2, TripEventType.TRIP_CANCELLED, Map.of(
                        "cancelledBy", "RIDER", "reason", "changed mind")));

        TripState state = folder.rehydrate("trip-3", events);

        assertEquals(TripStatus.CANCELLED, state.status());
        assertEquals("RIDER", state.cancelledBy());
        assertEquals("changed mind", state.cancellationReason());
    }
}
