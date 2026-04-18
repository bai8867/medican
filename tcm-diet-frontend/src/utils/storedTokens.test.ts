// @ts-nocheck
import test from 'node:test'
import assert from 'node:assert/strict'
import {
  LS_ADMIN_TOKEN,
  LS_CAMPUS_TOKEN,
  LS_LEGACY_TOKEN,
  readAdminToken,
  readCampusToken,
} from './storedTokens'

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

test('readCampusToken prefers campus key then legacy', () => {
  const restore = installMemoryLocalStorage()
  try {
    assert.equal(readCampusToken(), null)
    localStorage.setItem(LS_LEGACY_TOKEN, 'legacy-jwt')
    assert.equal(readCampusToken(), 'legacy-jwt')
    localStorage.setItem(LS_CAMPUS_TOKEN, 'campus-jwt')
    assert.equal(readCampusToken(), 'campus-jwt')
  } finally {
    restore()
  }
})

test('readAdminToken reads direct admin token key', () => {
  const restore = installMemoryLocalStorage()
  try {
    localStorage.setItem(LS_ADMIN_TOKEN, 'admin-direct')
    assert.equal(readAdminToken(), 'admin-direct')
  } finally {
    restore()
  }
})

test('readAdminToken parses tcm_admin_auth JSON shapes', () => {
  const restore = installMemoryLocalStorage()
  try {
    localStorage.setItem('tcm_admin_auth', JSON.stringify({ token: 't1' }))
    assert.equal(readAdminToken(), 't1')

    localStorage.setItem('tcm_admin_auth', JSON.stringify({ state: { token: 't2' } }))
    assert.equal(readAdminToken(), 't2')

    localStorage.setItem('tcm_admin_auth', JSON.stringify({}))
    assert.equal(readAdminToken(), null)
  } finally {
    restore()
  }
})

test('readAdminToken returns null on invalid JSON', () => {
  const restore = installMemoryLocalStorage()
  try {
    localStorage.setItem('tcm_admin_auth', '{')
    assert.equal(readAdminToken(), null)
  } finally {
    restore()
  }
})
