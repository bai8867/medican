import request from './request.js'
import { MOCK_NO_MATCH } from './mockTypes.js'

/** POST /api/auth/login → { token, user } */
export function login(username, password) {
  return request.post('/auth/login', { username, password })
}

/**
 * @param {'USER' | 'CANTEEN_MANAGER'} [role] 默认 USER（学生）
 */
export function register(username, password, role = 'USER') {
  return request.post('/auth/register', { username, password, role })
}

/**
 * @param {import('axios').InternalAxiosRequestConfig} [config]
 */
function parseMockJsonBody(config) {
  const d = config?.data
  if (d == null) return {}
  if (typeof d === 'object' && !(d instanceof FormData) && !Array.isArray(d)) return d
  if (typeof d === 'string') {
    try {
      return JSON.parse(d)
    } catch {
      return {}
    }
  }
  return {}
}

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} [config]
 */
export function matchAuthApiMock(method, path, config) {
  if (method === 'POST' && path === '/auth/login') {
    return {
      token: 'mock-campus-token',
      user: { id: '1', username: 'mock', role: 'USER' },
    }
  }
  if (method === 'POST' && path === '/auth/register') {
    const b = parseMockJsonBody(config)
    const u = String(b.username || 'mock').trim() || 'mock'
    const role =
      String(b.role || 'USER').toUpperCase() === 'CANTEEN_MANAGER' ? 'CANTEEN_MANAGER' : 'USER'
    return {
      token: `mock-register-${role}-${Date.now().toString(36)}`,
      user: { id: String(8000 + u.length), username: u, role },
    }
  }
  return MOCK_NO_MATCH
}
