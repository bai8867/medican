/**
 * P1-1：统计 vue-tsc 报错条数（便于 CI 日志收敛指标）。
 * 在仓库根执行: node scripts/count-vue-tsc-errors.mjs
 * 若存在 error TS* 行则 exit 1（与 npm run typecheck 一致）；仅打印计数可用环境变量 TCM_TYPECHECK_REPORT_ONLY=1。
 */
import { spawnSync } from 'node:child_process'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const __dirname = dirname(fileURLToPath(import.meta.url))
const frontend = join(__dirname, '..', 'tcm-diet-frontend')
const reportOnly = process.env.TCM_TYPECHECK_REPORT_ONLY === '1'

const cmd = process.platform === 'win32' ? 'npx.cmd' : 'npx'
const r = spawnSync(cmd, ['vue-tsc', '--noEmit', '--pretty', 'false'], {
  cwd: frontend,
  encoding: 'utf8',
  maxBuffer: 20 * 1024 * 1024,
  shell: process.platform === 'win32',
})

const combined = `${r.stdout || ''}\n${r.stderr || ''}`
const errorLines = combined.split(/\r?\n/).filter((line) => /\berror TS\d+/.test(line))

console.log(`[typecheck-report] vue-tsc exitCode=${r.status ?? r.signal} distinct_error_lines=${errorLines.length}`)
if (errorLines.length > 0) {
  const preview = combined.trim().slice(0, 12000)
  console.log(preview)
}

if (reportOnly) {
  process.exit(0)
}

process.exit(errorLines.length > 0 ? 1 : 0)
