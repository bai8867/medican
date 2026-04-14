/**
 * 聚合各模块 Mock 匹配；仅被 request.js 引用，避免与业务 API 模块静态循环依赖。
 */

import { MOCK_NO_MATCH, normalizeMockResult } from './mockTypes.js'

/**
 * @param {import('axios').InternalAxiosRequestConfig} config
 * @returns {string} 归一化路径，如 `/recipes`、`/recipes/demo-001`
 */
export function getRequestPath(config) {
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

/**
 * @param {import('axios').InternalAxiosRequestConfig} config
 * @returns {Promise<import('./mockTypes.js').NormalizedMockResult>}
 */
export async function resolveApiMock(config) {
  const method = (config.method || 'get').toUpperCase()
  const path = getRequestPath(config)

  const { matchAuthApiMock } = await import('./auth.js')
  let r = matchAuthApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminRecipeApiMock } = await import('./adminRecipe.js')
  r = matchAdminRecipeApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchScenesApiMock } = await import('./scenesMock.js')
  r = matchScenesApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchWeeklyMenuApiMock } = await import('./campusCalendar.js')
  r = matchWeeklyMenuApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchRecipeApiMock } = await import('./recipe.js')
  r = matchRecipeApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchProfileApiMock } = await import('./profile.js')
  r = matchProfileApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchConstitutionApiMock } = await import('./constitution.js')
  r = matchConstitutionApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAiApiMock } = await import('./ai.js')
  r = matchAiApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchIngredientApiMock } = await import('./ingredient.js')
  r = matchIngredientApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminUserApiMock } = await import('./adminUser.js')
  r = matchAdminUserApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminDashboardApiMock } = await import('./adminDashboard.js')
  r = matchAdminDashboardApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminSystemSettingsMock } = await import('./adminSystemSettings.js')
  r = matchAdminSystemSettingsMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchUserSettingsApiMock } = await import('./userSettings.ts')
  r = matchUserSettingsApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchWeeklyCalendarApiMock, matchCampusCanteensApiMock } = await import('./campusCalendarMock.js')
  r = matchWeeklyCalendarApiMock(method, path, config)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  r = matchCampusCanteensApiMock(method, path)
  if (r !== MOCK_NO_MATCH) return normalizeMockResult(r)

  const { matchAdminCampusWeeklyCalendarApiMock } = await import('./adminCampusCalendarMock.js')
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
