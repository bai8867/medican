// @ts-check
import { test, expect } from '@playwright/test'

/**
 * 前端「渲染 + 脚本挂载」冒烟：验证 Vue 应用壳、路由懒加载、Vant/Element、Mock 网关等是否协同工作。
 *
 * 须以 Mock 模式启动 Vite（由 `npm run test:e2e:render` / `scripts/run-frontend-render-smoke.mjs` 注入 `VITE_USE_MOCK=true`）。
 * 不替代完整联调 E2E（`student-navigation` / `recommend-smoke` 等）。
 */

function attachPageDiagnostics(page) {
  const errors = []
  page.on('pageerror', (err) => {
    errors.push(`pageerror: ${err.message}`)
  })
  page.on('console', (msg) => {
    if (msg.type() === 'error') {
      errors.push(`console.error: ${msg.text()}`)
    }
  })
  return {
    assertClean() {
      const fatal = errors.filter((e) => !/ResizeObserver loop|Failed to load resource/i.test(e))
      expect(fatal, `页面运行期错误:\n${fatal.join('\n')}`).toEqual([])
    },
  }
}

async function assertAppMounted(page) {
  await expect(page.locator('#app')).toBeAttached({ timeout: 30_000 })
  const ok = await page.evaluate(() => {
    const el = document.querySelector('#app')
    return !!(el && el.innerHTML && el.innerHTML.trim().length > 0)
  })
  expect(ok).toBeTruthy()
}

test.describe('门户与公开页（无登录）', () => {
  test.beforeEach(async ({ page }) => {
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
  })

  test('模式页 /mode 挂载', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await page.goto('/mode', { waitUntil: 'load' })
    await assertAppMounted(page)
    await expect(page.getByRole('heading', { name: '校园药膳推荐系统' })).toBeVisible()
    diag.assertClean()
  })

  test('入口 / 挂载', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await page.goto('/', { waitUntil: 'load' })
    await assertAppMounted(page)
    await expect(page.getByRole('heading', { name: '校园药膳推荐系统' })).toBeVisible()
    diag.assertClean()
  })

  test('账号禁用页', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await page.goto('/account-disabled', { waitUntil: 'load' })
    await assertAppMounted(page)
    await expect(page.locator('#app')).toContainText('账号已禁用')
    diag.assertClean()
  })

  test('404 页', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await page.goto('/__ui_render_smoke__/missing', { waitUntil: 'load' })
    await assertAppMounted(page)
    await expect(page.getByRole('heading', { name: '页面不存在' })).toBeVisible()
    diag.assertClean()
  })
})

/** @param {import('@playwright/test').Page} page */
async function campusMockLoginToHome(page) {
  await page.goto('/mode', { waitUntil: 'load' })
  await page.getByRole('button', { name: /学生端|校园端/ }).click()
  await expect(page).toHaveURL(/\/campus\/login/)
  await page.getByPlaceholder('例如 student').fill('render-smoke')
  await page.getByLabel('密码').fill('any')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/(home|recommend|constitution)$/, { timeout: 15_000 })
  if (page.url().includes('/constitution')) {
    await page.getByRole('button', { name: '跳过测评' }).click()
    await page.waitForURL(/\/(home|recommend)$/, { timeout: 15_000 })
  }
  await expect(page.locator('.recommend')).toBeVisible({ timeout: 15_000 })
}

test.describe('校园端（Mock 登录后多路由壳）', () => {
  test.beforeEach(async ({ page }) => {
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
  })

  test('主流程页面依次可渲染', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await campusMockLoginToHome(page)

    const steps = [
      {
        path: '/home',
        assert: async () => {
          await expect(page.locator('.recommend')).toBeVisible()
          await expect(page.locator('.toolbar')).toBeVisible()
        },
      },
      {
        path: '/calendar',
        assert: async () => {
          await expect(page.getByRole('heading', { name: '本周药膳日历' })).toBeVisible()
        },
      },
      {
        path: '/constitution',
        assert: async () => {
          await expect(page.getByRole('heading', { name: '体质采集' })).toBeVisible()
        },
      },
      {
        path: '/recipe/demo-001',
        assert: async () => {
          await expect(page.locator('#app')).toContainText('黄芪炖鸡')
        },
      },
      {
        path: '/ai',
        assert: async () => {
          await expect(page.getByText('AI 食疗方案').first()).toBeVisible()
        },
      },
      {
        path: '/ai/plan/offline-smoke',
        assert: async () => {
          await expect(page.getByText('AI 方案详情')).toBeVisible()
          await expect(page.locator('.ai-plan-detail')).toBeVisible()
        },
      },
      {
        path: '/profile',
        assert: async () => {
          await expect(page.getByRole('heading', { name: '我的' })).toBeVisible()
        },
      },
      {
        path: '/scenes',
        assert: async () => {
          await expect(page.getByText('校园十大场景食疗')).toBeVisible()
        },
      },
      {
        path: '/scene/1',
        assert: async () => {
          await expect(page.getByText('考前突击·熬夜救星')).toBeVisible()
        },
      },
      {
        path: '/settings',
        assert: async () => {
          await expect(page.getByRole('heading', { name: '设置', exact: true })).toBeVisible()
        },
      },
      {
        path: '/settings/profile',
        assert: async () => {
          await expect(page.getByText('个人资料')).toBeVisible()
        },
      },
      {
        path: '/settings/password',
        assert: async () => {
          await expect(page.getByText('修改密码')).toBeVisible()
        },
      },
      {
        path: '/settings/security',
        assert: async () => {
          await expect(page.getByText('账户安全')).toBeVisible()
        },
      },
      {
        path: '/settings/dislikes',
        assert: async () => {
          await expect(page.getByText('不感兴趣', { exact: true })).toBeVisible()
        },
      },
      {
        path: '/settings/privacy',
        assert: async () => {
          await expect(page.getByText('隐私政策')).toBeVisible()
        },
      },
      {
        path: '/settings/preference',
        assert: async () => {
          await expect(page.getByText('饮食偏好')).toBeVisible()
        },
      },
    ]

    for (const step of steps) {
      await page.goto(step.path, { waitUntil: 'load' })
      await assertAppMounted(page)
      await step.assert()
    }

    diag.assertClean()
  })
})

/** @param {import('@playwright/test').Page} page */
async function adminMockLoginToDashboard(page) {
  await page.goto('/mode', { waitUntil: 'load' })
  await page.getByRole('button', { name: '后台管理' }).click()
  await expect(page).toHaveURL(/\/admin\/login/)
  await page.getByPlaceholder('admin / canteen / canteen_manager').fill('admin')
  await page.getByLabel('密码').fill('123456')
  await page.getByRole('button', { name: '登录' }).click()
  await page.waitForURL(/\/admin\/dashboard/, { timeout: 15_000 })
}

test.describe('管理后台（Mock 登录后壳）', () => {
  test.beforeEach(async ({ page }) => {
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
  })

  test('看板与主要子路由可渲染', async ({ page }) => {
    const diag = attachPageDiagnostics(page)
    await adminMockLoginToDashboard(page)
    await assertAppMounted(page)
    await expect(page.locator('.dash')).toBeVisible()
    await expect(page.getByText('总收藏数')).toBeVisible()
    // ECharts 在饼图 + 横向柱状图各挂载 canvas；为 0 时表示 init/setOption 失败或容器尺寸为 0
    await expect(page.locator('.dash canvas')).toHaveCount(2, { timeout: 15_000 })

    const adminPaths = [
      { path: '/admin/dashboard', text: '总收藏数' },
      { path: '/admin/campus-calendar', text: '本周药膳日历' },
      { path: '/admin/recipe', text: '药膳管理' },
      { path: '/admin/ingredient', text: '食材管理' },
      { path: '/admin/user', text: '用户管理' },
      { path: '/admin/system/settings', text: '系统设置' },
      { path: '/admin/ai-quality', text: 'AI 质量治理' },
    ]
    for (const { path: p, text } of adminPaths) {
      await page.goto(p, { waitUntil: 'load' })
      await assertAppMounted(page)
      await expect(page.locator('.admin-root')).toBeVisible()
      await expect(page.locator('#app')).toContainText(text)
    }

    diag.assertClean()
  })
})
