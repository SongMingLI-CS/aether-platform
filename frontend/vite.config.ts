import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  // Relative asset paths so the built bundle also works when loaded from
  // file:// inside the Electron shell (in addition to nginx/web hosting).
  base: './',
  plugins: [react()],
  server: {
    port: 5173,
    // 开发期将 /api 请求代理到本地 Spring Boot 后端
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
