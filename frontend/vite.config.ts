import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig(({ command, mode }) => ({
  plugins: [react()],
  base: command === 'build' ? '/app/' : '/',
  server: {
    port: 5173,
    proxy: {
      '/api': {
        // IPv4 avoids Windows localhost resolving to ::1 when Spring binds 127.0.0.1.
        // Override via VITE_DEV_API_TARGET in frontend/.env.local if backend uses another port.
        target: loadEnv(mode, process.cwd(), 'VITE_').VITE_DEV_API_TARGET || 'http://127.0.0.1:8083',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: '../src/main/resources/static/app',
    emptyOutDir: true,
  },
}));
