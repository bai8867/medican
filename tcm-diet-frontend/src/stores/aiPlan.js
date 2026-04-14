import { defineStore } from 'pinia'
import { ref } from 'vue'

const STORAGE_KEY = 'tcm_ai_saved_plans'

function readPlans() {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    const arr = raw ? JSON.parse(raw) : []
    return Array.isArray(arr) ? arr : []
  } catch {
    return []
  }
}

function writePlans(plans) {
  localStorage.setItem(STORAGE_KEY, JSON.stringify(plans))
}

/** 本地收藏的 AI 食疗方案快照（plan 为接口 data 对象） */
export const useAiPlanStore = defineStore('aiPlan', () => {
  const savedPlans = ref(readPlans())

  function persist() {
    writePlans(savedPlans.value)
  }

  function isPlanSaved(planId) {
    return savedPlans.value.some((p) => p.planId === planId)
  }

  function savePlan(plan) {
    if (!plan?.planId) return false
    if (isPlanSaved(plan.planId)) return false
    savedPlans.value = [
      {
        planId: plan.planId,
        savedAt: new Date().toISOString(),
        symptomSummary: plan.symptomSummary,
        snapshot: plan,
      },
      ...savedPlans.value,
    ].slice(0, 30)
    persist()
    return true
  }

  function removePlan(planId) {
    const next = savedPlans.value.filter((p) => p.planId !== planId)
    if (next.length === savedPlans.value.length) return false
    savedPlans.value = next
    persist()
    return true
  }

  function toggleSavePlan(plan) {
    if (isPlanSaved(plan.planId)) {
      removePlan(plan.planId)
      return false
    }
    savePlan(plan)
    return true
  }

  return {
    savedPlans,
    isPlanSaved,
    savePlan,
    removePlan,
    toggleSavePlan,
  }
})
