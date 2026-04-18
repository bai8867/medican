// @ts-nocheck
/**
 * 本周校园药膳日历 · 演示数据（可与后端 GET /campus/weekly-calendar 对齐）
 */

export const CAMPUS_LOCATION_OPTIONS = [
  { id: 'zone_north', label: '北校区' },
  { id: 'zone_south', label: '南校区' },
  { id: 'canteen_north_1', label: '北一食堂' },
  { id: 'canteen_north_2', label: '北二食堂' },
  { id: 'canteen_south_li', label: '南苑·立人餐厅' },
]

export const ALLERGY_TAG_OPTIONS = [
  { id: 'seafood', label: '海鲜', keywords: ['虾', '蟹', '鱼', '鱿', '鲍', '海鲜', '贝', '蚝', '海带'] },
  { id: 'peanut', label: '花生', keywords: ['花生'] },
  { id: 'dairy', label: '乳制品', keywords: ['牛奶', '奶粉', '奶酪', '芝士', '黄油', '奶油', '酸奶'] },
  { id: 'egg', label: '蛋类', keywords: ['鸡蛋', '蛋清', '蛋黄', '鸭蛋', '鹌鹑蛋'] },
  { id: 'gluten', label: '麸质', keywords: ['小麦', '面筋', '麸皮', '全麦'] },
  { id: 'mutton', label: '羊肉', keywords: ['羊肉', '羊骨'] },
]

export const BUDGET_TIER_OPTIONS = [
  { id: 'economy', label: '经济型' },
  { id: 'regular', label: '常规型' },
  { id: 'unlimited', label: '不限' },
]

function mondayOfWeek(d = new Date()) {
  const x = new Date(d)
  const day = x.getDay()
  const diff = day === 0 ? -6 : 1 - day
  x.setDate(x.getDate() + diff)
  x.setHours(0, 0, 0, 0)
  return x
}

function addDays(d, n) {
  const x = new Date(d)
  x.setDate(x.getDate() + n)
  return x
}

export function formatWeekRangeLabel(d = new Date()) {
  const start = mondayOfWeek(d)
  const end = addDays(start, 6)
  const y = start.getFullYear()
  const m1 = start.getMonth() + 1
  const day1 = start.getDate()
  const m2 = end.getMonth() + 1
  const day2 = end.getDate()
  return `${y}年${m1}月${day1}日 — ${m2}月${day2}日`
}

const WEEKDAYS = ['周一', '周二', '周三', '周四', '周五', '周六', '周日']

export function weekdayLabel(weekday) {
  const n = Number(weekday)
  if (!Number.isFinite(n) || n < 1 || n > 7) return ''
  return WEEKDAYS[n - 1]
}

/** @returns {object[]} */
export function buildDefaultWeeklySlots() {
  /** recipeId 与后端 data.sql `recipe` 表一致，便于跳转详情 */
  return [
    {
      recipeId: '2',
      windowLabel: '北一食堂 · 午市 11:00—13:30',
      locationIds: ['canteen_north_1'],
      priceBand: 'regular',
      weekday: 1,
    },
    {
      recipeId: '9',
      windowLabel: '北二食堂 · 早餐 7:00—9:00',
      locationIds: ['canteen_north_2'],
      priceBand: 'economy',
      weekday: 2,
    },
    {
      recipeId: '10',
      windowLabel: '立人餐厅 · 下午茶 14:00—16:00',
      locationIds: ['canteen_south_li'],
      priceBand: 'regular',
      weekday: 3,
    },
    {
      recipeId: '5',
      windowLabel: '北一食堂 · 晚市 17:00—19:00',
      locationIds: ['canteen_north_1'],
      priceBand: 'economy',
      weekday: 4,
    },
    {
      recipeId: '8',
      windowLabel: '北二食堂 · 午市 11:00—13:00',
      locationIds: ['canteen_north_2'],
      priceBand: 'regular',
      weekday: 5,
    },
  ]
}
