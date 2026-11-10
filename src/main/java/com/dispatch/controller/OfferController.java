package com.dispatch.controller;

import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.DriverResponseEvent;
import com.dispatch.auth.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class OfferController {

    private static final Logger log = LoggerFactory.getLogger(OfferController.class);

    private final KafkaTemplate<String, String> kafka;
    private final StringRedisTemplate redis;
    private final JwtService jwt;

    OfferController(KafkaTemplate<String, String> kafka, StringRedisTemplate redis, JwtService jwt) {
        this.kafka = kafka;
        this.redis = redis;
        this.jwt = jwt;
    }

    @PostMapping("/drivers/{driverId}/ride-offer/{offerId}/accept")
    public ResponseEntity<?> accept(@PathVariable String driverId, @PathVariable String offerId) {
        return respond(driverId, offerId, DriverResponseEvent.Response.ACCEPT);
    }

    @PostMapping("/drivers/{driverId}/ride-offer/{offerId}/reject")
    public ResponseEntity<?> reject(@PathVariable String driverId, @PathVariable String offerId) {
        return respond(driverId, offerId, DriverResponseEvent.Response.REJECT);
    }

    /** Convenience token minting for the simulator dashboard. */
    @GetMapping("/auth/token")
    public Map<String, String> token(@RequestParam String userId,
                                     @RequestParam(defaultValue = "RIDER") String role) {
        return Map.of("token", jwt.issue(userId, role));
    }

    private ResponseEntity<?> respond(String driverId, String offerId, DriverResponseEvent.Response response) {
        String tripId = redis.opsForValue().get("offer:trip:" + offerId);
        if (tripId == null) {
            return ResponseEntity.status(410).body(Map.of("error", "offer expired or unknown"));
        }
        DriverResponseEvent event = new DriverResponseEvent(tripId, offerId, driverId, response);
        kafka.send(KafkaTopics.DRIVER_RESPONSE, tripId,
                EventCodec.encode(EventEnvelope.of("driver.response", "notification-service", tripId, event)));
        log.info("Driver {} {} offer {} (trip {})", driverId, response, offerId, tripId);
        return ResponseEntity.ok(Map.of("tripId", tripId, "response", response.name()));
    }
}
