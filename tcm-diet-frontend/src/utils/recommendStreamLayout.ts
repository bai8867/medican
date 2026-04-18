// @ts-nocheck
import { matchesConstitution } from '../composables/useRecommendFeedFilters'

/** 「按收藏量」且开启个性化时：适宜本人体质者在前，同档内再按收藏量 */
export function sortCollectConstitutionFirst(recipes, constitutionLabel) {
  const copy = [...recipes]
  copy.sort((a, b) => {
    const ma = matchesConstitution(a, constitutionLabel) ? 1 : 0
    const mb = matchesConstitution(b, constitutionLabel) ? 1 : 0
    if (ma !== mb) return mb - ma
    const d = (b.collectCount || 0) - (a.collectCount || 0)
    if (d !== 0) return d
    return String(a.id).localeCompare(String(b.id), 'zh-CN')
  })
  return copy
}

/** 每 4 条菜谱插入 1 张 AI 卡 ≈ 20% */
export function withAiTilesRatio(recipeRow) {
  const out = []
  let ai = 0
  recipeRow.forEach((r, idx) => {
    out.push({ kind: 'recipe', recipe: r })
    if ((idx + 1) % 4 === 0) {
      ai += 1
      out.push({ kind: 'ai', id: `ai-entry-${ai}` })
    }
  })
  return out
}

/** 冷启动：应季药膳与 AI 入口 1:1（各约 50%） */
export function withColdStartAiInterleave(seasonRecipes) {
  const out = []
  let ai = 0
  seasonRecipes.forEach((r) => {
    out.push({ kind: 'recipe', recipe: r })
    ai += 1
    out.push({ kind: 'ai', id: `ai-entry-${ai}` })
  })
  return out
}
