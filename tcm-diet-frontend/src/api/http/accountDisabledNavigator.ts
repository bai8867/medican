import { LS_ADMIN_TOKEN, LS_CAMPUS_TOKEN, LS_LEGACY_TOKEN } from '@/utils/storedTokens'

export type AccountDisabledHandler = (message?: string) => Promise<void>

let accountDisabledHandler: AccountDisabledHandler = defaultAccountDisabledHandler

async function defaultAccountDisabledHandler(_message?: string): Promise<void> {
  try {
    localStorage.removeItem(LS_LEGACY_TOKEN)
    localStorage.removeItem(LS_CAMPUS_TOKEN)
    localStorage.removeItem(LS_ADMIN_TOKEN)
  } catch {
    /* ignore */
  }
  const base = import.meta.env.BASE_URL || '/'
  const href = base.endsWith('/') ? `${base}account-disabled` : `${base}/account-disabled`
  window.location.assign(href)
}

/** 在应用挂载前注入（见 main.ts）：登出双端会话 + 路由跳转 + 提示。 */
export function setAccountDisabledHandler(fn: AccountDisabledHandler | unknown): void {
  accountDisabledHandler = typeof fn === 'function' ? (fn as AccountDisabledHandler) : defaultAccountDisabledHandler
}

export function runAccountDisabledNavigation(message?: string): Promise<void> {
  return accountDisabledHandler(message)
}
