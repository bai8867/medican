// @ts-nocheck
import { watch } from 'vue'
import { debounce } from '@/utils/debounce'
import {
  persistConstitutionFilterPreference,
  persistEfficacyPreference,
  persistSortPreference,
} from '@/composables/useRecommendPreferences'

/**
 * 推荐页：筛选、用户画像与场景 query 变化时重置首屏条数或重拉远程池（与周历 useRecommendWeeklyOffer 解耦）。
 */
export function useRecommendFeedReload({
  userStore,
  effectFilter,
  constitutionFilter,
  sortBy,
  sceneTagQuery,
  feedPage,
  visibleCount,
  loadPool,
}) {
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
}
