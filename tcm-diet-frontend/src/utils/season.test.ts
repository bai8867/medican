// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import { getCurrentSeasonCode, getSeasonLabel, SEASON_OPTIONS } from './season'

test('getCurrentSeasonCode maps months to seasons', () => {
  assert.equal(getCurrentSeasonCode(3), 'spring')
  assert.equal(getCurrentSeasonCode(5), 'spring')
  assert.equal(getCurrentSeasonCode(6), 'summer')
  assert.equal(getCurrentSeasonCode(8), 'summer')
  assert.equal(getCurrentSeasonCode(9), 'autumn')
  assert.equal(getCurrentSeasonCode(11), 'autumn')
  assert.equal(getCurrentSeasonCode(12), 'winter')
  assert.equal(getCurrentSeasonCode(1), 'winter')
  assert.equal(getCurrentSeasonCode(2), 'winter')
})

test('getSeasonLabel returns Chinese label or default', () => {
  assert.equal(getSeasonLabel('spring'), '春')
  assert.equal(getSeasonLabel('summer'), '夏')
  assert.equal(getSeasonLabel('unknown'), '春')
})

test('SEASON_OPTIONS has four seasons', () => {
  assert.equal(SEASON_OPTIONS.length, 4)
  const codes = SEASON_OPTIONS.map((s) => s.code)
  assert.deepEqual(codes, ['spring', 'summer', 'autumn', 'winter'])
})
