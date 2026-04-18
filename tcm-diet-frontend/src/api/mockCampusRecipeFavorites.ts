/**
 * Mock 专用：在 POST/DELETE /user/favorites 与 GET /user/favorites 之间
 * 共享药膳收藏状态，避免「详情已收藏但我的页列表不变」的静态 Mock 问题。
 */
import type { MockUnifiedRecipeRow } from './mockTypes'
import {
  getUnifiedRecipeMockStore,
  type UnifiedMockRecipeRow,
} from '@/data/unifiedRecipeMockStore'
import { recipeSchematicCoverUrl } from '../utils/recipeCoverPlaceholder'

/** 与 GET /user/favorites 分页 records 及 Profile mock 对齐的收藏行 */
export interface MockCampusFavoriteRow {
  favoriteId: string
  recipeId: string
  name: string
  coverUrl: string
  effect: string
  favoritedAt: string
}

const byRecipeId = new Map<string, MockCampusFavoriteRow>()

function buildRow(recipeId: string | number): MockCampusFavoriteRow {
  const sid = String(recipeId)
  const row = getUnifiedRecipeMockStore().find((r: MockUnifiedRecipeRow) => String(r.id) === sid) as
    | UnifiedMockRecipeRow
    | undefined
  const name = String(row?.name ?? '药膳')
  return {
    favoriteId: `mock-fav-${sid.replace(/[^a-zA-Z0-9_-]/g, '-')}`,
    recipeId: sid,
    name,
    coverUrl: recipeSchematicCoverUrl(name),
    effect: String(row?.effect ?? row?.summary ?? ''),
    favoritedAt: new Date().toISOString(),
  }
}

function seedDefaults(): void {
  byRecipeId.clear()
  const pool = getUnifiedRecipeMockStore()
  const r0 = pool[0]
  const r1 = pool[1] || r0
  const now = new Date().toISOString()
  if (r0) {
    byRecipeId.set(String(r0.id), {
      favoriteId: 'mock-fav-recipe-1',
      recipeId: String(r0.id),
      name: String(r0.name ?? ''),
      coverUrl: recipeSchematicCoverUrl(String(r0.name ?? '')),
      effect: String(r0.effect ?? r0.summary ?? ''),
      favoritedAt: now,
    })
  }
  if (r1 && String(r1.id) !== String(r0?.id)) {
    byRecipeId.set(String(r1.id), {
      favoriteId: 'mock-fav-recipe-2',
      recipeId: String(r1.id),
      name: String(r1.name ?? ''),
      coverUrl: recipeSchematicCoverUrl(String(r1.name ?? '')),
      effect: String(r1.effect ?? r1.summary ?? ''),
      favoritedAt: now,
    })
  }
}

seedDefaults()

/** 供 HMR / 测试重置（一般无需调用） */
export function resetMockCampusRecipeFavorites(): void {
  seedDefaults()
}

export function isMockRecipeFavorited(recipeId: unknown): boolean {
  return byRecipeId.has(String(recipeId))
}

export function setMockRecipeFavorited(recipeId: unknown, favorited: boolean): void {
  const sid = String(recipeId)
  if (!sid) return
  if (favorited) {
    if (!byRecipeId.has(sid)) byRecipeId.set(sid, buildRow(sid))
  } else {
    byRecipeId.delete(sid)
  }
}

export function getMockRecipeFavoriteRows(): MockCampusFavoriteRow[] {
  return [...byRecipeId.values()].sort(
    (a, b) =>
      new Date(b.favoritedAt || 0).getTime() - new Date(a.favoritedAt || 0).getTime(),
  )
}

export function removeMockRecipeFavoritesByFavoriteIds(
  favoriteIds: readonly unknown[] | null | undefined,
): void {
  const set = new Set((favoriteIds ?? []).map(String).filter(Boolean))
  if (!set.size) return
  for (const [recipeId, row] of byRecipeId) {
    if (set.has(String(row.favoriteId))) byRecipeId.delete(recipeId)
  }
}
