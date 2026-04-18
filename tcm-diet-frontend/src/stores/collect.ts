// @ts-nocheck
import { defineStore } from 'pinia'



const LS_LEGACY_RECIPES = 'tcm_collect_ids'

const LS_LEGACY_HISTORY = 'tcm_history_ids'

const PERSIST_KEY = 'tcm_collect'



function asStringIds(ids) {

  if (!Array.isArray(ids)) return []

  return ids.map((x) => String(x)).filter(Boolean)

}



function readLegacyJsonArray(key) {

  try {

    const raw = localStorage.getItem(key)

    const arr = raw ? JSON.parse(raw) : []

    return Array.isArray(arr) ? arr.map((x) => String(x)).filter(Boolean) : []

  } catch {

    return []

  }

}



/** 从旧版 pinia 持久化 JSON 中读出 historyIds，迁到 browseHistory */

function migratePersistedHistoryIds(store) {

  if (Array.isArray(store.browseHistory) && store.browseHistory.length) return

  try {

    const raw = localStorage.getItem(PERSIST_KEY)

    if (!raw) return

    const o = JSON.parse(raw)

    const ids = o?.historyIds

    if (!Array.isArray(ids) || !ids.length) return

    const now = Date.now()

    store.browseHistory = ids.map((id, i) => ({

      recipeId: String(id),

      name: '药膳',

      coverUrl: '',

      subtitle: '',

      viewedAt: now - i,

    }))

  } catch {

    /* ignore */

  }

}



function migrateLegacyCollect(store) {

  const legacyRecipes = readLegacyJsonArray(LS_LEGACY_RECIPES)

  if (legacyRecipes.length && !store.recipeIds.length) {

    store.recipeIds = legacyRecipes

  }

  const legacyHistory = readLegacyJsonArray(LS_LEGACY_HISTORY)

  if (legacyHistory.length && !store.browseHistory?.length) {

    const now = Date.now()

    store.browseHistory = legacyHistory.map((id, i) => ({

      recipeId: String(id),

      name: '药膳',

      coverUrl: '',

      subtitle: '',

      viewedAt: now - i,

    }))

  }

  localStorage.removeItem(LS_LEGACY_RECIPES)

  localStorage.removeItem(LS_LEGACY_HISTORY)

}



export const useCollectStore = defineStore('collect', {

  state: () => ({

    /** 收藏的药膳（菜谱）ID */

    recipeIds: [],

    /** 收藏的 AI 方案 ID */

    aiPlanIds: [],

    /**

     * 浏览历史（仅本机缓存，最多 10 条药膳，同一药膳再次浏览会顶到最前并更新时间）

     * @type {{ recipeId: string, name: string, coverUrl: string, subtitle: string, viewedAt: number }[]}

     */

    browseHistory: [],

  }),



  getters: {

    /** 药膳收藏列表别名，兼容旧组件 */

    collectedIds: (s) => s.recipeIds,

    collectedCount: (s) => s.recipeIds.length,

    collectCount: (s) => s.recipeIds.length + s.aiPlanIds.length,

    /** 使用 state 闭包，保证模板/computed 内调用时能稳定建立对 recipeIds 的响应式依赖 */

    isCollected: (state) => (id, type = 'recipe') => {

      const sid = String(id)

      const list = type === 'aiPlan' ? state.aiPlanIds : state.recipeIds

      return list.includes(sid)

    },

  },



  actions: {

    addCollect(id, type = 'recipe') {

      const sid = String(id)

      if (!sid) return

      const key = type === 'aiPlan' ? 'aiPlanIds' : 'recipeIds'

      if (this[key].includes(sid)) return

      this[key] = [...this[key], sid]

    },



    removeCollect(id, type = 'recipe') {

      const sid = String(id)

      const key = type === 'aiPlan' ? 'aiPlanIds' : 'recipeIds'

      this[key] = this[key].filter((x) => x !== sid)

    },



    batchRemoveCollect(ids, type = 'recipe') {

      const set = new Set(asStringIds(ids))

      if (!set.size) return

      const key = type === 'aiPlan' ? 'aiPlanIds' : 'recipeIds'

      this[key] = this[key].filter((x) => !set.has(x))

    },



    syncCollectList(payload) {

      if (!payload || typeof payload !== 'object') return

      if (payload.recipeIds != null) this.recipeIds = asStringIds(payload.recipeIds)

      if (payload.aiPlanIds != null) this.aiPlanIds = asStringIds(payload.aiPlanIds)

    },



    toggleCollect(id) {

      const sid = String(id)

      if (this.recipeIds.includes(sid)) this.removeCollect(sid, 'recipe')

      else this.addCollect(sid, 'recipe')

    },



    /**

     * @param {string|number} id 药膳 ID

     * @param {{ name?: string, coverUrl?: string, subtitle?: string }} [snapshot] 展示用快照（可选）

     */

    pushHistory(id, snapshot = {}) {

      const sid = String(id)

      if (!sid) return

      const name = snapshot.name != null && String(snapshot.name).trim() ? String(snapshot.name).trim() : '药膳'

      const coverUrl = typeof snapshot.coverUrl === 'string' ? snapshot.coverUrl : ''

      const subtitle = typeof snapshot.subtitle === 'string' ? snapshot.subtitle : ''

      const viewedAt = Date.now()

      const rest = (this.browseHistory || []).filter((x) => String(x.recipeId) !== sid)

      this.browseHistory = [{ recipeId: sid, name, coverUrl, subtitle, viewedAt }, ...rest].slice(0, 10)

    },



    removeHistoryByRecipeId(recipeId) {

      const sid = String(recipeId)

      if (!sid) return

      this.browseHistory = (this.browseHistory || []).filter((x) => String(x.recipeId) !== sid)

    },



    clearHistory() {

      this.browseHistory = []

    },

  },



  persist: {

    key: PERSIST_KEY,

    paths: ['recipeIds', 'aiPlanIds', 'browseHistory'],

    afterRestore: ({ store }) => {

      migrateLegacyCollect(store)

      migratePersistedHistoryIds(store)

      if (!Array.isArray(store.browseHistory)) store.browseHistory = []

    },

  },

})

