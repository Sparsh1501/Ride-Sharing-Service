package com.dispatch.service;

import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.SurgeUpdatedEvent;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Recomputes the surge multiplier for every active zone on a fixed interval,
 * caches it with a 90s TTL, and emits {@code surge.updated} only when the
 * multiplier for a zone actually changes.
 */
@Component
public class SurgeRecalculator {

    private static final Logger log = LoggerFactory.getLogger(SurgeRecalculator.class);
    private static final Duration MULTIPLIER_TTL = Duration.ofSeconds(90);

    private final SurgePricingService surge;
    private final StringRedisTemplate redis;
    private final KafkaTemplate<String, String> kafka;
    private final MeterRegistry meters;

    public SurgeRecalculator(SurgePricingService surge, StringRedisTemplate redis,
                             KafkaTemplate<String, String> kafka, MeterRegistry meters) {
        this.surge = surge;
        this.redis = redis;
        this.kafka = kafka;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${surge.recalculation-interval-seconds:60}000")
    public void recalculate() {
        Set<String> zones = redis.opsForSet().members(SurgePricingService.ZONES_KEY);
        if (zones == null) {
            return;
        }
        for (String zone : zones) {
            long demand = surge.demand(zone);
            long supply = surge.supply(zone);
            double previous = surge.currentMultiplier(zone);
            double multiplier = surge.compute(zone);

            redis.opsForValue().set(SurgePricingService.multiplierKey(zone),
                    String.valueOf(multiplier), MULTIPLIER_TTL);
            meters.summary("surge.multiplier.distribution").record(multiplier);

            if (Double.compare(previous, multiplier) != 0) {
                SurgeUpdatedEvent event = new SurgeUpdatedEvent(zone, "default", multiplier, demand, supply, Instant.now());
                kafka.send(KafkaTopics.SURGE_UPDATED, zone,
                        EventCodec.encode(EventEnvelope.of("surge.updated", "surge-pricing-service", zone, event)));
                log.info("Surge changed zone={} {}x -> {}x (demand={}, supply={})",
                        zone, previous, multiplier, demand, supply);
            }
        }
    }
}
