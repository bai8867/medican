#Requires -Version 5.1
<#
.SYNOPSIS
  Run campus-diet-backend Maven tests with JDK 11 (P0-1 本地闭环 / CI 对齐).

.DESCRIPTION
  1) Dot-source Use-RepoJavaMaven.ps1（MEDICAN_DEV_TOOLS / .devtools / dev-tools / C:\dev）。
  2) 若仍无可用 java.exe，则回退扫描「C:\Program Files\Microsoft」「C:\Program Files\Eclipse Adoptium」「C:\dev」下的 JDK 11。
  3) 在 campus-diet-backend 执行 mvnw + maven-settings-d.xml。

  用法（仓库根）:
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-BackendTests.ps1
    powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\Run-BackendTests.ps1 -SecurityOnly
#>
param(
  [switch] $SecurityOnly
)

$ErrorActionPreference = "Stop"
$repoRoot = Resolve-Path (Join-Path $PSScriptRoot "..")
$backend = Join-Path $repoRoot "campus-diet-backend"
$settings = Join-Path $repoRoot "maven-settings-d.xml"

function Test-JavaHomePath([string] $jdkRoot) {
  if (-not $jdkRoot) { return $false }
  $java = Join-Path $jdkRoot.TrimEnd('\') "bin\java.exe"
  return Test-Path -LiteralPath $java
}

function Find-Jdk11Under([string] $basePath) {
  if (-not (Test-Path -LiteralPath $basePath)) { return $null }
  $dirs = @(Get-ChildItem -LiteralPath $basePath -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like 'jdk-*' -or $_.Name -like 'jdk*' -or $_.Name -like 'microsoft-jdk-*' })
  foreach ($d in $dirs) {
    $release = Join-Path $d.FullName "release"
    if (-not (Test-Path -LiteralPath $release)) { continue }
    $txt = Get-Content -LiteralPath $release -Raw -ErrorAction SilentlyContinue
    if ($txt -and ($txt -match 'JAVA_VERSION="11')) {
      return $d.FullName
    }
  }
  foreach ($d in $dirs) {
    if ($d.Name -match '(^jdk-11|^jdk11|(^|[._-])11\.0\.|microsoft-jdk-11)') {
      return $d.FullName
    }
  }
  return $null
}

. (Join-Path $repoRoot "scripts\Use-RepoJavaMaven.ps1")

if (-not (Test-JavaHomePath $env:JAVA_HOME)) {
  Write-Warning "JAVA_HOME not usable after Use-RepoJavaMaven; probing common JDK 11 installs."
  $candidates = @(
    "C:\Program Files\Microsoft",
    "C:\Program Files\Eclipse Adoptium",
    "C:\dev"
  )
  foreach ($base in $candidates) {
    $jdk = Find-Jdk11Under $base
    if ($jdk) {
      $env:JAVA_HOME = $jdk.TrimEnd('\')
      $env:PATH = "$(Join-Path $env:JAVA_HOME 'bin');$env:PATH"
      Write-Host "[JAVA_HOME fallback] $env:JAVA_HOME"
      break
    }
  }
}

if (-not (Test-JavaHomePath $env:JAVA_HOME)) {
  throw "No JDK 11 found. Set MEDICAN_DEV_TOOLS, install JDK 11 under C:\dev, or fix JAVA_HOME (see AGENTS.md)."
}

Push-Location $backend
try {
  $mvnArgs = @("-B", "-ntp", "-s", $settings, "test")
  if ($SecurityOnly) {
    $mvnArgs = @("-B", "-ntp", "-s", $settings, "-Dtest=ProdSecurityBaselineValidatorTest", "test")
  }
  & .\mvnw.cmd @mvnArgs
  if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
  }
}
finally {
  Pop-Location
}
