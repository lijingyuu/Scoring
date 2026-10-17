import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const target = process.env.VITE_API_TARGET || 'https://api.eunomia.cc'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target,
        changeOrigin: true,
        secure: false,
        configure: (proxy) => {
          proxy.on('proxyReq', (proxyReq) => {
            if (target.includes('eunomia.cc')) {
              proxyReq.setHeader('Origin', 'https://www.eunomia.cc')
              proxyReq.setHeader('Referer', 'https://www.eunomia.cc/')
            }
          })
        },
      },
    },
  },
})
