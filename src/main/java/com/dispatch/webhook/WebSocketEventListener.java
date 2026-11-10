package com.dispatch.webhook;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks active WebSocket connections as a gauge and logs graceful disconnects.
 * The simple broker cleans up a session's subscriptions automatically on
 * disconnect; here we just keep the count accurate.
 */
@Component
public class WebSocketEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final AtomicInteger active = new AtomicInteger(0);

    public WebSocketEventListener(MeterRegistry meters) {
        meters.gauge("websocket.connections.active", active);
    }

    @EventListener
    public void onConnect(SessionConnectedEvent event) {
        log.info("WebSocket connected, active={}", active.incrementAndGet());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        log.info("WebSocket disconnected (session {}), active={}", event.getSessionId(), active.decrementAndGet());
    }
}
