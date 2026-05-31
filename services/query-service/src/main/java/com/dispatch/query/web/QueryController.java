package com.dispatch.query.web;

import com.dispatch.query.model.QueryTrip;
import com.dispatch.query.model.QueryTripRepository;
import com.dispatch.query.projection.RebuildService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1")
class QueryController {

    private final QueryTripRepository trips;
    private final RebuildService rebuild;
    private final StringRedisTemplate redis;

    QueryController(QueryTripRepository trips, RebuildService rebuild, StringRedisTemplate redis) {
        this.trips = trips;
        this.rebuild = rebuild;
        this.redis = redis;
    }

    @GetMapping("/trips/{tripId}")
    public QueryTrip trip(@PathVariable String tripId) {
        return trips.findById(tripId).orElse(null);
    }

    @GetMapping("/trips/{tripId}/tracking")
    public Map<String, Object> tracking(@PathVariable String tripId) {
        QueryTrip trip = trips.findById(tripId).orElse(null);
        Map<String, Object> result = new HashMap<>();
        if (trip == null) {
            return result;
        }
        result.put("tripId", tripId);
        result.put("status", trip.getStatus());
        result.put("driverId", trip.getDriverId());
        if (trip.getDriverId() != null) {
            Map<Object, Object> loc = redis.<Object, Object>opsForHash().entries("driver:location:" + trip.getDriverId());
            result.put("driverLocation", loc);
        }
        return result;
    }

    @GetMapping("/riders/{riderId}/trips")
    public Page<QueryTrip> riderTrips(@PathVariable String riderId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        return trips.findByRiderIdOrderByCreatedAtDesc(riderId, PageRequest.of(page, size));
    }

    @GetMapping("/drivers/{driverId}/trips")
    public Map<String, Object> driverTrips(@PathVariable String driverId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size) {
        Page<QueryTrip> pageResult = trips.findByDriverIdOrderByCreatedAtDesc(driverId, PageRequest.of(page, size));
        double earnings = trips.findByDriverId(driverId).stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .mapToDouble(QueryTrip::getFinalFare).sum();
        Map<String, Object> result = new HashMap<>();
        result.put("trips", pageResult.getContent());
        result.put("totalTrips", pageResult.getTotalElements());
        result.put("totalEarnings", earnings);
        return result;
    }

    @GetMapping("/analytics/city/{cityId}/summary")
    public Map<String, Object> citySummary(@PathVariable String cityId) {
        Map<String, Object> result = new HashMap<>();
        result.put("cityId", cityId);
        result.put("completedTrips", trips.countByStatus("COMPLETED"));
        result.put("cancelledTrips", trips.countByStatus("CANCELLED"));
        result.put("activeTrips", trips.countByStatus("TRIP_STARTED") + trips.countByStatus("DRIVER_ASSIGNED"));
        Long activeDrivers = redis.opsForZSet().zCard("geo:drivers:active");
        result.put("activeDrivers", activeDrivers == null ? 0 : activeDrivers);
        return result;
    }

    @GetMapping("/analytics/surge/map")
    public Map<String, Double> surgeMap() {
        Set<String> zones = redis.opsForSet().members("surge:zones");
        Map<String, Double> result = new HashMap<>();
        if (zones != null) {
            for (String zone : zones) {
                String m = redis.opsForValue().get("surge:multiplier:" + zone);
                result.put(zone, m == null ? 1.0 : Double.parseDouble(m));
            }
        }
        return result;
    }

    @PostMapping("/admin/query/rebuild")
    public Map<String, Object> rebuild() {
        int replayed = rebuild.rebuild();
        return Map.of("status", "ok", "eventsReplayed", replayed);
    }
}
