/**
 * Mock 专用：在 POST/DELETE /user/favorites 与 GET /user/favorites 之间
 * 共享药膳收藏状态，避免「详情已收藏但我的页列表不变」的静态 Mock 问题。
 */
import { getUnifiedRecipeMockStore } from '@/data/unifiedRecipeMockStore.js'
import { recipeSchematicCoverUrl } from '@/utils/recipeCoverPlaceholder.js'

/** @type {Map<string, object>} recipeId -> profile 收藏行 */
const byRecipeId = new Map()

function buildRow(recipeId) {
  const sid = String(recipeId)
  const row = getUnifiedRecipeMockStore().find((r) => String(r.id) === sid)
  return {
    favoriteId: `mock-fav-${sid.replace(/[^a-zA-Z0-9_-]/g, '-')}`,
    recipeId: sid,
    name: row?.name || '药膳',
    coverUrl: recipeSchematicCoverUrl(row?.name || '药膳'),
    effect: row?.effect || row?.summary || '',
    favoritedAt: new Date().toISOString(),
  }
}

function seedDefaults() {
  byRecipeId.clear()
  const pool = getUnifiedRecipeMockStore()
  const r0 = pool[0]
  const r1 = pool[1] || r0
  const now = new Date().toISOString()
  if (r0) {
    byRecipeId.set(String(r0.id), {
      favoriteId: 'mock-fav-recipe-1',
      recipeId: String(r0.id),
      name: r0.name,
      coverUrl: recipeSchematicCoverUrl(r0.name),
      effect: r0.effect || r0.summary || '',
      favoritedAt: now,
    })
  }
  if (r1 && String(r1.id) !== String(r0?.id)) {
    byRecipeId.set(String(r1.id), {
      favoriteId: 'mock-fav-recipe-2',
      recipeId: String(r1.id),
      name: r1.name,
      coverUrl: recipeSchematicCoverUrl(r1.name),
      effect: r1.effect || r1.summary || '',
      favoritedAt: now,
    })
  }
}

seedDefaults()

/** 供 HMR / 测试重置（一般无需调用） */
export function resetMockCampusRecipeFavorites() {
  seedDefaults()
}

export function isMockRecipeFavorited(recipeId) {
  return byRecipeId.has(String(recipeId))
}

/**
 * @param {string|number} recipeId
 * @param {boolean} favorited
 */
export function setMockRecipeFavorited(recipeId, favorited) {
  const sid = String(recipeId)
  if (!sid) return
  if (favorited) {
    if (!byRecipeId.has(sid)) byRecipeId.set(sid, buildRow(sid))
  } else {
    byRecipeId.delete(sid)
  }
}

export function getMockRecipeFavoriteRows() {
  return [...byRecipeId.values()].sort(
    (a, b) =>
      new Date(b.favoritedAt || 0).getTime() - new Date(a.favoritedAt || 0).getTime(),
  )
}

/**
 * @param {string[]} favoriteIds
 */
export function removeMockRecipeFavoritesByFavoriteIds(favoriteIds) {
  const set = new Set((favoriteIds || []).map(String).filter(Boolean))
  if (!set.size) return
  for (const [recipeId, row] of byRecipeId) {
    if (set.has(String(row.favoriteId))) byRecipeId.delete(recipeId)
  }
}
