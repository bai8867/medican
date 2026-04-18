import type { AxiosAdapter, AxiosError, AxiosInstance, InternalAxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import { resolveApiMock, getRequestPath } from '@/api/mockGateway'
import { runAccountDisabledNavigation } from '@/api/http/accountDisabledNavigator'
import { readAdminToken, readCampusToken } from '@/utils/storedTokens'

const ACCOUNT_DISABLED_CODE = 4031
const inflightControllers = new Map<string, AbortController>()
const runtimeMetricKey = 'tcm_runtime_metrics'

function useRealHttpWhenMock(config: InternalAxiosRequestConfig): boolean {
  if ((config.method || 'get').toUpperCase() !== 'GET') return false
  const path = getRequestPath(config)
  if (/^\/recipes\/\d+$/.test(path)) return true
  if (/^\/scenes\/\d+\/recipes$/.test(path)) return true
  if (path === '/campus/recipes/recommend-feed') {
    const p = (config.params || {}) as Record<string, unknown>
    const kw = String(p.keyword ?? p.q ?? '').trim()
    if (kw) return true
  }
  return false
}

function getBearerToken(config: InternalAxiosRequestConfig): string | null {
  const path = getRequestPath(config)
  const isAdminApi = /^\/admin(\/|$)/.test(path)
  if (isAdminApi) {
    return readAdminToken()
  }
  return readCampusToken()
}

function recordRuntimeMetric(path: string, elapsedMs: number, ok: boolean): void {
  if (!path) return
  const isKeyPath =
    path.includes('/campus/recipes/recommend-feed') ||
    path.includes('/ai/generate') ||
    path.includes('/ai/diet-plan')
  if (!isKeyPath) return
  try {
    const raw = localStorage.getItem(runtimeMetricKey)
    const parsed = (raw ? JSON.parse(raw) : {}) as Record<
      string,
      { total: number; ok: number; fail: number; avgMs: number; updatedAt?: number }
    >
    const prev = parsed[path] || { total: 0, ok: 0, fail: 0, avgMs: 0 }
    const total = prev.total + 1
    const okCount = prev.ok + (ok ? 1 : 0)
    const failCount = prev.fail + (ok ? 0 : 1)
    const avgMs = Math.round(((prev.avgMs || 0) * prev.total + Math.max(0, elapsedMs)) / total)
    parsed[path] = { total, ok: okCount, fail: failCount, avgMs, updatedAt: Date.now() }
    localStorage.setItem(runtimeMetricKey, JSON.stringify(parsed))
  } catch {
    /* ignore runtime metric errors */
  }
}

function buildDedupeKey(config: InternalAxiosRequestConfig): string {
  if (config.dedupeKey) return String(config.dedupeKey)
  const method = String(config.method || 'get').toUpperCase()
  const path = getRequestPath(config)
  const params = config.params ? JSON.stringify(config.params) : ''
  return `${method}:${path}:${params}`
}

const mockAdapter: AxiosAdapter = (config) =>
  resolveApiMock(config).then((result) => {
    if (result.ok) {
      return {
        data: { code: 200, data: result.data },
        status: 200,
        statusText: 'OK',
        headers: { 'content-type': 'application/json' },
        config,
        request: {},
      }
    }
    return {
      data: result.payload,
      status: 200,
      statusText: 'OK',
      headers: { 'content-type': 'application/json' },
      config,
      request: {},
    }
  })

export function installInterceptors(service: AxiosInstance, { useMock }: { useMock: boolean }): void {
  service.interceptors.request.use(
    (config) => {
      if (config.cancelPrevious === true) {
        const key = buildDedupeKey(config)
        const prev = inflightControllers.get(key)
        if (prev) prev.abort(`cancel_previous:${key}`)
        const controller = new AbortController()
        config.signal = controller.signal
        config.__dedupeKey = key
        inflightControllers.set(key, controller)
      }
      if (useMock && !useRealHttpWhenMock(config)) {
        config.adapter = mockAdapter
      }
      const token = getBearerToken(config)
      if (token) {
        config.headers.Authorization = `Bearer ${token}`
      }
      config.__requestStartedAt = Date.now()
      return config
    },
    (error) => Promise.reject(error),
  )

  /** 与历史实现一致：返回解包后的业务 data，而非 AxiosResponse（与 axios 默认泛型不完全一致，故用 any 收口） */
  service.interceptors.response.use(
    (response): any => {
      const config = response.config || {}
      if (config.__dedupeKey) {
        inflightControllers.delete(config.__dedupeKey)
      }
      const elapsedMs = Date.now() - Number(config.__requestStartedAt || Date.now())
      recordRuntimeMetric(getRequestPath(config), elapsedMs, true)
      const res = response.data as Record<string, unknown> | null
      if (res && typeof res === 'object' && 'code' in res) {
        const code = res.code as number
        if (code === 0 || code === 200) {
          return res.data !== undefined ? res.data : res
        }
        if (code === ACCOUNT_DISABLED_CODE) {
          const msg = String(res.msg || res.message || '')
          return runAccountDisabledNavigation(msg).then(() => {
            const err = new Error(msg || '账号已被禁用，请重新注册') as Error & { code?: number; accountDisabled?: boolean }
            err.code = code
            err.accountDisabled = true
            return Promise.reject(err)
          })
        }
        if (!config.skipGlobalMessage) {
          ElMessage.error(String(res.msg || res.message || '请求失败'))
        }
        const errMsg = String(res.msg || res.message || 'Error')
        const err = new Error(errMsg) as Error & { code?: number }
        err.code = code
        return Promise.reject(err)
      }
      return res ?? response.data
    },
    (error: AxiosError & { code?: string }) => {
      if (error.config?.__dedupeKey) {
        inflightControllers.delete(error.config.__dedupeKey)
      }
      if (error.config) {
        const elapsedMs = Date.now() - Number(error.config.__requestStartedAt || Date.now())
        recordRuntimeMetric(getRequestPath(error.config), elapsedMs, false)
      }
      if (error.code === 'ERR_CANCELED') {
        return Promise.reject(error)
      }
      const body = error.response?.data as Record<string, unknown> | undefined
      const isNoResponse =
        !error.response &&
        (error.code === 'ERR_NETWORK' ||
          (typeof error.message === 'string' && error.message.toLowerCase().includes('network')))
      const bodyCode = body && typeof body === 'object' && 'code' in body ? (body.code as number | null) : null
      if (bodyCode === ACCOUNT_DISABLED_CODE && body) {
        const msg = String(body.msg || body.message || '')
        return runAccountDisabledNavigation(msg).then(() => {
          const err = new Error(msg || '账号已被禁用，请重新注册') as Error & { code?: number; accountDisabled?: boolean }
          err.code = bodyCode
          err.accountDisabled = true
          return Promise.reject(err)
        })
      }
      const msg =
        (body && String(body.msg || body.message || '')) ||
        (isNoResponse
          ? '无法连接后端：请确认 Spring 已在 11888 启动；开发请用 npm run dev 走代理；后端在别的机器时改 .env 里 VITE_PROXY_TARGET 或 VITE_API_BASE_URL。'
          : '') ||
        error.message ||
        '网络异常，请稍后重试'
      if (!error.config?.skipGlobalMessage) {
        ElMessage.error(msg)
      }
      return Promise.reject(error)
    },
  )
}
