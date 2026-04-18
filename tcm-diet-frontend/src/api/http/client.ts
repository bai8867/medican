import axios from 'axios'
import type { TcmHttpClient } from './httpTypes'
import { installInterceptors } from './interceptors'

/** 与 `vite.config.js` 中 `VITE_API_PREFIX` 对齐：仅改代理前缀时避免仍请求默认 `/api` 而 404 */
const baseURL =
  import.meta.env.VITE_API_BASE_URL || import.meta.env.VITE_API_PREFIX || '/api'
const useMock = import.meta.env.VITE_USE_MOCK === 'true'

const service = axios.create({
  baseURL,
  timeout: 20000,
})

installInterceptors(service, { useMock })

export default service as TcmHttpClient
