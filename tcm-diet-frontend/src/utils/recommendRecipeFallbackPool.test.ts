// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import { localRecipeFallback, recipeFeedFallbackBaseList } from './recommendRecipeFallbackPool'

test('recipeFeedFallbackBaseList: empty scene tag returns full local pool shape', () => {
  const full = recipeFeedFallbackBaseList('')
  const again = localRecipeFallback()
  assert.equal(full.length, again.length)
})

test('recipeFeedFallbackBaseList: narrows by scene tag when possible', () => {
  const narrowed = recipeFeedFallbackBaseList('不存在的超长标签xyz123')
  assert.ok(Array.isArray(narrowed))
  assert.ok(narrowed.length <= localRecipeFallback().length)
})
