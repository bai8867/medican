/**
 * Mock 药膳库行：供 ApiMock / 视图回调显式标注（避免依赖 @ts-nocheck 模块的 ReturnType 退化为 any）。
 * 细字段仍以运行时为准，此处只收紧 id 等高频访问键。
 */
export type MockUnifiedRecipeRow = Record<string, unknown> & {
  id: string | number
  status?: string
  name?: string
  collectCount?: number
  suitConstitutions?: string[]
  suitConstitution?: string
  effectTags?: string[]
  effect?: string
  summary?: string
  seasonFit?: string[]
}

/** 表示当前请求不由本模块 Mock 处理 */
export const MOCK_NO_MATCH = Symbol('MOCK_NO_MATCH')
export type MockNoMatch = typeof MOCK_NO_MATCH

export interface MockErrorResult {
  mockError: true
  code?: number
  status?: number
  message?: string
}

export interface NormalizedMockResult {
  ok: boolean
  status?: number
  /** 成功时与后端 data 字段对齐，交给响应拦截器 */
  data?: unknown
  /** 失败时整包给拦截器（含 code / message） */
  payload?: { code?: number; message?: string }
}

export function normalizeMockResult(r: unknown): NormalizedMockResult {
  if (isMockErrorResult(r)) {
    const code = r.code ?? r.status ?? 400
    return {
      ok: false,
      status: code,
      payload: {
        code,
        message: r.message || '请求失败',
      },
    }
  }
  return { ok: true, data: r }
}

export function isMockErrorResult(r: unknown): r is MockErrorResult {
  return Boolean(r && typeof r === 'object' && 'mockError' in r && (r as MockErrorResult).mockError === true)
}
