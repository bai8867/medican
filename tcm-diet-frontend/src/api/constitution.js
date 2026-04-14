import request from './request'
import { MOCK_NO_MATCH } from './mockTypes.js'
import {
  CONSTITUTION_BRIEF,
  computeMockConstitution,
} from '@/data/constitutionSurvey.js'

/** 与 Spring {@code UserController.SurveyBody} 一致：9 题得分 + 可选季节 */
export function submitConstitutionSurvey(payload) {
  return request.post('/user/constitution/survey', payload)
}

const NEUTRAL_NINE = [3, 3, 3, 3, 3, 3, 3, 3, 3]

/**
 * 体质提交：对齐后端 POST /api/user/constitution/survey 与 PUT /api/user/profile。
 * @param {{
 *   answers?: number[]
 *   seasonCode?: string
 *   constitutionCode?: string
 * }} payload
 * @returns {Promise<{ constitutionCode: string, brief: string }>}
 */
export async function submitConstitutionTest(payload) {
  const body = payload && typeof payload === 'object' ? payload : {}
  const answers = Array.isArray(body.answers) ? body.answers.map((n) => Number(n)) : null
  const seasonCode =
    typeof body.seasonCode === 'string' && body.seasonCode.trim() ? body.seasonCode.trim() : undefined
  const targetCode =
    typeof body.constitutionCode === 'string' ? body.constitutionCode.trim() : ''

  const validNine =
    answers &&
    answers.length === 9 &&
    !answers.some((n) => !Number.isFinite(n) || n < 1 || n > 5)

  if (validNine) {
    const surveyRes = await request.post(
      '/user/constitution/survey',
      { answers, seasonCode },
      { skipGlobalMessage: true },
    )
    let code = surveyRes?.primaryCode
    let brief = surveyRes?.primaryLabel || CONSTITUTION_BRIEF[code] || ''
    if (targetCode && targetCode !== code) {
      await request.put('/user/profile', { constitutionCode: targetCode }, { skipGlobalMessage: true })
      code = targetCode
      brief = CONSTITUTION_BRIEF[targetCode] || brief
    }
    return { constitutionCode: code, brief }
  }

  if (targetCode || seasonCode) {
    const surveyRes = await request.post(
      '/user/constitution/survey',
      { answers: NEUTRAL_NINE, seasonCode },
      { skipGlobalMessage: true },
    )
    let code = surveyRes?.primaryCode
    if (targetCode) {
      await request.put('/user/profile', { constitutionCode: targetCode }, { skipGlobalMessage: true })
      code = targetCode
    }
    const brief = CONSTITUTION_BRIEF[code] || surveyRes?.primaryLabel || ''
    return { constitutionCode: code, brief }
  }

  return Promise.reject(new Error('无效的体质提交参数'))
}

const MOCK_CONSTITUTION_CODE_TO_LABEL = {
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

/** Mock 示例：体质说明 GET 形状 */
export const MOCK_CONSTITUTION_INFO_SHAPE = {
  constitutionCode: 'yinxu',
  label: '阴虚质',
  brief: CONSTITUTION_BRIEF.yinxu,
  dietTips: ['宜甘凉滋润，如银耳、百合、梨。', '少食辛辣煎炸，忌熬夜伤阴。'],
  avoidTips: ['辛辣火锅', '过量咖啡与熬夜'],
}

/** 后端无体质说明接口：由本地文案拼装（与 Mock 结构一致） */
export function fetchConstitutionInfo(code) {
  const c = typeof code === 'string' ? code.trim() : ''
  const brief = CONSTITUTION_BRIEF[c]
  if (!brief) {
    return Promise.reject(new Error('未知体质编码'))
  }
  const labelMap = MOCK_CONSTITUTION_CODE_TO_LABEL
  return Promise.resolve({
    constitutionCode: c,
    label: labelMap[c] || c,
    brief,
    dietTips: MOCK_CONSTITUTION_INFO_SHAPE.dietTips,
    avoidTips: MOCK_CONSTITUTION_INFO_SHAPE.avoidTips,
  })
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

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchConstitutionApiMock(method, path, config) {
  if (method === 'POST' && path === '/user/constitution/survey') {
    const body = parseMockBody(config.data)
    const answers = Array.isArray(body.answers) ? body.answers.map(Number) : []
    if (answers.length === 9 && !answers.some((n) => !Number.isFinite(n) || n < 1 || n > 5)) {
      const { constitutionCode } = computeMockConstitution(answers)
      return {
        primaryCode: constitutionCode,
        primaryLabel: MOCK_CONSTITUTION_CODE_TO_LABEL[constitutionCode] || constitutionCode,
        scores: {},
      }
    }
    return { mockError: true, code: 400, message: 'answers 须为长度 9 且每项 1–5 的分值' }
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
    const brief = CONSTITUTION_BRIEF[code]
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
