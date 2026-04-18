// @ts-nocheck
/** 与推荐页「不感兴趣」一致的 localStorage 键 */
export const DISMISS_LS_KEY = 'tcm_recommend_dismissed'

export function loadDismissedRecipeIds(): string[] {
  try {
    const raw = JSON.parse(localStorage.getItem(DISMISS_LS_KEY) || '[]')
    return Array.isArray(raw) ? raw.map((x) => String(x)).filter(Boolean) : []
  } catch {
    return []
  }
}

export function saveDismissedRecipeIds(ids: string[]) {
  const uniq = [...new Set(ids.map((x) => String(x)).filter(Boolean))]
  localStorage.setItem(DISMISS_LS_KEY, JSON.stringify(uniq))
}

export function removeDismissedRecipeId(recipeId: string) {
  const sid = String(recipeId)
  const next = loadDismissedRecipeIds().filter((x) => x !== sid)
  saveDismissedRecipeIds(next)
}
