import type { InternalAxiosRequestConfig } from 'axios'
import request from './request'
import { MOCK_NO_MATCH } from './mockTypes'

export interface AuthUser {
  id?: string | number
  username: string
  role: string
}

export interface AuthLoginResult {
  token: string
  user: AuthUser
}

export type RegisterRole = 'USER' | 'CANTEEN_MANAGER'

/** POST /api/auth/login → { token, user } */
export function login(username: string, password: string): Promise<AuthLoginResult> {
  return request.post('/auth/login', { username, password })
}

/** 校园注册；默认学生 USER */
export function register(
  username: string,
  password: string,
  role: RegisterRole = 'USER',
): Promise<AuthLoginResult> {
  return request.post('/auth/register', { username, password, role })
}

function parseMockJsonBody(config?: InternalAxiosRequestConfig): Record<string, unknown> {
  const d = config?.data
  if (d == null) return {}
  if (typeof d === 'object' && !(d instanceof FormData) && !Array.isArray(d)) return d as Record<string, unknown>
  if (typeof d === 'string') {
    try {
      return JSON.parse(d) as Record<string, unknown>
    } catch {
      return {}
    }
  }
  return {}
}

export function matchAuthApiMock(
  method: string,
  path: string,
  config?: InternalAxiosRequestConfig,
): unknown {
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
