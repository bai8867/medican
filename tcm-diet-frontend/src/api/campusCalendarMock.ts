import type { InternalAxiosRequestConfig } from 'axios'
import { MOCK_NO_MATCH } from './mockTypes'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock'

export function matchWeeklyCalendarApiMock(
  method: string,
  path: string,
  config: InternalAxiosRequestConfig,
): typeof MOCK_NO_MATCH | ReturnType<typeof buildWeeklyCalendarMockPayload> {
  if (method !== 'GET' || path !== '/campus/weekly-calendar') return MOCK_NO_MATCH
  const id =
    (config.params && (config.params.canteenId || config.params.canteen_id)) || 'north-1'
  return buildWeeklyCalendarMockPayload(String(id))
}

export function matchCampusCanteensApiMock(
  method: string,
  path: string,
): typeof MOCK_NO_MATCH | ReturnType<typeof buildWeeklyCalendarMockPayload>['canteens'] {
  if (method !== 'GET' || path !== '/campus/canteens') return MOCK_NO_MATCH
  return buildWeeklyCalendarMockPayload('north-1').canteens
}
