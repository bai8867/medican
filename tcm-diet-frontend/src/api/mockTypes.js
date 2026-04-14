/** 表示当前请求不由本模块 Mock 处理 */
export const MOCK_NO_MATCH = Symbol('MOCK_NO_MATCH')

/**
 * @typedef {object} MockErrorResult
 * @property {true} mockError
 * @property {number} [code] 业务错误码（写入响应 body）
 * @property {number} [status]
 * @property {string} [message]
 */

/**
 * @typedef {object} NormalizedMockResult
 * @property {boolean} ok
 * @property {number} [status]
 * @property {unknown} [data] 成功时与后端 data 字段对齐，交给响应拦截器
 * @property {object} [payload] 失败时整包给拦截器（含 code / message）
 */

/**
 * @param {unknown} r
 * @returns {NormalizedMockResult}
 */
export function normalizeMockResult(r) {
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

/**
 * @param {unknown} r
 * @returns {r is MockErrorResult}
 */
export function isMockErrorResult(r) {
  return Boolean(r && typeof r === 'object' && 'mockError' in r && r.mockError === true)
}
