package com.dispatch.location.web;

import com.dispatch.common.domain.DriverStatus;
import com.dispatch.events.payload.DriverLocationUpdatedEvent;
import com.dispatch.location.service.DriverLocationService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/drivers")
public class DriverLocationController {

    private final DriverLocationService service;

    public DriverLocationController(DriverLocationService service) {
        this.service = service;
    }

    @PostMapping("/{driverId}/location")
    public ResponseEntity<DriverLocationUpdatedEvent> updateLocation(
            @PathVariable String driverId,
            @Valid @RequestBody LocationUpdateRequest request) {
        return ResponseEntity.accepted().body(service.updateLocation(driverId, request));
    }

    @PostMapping("/{driverId}/register")
    public ResponseEntity<Void> register(
            @PathVariable String driverId,
            @RequestParam String vehicleType,
            @RequestParam(defaultValue = "5.0") double rating,
            @RequestParam(required = false) String cityId) {
        service.registerDriver(driverId, vehicleType, rating, cityId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{driverId}/status")
    public ResponseEntity<Void> setStatus(@PathVariable String driverId, @RequestParam DriverStatus status) {
        service.setStatus(driverId, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/{driverId}/state")
    public Map<Object, Object> state(@PathVariable String driverId) {
        return service.getState(driverId);
    }

    @GetMapping("/{driverId}/location")
    public Map<Object, Object> location(@PathVariable String driverId) {
        return service.getLocation(driverId);
    }

    @GetMapping("/nearby")
    public List<String> nearby(@RequestParam double lat,
                               @RequestParam double lng,
                               @RequestParam(defaultValue = "5") double radiusKm) {
        return service.nearby(lat, lng, radiusKm).stream()
                .map(g -> g.getName())
                .toList();
    }
}
