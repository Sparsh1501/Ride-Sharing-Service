package com.dispatch.webhook;

import com.dispatch.service.SurgePricingService;

import com.dispatch.util.EventCodec;
import com.dispatch.dto.EventEnvelope;
import com.dispatch.util.KafkaTopics;
import com.dispatch.dto.DriverLocationUpdatedEvent;
import com.dispatch.dto.RideRequestedEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Feeds the sliding-window demand/supply counters from the live event streams.
 */
@Component
public class SurgeListeners {

    private final SurgePricingService surge;

    public SurgeListeners(SurgePricingService surge) {
        this.surge = surge;
    }

    @KafkaListener(topics = KafkaTopics.RIDE_REQUESTED, groupId = "surge-pricing-service")
    public void onRideRequested(String message) {
        EventEnvelope<RideRequestedEvent> env = EventCodec.decode(message, RideRequestedEvent.class);
        RideRequestedEvent r = env.payload();
        surge.recordRequest(r.pickupLat(), r.pickupLng());
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_LOCATION_UPDATED, groupId = "surge-pricing-service")
    public void onDriverLocation(String message) {
        EventEnvelope<DriverLocationUpdatedEvent> env = EventCodec.decode(message, DriverLocationUpdatedEvent.class);
        DriverLocationUpdatedEvent d = env.payload();
        surge.recordDriver(d.driverId(), d.lat(), d.lng());
    }
}
