// @ts-nocheck
import { ref, watch, nextTick, onUnmounted } from 'vue'
import { debounce } from '@/utils/debounce'

/**
 * 推荐页底部 sentinel + IntersectionObserver，本地增量 visibleCount 与远程 append 加载更多。
 */
export function useRecommendFeedScroll({
  mergedStream,
  loading,
  loadingMore,
  hasRemoteMore,
  loadPool,
  visibleCount,
}) {
  const loadMoreLock = ref(false)
  const sentinelEl = ref(null)
  let observer = null

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

  onUnmounted(() => {
    observer?.disconnect()
    observer = null
  })

  return {
    sentinelEl,
    setupObserver,
  }
}
