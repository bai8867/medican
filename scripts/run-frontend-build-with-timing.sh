#!/usr/bin/env bash
# 在 `tcm-diet-frontend` 目录下执行（与 CI working-directory 一致）：`npm run build` 并输出耗时秒数。
#
# 可选门禁：FRONTEND_BUILD_GUARD=1 时若耗时超过 FRONTEND_BUILD_MAX_SECONDS（默认 300）则 exit 1。
set -euo pipefail
start="$(date +%s)"
npm run build
end="$(date +%s)"
dur=$((end - start))
echo "FRONTEND_BUILD_SECONDS=${dur}"
if [[ "${FRONTEND_BUILD_GUARD:-}" == "1" ]]; then
  max="${FRONTEND_BUILD_MAX_SECONDS:-300}"
  if (( dur > max )); then
    echo "FRONTEND_BUILD_GUARD: 构建耗时 ${dur}s 超过上限 ${max}s" >&2
    exit 1
  fi
  echo "FRONTEND_BUILD_GUARD: 构建耗时检查通过（<=${max}s）"
fi
