import axios from 'axios'
import { ElMessage } from 'element-plus'
import { resolveApiMock, getRequestPath } from './mockGateway.js'
import {
  LS_ADMIN_TOKEN,
  LS_CAMPUS_TOKEN,
  LS_LEGACY_TOKEN,
  readAdminToken,
  readCampusToken,
} from '@/utils/storedTokens.js'

/** 与后端 ErrorCodes.ACCOUNT_DISABLED 一致：管理员禁用后须清会话并进入禁用说明页 */
const ACCOUNT_DISABLED_CODE = 4031

async function handleAccountDisabledFromApi(message) {
  const msg = message || '账号已被禁用，请重新注册'
  try {
    const [{ useUserStore }, { useAdminAuthStore }, routerMod] = await Promise.all([
      import('@/stores/user.js'),
      import('@/stores/adminAuth.js'),
      import('@/router/index.js'),
    ])
    useUserStore().logoutCampus()
    useAdminAuthStore().logout()
    ElMessage.error(msg)
    await routerMod.default.replace({ path: '/account-disabled' })
  } catch {
    try {
      localStorage.removeItem(LS_LEGACY_TOKEN)
      localStorage.removeItem(LS_CAMPUS_TOKEN)
      localStorage.removeItem(LS_ADMIN_TOKEN)
    } catch {
      /* ignore */
    }
    {
      const base = import.meta.env.BASE_URL || '/'
      const href = base.endsWith('/') ? `${base}account-disabled` : `${base}/account-disabled`
      window.location.assign(href)
    }
  }
}

/**
 * 以下请求在 VITE_USE_MOCK 时仍走真实 HTTP，以便学生端与库表药膳 id 一致（详情、场景方案等）。
 * 带关键词的推荐流搜索须查库，不能走本地 Mock 菜谱。
 * 其余接口继续走本地 Mock。
 */
function useRealHttpWhenMock(config) {
  if ((config.method || 'get').toUpperCase() !== 'GET') return false
  const path = getRequestPath(config)
  if (/^\/recipes\/\d+$/.test(path)) return true
  if (/^\/scenes\/\d+\/recipes$/.test(path)) return true
  if (path === '/campus/recipes/recommend-feed') {
    const p = config.params || {}
    const kw = String(p.keyword ?? p.q ?? '').trim()
    if (kw) return true
  }
  return false
}

/**
 * 后端根路径（须以 /api 结尾或与后端 context 一致）。
 * - 开发：未设置 VITE_API_BASE_URL 时用同源 `/api`，走 Vite 代理到 11888（见 vite.config.js）。
 * - 生产：未设置时默认相对 `/api`（与前端同域部署）；也可用 VITE_API_BASE_URL 指向独立网关。
 * - start.bat 会注入 http://localhost:11888/api 直连后端（不经代理）。
 */
const baseURL = import.meta.env.VITE_API_BASE_URL || '/api'

/** 为 true 时不发真实 HTTP，由 resolveApiMock 返回本地数据 */
const useMock = import.meta.env.VITE_USE_MOCK === 'true'

/**
 * 管理接口须带后台 JWT；校园接口带学生端 JWT。二者分键存储，避免校园登录覆盖 tcm_token 后后台仍显示已登录却请求全 403。
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
function getBearerToken(config) {
  const path = getRequestPath(config)
  const isAdminApi = /^\/admin(\/|$)/.test(path)
  if (isAdminApi) {
    return readAdminToken()
  }
  return readCampusToken()
}

const service = axios.create({
  baseURL,
  timeout: 20000,
})

/**
 * Mock 适配器：统一走 HTTP 200，业务码放在 body，与真实后端包装一致。
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
function mockAdapter(config) {
  return resolveApiMock(config).then((result) => {
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
}

service.interceptors.request.use(
  (config) => {
    if (useMock && !useRealHttpWhenMock(config)) {
      config.adapter = mockAdapter
    }
    const token = getBearerToken(config)
    if (token) {
      config.headers.Authorization = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error),
)

service.interceptors.response.use(
  (response) => {
    const config = response.config || {}
    const res = response.data
    if (res && typeof res === 'object' && 'code' in res) {
      if (res.code === 0 || res.code === 200) {
        return res.data !== undefined ? res.data : res
      }
      if (res.code === ACCOUNT_DISABLED_CODE) {
        return handleAccountDisabledFromApi(res.msg || res.message).then(() => {
          const err = new Error(res.msg || res.message || '账号已被禁用，请重新注册')
          err.code = res.code
          err.accountDisabled = true
          return Promise.reject(err)
        })
      }
      if (!config.skipGlobalMessage) {
        ElMessage.error(res.msg || res.message || '请求失败')
      }
      const errMsg = res.msg || res.message || 'Error'
      const err = new Error(errMsg)
      err.code = res.code
      return Promise.reject(err)
    }
    return res
  },
  (error) => {
    const body = error.response?.data
    const isNoResponse =
      !error.response &&
      (error.code === 'ERR_NETWORK' ||
        (typeof error.message === 'string' &&
          error.message.toLowerCase().includes('network')))
    const bodyCode = body && typeof body === 'object' && 'code' in body ? body.code : null
    if (bodyCode === ACCOUNT_DISABLED_CODE) {
      return handleAccountDisabledFromApi(body.msg || body.message).then(() => {
        const err = new Error(body.msg || body.message || '账号已被禁用，请重新注册')
        err.code = bodyCode
        err.accountDisabled = true
        return Promise.reject(err)
      })
    }
    const msg =
      (body && (body.msg || body.message)) ||
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

export default service
