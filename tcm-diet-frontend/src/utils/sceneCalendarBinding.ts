// @ts-nocheck
import { scoreRecipeForScene } from '@/data/sceneTherapySeed'
import { buildWeeklyCalendarMockPayload } from '@/data/buildCampusCalendarMock.ts'
import { fetchWeeklyCalendar } from '@/api/campusCalendar.ts'
import {
  formatYmd,
  getWeekMeta,
  timeBucketsToMealKeys,
} from '@/utils/campusWeekCalendar'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder'

/**
 * 后端 `recipe` 主键（周历 / data.sql）→ 推荐 Mock 药膳 id，仅用于场景页「本周日历」联动打分；详情页仍用日历里的真实 id 请求接口。
 * @type {Record<string, string>}
 */
const CALENDAR_DB_RECIPE_ID_TO_MOCK_ID = {
  1: 'demo-005',
  2: 'demo-001',
  3: 'demo-007',
  4: 'demo-003',
  5: 'demo-006',
  6: 'demo-007',
  7: 'demo-007',
  8: 'demo-005',
  9: 'demo-002',
  10: 'demo-003',
}

/**
 * @param {object[]} store
 */
function recipeByIdMap(store) {
  const m = new Map()
  for (const r of store) {
    m.set(String(r.id), r)
  }
  for (const [dbId, mockId] of Object.entries(CALENDAR_DB_RECIPE_ID_TO_MOCK_ID)) {
    if (m.has(dbId)) continue
    const r = m.get(mockId)
    if (r) m.set(dbId, r)
  }
  return m
}

export function isCampusWeekCalendarForceUnpublished() {
  const v = import.meta.env.VITE_CAMPUS_WEEK_CALENDAR_PUBLISHED
  return v === '0' || v === 'false' || v === 'FALSE'
}

/**
 * 跳转药膳详情路由用的 id：已是数字则原样返回；若为历史周历里的 demo-*，则映射到库表 recipe.id（与后端 data.sql / 详情接口一致）。
 * @param {string|number|undefined|null} rawId
 * @returns {string}
 */
export function resolveRecipeDetailNavId(rawId) {
  const s = String(rawId ?? '').trim()
  if (!s) return s
  if (/^\d+$/.test(s)) return s
  for (const [dbId, mockId] of Object.entries(CALENDAR_DB_RECIPE_ID_TO_MOCK_ID)) {
    if (String(mockId) === s) return String(dbId)
  }
  return s
}

/**
 * 将 /campus/weekly-calendar Mock 载荷摊平为「餐段 × 菜品」行，供场景页与详情页统一消费。
 * @param {import('@/data/buildCampusCalendarMock').WeeklyCalendarPayload} payload
 */
export function flattenWeeklyCalendarPayload(payload) {
  if (!payload?.published || !Array.isArray(payload.days)) return []
  /** @type {import('@/data/buildCampusCalendarMock').MealSlot[]} */
  const MEAL_KEYS = ['breakfast', 'lunch', 'dinner', 'midnightSnack']
  const MEAL_LABEL_FULL = {
    breakfast: '早餐',
    lunch: '午餐',
    dinner: '晚餐',
    midnightSnack: '宵夜',
  }
  const out = []
  for (const day of payload.days) {
    const dow = (day.weekdayIndex ?? 0) + 1
    for (const meal of MEAL_KEYS) {
      const dishes = day.meals?.[meal] || []
      let idx = 0
      for (const dish of dishes) {
        idx += 1
        out.push({
          dow,
          dowLabel: day.weekdayLabel,
          meal,
          mealLabel: MEAL_LABEL_FULL[meal] || meal,
          dateStr: day.date,
          recipeId: String(dish.recipeId),
          stopped: !!dish.stopped,
          stopReason: dish.stopReason || '',
          calendarLine: `${dish.window} · ${dish.supplyTimeLabel}`,
          calendarDishName: dish.name,
          dishInstanceKey: `${day.date}-${meal}-${idx}`,
        })
      }
    }
  }
  return out
}

function emptyResolved(anchor, metaTitle) {
  const { monday, sunday, weekId, weekTitle } = getWeekMeta(anchor)
  return {
    published: false,
    weekId,
    weekTitle: metaTitle || weekTitle,
    mondayYmd: formatYmd(monday),
    sundayYmd: formatYmd(sunday),
    slots: [],
  }
}

/**
 * 与后端 GET /api/campus/weekly-calendar 对齐；失败时回退为本地 Mock。
 * @param {Date} [anchor]
 */
export async function fetchResolvedWeeklyCalendar(anchor = new Date()) {
  const { monday, sunday, weekId, weekTitle: metaTitle } = getWeekMeta(anchor)
  if (isCampusWeekCalendarForceUnpublished()) {
    return emptyResolved(anchor, metaTitle)
  }
  try {
    const payload = await fetchWeeklyCalendar('north-1')
    if (!payload.published) {
      return {
        published: false,
        weekId,
        weekTitle: payload.weekTitle || metaTitle,
        mondayYmd: formatYmd(monday),
        sundayYmd: formatYmd(sunday),
        slots: [],
      }
    }
    const slots = flattenWeeklyCalendarPayload(payload)
    return {
      published: true,
      weekId,
      weekTitle: payload.weekTitle || metaTitle,
      mondayYmd: formatYmd(monday),
      sundayYmd: formatYmd(sunday),
      slots,
    }
  } catch {
    return buildResolvedWeeklyCalendar(anchor)
  }
}

/**
 * 同步版（仅 Mock / 回退）；场景页请优先使用 {@link fetchResolvedWeeklyCalendar}。
 * @param {Date} [anchor]
 */
export function buildResolvedWeeklyCalendar(anchor = new Date()) {
  const { monday, sunday, weekId, weekTitle: metaTitle } = getWeekMeta(anchor)
  if (isCampusWeekCalendarForceUnpublished()) {
    return emptyResolved(anchor, metaTitle)
  }
  const payload = buildWeeklyCalendarMockPayload('north-1', anchor)
  if (!payload.published) {
    return {
      published: false,
      weekId,
      weekTitle: payload.weekTitle || metaTitle,
      mondayYmd: formatYmd(monday),
      sundayYmd: formatYmd(sunday),
      slots: [],
    }
  }
  const slots = flattenWeeklyCalendarPayload(payload)
  return {
    published: true,
    weekId,
    weekTitle: payload.weekTitle || metaTitle,
    mondayYmd: formatYmd(monday),
    sundayYmd: formatYmd(sunday),
    slots,
  }
}

/**
 * 本周日历中与场景方向有匹配分的不同菜品数量
 * @param {import('@/data/sceneTherapySeed').SceneTherapySeed} scene
 * @param {{ slots: { recipeId: string }[] }} calendar
 * @param {object[]} store
 */
export function countDistinctSceneMatchesInWeek(scene, calendar, store) {
  if (!calendar?.published || !scene || !calendar.slots?.length) return 0
  const map = recipeByIdMap(store)
  const ids = new Set()
  for (const s of calendar.slots) {
    const r = map.get(String(s.recipeId))
    if (!r || r.status === 'off_shelf') continue
    const { score } = scoreRecipeForScene(r, scene)
    if (score > 0) ids.add(String(s.recipeId))
  }
  return ids.size
}

const MEAL_ORDER = { breakfast: 0, lunch: 1, dinner: 2, midnightSnack: 3 }

function sortSlotsChronological(slots) {
  return [...slots].sort(
    (a, b) =>
      String(a.dateStr).localeCompare(String(b.dateStr)) ||
      MEAL_ORDER[a.meal] - MEAL_ORDER[b.meal],
  )
}

/**
 * @param {import('@/data/sceneTherapySeed').SceneTherapySeed} scene
 * @param {ReturnType<typeof buildResolvedWeeklyCalendar>} calendar
 * @param {object[]} store
 * @param {'morning'|'noon'|'afternoon'|'evening'|'night'} timeBucket
 */
export function buildSceneCalendarPresentation(scene, calendar, store, timeBucket) {
  const empty = {
    todayPrimary: [],
    todayFallbackNote: '',
    weekPath: [],
    avoidList: [],
    aiNarrative: '',
    totalMatchDishes: 0,
  }
  if (!scene || !calendar?.published) return empty

  const map = recipeByIdMap(store)
  const slots = calendar.slots || []
  const scored = sortSlotsChronological(slots).map((slot) => {
    const r = map.get(String(slot.recipeId))
    const { score, matched } = r ? scoreRecipeForScene(r, scene) : { score: 0, matched: [] }
    return { slot, r, score, matched }
  })

  const totalMatchDishes = countDistinctSceneMatchesInWeek(scene, calendar, store)

  const todayYmd = formatYmd(new Date())
  const mealKeys = timeBucketsToMealKeys(timeBucket)
  const todayRows = scored.filter(
    (x) => x.slot.dateStr === todayYmd && mealKeys.includes(x.slot.meal) && x.r,
  )
  todayRows.sort((a, b) => {
    const sa = a.slot.stopped ? 1 : 0
    const sb = b.slot.stopped ? 1 : 0
    if (sa !== sb) return sa - sb
    return b.score - a.score
  })

  let primary = todayRows
    .filter((x) => x.score > 0)
    .slice(0, 3)
    .map((x) => toDishCard(x, scene))
    .filter(Boolean)

  let todayFallbackNote = ''
  if (!primary.length) {
    const pool = [...todayRows, ...scored].filter((x) => x.r)
    pool.sort((a, b) => {
      const sa = a.slot.stopped ? 1 : 0
      const sb = b.slot.stopped ? 1 : 0
      if (sa !== sb) return sa - sb
      return b.score - a.score
    })
    const best = pool[0]
    if (best?.r) {
      const card = toDishCard(best, scene)
      primary = card ? [card] : []
      todayFallbackNote =
        best.score > 0
          ? '今日餐段暂无满分匹配，下面是从本周日历里为你挑的最接近选项。'
          : '今日暂无完美匹配，下面是从本周日历里相对最接近的一道，可作温和参考。'
    }
  }

  /** 本周组合路径：优先高分且覆盖不同餐段实例 */
  const path = []
  const usedKey = new Set()
  for (const x of scored.filter((r) => r.score >= 4 && r.r)) {
    const k = x.slot.dishInstanceKey || `${x.slot.dateStr}-${x.slot.meal}-${x.slot.recipeId}`
    if (usedKey.has(k)) continue
    path.push(toPathItem(x))
    usedKey.add(k)
    if (path.length >= 8) break
  }
  if (path.length < 5) {
    for (const x of scored) {
      if (x.score < 1 || !x.r) continue
      const k = x.slot.dishInstanceKey || `${x.slot.dateStr}-${x.slot.meal}-${x.slot.recipeId}`
      if (usedKey.has(k)) continue
      path.push(toPathItem(x))
      usedKey.add(k)
      if (path.length >= 8) break
    }
  }
  if (path.length < 4) {
    for (const x of scored) {
      if (!x.r) continue
      const k = x.slot.dishInstanceKey || `${x.slot.dateStr}-${x.slot.meal}-${x.slot.recipeId}`
      if (usedKey.has(k)) continue
      path.push(toPathItem(x))
      usedKey.add(k)
      if (path.length >= 7) break
    }
  }

  /** 尽量少选：先取分数偏低者；若整体都高，则取相对最低 */
  const byRecipe = new Map()
  for (const x of scored) {
    if (!x.r) continue
    const rid = String(x.slot.recipeId)
    const prev = byRecipe.get(rid)
    if (!prev || x.score < prev.minScore) {
      byRecipe.set(rid, { r: x.r, minScore: x.score, slot: x.slot })
    }
  }
  const uniq = [...byRecipe.values()].sort((a, b) => a.minScore - b.minScore)
  let avoid = uniq.slice(0, 2).filter((x) => x.minScore < 10)
  if (avoid.length < 1 && uniq.length) {
    avoid = uniq.slice(0, Math.min(2, uniq.length))
  }
  const avoidList = avoid.map((x) => ({
    id: String(x.slot.recipeId),
    name: x.slot.calendarDishName || x.r.name,
    reason:
      x.minScore <= 0
        ? '与当前场景痛点、功效方向的直接重合较少，本周若该场景困扰明显，建议不作为主力选择。'
        : x.minScore < 6
          ? '有一定帮助，但相较本周日历里更贴合的选项，优先级可以往后放。'
          : '在本周日历供给里属于相对不那么贴该场景重点的一道，可减少频率、搭配更对症的菜品一起。',
  }))

  const aiNarrative = buildAiNarrative(scene.name, {
    totalMatchDishes,
    primaryLen: primary.length,
    pathLen: path.length,
    avoidLen: avoidList.length,
  })

  return {
    todayPrimary: primary,
    todayFallbackNote,
    weekPath: path,
    avoidList,
    aiNarrative,
    totalMatchDishes,
  }
}

/**
 * @param {import('@/data/sceneTherapySeed').SceneTherapySeed} scene
 */
function toDishCard(x, scene) {
  if (!x.r) return null
  const painBits =
    x.matched?.length ? x.matched : (scene.painTags || []).slice(0, 2)
  const displayName = x.slot.calendarDishName || x.r.name
  return {
    /** 与日历 / 库表一致，避免 recipeByIdMap 复用 Mock 对象时 id 仍为 demo-* */
    id: String(x.slot.recipeId),
    name: displayName,
    coverUrl: recipeSchematicCoverUrl(displayName),
    efficacySummary: x.r.summary || x.r.effect || '',
    slotLabel: `${x.slot.dowLabel}${x.slot.mealLabel}`,
    calendarLine: x.slot.calendarLine || '',
    stopped: !!x.slot.stopped,
    stopReason: x.slot.stopReason || '',
    score: x.score,
    whyFit:
      x.score > 0
        ? `与「${painBits.join('、')}」及场景调养方向较为贴近（基于本周日历菜品）。`
        : '与场景的直接重合有限，作为今日餐段的温和备选。',
  }
}

function toPathItem(x) {
  const displayName = x.slot.calendarDishName || x.r.name
  return {
    id: String(x.slot.recipeId),
    name: displayName,
    dowLabel: x.slot.dowLabel,
    mealLabel: x.slot.mealLabel,
    dateStr: x.slot.dateStr,
    lineTitle: `${x.slot.dowLabel} ${x.slot.mealLabel}`,
    calendarLine: x.slot.calendarLine || '',
    score: x.score,
  }
}

function buildAiNarrative(
  sceneName,
  { totalMatchDishes, primaryLen, pathLen, avoidLen },
) {
  return `亲爱的同学，「${sceneName}」这一周我对照了本周校园药膳日历：全周约有 ${totalMatchDishes} 道与你的场景方向明显呼应；今日优先展示 ${primaryLen} 道，时间轴上串联了 ${pathLen} 个重点餐段，尽量少选区列出 ${avoidLen} 道相对不那么优先的供你灵活取舍——排序与组合都是为了让「先吃对一口」变简单，而不是增加选择焦虑。内容仅供养生参考，不适请就医。`
}

/**
 * 供列表页批量计数（同一周、同一菜谱池）
 * @param {import('@/data/sceneTherapySeed').SceneTherapySeed[]} sceneSeeds
 */
export function buildSceneWeekMatchCounts(sceneSeeds, calendar, store) {
  const map = {}
  for (const s of sceneSeeds) {
    map[s.id] = countDistinctSceneMatchesInWeek(s, calendar, store)
  }
  return map
}
