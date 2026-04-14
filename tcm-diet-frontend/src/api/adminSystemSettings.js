import { MOCK_NO_MATCH } from './mockTypes.js'

const defaultSettings = {
  site_name: '校园药膳推荐',
  contact_email: 'admin@example.com',
  default_season: 'auto',
  items_per_page: 20,
  ai_generate_enabled: true,
  recommend_switch: true,
  maintenance_mode: false,
  compliance_disclaimer:
    '<p>本药膳信息仅供养生保健参考，不构成医疗诊断或治疗建议。如有疾病请及时就医。</p>',
}

/** @type {typeof defaultSettings} */
let settingsStore = { ...defaultSettings }

const mockAuditLogs = [
  { id: '1', operation_time: '2026-04-12 09:12:03', operation: '更新药膳「山药排骨汤」', ip: '10.0.0.12' },
  { id: '2', operation_time: '2026-04-11 16:40:22', operation: '批量导入食材数据', ip: '10.0.0.12' },
  { id: '3', operation_time: '2026-04-11 11:05:18', operation: '禁用用户 campus_u_003', ip: '10.0.0.12' },
  { id: '4', operation_time: '2026-04-10 14:22:51', operation: '登录后台管理', ip: '192.168.1.88' },
  { id: '5', operation_time: '2026-04-10 10:01:00', operation: '导出用户体质统计', ip: '10.0.0.12' },
  { id: '6', operation_time: '2026-04-09 18:33:44', operation: '清空首页推荐缓存', ip: '10.0.0.12' },
  { id: '7', operation_time: '2026-04-09 15:20:11', operation: '修改系统免责声明', ip: '10.0.0.12' },
  { id: '8', operation_time: '2026-04-08 09:45:30', operation: '新增食材「百合」', ip: '10.0.0.5' },
  { id: '9', operation_time: '2026-04-07 13:12:09', operation: '更新系统基本设置', ip: '10.0.0.5' },
  { id: '10', operation_time: '2026-04-06 08:30:00', operation: '登录后台管理', ip: '192.168.1.20' },
]

let redisMock = {
  used_memory: '48.2 MB',
  max_memory: '256 MB',
  connected: true,
}

function safeJson(raw) {
  if (raw == null || raw === '') return {}
  if (typeof raw === 'object') return raw
  try {
    return JSON.parse(String(raw))
  } catch {
    return {}
  }
}

/**
 * @param {import('axios').InternalAxiosRequestConfig} config
 * @returns {boolean | null}
 */
function queryEnabled(config) {
  const raw = String(config.url || '')
  const q = raw.includes('?') ? raw.split('?')[1].split('#')[0] : ''
  const v = new URLSearchParams(q).get('enabled')
  if (v === 'true' || v === '1') return true
  if (v === 'false' || v === '0') return false
  return null
}

/**
 * @param {string} method
 * @param {string} path
 * @param {import('axios').InternalAxiosRequestConfig} config
 */
export function matchAdminSystemSettingsMock(method, path, config) {
  const p = (path || '').split('?')[0].replace(/\/$/, '') || '/'

  if (p === '/admin/system/flags' && method === 'GET') {
    return {
      recommendGlobalEnabled: settingsStore.recommend_switch,
      aiGenerationEnabled: settingsStore.ai_generate_enabled,
    }
  }

  if (p === '/admin/system/ai' && method === 'PUT') {
    const en = queryEnabled(config)
    if (en != null) settingsStore.ai_generate_enabled = en
    return { ok: true }
  }

  if (p === '/admin/system/recommend' && method === 'PUT') {
    const en = queryEnabled(config)
    if (en != null) settingsStore.recommend_switch = en
    return { ok: true }
  }

  if (p === '/admin/system/kv' && method === 'PUT') {
    return { ok: true }
  }

  if (p === '/admin/system/settings' && method === 'GET') {
    return { ...settingsStore }
  }

  if (p === '/admin/system/settings' && method === 'PUT') {
    const body = safeJson(config.data)
    settingsStore = {
      ...settingsStore,
      site_name: body.site_name ?? settingsStore.site_name,
      contact_email: body.contact_email ?? settingsStore.contact_email,
      default_season: body.default_season ?? settingsStore.default_season,
      items_per_page: Number(body.items_per_page) || settingsStore.items_per_page,
    }
    return { ...settingsStore }
  }

  if (p === '/admin/system/settings/features' && method === 'PATCH') {
    const body = safeJson(config.data)
    if (typeof body.ai_generate_enabled === 'boolean') {
      settingsStore.ai_generate_enabled = body.ai_generate_enabled
    }
    if (typeof body.recommend_switch === 'boolean') {
      settingsStore.recommend_switch = body.recommend_switch
    }
    if (typeof body.maintenance_mode === 'boolean') {
      settingsStore.maintenance_mode = body.maintenance_mode
    }
    return {
      ai_generate_enabled: settingsStore.ai_generate_enabled,
      recommend_switch: settingsStore.recommend_switch,
      maintenance_mode: settingsStore.maintenance_mode,
    }
  }

  if (p === '/admin/system/settings/change-password' && method === 'POST') {
    const body = safeJson(config.data)
    if (!body.old_password || !body.new_password) {
      return { mockError: true, code: 400, message: '请填写完整密码信息' }
    }
    return { success: true }
  }

  if (p === '/admin/system/settings/clear-recommend-cache' && method === 'POST') {
    redisMock = { ...redisMock, used_memory: '12.1 MB' }
    return { cleared: true }
  }

  if (p === '/admin/system/settings/export-user-stats' && method === 'POST') {
    return {
      csv:
        '体质类型,用户数\n平和质,120\n气虚质,45\n阳虚质,33\n阴虚质,28\n痰湿质,19\n',
      filename: 'user_constitution_stats.csv',
    }
  }

  if (p === '/admin/system/settings/anonymize-history' && method === 'POST') {
    const body = safeJson(config.data)
    if (!body.admin_password) {
      return { mockError: true, code: 400, message: '请输入管理员密码' }
    }
    return { job_id: 'mock-job-001', status: 'queued' }
  }

  if (p === '/admin/system/settings/compliance-disclaimer' && method === 'GET') {
    return { content: settingsStore.compliance_disclaimer }
  }

  if (p === '/admin/system/settings/compliance-disclaimer' && method === 'PUT') {
    const body = safeJson(config.data)
    if (body.content != null) {
      settingsStore.compliance_disclaimer = String(body.content)
    }
    return { content: settingsStore.compliance_disclaimer }
  }

  if (p === '/admin/system/settings/redis-cache' && method === 'GET') {
    return { ...redisMock }
  }

  if (p === '/admin/system/settings/audit-log' && method === 'GET') {
    return { items: mockAuditLogs }
  }

  return MOCK_NO_MATCH
}
