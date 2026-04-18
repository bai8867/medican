// @ts-nocheck
import { CONSTITUTION_TYPES } from '../data/constitutionTypes'
import { recipeSchematicCoverUrl } from './recipeCoverPlaceholder'

const CONSTITUTION_CODE_TO_LABEL = Object.fromEntries(
  CONSTITUTION_TYPES.map((c) => [c.code, c.label]),
)

export function splitCsvTags(v) {
  if (v == null) return []
  if (Array.isArray(v)) return v.map((s) => String(s).trim()).filter(Boolean)
  return String(v)
    .split(/[,，]/)
    .map((s) => s.trim())
    .filter(Boolean)
}

export function suitConstitutionsFromRemote(r) {
  if (Array.isArray(r.suitConstitutions) && r.suitConstitutions.length) {
    return r.suitConstitutions.map((x) => String(x).trim()).filter(Boolean)
  }
  if (r.suitConstitution) return [String(r.suitConstitution).trim()].filter(Boolean)
  const csv =
    r.constitutionTags ||
    r.constitution_tags ||
    (Array.isArray(r.constitutionCodes) ? r.constitutionCodes.join(',') : '')
  if (!csv || typeof csv !== 'string') return []
  return splitCsvTags(csv)
    .map((token) => {
      const key = token.toLowerCase()
      return CONSTITUTION_CODE_TO_LABEL[key] || token
    })
    .filter(Boolean)
}

export function normalizeRecipe(r) {
  const suitConstitutions = suitConstitutionsFromRemote(r)
  const coverUrl = recipeSchematicCoverUrl(r.name)
  const rawCollect = r.collectCount ?? r.collect_count ?? r.favoriteCount
  const nCollect = Number(rawCollect)
  const rawEffectTags = Array.isArray(r.effectTags) ? r.effectTags : []
  const fromEfficacyCsv =
    typeof r.efficacyTags === 'string'
      ? splitCsvTags(r.efficacyTags)
      : typeof r.efficacy_tags === 'string'
        ? splitCsvTags(r.efficacy_tags)
        : []
  const effectTags = rawEffectTags.length ? rawEffectTags : fromEfficacyCsv
  const effectText = r.effect || r.efficacySummary || r.efficacy_summary || ''
  const seasonFromCsv = splitCsvTags(r.seasonTags || r.season_tags).map((s) => s.toLowerCase())
  const seasonRaw = Array.isArray(r.seasonFit)
    ? r.seasonFit
    : Array.isArray(r.suitSeasons)
      ? r.suitSeasons
      : seasonFromCsv.length
        ? seasonFromCsv
        : []
  const seasonFit = seasonRaw.length ? seasonRaw : ['all']
  const reasonFromBackend = String(r.recommendReason || '').trim()
  const reasonFallback = effectText
    ? `推荐理由：${effectText}`
    : `推荐理由：结合时令与热度为你筛选`
  return {
    ...r,
    suitConstitutions,
    effectTags,
    effect: effectText,
    seasonFit,
    collectCount: Number.isFinite(nCollect) ? nCollect : 0,
    recommendReason: reasonFromBackend || r.summary || reasonFallback,
    coverUrl,
  }
}

export function extractList(data) {
  if (Array.isArray(data)) return data
  if (data?.records) return data.records
  if (data?.list) return data.list
  if (data?.items) return data.items
  if (Array.isArray(data?.content)) return data.content
  if (Array.isArray(data?.rows)) return data.rows
  if (Array.isArray(data?.recipes)) return data.recipes
  return []
}
