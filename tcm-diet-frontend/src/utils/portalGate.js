/** 门户门禁：须从「选择模式」页进入对应端，避免首次访问通过复制内链绕过 */

const PORTAL_CAMPUS_KEY = 'tcm_portal_campus_ok'
const PORTAL_ADMIN_KEY = 'tcm_portal_admin_ok'
const PENDING_CAMPUS_REDIRECT_KEY = 'tcm_pending_campus_redirect'
const PENDING_ADMIN_REDIRECT_KEY = 'tcm_pending_admin_redirect'

function safeSessionGet(key) {
  try {
    return sessionStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSessionSet(key, value) {
  try {
    sessionStorage.setItem(key, value)
  } catch {
    /* ignore */
  }
}

function safeSessionRemove(key) {
  try {
    sessionStorage.removeItem(key)
  } catch {
    /* ignore */
  }
}

export function hasPortalCampusOk() {
  return safeSessionGet(PORTAL_CAMPUS_KEY) === '1'
}

export function hasPortalAdminOk() {
  return safeSessionGet(PORTAL_ADMIN_KEY) === '1'
}

export function setPortalCampusOk() {
  safeSessionSet(PORTAL_CAMPUS_KEY, '1')
}

export function setPortalAdminOk() {
  safeSessionSet(PORTAL_ADMIN_KEY, '1')
}

/** 回到门户页时清除，强制重新选择进入路径 */
export function clearPortalGateFlags() {
  safeSessionRemove(PORTAL_CAMPUS_KEY)
  safeSessionRemove(PORTAL_ADMIN_KEY)
}

function isSafeInternalPath(p) {
  return typeof p === 'string' && p.startsWith('/') && !p.startsWith('//')
}

/** @param {string} fullPath */
export function stashCampusRedirect(fullPath) {
  if (!isSafeInternalPath(fullPath)) return
  if (fullPath.startsWith('/admin')) return
  safeSessionSet(PENDING_CAMPUS_REDIRECT_KEY, fullPath)
}

/** @returns {string} */
export function takeCampusRedirectPending() {
  const v = safeSessionGet(PENDING_CAMPUS_REDIRECT_KEY)
  safeSessionRemove(PENDING_CAMPUS_REDIRECT_KEY)
  if (!v || !isSafeInternalPath(v) || v.startsWith('/admin')) return ''
  return v
}

/** @param {string} fullPath */
export function stashAdminRedirect(fullPath) {
  if (!isSafeInternalPath(fullPath)) return
  if (!fullPath.startsWith('/admin')) return
  safeSessionSet(PENDING_ADMIN_REDIRECT_KEY, fullPath)
}

/** @returns {string} */
export function takeAdminRedirectPending() {
  const v = safeSessionGet(PENDING_ADMIN_REDIRECT_KEY)
  safeSessionRemove(PENDING_ADMIN_REDIRECT_KEY)
  if (!v || !isSafeInternalPath(v) || !v.startsWith('/admin')) return ''
  return v
}
