// @ts-nocheck
/** 校园端 JWT，与后台令牌分键存储，避免互相覆盖导致管理接口 403 */
export const LS_CAMPUS_TOKEN = 'tcm_campus_token'
/** 管理后台 JWT */
export const LS_ADMIN_TOKEN = 'tcm_admin_token'
/** 历史单键（迁移期回退） */
export const LS_LEGACY_TOKEN = 'tcm_token'

export function readCampusToken() {
  try {
    return localStorage.getItem(LS_CAMPUS_TOKEN) || localStorage.getItem(LS_LEGACY_TOKEN)
  } catch {
    return null
  }
}

export function readAdminToken() {
  try {
    const direct = localStorage.getItem(LS_ADMIN_TOKEN)
    if (direct) return direct
    const raw = localStorage.getItem('tcm_admin_auth')
    if (!raw) return null
    const o = JSON.parse(raw)
    const tok = o?.token ?? o?.state?.token
    return typeof tok === 'string' && tok.length > 0 ? tok : null
  } catch {
    return null
  }
}
