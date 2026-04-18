import { test, expect } from '@playwright/test'

function clearBrowserStores(page) {
  return page.evaluate(() => {
    localStorage.clear()
    sessionStorage.clear()
  })
}

/** 必须在同源页面调用，否则 localStorage 会抛 SecurityError */
async function gotoOriginAndClear(page) {
  await page.goto('/')
  await clearBrowserStores(page)
}

/** 模式页 → 校园登录 → 补齐体质后进入推荐页 */
async function loginStudentToRecommend(page) {
  await page.goto('/')
  await page.getByRole('button', { name: /校园端/ }).click()
  await expect(page).toHaveURL(/\/campus\/login/)
  await page.getByPlaceholder('例如 student').fill('demo')
  await page.getByLabel('密码').fill('demo123')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/constitution|\/recommend|\/home/)
  await page.evaluate(() => {
    const k = 'tcm_user_profile'
    let state = {}
    try {
      state = JSON.parse(localStorage.getItem(k) || '{}')
    } catch {
      /* ignore */
    }
    state.constitutionCode = 'pinghe'
    state.constitutionSource = 'survey'
    state.constitutionSurveyCompleted = true
    localStorage.setItem(k, JSON.stringify(state))
  })
  await page.goto('/recommend')
  await expect(page.locator('.recommend')).toBeVisible({ timeout: 20_000 })
}

async function adminLogin(page) {
  await page.goto('/mode')
  await page.getByRole('button', { name: '后台管理' }).click()
  await expect(page).toHaveURL(/\/admin\/login/)
  await page.getByPlaceholder('admin / canteen / canteen_manager').fill('admin')
  await page.getByPlaceholder('123456').fill('admin123')
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/\/admin\/dashboard/)
}

/** 学生壳页面内不应出现指向后台的链接 */
async function assertNoAdminLinksInDom(page) {
  const hits = await page.evaluate(() => {
    const out = []
    for (const a of document.querySelectorAll('a[href]')) {
      const h = a.getAttribute('href') || ''
      if (h.includes('/admin')) out.push(h)
    }
    return out
  })
  expect(
    hits,
    `unexpected /admin links on ${page.url()}: ${JSON.stringify(hits)}`,
  ).toEqual([])
}

test.describe('学生端与后台管理端隔离', () => {
  test.beforeEach(async ({ page }) => {
    await gotoOriginAndClear(page)
  })

  test('从学生端手动访问 /admin 应被门户拦截并返回模式页（不崩溃）', async ({
    page,
  }) => {
    await page.evaluate(() => localStorage.removeItem('tcm_admin_auth'))
    await loginStudentToRecommend(page)
    await page.goto('/admin')
    await expect(page).toHaveURL(/\/$/)
    await expect(page.getByRole('heading', { name: '校园药膳推荐系统' })).toBeVisible()
    const path = new URL(page.url()).pathname
    expect(path.startsWith('/admin')).toBeFalsy()
    expect(path === '/recommend' || path.startsWith('/constitution')).toBeFalsy()
  })

  test('后台已登录时新开标签访问学生端：首页回门户且后台会话保留', async ({
    browser,
  }) => {
    const ctx = await browser.newContext()
    const adminPage = await ctx.newPage()
    const studentPage = await ctx.newPage()

    await gotoOriginAndClear(adminPage)
    await loginStudentToRecommend(studentPage)
    await adminLogin(adminPage)

    const adminLsBefore = await adminPage.evaluate(() =>
      localStorage.getItem('tcm_admin_auth'),
    )
    expect(adminLsBefore).toBeTruthy()

    await studentPage.goto('/home')
    await expect(studentPage).toHaveURL(/\/campus\/login\?redirect=\/home$/)
    await expect(studentPage.getByRole('heading', { name: '校园端登录' })).toBeVisible()

    await expect(adminPage).toHaveURL(/\/admin\/dashboard/)
    const adminLsAfter = await adminPage.evaluate(() =>
      localStorage.getItem('tcm_admin_auth'),
    )
    expect(adminLsAfter).toBeTruthy()

  })

  test('学生端页面 DOM 中无指向 /admin 的链接（模式页除外）', async ({ page }) => {
    await loginStudentToRecommend(page)

    const paths = ['/recommend', '/constitution', '/ai', '/profile', '/recipe/demo-001']
    for (const p of paths) {
      await page.goto(p)
      await page.waitForLoadState('networkidle').catch(() => null)
      await assertNoAdminLinksInDom(page)
    }
  })

  test('后台退出登录后：学生画像保留并可重新进入校园端', async ({ browser }) => {
    const ctx = await browser.newContext()
    const studentPage = await ctx.newPage()
    const adminPage = await ctx.newPage()

    await gotoOriginAndClear(studentPage)
    await loginStudentToRecommend(studentPage)
    await adminLogin(adminPage)

    await studentPage.bringToFront()
    await expect(studentPage).toHaveURL(/\/home|\/recommend|\/constitution/)

    await adminPage.bringToFront()
    await adminPage.getByRole('button', { name: '退出' }).click()
    await expect(adminPage).toHaveURL(/\/admin\/login/)

    const userLs = await studentPage.evaluate(() =>
      localStorage.getItem('tcm_user_profile'),
    )
    expect(userLs).toBeTruthy()
    const parsed = JSON.parse(userLs)
    expect(Boolean(parsed.token)).toBe(true)

    await studentPage.reload()
    await expect(studentPage).toHaveURL(/\/campus\/login|\/home|\/recommend|\/constitution/)

    await loginStudentToRecommend(studentPage)

  })
})
