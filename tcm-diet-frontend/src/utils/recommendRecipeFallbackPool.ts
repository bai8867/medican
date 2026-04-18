// @ts-nocheck
import { MOCK_RECIPES } from '../data/recommendMock'
import { getUnifiedRecipeMockStore } from '../data/unifiedRecipeMockStore'
import { normalizeRecipe } from './recommendRecipeNormalize'
import { recipeMatchesSceneTag } from './recommendRecipeMatch'

export function localRecipeFallback() {
  const vis = getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
  const src = vis.length ? vis : MOCK_RECIPES
  return src.map(normalizeRecipe)
}

/** 推荐流本地兜底池（已上架 + 可选场景标签收窄），供关键词回退与接口结果校验 */
export function recipeFeedFallbackBaseList(sceneTag) {
  const fallback = localRecipeFallback()
  const tag = String(sceneTag || '').trim()
  if (!tag) return fallback
  return fallback.filter((r) => recipeMatchesSceneTag(r, tag))
}
