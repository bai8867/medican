/**
 * 前端 CI gate：先保证有 dist，再以 preview 模式跑 Playwright（见 playwright.config.js 中 CI_E2E_GATE）。
 */
import { spawnSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, join } from 'node:path'

const root = join(dirname(fileURLToPath(import.meta.url)), '..', 'tcm-diet-frontend')
if (!existsSync(join(root, 'dist', 'index.html'))) {
  console.error('缺少 tcm-diet-frontend/dist，请先执行 npm run build')
  process.exit(1)
}

process.env.CI_E2E_GATE = '1'
/** Windows：`spawnSync('npx', …, { shell:false })` 常 EINVAL；用 `cmd /c` 拿到稳定退出码 */
const isWin = process.platform === 'win32'
const r = isWin
  ? spawnSync(
      process.env.ComSpec || 'cmd.exe',
      ['/d', '/s', '/c', 'npx playwright test e2e/ci-gate.spec.js'],
      { cwd: root, stdio: 'inherit', env: process.env, windowsVerbatimArguments: false },
    )
  : spawnSync('npx', ['playwright', 'test', 'e2e/ci-gate.spec.js'], {
      cwd: root,
      stdio: 'inherit',
      env: process.env,
      shell: false,
    })
if (r.error) {
  console.error(r.error)
  process.exit(1)
}
process.exit(typeof r.status === 'number' ? r.status : 1)
