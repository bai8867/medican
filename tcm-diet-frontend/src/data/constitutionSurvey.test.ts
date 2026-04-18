// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  QUESTION_BANK_VERSION,
  RESEARCH_CONSTITUTION_QUESTIONS,
  getQuestionGroupCount,
  getTotalQuestionCount,
  buildNeutralAnswers,
  computeLegacyConstitutionFromNineAnswers,
  computeMockConstitution,
  scoreQuestionByDirection,
} from './constitutionSurvey'

test('研究版题库：版本号和题量满足混合题库约束', () => {
  assert.match(QUESTION_BANK_VERSION, /^v2-/)
  assert.equal(getTotalQuestionCount(), RESEARCH_CONSTITUTION_QUESTIONS.length)
  assert.equal(getTotalQuestionCount(), 45)
  assert.equal(getQuestionGroupCount(), 5)
})

test('Legacy 对照：保留旧9题规则用于A/B基线', () => {
  const answers = [4, 3, 3, 3, 3, 3, 3, 3, 4]
  const legacy = computeLegacyConstitutionFromNineAnswers(answers)
  assert.equal(legacy.constitutionCode, 'qixu')
})

test('方向计分：反向题会把高分转为低分', () => {
  assert.equal(scoreQuestionByDirection(5, 'direct'), 5)
  assert.equal(scoreQuestionByDirection(5, 'reverse'), 1)
  assert.equal(scoreQuestionByDirection(2, 'reverse'), 4)
})

test('研究版判定：平和高且偏颇低时，主质为平和', () => {
  const answers = buildNeutralAnswers(2)
  const pingheQuestionIds = RESEARCH_CONSTITUTION_QUESTIONS
    .filter((q) => q.targetCode === 'pinghe')
    .map((q) => q.id)
  for (const id of pingheQuestionIds) {
    answers[id - 1] = 5
  }
  const r = computeMockConstitution(answers, { questionVersion: QUESTION_BANK_VERSION })
  assert.equal(r.constitutionCode, 'pinghe')
  assert.ok(r.meta.ruleTrace.some((s) => s.includes('pinghe-gate')))
})

test('研究版判定：可输出兼夹体质与置信度', () => {
  const answers = buildNeutralAnswers(2)
  for (const q of RESEARCH_CONSTITUTION_QUESTIONS) {
    if (q.targetCode === 'qixu') answers[q.id - 1] = 5
    if (q.targetCode === 'qiyu') answers[q.id - 1] = 4
  }
  const r = computeMockConstitution(answers, { questionVersion: QUESTION_BANK_VERSION })
  assert.equal(r.constitutionCode, 'qixu')
  assert.ok(Array.isArray(r.meta.secondaryCodes))
  assert.ok(r.meta.secondaryCodes.includes('qiyu'))
  assert.ok(r.meta.confidence >= 0 && r.meta.confidence <= 1)
})
