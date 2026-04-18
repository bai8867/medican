import { MOCK_NO_MATCH, type MockUnifiedRecipeRow } from './mockTypes'
import {
  getSceneSeed,
  pickSceneRecipes,
  SCENE_THERAPY_SEED,
  type SceneTherapySeed,
} from '@/data/sceneTherapySeed'
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder'

type PickedRow = ReturnType<typeof pickSceneRecipes>[number]

function campusVisiblePool() {
  return getUnifiedRecipeMockStore().filter((r: MockUnifiedRecipeRow) => r.status !== 'off_shelf')
}

function recipeCountForScene(scene: SceneTherapySeed) {
  const n = pickSceneRecipes(scene, campusVisiblePool() as Record<string, unknown>[]).length
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

function buildWhyFit(scene: SceneTherapySeed, r: PickedRow['r'], matched: string[]) {
  const pains = matched.length ? matched.join('、') : scene.painTags.slice(0, 2).join('、')
  const effTags = r.effectTags
  const eff =
    Array.isArray(effTags) && effTags.length
      ? effTags.join('、')
      : String(r.effect ?? r.summary ?? '综合调养')
  const matchLine = scene.tags.length ? `场景调养侧重「${scene.tags.slice(0, 4).join('、')}」。` : ''
  return `你的困扰标签包含「${pains}」。本药膳功效侧重「${eff}」，与上述调养方向相契合。${matchLine}个体有差异，请结合自身情况参考。`
}

function toRecipeCard(scene: SceneTherapySeed, row: PickedRow) {
  const r = row.r
  const matched = row.matched || []
  const name = String(r.name ?? '')
  return {
    id: String(r.id),
    name,
    coverUrl: recipeSchematicCoverUrl(name),
    collectCount: Number(r.collectCount) || 0,
    efficacySummary: String(r.summary ?? r.effect ?? ''),
    whyFit: buildWhyFit(scene, r, matched),
    matchedPainTags: matched.length ? matched : null,
  }
}

function toSolutionApi(id: number) {
  const scene = getSceneSeed(id)
  if (!scene) {
    return { mockError: true as const, code: 404, message: '场景不存在' }
  }
  const pool = campusVisiblePool() as Record<string, unknown>[]
  const picked = pickSceneRecipes(scene, pool).slice(0, 3)
  const recipes = picked.map((row: PickedRow) => toRecipeCard(scene, row))
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

export function matchScenesApiMock(
  method: string,
  path: string,
): typeof MOCK_NO_MATCH | Record<string, unknown> {
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
