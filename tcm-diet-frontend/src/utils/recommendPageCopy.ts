// @ts-nocheck
import { filterByConstitution, filterByEffect } from '../composables/useRecommendFeedFilters'
import { recipeViolatesAllergies } from './campusMealPreferences'

/** 无画像时的默认体质码（与 stores 约定一致） */
export const RECOMMEND_DEFAULT_CONSTITUTION_CODE = 'pinghe'

/** 无 store 命中时的默认体质展示名 */
export const RECOMMEND_DEFAULT_CONSTITUTION_LABEL = '平和质'

/** 筛选面板「全部体质」选项文案 */
export const RECOMMEND_CONSTITUTION_FILTER_ALL_LABEL = '全部体质'

export const RECOMMEND_TAG_CONSTITUTION_PREFIX = '体质 ·'
export const RECOMMEND_TAG_SEASON_PREFIX = '季节 ·'
export const RECOMMEND_BTN_EDIT_CONSTITUTION = '修改'

/** 场景条：关键词前后的静态片段（关键词在模板中加粗） */
export function buildRecommendSceneStripAroundKeyword({ sceneLabelQuery }) {
  const labelPart = sceneLabelQuery ? `「${sceneLabelQuery}」` : ''
  return {
    beforeKeyword: `当前按场景${labelPart}，以功效关键词「`,
    afterKeyword: '」收窄药膳列表。',
  }
}

export const RECOMMEND_SCENE_STRIP_NO_PROFILE_TAIL =
  '您尚未完成体质测评，仍可先浏览；测评后推荐会更贴合个人。'

export const RECOMMEND_SCENE_CLEAR = '清除场景筛选'

export const RECOMMEND_COLD_START_CTA = '完成体质测评，获得更精准的推荐 →'

/**
 * @param {number} calendarFitCount
 */
export function buildRecommendCalendarAriaLabel(calendarFitCount) {
  const n = Number(calendarFitCount) || 0
  return `打开本周药膳日历，当前匹配 ${n} 道菜`
}

export const RECOMMEND_CALENDAR_KICKER = '本周药膳日历'

/**
 * @param {number} calendarFitCount
 */
export function buildRecommendCalendarEntryTitle(calendarFitCount) {
  const n = Number(calendarFitCount) || 0
  return `适合你的 ${n} 道菜`
}

/**
 * @param {string} calendarWeekHint
 */
export function buildRecommendCalendarEntrySub(calendarWeekHint) {
  const h = String(calendarWeekHint || '').trim()
  return `${h} · 点按应用个人筛选`
}

export const RECOMMEND_CALENDAR_UNPUBLISHED_TITLE = '本周校园药膳日历尚未发布'

export const RECOMMEND_CALENDAR_UNPUBLISHED_DEFAULT_NOTICE =
  '食堂排期确认后将上线「本周可点」推荐；您仍可浏览下方通用养生推荐。'

export const RECOMMEND_LOAD_MORE_LOADING = '加载中…'
export const RECOMMEND_LOAD_MORE_HINT = '上滑加载更多…'

/** 体质 → 推荐文案侧重点（避免写死「润肺益气」） */
export const RECOMMEND_CONSTITUTION_FOCUS_BY_CODE = {
  pinghe: '平和调养、顺应时令',
  qixu: '益气健脾、固表补虚',
  yangxu: '温阳散寒、温补气血',
  yinxu: '养阴润燥、生津清热',
  tanshi: '健脾祛湿、化痰利水',
  shire: '清热利湿、和中化浊',
  xueyu: '活血祛瘀、通络养血',
  qiyu: '疏肝理气、解郁安神',
  tebing: '益气固表、调和体质',
}

/**
 * @param {Date} [d]
 * @returns {{ key: 'spring'|'summer'|'autumn'|'winter', label: string }}
 */
export function recommendSeasonFromDate(d = new Date()) {
  const m = d.getMonth() + 1
  if (m >= 3 && m <= 5) return { key: 'spring', label: '春季' }
  if (m >= 6 && m <= 8) return { key: 'summer', label: '夏季' }
  if (m >= 9 && m <= 11) return { key: 'autumn', label: '秋季' }
  return { key: 'winter', label: '冬季' }
}

/**
 * @param {string} code
 */
export function recommendConstitutionFocusPhrase(code) {
  const c = String(code || RECOMMEND_DEFAULT_CONSTITUTION_CODE)
  return (
    RECOMMEND_CONSTITUTION_FOCUS_BY_CODE[c] ||
    RECOMMEND_CONSTITUTION_FOCUS_BY_CODE[RECOMMEND_DEFAULT_CONSTITUTION_CODE]
  )
}

export function buildRecommendEmptyListHint(keywordTrimmed) {
  const kw = String(keywordTrimmed || '').trim()
  if (kw) {
    return `没有找到与「${kw}」匹配的药膳，可尝试其他关键词或清空搜索`
  }
  return '当前筛选下暂无菜谱，试试更换功效或体质筛选'
}

/**
 * @param {{
 *   constitutionLabel: string
 *   seasonLabel: string
 *   focusPhrase: string
 *   personalizedRecommendEnabled: boolean
 *   hasProfile: boolean
 *   efficacyIsAll: boolean
 * }} ctx
 */
export function buildRecommendReasonCardText(ctx) {
  const c = ctx.constitutionLabel
  const s = ctx.seasonLabel
  const focus = ctx.focusPhrase
  if (!ctx.personalizedRecommendEnabled) {
    if (ctx.efficacyIsAll) {
      return `您已关闭个性化推荐。当前为「全部功效」，正展示全部可见药膳（可按收藏量或应季排序）。可在「我的」中重新开启个性化。`
    }
    return `您已关闭个性化推荐。当前仅按「${s}」展示应季与四季通用药膳，不再根据体质匹配排序。可在「我的」中重新开启。`
  }
  if (!ctx.hasProfile) {
    if (ctx.efficacyIsAll) {
      return `您尚未完成体质测评。当前为「全部功效」，展示全部可见药膳；选择具体功效或完成测评后，列表会进一步聚焦。`
    }
    return `您尚未完成体质测评，系统暂按「${c}」与「${s}」气候为您组合推荐；完成测评后可获得更精准的药膳搭配。`
  }
  return `根据您的${c}体质和${s}气候特点，为您筛选兼顾「${focus}」与应季平衡的药膳方案。`
}

/**
 * @param {{
 *   constitutionLabel: string
 *   seasonLabel: string
 *   focusPhrase: string
 *   personalizedRecommendEnabled: boolean
 *   hasProfile: boolean
 *   efficacyIsAll: boolean
 * }} ctx
 */
export function buildRecommendHeadlineReason(ctx) {
  const c = ctx.constitutionLabel
  const s = ctx.seasonLabel
  const focus = ctx.focusPhrase
  if (!ctx.personalizedRecommendEnabled) {
    if (ctx.efficacyIsAll) {
      return '全部功效 · 全部可见药膳'
    }
    return `应季通用推荐（${s}）`
  }
  if (!ctx.hasProfile) {
    if (ctx.efficacyIsAll) {
      return '全部功效 · 浏览全部可见药膳'
    }
    return `根据「${c}」与「${s}」气候为您推荐以下药膳（完成测评可进一步提高匹配度）`
  }
  return `根据您的${c}体质和${s}气候，推荐以下${focus.split('、')[0]}等的药膳`
}

/**
 * 有关键词且功效/体质收窄为空、已回退展示全部关键词命中时，用于提示条
 */
export function computeRecipeSearchFallbackToKeywordOnly({
  keywordTrimmed,
  poolRows,
  allergyTags,
  effectFilterValue,
  constitutionFilterValue,
}) {
  const kw = String(keywordTrimmed || '').trim()
  if (!kw) return false
  let base = poolRows
  const tags = allergyTags || []
  const allergyOk = base.filter((r) => !recipeViolatesAllergies(r, tags))
  if (allergyOk.length) base = allergyOk
  const narrowed = filterByConstitution(
    filterByEffect(base, effectFilterValue),
    constitutionFilterValue,
  )
  return !narrowed.length && base.length > 0
}
