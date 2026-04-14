<script setup>
import { ref, computed, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { Right, Search } from '@element-plus/icons-vue'
import RecipeCard from '@/components/common/RecipeCard.vue'
import LoadingSkeleton from '@/components/common/LoadingSkeleton.vue'
import { useUserStore, CONSTITUTION_TYPES } from '@/stores/user'
import { useCollectStore } from '@/stores/collect'
import { fetchProfileFavorites } from '@/api/profile.js'
import { fetchRecommendFeed } from '@/api/recipe.js'
import { looksLikeBearerJwt } from '@/utils/authToken.js'
import { readCampusToken } from '@/utils/storedTokens.js'
import {
  MOCK_RECIPES,
  EFFECT_FILTER_OPTIONS,
  EFFICACY_FILTER_ALL,
} from '@/data/recommendMock.js'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'
import { diversifyEfficacyRoundRobin } from '@/utils/recommendEfficacyMix.js'
import { loadDismissedRecipeIds, saveDismissedRecipeIds } from '@/utils/recommendDismiss'
import { fetchWeeklyCampusMenu } from '@/api/campusCalendar.js'
import { buildDefaultWeeklySlots, formatWeekRangeLabel } from '@/data/campusWeeklyCalendarSeed.js'
import { filterSlotsByPreferences, recipeViolatesAllergies } from '@/utils/campusMealPreferences.js'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder.js'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const collectStore = useCollectStore()

async function syncServerFavoritesToCollectStore() {
  if (import.meta.env.VITE_USE_MOCK === 'true') return
  if (!looksLikeBearerJwt(readCampusToken())) return
  try {
    const data = await fetchProfileFavorites()
    const list = data?.recipeFavorites || []
    for (const row of list) {
      if (row.recipeId) collectStore.addCollect(row.recipeId)
    }
  } catch {
    /* 忽略：未登录或网络异常 */
  }
}

/** 来自「场景食疗」等入口：按功效标签关键词收窄推荐池 */
const sceneTagQuery = computed(() => {
  const q = route.query.scene_tag ?? route.query.sceneTag
  return typeof q === 'string' && q.trim() ? q.trim() : ''
})

const sceneLabelQuery = computed(() => {
  const q = route.query.scene_label ?? route.query.sceneLabel
  if (typeof q !== 'string' || !q.trim()) return ''
  try {
    return decodeURIComponent(q.trim())
  } catch {
    return q.trim()
  }
})

function recipeMatchesSceneTag(recipe, tag) {
  const needle = String(tag || '').trim()
  if (!needle) return true
  const tags = recipe.effectTags || []
  if (tags.some((t) => String(t).includes(needle) || needle.includes(String(t)))) return true
  return String(recipe.effect || '').includes(needle)
}

function recipeMatchesKeyword(recipe, kw) {
  const k = String(kw || '').trim()
  if (!k) return true
  const kLower = k.toLowerCase()
  const textHit = (s) => {
    if (s == null || s === '') return false
    const t = String(s)
    return t.includes(k) || t.toLowerCase().includes(kLower)
  }
  if (textHit(recipe.name)) return true
  if (textHit(recipe.effect)) return true
  if (textHit(recipe.efficacySummary) || textHit(recipe.efficacy_summary)) return true
  const tags = recipe.effectTags || []
  if (tags.some((t) => textHit(t))) return true
  const csv = [recipe.efficacyTags, recipe.efficacy_tags, recipe.symptomTags, recipe.symptom_tags]
    .filter(Boolean)
    .join(',')
  if (csv && csv.split(/[,，]/).some((x) => textHit(x.trim()))) return true
  return false
}

function filterPoolByKeyword(list) {
  const k = recipeSearchKeyword.value.trim()
  if (!k) return list
  return list.filter((r) => recipeMatchesKeyword(r, k))
}

function clearSceneQuery() {
  const q = { ...route.query }
  delete q.scene_tag
  delete q.sceneTag
  delete q.scene_label
  delete q.sceneLabel
  router.replace({ path: route.path, query: q })
}

const SORT_LS_KEY = 'tcm_recommend_sort_by'
const SORT_BY_VALUES = new Set(['collect', 'season'])
const EFFICACY_LS_KEY = 'tcm_recommend_effect_filter'
const EFFICACY_FILTER_VALUES = new Set(EFFECT_FILTER_OPTIONS.map((o) => o.value))

/** 与功效「全部」错开，避免 localStorage 串值 */
const CONSTITUTION_FILTER_ALL = '__all_constitution__'
const CONSTITUTION_FILTER_LS_KEY = 'tcm_recommend_constitution_filter'
const CONSTITUTION_FILTER_VALUES = new Set([
  CONSTITUTION_FILTER_ALL,
  ...CONSTITUTION_TYPES.map((c) => c.label),
])

/** 药膳名称 / 功效关键词，与后端 recommend-feed `keyword` 对齐 */
const recipeSearchInput = ref('')
const recipeSearchKeyword = ref('')

const loading = ref(false)
const loadingMore = ref(false)
const recipePool = ref([])
const effectFilter = ref(loadEfficacyPreference())
const constitutionFilter = ref(loadConstitutionFilterPreference())
const sortBy = ref(loadSortPreference())
const visibleCount = ref(12)
const loadMoreLock = ref(false)
const sentinelEl = ref(null)
const feedPage = ref(1)
const hasRemoteMore = ref(true)
const dismissedIds = ref(new Set())
let observer = null
/** 上次拉取推荐流时间，用于窗口重新可见时节流刷新（后台新上架药膳能及时出现） */
let lastFeedLoadedAt = 0
const FEED_REFRESH_ON_VISIBLE_MS = 25_000

const PINGHE_CODE = 'pinghe'

/** 体质 code → 中文名（与后端 constitution_tags、Mock 对齐） */
const CONSTITUTION_CODE_TO_LABEL = Object.fromEntries(
  CONSTITUTION_TYPES.map((c) => [c.code, c.label]),
)

/** 体质 → 推荐文案侧重点（避免写死「润肺益气」） */
const CONSTITUTION_FOCUS = {
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

function seasonFromDate(d = new Date()) {
  const m = d.getMonth() + 1
  if (m >= 3 && m <= 5) return { key: 'spring', label: '春季' }
  if (m >= 6 && m <= 8) return { key: 'summer', label: '夏季' }
  if (m >= 9 && m <= 11) return { key: 'autumn', label: '秋季' }
  return { key: 'winter', label: '冬季' }
}

const seasonCtx = computed(() => seasonFromDate())

const effectiveConstitutionCode = computed(
  () => userStore.constitutionCode || PINGHE_CODE,
)

const effectiveConstitutionLabel = computed(() => {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === effectiveConstitutionCode.value)
  return hit?.label || '平和质'
})

const constitutionFilterOptions = computed(() => [
  { value: CONSTITUTION_FILTER_ALL, label: '全部体质' },
  ...CONSTITUTION_TYPES.map((c) => ({ value: c.label, label: c.label })),
])

function loadDismissedSet() {
  return new Set(loadDismissedRecipeIds())
}

function persistDismissed() {
  saveDismissedRecipeIds([...dismissedIds.value])
}

function loadSortPreference() {
  try {
    const raw = localStorage.getItem(SORT_LS_KEY)
    if (raw && SORT_BY_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return 'collect'
}

function persistSortPreference(v) {
  try {
    if (SORT_BY_VALUES.has(v)) localStorage.setItem(SORT_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

function loadEfficacyPreference() {
  try {
    const raw = localStorage.getItem(EFFICACY_LS_KEY)
    if (raw && EFFICACY_FILTER_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return EFFICACY_FILTER_ALL
}

function persistEfficacyPreference(v) {
  try {
    if (EFFICACY_FILTER_VALUES.has(v)) localStorage.setItem(EFFICACY_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

function loadConstitutionFilterPreference() {
  try {
    const raw = localStorage.getItem(CONSTITUTION_FILTER_LS_KEY)
    if (raw && CONSTITUTION_FILTER_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return CONSTITUTION_FILTER_ALL
}

function persistConstitutionFilterPreference(v) {
  try {
    if (CONSTITUTION_FILTER_VALUES.has(v)) localStorage.setItem(CONSTITUTION_FILTER_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

function isConstitutionFilterAll(v) {
  return v == null || v === '' || v === CONSTITUTION_FILTER_ALL
}

function constitutionFilterApiCode() {
  if (isConstitutionFilterAll(constitutionFilter.value)) return undefined
  const hit = CONSTITUTION_TYPES.find((c) => c.label === constitutionFilter.value)
  return hit?.code
}

function splitCsvTags(v) {
  if (v == null) return []
  if (Array.isArray(v)) return v.map((s) => String(s).trim()).filter(Boolean)
  return String(v)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

function suitConstitutionsFromRemote(r) {
  if (Array.isArray(r.suitConstitutions) && r.suitConstitutions.length) {
    return r.suitConstitutions.map((x) => String(x).trim()).filter(Boolean)
  }
  if (r.suitConstitution) return [String(r.suitConstitution).trim()].filter(Boolean)
  const csv =
    r.constitutionTags ||
    r.constitution_tags ||
    (Array.isArray(r.constitutionCodes) ? r.constitutionCodes.join(',') : '')
  if (!csv || typeof csv !== 'string') return []
  return splitCsvTags(csv)
    .map((token) => {
      const key = token.toLowerCase()
      return CONSTITUTION_CODE_TO_LABEL[key] || token
    })
    .filter(Boolean)
}

function normalizeRecipe(r) {
  let suitConstitutions = suitConstitutionsFromRemote(r)
  const coverUrl = recipeSchematicCoverUrl(r.name)
  const rawCollect = r.collectCount ?? r.collect_count ?? r.favoriteCount
  const nCollect = Number(rawCollect)
  const rawEffectTags = Array.isArray(r.effectTags) ? r.effectTags : []
  const fromEfficacyCsv =
    typeof r.efficacyTags === 'string'
      ? splitCsvTags(r.efficacyTags)
      : typeof r.efficacy_tags === 'string'
        ? splitCsvTags(r.efficacy_tags)
        : []
  const effectTags = rawEffectTags.length ? rawEffectTags : fromEfficacyCsv
  const effectText = r.effect || r.efficacySummary || r.efficacy_summary || ''
  const seasonFromCsv = splitCsvTags(r.seasonTags || r.season_tags).map((s) => s.toLowerCase())
  const seasonRaw = Array.isArray(r.seasonFit)
    ? r.seasonFit
    : Array.isArray(r.suitSeasons)
      ? r.suitSeasons
      : seasonFromCsv.length
        ? seasonFromCsv
        : []
  const seasonFit = seasonRaw.length ? seasonRaw : ['all']
  return {
    ...r,
    suitConstitutions,
    effectTags,
    effect: effectText,
    seasonFit,
    collectCount: Number.isFinite(nCollect) ? nCollect : 0,
    recommendReason: r.recommendReason || r.summary || effectText || '',
    coverUrl,
  }
}

function extractList(data) {
  if (Array.isArray(data)) return data
  if (data?.records) return data.records
  if (data?.list) return data.list
  if (data?.items) return data.items
  if (Array.isArray(data?.content)) return data.content
  if (Array.isArray(data?.rows)) return data.rows
  if (Array.isArray(data?.recipes)) return data.recipes
  return []
}

function seasonOk(recipe, seasonKey) {
  const sf = recipe.seasonFit || []
  return sf.includes('all') || sf.includes(seasonKey)
}

function matchesConstitution(recipe, label) {
  const suits = recipe.suitConstitutions || []
  if (suits.includes(label) || recipe.suitConstitution === label) return true
  /** 旧版 recommend-feed 卡片无体质字段时，不在客户端误筛成空列表 */
  const hasMeta = suits.length > 0 || Boolean(recipe.suitConstitution)
  return !hasMeta
}

function isEfficacyAll(effect) {
  return effect == null || effect === '' || effect === EFFICACY_FILTER_ALL
}

function filterByEffect(recipes, effect) {
  if (isEfficacyAll(effect)) return recipes
  return recipes.filter((r) => {
    const tags = r.effectTags || []
    if (tags.includes(effect)) return true
    const hay = String(r.effect || r.efficacySummary || r.efficacy_summary || '')
    if (hay.includes(effect)) return true
    const hasMeta = tags.length > 0 || hay.trim().length > 0
    return !hasMeta
  })
}

function filterByConstitution(recipes, constitutionLabel) {
  if (isConstitutionFilterAll(constitutionLabel)) return recipes
  return recipes.filter((r) => matchesConstitution(r, constitutionLabel))
}

/**
 * 先保证关键词（或上游）命中池可见，再在本地做功效/体质收窄；
 * 收窄结果为空时回退为仅过敏过滤后的池，避免「搜得到但列表空」。
 */
function keywordThenEffConstPool(list) {
  const allergyTags = userStore.preferences?.allergyTags || []
  let base = list
  const allergyOk = base.filter((r) => !recipeViolatesAllergies(r, allergyTags))
  if (allergyOk.length) base = allergyOk
  const narrowed = filterByConstitution(
    filterByEffect(base, effectFilter.value),
    constitutionFilter.value,
  )
  if (!narrowed.length && base.length) return base
  return narrowed.length ? narrowed : base
}

/** 「按收藏量」且开启个性化时：适宜本人体质者在前，同档内再按收藏量 */
function sortCollectConstitutionFirst(recipes, constitutionLabel) {
  const copy = [...recipes]
  copy.sort((a, b) => {
    const ma = matchesConstitution(a, constitutionLabel) ? 1 : 0
    const mb = matchesConstitution(b, constitutionLabel) ? 1 : 0
    if (ma !== mb) return mb - ma
    const d = (b.collectCount || 0) - (a.collectCount || 0)
    if (d !== 0) return d
    return String(a.id).localeCompare(String(b.id), 'zh-CN')
  })
  return copy
}

function sortRecipes(recipes, sortKey, seasonKey) {
  const copy = [...recipes]
  if (sortKey === 'collect') {
    copy.sort((a, b) => {
      const d = (b.collectCount || 0) - (a.collectCount || 0)
      if (d !== 0) return d
      return String(a.id).localeCompare(String(b.id), 'zh-CN')
    })
    return copy
  }
  copy.sort((a, b) => {
    const as = seasonOk(a, seasonKey) ? 0 : 1
    const bs = seasonOk(b, seasonKey) ? 0 : 1
    if (as !== bs) return as - bs
    return (b.collectCount || 0) - (a.collectCount || 0)
  })
  return copy
}

function uniqueById(list) {
  const seen = new Set()
  const out = []
  for (const r of list) {
    if (seen.has(r.id)) continue
    seen.add(r.id)
    out.push(r)
  }
  return out
}

function matchScore(recipe, constitutionLabel, seasonKey) {
  let s = 0
  if (matchesConstitution(recipe, constitutionLabel)) s += 1000
  if (seasonOk(recipe, seasonKey)) s += 200
  s += Math.min(500, Number(recipe.collectCount) || 0) / 500
  return s
}

/** 体质槽与应季槽约 3:1（60% : 20%）交织；不足时互相补足 */
function mergeConstitutionSeasonSlots(constQ, seaQ) {
  const pattern = ['c', 'c', 'c', 's']
  const out = []
  let ci = 0
  let si = 0
  let pi = 0
  let guard = 0
  const maxGuard = (constQ.length + seaQ.length + 5) * 8
  while ((ci < constQ.length || si < seaQ.length) && guard < maxGuard) {
    guard++
    const typ = pattern[pi % pattern.length]
    pi++
    if (typ === 'c' && ci < constQ.length) {
      out.push(constQ[ci++])
      continue
    }
    if (typ === 's' && si < seaQ.length) {
      out.push(seaQ[si++])
      continue
    }
    if (ci < constQ.length) {
      out.push(constQ[ci++])
      continue
    }
    if (si < seaQ.length) {
      out.push(seaQ[si++])
      continue
    }
  }
  return uniqueById(out)
}

/** 每 4 条菜谱插入 1 张 AI 卡 ≈ 20% */
function withAiTilesRatio(recipeRow) {
  const out = []
  let ai = 0
  recipeRow.forEach((r, idx) => {
    out.push({ kind: 'recipe', recipe: r })
    if ((idx + 1) % 4 === 0) {
      ai += 1
      out.push({ kind: 'ai', id: `ai-entry-${ai}` })
    }
  })
  return out
}

/** 冷启动：应季药膳与 AI 入口 1:1（各约 50%） */
function withColdStartAiInterleave(seasonRecipes) {
  const out = []
  let ai = 0
  seasonRecipes.forEach((r) => {
    out.push({ kind: 'recipe', recipe: r })
    ai += 1
    out.push({ kind: 'ai', id: `ai-entry-${ai}` })
  })
  return out
}

const poolFiltered = computed(() =>
  recipePool.value.filter((r) => !dismissedIds.value.has(String(r.id))),
)

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

const weekOfferRecipes = computed(() => {
  if (!calendarPublished.value) return []
  const prefs = userStore.preferences || {}
  const slots = filterSlotsByPreferences(weeklyMenu.value.slots || [], prefs)
  const slotByRecipe = new Map()
  for (const s of slots) {
    const id = String(s.recipeId)
    if (!slotByRecipe.has(id)) slotByRecipe.set(id, s)
  }
  const kw = recipeSearchKeyword.value.trim()
  const poolBase = kw
    ? keywordThenEffConstPool(poolFiltered.value)
    : filterByConstitution(
        filterByEffect(poolFiltered.value, effectFilter.value),
        constitutionFilter.value,
      )
  const tag = sceneTagQuery.value
  const sk = seasonCtx.value.key
  const primaryLabel = isConstitutionFilterAll(constitutionFilter.value)
    ? effectiveConstitutionLabel.value
    : constitutionFilter.value
  const list = []
  for (const r of poolBase) {
    if (tag && !recipeMatchesSceneTag(r, tag)) continue
    const slot = slotByRecipe.get(String(r.id))
    if (!slot) continue
    if (recipeViolatesAllergies(r, prefs.allergyTags || [])) continue
    list.push({ ...r, campusWindowLabel: slot.windowLabel })
  }
  list.sort((a, b) => matchScore(b, primaryLabel, sk) - matchScore(a, primaryLabel, sk))
  return uniqueById(list)
})

const poolFilteredForGeneral = computed(() => {
  const hide = new Set(weekOfferRecipes.value.map((r) => String(r.id)))
  return poolFiltered.value.filter((r) => !hide.has(String(r.id)))
})

const calendarFitCount = computed(() => weekOfferRecipes.value.length)

const calendarWeekHint = computed(() => {
  const w = weeklyMenu.value.weekLabel?.trim()
  return w || formatWeekRangeLabel()
})

function goCalendar() {
  router.push({ path: '/calendar', query: { from: 'home', apply: '1' } })
}

const constitutionFocusPhrase = computed(() => {
  const code = effectiveConstitutionCode.value
  return CONSTITUTION_FOCUS[code] || CONSTITUTION_FOCUS.pinghe
})

const mergedStream = computed(() => {
  const profileLabel = effectiveConstitutionLabel.value
  const primaryLabel = isConstitutionFilterAll(constitutionFilter.value)
    ? profileLabel
    : constitutionFilter.value
  const sk = seasonCtx.value.key
  const kw = recipeSearchKeyword.value.trim()
  let poolForStream = kw
    ? keywordThenEffConstPool(poolFilteredForGeneral.value)
    : filterByConstitution(
        filterByEffect(poolFilteredForGeneral.value, effectFilter.value),
        constitutionFilter.value,
      )
  if (!kw) {
    const allergyTags = userStore.preferences?.allergyTags || []
    const allergyOk = poolForStream.filter((r) => !recipeViolatesAllergies(r, allergyTags))
    if (allergyOk.length) poolForStream = allergyOk
  }

  let sorted = sortRecipes(poolForStream, sortBy.value, sk)

  if (!sorted.length) return []

  /** 关键词：命中池已由接口 keyword 限定；本地功效/体质在 keywordThenEffConstPool 中已「先全量再收窄」 */
  if (kw) {
    return withAiTilesRatio(uniqueById(sorted))
  }

  /** 按收藏量：未个性化时仅按热度（可配合应季子集）；已个性化时体质适宜者优先，同档内按收藏量 */
  if (sortBy.value === 'collect') {
    let baseRaw = sorted
    const narrowSeason =
      !isEfficacyAll(effectFilter.value) &&
      (!userStore.hasProfile || !userStore.personalizedRecommendEnabled)
    if (narrowSeason) {
      const seasonal = sorted.filter((r) => seasonOk(r, sk))
      const allSeason = sorted.filter((r) => {
        const sf = r.seasonFit || []
        return sf.includes('all')
      })
      baseRaw = seasonal.length ? seasonal : allSeason.length ? allSeason : sorted
    }
    const base =
      userStore.hasProfile && userStore.personalizedRecommendEnabled
        ? uniqueById(sortCollectConstitutionFirst([...baseRaw], primaryLabel))
        : uniqueById(sortRecipes([...baseRaw], 'collect', sk))
    if (!userStore.hasProfile) return withColdStartAiInterleave(base)
    return withAiTilesRatio(base)
  }

  if (!userStore.hasProfile) {
    const baseRaw = isEfficacyAll(effectFilter.value)
      ? sorted
      : (() => {
          const seasonal = sorted.filter((r) => seasonOk(r, sk))
          const allSeason = sorted.filter((r) => {
            const sf = r.seasonFit || []
            return sf.includes('all')
          })
          return seasonal.length ? seasonal : allSeason.length ? allSeason : sorted
        })()
    const base = uniqueById(sortRecipes([...baseRaw], sortBy.value, sk))
    const baseForTiles = isEfficacyAll(effectFilter.value)
      ? diversifyEfficacyRoundRobin(base)
      : base
    return withColdStartAiInterleave(baseForTiles)
  }

  if (!userStore.personalizedRecommendEnabled) {
    const baseRaw = isEfficacyAll(effectFilter.value)
      ? sorted
      : (() => {
          const seasonal = sorted.filter((r) => seasonOk(r, sk))
          const allSeason = sorted.filter((r) => {
            const sf = r.seasonFit || []
            return sf.includes('all')
          })
          return seasonal.length ? seasonal : allSeason.length ? allSeason : sorted
        })()
    const base = uniqueById(sortRecipes([...baseRaw], sortBy.value, sk))
    const baseForTiles = isEfficacyAll(effectFilter.value)
      ? diversifyEfficacyRoundRobin(base)
      : base
    return withAiTilesRatio(baseForTiles)
  }

  sorted = [...sorted].sort(
    (a, b) => matchScore(b, primaryLabel, sk) - matchScore(a, primaryLabel, sk),
  )

  if (isEfficacyAll(effectFilter.value)) {
    const base = uniqueById(sorted)
    const baseForTiles =
      sortBy.value === 'season' ? diversifyEfficacyRoundRobin(base) : base
    return withAiTilesRatio(baseForTiles)
  }

  const constMatches = sorted.filter((r) => matchesConstitution(r, primaryLabel))
  const constIds = new Set(constMatches.map((r) => r.id))
  const seasonMatches = sorted.filter((r) => !constIds.has(r.id) && seasonOk(r, sk))
  const seasonIds = new Set(seasonMatches.map((r) => r.id))
  const rest = sorted.filter((r) => !constIds.has(r.id) && !seasonIds.has(r.id))

  const constQ =
    constMatches.length > 0 ? constMatches : sorted.filter((r) => matchesConstitution(r, '平和质'))
  const seaQ =
    seasonMatches.length > 0
      ? seasonMatches
      : rest.filter((r) => seasonOk(r, sk))
  const constQueue = constQ.length ? constQ : sorted
  const constQueueIds = new Set(constQueue.map((r) => r.id))
  const seasonQueue =
    seaQ.length > 0 ? seaQ : rest.length > 0 ? rest : sorted.filter((r) => !constQueueIds.has(r.id))

  const row = mergeConstitutionSeasonSlots(uniqueById(constQueue), uniqueById(seasonQueue))
  const rowForTiles = isEfficacyAll(effectFilter.value)
    ? diversifyEfficacyRoundRobin(row)
    : row
  return withAiTilesRatio(rowForTiles)
})

const visibleSlice = computed(() => mergedStream.value.slice(0, visibleCount.value))

const hasMoreLocal = computed(() => visibleCount.value < mergedStream.value.length)

const hasMore = computed(() => hasMoreLocal.value || hasRemoteMore.value)

/** 有关键词且功效/体质收窄为空、已回退展示全部关键词命中时，用于提示条 */
const recipeSearchFallbackToKeywordOnly = computed(() => {
  const kw = recipeSearchKeyword.value.trim()
  if (!kw) return false
  const allergyTags = userStore.preferences?.allergyTags || []
  let base = poolFilteredForGeneral.value
  const allergyOk = base.filter((r) => !recipeViolatesAllergies(r, allergyTags))
  if (allergyOk.length) base = allergyOk
  const narrowed = filterByConstitution(
    filterByEffect(base, effectFilter.value),
    constitutionFilter.value,
  )
  return !narrowed.length && base.length > 0
})

const emptyListHint = computed(() => {
  if (recipeSearchKeyword.value.trim()) {
    return `没有找到与「${recipeSearchKeyword.value.trim()}」匹配的药膳，可尝试其他关键词或清空搜索`
  }
  return '当前筛选下暂无菜谱，试试更换功效或体质筛选'
})

const reasonCardText = computed(() => {
  const c = effectiveConstitutionLabel.value
  const s = seasonCtx.value.label
  const focus = constitutionFocusPhrase.value
  if (!userStore.personalizedRecommendEnabled) {
    if (isEfficacyAll(effectFilter.value)) {
      return `您已关闭个性化推荐。当前为「全部功效」，正展示全部可见药膳（可按收藏量或应季排序）。可在「我的」中重新开启个性化。`
    }
    return `您已关闭个性化推荐。当前仅按「${s}」展示应季与四季通用药膳，不再根据体质匹配排序。可在「我的」中重新开启。`
  }
  if (!userStore.hasProfile) {
    if (isEfficacyAll(effectFilter.value)) {
      return `您尚未完成体质测评。当前为「全部功效」，展示全部可见药膳；选择具体功效或完成测评后，列表会进一步聚焦。`
    }
    return `您尚未完成体质测评，系统暂按「${c}」与「${s}」气候为您组合推荐；完成测评后可获得更精准的药膳搭配。`
  }
  return `根据您的${c}体质和${s}气候特点，为您筛选兼顾「${focus}」与应季平衡的药膳方案。`
})

const headlineReason = computed(() => {
  const c = effectiveConstitutionLabel.value
  const s = seasonCtx.value.label
  const focus = constitutionFocusPhrase.value
  if (!userStore.personalizedRecommendEnabled) {
    if (isEfficacyAll(effectFilter.value)) {
      return '全部功效 · 全部可见药膳'
    }
    return `应季通用推荐（${s}）`
  }
  if (!userStore.hasProfile) {
    if (isEfficacyAll(effectFilter.value)) {
      return '全部功效 · 浏览全部可见药膳'
    }
    return `根据「${c}」与「${s}」气候为您推荐以下药膳（完成测评可进一步提高匹配度）`
  }
  return `根据您的${c}体质和${s}气候，推荐以下${focus.split('、')[0]}等的药膳`
})

function localRecipeFallback() {
  const vis = getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
  const src = vis.length ? vis : MOCK_RECIPES
  return src.map(normalizeRecipe)
}

/** 推荐流本地兜底池（已上架 + 场景标签），供关键词回退与接口结果校验 */
function recipeFeedFallbackBaseList() {
  const fallback = localRecipeFallback()
  if (sceneTagQuery.value) {
    return fallback.filter((r) => recipeMatchesSceneTag(r, sceneTagQuery.value))
  }
  return fallback
}

async function loadPool(opts = {}) {
  const append = opts.append === true
  const fallback = localRecipeFallback()

  if (append) {
    if (!hasRemoteMore.value || loadingMore.value || loading.value) return
    loadingMore.value = true
    feedPage.value += 1
  } else {
    loading.value = true
    feedPage.value = 1
  }

  let remoteHasMore = false
  try {
    const data = await fetchRecommendFeed({
      constitution: effectiveConstitutionCode.value,
      season: seasonCtx.value.key,
      personalized: userStore.personalizedRecommendEnabled ? '1' : '0',
      has_profile: userStore.hasProfile ? '1' : '0',
      filter_efficacy: isEfficacyAll(effectFilter.value) ? undefined : effectFilter.value,
      filter_constitution: constitutionFilterApiCode(),
      sort_by: sortBy.value,
      page: feedPage.value,
      page_size: 12,
      ...(sceneTagQuery.value ? { scene_tag: sceneTagQuery.value } : {}),
      ...(recipeSearchKeyword.value ? { keyword: recipeSearchKeyword.value } : {}),
    })
    const list = extractList(data)
    const kw = recipeSearchKeyword.value.trim()

    if (list.length) {
      let mapped = list.map(normalizeRecipe)
      let usedLocalKeywordFallback = false
      /** 接口若未按 keyword 过滤或卡片字段不全，会出现「搜了但列表无关」；首屏无命中时回退本地关键词池；加载更多仅追加本页命中行 */
      if (kw) {
        const hit = mapped.filter((r) => recipeMatchesKeyword(r, kw))
        if (hit.length) {
          mapped = hit
        } else if (!append) {
          mapped = filterPoolByKeyword(recipeFeedFallbackBaseList())
          usedLocalKeywordFallback = true
        } else {
          mapped = []
        }
      }
      if (!mapped.length && kw && !append) {
        mapped = filterPoolByKeyword(recipeFeedFallbackBaseList())
        usedLocalKeywordFallback = true
      }
      if (typeof data?.hasMore === 'boolean') {
        remoteHasMore = usedLocalKeywordFallback ? false : data.hasMore
      } else {
        remoteHasMore = usedLocalKeywordFallback ? false : list.length >= 12
      }
      if (append) {
        if (mapped.length) {
          recipePool.value = uniqueById([...recipePool.value, ...mapped])
        } else {
          feedPage.value = Math.max(1, feedPage.value - 1)
        }
      } else {
        recipePool.value = mapped
      }
    } else if (append) {
      feedPage.value = Math.max(1, feedPage.value - 1)
    } else {
      const kw = recipeSearchKeyword.value.trim()
      /** 带关键词时：接口空列表仍用本地库做关键词匹配，避免「选了功效/体质就走 narrowedRemote 置空」导致搜索永远无结果 */
      if (kw) {
        recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList())
        remoteHasMore = false
      } else {
        const narrowedRemote =
          !isEfficacyAll(effectFilter.value) || !isConstitutionFilterAll(constitutionFilter.value)
        if (narrowedRemote) {
          recipePool.value = []
          remoteHasMore = false
        } else {
          recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList())
          remoteHasMore = false
        }
      }
    }
  } catch {
    if (append) {
      feedPage.value = Math.max(1, feedPage.value - 1)
    } else {
      recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList())
      remoteHasMore = false
    }
  } finally {
    loading.value = false
    loadingMore.value = false
    hasRemoteMore.value = remoteHasMore
    lastFeedLoadedAt = Date.now()
  }
}

function isRecommendRoutePath() {
  const p = route.path
  return p === '/home' || p === '/recommend'
}

function refreshFeedIfStale() {
  if (!isRecommendRoutePath()) return
  if (document.visibilityState !== 'visible') return
  if (loading.value || loadingMore.value) return
  if (Date.now() - lastFeedLoadedAt < FEED_REFRESH_ON_VISIBLE_MS) return
  feedPage.value = 1
  visibleCount.value = 12
  loadPool()
}

/** 用户再次点击「首页」：立即重拉，不受节流限制 */
function refreshFeedFromUser() {
  if (!isRecommendRoutePath()) return
  if (loading.value || loadingMore.value) return
  feedPage.value = 1
  visibleCount.value = 12
  loadPool()
}

function onVisibilityForFeed() {
  refreshFeedIfStale()
}

function debounce(fn, ms) {
  let t = null
  const wrapped = (...args) => {
    if (t) clearTimeout(t)
    t = setTimeout(() => {
      t = null
      fn(...args)
    }, ms)
  }
  wrapped.cancel = () => {
    if (t) clearTimeout(t)
    t = null
  }
  return wrapped
}

function loadMore() {
  if (loadMoreLock.value) return
  loadMoreLock.value = true
  if (visibleCount.value < mergedStream.value.length) {
    visibleCount.value = Math.min(visibleCount.value + 10, mergedStream.value.length)
    requestAnimationFrame(() => {
      loadMoreLock.value = false
    })
    return
  }
  if (hasRemoteMore.value && !loadingMore.value && !loading.value) {
    loadPool({ append: true }).finally(() => {
      visibleCount.value = Math.min(visibleCount.value + 12, mergedStream.value.length)
      requestAnimationFrame(() => {
        loadMoreLock.value = false
      })
    })
    return
  }
  requestAnimationFrame(() => {
    loadMoreLock.value = false
  })
}

const loadMoreDebounced = debounce(loadMore, 320)

/** 回车触发搜索（须用 keydown：el-input 只向父组件转发 keydown，不转发 keyup） */
function onSearchEnter(ev) {
  if (ev.isComposing) return
  ev.preventDefault()
  commitRecipeSearch()
}

function commitRecipeSearch() {
  const next = recipeSearchInput.value.trim()
  recipeSearchKeyword.value = next
  feedPage.value = 1
  visibleCount.value = 12
  loadPool()
}

function onRecipeSearchClear() {
  recipeSearchInput.value = ''
  if (!recipeSearchKeyword.value) return
  recipeSearchKeyword.value = ''
  feedPage.value = 1
  visibleCount.value = 12
  loadPool()
}

function setupObserver() {
  observer?.disconnect()
  if (!sentinelEl.value) return
  observer = new IntersectionObserver(
    (entries) => {
      if (entries.some((e) => e.isIntersecting)) loadMoreDebounced()
    },
    { root: null, rootMargin: '120px', threshold: 0 },
  )
  observer.observe(sentinelEl.value)
}

watch([mergedStream, loading], async () => {
  if (!loading.value) {
    await nextTick()
    setupObserver()
  }
})

watch(
  () => [
    userStore.constitutionCode,
    effectFilter.value,
    constitutionFilter.value,
    sortBy.value,
    userStore.personalizedRecommendEnabled,
    userStore.hasProfile,
  ],
  () => {
    visibleCount.value = 12
  },
)

watch(
  () => [
    userStore.constitutionCode,
    userStore.personalizedRecommendEnabled,
    userStore.hasProfile,
    sceneTagQuery.value,
  ],
  () => {
    feedPage.value = 1
    loadPool()
  },
)

const reloadOnFilter = debounce(() => {
  feedPage.value = 1
  loadPool()
}, 300)

watch(
  () => effectFilter.value,
  (v) => {
    persistEfficacyPreference(v)
    reloadOnFilter()
  },
)

watch(
  () => constitutionFilter.value,
  (v) => {
    persistConstitutionFilterPreference(v)
    reloadOnFilter()
  },
)

watch(
  () => sortBy.value,
  (v) => {
    persistSortPreference(v)
    feedPage.value = 1
    loadPool()
  },
)

watch(
  () => userStore.preferences,
  () => {
    visibleCount.value = 12
  },
  { deep: true },
)

onMounted(() => {
  dismissedIds.value = loadDismissedSet()
  syncServerFavoritesToCollectStore()
  refreshWeeklyMenu()
  document.addEventListener('visibilitychange', onVisibilityForFeed)
  window.addEventListener('campus-home-refresh', refreshFeedFromUser)
  loadPool().then(() => setupObserver())
})

onUnmounted(() => {
  document.removeEventListener('visibilitychange', onVisibilityForFeed)
  window.removeEventListener('campus-home-refresh', refreshFeedFromUser)
  observer?.disconnect()
  observer = null
})

function goConstitution() {
  router.push({ path: '/constitution' })
}

function goAi() {
  router.push({ path: '/ai' })
}

function onPatchCollect({ id, collectCount }) {
  const s = String(id)
  const idx = recipePool.value.findIndex((r) => String(r.id) === s)
  if (idx >= 0) {
    recipePool.value[idx] = { ...recipePool.value[idx], collectCount }
  }
}

function onNotInterested(id) {
  const sid = String(id)
  dismissedIds.value = new Set([...dismissedIds.value, sid])
  persistDismissed()
}
</script>

<template>
  <div class="page recommend">
    <div class="portrait-bar page-card">
      <div class="portrait-bar__main">
        <el-tag type="success" effect="dark" round>体质 · {{ effectiveConstitutionLabel }}</el-tag>
        <el-tag type="info" effect="plain" round>季节 · {{ seasonCtx.label }}</el-tag>
      </div>
      <el-button text type="primary" class="portrait-bar__edit" @click="goConstitution">
        修改
      </el-button>
    </div>

    <el-alert
      v-if="sceneTagQuery"
      type="info"
      :closable="false"
      show-icon
      class="scene-strip page-card"
    >
      <div class="scene-strip__body">
        <p class="scene-strip__text">
          当前按场景{{ sceneLabelQuery ? `「${sceneLabelQuery}」` : '' }}，以功效关键词「<strong>{{ sceneTagQuery }}</strong>」收窄药膳列表。
          <template v-if="!userStore.hasProfile">您尚未完成体质测评，仍可先浏览；测评后推荐会更贴合个人。</template>
        </p>
        <el-button size="small" type="primary" plain @click="clearSceneQuery">清除场景筛选</el-button>
      </div>
    </el-alert>

    <div v-if="!userStore.hasProfile" class="cold-start page-card" role="link" @click="goConstitution">
      <span>完成体质测评，获得更精准的推荐 →</span>
      <el-icon class="cold-start__icon"><Right /></el-icon>
    </div>

    <div class="reason-calendar-row page-card">
      <section class="reason-calendar-row__reason" aria-labelledby="recommend-reason-lead">
        <p id="recommend-reason-lead" class="reason-card__lead">{{ headlineReason }}</p>
        <p class="reason-card__detail">{{ reasonCardText }}</p>
      </section>
      <button
        type="button"
        class="calendar-entry"
        :aria-label="`打开本周药膳日历，当前匹配 ${calendarFitCount} 道菜`"
        @click="goCalendar"
      >
        <div class="calendar-entry__art" aria-hidden="true" />
        <div class="calendar-entry__text">
          <span class="calendar-entry__kicker">本周药膳日历</span>
          <span class="calendar-entry__title">适合你的 {{ calendarFitCount }} 道菜</span>
          <span class="calendar-entry__sub">{{ calendarWeekHint }} · 点按应用个人筛选</span>
        </div>
        <span class="calendar-entry__chev" aria-hidden="true">›</span>
      </button>
    </div>

    <el-alert
      v-if="!calendarPublished"
      type="warning"
      :closable="false"
      show-icon
      class="calendar-unpublished page-card"
      title="本周校园药膳日历尚未发布"
      :description="weeklyMenu.notice || '食堂排期确认后将上线「本周可点」推荐；您仍可浏览下方通用养生推荐。'"
    />

    <div class="toolbar page-card">
      <div class="toolbar__row">
        <div class="toolbar__controls">
          <div class="toolbar__filters">
            <span class="toolbar__label">功效</span>
            <el-select
              v-model="effectFilter"
              placeholder="筛选功效"
              clearable
              :value-on-clear="EFFICACY_FILTER_ALL"
              class="toolbar__select"
            >
              <el-option
                v-for="opt in EFFECT_FILTER_OPTIONS"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
          <div class="toolbar__filters">
            <span class="toolbar__label">体质</span>
            <el-select
              v-model="constitutionFilter"
              placeholder="按体质筛选"
              clearable
              :value-on-clear="CONSTITUTION_FILTER_ALL"
              class="toolbar__select toolbar__select--const"
            >
              <el-option
                v-for="opt in constitutionFilterOptions"
                :key="opt.value"
                :label="opt.label"
                :value="opt.value"
              />
            </el-select>
          </div>
          <div class="toolbar__sort">
            <span class="toolbar__label">排序</span>
            <el-radio-group v-model="sortBy" size="small">
              <el-radio-button label="collect">按收藏量</el-radio-button>
              <el-radio-button label="season">按季节</el-radio-button>
            </el-radio-group>
          </div>
        </div>
        <div class="toolbar__search">
          <el-input
            v-model="recipeSearchInput"
            clearable
            class="toolbar__search-input"
            placeholder="药膳名称、功效或标签，回车搜索"
            :prefix-icon="Search"
            aria-label="搜索药膳，回车提交"
            autocomplete="off"
            @clear="onRecipeSearchClear"
            @keydown.enter="onSearchEnter"
          />
        </div>
      </div>
    </div>

    <el-alert
      v-if="recipeSearchFallbackToKeywordOnly"
      type="info"
      :closable="false"
      show-icon
      class="search-filter-fallback page-card"
    >
      当前功效或体质筛选下没有更精确匹配，已为您展示与「{{ recipeSearchKeyword.trim() }}」相关的全部命中结果。调整筛选可进一步收窄。
    </el-alert>

    <LoadingSkeleton v-if="loading" :rows="6" />

    <template v-else>
      <section v-if="calendarPublished" class="week-offer">
        <div class="week-offer__head">
          <span class="week-offer__dot" aria-hidden="true" />
          <h2 class="week-offer__title">本周可点推荐</h2>
        </div>
        <p v-if="!weekOfferRecipes.length" class="week-offer__empty">
          当前筛选下暂无与本周日历匹配的菜品，可尝试调整功效或体质筛选，或查看下方「通用养生推荐」。
        </p>
        <div v-else class="masonry masonry--week">
          <RecipeCard
            v-for="r in weekOfferRecipes"
            :key="'wk-' + r.id"
            :recipe="r"
            :corner-badge="r.campusWindowLabel || '本周供应'"
            @patch-collect="onPatchCollect"
            @not-interested="onNotInterested"
          />
        </div>
      </section>

      <div class="general-head">
        <h2 class="general-head__title">通用养生推荐</h2>
        <p class="general-head__sub">无固定校园窗口的知识类药膳与调养内容，可按体质与季节浏览。</p>
      </div>

      <div class="masonry">
        <template v-for="(item, idx) in visibleSlice" :key="item.kind === 'ai' ? item.id : item.recipe.id + '-' + idx">
          <RecipeCard
            v-if="item.kind === 'recipe'"
            :recipe="item.recipe"
            @patch-collect="onPatchCollect"
            @not-interested="onNotInterested"
          />
          <button
            v-else
            type="button"
            class="ai-tile page-card"
            @click="goAi"
          >
            <span class="ai-tile__kicker">AI 助手</span>
            <span class="ai-tile__title">生成我的药膳方案</span>
            <span class="ai-tile__desc">结合体质、忌口与当季食材，一键获得搭配建议</span>
            <span class="ai-tile__cta">立即体验 →</span>
          </button>
        </template>
      </div>

      <div ref="sentinelEl" class="sentinel" aria-hidden="true" />

      <p v-if="hasMore" class="load-hint">
        <template v-if="loadingMore">加载中…</template>
        <template v-else>上滑加载更多…</template>
      </p>
      <el-empty
        v-if="!visibleSlice.length && !(calendarPublished && weekOfferRecipes.length)"
        :description="emptyListHint"
      />
    </template>
  </div>
</template>

<style scoped>
.recommend {
  max-width: 1120px;
  margin: 0 auto;
}

.page-card {
  background: var(--color-bg-surface);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-soft);
}

.portrait-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
}

.portrait-bar__main {
  display: flex;
  flex-wrap: wrap;
  gap: var(--space-sm);
  align-items: center;
}

.portrait-bar__edit {
  flex-shrink: 0;
  font-weight: 600;
}

.scene-strip {
  margin-bottom: var(--space-md);
}

.scene-strip__body {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: var(--space-md);
  flex-wrap: wrap;
}

.scene-strip__text {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
}

.cold-start {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-md);
  cursor: pointer;
  color: var(--color-primary-dark);
  background: linear-gradient(90deg, #ecfdf5, #f9f7f4);
  border-color: #c5e6d0;
  transition: box-shadow 0.15s ease;
}

.cold-start:hover {
  box-shadow: var(--shadow-card-hover);
}

.cold-start__icon {
  flex-shrink: 0;
}

.reason-calendar-row {
  display: flex;
  align-items: stretch;
  gap: var(--space-xl);
  padding: var(--space-lg);
  margin-bottom: var(--space-lg);
}

.reason-calendar-row__reason {
  flex: 1 1 0;
  min-width: 0;
  padding-right: var(--space-md);
  border-right: 1px solid var(--color-border);
}

.reason-calendar-row .calendar-entry {
  flex: 0 1 340px;
  max-width: 380px;
  width: auto;
  min-width: 0;
  margin-bottom: 0;
  align-self: stretch;
  border-radius: var(--radius-md);
}

@media (max-width: 768px) {
  .reason-calendar-row {
    flex-direction: column;
    align-items: stretch;
    gap: var(--space-md);
  }

  .reason-calendar-row__reason {
    padding-right: 0;
    border-right: none;
    padding-bottom: var(--space-md);
    border-bottom: 1px solid var(--color-border);
  }

  .reason-calendar-row .calendar-entry {
    flex: 1 1 auto;
    max-width: none;
    width: 100%;
    align-self: stretch;
  }
}

.reason-card__lead {
  margin: 0 0 var(--space-sm);
  font-size: var(--font-size-lg);
  font-weight: 600;
  color: var(--color-text-primary);
}

.reason-card__detail {
  margin: 0;
  font-size: var(--font-size-sm);
  line-height: 1.65;
  color: var(--color-text-secondary);
}

.toolbar {
  padding: var(--space-md) var(--space-lg);
  margin-bottom: var(--space-lg);
}

.toolbar__row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: var(--space-md) var(--space-xl);
}

.toolbar__controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--space-md) var(--space-lg);
  flex: 1 1 auto;
  min-width: 0;
}

.toolbar__search {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex: 1 1 220px;
  max-width: min(100%, 420px);
  min-width: min(100%, 200px);
}

.toolbar__search-input {
  flex: 1;
  min-width: 0;
}

.toolbar__search-input :deep(.el-input__wrapper) {
  border-radius: var(--radius-lg);
  box-shadow: 0 0 0 1px var(--color-border) inset;
  transition: box-shadow 0.15s ease;
}

.toolbar__search-input :deep(.el-input__wrapper.is-focus) {
  box-shadow: 0 0 0 1px var(--color-primary) inset, 0 0 0 3px color-mix(in srgb, var(--color-primary) 18%, transparent);
}

.toolbar__filters,
.toolbar__sort {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  flex-wrap: wrap;
}

.toolbar__label {
  font-size: var(--font-size-sm);
  color: var(--color-text-muted);
  white-space: nowrap;
}

.toolbar__select {
  min-width: 140px;
}

.toolbar__select--const {
  min-width: 128px;
}

.search-filter-fallback {
  margin-bottom: var(--space-lg);
}

@media (max-width: 640px) {
  .toolbar__row {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar__search {
    max-width: none;
    min-width: 0;
  }
}

/**
 * 使用 Grid 而非 column-count：多列布局会按「先填满第一列再第二列」分配，
 * 视觉上从左到右扫读时顺序与 DOM 不一致；Grid 按先行后列与排序列表一致。
 */
.masonry {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: var(--space-lg);
  align-items: start;
}

@media (min-width: 900px) {
  .masonry {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

.masonry :deep(.card),
.masonry .ai-tile {
  margin-bottom: 0;
}

.ai-tile {
  width: 100%;
  text-align: left;
  border: 1px dashed var(--color-border-strong);
  background: linear-gradient(145deg, #faf9f6, #f0eee8);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  cursor: pointer;
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.ai-tile:hover {
  transform: translateY(-2px);
  box-shadow: var(--shadow-card-hover);
}

.ai-tile__kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--color-ai);
}

.ai-tile__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.ai-tile__desc {
  font-size: var(--font-size-sm);
  color: var(--color-text-secondary);
  line-height: 1.55;
}

.ai-tile__cta {
  margin-top: var(--space-xs);
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--color-primary);
}

.sentinel {
  height: 1px;
  margin-top: var(--space-lg);
}

.load-hint {
  text-align: center;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  margin: var(--space-md) 0 var(--space-xl);
}

.calendar-entry {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  width: 100%;
  margin-bottom: var(--space-md);
  padding: var(--space-md) var(--space-lg);
  text-align: left;
  cursor: pointer;
  border: 1px solid color-mix(in srgb, var(--color-primary) 28%, transparent);
  background: linear-gradient(110deg, #ecfdf5 0%, #d1fae5 42%, #f0fdf4 100%);
  transition: box-shadow 0.15s ease, transform 0.15s ease;
}

.calendar-entry:hover {
  box-shadow: var(--shadow-card-hover);
  transform: translateY(-1px);
}

.calendar-entry__art {
  width: 52px;
  height: 52px;
  flex-shrink: 0;
  border-radius: 14px;
  background:
    radial-gradient(circle at 30% 30%, rgba(255, 255, 255, 0.95), transparent 55%),
    linear-gradient(145deg, #34d399, #059669);
  opacity: 0.95;
}

.calendar-entry__text {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.calendar-entry__kicker {
  font-size: var(--font-size-xs);
  letter-spacing: 0.08em;
  color: var(--color-text-muted);
  text-transform: uppercase;
}

.calendar-entry__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--color-text-primary);
}

.calendar-entry__sub {
  font-size: var(--font-size-xs);
  color: var(--color-text-secondary);
  line-height: 1.45;
}

.calendar-entry__chev {
  flex-shrink: 0;
  font-size: 28px;
  font-weight: 300;
  color: var(--color-primary);
  line-height: 1;
}

.calendar-unpublished {
  margin-bottom: var(--space-md);
}

.week-offer {
  margin-bottom: var(--space-lg);
}

.week-offer__head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: var(--space-sm);
}

.week-offer__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #22c55e;
  box-shadow: 0 0 0 3px rgba(34, 197, 94, 0.25);
  flex-shrink: 0;
}

.week-offer__title {
  margin: 0;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.week-offer__empty {
  margin: 0 0 var(--space-md);
  padding: var(--space-md) var(--space-lg);
  font-size: var(--font-size-sm);
  line-height: 1.55;
  color: var(--color-text-secondary);
  background: var(--color-bg-surface);
  border: 1px dashed var(--color-border);
  border-radius: var(--radius-lg);
}

.general-head {
  margin-bottom: var(--space-md);
}

.general-head__title {
  margin: 0 0 6px;
  font-size: var(--font-size-md);
  font-weight: 700;
  color: var(--color-text-primary);
}

.general-head__sub {
  margin: 0;
  font-size: var(--font-size-xs);
  color: var(--color-text-muted);
  line-height: 1.5;
}

.masonry--week {
  margin-bottom: var(--space-md);
}
</style>
