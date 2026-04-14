import request from './request.js'

/** @returns {Promise<{ list: object[] }>} */
export function fetchScenes() {
  return request.get('/scenes')
}

/**
 * 场景一站式方案（药膳 + 茶饮/小方 + 解读 + 禁忌）
 * @param {number|string} id
 */
export function fetchSceneSolution(id) {
  return request.get(`/scenes/${id}/recipes`)
}
