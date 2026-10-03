import type { CapacitorConfig } from '@capacitor/cli';

// Native builds call the API by its public URL: build with VITE_API_URL=https://your-api.example.org
// (and allow that app's origin in CORS, which the backend does by default).
const config: CapacitorConfig = {
  appId: 'app.needs.platform',
  appName: 'Neighbourhood Needs',
  webDir: 'dist',
  android: { allowMixedContent: true },
  server: { androidScheme: 'https' },
};

export default config;
