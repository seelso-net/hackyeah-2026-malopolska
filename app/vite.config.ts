import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// In dev, the API runs on :8080 (cd ../backend && ./mvnw quarkus:dev) and Vite proxies to it.
// In production the backend serves this app from the same origin, so no base URL is needed.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
      '/q': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
  build: {
    outDir: 'dist',
    sourcemap: false,
    // @ionic/react registers every Ionic component on import: that chunk is ~1.2 MB (~260 kB gzipped).
    chunkSizeWarningLimit: 1300,
    rolldownOptions: {
      output: {
        // Libraries change less often than the app: separate chunks stay cached across deployments.
        codeSplitting: {
          groups: [
            { name: 'ionic', test: /node_modules[\\/](@ionic|@stencil|ionicons)[\\/]/ },
            { name: 'map', test: /node_modules[\\/](leaflet|react-leaflet|@react-leaflet)[\\/]/ },
            { name: 'react', test: /node_modules[\\/](react|react-dom|react-router|react-router-dom|scheduler)[\\/]/ },
            { name: 'vendor', test: /node_modules[\\/]/ },
          ],
        },
      },
    },
  },
});
