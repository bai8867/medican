// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  recipeMatchesSceneTag,
  recipeMatchesKeyword,
  filterPoolByKeyword,
} from './recommendRecipeMatch'

test('recipeMatchesSceneTag: empty tag matches', () => {
  assert.equal(recipeMatchesSceneTag({ effectTags: [] }, ''), true)
})

test('recipeMatchesSceneTag: matches effectTags substring', () => {
  assert.equal(recipeMatchesSceneTag({ effectTags: ['润肺'], effect: '' }, '润'), true)
})

test('recipeMatchesKeyword: matches name', () => {
  assert.equal(recipeMatchesKeyword({ name: '银耳莲子汤' }, '银耳'), true)
})

test('recipeMatchesKeyword: empty keyword matches', () => {
  assert.equal(recipeMatchesKeyword({ name: 'x' }, ''), true)
})

test('filterPoolByKeyword: empty keyword returns list', () => {
  const list = [{ id: 1, name: 'a' }]
  assert.deepEqual(filterPoolByKeyword(list, ''), list)
})

test('filterPoolByKeyword: filters by keyword', () => {
  const list = [
    { id: 1, name: '百合粥' },
    { id: 2, name: '绿豆汤' },
  ]
  const out = filterPoolByKeyword(list, '百合')
  assert.deepEqual(out.map((r) => r.id), [1])
})
