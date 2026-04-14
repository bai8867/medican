import request from './request'
import { MOCK_NO_MATCH } from './mockTypes.js'
import {
  getMockRecipeFavoriteRows,
  isMockRecipeFavorited,
  removeMockRecipeFavoritesByFavoriteIds,
  setMockRecipeFavorited,
} from './mockCampusRecipeFavorites.js'
import { MOCK_RECIPES, EFFICACY_FILTER_ALL } from '@/data/recommendMock.js'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'
import { normalizeIngredientGroups } from '@/utils/recipeIngredientGroups.js'
import { diversifyEfficacyRoundRobin } from '@/utils/recommendEfficacyMix.js'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder.js'

export { normalizeIngredientGroups }

export const DEFAULT_TABOO =
  '实热证、急性炎症期慎用；对所含食材或药材过敏者请勿食用。孕妇、哺乳期妇女及服药人群请先咨询医师。'

export const DEFAULT_DISCLAIMER =
  '本内容仅供健康教育参考，不能替代专业诊疗与用药指导；个体存在差异，请结合自身情况谨慎参考。如有不适请及时就医。'

/** 详情页底部统一合规提示（与 C-03 一致，各药膳详情共用） */
export const RECIPE_DETAIL_FOOTER_LEGAL =
  '本药膳仅作为养生参考，不替代药物治疗；体质特殊者请咨询专业医师后食用'

export function normalizeRecipeDetail(raw) {
  if (!raw) return null
  const r = { ...raw }
  if (typeof r.collectCount !== 'number') {
    const n = Number(r.collectCount)
    r.collectCount = Number.isFinite(n) ? n : 0
  }
  if (
    (!Array.isArray(r.steps) || r.steps.length === 0) &&
    typeof r.stepsJson === 'string' &&
    r.stepsJson.trim().startsWith('{')
  ) {
    try {
      const extra = JSON.parse(r.stepsJson)
      if (extra && typeof extra === 'object') {
        if (Array.isArray(extra.steps) && extra.steps.length) {
          r.steps = extra.steps.map((s) =>
            typeof s === 'string'
              ? { text: s }
              : { text: String(s.text ?? s.description ?? '').trim(), tip: s.tip },
          )
        }
        if (Array.isArray(extra.ingredients) && extra.ingredients.length) {
          r.ingredients = extra.ingredients
        }
        if (Array.isArray(extra.effectTags) && extra.effectTags.length) {
          r.effectTags = [...extra.effectTags]
        }
        if (Array.isArray(extra.seasonFit) && extra.seasonFit.length) {
          r.seasonFit = [...extra.seasonFit]
        }
        if (Array.isArray(extra.suitConstitutions) && extra.suitConstitutions.length) {
          r.suitConstitutions = [...extra.suitConstitutions]
        }
        for (const k of ['summary', 'effect', 'cookTime', 'difficulty', 'recommendReason']) {
          if (extra[k] != null && extra[k] !== '' && r[k] == null) {
            r[k] = extra[k]
          }
        }
      }
    } catch {
      /* ignore */
    }
  }
  r.steps = Array.isArray(r.steps) ? r.steps : []
  r.ingredientGroups = normalizeIngredientGroups(r)
  if (!r.effectTags?.length && r.effect) {
    r.effectTags = String(r.effect)
      .split(/[、，,]/)
      .map((s) => s.trim())
      .filter(Boolean)
      .slice(0, 6)
  }
  r.suitConstitutions =
    r.suitConstitutions ||
    (r.suitConstitution ? [r.suitConstitution] : []) ||
    []
  r.seasonFit = r.seasonFit || r.suitSeasons || []
  r.taboo = r.taboo || r.tabooReminder || r.contraindication || DEFAULT_TABOO
  r.disclaimer = r.disclaimer || r.detailDisclaimer || DEFAULT_DISCLAIMER
  const coverRaw = r.coverUrl != null ? String(r.coverUrl).trim() : ''
  r.coverUrl = coverRaw || recipeSchematicCoverUrl(r.name || r.title || '药膳')
  return r
}

function unwrapDetail(res) {
  if (!res) return null
  if (res.recipe) return res.recipe
  if (res.detail) return res.detail
  return res
}

/**
 * 药膳列表：后端无 GET /recipes 列表，对齐为推荐流 {@link fetchRecommendFeed}。
 */
export function fetchRecipeList(params = {}) {
  const p = { page: 1, page_size: 20, ...params }
  return fetchRecommendFeed(p)
}

/** 药膳详情（与后端 GET /api/recipes/:id 对齐） */
export function fetchRecipeDetail(id) {
  return request.get(`/recipes/${id}`, { skipGlobalMessage: true })
}

/** 收藏 / 取消收藏（对接后端 POST/DELETE /api/user/favorites） */
export function setRecipeFavorite(id, favorited) {
  const sid = String(id)
  if (favorited) {
    return request.post('/user/favorites', { recipeId: sid }, { skipGlobalMessage: true })
  }
  return request.delete(`/user/favorites/${encodeURIComponent(sid)}`, { skipGlobalMessage: true })
}

/** 详情页「有用/没用」反馈 → POST /api/feedback（通用反馈表） */
export function postRecipeContentFeedback(id, vote) {
  const content = `recipeId=${id} vote=${vote}`
  return request.post('/feedback', { content, source: 'recipe_detail' }, { skipGlobalMessage: true })
}

/** 根据体质推荐 */
export function fetchRecommendByConstitution(constitutionCode, params) {
  return fetchRecommendFeed({
    ...params,
    constitutionCode: constitutionCode,
    personalized: true,
  })
}

/** 将推荐页查询参数映射为 Spring 端 {@code CampusSceneController} 的命名；其余字段后端忽略，Mock 仍可读 */
function buildCampusRecommendFeedParams(raw = {}) {
  const p = { ...raw }
  if (p.constitution != null && p.constitutionCode == null) {
    p.constitutionCode = p.constitution
  }
  if (p.season != null && p.seasonCode == null) {
    p.seasonCode = p.season
  }
  if (p.scene_tag != null && p.sceneTag == null) {
    p.sceneTag = p.scene_tag
  }
  if (p.personalized === '1' || p.personalized === 1) p.personalized = true
  else if (p.personalized === '0' || p.personalized === 0) p.personalized = false
  return p
}

/**
 * 推荐流（对接 GET /api/campus/recipes/recommend-feed）
 * skipGlobalMessage：失败时由页面自行降级 Mock，不弹全局错误
 */
export function fetchRecommendFeed(params) {
  return request.get('/campus/recipes/recommend-feed', {
    params: buildCampusRecommendFeedParams(params),
    skipGlobalMessage: true,
  })
}

/** 本地演示数据（后端未就绪时页面仍可浏览） */
export const DEMO_RECIPES = MOCK_RECIPES

export function getDemoRecipe(id) {
  return getUnifiedRecipeMockStore().find((r) => String(r.id) === String(id)) || null
}

/** 仅 `demo-*` 等演示 id 允许在接口失败时用本地 Mock 详情；数字 id 一律对应后端库表药膳 */
export function shouldUseLocalDemoRecipeDetailFallback(id) {
  const s = String(id ?? '').trim()
  if (!s) return false
  return /^demo-/i.test(s)
}

export { unwrapDetail }

/** 体质 code → 中文名（Mock 筛选与 PRD 体质维度对齐） */
const MOCK_CONSTITUTION_CODE_TO_LABEL = {
  pinghe: '平和质',
  qixu: '气虚质',
  yangxu: '阳虚质',
  yinxu: '阴虚质',
  tanshi: '痰湿质',
  shire: '湿热质',
  xueyu: '血瘀质',
  qiyu: '气郁质',
  tebing: '特禀质',
}

function parseMockBody(data) {
  if (data == null) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return {}
    }
  }
  return data
}

function filterRecipesByConstitutionLabel(label, pool) {
  const src = pool || getUnifiedRecipeMockStore()
  if (!label) return [...src]
  return src.filter((r) => {
    const suits = r.suitConstitutions || []
    return suits.includes(label) || r.suitConstitution === label
  })
}

/** 前台可见：已上架（Mock 与真实后端对齐） */
function campusVisibleRecipes() {
  return getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
}

function seasonOkForMock(recipe, seasonKey) {
  const sf = recipe.seasonFit || []
  if (!seasonKey) return true
  return sf.includes('all') || sf.includes(seasonKey)
}

function filterByEfficacyMock(list, efficacy) {
  if (!efficacy) return list
  return list.filter((r) => {
    const tags = r.effectTags || []
    if (tags.includes(efficacy)) return true
    return String(r.effect || '').includes(efficacy)
  })
}

function matchesConstitutionMock(recipe, label) {
  if (!label) return false
  const suits = recipe.suitConstitutions || []
  return suits.includes(label) || recipe.suitConstitution === label
}

/**
 * @param {object[]} list
 * @param {'collect'|'season'} sortKey
 * @param {string} seasonKey
 * @param {{ constitutionLabel?: string, constitutionPrimary?: boolean }} [opts]
 */
function sortRecommendMockList(list, sortKey, seasonKey, opts = {}) {
  const copy = [...list]
  const { constitutionLabel, constitutionPrimary } = opts
  if (sortKey === 'season') {
    copy.sort((a, b) => {
      const as = seasonOkForMock(a, seasonKey) ? 0 : 1
      const bs = seasonOkForMock(b, seasonKey) ? 0 : 1
      if (as !== bs) return as - bs
      return (Number(b.collectCount) || 0) - (Number(a.collectCount) || 0)
    })
    return copy
  }
  if (constitutionPrimary && constitutionLabel) {
    copy.sort((a, b) => {
      const ma = matchesConstitutionMock(a, constitutionLabel) ? 1 : 0
      const mb = matchesConstitutionMock(b, constitutionLabel) ? 1 : 0
      if (ma !== mb) return mb - ma
      return (Number(b.collectCount) || 0) - (Number(a.collectCount) || 0)
    })
    return copy
  }
  copy.sort((a, b) => (Number(b.collectCount) || 0) - (Number(a.collectCount) || 0))
  return copy
}

/**
 * PRD：推荐流分页数据结构（与 Recommend 页 extractList 一致）
 * @typedef {object} RecipeFeedPage
 * @property {object[]} [records]
 * @property {object[]} [list]
 * @property {object[]} [items]
 */

/** Mock 示例：列表/推荐流一页（字段与 RecipeCard、详情 normalize 对齐） */
export const MOCK_RECIPE_LIST_SHAPE = {
  records: MOCK_RECIPES.slice(0, 3),
}

/** Mock 示例：GET /api/recipes/:id 详情（可为裸对象或 { recipe }） */
export const MOCK_RECIPE_DETAIL_SHAPE = {
  recipe: MOCK_RECIPES[0],
}

/**
 * VITE_USE_MOCK 时由 mockGateway 动态导入调用；勿在 request.js 中静态 import 本文件以免循环依赖。
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 * @returns {unknown|typeof import('./mockTypes.js').MOCK_NO_MATCH|import('./mockTypes.js').MockErrorResult}
 */
export function matchRecipeApiMock(method, path, config) {
  if (method === 'GET' && path === '/recipes') {
    return { records: campusVisibleRecipes().map((r) => ({ ...r })) }
  }

  const detailMatch = path.match(/^\/recipes\/([^/]+)$/)
  if (method === 'GET' && detailMatch) {
    const id = detailMatch[1]
    const row = getUnifiedRecipeMockStore().find((r) => String(r.id) === String(id))
    if (!row || row.status === 'off_shelf') {
      return { mockError: true, code: 404, message: '未找到该菜谱' }
    }
    return { recipe: { ...row, favorited: isMockRecipeFavorited(id) } }
  }

  if (method === 'GET' && path === '/user/favorites') {
    const params = config.params || {}
    const page = Math.max(1, Number(params.page) || 1)
    const pageSize = Math.min(100, Math.max(1, Number(params.page_size) || 10))
    const rows = getMockRecipeFavoriteRows()
    const total = rows.length
    const from = (page - 1) * pageSize
    const slice = rows.slice(from, from + pageSize)
    const records = slice.map((row) => ({
      id: String(row.recipeId),
      name: row.name,
      coverUrl: recipeSchematicCoverUrl(row.name),
      collectCount: 0,
      efficacySummary: row.effect || '',
    }))
    return {
      records,
      total,
      page,
      pageSize,
      hasMore: from + pageSize < total,
    }
  }

  if (method === 'POST' && path === '/user/favorites') {
    const body = parseMockBody(config.data)
    const id = body.recipeId != null ? String(body.recipeId).trim() : ''
    const row = id ? getUnifiedRecipeMockStore().find((r) => String(r.id) === String(id)) : null
    const wasFav = id ? isMockRecipeFavorited(id) : false
    if (id) setMockRecipeFavorited(id, true)
    let count = Number(row?.collectCount) || 0
    if (row && !wasFav) count = Math.max(0, count + 1)
    if (row) row.collectCount = count
    return { ok: true, collectCount: row ? count : undefined, favorited: true }
  }

  const userFavDel = path.match(/^\/user\/favorites\/([^/]+)$/)
  if (method === 'DELETE' && userFavDel) {
    const token = decodeURIComponent(userFavDel[1])
    const row = getUnifiedRecipeMockStore().find((r) => String(r.id) === String(token))
    const wasFav = token ? isMockRecipeFavorited(token) : false
    removeMockRecipeFavoritesByFavoriteIds([token])
    setMockRecipeFavorited(token, false)
    let count = Number(row?.collectCount) || 0
    if (row && wasFav) count = Math.max(0, count - 1)
    if (row) row.collectCount = count
    return { ok: true, collectCount: row ? count : undefined, favorited: false }
  }

  if (method === 'POST' && path === '/feedback') {
    return { ok: true }
  }

  const feedbackMatch = path.match(/^\/recipe\/([^/]+)\/feedback$/)
  if (method === 'POST' && feedbackMatch) {
    return { ok: true }
  }

  const recMatch = path.match(/^\/recipes\/recommend\/([^/]+)$/)
  if (method === 'GET' && recMatch) {
    const code = recMatch[1]
    const label = MOCK_CONSTITUTION_CODE_TO_LABEL[code] || code
    const visible = campusVisibleRecipes()
    const list = filterRecipesByConstitutionLabel(label, visible)
    return { records: list.length ? list : visible.map((r) => ({ ...r })) }
  }

  if (method === 'GET' && (path === '/recipes/recommend-feed' || path === '/campus/recipes/recommend-feed')) {
    const params = config.params || {}
    const season = params.seasonCode || params.season || ''
    const page = Math.max(1, Number(params.page) || 1)
    const pageSize = Math.min(50, Math.max(1, Number(params.page_size) || 12))
    const sortKey = params.sort_by === 'season' ? 'season' : 'collect'
    const rawEfficacy = params.filter_efficacy || params.filterEfficacy || ''
    const efficacy =
      !rawEfficacy || rawEfficacy === EFFICACY_FILTER_ALL ? '' : String(rawEfficacy)
    /** 选「全部功效」时：分页池为全部上架药膳，不按应季子集收窄 */
    const efficacyAll = !efficacy
    const generalOnly =
      params.personalized === '0' ||
      params.personalized === 0 ||
      params.personalized === false
    const hasProfile =
      params.has_profile !== '0' &&
      params.has_profile !== 0 &&
      params.has_profile !== false

    const fullPool = campusVisibleRecipes().map((r) => ({ ...r }))

    const withAll = fullPool.filter((r) => {
      const sf = r.seasonFit || []
      return sf.includes('all')
    })

    let merged = fullPool

    if (!efficacyAll) {
      if (generalOnly) {
        const bySeason = fullPool.filter((r) => seasonOkForMock(r, season))
        merged =
          bySeason.length >= 4 ? bySeason : bySeason.length ? bySeason : withAll.length ? withAll : fullPool
      } else if (!hasProfile) {
        const bySeason = fullPool.filter((r) => seasonOkForMock(r, season))
        merged =
          bySeason.length >= 4 ? bySeason : bySeason.length ? bySeason : withAll.length ? withAll : fullPool
      } else {
        const code = params.constitutionCode || params.constitution || 'pinghe'
        const label = MOCK_CONSTITUTION_CODE_TO_LABEL[code] || code
        let feedPool = filterRecipesByConstitutionLabel(label, fullPool)
        if (feedPool.length < 4) {
          feedPool = fullPool
        }
        const seasonFirst = feedPool.filter((r) => seasonOkForMock(r, season))
        merged = seasonFirst.length >= 4 ? seasonFirst : feedPool
      }
    }

    merged = filterByEfficacyMock(merged, efficacy)
    const rawFc = params.filter_constitution || params.filterConstitution || ''
    const fcCode = typeof rawFc === 'string' ? rawFc.trim() : ''
    let fcLabel = ''
    if (fcCode) {
      fcLabel = MOCK_CONSTITUTION_CODE_TO_LABEL[fcCode] || fcCode
      merged = merged.filter((r) => matchesConstitutionMock(r, fcLabel))
    }

    const rawSceneTag = params.scene_tag || params.sceneTag || ''
    const sceneTag = typeof rawSceneTag === 'string' ? rawSceneTag.trim() : ''
    if (sceneTag) {
      const matchScene = (r) => {
        const tags = r.effectTags || []
        if (tags.some((t) => String(t).includes(sceneTag) || sceneTag.includes(String(t)))) {
          return true
        }
        return String(r.effect || '').includes(sceneTag)
      }
      let narrowed = merged.filter(matchScene)
      if (narrowed.length < 2) {
        narrowed = fullPool.filter(matchScene)
      }
      merged = narrowed.length ? narrowed : merged
    }

    const rawKw = params.keyword || params.q || ''
    const kw = typeof rawKw === 'string' ? rawKw.trim() : ''
    if (kw) {
      const k = kw
      const kLower = k.toLowerCase()
      const textHit = (s) => {
        if (s == null) return false
        const t = String(s)
        return t.includes(k) || t.toLowerCase().includes(kLower)
      }
      const matchKeyword = (r) => {
        if (textHit(r.name)) return true
        if (textHit(r.effect) || textHit(r.efficacySummary) || textHit(r.efficacy_summary)) return true
        const tags = r.effectTags || []
        if (tags.some((t) => textHit(t))) return true
        const csv = [r.efficacyTags, r.efficacy_tags, r.symptomTags, r.symptom_tags]
          .filter(Boolean)
          .join(',')
        if (csv && csv.split(/[,，]/).some((x) => textHit(x.trim()))) return true
        return false
      }
      merged = merged.filter(matchKeyword)
    }

    const constPrimary =
      sortKey === 'collect' && hasProfile && !generalOnly
    const codeForSort = params.constitution || 'pinghe'
    const labelForProfile = MOCK_CONSTITUTION_CODE_TO_LABEL[codeForSort] || codeForSort
    const labelForSort = fcLabel || labelForProfile
    merged = sortRecommendMockList(merged, sortKey, season, {
      constitutionLabel: labelForSort,
      constitutionPrimary: constPrimary,
    })
    /** 按收藏量排序时不做功效轮询，否则会打乱 collect 序 */
    if (!efficacy && sortKey !== 'collect') {
      merged = diversifyEfficacyRoundRobin(merged)
    }

    const start = (page - 1) * pageSize
    const slice = merged.slice(start, start + pageSize)
    const hasMore = start + slice.length < merged.length

    return { records: slice.map((r) => ({ ...r })), hasMore }
  }

  return MOCK_NO_MATCH
}
