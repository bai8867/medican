/**
 * 前端 UI 渲染冒烟：以 Mock 模式启动 Vite（与 `e2e/ui-render-smoke.spec.js` 配套），不依赖本机 MySQL / Spring。
 *
 * 用法（仓库根）:
 *   node scripts/run-frontend-render-smoke.mjs
 *
 * 或在前端目录:
 *   npm run test:e2e:render
 */
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const root = join(dirname(fileURLToPath(import.meta.url)), '..')
const fe = join(root, 'tcm-diet-frontend')

if (!existsSync(join(fe, 'package.json'))) {
  console.error('未找到 tcm-diet-frontend')
  process.exit(1)
}

process.env.VITE_USE_MOCK = 'true'
process.env.PW_FRONTEND_RENDER_SMOKE = '1'
if (!process.env.VITE_AI_GENERATE_UI_DELAY_MS) {
  process.env.VITE_AI_GENERATE_UI_DELAY_MS = '0'
}

const r = spawnSync('npx', ['playwright', 'test', 'e2e/ui-render-smoke.spec.js'], {
  cwd: fe,
  stdio: 'inherit',
  env: { ...process.env },
  shell: true,
})
process.exit(r.status ?? 1)
