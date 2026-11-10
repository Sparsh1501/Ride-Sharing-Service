package com.dispatch.controller;

import com.dispatch.entity.TripEvent;
import com.dispatch.entity.TripState;
import com.dispatch.dto.CommandResult;
import com.dispatch.service.TripCommandService;
import com.dispatch.service.TripEventStore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/trips")
public class TripController {

    private final TripCommandService commands;
    private final TripEventStore store;

    TripController(TripCommandService commands, TripEventStore store) {
        this.commands = commands;
        this.store = store;
    }

    @PostMapping("/{tripId}/arrived")
    public CommandResult arrived(@PathVariable String tripId, @RequestParam String driverId) {
        return commands.driverArrived(tripId, driverId);
    }

    @PostMapping("/{tripId}/start")
    public CommandResult start(@PathVariable String tripId,
                               @RequestParam double startLat,
                               @RequestParam double startLng) {
        return commands.startTrip(tripId, startLat, startLng);
    }

    @PostMapping("/{tripId}/complete")
    public CommandResult complete(@PathVariable String tripId, @RequestBody Map<String, Object> body) {
        return commands.completeTrip(tripId,
                num(body, "endLat"), num(body, "endLng"),
                num(body, "finalDistanceKm"), num(body, "finalFare"),
                body.containsKey("surgeMultiplier") ? num(body, "surgeMultiplier") : 1.0);
    }

    @PostMapping("/{tripId}/cancel")
    public CommandResult cancel(@PathVariable String tripId,
                                @RequestParam String cancelledBy,
                                @RequestParam(required = false) String reason,
                                @RequestParam(defaultValue = "0") double fee) {
        return commands.cancelTrip(tripId, cancelledBy, reason, fee);
    }

    @GetMapping("/{tripId}/state")
    public TripState state(@PathVariable String tripId) {
        return store.rehydrate(tripId);
    }

    @GetMapping("/{tripId}/events")
    public List<TripEvent> events(@PathVariable String tripId) {
        return store.getEvents(tripId);
    }

    private double num(Map<String, Object> body, String key) {
        Object v = body.get(key);
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }
}
