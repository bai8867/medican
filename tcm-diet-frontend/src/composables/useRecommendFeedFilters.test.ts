// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  EFFICACY_FILTER_ALL,
  filterByConstitution,
  filterByEffect,
  isEfficacyAll,
  mergeConstitutionSeasonSlots,
  seasonOk,
  sortRecipes,
  uniqueById,
} from './useRecommendFeedFilters'

test('isEfficacyAll should treat empty and all marker as all', () => {
  assert.equal(isEfficacyAll(''), true)
  assert.equal(isEfficacyAll(EFFICACY_FILTER_ALL), true)
  assert.equal(isEfficacyAll('补气'), false)
})

test('filterByEffect should keep unknown-meta rows when filtering', () => {
  const rows = [
    { id: 'a', effectTags: ['补气'], effect: '益气健脾' },
    { id: 'b', effectTags: [], effect: '' },
    { id: 'c', effectTags: ['安神'], effect: '宁心安神' },
  ]
  const out = filterByEffect(rows, '补气')
  assert.deepEqual(
    out.map((r) => r.id),
    ['a', 'b'],
  )
})

test('filterByConstitution should keep rows without constitution metadata', () => {
  const rows = [
    { id: 'a', suitConstitutions: ['气虚质'] },
    { id: 'b', suitConstitutions: [] },
    { id: 'c', suitConstitutions: ['阳虚质'] },
  ]
  const out = filterByConstitution(rows, '气虚质')
  assert.deepEqual(
    out.map((r) => r.id),
    ['a', 'b'],
  )
})

test('sortRecipes collect mode should sort by collect count then id', () => {
  const rows = [
    { id: '2', collectCount: 9, seasonFit: ['all'] },
    { id: '1', collectCount: 9, seasonFit: ['all'] },
    { id: '3', collectCount: 5, seasonFit: ['summer'] },
  ]
  const out = sortRecipes(rows, 'collect', 'summer')
  assert.deepEqual(
    out.map((r) => r.id),
    ['1', '2', '3'],
  )
})

test('seasonOk and uniqueById should work together', () => {
  const rows = [
    { id: '1', seasonFit: ['summer'] },
    { id: '1', seasonFit: ['summer'] },
    { id: '2', seasonFit: ['winter'] },
  ]
  const uniq = uniqueById(rows)
  assert.equal(uniq.length, 2)
  assert.equal(seasonOk(uniq[0], 'summer'), true)
  assert.equal(seasonOk(uniq[1], 'summer'), false)
})

test('mergeConstitutionSeasonSlots should follow 3:1 and dedupe', () => {
  const constQ = [{ id: 'c1' }, { id: 'c2' }, { id: 'c3' }, { id: 'c4' }]
  const seasonQ = [{ id: 's1' }, { id: 'c2' }, { id: 's2' }]
  const out = mergeConstitutionSeasonSlots(constQ, seasonQ)
  assert.equal(out[0].id, 'c1')
  assert.equal(out[1].id, 'c2')
  assert.equal(out[2].id, 'c3')
  assert.equal(out[3].id, 's1')
  assert.deepEqual(
    out.map((x) => x.id),
    ['c1', 'c2', 'c3', 's1', 'c4', 's2'],
  )
})
