#!/usr/bin/env bash
# 在 `tcm-diet-frontend` 下执行 `npm run build` 后运行，向 stdout 输出 dist 总体积与最大的若干静态资源（供 CI 日志留存）。
#
# 可选门禁（Linux CI）：设置 FRONTEND_DIST_GUARD=1 时校验总体积与最大单 JS 文件，超限则 exit 1。
#   FRONTEND_DIST_MAX_TOTAL_BYTES   默认 9000000（约 8.6 MiB；CI 当前见 .github/workflows/ci.yml，已略收紧）
#   FRONTEND_DIST_MAX_LARGEST_JS_BYTES 默认 1048576（1 MiB；CI 已略收紧时可对照 artifact ci-metrics-summary.txt）
#
# 调紧阈值：在完成懒加载/拆包优化后，将上述环境变量下调并提交 workflow 变更。
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIST="${ROOT}/tcm-diet-frontend/dist"
if [[ ! -d "$DIST" ]]; then
  echo "report-frontend-dist-size: dist 目录不存在: $DIST" >&2
  exit 1
fi
echo "=== Frontend dist size ==="
du -sh "$DIST" | awk '{print "dist total:", $1}'

TOTAL_BYTES="$(du -sb "$DIST" | awk '{print $1}')"
echo "DIST_TOTAL_BYTES=${TOTAL_BYTES}"

LARGEST_JS_BYTES="$(find "$DIST" -type f -name '*.js' -printf '%s\n' 2>/dev/null | sort -nr | head -1 || true)"
if [[ -z "${LARGEST_JS_BYTES}" ]]; then
  LARGEST_JS_BYTES=0
fi
echo "DIST_LARGEST_JS_BYTES=${LARGEST_JS_BYTES}"

echo "=== Largest assets (top 15 by bytes) ==="
find "$DIST" -type f \( -name '*.js' -o -name '*.css' -o -name '*.woff2' \) -printf '%s\t%p\n' 2>/dev/null | sort -nr | head -15 | awk '{printf "%.1f KB\t%s\n", $1/1024, $2}' || true

if [[ "${FRONTEND_DIST_GUARD:-}" == "1" ]]; then
  max_total="${FRONTEND_DIST_MAX_TOTAL_BYTES:-9000000}"
  max_js="${FRONTEND_DIST_MAX_LARGEST_JS_BYTES:-1048576}"
  fail=0
  if (( TOTAL_BYTES > max_total )); then
    echo "FRONTEND_DIST_GUARD: dist 总字节 ${TOTAL_BYTES} 超过上限 ${max_total}" >&2
    fail=1
  fi
  if (( LARGEST_JS_BYTES > max_js )); then
    echo "FRONTEND_DIST_GUARD: 最大 JS 文件 ${LARGEST_JS_BYTES} 字节超过上限 ${max_js}" >&2
    fail=1
  fi
  if (( fail )); then
    exit 1
  fi
  echo "FRONTEND_DIST_GUARD: 体积检查通过（total<=${max_total}, largest_js<=${max_js}）"
fi
