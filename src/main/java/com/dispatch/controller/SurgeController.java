package com.dispatch.controller;

import com.dispatch.service.SurgePricingService;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/surge")
public class SurgeController {

    private final SurgePricingService surge;
    private final StringRedisTemplate redis;

    SurgeController(SurgePricingService surge, StringRedisTemplate redis) {
        this.surge = surge;
        this.redis = redis;
    }

    @GetMapping("/{geohash}")
    public Map<String, Object> zone(@PathVariable String geohash) {
        Map<String, Object> body = new HashMap<>();
        body.put("geohash", geohash);
        body.put("multiplier", surge.currentMultiplier(geohash));
        body.put("demand", surge.demand(geohash));
        body.put("supply", surge.supply(geohash));
        return body;
    }

    @GetMapping("/lookup")
    public Map<String, Object> lookup(@RequestParam double lat, @RequestParam double lng) {
        return zone(surge.zoneFor(lat, lng));
    }

    @GetMapping("/map")
    public Map<String, Double> map() {
        Set<String> zones = redis.opsForSet().members(SurgePricingService.ZONES_KEY);
        Map<String, Double> result = new HashMap<>();
        if (zones != null) {
            for (String zone : zones) {
                result.put(zone, surge.currentMultiplier(zone));
            }
        }
        return result;
    }
}
