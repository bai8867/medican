import type { InternalAxiosRequestConfig } from 'axios'
import request from './request'
import { MOCK_NO_MATCH } from './mockTypes'
import { MOCK_RECIPES } from '@/data/recommendMock'
import { recipeSchematicCoverUrl } from '../utils/recipeCoverPlaceholder'
import {
  getMockRecipeFavoriteRows,
  removeMockRecipeFavoritesByFavoriteIds,
} from './mockCampusRecipeFavorites'

/** GET /user/favorites 聚合后的药膳收藏行（与后端分页 records 对齐） */
export interface ProfileRecipeFavoriteRow {
  favoriteId: string
  recipeId: string
  name: string
  coverUrl: string
  effect: string
  /** 原始菜谱对象，字段以后端为准 */
  recipe: Record<string, unknown>
  favoritedAt: number
}

export interface ProfileAiPlanFavoriteRow {
  favoriteId: string
  planId: string
  name: string
  coverUrl: string
  intro?: string
  favoritedAt: number
}

export interface ProfileFavoritesPack {
  recipeFavorites: ProfileRecipeFavoriteRow[]
  aiPlanFavorites: ProfileAiPlanFavoriteRow[]
}

/** normalizeFavoriteRecipes 输出项 */
export interface NormalizedRecipeFavorite {
  favoriteId: string
  recipeId: string
  name: string
  coverUrl: string
  subtitle: string
  favoritedAt: number
}

/** normalizeFavoriteAiPlans 输出项 */
export interface NormalizedAiPlanFavorite {
  favoriteId: string
  planId: string
  name: string
  coverUrl: string
  subtitle: string
  favoritedAt: number
}

/** normalizeBrowseHistory 输出项 */
export interface NormalizedBrowseHistoryItem {
  historyId: string
  type: 'recipe' | 'ai_plan'
  targetId: string
  name: string
  coverUrl: string
  subtitle: string
  viewedAt: number
}

/** 与 normalize* 所接受的松散载荷对齐 */
export type FavoriteListPayload = Record<string, unknown> | unknown[]

/** 后端字段兼容：列表可能是数组或 { list|items|records } */
function asList(raw: unknown): unknown[] {
  if (Array.isArray(raw)) return raw
  if (raw && typeof raw === 'object') {
    const o = raw as Record<string, unknown>
    const v = o.list ?? o.items ?? o.records
    return Array.isArray(v) ? v : []
  }
  return []
}

function numTime(v: unknown): number {
  if (v == null) return 0
  if (typeof v === 'number' && Number.isFinite(v)) return v
  const t = new Date(v as string | number | Date).getTime()
  return Number.isFinite(t) ? t : 0
}

/**
 * 收藏列表：GET /api/user/favorites（分页），归一为 Profile 页可用的聚合结构。
 */
export async function fetchProfileFavorites(): Promise<ProfileFavoritesPack> {
  const page = (await request.get('/user/favorites', {
    params: { page: 1, page_size: 200 },
    skipGlobalMessage: true,
  })) as { records?: Array<Record<string, unknown>> }
  const records = page?.records || []
  const recipeFavorites: ProfileRecipeFavoriteRow[] = records.map((row) => ({
    favoriteId: `recipe:${row.id}`,
    recipeId: String(row.id ?? ''),
    name: String(row.name ?? ''),
    coverUrl: recipeSchematicCoverUrl(String(row.name ?? '药膳')),
    effect: String(row.efficacySummary ?? ''),
    recipe: row,
    favoritedAt: 0,
  }))
  return { recipeFavorites, aiPlanFavorites: [] }
}

export function normalizeFavoriteRecipes(payload: FavoriteListPayload): NormalizedRecipeFavorite[] {
  const data = (payload || {}) as Record<string, unknown>
  const raw =
    asList(data.recipeFavorites) ||
    asList(data.recipe_favorites) ||
    asList(data.recipes) ||
    asList(data.recipeList)
  return raw
    .map((row) => normalizeRecipeFavoriteRow(row as Record<string, unknown>))
    .sort((a, b) => numTime(b.favoritedAt) - numTime(a.favoritedAt))
}

export function normalizeFavoriteAiPlans(payload: FavoriteListPayload): NormalizedAiPlanFavorite[] {
  const data = (payload || {}) as Record<string, unknown>
  const raw =
    asList(data.aiPlanFavorites) ||
    asList(data.ai_plan_favorites) ||
    asList(data.aiPlans) ||
    asList(data.ai_plans)
  return raw
    .map((row) => normalizeAiPlanFavoriteRow(row as Record<string, unknown>))
    .sort((a, b) => numTime(b.favoritedAt) - numTime(a.favoritedAt))
}

function normalizeRecipeFavoriteRow(row: Record<string, unknown>): NormalizedRecipeFavorite {
  const r = (row.recipe || row.target || {}) as Record<string, unknown>
  const recipeId = row.recipeId ?? row.targetId ?? r.id ?? row.id
  const favoriteId = String(row.favoriteId ?? row.id ?? `recipe:${recipeId}`)
  const name = String(row.name || r.name || '未命名药膳')
  return {
    favoriteId,
    recipeId: String(recipeId ?? ''),
    name,
    coverUrl: recipeSchematicCoverUrl(name),
    subtitle: String(
      row.effect || row.coreEffect || row.summary || r.effect || r.summary || '',
    ),
    favoritedAt: Number(
      row.favoritedAt || row.collectedAt || row.createdAt || row.updatedAt || 0,
    ),
  }
}

function normalizeAiPlanFavoriteRow(row: Record<string, unknown>): NormalizedAiPlanFavorite {
  const p = (row.plan || row.target || {}) as Record<string, unknown>
  const planId = row.planId ?? row.aiPlanId ?? row.targetId ?? p.id ?? row.id
  const favoriteId = String(row.favoriteId ?? row.id ?? `ai:${planId}`)
  return {
    favoriteId,
    planId: String(planId ?? ''),
    name: String(row.name || row.title || p.name || p.title || 'AI 方案'),
    coverUrl: String(row.coverUrl || row.cover || p.coverUrl || p.cover || ''),
    subtitle: String(row.intro || row.summary || row.description || p.intro || p.summary || ''),
    favoritedAt: Number(
      row.favoritedAt || row.collectedAt || row.createdAt || row.updatedAt || 0,
    ),
  }
}

/**
 * 批量取消药膳收藏：依次 DELETE /api/user/favorites/{recipeId}
 * favoriteId 形如 recipe:123 或即为 recipeId
 */
export async function deleteRecipeFavorites(favoriteIds: string[]): Promise<{ deleted: boolean }> {
  const ids = Array.isArray(favoriteIds) ? favoriteIds : []
  for (const fid of ids) {
    const s = String(fid ?? '')
    const recipeId = s.includes(':') ? s.slice(s.lastIndexOf(':') + 1) : s
    if (!recipeId) continue
    await request.delete(`/user/favorites/${encodeURIComponent(recipeId)}`, {
      skipGlobalMessage: true,
    })
  }
  return { deleted: true }
}

/** 批量取消 AI 方案收藏：DELETE /profile/favorites/ai-plans */
export function deleteAiPlanFavorites(favoriteIds: string[]) {
  return request.delete('/profile/favorites/ai-plans', {
    data: { favoriteIds, ids: favoriteIds },
    skipGlobalMessage: true,
  })
}

/** GET /api/user/history */
export function fetchBrowseHistory(params?: { limit?: number }) {
  return request.get('/user/history', {
    params: { limit: params?.limit ?? 10 },
    skipGlobalMessage: true,
  })
}

/** POST /api/user/history（服务端数字 recipeId） */
export function recordBrowseHistory(recipeId: string | number) {
  return request.post('/user/history', { recipeId: String(recipeId) }, { skipGlobalMessage: true })
}

export function normalizeBrowseHistory(payload: FavoriteListPayload): NormalizedBrowseHistoryItem[] {
  if (Array.isArray(payload)) {
    return payload
      .map((x) => normalizeHistoryRow(x as Record<string, unknown>))
      .sort((a, b) => numTime(b.viewedAt) - numTime(a.viewedAt))
  }
  const data = (payload || {}) as Record<string, unknown>
  const raw =
    asList(data.items) ||
    asList(data.list) ||
    asList(data.records) ||
    asList(data.histories) ||
    asList(data)
  return raw
    .map((x) => normalizeHistoryRow(x as Record<string, unknown>))
    .sort((a, b) => numTime(b.viewedAt) - numTime(a.viewedAt))
}

function normalizeHistoryRow(row: Record<string, unknown>): NormalizedBrowseHistoryItem {
  const meta = (row.target || row.recipe || row.plan || {}) as Record<string, unknown>
  const typeRaw = row.type || row.resourceType || row.kind || 'recipe'
  const type = String(typeRaw).toLowerCase().includes('ai') ? 'ai_plan' : 'recipe'
  const targetId = String(row.targetId ?? row.recipeId ?? row.planId ?? meta.id ?? '')
  const name = String(row.name || meta.name || meta.title || '未命名')
  const viewedAtRaw =
    row.viewedAt ?? row.lastViewedAt ?? row.updatedAt ?? row.createdAt ?? 0
  const viewedAt =
    typeof viewedAtRaw === 'number' && Number.isFinite(viewedAtRaw)
      ? viewedAtRaw
      : numTime(viewedAtRaw)
  return {
    historyId: String(row.historyId ?? row.id ?? `${type}:${targetId}:${viewedAt}`),
    type,
    targetId,
    name,
    coverUrl:
      type === 'recipe'
        ? typeof row.coverUrl === 'string' && row.coverUrl.trim()
          ? row.coverUrl
          : recipeSchematicCoverUrl(name)
        : String(row.coverUrl || row.cover || meta.coverUrl || meta.cover || ''),
    subtitle: String(
      row.subtitle ||
        row.summary ||
        meta.summary ||
        meta.effect ||
        meta.intro ||
        row.efficacySummary ||
        '',
    ),
    viewedAt,
  }
}

/** 删除单条浏览记录：DELETE /api/user/history/:id */
export function deleteBrowseHistoryItem(historyId: string) {
  return request.delete(`/user/history/${encodeURIComponent(historyId)}`, {
    skipGlobalMessage: true,
  })
}

/** 清空浏览历史：DELETE /api/user/history */
export function clearBrowseHistory() {
  return request.delete('/user/history', { skipGlobalMessage: true })
}

function parseMockBody(data: unknown): Record<string, unknown> {
  if (data == null) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data) as Record<string, unknown>
    } catch {
      return {}
    }
  }
  return typeof data === 'object' && !Array.isArray(data) ? (data as Record<string, unknown>) : {}
}

export function matchProfileApiMock(
  method: string,
  path: string,
  config?: InternalAxiosRequestConfig,
): unknown {
  if (method === 'GET' && path === '/user/profile') {
    const rows = {
      pinghe: 3,
      qixu: 4,
      yangxu: 3,
      yinxu: 3,
      tanshi: 3,
      shire: 3,
      xueyu: 3,
      qiyu: 3,
      tebing: 3,
    }
    return {
      userId: 1,
      username: 'mock',
      role: 'USER',
      constitutionCode: 'qixu',
      seasonCode: 'spring',
      recommendEnabled: true,
      dataCollectionEnabled: true,
      surveyScoresJson: JSON.stringify(rows),
    }
  }

  if (method === 'GET' && (path === '/profile/favorites' || path === '/user/favorites')) {
    const recipeFavorites = getMockRecipeFavoriteRows()
    const now = Date.now()
    return {
      recipeFavorites,
      aiPlanFavorites: [
        {
          favoriteId: 'mock-fav-ai-1',
          planId: 'mock-ai-plan-001',
          name: '润燥养阴试吃方案',
          coverUrl: '',
          intro: 'Mock：收藏的 AI 食疗方案',
          favoritedAt: now,
        },
      ],
    }
  }

  if (method === 'DELETE' && path === '/profile/favorites/recipes') {
    const body = parseMockBody(config?.data)
    const ids = (body.favoriteIds || body.ids) as unknown
    removeMockRecipeFavoritesByFavoriteIds(Array.isArray(ids) ? (ids as string[]) : [])
    return { deleted: true }
  }

  const userFavDel = path.match(/^\/user\/favorites\/([^/]+)$/)
  if (method === 'DELETE' && userFavDel) {
    removeMockRecipeFavoritesByFavoriteIds([decodeURIComponent(userFavDel[1])])
    return { deleted: true }
  }

  if (method === 'DELETE' && path === '/profile/favorites/ai-plans') {
    return { deleted: true }
  }

  if (method === 'POST' && path === '/user/history') {
    return { ok: true }
  }

  if (method === 'GET' && (path === '/profile/browse-history' || path === '/user/history')) {
    const r = MOCK_RECIPES[0] as Record<string, unknown> | undefined
    const viewedAt = Date.now()
    const recipeRow = r
      ? {
          historyId: 'mock-hist-recipe-1',
          type: 'recipe' as const,
          targetId: String(r.id),
          name: String(r.name ?? ''),
          coverUrl: String(r.coverUrl || ''),
          subtitle: String(r.effect || r.summary || ''),
          viewedAt,
        }
      : null
    const list = [
      ...(recipeRow ? [recipeRow] : []),
      {
        historyId: 'mock-hist-ai-1',
        type: 'ai_plan' as const,
        targetId: 'mock-ai-plan-001',
        name: '润燥养阴试吃方案',
        coverUrl: '',
        subtitle: 'Mock 浏览记录',
        viewedAt: viewedAt - 3600000,
      },
    ]
    return { items: list, records: list }
  }

  if (method === 'DELETE' && (path === '/profile/browse-history' || path === '/user/history')) {
    return { cleared: true }
  }

  const histOne = path.match(/^\/profile\/browse-history\/([^/]+)$/)
  if (method === 'DELETE' && histOne) {
    return { deleted: true, id: decodeURIComponent(histOne[1]) }
  }

  const userHistOne = path.match(/^\/user\/history\/([^/]+)$/)
  if (method === 'DELETE' && userHistOne) {
    return { deleted: true, id: decodeURIComponent(userHistOne[1]) }
  }

  return MOCK_NO_MATCH
}
