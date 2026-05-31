package com.dispatch.riderequest;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@RestController
@RequestMapping("/api/v1/rides")
class RideController {

    private final RideRequestService service;

    RideController(RideRequestService service) {
        this.service = service;
    }

    @PostMapping
    public RideResponse request(@Valid @RequestBody RideRequest request) {
        return service.requestRide(request);
    }

    @PostMapping("/quote")
    public RideQuote quote(@Valid @RequestBody RideRequest request) {
        return service.quote(request);
    }
}

@Configuration
class WebClientConfig {
    @Bean
    WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
