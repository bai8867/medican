import request from './request.js'
import {
  mergeUnpublishedWeeklyCalendarWithMock,
  type WeeklyCalendarPayload,
} from '@/data/buildCampusCalendarMock'

export type { WeeklyCalendarPayload } from '@/data/buildCampusCalendarMock'

function isCampusWeekCalendarForceUnpublished(): boolean {
  const v = import.meta.env.VITE_CAMPUS_WEEK_CALENDAR_PUBLISHED
  return v === '0' || v === 'false' || v === 'FALSE'
}

export async function fetchWeeklyCalendar(canteenId: string): Promise<WeeklyCalendarPayload> {
  const data = await request.get('/campus/weekly-calendar', {
    params: { canteenId: canteenId || 'north-1' },
    skipGlobalMessage: true,
  })
  const raw = data as WeeklyCalendarPayload
  if (isCampusWeekCalendarForceUnpublished()) {
    return raw
  }
  return mergeUnpublishedWeeklyCalendarWithMock(raw, canteenId || 'north-1')
}

export async function fetchCampusCanteens(): Promise<{ id: string; campusName: string; name: string }[]> {
  const data = await request.get('/campus/canteens', { skipGlobalMessage: true })
  return (data || []) as { id: string; campusName: string; name: string }[]
}
