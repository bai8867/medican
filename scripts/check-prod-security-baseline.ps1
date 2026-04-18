#Requires -Version 5.1
<#
.SYNOPSIS
  Windows 等价于 scripts/check-prod-security-baseline.sh：静态核对 prod 配置片段与校验器源码关键字。

.DESCRIPTION
  在仓库根目录执行：powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\check-prod-security-baseline.ps1
  exit 0 表示通过；失败时 exit 1 并打印缺失项说明。
#>
$ErrorActionPreference = 'Stop'

$RepoRoot = Split-Path -Parent $PSScriptRoot
$AppProd = Join-Path $RepoRoot 'campus-diet-backend\src\main\resources\application-prod.yml'
$Validator = Join-Path $RepoRoot 'campus-diet-backend\src\main\java\com\campus\diet\config\ProdSecurityBaselineValidator.java'
$Checklist = Join-Path $RepoRoot 'docs\security-baseline-checklist.md'

function Require-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "[security-check] missing file: $Path"
        exit 1
    }
}

function Require-Line {
    param(
        [string]$Path,
        [string]$Pattern,
        [string]$Description
    )
    if (-not (Select-String -LiteralPath $Path -Pattern $Pattern -Quiet)) {
        Write-Host "[security-check] missing: $Description ($Path)"
        exit 1
    }
}

Require-File $AppProd
Require-File $Validator
Require-File $Checklist

Require-Line $AppProd 'mode:\s*never' 'prod sql init disabled'
Require-Line $AppProd 'seed-mock-recipes:\s*false' 'prod mock recipe seed disabled'
Require-Line $AppProd 'seed-demo-interactions:\s*false' 'prod demo interaction seed disabled'
Require-Line $AppProd 'seed-weekly-calendar:\s*false' 'prod weekly calendar seed disabled'

Require-Line $Validator 'spring\.datasource\.username' 'runtime validator checks datasource username'
Require-Line $Validator 'spring\.datasource\.password' 'runtime validator checks datasource password'
Require-Line $Validator 'change-this-db-password' 'runtime validator rejects application.yml datasource password placeholder'
Require-Line $Validator 'campus\.seed-users\.admin-password' 'runtime validator checks seed user passwords'
Require-Line $Validator 'campus\.jwt\.secret' 'runtime validator checks jwt secret'
Require-Line $Validator 'LLM_API_KEY' 'runtime validator checks llm key'

Write-Host '[security-check] prod security baseline checks passed.'
