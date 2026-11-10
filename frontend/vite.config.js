import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The simulator talks to the dispatch application on port 8080.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/ws': { target: 'http://localhost:8080', changeOrigin: true, ws: true }
    }
  }
});
