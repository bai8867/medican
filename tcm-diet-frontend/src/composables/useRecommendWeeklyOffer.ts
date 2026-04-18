// @ts-nocheck
import { computed } from 'vue'
import { filterSlotsByPreferences, recipeViolatesAllergies } from '@/utils/campusMealPreferences'
import { keywordThenEffConstPool } from '@/composables/useRecommendMergedStream'
import {
  filterByConstitution,
  filterByEffect,
  matchScore,
  uniqueById,
} from '@/composables/useRecommendFeedFilters'
import { isConstitutionFilterAll } from '@/composables/useRecommendPreferences'
import { recipeMatchesSceneTag } from '@/utils/recommendRecipeMatch'

/**
 * 推荐页：周历已发布时，从当前推荐池与周窗口 slot 交叉得到「本周可点」列表，并派生供通用流去重的池视图。
 */
export function useRecommendWeeklyOffer({
  userStore,
  poolFiltered,
  recipeSearchKeyword,
  effectFilter,
  constitutionFilter,
  calendarPublished,
  weeklyMenu,
  sceneTagQuery,
  seasonCtx,
  effectiveConstitutionLabel,
}) {
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
      ? keywordThenEffConstPool(poolFiltered.value, {
          allergyTags: prefs.allergyTags || [],
          effectFilterValue: effectFilter.value,
          constitutionFilterValue: constitutionFilter.value,
        })
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

  return {
    weekOfferRecipes,
    poolFilteredForGeneral,
    calendarFitCount,
  }
}
