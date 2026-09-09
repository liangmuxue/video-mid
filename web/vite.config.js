import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://8.130.74.232:8090',
        changeOrigin: true
      },
      '/health': {
        target: 'http://8.130.74.232:8090',
        changeOrigin: true
      }
    }
  }
})
