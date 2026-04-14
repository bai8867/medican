import { ALLERGY_TAG_OPTIONS } from '@/data/campusWeeklyCalendarSeed.js'

function norm(s) {
  return String(s || '')
    .trim()
    .toLowerCase()
}

function recipeHaystack(recipe) {
  const parts = [recipe.name, recipe.summary, recipe.effect, recipe.recommendReason]
  const ing = Array.isArray(recipe.ingredients) ? recipe.ingredients : []
  for (const it of ing) {
    parts.push(it?.name, it?.note)
  }
  return norm(parts.filter(Boolean).join(' '))
}

/**
 * @param {object} recipe
 * @param {string[]} allergyTagIds
 */
export function recipeViolatesAllergies(recipe, allergyTagIds) {
  const ids = Array.isArray(allergyTagIds) ? allergyTagIds.map((x) => String(x)) : []
  if (!ids.length) return false
  const hay = recipeHaystack(recipe)
  if (!hay) return false
  for (const id of ids) {
    const hit = ALLERGY_TAG_OPTIONS.find((o) => o.id === id)
    const kws = hit?.keywords?.length ? hit.keywords : hit ? [hit.label] : []
    for (const kw of kws) {
      if (kw && hay.includes(norm(kw))) return true
    }
  }
  return false
}

/**
 * @param {string} [priceBand] economy | regular | luxury
 * @param {string} [budgetTier] economy | regular | unlimited
 */
export function slotMatchesBudget(priceBand, budgetTier) {
  const band = priceBand || 'regular'
  const tier = budgetTier || 'regular'
  if (tier === 'unlimited') return true
  if (tier === 'economy') return band === 'economy'
  return band === 'economy' || band === 'regular'
}

/**
 * @param {{ locationIds?: string[] }} slot
 * @param {string[]} selectedIds 用户常去校区/食堂，空表示不限
 */
export function slotMatchesLocations(slot, selectedIds) {
  const sel = Array.isArray(selectedIds) ? selectedIds.map((x) => String(x)).filter(Boolean) : []
  if (!sel.length) return true
  const locs = Array.isArray(slot?.locationIds) ? slot.locationIds.map((x) => String(x)) : []
  if (!locs.length) return true
  return locs.some((id) => sel.includes(id))
}

/**
 * @param {object[]} slots
 * @param {{ campusLocationIds?: string[], budgetTier?: string }} prefs
 */
export function filterSlotsByPreferences(slots, prefs) {
  const p = prefs || {}
  const loc = Array.isArray(p.campusLocationIds) ? p.campusLocationIds : []
  const budget = p.budgetTier || 'regular'
  return (Array.isArray(slots) ? slots : []).filter(
    (s) => slotMatchesLocations(s, loc) && slotMatchesBudget(s.priceBand, budget),
  )
}

/**
 * 场景页：忌口过敏的往后排；预算倾向（演示：收藏高略倾向常规档）微调
 * @param {object} a
 * @param {object} b
 * @param {{ allergyTags?: string[], budgetTier?: string }} prefs
 */
export function compareSceneRecipeRows(a, b, prefs) {
  const p = prefs || {}
  const va = recipeViolatesAllergies({ name: a.name, ingredients: [] }, p.allergyTags || []) ? 1 : 0
  const vb = recipeViolatesAllergies({ name: b.name, ingredients: [] }, p.allergyTags || []) ? 1 : 0
  if (va !== vb) return va - vb
  const tier = p.budgetTier || 'regular'
  const ca = Number(a.collectCount) || 0
  const cb = Number(b.collectCount) || 0
  if (tier === 'economy') return ca - cb
  return cb - ca
}
