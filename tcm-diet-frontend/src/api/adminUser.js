import request from './request.js'
import { MOCK_NO_MATCH } from './mockTypes.js'
import { CONSTITUTION_TYPES } from '@/stores/user.js'
import { SEASON_OPTIONS } from '@/utils/season.js'
import { MOCK_RECIPES } from '@/data/recommendMock.js'
import { readUserStatusMap, setUserStatus } from '@/utils/adminUserStatus.js'

function labelForConstitution(code) {
  if (!code) return '未设置'
  return CONSTITUTION_TYPES.find((c) => c.code === code)?.label || code
}

function labelForSeason(code) {
  return SEASON_OPTIONS.find((s) => s.code === code)?.label || code || ''
}

function readPersistedUserState() {
  try {
    const raw = localStorage.getItem('tcm_user_profile')
    const o = raw ? JSON.parse(raw) : null
    return o && typeof o === 'object' ? o : {}
  } catch {
    return {}
  }
}

function readCollectState() {
  try {
    const raw = localStorage.getItem('tcm_collect')
    const o = raw ? JSON.parse(raw) : null
    return o && typeof o === 'object' ? o : {}
  } catch {
    return {}
  }
}

function recipeTitle(id) {
  const sid = String(id)
  const hit = MOCK_RECIPES.find((r) => r.id === sid)
  return hit?.name || `药膳 ${sid}`
}

/** @type {import('./mockTypes.js').NormalizedMockResult['data']} */
const SEED_USERS = [
  {
    id: 'campus-u-10001',
    constitutionCode: 'qixu',
    registeredAt: '2025-09-12T08:20:00.000Z',
    defaultStatus: 'active',
  },
  {
    id: 'campus-u-10002',
    constitutionCode: 'tanshi',
    registeredAt: '2025-10-01T14:05:00.000Z',
    defaultStatus: 'active',
  },
  {
    id: 'campus-u-10003',
    constitutionCode: 'yinxu',
    registeredAt: '2025-11-18T19:40:00.000Z',
    defaultStatus: 'disabled',
  },
  {
    id: 'campus-u-10004',
    constitutionCode: 'pinghe',
    registeredAt: '2026-01-03T11:15:00.000Z',
    defaultStatus: 'active',
  },
  {
    id: 'campus-u-10005',
    constitutionCode: 'yangxu',
    registeredAt: '2026-02-20T09:00:00.000Z',
    defaultStatus: 'active',
  },
]

function effectiveStatus(userId, defaultStatus) {
  const map = readUserStatusMap()
  const id = String(userId)
  if (Object.prototype.hasOwnProperty.call(map, id)) {
    return map[id] === 'disabled' ? 'disabled' : 'active'
  }
  return defaultStatus || 'active'
}

function buildClientListRow() {
  const st = readPersistedUserState()
  const id = String(st.userId || '').trim()
  if (!id) return null
  const code = st.constitutionCode || ''
  const reg = st.registeredAt || new Date().toISOString()
  return {
    id,
    constitutionCode: code,
    registeredAt: reg,
    defaultStatus: 'active',
  }
}

function surveyScoresFor(code) {
  if (!code || !CONSTITUTION_TYPES.some((c) => c.code === code)) {
    return {
      constitutionTendency: {},
      groupAverages: {},
      submittedAt: null,
    }
  }
  const base = Object.fromEntries(CONSTITUTION_TYPES.map((c) => [c.code, 0]))
  base[code] = 72
  const second = code === 'pinghe' ? 'qixu' : 'pinghe'
  base[second] = 48
  return {
    /** 各体质倾向得分（示意） */
    constitutionTendency: base,
    /** 问卷原始分组均分（示意） */
    groupAverages: { 气与神: 4.1, 寒热: 3.6, 湿与痰: 3.9 },
    submittedAt: '2026-01-10T12:00:00.000Z',
  }
}

function seedDetail(row) {
  const code = row.constitutionCode || 'pinghe'
  const seasonCode = code === 'yinxu' ? 'autumn' : 'spring'
  const favRecipes = MOCK_RECIPES.filter((_, i) => i < 2).map((r) => ({
    recipeId: r.id,
    name: r.name,
    favoritedAt: '2026-02-01T10:00:00.000Z',
  }))
  return {
    id: row.id,
    constitutionCode: code,
    constitutionLabel: labelForConstitution(code),
    constitutionSource: 'questionnaire',
    seasonCode,
    seasonLabel: labelForSeason(seasonCode),
    surveyScores: surveyScoresFor(code),
    favorites: {
      recipes: favRecipes,
      aiPlans: [
        {
          planId: 'plan-demo-01',
          name: '期末周气虚调理方案',
          favoritedAt: '2026-02-05T16:30:00.000Z',
        },
      ],
    },
    registeredAt: row.registeredAt,
    status: effectiveStatus(row.id, row.defaultStatus),
  }
}

function clientDetailFromLocal(row) {
  const st = readPersistedUserState()
  const col = readCollectState()
  const recipeIds = Array.isArray(col.recipeIds) ? col.recipeIds : []
  const aiPlanIds = Array.isArray(col.aiPlanIds) ? col.aiPlanIds : []
  const code = st.constitutionCode || row.constitutionCode || ''
  const seasonCode = st.seasonCode || 'spring'
  const recipes = recipeIds.map((rid) => ({
    recipeId: String(rid),
    name: recipeTitle(rid),
    favoritedAt: new Date().toISOString(),
  }))
  const aiPlans = aiPlanIds.map((pid) => ({
    planId: String(pid),
    name: `AI 方案 ${String(pid).slice(0, 8)}`,
    favoritedAt: new Date().toISOString(),
  }))
  return {
    id: row.id,
    constitutionCode: code,
    constitutionLabel: labelForConstitution(code),
    constitutionSource: st.constitutionSource || (st.constitutionSurveyCompleted ? 'questionnaire' : 'unset'),
    seasonCode,
    seasonLabel: labelForSeason(seasonCode),
    surveyScores: code ? surveyScoresFor(code) : { constitutionTendency: {}, groupAverages: {}, submittedAt: null },
    favorites: { recipes, aiPlans },
    registeredAt: row.registeredAt,
    status: effectiveStatus(row.id, row.defaultStatus),
  }
}

function mergedUserRows() {
  const map = new Map()
  for (const r of SEED_USERS) map.set(r.id, r)
  const client = buildClientListRow()
  if (client && !map.has(client.id)) map.set(client.id, client)
  return [...map.values()]
}

/** 与 GET /admin/users 同源，供数据看板等统计接口复用（Mock 与真实后端对齐时由服务端聚合） */
export function getMergedUserRowsForMock() {
  return mergedUserRows()
}

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchAdminUserApiMock(method, path, config) {
  if (method === 'GET' && path === '/admin/users') {
    const params = config.params || {}
    const qId = String(params.userId || '').trim().toLowerCase()
    const qConst = String(params.constitutionCode || '').trim()
    let list = mergedUserRows().map((row) => ({
      id: row.id,
      constitutionCode: row.constitutionCode,
      constitutionLabel: labelForConstitution(row.constitutionCode),
      registeredAt: row.registeredAt,
      status: effectiveStatus(row.id, row.defaultStatus),
    }))
    if (qId) list = list.filter((u) => u.id.toLowerCase().includes(qId))
    if (qConst) list = list.filter((u) => u.constitutionCode === qConst)
    list.sort((a, b) => String(b.registeredAt).localeCompare(String(a.registeredAt)))
    return { list, total: list.length }
  }

  const detailMatch = path.match(/^\/admin\/users\/([^/]+)$/)
  if (detailMatch && method === 'GET') {
    const id = decodeURIComponent(detailMatch[1])
    const row = mergedUserRows().find((r) => r.id === id)
    if (!row) return { mockError: true, code: 404, message: '用户不存在' }
    const client = buildClientListRow()
    const detail =
      client && client.id === id ? clientDetailFromLocal({ ...row, ...client }) : seedDetail(row)
    return detail
  }

  if (detailMatch && method === 'PATCH') {
    const id = decodeURIComponent(detailMatch[1])
    const row = mergedUserRows().find((r) => r.id === id)
    if (!row) return { mockError: true, code: 404, message: '用户不存在' }
    let body = config.data
    if (typeof body === 'string') {
      try {
        body = JSON.parse(body)
      } catch {
        body = {}
      }
    }
    const status = body?.status
    if (status !== 'active' && status !== 'disabled') {
      return { mockError: true, code: 400, message: '无效的状态值' }
    }
    setUserStatus(id, status)
    return {
      id,
      status,
      constitutionCode: row.constitutionCode,
      constitutionLabel: labelForConstitution(row.constitutionCode),
      registeredAt: row.registeredAt,
    }
  }

  return MOCK_NO_MATCH
}

export function fetchAdminUserList(params) {
  return request.get('/admin/users', { params }).then((page) => {
    const list = page?.list ?? page?.records ?? []
    const total = typeof page?.total === 'number' ? page.total : list.length
    return { ...page, list, total }
  })
}

/**
 * @param {string} userId
 */
export function fetchAdminUserDetail(userId) {
  return request.get(`/admin/users/${encodeURIComponent(userId)}`)
}

/**
 * @param {string} userId
 * @param {'active' | 'disabled'} status
 */
export function patchAdminUserStatus(userId, status) {
  return request.patch(`/admin/users/${encodeURIComponent(userId)}`, { status })
}
