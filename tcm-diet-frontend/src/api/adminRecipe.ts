import type { InternalAxiosRequestConfig } from 'axios'
import request from './request'
import { MOCK_NO_MATCH, type MockUnifiedRecipeRow } from './mockTypes'
import {
  getUnifiedRecipeMockStore,
  setUnifiedRecipeMockStore,
  inferSeasonFromFit,
  seasonFitFromSingle,
  type UnifiedMockRecipeRow,
} from '@/data/unifiedRecipeMockStore'

/** PRD 5.6.2：功效标签预设 */
export const ADMIN_EFFECT_TAG_OPTIONS = [
  '益气',
  '养阴',
  '健脾',
  '润肺',
  '祛湿',
  '安神',
  '养胃',
  '健脾润肺',
  '清热',
]

/** 九种体质（中文名，与测评/推荐一致） */
export const ADMIN_CONSTITUTION_OPTIONS = [
  '平和质',
  '气虚质',
  '阳虚质',
  '阴虚质',
  '痰湿质',
  '湿热质',
  '血瘀质',
  '气郁质',
  '特禀质',
]

/** 适用季节：表单与筛选项 */
export const ADMIN_SEASON_FORM_OPTIONS = [
  { value: 'spring', label: '春' },
  { value: 'summer', label: '夏' },
  { value: 'autumn', label: '秋' },
  { value: 'winter', label: '冬' },
  { value: 'all', label: '通用' },
]

export const ADMIN_SEASON_FILTER_OPTIONS = [
  { value: '', label: '全部季节' },
  ...ADMIN_SEASON_FORM_OPTIONS,
]

const DEFAULT_TABOO =
  '实热证、急性炎症期慎用；对所含食材或药材过敏者请勿食用。孕妇、哺乳期妇女及服药人群请先咨询医师。'

const STATUS = {
  ON: 'on_shelf',
  OFF: 'off_shelf',
}

/** 与后端 {@code ConstitutionLabelUtil} 一致：库表体质 code → 中文名 */
const CONSTITUTION_CODE_TO_LABEL: Record<string, string> = {
  pinghe: '平和质',
  qixu: '气虚质',
  yangxu: '阳虚质',
  yinxu: '阴虚质',
  tanshi: '痰湿质',
  shire: '湿热质',
  xueyu: '血瘀质',
  qiyu: '气郁质',
  tebing: '特禀质',
}

const CONSTITUTION_LABEL_TO_CODE = Object.fromEntries(
  Object.entries(CONSTITUTION_CODE_TO_LABEL).map(([code, label]) => [label, code]),
) as Record<string, string>

function splitCsv(s: unknown): string[] {
  if (s == null || typeof s !== 'string') return []
  return s.split(',').map((x) => x.trim()).filter(Boolean)
}

/**
 * 将 Spring 直接返回的 `Recipe` 实体（efficacyTags、constitutionTags 等）
 * 对齐为管理端表格/表单使用的 effectTags、suitConstitutions、season 等。
 */
export function normalizeAdminRecipeFromBackend(row: unknown): unknown {
  if (!row || typeof row !== 'object') return row
  const out = { ...(row as Record<string, unknown>) }

  if (!Array.isArray(out.effectTags) || !out.effectTags.length) {
    const fromDb = out.efficacyTags
    if (typeof fromDb === 'string' && fromDb.trim()) {
      out.effectTags = splitCsv(fromDb)
    } else {
      out.effectTags = []
    }
  }

  if ((!Array.isArray(out.suitConstitutions) || !out.suitConstitutions.length) && out.constitutionTags) {
    out.suitConstitutions = splitCsv(out.constitutionTags).map((c) => {
      const key = String(c).trim().toLowerCase()
      return CONSTITUTION_CODE_TO_LABEL[key] || String(c).trim()
    })
  }

  if (!out.season && out.seasonTags) {
    const fit = splitCsv(out.seasonTags).map((x) => String(x).trim().toLowerCase())
    out.seasonFit = fit.length ? fit : ['all']
    out.season = inferSeasonFromFit(out.seasonFit)
  }

  if (out.taboo == null || out.taboo === '') {
    if (out.contraindication) out.taboo = out.contraindication
  }
  out.summary = String(out.efficacySummary ?? out.summary ?? '').trim()
  out.instructionSummary = String(out.instructionSummary ?? '').trim()

  const st = out.status
  if (typeof st === 'number' || (typeof st === 'string' && /^\d+$/.test(st))) {
    const n = Number(st)
    if (n === 1) out.status = STATUS.ON
    else if (n === 0) out.status = STATUS.OFF
  }

  return out
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
  if (typeof data === 'object' && !Array.isArray(data)) return data as Record<string, unknown>
  return {}
}

function getStore() {
  return getUnifiedRecipeMockStore()
}

function nextId() {
  return `admin-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

function rowToListItem(row: UnifiedMockRecipeRow) {
  const fx = (row.effectTags as string[] | undefined) || []
  const suits = (row.suitConstitutions as string[] | undefined) || []
  return {
    id: row.id,
    name: row.name,
    effectTags: [...fx],
    suitConstitutions: [...suits],
    season: row.season || inferSeasonFromFit(row.seasonFit),
    collectCount: row.collectCount ?? 0,
    status: row.status === STATUS.OFF ? STATUS.OFF : STATUS.ON,
    coverUrl: row.coverUrl || '',
    updatedAt: row.updatedAt || null,
  }
}

function matchesFilters(row: UnifiedMockRecipeRow, q: Record<string, unknown>) {
  const kw = String(q.keyword || '')
    .trim()
    .toLowerCase()
  if (kw && !String(row.name || '').toLowerCase().includes(kw)) return false

  const effect = String(q.effectTag || '').trim()
  if (effect) {
    const tags = (row.effectTags as string[] | undefined) || []
    if (!tags.includes(effect)) return false
  }

  const season = String(q.season || '').trim()
  if (season) {
    const s = (row.season as string | undefined) || inferSeasonFromFit(row.seasonFit as string[] | undefined)
    if (s !== season) return false
  }

  const con = String(q.constitution || '').trim()
  if (con) {
    const suits = (row.suitConstitutions as string[] | undefined) || []
    if (!suits.includes(con)) return false
  }
  return true
}

function seasonTagsFromFormSeason(season: unknown) {
  if (!season || season === 'all') return 'spring,summer,autumn,winter'
  return String(season)
}

function constitutionLabelsToTagsCsv(labels: unknown) {
  if (!Array.isArray(labels)) return ''
  const parts: string[] = []
  for (const x of labels) {
    const s = String(x || '').trim()
    if (!s) continue
    const code = CONSTITUTION_LABEL_TO_CODE[s]
    if (code) parts.push(code)
    else if (/^[a-z]{2,20}$/i.test(s)) parts.push(s.toLowerCase())
  }
  return [...new Set(parts)].join(',')
}

function flattenIngredientsFromGroups(groups: unknown) {
  const out: { name: string; amount: string; note?: unknown }[] = []
  if (!Array.isArray(groups)) return out
  for (const g of groups as Record<string, unknown>[]) {
    const items = (Array.isArray(g.items) ? g.items : []) as Record<string, unknown>[]
    for (const it of items) {
      const name = String(it.name || '').trim()
      if (!name) continue
      const o: { name: string; amount: string; note?: unknown } = {
        name,
        amount: String(it.amount || '').trim(),
      }
      if (it.note != null && it.note !== '') o.note = it.note
      out.push(o)
    }
  }
  return out
}

function buildStepsJsonForRecipeSave(payload: Record<string, unknown>) {
  const steps = ((payload.steps as unknown[]) || [])
    .map((s: unknown) => {
      const o = typeof s === 'object' && s ? (s as Record<string, unknown>) : {}
      const text = String(o.text ?? o.description ?? '').trim()
      return text ? { text } : null
    })
    .filter(Boolean)
  const ingredients = flattenIngredientsFromGroups(payload.ingredientGroups)
  return JSON.stringify({ steps, ingredients })
}

/**
 * 管理端表单载荷 → Spring {@code RecipeSave}（真实 HTTP 时使用；Mock 仍走扁平结构）。
 * @param {object} payload
 * @param {{ sceneIds?: number[] }} [opts]
 */
export function adminFormToRecipeSave(
  payload: Record<string, unknown>,
  opts: { sceneIds?: number[] } = {},
): Record<string, unknown> {
  const collectCount =
    payload.collectCount != null && Number.isFinite(Number(payload.collectCount))
      ? Math.max(0, Math.floor(Number(payload.collectCount)))
      : 0
  const st = payload.status === STATUS.OFF ? 0 : 1
  const sceneIds = opts.sceneIds !== undefined ? opts.sceneIds : (payload.sceneIds as number[] | undefined)
  const o: Record<string, unknown> = {
    name: String(payload.name || '').trim(),
    coverUrl: String(payload.coverUrl || '').trim(),
    efficacySummary: String(payload.summary || '').trim(),
    instructionSummary: String(payload.instructionSummary || '').trim(),
    collectCount,
    seasonTags: seasonTagsFromFormSeason(payload.season),
    constitutionTags: constitutionLabelsToTagsCsv(payload.suitConstitutions || []),
    efficacyTags: ((payload.effectTags as unknown[]) || [])
      .map((x: unknown) => String(x).trim())
      .filter(Boolean)
      .join(','),
    contraindication: String(payload.taboo || '').trim(),
    stepsJson: buildStepsJsonForRecipeSave(payload),
    status: st,
  }
  if (Array.isArray(sceneIds) && sceneIds.length) {
    o.sceneIds = sceneIds.map((x) => Number(x)).filter((n) => Number.isFinite(n))
  }
  return o
}

function useAdminRecipeLegacyWireFormat() {
  return import.meta.env.VITE_USE_MOCK === 'true'
}

function recipeSaveDtoToLegacyForm(b: Record<string, unknown>) {
  let env: Record<string, unknown> = {}
  try {
    env = JSON.parse(String(b.stepsJson || '{}')) as Record<string, unknown>
  } catch {
    env = {}
  }
  const steps = (Array.isArray(env.steps) ? env.steps : []).map((s: unknown, idx: number) => ({
    order: idx + 1,
    description: String(
      typeof s === 'string' ? s : ((s as Record<string, unknown>).text ?? (s as Record<string, unknown>).description ?? ''),
    ).trim(),
    tip: typeof s === 'object' && s ? (s as Record<string, unknown>).tip : undefined,
  })).filter((s) => s.description)

  const items = (Array.isArray(env.ingredients) ? env.ingredients : []).map((it: unknown) => {
    const row = it as Record<string, unknown>
    return {
      name: String(row.name || '').trim(),
      amount: String(row.amount || '').trim(),
      note: row.note,
    }
  }).filter((it) => it.name)

  const ingredientGroups = items.length ? [{ key: 'main', label: '主料', items }] : []

  const suitCodes = splitCsv(b.constitutionTags)
  const suitConstitutions = suitCodes
    .map((c) => {
      const key = String(c).trim().toLowerCase()
      return CONSTITUTION_CODE_TO_LABEL[key] || String(c).trim()
    })
    .filter(Boolean)

  const seasonFit = splitCsv(b.seasonTags)
    .map((x) => String(x).trim().toLowerCase())
    .filter(Boolean)
  const season = inferSeasonFromFit(seasonFit.length ? seasonFit : ['all'])

  return {
    name: b.name,
    coverUrl: b.coverUrl,
    taboo: String(b.contraindication || '').trim(),
    summary: String(b.efficacySummary || '').trim(),
    instructionSummary: String(b.instructionSummary || '').trim(),
    effectTags: splitCsv(b.efficacyTags),
    suitConstitutions,
    season,
    status: Number(b.status) === 0 ? STATUS.OFF : STATUS.ON,
    ingredientGroups,
    steps,
    collectCount: b.collectCount,
  }
}

function normalizeIncomingRecipeBody(body: unknown): unknown {
  if (!body || typeof body !== 'object') return body
  const b = body as Record<string, unknown>
  if (Array.isArray(b.ingredientGroups)) return body
  if (typeof b.stepsJson === 'string' && b.stepsJson.trim().startsWith('{')) {
    return recipeSaveDtoToLegacyForm(b)
  }
  return body
}

function normalizePayload(body: unknown, existingId: unknown) {
  const raw = normalizeIncomingRecipeBody(body) as Record<string, unknown>
  const name = String(raw.name || '').trim()
  const taboo = String(raw.taboo || '').trim()
  const status = raw.status === STATUS.OFF ? STATUS.OFF : STATUS.ON
  const seasonRaw = raw.season
  const season =
    typeof seasonRaw === 'string' && ['spring', 'summer', 'autumn', 'winter', 'all'].includes(seasonRaw)
      ? seasonRaw
      : 'all'
  const effectTags = Array.isArray(raw.effectTags)
    ? raw.effectTags.map((x) => String(x).trim()).filter(Boolean)
    : []
  const suitConstitutions = Array.isArray(raw.suitConstitutions)
    ? raw.suitConstitutions.map((x) => String(x).trim()).filter(Boolean)
    : []
  const coverUrl = String(raw.coverUrl || '').trim()

  const ingredientGroupsIn = Array.isArray(raw.ingredientGroups)
    ? (raw.ingredientGroups as Record<string, unknown>[])
    : []
  const ingredientGroups = ingredientGroupsIn
    .map((g: Record<string, unknown>) => ({
      key: g.key || 'main',
      label: g.label || '',
      items: (Array.isArray(g.items) ? g.items : [])
        .map((it: unknown) => {
          const row = it as Record<string, unknown>
          return {
            name: String(row.name || '').trim(),
            amount: String(row.amount || '').trim(),
            note: row.note,
          }
        })
        .filter((it) => it.name),
    }))
    .filter((g) => g.items.length)

  const stepsIn = Array.isArray(raw.steps) ? (raw.steps as Record<string, unknown>[]) : []
  const steps = stepsIn
    .map((s: Record<string, unknown>, idx: number) => ({
      order: Number(s.order) || idx + 1,
      description: String(s.description ?? s.text ?? '').trim(),
      tip: s.tip,
    }))
    .filter((s) => s.description)

  const collectCount =
    existingId != null && Number.isFinite(Number(raw.collectCount))
      ? Math.max(0, Math.floor(Number(raw.collectCount)))
      : undefined

  return {
    id: existingId,
    name,
    /** 保存时须由表单显式填写，不在此处回填默认文案 */
    taboo,
    status,
    season,
    seasonFit: seasonFitFromSingle(season),
    effectTags,
    suitConstitutions,
    coverUrl,
    ingredientGroups,
    steps: steps.map((s, i) => ({ ...s, order: i + 1 })),
    summary: String(raw.summary || '').trim(),
    instructionSummary: String(raw.instructionSummary || '').trim(),
    effect: String(raw.effect || '').trim(),
    collectCount,
  }
}

/**
 * PRD 5.6.2.2 对齐的管理端药膳结构（列表项 / 详情子集）
 * @typedef {object} AdminRecipeListItem
 * @property {string} id
 * @property {string} name
 * @property {string[]} effectTags
 * @property {string[]} suitConstitutions
 * @property {string} season
 * @property {number} collectCount
 * @property {'on_shelf'|'off_shelf'} status
 * @property {string} [coverUrl]
 * @property {string|null} [updatedAt]
 */

/** 分页列表 GET /admin/recipes */
export function fetchAdminRecipePage(params: Record<string, unknown> = {}) {
  const p: Record<string, unknown> = { ...params }
  if (p.pageSize != null && p.page_size == null) {
    p.page_size = p.pageSize
    delete p.pageSize
  }
  return request.get('/admin/recipes', { params: p }).then((data: unknown) => {
    const d = data as Record<string, unknown>
    const rows = d?.records || d?.list
    if (!Array.isArray(rows)) return data
    const mapped = rows.map((r: unknown) => normalizeAdminRecipeFromBackend(r))
    return { ...(d as object), records: mapped, list: mapped }
  })
}

/** 详情 GET /admin/recipes/:id */
export function fetchAdminRecipeDetail(id: string | number) {
  return request.get(`/admin/recipes/${id}`).then((data: unknown) => {
    if (data && typeof data === 'object') {
      const d = data as Record<string, unknown>
      if (d.recipe != null) {
        return { ...d, recipe: normalizeAdminRecipeFromBackend(d.recipe) }
      }
    }
    return normalizeAdminRecipeFromBackend(data)
  })
}

/** 新增 POST /admin/recipes */
export function createAdminRecipe(payload: Record<string, unknown>) {
  const body = useAdminRecipeLegacyWireFormat() ? payload : adminFormToRecipeSave(payload)
  return request.post('/admin/recipes', body)
}

/** 更新 PUT /admin/recipes/:id */
export function updateAdminRecipe(id: string | number, payload: Record<string, unknown>) {
  const body = useAdminRecipeLegacyWireFormat() ? payload : adminFormToRecipeSave(payload)
  return request.put(`/admin/recipes/${id}`, body)
}

export function updateAdminRecipeStatus(id: string | number, status: unknown) {
  return request.patch(`/admin/recipes/${id}/status`, { status })
}

/** 删除 DELETE /admin/recipes/:id */
export function deleteAdminRecipe(id: string | number) {
  return request.delete(`/admin/recipes/${id}`)
}

/** 批量删除 DELETE /admin/recipes body: { ids: string[] } */
export function batchDeleteAdminRecipes(ids: unknown[]) {
  return request.delete('/admin/recipes', { data: { ids } })
}

/**
 * 封面上传（Mock：JSON 传 dataUrl；真实后端可改为 multipart）
 * POST /admin/recipes/cover
 */
export function uploadAdminRecipeCoverDataUrl(dataUrl: string, fileName: string) {
  return request.post(
    '/admin/recipes/cover',
    { dataUrl, fileName },
    { skipGlobalMessage: true },
  )
}

export function matchAdminRecipeApiMock(
  method: string,
  path: string,
  config: InternalAxiosRequestConfig,
): typeof MOCK_NO_MATCH | Record<string, unknown> {
  if (method === 'POST' && path === '/admin/recipes/cover') {
    const body = parseMockBody(config.data)
    const dataUrl = typeof body.dataUrl === 'string' ? body.dataUrl : ''
    if (!dataUrl || !dataUrl.startsWith('data:image/')) {
      return { mockError: true, code: 400, message: '请上传有效的图片数据' }
    }
    return { url: dataUrl }
  }

  if (method === 'GET' && path === '/admin/recipes') {
    const p = (config.params || {}) as Record<string, unknown>
    const page = Math.max(1, parseInt(String(p.page ?? ''), 10) || 1)
    const rawPs = p.pageSize ?? p.page_size
    const pageSize = Math.min(200, Math.max(1, parseInt(String(rawPs ?? ''), 10) || 10))
    const q = {
      keyword: p.name ?? p.keyword ?? '',
      effectTag: p.effectTag ?? '',
      season: p.season ?? '',
      constitution: p.constitution ?? p.suitConstitution ?? '',
    }
    const store = getStore()
    const filtered = store.filter((row: MockUnifiedRecipeRow) => matchesFilters(row, q))
    const total = filtered.length
    const start = (page - 1) * pageSize
    const slice = filtered.slice(start, start + pageSize).map(rowToListItem)
    return { records: slice, total, page, pageSize }
  }

  const oneMatch = path.match(/^\/admin\/recipes\/([^/]+)$/)
  if (oneMatch && oneMatch[1] !== 'cover') {
    const id = oneMatch[1]
    const store = getStore()
    const idx = store.findIndex((r: MockUnifiedRecipeRow) => String(r.id) === String(id))

    if (method === 'GET') {
      if (idx < 0) return { mockError: true, code: 404, message: '药膳不存在' }
      return { recipe: JSON.parse(JSON.stringify(store[idx])) }
    }

    if (method === 'PUT') {
      if (idx < 0) return { mockError: true, code: 404, message: '药膳不存在' }
      const body = parseMockBody(config.data)
      const n = normalizePayload(body, id)
      if (!n.name) return { mockError: true, code: 400, message: '药膳名称不能为空' }
      if (!n.taboo) return { mockError: true, code: 400, message: '禁忌提醒不能为空' }
      if (!n.steps.length) return { mockError: true, code: 400, message: '请至少填写一步制作步骤' }
      if (!n.ingredientGroups.length) return { mockError: true, code: 400, message: '请至少填写一项食材' }
      const prev = store[idx]
      const merged = {
        ...prev,
        ...n,
        id: prev.id,
        collectCount:
          n.collectCount !== undefined ? n.collectCount : prev.collectCount ?? 0,
        updatedAt: new Date().toISOString(),
      }
      store[idx] = merged
      return { recipe: JSON.parse(JSON.stringify(merged)) }
    }

    if (method === 'DELETE') {
      if (idx < 0) return { mockError: true, code: 404, message: '药膳不存在' }
      store.splice(idx, 1)
      return { ok: true }
    }
  }

  const statusPatchMatch = path.match(/^\/admin\/recipes\/([^/]+)\/status$/)
  if (method === 'PATCH' && statusPatchMatch) {
    const id = statusPatchMatch[1]
    const store = getStore()
    const idx = store.findIndex((r: MockUnifiedRecipeRow) => String(r.id) === String(id))
    if (idx < 0) return { mockError: true, code: 404, message: '药膳不存在' }
    const body = parseMockBody(config.data)
    const nextStatus = body?.status === STATUS.OFF || body?.status === 0 ? STATUS.OFF : STATUS.ON
    store[idx] = { ...store[idx], status: nextStatus, updatedAt: new Date().toISOString() }
    return { recipe: JSON.parse(JSON.stringify(store[idx])) }
  }

  if (method === 'POST' && path === '/admin/recipes') {
    const body = parseMockBody(config.data)
    const n = normalizePayload(body, null)
    if (!n.name) return { mockError: true, code: 400, message: '药膳名称不能为空' }
    if (!n.taboo) return { mockError: true, code: 400, message: '禁忌提醒不能为空' }
    if (!n.steps.length) return { mockError: true, code: 400, message: '请至少填写一步制作步骤' }
    if (!n.ingredientGroups.length) return { mockError: true, code: 400, message: '请至少填写一项食材' }
    const store = getStore()
    const id = nextId()
    const row = {
      ...n,
      id,
      collectCount: 0,
      cookTime: body.cookTime || '',
      difficulty: body.difficulty || '',
      recommendReason: body.recommendReason || '',
      ingredients: [],
      updatedAt: new Date().toISOString(),
    }
    store.unshift(row)
    return { recipe: JSON.parse(JSON.stringify(row)) }
  }

  if (method === 'DELETE' && path === '/admin/recipes') {
    const body = parseMockBody(config.data)
    const ids = Array.isArray(body.ids) ? (body.ids as unknown[]).map((x) => String(x)) : []
    if (!ids.length) return { mockError: true, code: 400, message: '请选择要删除的药膳' }
    const store = getStore()
    const set = new Set(ids)
    const next = store.filter((r: MockUnifiedRecipeRow) => !set.has(String(r.id)))
    const removed = store.length - next.length
    if (!removed) return { mockError: true, code: 404, message: '未找到可删除的记录' }
    setUnifiedRecipeMockStore(next)
    return { deleted: removed }
  }

  return MOCK_NO_MATCH
}

export { STATUS as ADMIN_RECIPE_STATUS }
