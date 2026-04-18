import request from './request'

export interface AdminWeeklyCalendarQuery {
  weekMonday: string
  canteenId: string
}

export function fetchAdminWeeklyCalendar(params: AdminWeeklyCalendarQuery) {
  return request.get('/admin/campus-weekly-calendar', {
    params,
    skipGlobalMessage: true,
  })
}

/** mealsTemplate：周菜谱模板（与单日 meals 同结构），服务端展开为 7 日 days_json。days：可选完整周数据，一般无需传。 */
export interface SaveAdminWeeklyCalendarBody {
  weekMonday: string
  canteenId: string
  published: boolean
  weekTitle?: string
  estimatedPublishNote?: string
  mealsTemplate?: Record<string, unknown[]>
  days?: unknown[]
}

export function saveAdminWeeklyCalendar(body: SaveAdminWeeklyCalendarBody) {
  return request.put('/admin/campus-weekly-calendar', body)
}
