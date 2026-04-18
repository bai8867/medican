// @ts-check
import { test, expect } from '@playwright/test'

async function loginToHome(page) {
  await page.context().clearCookies()
  await page.goto('/mode')
  await page.getByRole('button', { name: /学生端|校园端/ }).click()
  await page.getByPlaceholder('例如 student').fill('demo')
  await page.getByLabel('密码').fill('demo123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/(home|recommend|constitution)$/, { timeout: 10_000 })
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
    await page.waitForURL(/\/(home|recommend)$/, { timeout: 10_000 })
  }
}

async function waitTransientMessageGone(page) {
  await page.locator('.el-message').last().waitFor({ state: 'hidden', timeout: 5_000 }).catch(() => {})
}

test.describe.configure({ mode: 'serial' })

test('推荐页筛选面板可用', async ({ page }) => {
  await loginToHome(page)
  await expect(page.locator('.recommend')).toBeVisible()
  await expect(page.locator('.toolbar')).toBeVisible()
  await waitTransientMessageGone(page)
  await page.getByRole('radio', { name: '按收藏量' }).check({ force: true })
  await page.getByRole('radio', { name: '按季节' }).check({ force: true })
  await expect(page.locator('.recommend')).toBeVisible()
})

test('推荐页搜索与加载更多可用', async ({ page }) => {
  await loginToHome(page)
  await expect(page.locator('.recommend')).toBeVisible()

  const searchInput = page.getByRole('textbox', { name: '搜索药膳，回车提交' })
  await searchInput.fill('山药')
  await searchInput.press('Enter')
  await expect(page.locator('.recommend')).toBeVisible()
  await expect(page.locator('article.card, .el-empty').first()).toBeVisible()

  await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight))
  await expect(page.locator('.recommend')).toBeVisible()
})
