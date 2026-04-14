#Requires -Version 5.1
<#
.SYNOPSIS
  本机一键：JDK 11 + Maven + MariaDB 启动 + 编译并运行 campus-diet-backend（联调用）。

  默认工具目录（均在 D 盘项目下，避免占满 C 盘）：
    <项目根>/dev-tools/jdk-11.0.30.7-hotspot
    <项目根>/dev-tools/apache-maven-3.9.14
    <项目根>/dev-tools/MariaDB-12.2

  若尚未复制到 dev-tools，可仍用 -JdkHome / -MavenHome / -MariaDbHome 指向本机路径。

  用法（在 campus-diet-backend 目录下）:
    .\scripts\dev-bootstrap.ps1
    .\scripts\dev-bootstrap.ps1 -SkipBuild
    $env:CAMPUS_DIET_AI_MOCK='true'; .\scripts\dev-bootstrap.ps1
#>
param(
    [switch] $SkipBuild,
    [string] $MariaDbHome = "",
    [string] $JdkHome = "",
    [string] $MavenHome = ""
)

$repoRoot = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent
$devTools = Join-Path $repoRoot "dev-tools"
if (-not $MariaDbHome) { $MariaDbHome = Join-Path $devTools "MariaDB-12.2" }
if (-not $JdkHome) { $JdkHome = Join-Path $devTools "jdk-11.0.30.7-hotspot" }
if (-not $MavenHome) { $MavenHome = Join-Path $devTools "apache-maven-3.9.14" }

$ErrorActionPreference = "Stop"

if (Test-Path $JdkHome) {
    $env:JAVA_HOME = $JdkHome
    $env:Path = "$JdkHome\bin;$env:Path"
} else {
    Write-Warning "未找到 JDK 11: $JdkHome ，请安装 EclipseAdoptium.Temurin.11.JDK 或设置 `$JdkHome"
}

if (Test-Path "$MavenHome\bin\mvn.cmd") {
    $env:Path = "$MavenHome\bin;$env:Path"
} else {
    Write-Warning "未找到 Maven: $MavenHome ，请解压 apache-maven-*-bin.zip 到 `$MavenHome 或调整参数"
}

$mysqld = Join-Path $MariaDbHome "bin\mysqld.exe"
$mysql = Join-Path $MariaDbHome "bin\mysql.exe"
$myIni = Join-Path $MariaDbHome "data\my.ini"

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    if (-not (Test-Path $mysqld)) {
        throw "未找到 MariaDB: $mysqld ，请安装 MariaDB.Server 或设置 `$MariaDbHome"
    }
    Write-Host "启动 MariaDB (mysqld)..."
    Start-Process -FilePath $mysqld -ArgumentList "--defaults-file=$myIni" -WindowStyle Hidden
    Start-Sleep -Seconds 4
}

if (-not (Get-Process mysqld -ErrorAction SilentlyContinue)) {
    throw "MariaDB 未能启动，请检查 $myIni 与 data 目录权限。"
}

if (-not $SkipBuild) {
    $backendRoot = Split-Path $PSScriptRoot -Parent
    Push-Location $backendRoot
    try {
        Write-Host "Maven package..."
        mvn -DskipTests package -B -q
    } finally {
        Pop-Location
    }
}

$jar = Join-Path (Split-Path $PSScriptRoot -Parent) "target\campus-diet-backend-1.0.0-SNAPSHOT.jar"
if (-not (Test-Path $jar)) { throw "未找到 JAR: $jar ，请先编译或去掉 -SkipBuild" }

Write-Host "启动后端: $jar"
if (-not $env:CAMPUS_DIET_AI_MOCK) { $env:CAMPUS_DIET_AI_MOCK = "true" }
Start-Process -FilePath java -ArgumentList @("-jar", $jar) -WorkingDirectory (Split-Path $jar -Parent) -NoNewWindow

Write-Host "已启动。健康检查: http://localhost:11888/api/health"
Write-Host "（可选）初始化库表与种子数据：若库为空，重启应用即可由 spring.sql.init 执行 schema/data.sql"
