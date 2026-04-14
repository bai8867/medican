import test from 'node:test'
import assert from 'node:assert/strict'
import {
  computeMockConstitution,
  isPingheDominantBySpecialRule,
  PINGHE_DOMINANT_THRESHOLDS,
} from './constitutionSurvey.js'

test('平和质特殊：8 偏颇均≤3 且第9题≥4 → 主导平和', () => {
  const answers = [2, 2, 2, 2, 2, 2, 2, 2, 4]
  assert.equal(isPingheDominantBySpecialRule(answers), true)
  const r = computeMockConstitution(answers)
  assert.equal(r.constitutionCode, 'pinghe')
  assert.equal(r.meta.pingheDominantBySpecialRule, true)
})

test('平和质特殊：边界全为 3 与 4', () => {
  const answers = [3, 3, 3, 3, 3, 3, 3, 3, 4]
  assert.equal(isPingheDominantBySpecialRule(answers), true)
  assert.equal(computeMockConstitution(answers).constitutionCode, 'pinghe')
})

test('不满足特殊：任一偏颇题>3 则不走平和强制，按最高分+并列顺序', () => {
  /** 第1题>3 破坏平和特殊；气虚与平和同分 4，并列时气虚优先 */
  const answers = [4, 3, 3, 3, 3, 3, 3, 3, 4]
  assert.equal(isPingheDominantBySpecialRule(answers), false)
  const r = computeMockConstitution(answers)
  assert.equal(r.constitutionCode, 'qixu')
  assert.equal(r.meta.pingheDominantBySpecialRule, false)
})

test('不满足特殊：第9题<4 时按最高分并列（全3时气虚质优先）', () => {
  const answers = [3, 3, 3, 3, 3, 3, 3, 3, 3]
  assert.equal(isPingheDominantBySpecialRule(answers), false)
  const r = computeMockConstitution(answers)
  assert.equal(r.constitutionCode, 'qixu')
  assert.equal(r.meta.pingheDominantBySpecialRule, false)
})

test('并列最高分：气虚与平和同 5，无平和特殊时取气虚', () => {
  const answers = [5, 2, 2, 2, 2, 2, 2, 2, 5]
  assert.equal(isPingheDominantBySpecialRule(answers), false)
  const r = computeMockConstitution(answers)
  assert.equal(r.constitutionCode, 'qixu')
})

test('未答完或非法分值：平和特殊为 false，仍按已填题计分', () => {
  assert.equal(isPingheDominantBySpecialRule([1, 1, 1, 1, 1, 1, 1, 1, null]), false)
  assert.equal(isPingheDominantBySpecialRule([1, 1, 1, 1, 1, 1, 1]), false)
})

test('阈值可覆盖：放宽偏颇上限后满足平和特殊', () => {
  const answers = [4, 2, 2, 2, 2, 2, 2, 2, 4]
  assert.equal(isPingheDominantBySpecialRule(answers), false)
  assert.equal(
    isPingheDominantBySpecialRule(answers, { ...PINGHE_DOMINANT_THRESHOLDS, maxBiasedQuestionScore: 4 }),
    true,
  )
})

test('平和特殊可覆盖「全项同分并列」：阈值为 3 时九题皆 3 → 强制平和而非气虚', () => {
  const answers = [3, 3, 3, 3, 3, 3, 3, 3, 3]
  assert.equal(computeMockConstitution(answers).constitutionCode, 'qixu')
  const relaxed = { minPingheQuestionScore: 3, maxBiasedQuestionScore: 3 }
  const r = computeMockConstitution(answers, { pingheThresholds: relaxed })
  assert.equal(r.constitutionCode, 'pinghe')
  assert.equal(r.meta.pingheDominantBySpecialRule, true)
})
