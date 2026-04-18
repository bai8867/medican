#Requires -Version 5.1
<#
.SYNOPSIS
  Windows 侧等价于 scripts/check-prod-security-baseline.sh：静态断言 prod 基线关键行存在。

.DESCRIPTION
  用于本机无 bash/Git Bash 时执行 P0-2 清单第 2 步；CI/Linux 仍可使用 .sh 版本。

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Check-ProdSecurityBaseline.ps1
#>
$ErrorActionPreference = 'Stop'

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path

function Require-File {
    param([Parameter(Mandatory)][string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        Write-Host "[security-check] missing file: $Path"
        exit 1
    }
}

function Require-Line {
    param(
        [Parameter(Mandatory)][string]$Path,
        [Parameter(Mandatory)][string]$Pattern,
        [Parameter(Mandatory)][string]$Description
    )
    Require-File $Path
    $raw = Get-Content -LiteralPath $Path -Raw
    if ($raw -notmatch $Pattern) {
        Write-Host "[security-check] missing: $Description ($Path)"
        exit 1
    }
}

$AppProdFile = Join-Path $RepoRoot 'campus-diet-backend\src\main\resources\application-prod.yml'
$ValidatorFile = Join-Path $RepoRoot 'campus-diet-backend\src\main\java\com\campus\diet\config\ProdSecurityBaselineValidator.java'
$ChecklistFile = Join-Path $RepoRoot 'docs\security-baseline-checklist.md'

Require-File $AppProdFile
Require-File $ValidatorFile
Require-File $ChecklistFile

Require-Line $AppProdFile 'mode:\s*never' 'prod sql init disabled'
Require-Line $AppProdFile 'seed-mock-recipes:\s*false' 'prod mock recipe seed disabled'
Require-Line $AppProdFile 'seed-demo-interactions:\s*false' 'prod demo interaction seed disabled'
Require-Line $AppProdFile 'seed-weekly-calendar:\s*false' 'prod weekly calendar seed disabled'

Require-Line $ValidatorFile 'spring\.datasource\.username' 'runtime validator checks datasource username'
Require-Line $ValidatorFile 'spring\.datasource\.password' 'runtime validator checks datasource password'
Require-Line $ValidatorFile 'campus\.seed-users\.admin-password' 'runtime validator checks seed user passwords'
Require-Line $ValidatorFile 'campus\.jwt\.secret' 'runtime validator checks jwt secret'
Require-Line $ValidatorFile 'LLM_API_KEY' 'runtime validator checks llm key'

Write-Host '[security-check] prod security baseline checks passed.'
