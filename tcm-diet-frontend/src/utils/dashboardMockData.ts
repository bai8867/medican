// @ts-nocheck
import { MOCK_RECIPES } from '@/data/recommendMock'
import { getCurrentSeasonCode } from '@/utils/season'

/** 药膳是否属于某季节（含四季通用） */
export function recipeMatchesSeason(recipe, seasonCode) {
  const fit = recipe.seasonFit || []
  return fit.includes('all') || fit.includes(seasonCode)
}

/** 全站收藏量合计（Mock：各药膳 collectCount 之和） */
export function getPlatformTotalCollectCount() {
  return MOCK_RECIPES.reduce((sum, r) => sum + (Number(r.collectCount) || 0), 0)
}

/**
 * @param {number} n
 * @param {string | null} seasonCode 为 null 表示不按季节过滤
 */
export function getTopRecipesByCollect(n = 10, seasonCode = null) {
  let list = [...MOCK_RECIPES]
  if (seasonCode) {
    list = list.filter((r) => recipeMatchesSeason(r, seasonCode))
  }
  return list
    .sort((a, b) => (Number(b.collectCount) || 0) - (Number(a.collectCount) || 0))
    .slice(0, n)
}

export function getSeasonalTopRecipes(seasonCode = getCurrentSeasonCode(), n = 5) {
  return getTopRecipesByCollect(999, seasonCode).slice(0, n)
}

/** 用户总数 Mock（PRD 5.6.3） */
export const MOCK_PLATFORM_USER_TOTAL = 2847

/**
 * 各体质用户数量 Mock（饼图）
 * 与 stores/user CONSTITUTION_TYPES 名称对齐
 */
export const MOCK_CONSTITUTION_USER_COUNTS = [
  { name: '平和质', value: 418 },
  { name: '气虚质', value: 356 },
  { name: '阳虚质', value: 198 },
  { name: '阴虚质', value: 267 },
  { name: '痰湿质', value: 289 },
  { name: '湿热质', value: 224 },
  { name: '血瘀质', value: 176 },
  { name: '气郁质', value: 245 },
  { name: '特禀质', value: 134 },
]

export function formatIngredientList(recipe) {
  const items = recipe.ingredients || []
  return items
    .map((i) => {
      const parts = [i.name, i.amount].filter(Boolean)
      if (i.note) parts.push(`（${i.note}）`)
      return parts.join('')
    })
    .join('；')
}

export function formatSuitConstitutions(recipe) {
  if (recipe.suitConstitutions?.length) {
    return recipe.suitConstitutions.join('、')
  }
  return recipe.suitConstitution || '—'
}
