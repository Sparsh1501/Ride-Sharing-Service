import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The simulator talks to the API gateway (8080) and the notification service's
// WebSocket (8086). Adjust these targets if you run services on other hosts.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8086', changeOrigin: true, ws: true }
    }
  }
});
