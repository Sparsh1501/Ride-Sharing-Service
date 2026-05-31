package com.dispatch.dispatch.kafka;

import com.dispatch.dispatch.match.DispatchService;
import com.dispatch.events.EventCodec;
import com.dispatch.events.EventEnvelope;
import com.dispatch.events.KafkaTopics;
import com.dispatch.events.payload.DriverResponseEvent;
import com.dispatch.events.payload.RideRequestedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class DispatchListeners {

    private static final Logger log = LoggerFactory.getLogger(DispatchListeners.class);

    private final DispatchService dispatch;
    private final IdempotencyGuard idempotency;

    public DispatchListeners(DispatchService dispatch, IdempotencyGuard idempotency) {
        this.dispatch = dispatch;
        this.idempotency = idempotency;
    }

    @KafkaListener(topics = KafkaTopics.RIDE_REQUESTED, groupId = "dispatch-service")
    public void onRideRequested(String message) {
        EventEnvelope<RideRequestedEvent> env = EventCodec.decode(message, RideRequestedEvent.class);
        if (!idempotency.firstTime(env.eventId())) {
            return;
        }
        try {
            dispatch.dispatch(env.payload());
        } catch (Exception e) {
            log.error("Failed to dispatch ride {}", env.payload().tripId(), e);
        }
    }

    @KafkaListener(topics = KafkaTopics.DRIVER_RESPONSE, groupId = "dispatch-service")
    public void onDriverResponse(String message) {
        EventEnvelope<DriverResponseEvent> env = EventCodec.decode(message, DriverResponseEvent.class);
        if (!idempotency.firstTime(env.eventId())) {
            return;
        }
        dispatch.onDriverResponse(env.payload());
    }
}
