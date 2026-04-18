// @ts-nocheck
import { ref, computed } from 'vue'
import { fetchWeeklyCampusMenu } from '@/api/campusCalendar'
import { buildDefaultWeeklySlots, formatWeekRangeLabel } from '@/data/campusWeeklyCalendarSeed'

/**
 * 推荐页：本周校园药膳日历条数据（与通用推荐流解耦，仅管周历接口与展示用 hint）。
 */
export function useRecommendWeeklyMenu() {
  const weeklyMenu = ref({
    published: true,
    weekLabel: '',
    slots: [],
    notice: '',
  })

  async function refreshWeeklyMenu() {
    try {
      const data = await fetchWeeklyCampusMenu()
      weeklyMenu.value = {
        published: data?.published !== false,
        weekLabel: String(data?.weekLabel || ''),
        slots: Array.isArray(data?.slots) ? data.slots : [],
        notice: String(data?.notice || ''),
      }
    } catch {
      weeklyMenu.value = {
        published: true,
        weekLabel: formatWeekRangeLabel(),
        slots: buildDefaultWeeklySlots(),
        notice: '',
      }
    }
  }

  const calendarPublished = computed(() => weeklyMenu.value.published !== false)

  const calendarWeekHint = computed(() => {
    const w = weeklyMenu.value.weekLabel?.trim()
    return w || formatWeekRangeLabel()
  })

  return {
    weeklyMenu,
    refreshWeeklyMenu,
    calendarPublished,
    calendarWeekHint,
  }
}
