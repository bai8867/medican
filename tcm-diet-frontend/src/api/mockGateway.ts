/**
 * 聚合各模块 Mock 匹配；仅被 `http/client`（经 `request` 入口）引用，避免与业务 API 模块静态循环依赖。
 */

import type { InternalAxiosRequestConfig } from 'axios'
import { MOCK_NO_MATCH, normalizeMockResult } from './mockTypes'
import type { NormalizedMockResult } from './mockTypes'

export function getRequestPath(config: InternalAxiosRequestConfig): string {
  const raw = (config.url || '').split('?')[0] || '/'
  if (raw.startsWith('http://') || raw.startsWith('https://')) {
    try {
      const pathname = new URL(raw).pathname
      if (pathname.startsWith('/api/')) return pathname.slice(4) || '/'
      if (pathname === '/api') return '/'
      return pathname || '/'
    } catch {
      return '/'
    }
  }
  return raw.startsWith('/') ? raw : `/${raw}`
}

export async function resolveApiMock(config: InternalAxiosRequestConfig): Promise<NormalizedMockResult> {
  const method = (config.method || 'get').toUpperCase()
  const path = getRequestPath(config)

  const { matchAuthApiMock } = await import('./auth')
  let r: unknown = matchAuthApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminRecipeApiMock } = await import('./adminRecipe')
  r = matchAdminRecipeApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchScenesApiMock } = await import('./scenesMock')
  r = matchScenesApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchWeeklyMenuApiMock } = await import('./campusCalendar')
  r = matchWeeklyMenuApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchRecipeApiMock } = await import('./recipe')
  r = matchRecipeApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchProfileApiMock } = await import('./profile')
  r = matchProfileApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchConstitutionApiMock } = await import('./constitution')
  r = matchConstitutionApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAiApiMock } = await import('./ai')
  r = matchAiApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchIngredientApiMock } = await import('./ingredientMock')
  r = matchIngredientApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminUserApiMock } = await import('./adminUser')
  r = matchAdminUserApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminDashboardApiMock } = await import('./adminDashboard')
  r = matchAdminDashboardApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminSystemSettingsMock } = await import('./adminSystemSettings')
  r = matchAdminSystemSettingsMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminAiQualityMock } = await import('./adminAiQualityMock')
  r = matchAdminAiQualityMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchUserSettingsApiMock } = await import('./userSettings')
  r = matchUserSettingsApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchWeeklyCalendarApiMock, matchCampusCanteensApiMock } = await import('./campusCalendarMock')
  r = matchWeeklyCalendarApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  r = matchCampusCanteensApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminCampusWeeklyCalendarApiMock } = await import('./adminCampusCalendarMock')
  r = matchAdminCampusWeeklyCalendarApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  return {
    ok: false,
    status: 404,
    payload: {
      code: 404,
      message: `Mock 未覆盖: ${method} ${path}`,
    },
  }
}
