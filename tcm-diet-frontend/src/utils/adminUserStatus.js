/** PRD 5.6.4：管理员设置的用户状态（Mock 与路由兜底；连后端时以接口业务码 4031 为准） */

const LS_KEY = 'tcm_admin_user_status'

/**
 * @returns {Record<string, 'active' | 'disabled'>}
 */
export function readUserStatusMap() {
  try {
    const raw = localStorage.getItem(LS_KEY)
    const o = raw ? JSON.parse(raw) : {}
    return o && typeof o === 'object' ? o : {}
  } catch {
    return {}
  }
}

/**
 * @param {Record<string, 'active' | 'disabled'>} map
 */
export function writeUserStatusMap(map) {
  localStorage.setItem(LS_KEY, JSON.stringify(map || {}))
}

/**
 * @param {string} userId
 * @returns {boolean}
 */
export function isUserDisabled(userId) {
  const id = String(userId || '').trim()
  if (!id) return false
  return readUserStatusMap()[id] === 'disabled'
}

/**
 * @param {string} userId
 * @param {'active' | 'disabled'} status
 */
export function setUserStatus(userId, status) {
  const id = String(userId || '').trim()
  if (!id) return
  const next = { ...readUserStatusMap() }
  next[id] = status
  writeUserStatusMap(next)
}
