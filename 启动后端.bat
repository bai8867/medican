@echo off
chcp 65001 >nul 2>&1
set "ROOT=%~dp0"
cd /d "%ROOT%campus-diet-backend"

rem 若设置 MEDICAN_DEV_TOOLS 且目录存在，则只从该目录取 JDK/Maven（与 PowerShell 脚本「单源」一致）
rem 否则依次尝试 .devtools / dev-tools / C:\dev。优先 jdk-11*；Maven 优先 apache-maven-3.9.14
set "JAVA_HOME="
set "SINGLE_DEV_ROOT="
if defined MEDICAN_DEV_TOOLS if exist "%MEDICAN_DEV_TOOLS%\." set "SINGLE_DEV_ROOT=1"
if "%SINGLE_DEV_ROOT%"=="1" (
  for /d %%J in ("%MEDICAN_DEV_TOOLS%\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%MEDICAN_DEV_TOOLS%\jdk-*") do set "JAVA_HOME=%%~fJ"
) else (
  if defined MEDICAN_DEV_TOOLS (
    for /d %%J in ("%MEDICAN_DEV_TOOLS%\jdk-11*") do set "JAVA_HOME=%%~fJ"
    if not defined JAVA_HOME for /d %%J in ("%MEDICAN_DEV_TOOLS%\jdk-*") do set "JAVA_HOME=%%~fJ"
  )
  if not defined JAVA_HOME for /d %%J in ("%ROOT%.devtools\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%.devtools\jdk-*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%dev-tools\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%dev-tools\jdk-*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("C:\dev\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("C:\dev\jdk-*") do set "JAVA_HOME=%%~fJ"
)
rem 已设 MEDICAN_DEV_TOOLS 但该目录未解压 jdk-* 时，上面「单源」分支不会落到 C:\dev；此处统一回退
if not defined JAVA_HOME (
  for /d %%J in ("%ROOT%.devtools\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%.devtools\jdk-*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%dev-tools\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("%ROOT%dev-tools\jdk-*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("C:\dev\jdk-11*") do set "JAVA_HOME=%%~fJ"
  if not defined JAVA_HOME for /d %%J in ("C:\dev\jdk-*") do set "JAVA_HOME=%%~fJ"
)
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_HOME="
if "%SINGLE_DEV_ROOT%"=="1" (
  if exist "%MEDICAN_DEV_TOOLS%\apache-maven-3.9.14" set "MAVEN_HOME=%MEDICAN_DEV_TOOLS%\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("%MEDICAN_DEV_TOOLS%\apache-maven-*") do set "MAVEN_HOME=%%~fM"
) else (
  if defined MEDICAN_DEV_TOOLS (
    if exist "%MEDICAN_DEV_TOOLS%\apache-maven-3.9.14" set "MAVEN_HOME=%MEDICAN_DEV_TOOLS%\apache-maven-3.9.14"
    if not defined MAVEN_HOME for /d %%M in ("%MEDICAN_DEV_TOOLS%\apache-maven-*") do set "MAVEN_HOME=%%~fM"
  )
  if not defined MAVEN_HOME if exist "%ROOT%.devtools\apache-maven-3.9.14" set "MAVEN_HOME=%ROOT%.devtools\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("%ROOT%.devtools\apache-maven-*") do set "MAVEN_HOME=%%~fM"
  if not defined MAVEN_HOME if exist "%ROOT%dev-tools\apache-maven-3.9.14" set "MAVEN_HOME=%ROOT%dev-tools\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("%ROOT%dev-tools\apache-maven-*") do set "MAVEN_HOME=%%~fM"
  if not defined MAVEN_HOME if exist "C:\dev\apache-maven-3.9.14" set "MAVEN_HOME=C:\dev\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("C:\dev\apache-maven-*") do set "MAVEN_HOME=%%~fM"
)
if not defined MAVEN_HOME (
  if exist "%ROOT%.devtools\apache-maven-3.9.14" set "MAVEN_HOME=%ROOT%.devtools\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("%ROOT%.devtools\apache-maven-*") do set "MAVEN_HOME=%%~fM"
  if not defined MAVEN_HOME if exist "%ROOT%dev-tools\apache-maven-3.9.14" set "MAVEN_HOME=%ROOT%dev-tools\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("%ROOT%dev-tools\apache-maven-*") do set "MAVEN_HOME=%%~fM"
  if not defined MAVEN_HOME if exist "C:\dev\apache-maven-3.9.14" set "MAVEN_HOME=C:\dev\apache-maven-3.9.14"
  if not defined MAVEN_HOME for /d %%M in ("C:\dev\apache-maven-*") do set "MAVEN_HOME=%%~fM"
)
if defined MAVEN_HOME set "PATH=%MAVEN_HOME%\bin;%PATH%"

echo.
echo [环境] JAVA_HOME=%JAVA_HOME%
where java >nul 2>&1
if errorlevel 1 (
  echo [错误] 未在 PATH 中找到 java.exe。请安装 JDK 11，或将解压后的 jdk-11* 放到 MEDICAN_DEV_TOOLS、仓库 .devtools、dev-tools 或 C:\dev。
  pause
  exit /b 1
)
java -version

rem 与《大模型调用调试说明》内网网关一致；本机 Ollama 请自行 set LLM_URL=http://127.0.0.1:11434/v1/chat/completions
if not defined LLM_URL set "LLM_URL=http://ds.local.ai:30080/v1/chat/completions"
if not defined LLM_MODEL set "LLM_MODEL=MDQWEN330BBF4AAE62D7EA"

echo.
echo [大模型] LLM_URL=%LLM_URL%
echo [大模型] LLM_MODEL=%LLM_MODEL%
rem 勿把含「set VAR=」的 echo 写在 if^(^) 块内，否则 cmd 会当成 set 命令解析并报错
if defined LLM_API_KEY goto :after_llm_key_hint
echo [大模型] 未设置 LLM_API_KEY：将使用 application.yml 默认 Key；生产环境请 set LLM_API_KEY=你的Key
echo           请确认 hosts 已解析 ds.local.ai，或改用可访问的内网 IP（见《大模型调用调试说明》）
echo.
:after_llm_key_hint

set "USE_WRAPPER=0"
where mvn >nul 2>&1
if errorlevel 1 (
  if exist "%ROOT%campus-diet-backend\mvnw.cmd" (
    set "USE_WRAPPER=1"
  ) else (
    echo [错误] 未在 PATH 中找到 mvn，且缺少 campus-diet-backend\mvnw.cmd。请安装 Maven，或将 JDK/Maven 解压到 .devtools 或 dev-tools，或从仓库拉取含 Wrapper 的版本。
    pause
    exit /b 1
  )
)

rem 每次启动前先结束仍占用 11888 的旧实例（Spring Boot 监听进程）
rem PowerShell 一行内勿用 cmd 会配对的英文圆括号，否则易把端口号等误当成命令
echo [清理] 结束占用端口 11888 的旧进程，如有则结束监听进程...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetTCPConnection -LocalPort 11888 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
timeout /t 1 /nobreak >nul

echo [启动] mvn spring-boot:run，端口 11888；后端默认监听 127.0.0.1（局域网直连 API 请先 set SERVER_ADDRESS=0.0.0.0）
echo.
if "%USE_WRAPPER%"=="1" (
  if exist "%ROOT%maven-settings-d.xml" (
    call mvnw.cmd -s "%ROOT%maven-settings-d.xml" spring-boot:run
  ) else (
    call mvnw.cmd spring-boot:run
  )
) else (
  if exist "%ROOT%maven-settings-d.xml" (
    mvn -s "%ROOT%maven-settings-d.xml" spring-boot:run
  ) else (
    mvn spring-boot:run
  )
)
set "MVN_EXIT=%ERRORLEVEL%"
if not "%MVN_EXIT%"=="0" (
  echo.
  echo [错误] spring-boot:run 失败，退出码 %MVN_EXIT%。请向上滚动查看 Maven/Spring 报错。
  echo 常见原因：MySQL 未启动或库名/账号密码不对、11888 端口被占用、JDK 非 11、首次运行需联网下载依赖。
  pause
)
exit /b %MVN_EXIT%
