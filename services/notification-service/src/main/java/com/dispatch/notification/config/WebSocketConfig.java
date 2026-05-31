package com.dispatch.notification.config;

import com.dispatch.notification.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.util.Map;

/**
 * STOMP-over-WebSocket configuration. Connections authenticate via a JWT passed
 * as a {@code ?token=} query parameter during the handshake.
 */
@org.springframework.context.annotation.Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);
    private final JwtService jwtService;

    public WebSocketConfig(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .addInterceptors(new JwtHandshakeInterceptor(jwtService))
                .withSockJS();
    }

    static class JwtHandshakeInterceptor implements HandshakeInterceptor {
        private final JwtService jwtService;

        JwtHandshakeInterceptor(JwtService jwtService) {
            this.jwtService = jwtService;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                       WebSocketHandler wsHandler, Map<String, Object> attributes) {
            URI uri = request.getURI();
            String query = uri.getQuery();
            String token = extractToken(query);
            String subject = token == null ? null : jwtService.verify(token);
            if (subject != null) {
                attributes.put("userId", subject);
            }
            // Allow the handshake; unauthenticated sockets simply carry no userId.
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Exception exception) {
        }

        private String extractToken(String query) {
            if (query == null) {
                return null;
            }
            for (String part : query.split("&")) {
                if (part.startsWith("token=")) {
                    return part.substring("token=".length());
                }
            }
            return null;
        }
    }
}
