export interface SceneTea {
  title: string
  body: string
  type: 'tea' | 'tip'
}

export interface SceneTherapySeed {
  id: number
  name: string
  icon: string
  description: string
  tagline: string
  painTags: string[]
  tags: string[]
  forbidden: string[]
  ingredientInsight: string
  teas: SceneTea[]
}

export const SCENE_THERAPY_SEED: readonly SceneTherapySeed[]

export function getSceneSeed(id: number): SceneTherapySeed | undefined

export function scoreRecipeForScene(
  r: Record<string, unknown>,
  scene: SceneTherapySeed,
): { score: number; matched: string[] }

export function pickSceneRecipes(
  scene: SceneTherapySeed,
  pool: Record<string, unknown>[],
): Array<{ r: Record<string, unknown>; score: number; matched: string[] }>
