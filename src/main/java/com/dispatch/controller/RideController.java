package com.dispatch.controller;

import com.dispatch.dto.RideQuote;
import com.dispatch.dto.RideRequest;
import com.dispatch.checkout.RideRequestService;
import com.dispatch.dto.RideResponse;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rides")
public class RideController {

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
