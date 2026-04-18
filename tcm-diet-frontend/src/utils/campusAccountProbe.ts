// @ts-nocheck
import request from '@/api/request'
import { readCampusToken } from '@/utils/storedTokens'
import { looksLikeBearerJwt } from '@/utils/authToken'

/** 与 request 模块、后端 ErrorCodes.ACCOUNT_DISABLED 一致 */
const ACCOUNT_DISABLED_CODE = 4031

const PROBE_MS = 8000
let lastProbeAt = 0

export function resetCampusAccountProbeThrottle() {
  lastProbeAt = 0
}

/** 切回前台时允许立刻再探测（仍受 Mock / 无 JWT 跳过） */
export function notifyCampusTabForeground() {
  lastProbeAt = 0
}

/**
 * 连后端且携带校园 JWT 时，轻量请求 profile 以触发 JwtAuthFilter 的禁用校验（4031）。
 * @returns {Promise<boolean>} 是否判定为已禁用（含请求层已跳转 account-disabled）
 */
export async function maybeProbeCampusAccountStatus() {
  if (import.meta.env.VITE_USE_MOCK === 'true') return false
  const tok = readCampusToken()
  if (!looksLikeBearerJwt(tok)) return false
  const now = Date.now()
  if (now - lastProbeAt < PROBE_MS) return false
  lastProbeAt = now
  try {
    await request.get('/user/profile', { skipGlobalMessage: true })
    return false
  } catch (e) {
    if (e?.code === ACCOUNT_DISABLED_CODE || e?.accountDisabled) {
      return true
    }
    return false
  }
}
