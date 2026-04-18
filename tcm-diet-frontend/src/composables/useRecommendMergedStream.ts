// @ts-nocheck
import { computed } from 'vue'
import {
  filterByConstitution,
  filterByEffect,
  isEfficacyAll,
  matchScore,
  matchesConstitution,
  mergeConstitutionSeasonSlots,
  seasonOk,
  sortRecipes,
  uniqueById,
} from '@/composables/useRecommendFeedFilters'
import { diversifyEfficacyRoundRobin } from '@/utils/recommendEfficacyMix'
import { recipeViolatesAllergies } from '@/utils/campusMealPreferences'
import {
  sortCollectConstitutionFirst,
  withAiTilesRatio,
  withColdStartAiInterleave,
} from '@/utils/recommendStreamLayout'
import { isConstitutionFilterAll } from '@/composables/useRecommendPreferences'

/**
 * 先保证关键词（或上游）命中池可见，再在本地做功效/体质收窄；
 * 收窄结果为空时回退为仅过敏过滤后的池，避免「搜得到但列表空」。
 */
export function keywordThenEffConstPool(list, { allergyTags, effectFilterValue, constitutionFilterValue }) {
  let base = list
  const allergyOk = base.filter((r) => !recipeViolatesAllergies(r, allergyTags || []))
  if (allergyOk.length) base = allergyOk
  const narrowed = filterByConstitution(
    filterByEffect(base, effectFilterValue),
    constitutionFilterValue,
  )
  if (!narrowed.length && base.length) return base
  return narrowed.length ? narrowed : base
}

function effConstCtx(userStore, effectFilter, constitutionFilter) {
  return {
    allergyTags: userStore.preferences?.allergyTags || [],
    effectFilterValue: effectFilter.value,
    constitutionFilterValue: constitutionFilter.value,
  }
}

/**
 * 推荐首页「通用流 + 排序 + AI 卡穿插」的合并列表逻辑（原 Recommend.vue mergedStream）。
 */
export function useRecommendMergedStream({
  userStore,
  poolFilteredForGeneral,
  recipeSearchKeyword,
  effectFilter,
  constitutionFilter,
  sortBy,
  seasonCtx,
  effectiveConstitutionLabel,
}) {
  const mergedStream = computed(() => {
    const profileLabel = effectiveConstitutionLabel.value
    const primaryLabel = isConstitutionFilterAll(constitutionFilter.value)
      ? profileLabel
      : constitutionFilter.value
    const sk = seasonCtx.value.key
    const kw = recipeSearchKeyword.value.trim()
    const ctx = effConstCtx(userStore, effectFilter, constitutionFilter)

    let poolForStream = kw
      ? keywordThenEffConstPool(poolFilteredForGeneral.value, ctx)
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

    if (kw) {
      return withAiTilesRatio(uniqueById(sorted))
    }

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

  return { mergedStream }
}
