import request from './request'
import { MOCK_NO_MATCH } from './mockTypes.js'

/** @typedef {{ id: number, name: string, efficacySummary: string, enabled: boolean }} IngredientRow */

export const PRESET_INGREDIENTS = [
  { id: 1, name: '山药', efficacySummary: '补脾养胃、生津益肺', enabled: true },
  { id: 2, name: '枸杞', efficacySummary: '滋补肝肾、益精明目', enabled: true },
  { id: 3, name: '银耳', efficacySummary: '滋阴润肺、养胃生津', enabled: true },
  { id: 4, name: '莲子', efficacySummary: '补脾止泻、养心安神', enabled: true },
  { id: 5, name: '百合', efficacySummary: '养阴润肺、清心安神', enabled: true },
  { id: 6, name: '雪梨', efficacySummary: '润燥生津、清热化痰', enabled: true },
  { id: 7, name: '黄芪', efficacySummary: '补气固表、利水消肿', enabled: true },
  { id: 8, name: '当归', efficacySummary: '补血活血、调经止痛', enabled: true },
  { id: 9, name: '红枣', efficacySummary: '补中益气、养血安神', enabled: true },
  { id: 10, name: '冰糖', efficacySummary: '补中益气、和胃润肺', enabled: true },
]

/** @type {IngredientRow[]} */
let ingredientStore = null

function ensureIngredientStore() {
  if (!ingredientStore) {
    ingredientStore = PRESET_INGREDIENTS.map((r) => ({ ...r }))
  }
  return ingredientStore
}

function nextIngredientId() {
  const rows = ensureIngredientStore()
  return rows.reduce((m, r) => Math.max(m, Number(r.id) || 0), 0) + 1
}

function parseMockBody(data) {
  if (data == null) return {}
  if (typeof data === 'string') {
    try {
      return JSON.parse(data)
    } catch {
      return {}
    }
  }
  return data
}

function normName(s) {
  return String(s ?? '').trim()
}

export function fetchIngredientList(params) {
  return request.get('/admin/ingredients', { params }).then((data) => {
    const raw = data?.records ?? data?.list ?? []
    const rows = raw.map((r) => ({
      ...r,
      efficacySummary: r.efficacySummary ?? r.note ?? '',
      enabled: r.enabled !== false && r.enabled !== 0,
    }))
    return { ...data, records: rows, list: rows }
  })
}

export function createIngredient(payload) {
  return request.post('/admin/ingredients', payload)
}

export function updateIngredient(id, payload) {
  return request.put(`/admin/ingredients/${id}`, payload)
}

export function deleteIngredient(id) {
  return request.delete(`/admin/ingredients/${id}`)
}

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchIngredientApiMock(method, path, config) {
  const listMatch = method === 'GET' && path === '/admin/ingredients'
  const createMatch = method === 'POST' && path === '/admin/ingredients'
  const idMatch = path.match(/^\/admin\/ingredients\/([^/]+)$/)
  const updateMatch = idMatch && method === 'PUT'
  const deleteMatch = idMatch && method === 'DELETE'

  const rows = ensureIngredientStore()

  if (listMatch) {
    const params = config.params || {}
    const only = params.enabled === true || params.enabled === 'true' || params.enabled === 1
    let list = rows.map((r) => ({ ...r }))
    if (only) list = list.filter((r) => r.enabled)
    return { records: list }
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
    const row = {
      id: nextIngredientId(),
      name,
      efficacySummary: normName(body.efficacySummary) || '—',
      enabled: body.enabled !== false,
    }
    rows.push(row)
    return { ingredient: { ...row } }
  }

  if (updateMatch || deleteMatch) {
    const id = Number(idMatch[1])
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
