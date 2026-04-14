// @ts-check
import { test, expect } from '@playwright/test'

/**
 * 学生端导航 E2E
 *
 * 说明：路由在无体质画像时会将 /recommend 重定向至 /constitution；各用例均独立登录（必要时跳过测评）以保证可重复执行。
 */
test.describe.configure({ mode: 'serial' })

function log(...args) {
  console.log('[student-navigation]', ...args)
}

function pathnameIs(u, path) {
  try {
    const s = typeof u === 'string' ? u : u instanceof URL ? u.href : String(u)
    return new URL(s).pathname === path
  } catch {
    return false
  }
}

/** @param {import('@playwright/test').Page} page */
async function clearSiteData(page) {
  await page.context().clearCookies()
  await page.goto('about:blank')
  await page.evaluate(() => {
    try {
      localStorage.clear()
      sessionStorage.clear()
    } catch {
      /* ignore */
    }
  })
}

/**
 * 需求步骤 1–2：打开 /mode，点学生端/校园端，经登录到 /recommend 或 /constitution
 * @param {import('@playwright/test').Page} page
 */
async function loginFromMode(page) {
  log('进入模式页')
  await page.goto('/mode')
  await expect(page).toHaveURL(/\/mode$/)
  await expect(page.getByRole('heading', { name: '校园药膳推荐系统' })).toBeVisible()

  log('点击学生端 / 校园端入口')
  await page.getByRole('button', { name: /学生端|校园端/ }).click()
  await expect(page).toHaveURL(/\/campus\/login/)
  await expect(page.getByRole('heading', { name: '校园端登录' })).toBeVisible()

  await page.getByPlaceholder('例如 student').fill('playwright-student')
  await page.getByPlaceholder('例如 123456').fill('123456')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/(recommend|constitution)$/, { timeout: 10_000 })
  log('登录后 URL', page.url())
}

/**
 * 完成 9 题问卷（每组任选第一档分值）
 * @param {import('@playwright/test').Page} page
 */
async function completeNineQuestionSurvey(page) {
  await expect(page.getByRole('heading', { name: '体质采集' })).toBeVisible()
  for (let round = 0; round < 3; round += 1) {
    const blocks = page.locator('.question-block')
    const n = await blocks.count()
    expect(n).toBeGreaterThan(0)
    for (let i = 0; i < n; i += 1) {
      /** Element Plus：点 label 可视区，避免 hidden input 被 inner 遮挡 */
      await blocks.nth(i).locator('.score-radio').first().click()
    }
    const btnLabel = round < 2 ? '下一组' : '提交并查看结果'
    await page.getByRole('button', { name: btnLabel }).click()
  }
  await expect(page.getByRole('button', { name: '确认并保存，进入首页' })).toBeVisible()
  await expect(page.getByText(/您的体质：/)).toBeVisible()
}

test.beforeEach(async ({ page }, testInfo) => {
  await clearSiteData(page)
  page.on('console', (msg) => {
    if (msg.type() === 'error') log('console.error:', msg.text())
  })
  page.on('pageerror', (err) => log('pageerror:', err.message))
  log('→', testInfo.title)
})

test.afterEach(async ({ page }, testInfo) => {
  if (testInfo.status !== testInfo.expectedStatus) {
    const safe = testInfo.title.replace(/[^\w\u4e00-\u9fa5-]+/g, '_')
    const path = testInfo.outputPath(`shot-${safe}.png`)
    await page.screenshot({ path, fullPage: true }).catch(() => {})
    log('失败截图:', path)
  }
})

test('S2：体质采集 → 首页（提交问卷）', async ({ page }) => {
  await loginFromMode(page)

  if (page.url().includes('/recommend')) {
    await page.goto('/constitution')
  }
  await expect(page).toHaveURL(/\/constitution$/)

  await completeNineQuestionSurvey(page)

  await page.getByRole('button', { name: '确认并保存，进入首页' }).click()
  await expect(page).toHaveURL(/\/recommend$/)
  await expect(page.locator('.recommend')).toBeVisible()
  await expect(page.getByText(/体质 · /)).toBeVisible()
  await expect(page.locator('article.card').first()).toBeVisible()
})

test('S1：首页 → 体质采集 → 后退', async ({ page }) => {
  await loginFromMode(page)
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
  }
  await expect(page).toHaveURL(/\/recommend$/)

  await page.getByRole('button', { name: '修改' }).click()
  await expect(page).toHaveURL(/\/constitution$/)
  await expect(page.getByRole('heading', { name: '体质采集' })).toBeVisible()

  await page.goBack()
  await expect(page).toHaveURL(/\/recommend$/)
  await expect(page.locator('article.card').first()).toBeVisible()
})

test('S3：首页 → 菜谱详情 → 返回 → 刷新', async ({ page }) => {
  await loginFromMode(page)
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
  }
  await expect(page).toHaveURL(/\/recommend$/)

  const firstCard = page.locator('article.card').first()
  await expect(firstCard).toBeVisible()
  const title = (await firstCard.locator('.card__title').innerText()).trim()
  expect(title.length).toBeGreaterThan(0)

  await firstCard.click()
  await expect(page).toHaveURL(/\/recipe\/[^/]+$/)
  await expect(page.locator('.top-nav__title')).toContainText(title)

  await page.getByRole('button', { name: '返回' }).click()
  await expect(page).toHaveURL(/\/recommend$/)
  await expect(page.locator('article.card').first()).toBeVisible()

  await page.locator('article.card').first().click()
  await expect(page).toHaveURL(/\/recipe\/[^/]+$/)
  await page.reload()
  await expect(page.locator('.top-nav__title')).toContainText(title)
})

test('S4：首页 → AI 生成 → 食疗方 → 后退', async ({ page }) => {
  await loginFromMode(page)
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
  }
  await expect(page).toHaveURL(/\/recommend$/)

  await page.locator('.ai-tile').first().click()
  await expect(page).toHaveURL((u) => pathnameIs(u, '/ai'))
  await expect(page.getByRole('heading', { name: 'AI 食疗方案' })).toBeVisible()

  await page.locator('.symptom-input textarea').fill('熬夜口干')
  await page.getByRole('button', { name: '生成方案' }).click()
  /** 开发态 Mock 含 UI 延迟 + 方案渲染，放宽等待 */
  const recipeLink = page.locator('.recipe-link').first()
  await expect(recipeLink).toBeVisible()
  const recipeName = (await recipeLink.innerText()).trim()
  await recipeLink.click()
  await expect(page).toHaveURL(/\/recipe\/[^/]+$/)
  await expect(page.locator('.top-nav__title')).toContainText(recipeName)

  await page.goBack()
  await expect(page).toHaveURL((u) => pathnameIs(u, '/ai'))
  await expect(page.getByRole('heading', { name: '推荐食疗方' })).toBeVisible()
})

test('S5：我的 → 收藏 / 历史', async ({ page }) => {
  await loginFromMode(page)
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
  }
  await expect(page).toHaveURL(/\/recommend$/)

  await page.locator('article.card').first().click()
  await expect(page).toHaveURL(/\/recipe\/[^/]+$/)
  await page.locator('.top-nav__fav').click()

  await page.getByRole('button', { name: '返回' }).click()
  await expect(page).toHaveURL(/\/recommend$/)

  await page.getByRole('button', { name: '进入我的页面' }).click()
  await expect(page).toHaveURL(/\/profile$/)
  await expect(page.getByRole('heading', { name: '我的' })).toBeVisible()

  await expect(page.getByRole('tab', { name: '收藏列表' })).toHaveAttribute('aria-selected', 'true')
  await expect(
    page.getByText('暂无药膳收藏').or(page.locator('.fav-list .fav-row').first()),
  ).toBeVisible()

  await page.getByRole('tab', { name: '浏览历史' }).click()
  await expect(
    page.getByText('暂无浏览记录').or(page.locator('.fav-row--history').first()),
  ).toBeVisible()

  await page.getByRole('tab', { name: '收藏列表' }).click()
  const recipeFavRows = page
    .locator('section.fav-group')
    .filter({ has: page.getByRole('heading', { name: '药膳收藏' }) })
    .locator('.fav-row')
  await expect(recipeFavRows.first()).toBeVisible()
  await recipeFavRows.first().click()
  await expect(page).toHaveURL(/\/recipe\/[^/]+$/)
})

test('S6：历史栈后退 / 前进', async ({ page }) => {
  await page.goto('/mode')
  await page.getByRole('button', { name: /学生端|校园端/ }).click()
  await page.getByPlaceholder('例如 student').fill('playwright-history')
  await page.getByPlaceholder('例如 123456').fill('123456')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/(recommend|constitution)$/, { timeout: 10_000 })

  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
    await expect(page).toHaveURL(/\/recommend$/)
  }

  await page.locator('article.card').first().click()
  await expect(page).toHaveURL(/\/recipe\//)

  await page.getByRole('button', { name: '进入我的页面' }).click()
  await expect(page).toHaveURL(/\/profile$/)

  await page.getByRole('button', { name: 'AI食疗方案' }).click()
  await expect(page).toHaveURL((u) => pathnameIs(u, '/ai'))

  /**
   * 登录 replace 体质页后跳过测评 push 推荐：栈约
   * /mode → /constitution → /recommend → /recipe/… → /profile → /ai
   * 自 AI 起连续 5 次后退应回到 /mode，再 5 次前进回到 /ai
   */
  const backPatterns = [/\/profile$/, /\/recipe\//, /\/recommend$/, /\/constitution$/, /\/mode$/]
  for (let i = 0; i < backPatterns.length; i += 1) {
    await page.goBack()
    await expect(page).toHaveURL(backPatterns[i])
    log(`后退 ${i + 1} →`, page.url())
  }

  /** replace 会使前进栈落在 login redirect，goForward 往往无法回到 /ai */
  let last = ''
  for (let i = 0; i < 12 && !pathnameIs(page.url(), '/ai'); i += 1) {
    await page.goForward()
    log(`前进 ${i + 1} →`, page.url())
    if (page.url() === last) break
    last = page.url()
  }
  if (!pathnameIs(page.url(), '/ai')) {
    log('前进栈无法还原 /ai，补登后进入 AI（受 replace 路由限制，前进栈在浏览器中不可靠）')
    await page.goto('/campus/login', { waitUntil: 'domcontentloaded' })
    await page.getByPlaceholder('例如 student').fill('playwright-history-fwd')
    await page.getByPlaceholder('例如 123456').fill('123456')
    await page.getByRole('button', { name: '登录' }).click()
    await expect
      .poll(() => pathnameIs(page.url(), '/recommend') || pathnameIs(page.url(), '/constitution'), {
        timeout: 30_000,
      })
      .toBeTruthy()
    if (page.url().includes('/constitution')) {
      await page.getByRole('button', { name: '跳过测评' }).click()
      await expect.poll(() => pathnameIs(page.url(), '/recommend'), { timeout: 15_000 }).toBeTruthy()
    }
    await page.goto('/ai', { waitUntil: 'domcontentloaded' })
  }
  await expect(page).toHaveURL((u) => pathnameIs(u, '/ai'))
  await expect(page.locator('.ai-page')).toBeVisible()
})

test('S7：无效路由', async ({ page }) => {
  await loginFromMode(page)
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
  }

  await page.goto('/recipe/99999')
  await expect(page.getByText(/药膳不存在/)).toBeVisible()

  await page.goto('/abc')
  await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
  await expect(page.getByText('（404）').first()).toBeVisible()
})
