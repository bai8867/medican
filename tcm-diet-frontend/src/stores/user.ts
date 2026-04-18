// @ts-nocheck
import { defineStore } from 'pinia'
import { getCurrentSeasonCode } from '@/utils/season'
import { LS_CAMPUS_TOKEN, LS_LEGACY_TOKEN } from '@/utils/storedTokens'
import { resetCampusAccountProbeThrottle } from '@/utils/campusAccountProbe'
import { CONSTITUTION_TYPES } from '../data/constitutionTypes'

export { CONSTITUTION_TYPES }

const LS_LEGACY_CONST = 'tcm_constitution'
const LS_LEGACY_SEASON = 'tcm_season'
const LS_LEGACY_SURVEY = 'tcm_constitution_done'

function labelForCode(code) {
  const hit = CONSTITUTION_TYPES.find((c) => c.code === code)
  return hit?.label || '未设置'
}

function migrateLegacyUser(store) {
  let patch = {}
  if (!store.constitutionCode) {
    const legacy = localStorage.getItem(LS_LEGACY_CONST)
    if (legacy) patch = { ...patch, constitutionCode: legacy }
  }
  if (!store.seasonCode) {
    const s = localStorage.getItem(LS_LEGACY_SEASON)
    if (s) patch = { ...patch, seasonCode: s }
  }
  if (!store.constitutionSurveyCompleted) {
    if (localStorage.getItem(LS_LEGACY_SURVEY) === '1') {
      patch.constitutionSurveyCompleted = true
    } else if (localStorage.getItem(LS_LEGACY_CONST)) {
      patch.constitutionSurveyCompleted = true
      if (!localStorage.getItem(LS_LEGACY_SEASON)) {
        patch.seasonCode = getCurrentSeasonCode()
      }
    }
  }
  if (Object.keys(patch).length) store.$patch(patch)
}

export const useUserStore = defineStore('user', {
  state: () => ({
    /** 校园端用户唯一标识（PRD 5.6.4 用户管理与禁用校验） */
    userId: '',
    /** 首次生成本机 userId 的时间（ISO） */
    registeredAt: '',
    /** 体质类型编码，如 qixu */
    constitutionCode: '',
    /** 体质来源：问卷 / 手动 / 接口等 */
    constitutionSource: '',
    /** 养生季节偏好编码 */
    seasonCode: getCurrentSeasonCode(),
    username: '',
    avatar: '',
    /** 登录令牌，持久化以保持会话 */
    token: '',
    /** 校园端：在门户选择模式后于登录页确认进入 */
    campusSignedIn: false,
    constitutionSurveyCompleted: false,
    /** 关闭后推荐页仅展示应季通用推荐，不按体质匹配 */
    personalizedRecommendEnabled: true,
    preferences: {
      avoidSpicy: false,
      avoidCold: false,
      taste: [],
      /** 常去校区/食堂（多选 id，与 campusWeeklyCalendarSeed 对齐） */
      campusLocationIds: [],
      /** economy | regular | unlimited */
      budgetTier: 'regular',
      /** 忌口/过敏标签 id 列表 */
      allergyTags: [],
    },
  }),

  getters: {
    currentConstitution: (s) => s.constitutionCode,
    currentSeason: (s) => s.seasonCode,
    hasProfile: (s) => Boolean(s.constitutionCode),
    constitutionLabel: (s) => labelForCode(s.constitutionCode),
  },

  actions: {
    /** 确保存在持久化的 userId，供后台用户列表与禁用策略识别本机会话 */
    ensureUserId() {
      if (this.userId) return
      const suffix = Math.random().toString(36).slice(2, 8)
      this.userId = `campus-u-${Date.now().toString(36)}-${suffix}`
      this.registeredAt = new Date().toISOString()
    },

    /** 校园端登录（演示：不校验密码，写入昵称并标记会话） */
    signInCampus({ username } = {}) {
      this.ensureUserId()
      const name = typeof username === 'string' ? username.trim() : ''
      if (name) this.username = name
      this.campusSignedIn = true
      const tk = `campus-token-${Date.now().toString(36)}`
      this.token = tk
      try {
        localStorage.setItem(LS_CAMPUS_TOKEN, tk)
      } catch {
        /* ignore */
      }
    },

    /**
     * 校园端：使用后端 JWT（/api/auth/login 或 /api/auth/register）
     * @param {{ resetLocalPortrait?: boolean }} [opts] 新注册账号须清空本机残留画像，否则会误判已测评、首页不跳转体质采集
     */
    applyCampusBackendSession({ token, user, resetLocalPortrait = false } = {}) {
      if (!token) return
      this.token = token
      if (user && typeof user === 'object') {
        if (user.username) this.username = String(user.username)
        if (user.id != null) this.userId = String(user.id)
      }
      if (!this.userId) this.ensureUserId()
      if (resetLocalPortrait) {
        this.constitutionCode = ''
        this.constitutionSource = ''
        this.constitutionSurveyCompleted = false
        this.seasonCode = getCurrentSeasonCode()
        try {
          localStorage.removeItem(LS_LEGACY_CONST)
          localStorage.removeItem(LS_LEGACY_SEASON)
          localStorage.removeItem(LS_LEGACY_SURVEY)
        } catch {
          /* ignore */
        }
      }
      this.campusSignedIn = true
      try {
        localStorage.setItem(LS_CAMPUS_TOKEN, token)
      } catch {
        /* ignore */
      }
      resetCampusAccountProbeThrottle()
    },

    /** 退出校园端：清除令牌与会话标记（保留本机 userId 等画像数据由调用方决定；此处仅清登录态） */
    logoutCampus() {
      this.token = ''
      this.campusSignedIn = false
      try {
        localStorage.removeItem(LS_CAMPUS_TOKEN)
        localStorage.removeItem(LS_LEGACY_TOKEN)
      } catch {
        /* ignore */
      }
    },

    setUserProfile(partial) {
      if (!partial || typeof partial !== 'object') return
      const { preferences: nextPref, ...rest } = partial
      if (nextPref && typeof nextPref === 'object') {
        this.preferences = { ...this.preferences, ...nextPref }
      }
      this.$patch(rest)
    },

    updateConstitution(payload) {
      if (typeof payload === 'string') {
        this.constitutionCode = payload
        return
      }
      if (!payload || typeof payload !== 'object') return
      if (payload.code !== undefined) this.constitutionCode = payload.code
      if (payload.source !== undefined) this.constitutionSource = payload.source
    },

    updateSeason(code) {
      this.seasonCode = code || getCurrentSeasonCode()
    },

    resetProfile() {
      this.userId = ''
      this.registeredAt = ''
      this.constitutionCode = ''
      this.constitutionSource = ''
      this.seasonCode = getCurrentSeasonCode()
      this.username = ''
      this.avatar = ''
      this.token = ''
      this.campusSignedIn = false
      this.constitutionSurveyCompleted = false
      this.personalizedRecommendEnabled = true
      this.preferences = {
        avoidSpicy: false,
        avoidCold: false,
        taste: [],
        campusLocationIds: [],
        budgetTier: 'regular',
        allergyTags: [],
      }
      localStorage.removeItem(LS_LEGACY_CONST)
      localStorage.removeItem(LS_LEGACY_SEASON)
      localStorage.removeItem(LS_LEGACY_SURVEY)
    },

    /** 确认画像：体质 + 季节 + 测评完成（兼容旧页面） */
    saveConstitutionProfile(code, season) {
      this.updateConstitution({ code, source: 'survey' })
      this.updateSeason(season)
      this.constitutionSurveyCompleted = true
    },

    setConstitution(code) {
      this.updateConstitution({ code })
    },

    setSeason(code) {
      this.updateSeason(code)
    },

    setConstitutionSurveyCompleted(done) {
      this.constitutionSurveyCompleted = Boolean(done)
    },

    setPreferences(partial) {
      this.preferences = { ...this.preferences, ...partial }
    },
  },

  persist: {
    key: 'tcm_user_profile',
    afterRestore: ({ store }) => migrateLegacyUser(store),
  },
})
