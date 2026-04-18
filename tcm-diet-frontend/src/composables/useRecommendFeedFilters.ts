// @ts-nocheck
import { EFFICACY_FILTER_ALL } from '../data/recommendMock'

export { EFFICACY_FILTER_ALL }

export function seasonOk(recipe, seasonKey) {
  const sf = recipe.seasonFit || []
  return sf.includes('all') || sf.includes(seasonKey)
}

export function matchesConstitution(recipe, label) {
  const suits = recipe.suitConstitutions || []
  if (suits.includes(label) || recipe.suitConstitution === label) return true
  // 旧版 recommend-feed 卡片无体质字段时，不在客户端误筛成空列表
  const hasMeta = suits.length > 0 || Boolean(recipe.suitConstitution)
  return !hasMeta
}

export function isEfficacyAll(effect) {
  return effect == null || effect === '' || effect === EFFICACY_FILTER_ALL
}

export function filterByEffect(recipes, effect) {
  if (isEfficacyAll(effect)) return recipes
  return recipes.filter((r) => {
    const tags = r.effectTags || []
    if (tags.includes(effect)) return true
    const hay = String(r.effect || r.efficacySummary || r.efficacy_summary || '')
    if (hay.includes(effect)) return true
    const hasMeta = tags.length > 0 || hay.trim().length > 0
    return !hasMeta
  })
}

export function filterByConstitution(recipes, constitutionLabel) {
  if (
    constitutionLabel == null ||
    constitutionLabel === '' ||
    constitutionLabel === '__all_constitution__'
  ) {
    return recipes
  }
  return recipes.filter((r) => matchesConstitution(r, constitutionLabel))
}

export function sortRecipes(recipes, sortKey, seasonKey) {
  const copy = [...recipes]
  if (sortKey === 'collect') {
    copy.sort((a, b) => {
      const d = (b.collectCount || 0) - (a.collectCount || 0)
      if (d !== 0) return d
      return String(a.id).localeCompare(String(b.id), 'zh-CN')
    })
    return copy
  }
  copy.sort((a, b) => {
    const as = seasonOk(a, seasonKey) ? 0 : 1
    const bs = seasonOk(b, seasonKey) ? 0 : 1
    if (as !== bs) return as - bs
    return (b.collectCount || 0) - (a.collectCount || 0)
  })
  return copy
}

export function uniqueById(list) {
  const seen = new Set()
  const out = []
  for (const r of list) {
    if (seen.has(r.id)) continue
    seen.add(r.id)
    out.push(r)
  }
  return out
}

export function matchScore(recipe, constitutionLabel, seasonKey) {
  let s = 0
  if (matchesConstitution(recipe, constitutionLabel)) s += 1000
  if (seasonOk(recipe, seasonKey)) s += 200
  s += Math.min(500, Number(recipe.collectCount) || 0) / 500
  return s
}

// 体质槽与应季槽约 3:1（60% : 20%）交织；不足时互相补足
export function mergeConstitutionSeasonSlots(constQ, seaQ) {
  const pattern = ['c', 'c', 'c', 's']
  const out = []
  let ci = 0
  let si = 0
  let pi = 0
  let guard = 0
  const maxGuard = (constQ.length + seaQ.length + 5) * 8
  while ((ci < constQ.length || si < seaQ.length) && guard < maxGuard) {
    guard++
    const typ = pattern[pi % pattern.length]
    pi++
    if (typ === 'c' && ci < constQ.length) {
      out.push(constQ[ci++])
      continue
    }
    if (typ === 's' && si < seaQ.length) {
      out.push(seaQ[si++])
      continue
    }
    if (ci < constQ.length) {
      out.push(constQ[ci++])
      continue
    }
    if (si < seaQ.length) {
      out.push(seaQ[si++])
      continue
    }
  }
  return uniqueById(out)
}
