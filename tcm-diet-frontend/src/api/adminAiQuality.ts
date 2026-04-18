import request from './request'
import type { TcmAxiosRequestConfig } from './http/httpTypes'

/** PUT /api/admin/ai-quality/rules 请求体 */
export interface AiQualityRulesUpdatePayload {
  guardEnabled?: boolean
  strictSafety?: boolean
  scoreThreshold?: number
}

/** GET /api/admin/ai-quality/samples 查询参数 */
export interface AiIssueSamplesQuery {
  page?: number
  pageSize?: number
  unresolvedOnly?: boolean
}

export function fetchAiQualityRules(config?: TcmAxiosRequestConfig) {
  return request.get('/admin/ai-quality/rules', config)
}

export function updateAiQualityRules(
  payload: AiQualityRulesUpdatePayload,
  config?: TcmAxiosRequestConfig,
) {
  return request.put('/admin/ai-quality/rules', payload, config)
}

export function fetchAiIssueSamples(
  params: AiIssueSamplesQuery = {},
  config?: TcmAxiosRequestConfig,
) {
  return request.get('/admin/ai-quality/samples', { ...config, params: { ...params, ...config?.params } })
}

export function fetchAiIssueSampleDetail(id: number | string, config?: TcmAxiosRequestConfig) {
  return request.get(`/admin/ai-quality/samples/${id}`, config)
}

export function replayAiIssueSample(id: number | string, config?: TcmAxiosRequestConfig) {
  return request.post(`/admin/ai-quality/samples/${id}/replay`, undefined, config)
}
