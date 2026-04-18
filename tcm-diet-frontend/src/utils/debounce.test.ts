// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import { debounce } from './debounce'

test('debounce: batches rapid calls', async () => {
  let n = 0
  const d = debounce(() => {
    n += 1
  }, 30)
  d()
  d()
  d()
  assert.equal(n, 0)
  await new Promise((r) => setTimeout(r, 70))
  assert.equal(n, 1)
})

test('debounce: cancel prevents run', async () => {
  let n = 0
  const d = debounce(() => {
    n += 1
  }, 40)
  d()
  d.cancel()
  await new Promise((r) => setTimeout(r, 90))
  assert.equal(n, 0)
})
