import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import { aiApiDevMockPlugin } from './vite-plugins/aiApiDevMock.js'

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  const apiPrefix = env.VITE_API_PREFIX || '/api'
  const useAiDevMock = mode === 'development' && env.VITE_AI_MOCK !== '0'

  return {
    plugins: [
      ...(useAiDevMock ? [aiApiDevMockPlugin({ apiPrefix })] : []),
      vue(),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url)),
      },
    },
    server: {
      port: 11999,
      host: '0.0.0.0',
      strictPort: true,
      // 保留 /api 前缀转发到 Spring（控制器均为 /api/...），勿去掉前缀
      proxy: {
        [apiPrefix]: {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:11888',
          changeOrigin: true,
          timeout: 120000,
          proxyTimeout: 120000,
        },
      },
    },
    preview: {
      port: 11999,
      host: '0.0.0.0',
      strictPort: true,
      proxy: {
        [apiPrefix]: {
          target: env.VITE_PROXY_TARGET || 'http://127.0.0.1:11888',
          changeOrigin: true,
          timeout: 120000,
          proxyTimeout: 120000,
        },
      },
    },
  }
})
