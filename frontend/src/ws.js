import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

// Connects to the notification service STOMP endpoint with a JWT token passed
// as a query param, mirroring how the backend authenticates handshakes.
export function connectStomp(token, onConnect, onLog) {
  const client = new Client({
    webSocketFactory: () => new SockJS(`/ws?token=${token || ''}`),
    reconnectDelay: 3000,
    onConnect: () => {
      onLog && onLog({ type: 'WS', message: 'connected' });
      onConnect && onConnect(client);
    },
    onStompError: (frame) => onLog && onLog({ type: 'WS_ERROR', message: frame.headers.message }),
    onWebSocketClose: () => onLog && onLog({ type: 'WS', message: 'disconnected' })
  });
  client.activate();
  return client;
}

export function subscribe(client, destination, handler) {
  return client.subscribe(destination, (msg) => {
    try {
      handler(JSON.parse(msg.body));
    } catch {
      handler(msg.body);
    }
  });
}
