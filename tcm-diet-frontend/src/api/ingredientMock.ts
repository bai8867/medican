import { MOCK_NO_MATCH } from './mockTypes'
import type { ApiMockRequestConfig } from './mockRequestConfig'
import { PRESET_INGREDIENTS, type IngredientRow } from './ingredientPreset'

let ingredientStore: IngredientRow[] | null = null

function ensureIngredientStore(): IngredientRow[] {
  if (!ingredientStore) {
    ingredientStore = PRESET_INGREDIENTS.map((r) => ({ ...r }))
  }
  return ingredientStore
}

function nextIngredientId(): number {
  const rows = ensureIngredientStore()
  return rows.reduce((m, r) => Math.max(m, Number(r.id) || 0), 0) + 1
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
  if (typeof data === 'object' && !Array.isArray(data)) {
    return data as Record<string, unknown>
  }
  return {}
}

function normName(s: unknown): string {
  return String(s ?? '').trim()
}

export function matchIngredientApiMock(
  method: string,
  path: string,
  config: ApiMockRequestConfig,
) {
  const listMatch = method === 'GET' && path === '/admin/ingredients'
  const createMatch = method === 'POST' && path === '/admin/ingredients'
  const idMatch = path.match(/^\/admin\/ingredients\/([^/]+)$/)
  const updateMatch = idMatch && method === 'PUT'
  const deleteMatch = idMatch && method === 'DELETE'

  const rows = ensureIngredientStore()

  if (listMatch) {
    const params = (config.params || {}) as Record<string, unknown>
    const onlyEnabled =
      params.enabled === true || params.enabled === 'true' || params.enabled === 1
    const onlyDisabled =
      params.enabled === false || params.enabled === 'false' || params.enabled === 0
    const kw = normName(params.keyword)
    let list = rows.map((r) => ({ ...r }))
    if (onlyEnabled) list = list.filter((r) => r.enabled !== false)
    if (onlyDisabled) list = list.filter((r) => r.enabled === false)
    if (kw) {
      list = list.filter((r) => {
        const name = normName(r.name)
        const sum = normName(r.efficacySummary)
        return name.includes(kw) || sum.includes(kw)
      })
    }
    const page = Math.max(1, Number(params.page) || 1)
    const rawSize = Number(params.page_size) || 10
    const pageSize = Math.min(100, Math.max(1, rawSize))
    const start = (page - 1) * pageSize
    const pageRecords = list.slice(start, start + pageSize)
    const total = list.length
    return {
      records: pageRecords,
      total,
      page,
      pageSize,
      hasMore: start + pageRecords.length < total,
    }
  }

  if (createMatch) {
    const body = parseMockBody(config.data)
    const name = normName(body.name)
    if (!name) {
      return { mockError: true, code: 400, message: '食材名称不能为空' }
    }
    if (rows.some((r) => normName(r.name) === name)) {
      return { mockError: true, code: 409, message: '食材名称已存在，请更换名称' }
    }
    const row: IngredientRow = {
      id: nextIngredientId(),
      name,
      efficacySummary: normName(body.efficacySummary) || '—',
      enabled: body.enabled !== false,
    }
    rows.push(row)
    return { ingredient: { ...row } }
  }

  if (updateMatch || deleteMatch) {
    const id = Number(idMatch![1])
    const idx = rows.findIndex((r) => Number(r.id) === id)
    if (idx === -1) {
      return { mockError: true, code: 404, message: '未找到该食材' }
    }
    if (deleteMatch) {
      rows.splice(idx, 1)
      return { ok: true }
    }
    const body = parseMockBody(config.data)
    const name = body.name != null ? normName(body.name) : rows[idx].name
    if (!name) {
      return { mockError: true, code: 400, message: '食材名称不能为空' }
    }
    const dup = rows.some((r, i) => i !== idx && normName(r.name) === name)
    if (dup) {
      return { mockError: true, code: 409, message: '食材名称已存在，请更换名称' }
    }
    rows[idx] = {
      ...rows[idx],
      name,
      efficacySummary:
        body.efficacySummary != null
          ? normName(body.efficacySummary) || '—'
          : rows[idx].efficacySummary,
      enabled: body.enabled !== undefined ? Boolean(body.enabled) : rows[idx].enabled,
    }
    return { ingredient: { ...rows[idx] } }
  }

  return MOCK_NO_MATCH
}
