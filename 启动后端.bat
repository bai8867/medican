@echo off
chcp 65001 >nul 2>&1
set "ROOT=%~dp0"
cd /d "%ROOT%campus-diet-backend"

rem 若已将 JDK / Maven 解压到仓库根目录 .devtools（D 盘工程路径下），优先加入 PATH
set "JAVA_HOME="
for /d %%J in ("%ROOT%.devtools\jdk-*") do set "JAVA_HOME=%%~fJ"
if defined JAVA_HOME set "PATH=%JAVA_HOME%\bin;%PATH%"
set "MAVEN_HOME="
for /d %%M in ("%ROOT%.devtools\apache-maven-*") do set "MAVEN_HOME=%%~fM"
if defined MAVEN_HOME set "PATH=%MAVEN_HOME%\bin;%PATH%"

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

where mvn >nul 2>&1
if errorlevel 1 (
  echo [错误] 未在 PATH 中找到 mvn，请安装 Maven 并配置环境变量后重试。
  pause
  exit /b 1
)

rem 每次启动前先结束仍占用 11888 的旧实例（Spring Boot 监听进程）
rem PowerShell 一行内勿用 cmd 会配对的英文圆括号，否则易把端口号等误当成命令
echo [清理] 结束占用端口 11888 的旧进程，如有则结束监听进程...
powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetTCPConnection -LocalPort 11888 -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"
timeout /t 1 /nobreak >nul

echo [启动] mvn spring-boot:run，端口 11888，监听 0.0.0.0
echo.
if exist "%ROOT%maven-settings-d.xml" (
  mvn -s "%ROOT%maven-settings-d.xml" spring-boot:run
) else (
  mvn spring-boot:run
)
exit /b %ERRORLEVEL%
