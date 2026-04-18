#Requires -Version 5.1
<#
.SYNOPSIS
  Set JAVA_HOME and Maven PATH for this PowerShell session from portable tool roots.
.DESCRIPTION
  If $env:MEDICAN_DEV_TOOLS points to an existing directory, ONLY that folder is used (single stash; ignores repo dev-tools/.devtools/C:\dev fallbacks).

  Otherwise search (JDK and Maven may come from different roots):
    - <repo>/.devtools
    - <repo>/dev-tools
    - C:\dev

  JDK: only selects Java 11 (reads `release` for JAVA_VERSION="11, then name heuristics).
  Maven: prefers apache-maven-3.9.14, then newest apache-maven-*.

  Prepends JAVA_HOME\bin and MAVEN_HOME\bin to PATH so they override Oracle java8path.

  Does not modify the registry.
#>
$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")

function Get-DevToolRoots([string]$repoRoot) {
  if ($env:MEDICAN_DEV_TOOLS) {
    $raw = $env:MEDICAN_DEV_TOOLS.Trim().TrimEnd('\', '/')
    if ($raw -and (Test-Path -LiteralPath $raw)) {
      return @((Resolve-Path -LiteralPath $raw).Path)
    }
    Write-Warning "MEDICAN_DEV_TOOLS is set but not a valid directory: '$raw' — falling back to .devtools / dev-tools / C:\dev."
  }
  $list = New-Object System.Collections.Generic.List[string]
  foreach ($rel in @('.devtools', 'dev-tools')) {
    $p = Join-Path $repoRoot $rel
    if (Test-Path -LiteralPath $p) {
      $list.Add((Resolve-Path -LiteralPath $p).Path)
    }
  }
  if (Test-Path -LiteralPath 'C:\dev') {
    $list.Add('C:\dev')
  }
  $seen = @{}
  $out = foreach ($x in $list) {
    if (-not $seen.ContainsKey($x)) {
      $seen[$x] = $true
      $x
    }
  }
  return @($out)
}

function Find-Jdk11HomeFromBase([string]$basePath) {
  if (-not (Test-Path -LiteralPath $basePath)) { return $null }
  $dirs = @(Get-ChildItem -LiteralPath $basePath -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like 'jdk-*' -or $_.Name -like 'jdk*' -or $_.Name -like 'microsoft-jdk-*' })
  if ($dirs.Count -eq 0) { return $null }

  foreach ($d in $dirs) {
    $release = Join-Path $d.FullName 'release'
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

function Find-MavenHomeFromBase([string]$basePath) {
  if (-not (Test-Path -LiteralPath $basePath)) { return $null }
  $dirs = @(Get-ChildItem -LiteralPath $basePath -Directory -ErrorAction SilentlyContinue |
      Where-Object { $_.Name -like 'apache-maven-*' })
  if ($dirs.Count -eq 0) { return $null }
  $exact = $dirs | Where-Object { $_.Name -eq 'apache-maven-3.9.14' } | Select-Object -First 1
  if ($exact) { return $exact.FullName }
  return ($dirs | Sort-Object Name -Descending | Select-Object -First 1).FullName
}

function Test-MedicanStrictSingleRoot {
  if (-not $env:MEDICAN_DEV_TOOLS) { return $false }
  $raw = $env:MEDICAN_DEV_TOOLS.Trim().TrimEnd('\', '/')
  return [bool]($raw -and (Test-Path -LiteralPath $raw))
}

$medicStrictMode = Test-MedicanStrictSingleRoot
$roots = Get-DevToolRoots $root
if ($roots.Count -eq 0) {
  Write-Warning "No dev tool roots found: fix MEDICAN_DEV_TOOLS or add .devtools / dev-tools / C:\dev."
}

$jdk = $null
foreach ($base in $roots) {
  $jdk = Find-Jdk11HomeFromBase $base
  if ($jdk) { break }
}

$maven = $null
foreach ($base in $roots) {
  if (-not (Test-Path -LiteralPath $base)) { continue }
  $exact314 = Join-Path $base 'apache-maven-3.9.14'
  if (Test-Path -LiteralPath $exact314) {
    $maven = (Resolve-Path -LiteralPath $exact314).Path
    break
  }
}
if (-not $maven) {
  foreach ($base in $roots) {
    $maven = Find-MavenHomeFromBase $base
    if ($maven) { break }
  }
}

if ($jdk) {
  $env:JAVA_HOME = $jdk.TrimEnd('\')
  $bin = Join-Path $env:JAVA_HOME "bin"
  if (-not (Test-Path (Join-Path $bin "java.exe"))) {
    Write-Warning "JAVA_HOME=$env:JAVA_HOME but bin\java.exe missing."
  } else {
    $env:PATH = "${bin};${env:PATH}"
    Write-Host "[JAVA_HOME] $env:JAVA_HOME"
  }
} else {
  if ($medicStrictMode) {
    Write-Warning "No JDK 11 under MEDICAN_DEV_TOOLS ($($env:MEDICAN_DEV_TOOLS.Trim())). Add jdk-11* there, or unset MEDICAN_DEV_TOOLS to scan .devtools / dev-tools / C:\dev."
  } else {
    Write-Warning "No JDK 11 under scanned dev-tool directories (.devtools, dev-tools, C:\dev). Install Temurin 11 or set JAVA_HOME."
  }
}

if ($maven) {
  $mbin = Join-Path $maven "bin"
  if (Test-Path (Join-Path $mbin "mvn.cmd")) {
    $env:MAVEN_HOME = $maven.TrimEnd('\')
    $env:PATH = "${mbin};${env:PATH}"
    Write-Host "[MAVEN_HOME] $env:MAVEN_HOME"
  }
} else {
  Write-Host "No apache-maven-*; use campus-diet-backend\mvnw.cmd (needs JDK 11; first run may download Maven to %USERPROFILE%\.m2\wrapper)."
}

if ($env:JAVA_HOME) {
  Write-Host "java -version:"
  $javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
  cmd /c "`"$javaExe`" -version"
}
if (Get-Command mvn -ErrorAction SilentlyContinue) {
  Write-Host "mvn -version:"
  cmd /c "mvn -version"
}
