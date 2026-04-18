import type { InternalAxiosRequestConfig } from 'axios'
import request from './request'
import { MOCK_NO_MATCH } from './mockTypes'
import {
  CONSTITUTION_BRIEF,
  QUESTION_BANK_VERSION,
  getTotalQuestionCount,
  buildNeutralAnswers,
  computeMockConstitution,
} from '@/data/constitutionSurvey'

const BRIEF = CONSTITUTION_BRIEF as Record<string, string>

/** 与后端体质问卷 / 画像 PUT 对齐的提交体 */
export interface ConstitutionSubmitPayload {
  answers?: number[]
  seasonCode?: string
  constitutionCode?: string
  questionVersion?: string
}

export interface ConstitutionTestResult {
  constitutionCode: string
  brief: string
}

interface SurveyPostResponse {
  primaryCode?: string
  primaryLabel?: string
}

export function submitConstitutionSurvey(payload: unknown) {
  return request.post('/user/constitution/survey', payload)
}

const NEUTRAL_ANSWERS = buildNeutralAnswers(3)

/**
 * 体质提交：对齐后端 POST /api/user/constitution/survey 与 PUT /api/user/profile。
 */
export async function submitConstitutionTest(
  payload: ConstitutionSubmitPayload | null | undefined,
): Promise<ConstitutionTestResult> {
  const body = payload && typeof payload === 'object' ? payload : {}
  const answers = Array.isArray(body.answers) ? body.answers.map((n) => Number(n)) : null
  const seasonCode =
    typeof body.seasonCode === 'string' && body.seasonCode.trim() ? body.seasonCode.trim() : undefined
  const targetCode =
    typeof body.constitutionCode === 'string' ? body.constitutionCode.trim() : ''

  const validNine =
    answers &&
    answers.length === getTotalQuestionCount() &&
    !answers.some((n) => !Number.isFinite(n) || n < 1 || n > 5)

  if (validNine) {
    const surveyRes = await request.post<SurveyPostResponse>(
      '/user/constitution/survey',
      { answers, seasonCode, questionVersion: QUESTION_BANK_VERSION },
      { skipGlobalMessage: true },
    )
    let code = surveyRes?.primaryCode ?? ''
    let brief = surveyRes?.primaryLabel || BRIEF[code] || ''
    if (targetCode && targetCode !== code) {
      await request.put('/user/profile', { constitutionCode: targetCode }, { skipGlobalMessage: true })
      code = targetCode
      brief = BRIEF[targetCode] || brief
    }
    return { constitutionCode: code, brief }
  }

  if (targetCode || seasonCode) {
    const surveyRes = await request.post<SurveyPostResponse>(
      '/user/constitution/survey',
      { answers: NEUTRAL_ANSWERS, seasonCode, questionVersion: QUESTION_BANK_VERSION },
      { skipGlobalMessage: true },
    )
    let code = surveyRes?.primaryCode ?? ''
    if (targetCode) {
      await request.put('/user/profile', { constitutionCode: targetCode }, { skipGlobalMessage: true })
      code = targetCode
    }
    const brief = BRIEF[code] || surveyRes?.primaryLabel || ''
    return { constitutionCode: code, brief }
  }

  return Promise.reject(new Error('无效的体质提交参数'))
}

const MOCK_CONSTITUTION_CODE_TO_LABEL: Record<string, string> = {
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

export interface ConstitutionInfo {
  constitutionCode: string
  label: string
  brief: string
  dietTips: string[]
  avoidTips: string[]
}

/** Mock 示例：体质说明 GET 形状 */
export const MOCK_CONSTITUTION_INFO_SHAPE: ConstitutionInfo = {
  constitutionCode: 'yinxu',
  label: '阴虚质',
  brief: BRIEF.yinxu,
  dietTips: ['宜甘凉滋润，如银耳、百合、梨。', '少食辛辣煎炸，忌熬夜伤阴。'],
  avoidTips: ['辛辣火锅', '过量咖啡与熬夜'],
}

/** 后端无体质说明接口：由本地文案拼装（与 Mock 结构一致） */
export function fetchConstitutionInfo(code: string | null | undefined): Promise<ConstitutionInfo> {
  const c = typeof code === 'string' ? code.trim() : ''
  const brief = BRIEF[c]
  if (!brief) {
    return Promise.reject(new Error('未知体质编码'))
  }
  return Promise.resolve({
    constitutionCode: c,
    label: MOCK_CONSTITUTION_CODE_TO_LABEL[c] || c,
    brief,
    dietTips: MOCK_CONSTITUTION_INFO_SHAPE.dietTips,
    avoidTips: MOCK_CONSTITUTION_INFO_SHAPE.avoidTips,
  })
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
  if (typeof data === 'object') {
    return data as Record<string, unknown>
  }
  return {}
}

export function matchConstitutionApiMock(
  method: string,
  path: string,
  config: InternalAxiosRequestConfig,
): unknown {
  if (method === 'POST' && path === '/user/constitution/survey') {
    const body = parseMockBody(config.data)
    const answers = Array.isArray(body.answers) ? (body.answers as unknown[]).map(Number) : []
    if (
      answers.length === getTotalQuestionCount() &&
      !answers.some((n) => !Number.isFinite(n) || (n as number) < 1 || (n as number) > 5)
    ) {
      const { constitutionCode } = computeMockConstitution(answers as number[], {
        questionVersion: QUESTION_BANK_VERSION,
      })
      return {
        primaryCode: constitutionCode,
        primaryLabel: MOCK_CONSTITUTION_CODE_TO_LABEL[constitutionCode] || constitutionCode,
        scores: {},
      }
    }
    return {
      mockError: true,
      code: 400,
      message: `answers 须为长度 ${getTotalQuestionCount()} 且每项 1–5 的分值`,
    }
  }

  if (method === 'PUT' && path === '/user/profile') {
    const body = parseMockBody(config.data)
    if (body.constitutionCode) {
      return { ok: true }
    }
    return { ok: true }
  }

  const infoMatch = path.match(/^\/constitution\/([^/]+)$/)
  if (method === 'GET' && infoMatch) {
    const code = infoMatch[1]
    const brief = BRIEF[code]
    if (!brief) {
      return { mockError: true, code: 404, message: '未知体质编码' }
    }
    return {
      constitutionCode: code,
      label: MOCK_CONSTITUTION_CODE_TO_LABEL[code] || code,
      brief,
      dietTips: MOCK_CONSTITUTION_INFO_SHAPE.dietTips,
      avoidTips: MOCK_CONSTITUTION_INFO_SHAPE.avoidTips,
    }
  }

  return MOCK_NO_MATCH
}
