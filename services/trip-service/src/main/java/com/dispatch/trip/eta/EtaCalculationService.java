package com.dispatch.trip.eta;

import com.dispatch.common.eta.EtaCalculator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class EtaCalculationService {

    private final EtaCalculator calculator;

    public EtaCalculationService(
            @Value("${eta.road-factor-normal:1.4}") double normal,
            @Value("${eta.road-factor-busy:1.7}") double busy,
            @Value("${eta.road-factor-highway:1.2}") double highway) {
        this.calculator = new EtaCalculator(normal, busy, highway);
    }

    public EtaCalculator.EtaResult estimate(double fromLat, double fromLng, double toLat, double toLng) {
        return calculator.estimate(fromLat, fromLng, toLat, toLng, LocalDateTime.now());
    }
}
