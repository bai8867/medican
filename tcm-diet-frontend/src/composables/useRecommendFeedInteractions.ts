// @ts-nocheck
import { ref, computed } from 'vue'
import { fetchProfileFavorites } from '@/api/profile'
import { reportRecommendFeedback } from '@/api/recipe'
import { looksLikeBearerJwt } from '@/utils/authToken'
import { readCampusToken } from '@/utils/storedTokens'
import { loadDismissedRecipeIds, saveDismissedRecipeIds } from '@/utils/recommendDismiss'

/**
 * 推荐流：不感兴趣集合、池过滤、与服务端收藏同步、列表项埋点反馈。
 */
export function useRecommendFeedInteractions({ recipePool, collectStore }) {
  const dismissedIds = ref(new Set())

  function initDismissedFromStorage() {
    dismissedIds.value = new Set(loadDismissedRecipeIds())
  }

  function persistDismissed() {
    saveDismissedRecipeIds([...dismissedIds.value])
  }

  const poolFiltered = computed(() =>
    recipePool.value.filter((r) => !dismissedIds.value.has(String(r.id))),
  )

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

  function onPatchCollect({ id, collectCount }) {
    const s = String(id)
    const idx = recipePool.value.findIndex((r) => String(r.id) === s)
    if (idx >= 0) {
      recipePool.value[idx] = { ...recipePool.value[idx], collectCount }
    }
    reportRecommendFeedback({ recipeId: s, event: 'collect_toggle' }).catch(() => {})
  }

  function onNotInterested(id) {
    const sid = String(id)
    dismissedIds.value = new Set([...dismissedIds.value, sid])
    persistDismissed()
    reportRecommendFeedback({ recipeId: sid, event: 'not_interested' }).catch(() => {})
  }

  function onOpenRecipeDetail(id) {
    const sid = String(id || '')
    if (!sid) return
    reportRecommendFeedback({ recipeId: sid, event: 'open_detail' }).catch(() => {})
  }

  return {
    dismissedIds,
    poolFiltered,
    initDismissedFromStorage,
    syncServerFavoritesToCollectStore,
    onPatchCollect,
    onNotInterested,
    onOpenRecipeDetail,
  }
}
