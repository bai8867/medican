/**
 * 与 recommendMock.js 对齐的最小类型声明（渐进 P1-1）。
 */
export interface RecommendMockRecipe {
  id: string | number
  name: string
  collectCount?: number
  status?: string
  [key: string]: unknown
}

export const MOCK_RECIPES: RecommendMockRecipe[]
export const EFFICACY_FILTER_ALL: string
export const EFFECT_FILTER_OPTIONS: readonly unknown[]
