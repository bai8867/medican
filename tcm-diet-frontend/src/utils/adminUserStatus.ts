// @ts-nocheck
/** PRD 5.6.4：管理员设置的用户状态（Mock 与路由兜底；连后端时以接口业务码 4031 为准） */

const LS_KEY = 'tcm_admin_user_status'

export type AdminUserRuntimeStatus = 'active' | 'disabled'

export function readUserStatusMap(): Record<string, AdminUserRuntimeStatus> {
  try {
    const raw = localStorage.getItem(LS_KEY)
    const o = raw ? JSON.parse(raw) : {}
    return o && typeof o === 'object' ? (o as Record<string, AdminUserRuntimeStatus>) : {}
  } catch {
    return {}
  }
}

export function writeUserStatusMap(map: Record<string, AdminUserRuntimeStatus>): void {
  localStorage.setItem(LS_KEY, JSON.stringify(map || {}))
}

export function isUserDisabled(userId: string | undefined | null): boolean {
  const id = String(userId || '').trim()
  if (!id) return false
  return readUserStatusMap()[id] === 'disabled'
}

export function setUserStatus(userId: string, status: AdminUserRuntimeStatus): void {
  const id = String(userId || '').trim()
  if (!id) return
  const next = { ...readUserStatusMap() }
  next[id] = status
  writeUserStatusMap(next)
}
