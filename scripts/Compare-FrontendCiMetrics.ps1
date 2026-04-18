<#
.SYNOPSIS
  对照 CI 日志中的 FRONTEND_BUILD_SECONDS / DIST_* 与门禁阈值（默认与 .github/workflows/ci.yml 一致）。

.PARAMETER BuildLog
  CI artifact 中的 build-with-timing.log 路径。

.PARAMETER DistLog
  CI artifact 中的 dist-size-report.log 路径。

.EXAMPLE
  .\scripts\Compare-FrontendCiMetrics.ps1 `
    -BuildLog .\frontend-ci-metrics\build-with-timing.log `
    -DistLog .\frontend-ci-metrics\dist-size-report.log
#>
[CmdletBinding()]
param(
    [Parameter(Mandatory = $true)]
    [string] $BuildLog,
    [Parameter(Mandatory = $true)]
    [string] $DistLog,
    [int] $BuildMaxSeconds = 300,
    [long] $DistMaxTotalBytes = 9000000,
    [long] $DistMaxLargestJsBytes = 1048576
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Read-MetricLine {
    param([string] $Path, [string] $Prefix)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "File not found: $Path"
    }
    $line = Get-Content -LiteralPath $Path -Encoding UTF8 | Where-Object { $_ -match [regex]::Escape($Prefix) } | Select-Object -First 1
    if (-not $line) {
        throw "Metric line not found in ${Path}: prefix=${Prefix}"
    }
    if ($line -match ($Prefix + '=(\d+)')) {
        return [long]$Matches[1]
    }
    throw "Cannot parse metric from line: $line"
}

$buildSec = Read-MetricLine -Path $BuildLog -Prefix 'FRONTEND_BUILD_SECONDS'
$totalBytes = Read-MetricLine -Path $DistLog -Prefix 'DIST_TOTAL_BYTES'
$largestJs = Read-MetricLine -Path $DistLog -Prefix 'DIST_LARGEST_JS_BYTES'

Write-Host "FRONTEND_BUILD_SECONDS=$buildSec (max $BuildMaxSeconds)"
Write-Host "DIST_TOTAL_BYTES=$totalBytes (max $DistMaxTotalBytes)"
Write-Host "DIST_LARGEST_JS_BYTES=$largestJs (max $DistMaxLargestJsBytes)"

$failed = $false
if ($buildSec -gt $BuildMaxSeconds) {
    Write-Warning "Build duration exceeds FRONTEND_BUILD_MAX_SECONDS."
    $failed = $true
}
if ($totalBytes -gt $DistMaxTotalBytes) {
    Write-Warning "dist total bytes exceeds FRONTEND_DIST_MAX_TOTAL_BYTES."
    $failed = $true
}
if ($largestJs -gt $DistMaxLargestJsBytes) {
    Write-Warning "Largest JS exceeds FRONTEND_DIST_MAX_LARGEST_JS_BYTES."
    $failed = $true
}

if ($failed) {
    exit 1
}
Write-Host "All checks passed (under CI thresholds)."
exit 0
