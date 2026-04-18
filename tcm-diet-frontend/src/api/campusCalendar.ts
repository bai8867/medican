import request from './request'
import { MOCK_NO_MATCH } from './mockTypes'
import { buildDefaultWeeklySlots, formatWeekRangeLabel } from '@/data/campusWeeklyCalendarSeed'
import {
  mergeUnpublishedWeeklyCalendarWithMock,
  type WeeklyCalendarPayload,
} from '@/data/buildCampusCalendarMock'
import { flattenWeeklyCalendarPayload } from '@/utils/sceneCalendarBinding'

export type { WeeklyCalendarPayload } from '@/data/buildCampusCalendarMock'

/** 与历史 JS 载荷及接口演进字段兼容 */
type WeeklyCalendarPayloadLike = WeeklyCalendarPayload & { weekLabel?: string; notice?: string }

function isCampusWeekCalendarForceUnpublished(): boolean {
  const v = import.meta.env.VITE_CAMPUS_WEEK_CALENDAR_PUBLISHED
  return v === '0' || v === 'false' || v === 'FALSE'
}

export type CampusCanteenOption = { id: string; campusName: string; name: string }

export type CampusWeeklySlot = {
  recipeId: string
  windowLabel: string
  locationIds: string[]
  priceBand?: 'economy' | 'regular' | 'luxury'
  weekday?: number
}

export type WeeklyCampusHomeMenu = {
  published: boolean
  weekLabel: string
  notice: string
  slots: CampusWeeklySlot[]
}

function weeklyCalendarPayloadToHomeMenu(payload: WeeklyCalendarPayloadLike): WeeklyCampusHomeMenu {
  const published = payload?.published !== false
  const weekLabel = String(payload?.weekTitle || payload?.weekLabel || '')
  const notice = String(payload?.estimatedPublishNote || payload?.notice || '')
  if (!published || !Array.isArray(payload?.days)) {
    return { published, weekLabel, notice, slots: [] }
  }
  const flat = flattenWeeklyCalendarPayload(payload) as Array<
    Record<string, unknown> & {
      stopped?: boolean
      recipeId?: string
      dow?: number
      dowLabel?: string
      mealLabel?: string
      calendarLine?: string
    }
  >
  const slots = flat
    .filter((r) => !r.stopped)
    .map((r) => ({
      recipeId: String(r.recipeId),
      windowLabel: [r.dowLabel, r.mealLabel, r.calendarLine].filter(Boolean).join(' · '),
      locationIds: [] as string[],
      priceBand: 'regular' as const,
      weekday: r.dow,
    }))
  return { published, weekLabel, notice, slots }
}

export async function fetchWeeklyCalendar(canteenId: string): Promise<WeeklyCalendarPayload> {
  const raw = await request.get<WeeklyCalendarPayload>('/campus/weekly-calendar', {
    params: { canteenId: canteenId || 'north-1' },
    skipGlobalMessage: true,
  })
  if (isCampusWeekCalendarForceUnpublished()) {
    return raw
  }
  return mergeUnpublishedWeeklyCalendarWithMock(raw, canteenId || 'north-1')
}

export async function fetchCampusCanteens(): Promise<CampusCanteenOption[]> {
  const data = await request.get<CampusCanteenOption[]>('/campus/canteens', { skipGlobalMessage: true })
  return Array.isArray(data) ? data : []
}

/** 推荐页「本周可点」简表（固定 north-1） */
export async function fetchWeeklyCampusMenu(): Promise<WeeklyCampusHomeMenu> {
  const data = (await request.get<WeeklyCalendarPayload>('/campus/weekly-calendar', {
    params: { canteenId: 'north-1' },
    skipGlobalMessage: true,
  })) as WeeklyCalendarPayload
  let payload: WeeklyCalendarPayload = data || ({} as WeeklyCalendarPayload)
  if (!isCampusWeekCalendarForceUnpublished()) {
    payload = mergeUnpublishedWeeklyCalendarWithMock(payload, 'north-1')
  }
  return weeklyCalendarPayloadToHomeMenu(payload as WeeklyCalendarPayloadLike)
}

/**
 * Mock：旧路径 `/campus/weekly-menu`；`localStorage` `tcm_demo_weekly_menu_unpublished` = `1` 模拟未发布。
 */
export function matchWeeklyMenuApiMock(method: string, path: string): typeof MOCK_NO_MATCH | Record<string, unknown> {
  if (method === 'GET' && path === '/campus/weekly-menu') {
    let unpublished = false
    try {
      unpublished = localStorage.getItem('tcm_demo_weekly_menu_unpublished') === '1'
    } catch {
      unpublished = false
    }
    if (unpublished) {
      return {
        published: false,
        weekLabel: '',
        slots: [],
        notice: '本周校园药膳日历尚未发布，敬请期待。食堂确定排期后将第一时间上线。',
      }
    }
    return {
      published: true,
      weekLabel: formatWeekRangeLabel(new Date()),
      slots: buildDefaultWeeklySlots(),
    }
  }
  return MOCK_NO_MATCH
}
