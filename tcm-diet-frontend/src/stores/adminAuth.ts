// @ts-nocheck
import { defineStore } from 'pinia'

import request from '@/api/request'
import { LS_ADMIN_TOKEN } from '@/utils/storedTokens'

/** 后端 JWT role → 路由 adminRoles */
function mapBackendRole(role) {
  if (!role) return ''
  const up = String(role).trim().toUpperCase()
  if (up === 'ADMIN') return 'admin'
  if (up === 'CANTEEN_MANAGER') return 'canteen'
  return ''
}

const ACCOUNTS = {
  admin: { password: '123456', role: 'admin' },
  canteen: { password: '123456', role: 'canteen' },
  canteen_manager: { password: '123456', role: 'canteen' },
}

/** Mock 下动态注册的食堂账号（用户名全小写 → { password, role }） */
const mockExtraAccounts = {}

export const useAdminAuthStore = defineStore('adminAuth', {
  state: () => ({
    token: '',
    /** @type {'' | 'admin' | 'canteen'} */
    role: '',
    username: '',
  }),

  getters: {
    isLoggedIn: (s) => Boolean(s.token),
  },

  actions: {
    /**
     * 写入登录/注册成功后的 JWT（须为管理员或食堂负责人）。
     * @returns {{ ok: true } | { ok: false, message: string }}
     */
    applyAuthPayload(data, fallbackUsername) {
      const token = data?.token
      const user = data?.user
      const mapped = mapBackendRole(user?.role)
      if (!token || !mapped) {
        return { ok: false, message: '该账号无管理后台权限（需管理员或食堂负责人）' }
      }
      this.token = token
      this.role = mapped
      this.username = String(user?.username || fallbackUsername || '').toLowerCase()
      try {
        localStorage.setItem(LS_ADMIN_TOKEN, token)
      } catch {
        /* ignore */
      }
      return { ok: true }
    },

    /**
     * 纯本地模拟（VITE_USE_MOCK=true 时使用）。
     * @returns {{ ok: true } | { ok: false, message: string }}
     */
    loginMock(username, password) {
      const key = String(username ?? '').trim().toLowerCase()
      const acc = ACCOUNTS[key] || mockExtraAccounts[key]
      if (!acc || acc.password !== String(password ?? '')) {
        return { ok: false, message: '用户名或密码错误' }
      }
      this.token = `mock_${acc.role}_${Date.now().toString(36)}`
      this.role = acc.role
      this.username = key
      try {
        localStorage.setItem(LS_ADMIN_TOKEN, this.token)
      } catch {
        /* ignore */
      }
      return { ok: true }
    },

    /**
     * Mock：注册食堂负责人（仅内存，不连数据库）。
     * @returns {{ ok: true } | { ok: false, message: string }}
     */
    registerCanteenManagerMock(username, password, passwordConfirm) {
      const u = String(username ?? '').trim()
      const p = String(password ?? '')
      const p2 = String(passwordConfirm ?? '')
      if (!u || !p) {
        return { ok: false, message: '请输入账号和密码' }
      }
      if (p !== p2) {
        return { ok: false, message: '两次输入的密码不一致' }
      }
      const key = u.toLowerCase()
      if (ACCOUNTS[key] || mockExtraAccounts[key]) {
        return { ok: false, message: '用户名已存在' }
      }
      mockExtraAccounts[key] = { password: p, role: 'canteen' }
      this.token = `mock_canteen_reg_${Date.now().toString(36)}`
      this.role = 'canteen'
      this.username = key
      try {
        localStorage.setItem(LS_ADMIN_TOKEN, this.token)
      } catch {
        /* ignore */
      }
      return { ok: true }
    },

    /**
     * 对接后端：POST /api/auth/login，并把 JWT 写入 tcm_admin_token（与校园端分键，避免互相覆盖）。
     * @returns {Promise<{ ok: true } | { ok: false, message: string }>}
     */
    async loginApi(username, password) {
      const u = String(username ?? '').trim()
      const p = String(password ?? '')
      if (!u || !p) {
        return { ok: false, message: '请输入账号和密码' }
      }
      try {
        const data = await request.post(
          '/auth/login',
          { username: u, password: p },
          { skipGlobalMessage: true },
        )
        return this.applyAuthPayload(data, u)
      } catch (e) {
        const msg = e?.message || e?.msg || '登录失败'
        return { ok: false, message: msg }
      }
    },

    /**
     * 对接后端：注册食堂负责人并自动登录。
     * @returns {Promise<{ ok: true } | { ok: false, message: string }>}
     */
    async registerCanteenManagerApi(username, password, passwordConfirm) {
      const u = String(username ?? '').trim()
      const p = String(password ?? '')
      const p2 = String(passwordConfirm ?? '')
      if (!u || !p) {
        return { ok: false, message: '请输入账号和密码' }
      }
      if (p !== p2) {
        return { ok: false, message: '两次输入的密码不一致' }
      }
      try {
        const data = await request.post(
          '/auth/register',
          { username: u, password: p, role: 'CANTEEN_MANAGER' },
          { skipGlobalMessage: true },
        )
        return this.applyAuthPayload(data, u)
      } catch (e) {
        const msg = e?.message || e?.msg || '注册失败'
        return { ok: false, message: msg }
      }
    },

    /**
     * @returns {Promise<{ ok: true } | { ok: false, message: string }>}
     */
    async login(username, password) {
      if (import.meta.env.VITE_USE_MOCK === 'true') {
        return this.loginMock(username, password)
      }
      return this.loginApi(username, password)
    },

    /**
     * 后台页注册食堂经理（非管理员自助开通）。
     * @returns {Promise<{ ok: true } | { ok: false, message: string }>}
     */
    async registerCanteenManager(username, password, passwordConfirm) {
      if (import.meta.env.VITE_USE_MOCK === 'true') {
        return this.registerCanteenManagerMock(username, password, passwordConfirm)
      }
      return this.registerCanteenManagerApi(username, password, passwordConfirm)
    },

    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      try {
        localStorage.removeItem(LS_ADMIN_TOKEN)
      } catch {
        /* ignore */
      }
    },
  },

  persist: {
    key: 'tcm_admin_auth',
  },
})
