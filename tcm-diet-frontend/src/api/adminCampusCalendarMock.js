import { MOCK_NO_MATCH } from './mockTypes.js'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock.ts'

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchAdminCampusWeeklyCalendarApiMock(method, path, config) {
  if (path !== '/admin/campus-weekly-calendar') return MOCK_NO_MATCH
  if (method === 'GET') {
    const weekMonday = (config.params && config.params.weekMonday) || '2000-01-01'
    const canteenId = (config.params && config.params.canteenId) || 'north-1'
    const pub = buildWeeklyCalendarMockPayload(String(canteenId))
    const days = Array.isArray(pub.days) ? pub.days : []
    const meals0 = days[0]?.meals
    const mealsTemplate = { breakfast: [], lunch: [], dinner: [], midnightSnack: [] }
    if (meals0 && typeof meals0 === 'object') {
      for (const k of Object.keys(mealsTemplate)) {
        const arr = Array.isArray(meals0[k]) ? meals0[k] : []
        mealsTemplate[k] = arr.map((d) => {
          if (!d || typeof d !== 'object') return {}
          const { id: _id, ...rest } = d
          return rest
        })
      }
    }
    return {
      canteens: pub.canteens,
      exists: true,
      weekMonday,
      canteenId,
      published: !!pub.published,
      weekTitle: pub.weekTitle || '',
      estimatedPublishNote: pub.estimatedPublishNote || '',
      days,
      mealsTemplate,
    }
  }
  if (method === 'PUT') {
    return null
  }
  return MOCK_NO_MATCH
}
