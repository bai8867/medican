/** 周一为 1，周日为 7 */
export function getChinaWeekday(d = new Date()) {
  const n = d.getDay()
  return n === 0 ? 7 : n
}

/** @param {Date} d */
export function startOfWeekMonday(d) {
  const x = new Date(d)
  x.setHours(12, 0, 0, 0)
  const c = getChinaWeekday(x)
  x.setDate(x.getDate() - (c - 1))
  x.setHours(0, 0, 0, 0)
  return x
}

/** @param {Date} d */
export function formatYmd(d) {
  const y = d.getFullYear()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${y}-${m}-${day}`
}

/**
 * @param {Date} [anchor]
 * @returns {{ monday: Date, weekId: string, weekTitle: string, sunday: Date }}
 */
export function getWeekMeta(anchor = new Date()) {
  const monday = startOfWeekMonday(anchor)
  const sunday = new Date(monday)
  sunday.setDate(monday.getDate() + 6)
  const weekId = `${formatYmd(monday)}_${formatYmd(sunday)}`
  const weekTitle = `本周菜单（${formatYmd(monday)}～${formatYmd(sunday)}）`
  return { monday, sunday, weekId, weekTitle }
}

export const DOW_LABEL = {
  1: '周一',
  2: '周二',
  3: '周三',
  4: '周四',
  5: '周五',
  6: '周六',
  7: '周日',
}

export const MEAL_LABEL = {
  breakfast: '早餐',
  lunch: '午餐',
  dinner: '晚餐',
  midnightSnack: '宵夜',
}

/** @typedef {'morning'|'noon'|'afternoon'|'evening'|'night'} TimeBucket */
/**
 * @param {TimeBucket} bucket
 * @returns {('breakfast'|'lunch'|'dinner'|'midnightSnack')[]}
 */
export function timeBucketsToMealKeys(bucket) {
  if (bucket === 'morning') return ['breakfast']
  if (bucket === 'noon' || bucket === 'afternoon') return ['lunch']
  if (bucket === 'evening') return ['dinner']
  return ['dinner', 'midnightSnack']
}
