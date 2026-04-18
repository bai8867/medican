// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  buildRecommendCalendarAriaLabel,
  buildRecommendCalendarEntrySub,
  buildRecommendCalendarEntryTitle,
  buildRecommendEmptyListHint,
  buildRecommendHeadlineReason,
  buildRecommendReasonCardText,
  buildRecommendSceneStripAroundKeyword,
  computeRecipeSearchFallbackToKeywordOnly,
  recommendConstitutionFocusPhrase,
  recommendSeasonFromDate,
  RECOMMEND_DEFAULT_CONSTITUTION_CODE,
} from './recommendPageCopy'

test('buildRecommendEmptyListHint', () => {
  assert.ok(buildRecommendEmptyListHint('').includes('筛选'))
  assert.ok(buildRecommendEmptyListHint('  百合  ').includes('百合'))
})

test('buildRecommendReasonCardText: personalized off + efficacy all', () => {
  const t = buildRecommendReasonCardText({
    constitutionLabel: '气虚质',
    seasonLabel: '冬季',
    focusPhrase: 'x',
    personalizedRecommendEnabled: false,
    hasProfile: true,
    efficacyIsAll: true,
  })
  assert.ok(t.includes('关闭个性化'))
  assert.ok(t.includes('全部功效'))
})

test('buildRecommendHeadlineReason: no profile + not all efficacy', () => {
  const h = buildRecommendHeadlineReason({
    constitutionLabel: '气虚质',
    seasonLabel: '冬季',
    focusPhrase: '益气健脾、固表补虚',
    personalizedRecommendEnabled: true,
    hasProfile: false,
    efficacyIsAll: false,
  })
  assert.ok(h.includes('气虚质'))
  assert.ok(h.includes('冬季'))
})

test('recommendSeasonFromDate: winter boundary', () => {
  const w = recommendSeasonFromDate(new Date(2026, 0, 15))
  assert.equal(w.key, 'winter')
  assert.equal(w.label, '冬季')
})

test('recommendConstitutionFocusPhrase: known and fallback', () => {
  assert.ok(recommendConstitutionFocusPhrase('qixu').includes('益气'))
  assert.ok(
    recommendConstitutionFocusPhrase('unknown-code').includes('平和'),
  )
  assert.equal(RECOMMEND_DEFAULT_CONSTITUTION_CODE, 'pinghe')
})

test('buildRecommendSceneStripAroundKeyword', () => {
  const a = buildRecommendSceneStripAroundKeyword({ sceneLabelQuery: '' })
  assert.ok(a.beforeKeyword.includes('当前按场景'))
  assert.ok(a.beforeKeyword.endsWith('「'))
  const b = buildRecommendSceneStripAroundKeyword({ sceneLabelQuery: '食堂夜宵' })
  assert.ok(b.beforeKeyword.includes('「食堂夜宵」'))
})

test('buildRecommendCalendarAriaLabel / title / sub', () => {
  assert.ok(buildRecommendCalendarAriaLabel(3).includes('3'))
  assert.ok(buildRecommendCalendarEntryTitle(2).includes('2'))
  assert.ok(buildRecommendCalendarEntrySub('  周一  ').startsWith('周一'))
})

test('computeRecipeSearchFallbackToKeywordOnly: keyword but narrowed empty', () => {
  const rows = [
    { id: 1, name: '黄芪粥', effectTags: ['补气'], suitConstitutions: ['平和质'] },
  ]
  const ok = computeRecipeSearchFallbackToKeywordOnly({
    keywordTrimmed: '黄芪',
    poolRows: rows,
    allergyTags: [],
    effectFilterValue: '补气',
    constitutionFilterValue: '气虚质',
  })
  assert.equal(ok, true)
})
