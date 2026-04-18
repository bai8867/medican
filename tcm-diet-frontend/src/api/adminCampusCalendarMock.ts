import type { InternalAxiosRequestConfig } from 'axios'
import type { CalendarDay, CalendarDish, MealSlot } from '@/data/buildCampusCalendarMock'
import { MOCK_NO_MATCH } from './mockTypes'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock'

const MEAL_SLOTS: MealSlot[] = ['breakfast', 'lunch', 'dinner', 'midnightSnack']

export function matchAdminCampusWeeklyCalendarApiMock(
  method: string,
  path: string,
  config: InternalAxiosRequestConfig,
): typeof MOCK_NO_MATCH | Record<string, unknown> | null {
  if (path !== '/admin/campus-weekly-calendar') return MOCK_NO_MATCH
  if (method === 'GET') {
    const weekMonday = (config.params && config.params.weekMonday) || '2000-01-01'
    const canteenId = (config.params && config.params.canteenId) || 'north-1'
    const pub = buildWeeklyCalendarMockPayload(String(canteenId))
    const days: CalendarDay[] = Array.isArray(pub.days) ? pub.days : []
    const meals0 = days[0]?.meals
    const mealsTemplate: Record<MealSlot, Record<string, unknown>[]> = {
      breakfast: [],
      lunch: [],
      dinner: [],
      midnightSnack: [],
    }
    if (meals0 && typeof meals0 === 'object') {
      for (const k of MEAL_SLOTS) {
        const arr = Array.isArray(meals0[k]) ? meals0[k] : []
        mealsTemplate[k] = arr.map((d) => {
          if (!d || typeof d !== 'object') return {}
          const { id: _id, ...rest } = d as CalendarDish
          return rest as Record<string, unknown>
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
