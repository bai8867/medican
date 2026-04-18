// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  splitCsvTags,
  extractList,
  normalizeRecipe,
  suitConstitutionsFromRemote,
} from './recommendRecipeNormalize'

test('splitCsvTags: null and csv string', () => {
  assert.deepEqual(splitCsvTags(null), [])
  assert.deepEqual(splitCsvTags('a,b，c'), ['a', 'b', 'c'])
})

test('extractList: common response shapes', () => {
  assert.deepEqual(extractList([1, 2]), [1, 2])
  assert.deepEqual(extractList({ records: [3] }), [3])
  assert.deepEqual(extractList({ list: [4] }), [4])
  assert.deepEqual(extractList({}), [])
})

test('suitConstitutionsFromRemote: maps code to label', () => {
  const out = suitConstitutionsFromRemote({ constitution_tags: 'qixu' })
  assert.ok(out.includes('气虚质'))
})

test('normalizeRecipe: merges fields for feed card', () => {
  const row = {
    id: 9,
    name: '测试汤',
    effectTags: ['补气'],
    collectCount: 3,
    seasonFit: ['spring'],
  }
  const n = normalizeRecipe(row)
  assert.equal(n.id, 9)
  assert.equal(n.collectCount, 3)
  assert.deepEqual(n.effectTags, ['补气'])
  assert.ok(n.coverUrl)
  assert.ok(String(n.recommendReason).length > 0)
})
