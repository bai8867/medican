// @ts-nocheck
import { CONSTITUTION_TYPES } from '@/stores/user'
import { EFFECT_FILTER_OPTIONS, EFFICACY_FILTER_ALL } from '@/data/recommendMock'

const SORT_LS_KEY = 'tcm_recommend_sort_by'
const EFFICACY_LS_KEY = 'tcm_recommend_effect_filter'
const CONSTITUTION_FILTER_LS_KEY = 'tcm_recommend_constitution_filter'

export const CONSTITUTION_FILTER_ALL = '__all_constitution__'

const SORT_BY_VALUES = new Set(['collect', 'season'])
const EFFICACY_FILTER_VALUES = new Set(EFFECT_FILTER_OPTIONS.map((o) => o.value))
const CONSTITUTION_FILTER_VALUES = new Set([
  CONSTITUTION_FILTER_ALL,
  ...CONSTITUTION_TYPES.map((c) => c.label),
])

export function loadSortPreference() {
  try {
    const raw = localStorage.getItem(SORT_LS_KEY)
    if (raw && SORT_BY_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return 'collect'
}

export function persistSortPreference(v) {
  try {
    if (SORT_BY_VALUES.has(v)) localStorage.setItem(SORT_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

export function loadEfficacyPreference() {
  try {
    const raw = localStorage.getItem(EFFICACY_LS_KEY)
    if (raw && EFFICACY_FILTER_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return EFFICACY_FILTER_ALL
}

export function persistEfficacyPreference(v) {
  try {
    if (EFFICACY_FILTER_VALUES.has(v)) localStorage.setItem(EFFICACY_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

export function loadConstitutionFilterPreference() {
  try {
    const raw = localStorage.getItem(CONSTITUTION_FILTER_LS_KEY)
    if (raw && CONSTITUTION_FILTER_VALUES.has(raw)) return raw
  } catch {
    /* ignore */
  }
  return CONSTITUTION_FILTER_ALL
}

export function persistConstitutionFilterPreference(v) {
  try {
    if (CONSTITUTION_FILTER_VALUES.has(v)) localStorage.setItem(CONSTITUTION_FILTER_LS_KEY, v)
  } catch {
    /* ignore */
  }
}

export function isConstitutionFilterAll(v) {
  return v == null || v === '' || v === CONSTITUTION_FILTER_ALL
}
