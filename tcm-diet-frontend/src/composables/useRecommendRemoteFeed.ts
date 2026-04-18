// @ts-nocheck
import { ref } from 'vue'
import { CONSTITUTION_TYPES } from '@/stores/user'
import { fetchRecommendFeed } from '@/api/recipe'
import { isConstitutionFilterAll } from '@/composables/useRecommendPreferences'
import { isEfficacyAll, uniqueById } from '@/composables/useRecommendFeedFilters'
import { recipeMatchesKeyword, filterPoolByKeyword as filterListByKeywordTrimmed } from '@/utils/recommendRecipeMatch'
import { normalizeRecipe, extractList } from '@/utils/recommendRecipeNormalize'
import { recipeFeedFallbackBaseList } from '@/utils/recommendRecipeFallbackPool'

/**
 * 校园推荐页：远程 feed 拉取、分页、关键词回退与 loading 状态（原 Recommend.vue loadPool 块）。
 * 需与外部共享的 `recipeSearchKeyword` ref 配合（由 useRecommendPage 写入）。
 */
export function useRecommendRemoteFeed({
  userStore,
  effectiveConstitutionCode,
  seasonCtx,
  effectFilter,
  constitutionFilter,
  sortBy,
  sceneTagQuery,
  recipeSearchKeyword,
}) {
  const loading = ref(false)
  const loadingMore = ref(false)
  const recipePool = ref([])
  const feedPage = ref(1)
  const hasRemoteMore = ref(true)
  const lastFeedLoadedAt = ref(0)

  function constitutionFilterApiCode() {
    if (isConstitutionFilterAll(constitutionFilter.value)) return undefined
    const hit = CONSTITUTION_TYPES.find((c) => c.label === constitutionFilter.value)
    return hit?.code
  }

  function filterPoolByKeyword(list) {
    return filterListByKeywordTrimmed(list, recipeSearchKeyword.value.trim())
  }

  async function loadPool(opts = {}) {
    const append = opts.append === true

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
        if (kw) {
          const hit = mapped.filter((r) => recipeMatchesKeyword(r, kw))
          if (hit.length) {
            mapped = hit
          } else if (!append) {
            mapped = filterPoolByKeyword(recipeFeedFallbackBaseList(sceneTagQuery.value))
            usedLocalKeywordFallback = true
          } else {
            mapped = []
          }
        }
        if (!mapped.length && kw && !append) {
          mapped = filterPoolByKeyword(recipeFeedFallbackBaseList(sceneTagQuery.value))
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
        const kwEmpty = recipeSearchKeyword.value.trim()
        if (kwEmpty) {
          recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList(sceneTagQuery.value))
          remoteHasMore = false
        } else {
          const narrowedRemote =
            !isEfficacyAll(effectFilter.value) || !isConstitutionFilterAll(constitutionFilter.value)
          if (narrowedRemote) {
            recipePool.value = []
            remoteHasMore = false
          } else {
            recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList(sceneTagQuery.value))
            remoteHasMore = false
          }
        }
      }
    } catch {
      if (append) {
        feedPage.value = Math.max(1, feedPage.value - 1)
      } else {
        recipePool.value = filterPoolByKeyword(recipeFeedFallbackBaseList(sceneTagQuery.value))
        remoteHasMore = false
      }
    } finally {
      loading.value = false
      loadingMore.value = false
      hasRemoteMore.value = remoteHasMore
      lastFeedLoadedAt.value = Date.now()
    }
  }

  return {
    loading,
    loadingMore,
    recipePool,
    feedPage,
    hasRemoteMore,
    loadPool,
    lastFeedLoadedAt,
  }
}
