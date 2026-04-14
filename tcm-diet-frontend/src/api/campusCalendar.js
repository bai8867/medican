import request from './request.js'
import { MOCK_NO_MATCH } from './mockTypes.js'
import { buildDefaultWeeklySlots, formatWeekRangeLabel } from '@/data/campusWeeklyCalendarSeed.js'
import { mergeUnpublishedWeeklyCalendarWithMock } from '@/data/buildCampusCalendarMock.ts'
import { flattenWeeklyCalendarPayload } from '@/utils/sceneCalendarBinding.js'

function isCampusWeekCalendarForceUnpublished() {
  const v = import.meta.env.VITE_CAMPUS_WEEK_CALENDAR_PUBLISHED
  return v === '0' || v === 'false' || v === 'FALSE'
}

/**
 * @typedef {object} CampusWeeklySlot
 * @property {string} recipeId
 * @property {string} windowLabel
 * @property {string[]} locationIds
 * @property {'economy'|'regular'|'luxury'} [priceBand]
 * @property {number} [weekday] 1-7 周一到周日
 */

/**
 * 将后端 GET /api/campus/weekly-calendar 载荷转为首页「本周可点」用的简化结构。
 * @param {import('@/data/buildCampusCalendarMock').WeeklyCalendarPayload} payload
 * @returns {{ published: boolean, weekLabel: string, notice: string, slots: CampusWeeklySlot[] }}
 */
function weeklyCalendarPayloadToHomeMenu(payload) {
  const published = payload?.published !== false
  const weekLabel = String(payload?.weekTitle || payload?.weekLabel || '')
  const notice = String(payload?.estimatedPublishNote || payload?.notice || '')
  if (!published || !Array.isArray(payload?.days)) {
    return { published, weekLabel, notice, slots: [] }
  }
  const flat = flattenWeeklyCalendarPayload(payload)
  const slots = flat
    .filter((r) => !r.stopped)
    .map((r) => ({
      recipeId: String(r.recipeId),
      windowLabel: [r.dowLabel, r.mealLabel, r.calendarLine].filter(Boolean).join(' · '),
      locationIds: [],
      priceBand: 'regular',
      weekday: r.dow,
    }))
  return { published, weekLabel, notice, slots }
}

/**
 * @returns {Promise<{ published: boolean, weekLabel?: string, notice?: string, slots: CampusWeeklySlot[] }>}
 */
export async function fetchWeeklyCampusMenu() {
  const data = await request.get('/campus/weekly-calendar', {
    params: { canteenId: 'north-1' },
    skipGlobalMessage: true,
  })
  let payload = data || {}
  if (!isCampusWeekCalendarForceUnpublished()) {
    payload = mergeUnpublishedWeeklyCalendarWithMock(payload, 'north-1')
  }
  return weeklyCalendarPayloadToHomeMenu(payload)
}

/**
 * Mock：设置 localStorage `tcm_demo_weekly_menu_unpublished` = `1` 可模拟「本周未发布」
 * @param {string} method
 * @param {string} path
 * @returns {unknown|typeof MOCK_NO_MATCH}
 */
export function matchWeeklyMenuApiMock(method, path) {
  /** 仅旧路径：全站 Mock 时首页「本周菜单」演示；与 /campus/weekly-calendar 主 Mock 分离 */
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
