/**
 * Mock 环境下与后台管理共用的药膳内存库，保证增删改与首页/详情数据源一致。
 */
import { MOCK_RECIPES } from '@/data/recommendMock.js'
import { normalizeIngredientGroups } from '@/utils/recipeIngredientGroups.js'

const DEFAULT_TABOO =
  '实热证、急性炎症期慎用；对所含食材或药材过敏者请勿食用。孕妇、哺乳期妇女及服药人群请先咨询医师。'

export const UNIFIED_RECIPE_STATUS = {
  ON: 'on_shelf',
  OFF: 'off_shelf',
}

function inferSeasonFromFit(seasonFit) {
  const sf = Array.isArray(seasonFit) ? [...seasonFit] : []
  if (!sf.length || sf.includes('all')) return 'all'
  if (sf.length === 1) return sf[0]
  return 'all'
}

function seasonFitFromSingle(season) {
  if (!season || season === 'all') return ['all']
  return [season]
}

function toAdminRow(raw) {
  const base = JSON.parse(JSON.stringify(raw))
  const groups = normalizeIngredientGroups(base)
  const steps = (base.steps || []).map((s, idx) => ({
    order: idx + 1,
    description: String(s.text ?? s.description ?? '').trim(),
    tip: s.tip,
  }))
  return {
    ...base,
    effectTags: base.effectTags?.length
      ? [...base.effectTags]
      : String(base.effect || '')
          .split(/[、，,]/)
          .map((x) => x.trim())
          .filter(Boolean)
          .slice(0, 8),
    suitConstitutions:
      base.suitConstitutions?.length
        ? [...base.suitConstitutions]
        : base.suitConstitution
          ? [base.suitConstitution]
          : [],
    season: base.season || inferSeasonFromFit(base.seasonFit),
    seasonFit: base.seasonFit?.length ? [...base.seasonFit] : seasonFitFromSingle(base.season),
    collectCount: Number(base.collectCount) || 0,
    status: base.status === UNIFIED_RECIPE_STATUS.OFF ? UNIFIED_RECIPE_STATUS.OFF : UNIFIED_RECIPE_STATUS.ON,
    taboo: String(base.taboo || base.tabooReminder || DEFAULT_TABOO).trim() || DEFAULT_TABOO,
    coverUrl: base.coverUrl || '',
    ingredientGroups: groups.map((g) => ({
      key: g.key,
      label: g.label,
      items: (g.items || []).map((it) => ({
        name: String(it.name || '').trim(),
        amount: String(it.amount || '').trim(),
        note: it.note,
      })),
    })),
    steps,
  }
}

let unifiedStore = null

export function getUnifiedRecipeMockStore() {
  if (!unifiedStore) {
    unifiedStore = MOCK_RECIPES.map((r) => toAdminRow(r))
  }
  return unifiedStore
}

/** 批量删除等场景替换整个数组引用 */
export function setUnifiedRecipeMockStore(next) {
  unifiedStore = next
}

export { inferSeasonFromFit, seasonFitFromSingle, toAdminRow }
