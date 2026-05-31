package com.dispatch.riderequest;

import com.dispatch.common.geo.GeoHash;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.Map;

/**
 * Fetches the current surge multiplier for a pickup location from the
 * surge-pricing-service. Wrapped in a circuit breaker that degrades to 1.0x
 * (no surge) rather than cascading failure if the service is unavailable.
 */
@Component
public class SurgeClient {

    private static final Logger log = LoggerFactory.getLogger(SurgeClient.class);
    private final WebClient webClient;
    private final int precision;

    public SurgeClient(WebClient.Builder builder,
                       @Value("${surge.service-url:http://localhost:8085}") String surgeUrl,
                       @Value("${surge.geohash-precision:5}") int precision) {
        this.webClient = builder.baseUrl(surgeUrl).build();
        this.precision = precision;
    }

    @CircuitBreaker(name = "surge-service", fallbackMethod = "fallback")
    public double currentMultiplier(double lat, double lng) {
        String geohash = GeoHash.encode(lat, lng, precision);
        Map<?, ?> response = webClient.get()
                .uri("/api/v1/surge/{geohash}", geohash)
                .retrieve()
                .bodyToMono(Map.class)
                .timeout(Duration.ofSeconds(2))
                .block();
        if (response == null || response.get("multiplier") == null) {
            return 1.0;
        }
        return ((Number) response.get("multiplier")).doubleValue();
    }

    @SuppressWarnings("unused")
    private double fallback(double lat, double lng, Throwable t) {
        log.warn("surge-service unavailable, degrading to 1.0x: {}", t.getMessage());
        return 1.0;
    }
}
