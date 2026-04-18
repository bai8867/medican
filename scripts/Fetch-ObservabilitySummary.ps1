#Requires -Version 5.1
<#
.SYNOPSIS
  拉取管理端 GET /api/admin/dashboard/observability-summary，用于最小看板探针或本地排障。

.PARAMETER BaseUrl
  后端根地址，默认 http://127.0.0.1:11888（与 application 默认端口一致）。

.PARAMETER Token
  管理员 JWT；未传时读取环境变量 MEDICAN_ADMIN_JWT（或 TCM_ADMIN_JWT）。

.PARAMETER OutFile
  若指定，将 ApiResponse 的 JSON 原文写入该路径。

.PARAMETER MaxHttpErrorRateClassified
  可选门禁：当 data.http.error_rate_classified 非 null 且大于该值时 exit 5。默认 -1 表示不检查。

.PARAMETER MinRecommendNonemptyRatio
  可选门禁：当 data.recommend.feed.nonempty_ratio 非 null 且小于该值时 exit 5。不传或 $null 表示不检查。

.EXAMPLE
  $env:MEDICAN_ADMIN_JWT = '<paste>'
  powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Fetch-ObservabilitySummary.ps1

.EXAMPLE
  .\scripts\Fetch-ObservabilitySummary.ps1 -MaxHttpErrorRateClassified 0.15 -MinRecommendNonemptyRatio 0.3
#>
param(
    [string]$BaseUrl = 'http://127.0.0.1:11888',
    [string]$Token = $(if ($env:MEDICAN_ADMIN_JWT) { $env:MEDICAN_ADMIN_JWT } else { $env:TCM_ADMIN_JWT }),
    [string]$OutFile = '',
    [double]$MaxHttpErrorRateClassified = -1,
    [Nullable[double]]$MinRecommendNonemptyRatio = $null
)

$ErrorActionPreference = 'Stop'

if ([string]::IsNullOrWhiteSpace($Token)) {
    Write-Error '缺少 JWT：请传 -Token 或设置环境变量 MEDICAN_ADMIN_JWT / TCM_ADMIN_JWT。'
    exit 2
}

$uri = ($BaseUrl.TrimEnd('/') + '/api/admin/dashboard/observability-summary')
$headers = @{ Authorization = "Bearer $Token" }

try {
    $resp = Invoke-RestMethod -Uri $uri -Headers $headers -Method Get -ContentType 'application/json'
} catch {
    Write-Error "HTTP 请求失败: $($_.Exception.Message)"
    exit 1
}

if ($null -eq $resp.code -or $resp.code -ne 200) {
    Write-Host ($resp | ConvertTo-Json -Depth 8)
    Write-Error "接口返回非 200: code=$($resp.code)"
    exit 1
}

$json = $resp | ConvertTo-Json -Depth 12
Write-Host $json

if ($OutFile) {
    $dir = Split-Path -Parent $OutFile
    if ($dir -and -not (Test-Path -LiteralPath $dir)) {
        New-Item -ItemType Directory -Path $dir -Force | Out-Null
    }
    Set-Content -LiteralPath $OutFile -Value $json -Encoding utf8
}

$gateFailed = $false
$data = $resp.data
if ($null -ne $data -and $MaxHttpErrorRateClassified -ge 0) {
    $rate = $data.'http.error_rate_classified'
    if ($null -ne $rate -and [double]$rate -gt $MaxHttpErrorRateClassified) {
        Write-Warning "Gate: http.error_rate_classified=$rate > $MaxHttpErrorRateClassified"
        $gateFailed = $true
    }
}
if ($null -ne $data -and $null -ne $MinRecommendNonemptyRatio) {
    $ratio = $data.'recommend.feed.nonempty_ratio'
    if ($null -ne $ratio -and [double]$ratio -lt [double]$MinRecommendNonemptyRatio) {
        Write-Warning "Gate: recommend.feed.nonempty_ratio=$ratio < $MinRecommendNonemptyRatio"
        $gateFailed = $true
    }
}
if ($gateFailed) {
    exit 5
}

exit 0
