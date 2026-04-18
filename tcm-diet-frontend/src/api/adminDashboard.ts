import request from './request'
import { MOCK_NO_MATCH } from './mockTypes'
import { MOCK_RECIPES } from '@/data/recommendMock'
import { CONSTITUTION_TYPES } from '@/stores/user'
import { getMergedUserRowsForMock } from './adminUser'

export interface DashboardHotRecipeRow {
  id: string | number
  name: string
  collectCount: number
}

/** GET /api/admin/dashboard/overview 解包后的 data */
export interface DashboardOverview {
  totalCollectCount: number
  hotTop3: DashboardHotRecipeRow[]
  userTotal: number
}

/** GET /api/admin/dashboard/constitution-distribution 解包后的 data */
export interface ConstitutionDistributionItem {
  name: string
  value: number
}

export interface ConstitutionDistribution {
  items: ConstitutionDistributionItem[]
}

export function matchAdminDashboardApiMock(
  method: string,
  path: string,
): typeof MOCK_NO_MATCH | DashboardOverview | ConstitutionDistribution {
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
    const byLabel = new Map<string, number>()
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
    const items: ConstitutionDistributionItem[] = [...byLabel.entries()].map(([name, value]) => ({
      name,
      value,
    }))
    if (unset > 0) items.push({ name: '未设置', value: unset })
    return { items }
  }

  return MOCK_NO_MATCH
}

export function fetchDashboardOverview(): Promise<DashboardOverview> {
  return request.get('/admin/dashboard/overview')
}

export function fetchDashboardConstitutionDistribution(): Promise<ConstitutionDistribution> {
  return request.get('/admin/dashboard/constitution-distribution')
}

/** GET /api/admin/dashboard/runtime-metrics */
export function fetchDashboardRuntimeMetrics(): Promise<Record<string, unknown>> {
  return request.get('/admin/dashboard/runtime-metrics', { skipGlobalMessage: true })
}
