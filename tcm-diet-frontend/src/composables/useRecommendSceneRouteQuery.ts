// @ts-nocheck
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'

/**
 * 推荐页：来自路由的「场景食疗」等 query（scene_tag / scene_label 等）。
 */
export function useRecommendSceneRouteQuery() {
  const route = useRoute()
  const router = useRouter()

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

  function clearSceneQuery() {
    const q = { ...route.query }
    delete q.scene_tag
    delete q.sceneTag
    delete q.scene_label
    delete q.sceneLabel
    router.replace({ path: route.path, query: q })
  }

  return {
    sceneTagQuery,
    sceneLabelQuery,
    clearSceneQuery,
  }
}
