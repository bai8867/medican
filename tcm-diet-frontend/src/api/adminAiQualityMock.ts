import type { InternalAxiosRequestConfig } from 'axios'
import { MOCK_NO_MATCH } from './mockTypes'

type AiQualityRules = {
  guardEnabled: boolean
  strictSafety: boolean
  scoreThreshold: number
}

type AiQualitySampleRow = {
  id: number
  symptom: string
  constitutionCode: string
  qualityScore: number
  scoreThreshold: number
  safetyPassed: number
  source: string
  replayed: number
  createdAt: string
}

const state: {
  rules: AiQualityRules
  samples: AiQualitySampleRow[]
} = {
  rules: {
    guardEnabled: true,
    strictSafety: true,
    scoreThreshold: 75,
  },
  samples: [
    {
      id: 1,
      symptom: '熬夜后咽喉不适',
      constitutionCode: 'yinxu',
      qualityScore: 52,
      scoreThreshold: 75,
      safetyPassed: 0,
      source: 'upstream',
      replayed: 0,
      createdAt: '2026-04-16 09:30:00',
    },
  ],
}

function safeJson(raw: unknown): Record<string, unknown> {
  if (raw == null || raw === '') return {}
  if (typeof raw === 'object' && !Array.isArray(raw)) return raw as Record<string, unknown>
  try {
    return JSON.parse(String(raw)) as Record<string, unknown>
  } catch {
    return {}
  }
}

export function matchAdminAiQualityMock(
  method: string,
  path: string,
  config: InternalAxiosRequestConfig,
): typeof MOCK_NO_MATCH | Record<string, unknown> {
  const raw = (path || '').split('?')[0] || ''
  const p = raw.startsWith('/') ? raw : `/${raw}`

  if (p === '/admin/ai-quality/rules' && method === 'GET') {
    return { ...state.rules }
  }

  if (p === '/admin/ai-quality/rules' && method === 'PUT') {
    const body = safeJson(config.data)
    if (typeof body.guardEnabled === 'boolean') state.rules.guardEnabled = body.guardEnabled
    if (typeof body.strictSafety === 'boolean') state.rules.strictSafety = body.strictSafety
    if (body.scoreThreshold != null) state.rules.scoreThreshold = Number(body.scoreThreshold) || 75
    return { ...state.rules }
  }

  if (p === '/admin/ai-quality/samples' && method === 'GET') {
    const unresolvedOnly = String(config?.params?.unresolvedOnly || 'false') === 'true'
    const list = unresolvedOnly ? state.samples.filter((x) => Number(x.replayed) === 0) : state.samples
    return {
      page: Number(config?.params?.page) || 1,
      pageSize: Number(config?.params?.pageSize) || 20,
      total: list.length,
      records: list,
    }
  }

  const detailMatch = p.match(/^\/admin\/ai-quality\/samples\/(\d+)$/)
  if (detailMatch && method === 'GET') {
    const id = Number(detailMatch[1])
    const row = state.samples.find((x) => x.id === id)
    if (!row) return {}
    return {
      ...row,
      violatedRules: ['NO_CURE_CLAIM'],
      requestPayload: { symptom: row.symptom, constitutionCode: row.constitutionCode },
      responsePayload: { therapyRecommendMarkdown: '保证治愈' },
    }
  }

  const replayMatch = p.match(/^\/admin\/ai-quality\/samples\/(\d+)\/replay$/)
  if (replayMatch && method === 'POST') {
    const id = Number(replayMatch[1])
    const row = state.samples.find((x) => x.id === id)
    if (!row) return { ok: false, message: '样本不存在' }
    row.replayed = 1
    return { ok: true, sampleId: id, result: { qualityGovernance: { score: 80, pass: true } } }
  }

  return MOCK_NO_MATCH
}
