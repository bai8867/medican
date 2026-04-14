import { MOCK_NO_MATCH } from './mockTypes.js'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock.ts'

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchWeeklyCalendarApiMock(method, path, config) {
  if (method !== 'GET' || path !== '/campus/weekly-calendar') return MOCK_NO_MATCH
  const id =
    (config.params && (config.params.canteenId || config.params.canteen_id)) || 'north-1'
  return buildWeeklyCalendarMockPayload(String(id))
}

export function matchCampusCanteensApiMock(method, path) {
  if (method !== 'GET' || path !== '/campus/canteens') return MOCK_NO_MATCH
  return buildWeeklyCalendarMockPayload('north-1').canteens
}
