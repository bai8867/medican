// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  sortCollectConstitutionFirst,
  withAiTilesRatio,
  withColdStartAiInterleave,
} from './recommendStreamLayout'

test('withAiTilesRatio: inserts ai tile every 4 recipes', () => {
  const row = [{ id: 1 }, { id: 2 }, { id: 3 }, { id: 4 }]
  const out = withAiTilesRatio(row)
  assert.equal(out.length, 5)
  assert.equal(out[0].kind, 'recipe')
  assert.equal(out[4].kind, 'ai')
})

test('withColdStartAiInterleave: alternates recipe and ai', () => {
  const out = withColdStartAiInterleave([{ id: 'a' }, { id: 'b' }])
  assert.equal(out.length, 4)
  assert.deepEqual(
    out.map((x) => x.kind),
    ['recipe', 'ai', 'recipe', 'ai'],
  )
})

test('sortCollectConstitutionFirst: constitution match before collect count', () => {
  const recipes = [
    { id: '1', suitConstitutions: ['气虚质'], collectCount: 1 },
    { id: '2', suitConstitutions: ['平和质'], collectCount: 99 },
  ]
  const out = sortCollectConstitutionFirst(recipes, '气虚质')
  assert.equal(out[0].id, '1')
})
