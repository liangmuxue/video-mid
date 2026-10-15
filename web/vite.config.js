import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

function proxyBackend(path) {
  return {
    target: 'http://8.130.74.232:8090',
    changeOrigin: true,
    configure(proxy) {
      proxy.on('error', (_err, _req, res) => {
        if (!res || res.headersSent || typeof res.writeHead !== 'function') return
        res.writeHead(503, { 'Content-Type': 'application/json; charset=utf-8' })
        res.end(JSON.stringify({ code: 503, message: '无法连接后端服务，请确认 API 已启动' }))
      })
    }
  }
}

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': proxyBackend('/api'),
      '/health': proxyBackend('/health')
    }
  }
})
