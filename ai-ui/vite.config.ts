import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

export default defineConfig({
  base: '/ai/',
  plugins: [react()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  build: {
    outDir: '../src/main/resources/static/ai',
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      '/ai': 'http://localhost:8080',
      '/mcp': 'http://localhost:8080',
    },
  },
});
