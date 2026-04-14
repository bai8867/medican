import request from './request'
import { MOCK_NO_MATCH } from './mockTypes.js'
import { buildAiTherapyPlanData } from '@/mocks/aiTherapyPlanResponse.js'

/** AI 方案详情（收藏/历史跳转；后端未就绪时页面可降级） */
export function fetchAiPlanDetail(id) {
  return request.get(`/ai/plans/${encodeURIComponent(id)}`, { skipGlobalMessage: true })
}

/** AI 生成个性化药膳方案 */
export function generateDietPlan(payload) {
  return request.post('/ai/diet-plan', payload)
}

/** 流式生成（若后端支持 SSE，可在此扩展 EventSource） */
export function generateDietPlanSync(payload) {
  return request.post('/ai/diet-plan/sync', payload)
}

/**
 * PRD 5.4 — AI 食疗方案生成
 * @param {{ symptom: string, constitution?: string }} payload
 */
export function generateAiTherapyPlan(payload) {
  return request.post('/ai/generate', payload, {
    skipGlobalMessage: true,
    timeout: 120000,
  })
}

/**
 * 方案有用性反馈（可选对接）
 * @param {{ planId: string, useful: boolean }} payload
 */
export function submitAiPlanFeedback(payload) {
  return request.post('/ai/feedback', payload, { skipGlobalMessage: true })
}

/**
 * PRD 5.4.3 — AI 食疗方案生成接口 data 字段（与 AIGenerate / aiPlan 存根一致）
 * @typedef {object} AiTherapyPlanData
 * @property {string} planId
 * @property {string} symptomSummary
 * @property {string|null} constitutionApplied
 * @property {{ recipeId: string, recipeName: string, matchReason: string }[]} recipes
 * @property {{ name: string, benefit: string }[]} coreIngredients
 * @property {string[]} lifestyleAdvice
 * @property {string[]} cautionNotes
 * @property {string} rationale
 * @property {string} disclaimer
 * @property {string} therapyRecommendMarkdown — 推荐食疗方法（Markdown）
 */

/** Mock 示例：与 buildAiTherapyPlanData 输出结构一致 */
export const MOCK_AI_THERAPY_PLAN_SHAPE = buildAiTherapyPlanData({
  symptom: '熬夜多，口干舌燥',
  constitution: 'yinxu',
})

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

function therapyPlanToDetailDoc(plan) {
  const lines = []
  lines.push(`【症状摘要】${plan.symptomSummary || ''}`)
  if (plan.constitutionApplied) {
    lines.push(`【参考体质】${plan.constitutionApplied}`)
  }
  lines.push('\n【推荐药膳】')
  for (const r of plan.recipes || []) {
    lines.push(`- ${r.recipeName}：${r.matchReason}`)
  }
  lines.push('\n【核心食材】')
  for (const c of plan.coreIngredients || []) {
    lines.push(`- ${c.name}：${c.benefit}`)
  }
  lines.push('\n【生活调理】')
  for (const t of plan.lifestyleAdvice || []) {
    lines.push(`- ${t}`)
  }
  lines.push('\n【禁忌与注意】')
  for (const t of plan.cautionNotes || []) {
    lines.push(`- ${t}`)
  }
  lines.push(`\n【原因解读】\n${plan.rationale || ''}`)
  if (plan.disclaimer) {
    lines.push(`\n【声明】${plan.disclaimer}`)
  }
  return lines.join('\n')
}

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchAiApiMock(method, path, config) {
  const planMatch = path.match(/^\/ai\/plans\/([^/]+)$/)
  if (method === 'GET' && planMatch) {
    const id = decodeURIComponent(planMatch[1])
    const plan = buildAiTherapyPlanData({
      symptom: `已保存方案（Mock）`,
      constitution: undefined,
    })
    return {
      title: 'AI 药膳方案',
      summary: plan.symptomSummary,
      content: `方案编号：${id}\n\n${therapyPlanToDetailDoc({ ...plan, symptomSummary: plan.symptomSummary + `（${id}）` })}`,
      coverUrl: '',
      updatedAt: new Date().toISOString(),
    }
  }

  if (method === 'POST' && path === '/ai/generate') {
    const body = parseMockBody(config.data)
    const symptom = typeof body.symptom === 'string' ? body.symptom : ''
    const vague = symptom.trim().length > 0 && symptom.trim().length < 4
    const plan = buildAiTherapyPlanData({
      symptom,
      constitution:
        typeof body.constitution === 'string' && body.constitution
          ? body.constitution
          : undefined,
    })
    return vague ? { ...plan, isGenericPlan: true } : plan
  }

  if (method === 'POST' && path === '/ai/feedback') {
    return { received: true }
  }

  if (method === 'POST' && (path === '/ai/diet-plan' || path === '/ai/diet-plan/sync')) {
    const body = parseMockBody(config.data)
    return buildAiTherapyPlanData({
      symptom: body.symptom || body.goal || body.description || '个性化调养',
      constitution: body.constitution,
    })
  }

  return MOCK_NO_MATCH
}
