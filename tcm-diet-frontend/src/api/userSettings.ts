import request from './request.js'
import { MOCK_NO_MATCH } from './mockTypes.js'
import { DISMISS_LS_KEY } from '@/utils/recommendDismiss'
import { fetchProfileFavorites } from './profile.js'

export type UserPreferencesPayload = {
  seasonCode?: string
  personalizedRecommendEnabled?: boolean
  dataCollectionEnabled?: boolean
}

/** 与 Spring UserController.SurveyBody 维度顺序一致（用于 surveyScoresJson 反序列化） */
const SURVEY_DIMENSION_ORDER = [
  'pinghe',
  'qixu',
  'yangxu',
  'yinxu',
  'tanshi',
  'shire',
  'xueyu',
  'qiyu',
  'tebing',
] as const

function parseSurveyScoresToAnswers(jsonStr: unknown): number[] | null {
  if (typeof jsonStr !== 'string' || !jsonStr.trim()) return null
  try {
    const o = JSON.parse(jsonStr) as Record<string, unknown>
    if (!o || typeof o !== 'object') return null
    const out: number[] = []
    for (const k of SURVEY_DIMENSION_ORDER) {
      const n = Number(o[k])
      if (!Number.isFinite(n) || n < 1 || n > 5) return null
      out.push(n)
    }
    return out.length === 9 ? out : null
  } catch {
    return null
  }
}

/**
 * 仅更新季节时：若 profile 中已有问卷分数 JSON，则重新 POST 问卷以写入 seasonCode。
 */
async function syncSeasonViaSurveyIfPossible(seasonCode: string) {
  const prof = await request.get('/user/profile', { skipGlobalMessage: true })
  const answers = parseSurveyScoresToAnswers(prof?.surveyScoresJson)
  if (!answers) return
  await request.post(
    '/user/constitution/survey',
    { answers, seasonCode },
    { skipGlobalMessage: true },
  )
}

export async function updateUserPreferences(body: UserPreferencesPayload) {
  const putBody: { dataCollectionEnabled?: boolean; recommendEnabled?: boolean } = {}
  if (body.dataCollectionEnabled !== undefined) {
    putBody.dataCollectionEnabled = body.dataCollectionEnabled
  }
  if (body.personalizedRecommendEnabled !== undefined) {
    putBody.recommendEnabled = body.personalizedRecommendEnabled
  }
  if (Object.keys(putBody).length > 0) {
    await request.put('/user/preferences', putBody, { skipGlobalMessage: true })
  }
  if (body.seasonCode != null && String(body.seasonCode).trim() !== '') {
    await syncSeasonViaSurveyIfPossible(String(body.seasonCode).trim())
  }
}

function readLocalBrowseHistoryExport(): unknown[] {
  try {
    const raw = localStorage.getItem('tcm_collect')
    if (!raw) return []
    const o = JSON.parse(raw) as { browseHistory?: unknown[] }
    return Array.isArray(o?.browseHistory) ? o.browseHistory : []
  } catch {
    return []
  }
}

/** 后端无个人数据导出接口：聚合 profile / 收藏 / 本机浏览历史 */
export async function exportUserPersonalData() {
  const [profile, favPack] = await Promise.all([
    request.get('/user/profile', { skipGlobalMessage: true }),
    fetchProfileFavorites().catch(() => ({ recipeFavorites: [] as unknown[] })),
  ])
  const favRaw = favPack?.recipeFavorites ?? []
  const records = readLocalBrowseHistoryExport()
  let dislikes: string[] = []
  try {
    const raw = localStorage.getItem(DISMISS_LS_KEY) || '[]'
    const arr = JSON.parse(raw)
    dislikes = Array.isArray(arr) ? arr.map((x) => String(x)).filter(Boolean) : []
  } catch {
    dislikes = []
  }
  return {
    exportedAt: new Date().toISOString(),
    profile,
    favorites: favRaw,
    browseHistory: records,
    dislikes,
  }
}

/** Spring 未提供校园用户改密接口 */
export function changePassword(_payload: {
  oldPassword: string
  newPassword: string
  confirmPassword: string
}) {
  return Promise.reject(new Error('当前后端未开放用户修改密码接口'))
}

/** 后端 PUT /user/profile 仅支持 constitutionCode；昵称仅本机展示时由页面自行 patch store */
export function updateProfileBasics(_payload: { username?: string; avatar?: string }) {
  return Promise.resolve({ ok: true, savedLocalOnly: true })
}

function readDismissedFromLs(): string[] {
  try {
    const raw = JSON.parse(localStorage.getItem(DISMISS_LS_KEY) || '[]')
    return Array.isArray(raw) ? raw.map((x) => String(x)).filter(Boolean) : []
  } catch {
    return []
  }
}

function writeDismissedToLs(ids: string[]) {
  const uniq = [...new Set(ids.map((x) => String(x)).filter(Boolean))]
  localStorage.setItem(DISMISS_LS_KEY, JSON.stringify(uniq))
}

/** 不感兴趣列表仅存本机（推荐页 dismiss），无后端表 */
export function fetchDislikedRecipes() {
  const ids = readDismissedFromLs()
  return Promise.resolve({
    items: ids.map((recipeId) => ({
      recipeId,
      title: '',
      dismissedAt: new Date().toISOString(),
    })),
  })
}

export function removeDislikedRecipe(recipeId: string) {
  const id = String(recipeId || '')
  writeDismissedToLs(readDismissedFromLs().filter((x) => x !== id))
  return Promise.resolve({ removed: true, recipeId: id })
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
  return typeof data === 'object' ? (data as Record<string, unknown>) : {}
}

/**
 * Mock 匹配（VITE_USE_MOCK 时由 mockGateway 动态导入）
 */
export function matchUserSettingsApiMock(
  method: string,
  path: string,
  config: { data?: unknown },
) {
  if (method === 'PUT' && path === '/user/preferences') {
    const body = parseMockBody(config?.data)
    return { ok: true, ...body }
  }

  if (method === 'PATCH' && path === '/user/preferences') {
    const body = parseMockBody(config?.data)
    return { saved: true, ...body }
  }

  if (method === 'PUT' && path === '/user/profile') {
    const body = parseMockBody(config?.data)
    return { ok: true, profile: body }
  }

  if (method === 'PATCH' && path === '/user/profile') {
    const body = parseMockBody(config?.data)
    return { saved: true, profile: body }
  }

  if (method === 'POST' && path === '/user/change-password') {
    const body = parseMockBody(config?.data)
    const oldPw = String(body.oldPassword ?? '')
    const newPw = String(body.newPassword ?? '')
    const confirm = String(body.confirmPassword ?? '')
    if (!oldPw) {
      return { mockError: true as const, code: 400, message: '请输入原密码' }
    }
    if (!newPw || newPw.length < 6) {
      return { mockError: true as const, code: 400, message: '新密码至少 6 位' }
    }
    if (newPw !== confirm) {
      return { mockError: true as const, code: 400, message: '两次输入的新密码不一致' }
    }
    return { changed: true }
  }

  if (method === 'GET' && path === '/user/dislikes') {
    const ids = readDismissedFromLs()
    return {
      items: ids.map((recipeId) => ({
        recipeId,
        title: '',
        dismissedAt: new Date().toISOString(),
      })),
    }
  }

  const dislikeOne = path.match(/^\/user\/dislikes\/([^/]+)$/)
  if (method === 'DELETE' && dislikeOne) {
    const id = decodeURIComponent(dislikeOne[1])
    writeDismissedToLs(readDismissedFromLs().filter((x) => x !== id))
    return { removed: true, recipeId: id }
  }

  if (method === 'GET' && path === '/user/data-export') {
    return {
      exportedAt: new Date().toISOString(),
      user: {
        note: 'Mock：真实环境由后端汇总体质、收藏、浏览历史等字段',
      },
      constitution: { code: 'qixu', label: '气虚质' },
      favorites: [{ recipeId: 'demo-001', name: '示例药膳' }],
      browseHistory: [{ recipeId: 'demo-002', viewedAt: new Date().toISOString() }],
      dislikes: readDismissedFromLs(),
    }
  }

  return MOCK_NO_MATCH
}
