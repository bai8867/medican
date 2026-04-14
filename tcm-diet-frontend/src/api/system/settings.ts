import request from '../request.js'

export type DefaultSeason = 'spring' | 'summer' | 'autumn' | 'winter' | 'auto'

const LS_ADMIN_BASIC = 'tcm_admin_system_basic_v1'
const LS_COMPLIANCE = 'tcm_compliance_disclaimer_v1'

type BasicLs = {
  site_name: string
  contact_email: string
  default_season: DefaultSeason
  items_per_page: number
  maintenance_mode: boolean
}

const BASIC_DEFAULT: BasicLs = {
  site_name: '校园药膳推荐',
  contact_email: 'admin@example.com',
  default_season: 'auto',
  items_per_page: 20,
  maintenance_mode: false,
}

function readBasicLs(): BasicLs {
  try {
    const raw = localStorage.getItem(LS_ADMIN_BASIC)
    if (!raw) return { ...BASIC_DEFAULT }
    const o = JSON.parse(raw) as Partial<BasicLs>
    return {
      ...BASIC_DEFAULT,
      ...o,
      site_name: typeof o.site_name === 'string' ? o.site_name : BASIC_DEFAULT.site_name,
      contact_email: typeof o.contact_email === 'string' ? o.contact_email : BASIC_DEFAULT.contact_email,
      default_season: (o.default_season as DefaultSeason) || BASIC_DEFAULT.default_season,
      items_per_page: Number(o.items_per_page) || BASIC_DEFAULT.items_per_page,
      maintenance_mode: Boolean(o.maintenance_mode),
    }
  } catch {
    return { ...BASIC_DEFAULT }
  }
}

function writeBasicLs(partial: Partial<BasicLs>) {
  const next = { ...readBasicLs(), ...partial }
  localStorage.setItem(LS_ADMIN_BASIC, JSON.stringify(next))
}

/** 与 GET /admin/system/settings 对齐的完整设置 */
export interface SystemSettingsDTO {
  site_name: string
  contact_email: string
  default_season: DefaultSeason
  items_per_page: number
  ai_generate_enabled: boolean
  recommend_switch: boolean
  maintenance_mode: boolean
  compliance_disclaimer?: string
}

/** 基本设置表单提交 */
export interface SystemSettingsUpdatePayload {
  site_name: string
  contact_email: string
  default_season: DefaultSeason
  items_per_page: number
}

export interface FeatureFlagsPayload {
  ai_generate_enabled?: boolean
  recommend_switch?: boolean
  maintenance_mode?: boolean
}

export interface FeatureFlagsDTO {
  ai_generate_enabled: boolean
  recommend_switch: boolean
  maintenance_mode: boolean
}

export interface ChangePasswordPayload {
  old_password: string
  new_password: string
  confirm_password: string
}

export interface RedisCacheStatusDTO {
  used_memory: string
  max_memory: string
  connected: boolean
}

export interface AdminAuditLogRow {
  id: string
  operation_time: string
  operation: string
  ip: string
}

export interface AdminAuditLogResponse {
  items: AdminAuditLogRow[]
}

export interface ComplianceDisclaimerDTO {
  content: string
}

export interface ExportUserStatsMockDTO {
  csv: string
  filename: string
}

async function loadFlagsFromServer(): Promise<{
  ai_generate_enabled: boolean
  recommend_switch: boolean
}> {
  const data = (await request.get('/admin/system/flags', { skipGlobalMessage: true })) as {
    aiGenerationEnabled?: boolean
    recommendGlobalEnabled?: boolean
  }
  return {
    ai_generate_enabled: data?.aiGenerationEnabled !== false,
    recommend_switch: data?.recommendGlobalEnabled !== false,
  }
}

export async function getSystemSettings(): Promise<SystemSettingsDTO> {
  const flags = await loadFlagsFromServer()
  const basic = readBasicLs()
  let compliance = ''
  try {
    compliance = localStorage.getItem(LS_COMPLIANCE) || ''
  } catch {
    compliance = ''
  }
  return {
    site_name: basic.site_name,
    contact_email: basic.contact_email,
    default_season: basic.default_season,
    items_per_page: basic.items_per_page,
    ai_generate_enabled: flags.ai_generate_enabled,
    recommend_switch: flags.recommend_switch,
    maintenance_mode: basic.maintenance_mode,
    compliance_disclaimer: compliance,
  }
}

export async function updateSystemSettings(data: SystemSettingsUpdatePayload): Promise<SystemSettingsDTO> {
  writeBasicLs({
    site_name: data.site_name,
    contact_email: data.contact_email,
    default_season: data.default_season,
    items_per_page: data.items_per_page,
  })
  return getSystemSettings()
}

export async function updateFeatureFlags(data: FeatureFlagsPayload): Promise<FeatureFlagsDTO> {
  if (data.ai_generate_enabled !== undefined) {
    await request.put(`/admin/system/ai?enabled=${Boolean(data.ai_generate_enabled)}`, {}, { skipGlobalMessage: true })
  }
  if (data.recommend_switch !== undefined) {
    await request.put(`/admin/system/recommend?enabled=${Boolean(data.recommend_switch)}`, {}, { skipGlobalMessage: true })
  }
  if (data.maintenance_mode !== undefined) {
    writeBasicLs({ maintenance_mode: data.maintenance_mode })
  }
  const flags = await loadFlagsFromServer()
  const basic = readBasicLs()
  return {
    ai_generate_enabled: flags.ai_generate_enabled,
    recommend_switch: flags.recommend_switch,
    maintenance_mode: basic.maintenance_mode,
  }
}

export function changePassword(_data: ChangePasswordPayload): Promise<{ success: boolean }> {
  return Promise.reject(new Error('当前后端未开放管理员修改密码接口'))
}

/** 后端无独立推荐缓存清理：写入 KV 作为运维占位 */
export async function clearRecommendCache(): Promise<{ cleared: boolean }> {
  await request.put(
    '/admin/system/kv',
    { k: 'admin.recommend.cache_bump', v: String(Date.now()) },
    { skipGlobalMessage: true },
  )
  return { cleared: true }
}

export function getRedisCacheStatus(): Promise<RedisCacheStatusDTO> {
  return Promise.resolve({
    used_memory: '—',
    max_memory: '—',
    connected: false,
  })
}

export function fetchAdminAuditLog(): Promise<AdminAuditLogResponse> {
  return Promise.resolve({ items: [] })
}

export function getComplianceDisclaimer(): Promise<ComplianceDisclaimerDTO> {
  try {
    const content = localStorage.getItem(LS_COMPLIANCE) || ''
    return Promise.resolve({ content })
  } catch {
    return Promise.resolve({ content: '' })
  }
}

export function saveComplianceDisclaimer(content: string): Promise<ComplianceDisclaimerDTO> {
  try {
    localStorage.setItem(LS_COMPLIANCE, String(content ?? ''))
  } catch {
    /* ignore */
  }
  return Promise.resolve({ content: String(content ?? '') })
}

export function anonymizeHistoricalData(_adminPassword: string): Promise<{
  job_id: string
  status: string
}> {
  return Promise.reject(new Error('当前后端未提供匿名化历史数据接口'))
}

function triggerBlobDownload(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = filename
  a.rel = 'noopener'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

/**
 * 导出占位统计：后端无聚合接口时导出说明行；Mock 仍走原 POST。
 */
export async function exportUserStats(): Promise<void> {
  const useMock = import.meta.env.VITE_USE_MOCK === 'true'
  if (useMock) {
    const data = (await request.post(
      '/admin/system/settings/export-user-stats',
    )) as ExportUserStatsMockDTO
    const blob = new Blob([`\uFEFF${data.csv}`], {
      type: 'text/csv;charset=utf-8;',
    })
    triggerBlobDownload(blob, data.filename || 'user_stats.csv')
    return
  }
  let note = '说明,当前后端未提供用户体质分布统计导出；请通过数据库或后续报表服务导出\n'
  try {
    const dash = await request.get('/admin/dashboard', { skipGlobalMessage: true })
    const n = Array.isArray(dash?.topCollected) ? dash.topCollected.length : 0
    note += `dashboard_sample,热门药膳样条数=${n}\n`
  } catch {
    note += 'dashboard,不可读\n'
  }
  const blob = new Blob([`\uFEFF${note}`], { type: 'text/csv;charset=utf-8;' })
  triggerBlobDownload(blob, 'user_constitution_stats_placeholder.csv')
}
