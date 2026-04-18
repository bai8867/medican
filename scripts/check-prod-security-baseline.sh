#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

require_line() {
  local file="$1"
  local pattern="$2"
  local description="$3"
  if ! grep -Eq "${pattern}" "${file}"; then
    echo "[security-check] missing: ${description} (${file})"
    exit 1
  fi
}

require_file() {
  local file="$1"
  if [[ ! -f "${file}" ]]; then
    echo "[security-check] missing file: ${file}"
    exit 1
  fi
}

APP_PROD_FILE="${REPO_ROOT}/campus-diet-backend/src/main/resources/application-prod.yml"
VALIDATOR_FILE="${REPO_ROOT}/campus-diet-backend/src/main/java/com/campus/diet/config/ProdSecurityBaselineValidator.java"
CHECKLIST_FILE="${REPO_ROOT}/docs/security-baseline-checklist.md"

require_file "${APP_PROD_FILE}"
require_file "${VALIDATOR_FILE}"
require_file "${CHECKLIST_FILE}"

require_line "${APP_PROD_FILE}" "mode:[[:space:]]*never" "prod sql init disabled"
require_line "${APP_PROD_FILE}" "seed-mock-recipes:[[:space:]]*false" "prod mock recipe seed disabled"
require_line "${APP_PROD_FILE}" "seed-demo-interactions:[[:space:]]*false" "prod demo interaction seed disabled"
require_line "${APP_PROD_FILE}" "seed-weekly-calendar:[[:space:]]*false" "prod weekly calendar seed disabled"

require_line "${VALIDATOR_FILE}" "spring\\.datasource\\.username" "runtime validator checks datasource username"
require_line "${VALIDATOR_FILE}" "spring\\.datasource\\.password" "runtime validator checks datasource password"
require_line "${VALIDATOR_FILE}" "change-this-db-password" "runtime validator rejects application.yml datasource password placeholder"
require_line "${VALIDATOR_FILE}" "campus\\.seed-users\\.admin-password" "runtime validator checks seed user passwords"
require_line "${VALIDATOR_FILE}" "campus\\.jwt\\.secret" "runtime validator checks jwt secret"
require_line "${VALIDATOR_FILE}" "LLM_API_KEY" "runtime validator checks llm key"

echo "[security-check] prod security baseline checks passed."
