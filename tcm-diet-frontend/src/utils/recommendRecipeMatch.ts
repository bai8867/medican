// @ts-nocheck
/**
 * 推荐页：场景标签与搜索关键词对菜谱的纯函数匹配（无 Vue 依赖，便于单测与复用）。
 */

export function recipeMatchesSceneTag(recipe, tag) {
  const needle = String(tag || '').trim()
  if (!needle) return true
  const tags = recipe.effectTags || []
  if (tags.some((t) => String(t).includes(needle) || needle.includes(String(t)))) return true
  return String(recipe.effect || '').includes(needle)
}

export function recipeMatchesKeyword(recipe, kw) {
  const k = String(kw || '').trim()
  if (!k) return true
  const kLower = k.toLowerCase()
  const textHit = (s) => {
    if (s == null || s === '') return false
    const t = String(s)
    return t.includes(k) || t.toLowerCase().includes(kLower)
  }
  if (textHit(recipe.name)) return true
  if (textHit(recipe.effect)) return true
  if (textHit(recipe.efficacySummary) || textHit(recipe.efficacy_summary)) return true
  const tags = recipe.effectTags || []
  if (tags.some((t) => textHit(t))) return true
  const csv = [recipe.efficacyTags, recipe.efficacy_tags, recipe.symptomTags, recipe.symptom_tags]
    .filter(Boolean)
    .join(',')
  if (csv && csv.split(/[,，]/).some((x) => textHit(x.trim()))) return true
  return false
}

/** @param {string} keywordTrimmed 已 trim 的搜索词；空则原样返回 list */
export function filterPoolByKeyword(list, keywordTrimmed) {
  const k = String(keywordTrimmed || '').trim()
  if (!k) return list
  return list.filter((r) => recipeMatchesKeyword(r, k))
}
