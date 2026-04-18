// @ts-nocheck
import { onMounted, onUnmounted } from 'vue'

/** 窗口重新可见时节流刷新（后台新上架药膳能及时出现） */
const FEED_REFRESH_ON_VISIBLE_MS = 25_000

/**
 * 推荐页：文档可见性节流刷新 + 导航「首页」触发的立即重拉（与筛选 watcher 的 useRecommendFeedReload 并列）。
 */
export function useRecommendFeedVisibility({
  route,
  loading,
  loadingMore,
  lastFeedLoadedAt,
  feedPage,
  visibleCount,
  loadPool,
}) {
  function isRecommendRoutePath() {
    const p = route.path
    return p === '/home' || p === '/recommend'
  }

  function refreshFeedIfStale() {
    if (!isRecommendRoutePath()) return
    if (document.visibilityState !== 'visible') return
    if (loading.value || loadingMore.value) return
    if (Date.now() - lastFeedLoadedAt.value < FEED_REFRESH_ON_VISIBLE_MS) return
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

  onMounted(() => {
    document.addEventListener('visibilitychange', onVisibilityForFeed)
    window.addEventListener('campus-home-refresh', refreshFeedFromUser)
  })

  onUnmounted(() => {
    document.removeEventListener('visibilitychange', onVisibilityForFeed)
    window.removeEventListener('campus-home-refresh', refreshFeedFromUser)
  })
}
