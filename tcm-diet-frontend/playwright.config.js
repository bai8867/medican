// @ts-check
import { defineConfig, devices } from '@playwright/test'

/** CI gate 用独立端口跑 preview，避免与本机 dev:11999 冲突 */
const ciGate = process.env.CI_E2E_GATE === '1'
/** `ui-render-smoke`：独立端口，避免与本机常驻 `npm run dev`（11999）抢端口 */
const renderSmoke = process.env.PW_FRONTEND_RENDER_SMOKE === '1'
const port = ciGate ? '11998' : renderSmoke ? '11997' : '11999'
const base = `http://127.0.0.1:${port}`

export default defineConfig({
  testDir: './e2e',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  reporter: [['list'], ['html', { open: 'never', outputFolder: 'playwright-report' }]],
  timeout: 120_000,
  expect: { timeout: 10_000 },
  use: {
    /** 避免部分环境 localhost → ::1 与 Vite 监听不一致导致 #app 永不挂载 */
    baseURL: base,
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    actionTimeout: 10_000,
    navigationTimeout: 10_000,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  /**
   * CI_E2E_GATE=1：对已 `npm run build` 的 dist 跑 `vite preview`，贴近生产资源路径，避免 dev 独有行为。
   * 本地默认不复用端口：11999 若被非本项目的进程占用时，`reuseExistingServer:true` 会误判就绪，
   * 导致 HTML 能打开但 /src/*.js 全部 404、#app 永不挂载（表现为「页面加载失败」）。
   * 本地已手动 `npm run dev` 且希望 E2E 直连该实例时：`PW_REUSE_DEV_SERVER=1 npm run test:e2e`。
   */
  webServer: ciGate
    ? {
        command: `npx vite preview --host 127.0.0.1 --port ${port}`,
        url: `${base}/mode`,
        reuseExistingServer: false,
        timeout: 120_000,
      }
    : renderSmoke
      ? {
          command: `npx vite --mode development --host 127.0.0.1 --port ${port}`,
          url: `${base}/mode`,
          reuseExistingServer: false,
          timeout: 120_000,
          env: {
            ...process.env,
            VITE_AI_GENERATE_UI_DELAY_MS: '0',
          },
        }
      : {
          command: 'npm run dev',
          url: `${base}/mode`,
          reuseExistingServer:
            process.env.CI === 'true' || process.env.CI === '1'
              ? false
              : process.env.PW_REUSE_DEV_SERVER === '1',
          timeout: 120_000,
          env: {
            ...process.env,
            /** 避免 E2E 超过单测 10s 预算 */
            VITE_AI_GENERATE_UI_DELAY_MS: '0',
          },
        },
})
