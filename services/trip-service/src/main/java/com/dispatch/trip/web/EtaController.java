package com.dispatch.trip.web;

import com.dispatch.common.domain.VehicleType;
import com.dispatch.common.eta.EtaCalculator;
import com.dispatch.trip.eta.EtaCalculationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
class EtaController {

    private final EtaCalculationService eta;

    EtaController(EtaCalculationService eta) {
        this.eta = eta;
    }

    @GetMapping("/api/v1/eta")
    public Map<String, Object> eta(@RequestParam double driverLat,
                                   @RequestParam double driverLng,
                                   @RequestParam double riderLat,
                                   @RequestParam double riderLng,
                                   @RequestParam(required = false) VehicleType vehicleType) {
        EtaCalculator.EtaResult result = eta.estimate(driverLat, driverLng, riderLat, riderLng);
        return Map.of(
                "etaSeconds", result.etaSeconds(),
                "distanceMeters", Math.round(result.distanceMeters()),
                "roadFactor", result.roadFactor(),
                "avgSpeedKmh", result.avgSpeedKmh());
    }
}
