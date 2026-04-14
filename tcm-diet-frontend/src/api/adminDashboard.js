import request from './request.js'
import { MOCK_NO_MATCH } from './mockTypes.js'
import { MOCK_RECIPES } from '@/data/recommendMock.js'
import { CONSTITUTION_TYPES } from '@/stores/user.js'
import { getMergedUserRowsForMock } from './adminUser.js'

/**
 * @param {string} method
 * @param {string} path
 */
export function matchAdminDashboardApiMock(method, path) {
  if (method !== 'GET') return MOCK_NO_MATCH

  if (path === '/admin/dashboard/overview') {
    const totalCollectCount = MOCK_RECIPES.reduce(
      (sum, r) => sum + (Number(r.collectCount) || 0),
      0,
    )
    const hotTop3 = [...MOCK_RECIPES]
      .sort((a, b) => (Number(b.collectCount) || 0) - (Number(a.collectCount) || 0))
      .slice(0, 3)
      .map((r) => ({
        id: r.id,
        name: r.name,
        collectCount: Number(r.collectCount) || 0,
      }))
    const userTotal = getMergedUserRowsForMock().length

    return { totalCollectCount, hotTop3, userTotal }
  }

  if (path === '/admin/dashboard/constitution-distribution') {
    const rows = getMergedUserRowsForMock()
    const byLabel = new Map()
    let unset = 0
    for (const row of rows) {
      const code = row.constitutionCode
      if (!code) {
        unset += 1
        continue
      }
      const label =
        CONSTITUTION_TYPES.find((c) => c.code === code)?.label || String(code)
      byLabel.set(label, (byLabel.get(label) || 0) + 1)
    }
    const items = [...byLabel.entries()].map(([name, value]) => ({ name, value }))
    if (unset > 0) items.push({ name: '未设置', value: unset })
    return { items }
  }

  return MOCK_NO_MATCH
}

export function fetchDashboardOverview() {
  return request.get('/admin/dashboard/overview')
}

export function fetchDashboardConstitutionDistribution() {
  return request.get('/admin/dashboard/constitution-distribution')
}
