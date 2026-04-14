import request from './request.js'

/**
 * @param {{ weekMonday: string, canteenId: string }} params
 */
export function fetchAdminWeeklyCalendar(params) {
  return request.get('/admin/campus-weekly-calendar', {
    params,
    skipGlobalMessage: true,
  })
}

/**
 * mealsTemplate：周菜谱模板（与单日 meals 同结构），服务端展开为 7 日 days_json。
 * days：可选完整周数据，一般无需传。
 *
 * @param {{
 *   weekMonday: string
 *   canteenId: string
 *   published: boolean
 *   weekTitle?: string
 *   estimatedPublishNote?: string
 *   mealsTemplate?: Record<string, unknown[]>
 *   days?: unknown[]
 * }} body
 */
export function saveAdminWeeklyCalendar(body) {
  return request.put('/admin/campus-weekly-calendar', body)
}
