// @ts-check
import { test, expect } from '@playwright/test'

/**
 * 不依赖后端 MySQL / Spring 的轻量 E2E，供 CI 与本地快速回归。
 * 完整联调路径仍用 `npm run test:e2e`（student-navigation / recommend-smoke 等）。
 */
test.describe('CI gate (Vite only)', () => {
  test('账号禁用页可访问', async ({ page }) => {
    await page.goto('/account-disabled', { waitUntil: 'load' })
    await expect(page.locator('#app')).toContainText('账号已禁用', { timeout: 30_000 })
  })

  test('模式入口页可访问', async ({ page }) => {
    await page.goto('/mode', { waitUntil: 'load' })
    await expect(page.locator('#app')).toContainText('校园药膳推荐系统', { timeout: 30_000 })
  })
})
