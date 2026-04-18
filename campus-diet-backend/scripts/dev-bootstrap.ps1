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
    $msJdkRoot = "C:\Program Files\Microsoft"
    $wingetJdk11 = $null
    if (Test-Path $msJdkRoot) {
        $wingetJdk11 = (Get-ChildItem $msJdkRoot -Directory -ErrorAction SilentlyContinue |
            Where-Object { $_.Name -like "jdk-11.*" } |
            Sort-Object Name -Descending |
            Select-Object -First 1).FullName
    }
    if ($wingetJdk11 -and (Test-Path $wingetJdk11)) {
        $env:JAVA_HOME = $wingetJdk11
        $env:Path = "$wingetJdk11\bin;$env:Path"
        Write-Host "使用本机 JDK 11: $wingetJdk11"
    } else {
        Write-Warning "未找到 JDK 11: $JdkHome （也未在 $msJdkRoot 下找到 jdk-11.*）。请安装 JDK 11 或设置 -JdkHome"
    }
}

if (Test-Path "$MavenHome\bin\mvn.cmd") {
    $env:Path = "$MavenHome\bin;$env:Path"
} else {
    Write-Warning "未找到 Maven: $MavenHome 。将尝试使用仓库内 mvnw.cmd（需已设置 JAVA_HOME 为 JDK 11）"
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
        if (Get-Command mvn -ErrorAction SilentlyContinue) {
            Write-Host "Maven package (mvn)..."
            mvn -DskipTests package -B -q
        } elseif (Test-Path (Join-Path $backendRoot "mvnw.cmd")) {
            Write-Host "Maven package (mvnw.cmd)..."
            & (Join-Path $backendRoot "mvnw.cmd") -DskipTests package -B -q
        } else {
            throw "未找到 mvn 与 mvnw.cmd，无法编译。请安装 Maven 或从仓库根目录拉取 Maven Wrapper 文件。"
        }
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
