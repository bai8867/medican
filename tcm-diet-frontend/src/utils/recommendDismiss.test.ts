// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  DISMISS_LS_KEY,
  loadDismissedRecipeIds,
  removeDismissedRecipeId,
  saveDismissedRecipeIds,
} from './recommendDismiss'

function installMemoryLocalStorage() {
  const map = new Map()
  const ls = {
    getItem(k) {
      return map.has(k) ? map.get(k) : null
    },
    setItem(k, v) {
      map.set(k, String(v))
    },
    removeItem(k) {
      map.delete(k)
    },
    clear() {
      map.clear()
    },
  }
  const prev = globalThis.localStorage
  globalThis.localStorage = ls
  return () => {
    if (prev === undefined) delete globalThis.localStorage
    else globalThis.localStorage = prev
  }
}

test('loadDismissedRecipeIds returns empty on missing or bad JSON', () => {
  const restore = installMemoryLocalStorage()
  try {
    assert.deepEqual(loadDismissedRecipeIds(), [])
    localStorage.setItem(DISMISS_LS_KEY, 'not-json')
    assert.deepEqual(loadDismissedRecipeIds(), [])
    localStorage.setItem(DISMISS_LS_KEY, JSON.stringify({}))
    assert.deepEqual(loadDismissedRecipeIds(), [])
  } finally {
    restore()
  }
})

test('saveDismissedRecipeIds dedupes and stringifies', () => {
  const restore = installMemoryLocalStorage()
  try {
    saveDismissedRecipeIds(['1', '1', '2', '', '2'])
    assert.deepEqual(loadDismissedRecipeIds(), ['1', '2'])
    const raw = localStorage.getItem(DISMISS_LS_KEY)
    assert.equal(raw, JSON.stringify(['1', '2']))
  } finally {
    restore()
  }
})

test('removeDismissedRecipeId removes one id', () => {
  const restore = installMemoryLocalStorage()
  try {
    saveDismissedRecipeIds(['a', 'b', 'c'])
    removeDismissedRecipeId('b')
    assert.deepEqual(loadDismissedRecipeIds(), ['a', 'c'])
  } finally {
    restore()
  }
})
