import { MOCK_NO_MATCH } from './mockTypes.js'
import { getSceneSeed, pickSceneRecipes, SCENE_THERAPY_SEED } from '@/data/sceneTherapySeed.js'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder.js'

function campusVisiblePool() {
  return getUnifiedRecipeMockStore().filter((r) => r.status !== 'off_shelf')
}

function recipeCountForScene(scene) {
  const n = pickSceneRecipes(scene, campusVisiblePool()).length
  return Math.max(n, 3)
}

function toListApi() {
  const pool = campusVisiblePool()
  const list = SCENE_THERAPY_SEED.map((s) => ({
    id: s.id,
    name: s.name,
    icon: s.icon,
    description: s.description,
    tagline: s.tagline,
    painTags: s.painTags,
    recipeCount: recipeCountForScene(s),
    tags: s.tags,
  }))
  void pool
  return { list }
}

function buildWhyFit(scene, r, matched) {
  const pains = matched.length ? matched.join('、') : scene.painTags.slice(0, 2).join('、')
  const eff = (Array.isArray(r.effectTags) && r.effectTags.length
    ? r.effectTags.join('、')
    : r.effect || r.summary || '综合调养')
  const matchLine = scene.tags.length
    ? `场景调养侧重「${scene.tags.slice(0, 4).join('、')}」。`
    : ''
  return `你的困扰标签包含「${pains}」。本药膳功效侧重「${eff}」，与上述调养方向相契合。${matchLine}个体有差异，请结合自身情况参考。`
}

function toRecipeCard(scene, row) {
  const r = row.r
  const matched = row.matched || []
  return {
    id: String(r.id),
    name: r.name,
    coverUrl: recipeSchematicCoverUrl(r.name),
    collectCount: Number(r.collectCount) || 0,
    efficacySummary: r.summary || r.effect || '',
    whyFit: buildWhyFit(scene, r, matched),
    matchedPainTags: matched.length ? matched : null,
  }
}

function toSolutionApi(id) {
  const scene = getSceneSeed(id)
  if (!scene) {
    return { mockError: true, code: 404, message: '场景不存在' }
  }
  const pool = campusVisiblePool()
  const picked = pickSceneRecipes(scene, pool).slice(0, 3)
  const recipes = picked.map((row) => toRecipeCard(scene, row))
  const header = {
    id: scene.id,
    name: scene.name,
    icon: scene.icon,
    description: scene.description,
    tagline: scene.tagline,
    painTags: scene.painTags,
    recipeCount: recipeCountForScene(scene),
    tags: scene.tags,
  }
  return {
    scene: header,
    recipes,
    teas: scene.teas,
    ingredientInsight: scene.ingredientInsight,
    forbidden: scene.forbidden,
  }
}

/**
 * @param {string} method
 * @param {string} path
 * @returns {unknown|typeof MOCK_NO_MATCH}
 */
export function matchScenesApiMock(method, path) {
  if (method !== 'GET') return MOCK_NO_MATCH
  if (path === '/scenes') {
    return toListApi()
  }
  const m = path.match(/^\/scenes\/(\d+)\/recipes$/)
  if (m) {
    return toSolutionApi(Number(m[1]))
  }
  return MOCK_NO_MATCH
}
