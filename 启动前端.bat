@echo off

setlocal EnableExtensions EnableDelayedExpansion

chcp 65001 >nul 2>&1

cd /d "%~dp0"



set "PORT=11999"

set "BACKEND_HEALTH=http://localhost:11888/health"

set "FRONTEND_DIR=%~dp0tcm-diet-frontend"

set "DEFAULT_API_BASE="

set "ENV_PS=%~dp0scripts\update-frontend-env-development.ps1"



goto :main



:echo_green

powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host ('%~1') -ForegroundColor Green"

goto :eof



:echo_red

powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host ('%~1') -ForegroundColor Red"

goto :eof



:echo_yellow

powershell -NoProfile -ExecutionPolicy Bypass -Command "Write-Host ('%~1') -ForegroundColor Yellow"

goto :eof



:main

echo.

call :echo_green "[%date% %time%] 启动前端开发环境（使用当前 PATH 中的 Node / npm）..."

echo.



call :echo_yellow "[1/4] 检查端口 %PORT% 是否被占用..."

set "PORT_FREED=0"

powershell -NoProfile -ExecutionPolicy Bypass -Command "Get-NetTCPConnection -LocalPort %PORT% -State Listen -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }" 2>nul

for /f "tokens=5" %%P in ('netstat -ano 2^>nul ^| findstr /r /c:":%PORT% .*LISTENING"') do (

  if not "%%P"=="" if not "%%P"=="0" (

    call :echo_yellow "  正在结束占用端口 %PORT% 的进程 PID %%P ..."

    taskkill /F /PID %%P >nul 2>&1

    set "PORT_FREED=1"

  )

)

if "!PORT_FREED!"=="0" (

  call :echo_green "  端口 %PORT% 未被占用（或已释放）。"

) else (

  call :echo_green "  已尝试释放端口 %PORT%。"

)



if /i "%SKIP_BACKEND_CHECK%"=="1" (

  call :echo_yellow "[2/4] 已设置 SKIP_BACKEND_CHECK=1，跳过后端健康检查。"

) else (

  call :echo_yellow "[2/4] 检查后端健康接口 %BACKEND_HEALTH% ..."

  where curl >nul 2>&1

  if errorlevel 1 (

    call :echo_red "未检测到 curl，无法检查后端。可设置 SKIP_BACKEND_CHECK=1 后重试。"

    pause

    exit /b 1

  )

  curl -s -f --connect-timeout 5 "%BACKEND_HEALTH%" -o nul >nul 2>&1

  if errorlevel 1 (

    call :echo_red "后端未就绪：请先在本机另一窗口运行「启动后端.bat」，待 /health 返回 200 后再运行本脚本。"

    call :echo_yellow "若确定后端已就绪，可设置环境变量 SKIP_BACKEND_CHECK=1 跳过检查。"

    pause

    exit /b 1

  )

  call :echo_green "  后端可用（HTTP 200）。"

)



call :echo_yellow "[3/4] 配置 VITE_API_BASE_URL（留空=走 Vite 代理 /api → 127.0.0.1:11888）..."

if not defined VITE_API_BASE_URL (

  set "VITE_API_BASE_URL=%DEFAULT_API_BASE%"

  if "!VITE_API_BASE_URL!"=="" (

    call :echo_green "  未设置直连地址：将使用相对 /api → Vite 代理 → 127.0.0.1:11888"

  ) else (

    call :echo_yellow "  已使用默认值: !VITE_API_BASE_URL!"

  )

) else (

  call :echo_green "  使用已设置的环境变量: !VITE_API_BASE_URL!"

)



if not exist "%FRONTEND_DIR%\package.json" (

  call :echo_red "未找到前端目录或 package.json: \"%FRONTEND_DIR%\""

  pause

  exit /b 1

)

if not exist "%ENV_PS%" (

  call :echo_red "未找到脚本: \"%ENV_PS%\""

  pause

  exit /b 1

)

if "!VITE_API_BASE_URL!"=="" (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%ENV_PS%" -FrontendDir "%FRONTEND_DIR%"
) else (
  powershell -NoProfile -ExecutionPolicy Bypass -File "%ENV_PS%" -FrontendDir "%FRONTEND_DIR%" -ApiBase "!VITE_API_BASE_URL!"
)

if errorlevel 1 (

  call :echo_yellow "  警告: 写入 .env.development 失败，仍将仅通过当前进程环境变量传给 Vite。"

)



call :echo_yellow "[4/4] npm 依赖与开发服务器 ..."

cd /d "%FRONTEND_DIR%"

where npm >nul 2>&1

if errorlevel 1 (

  call :echo_red "未在 PATH 中找到 npm。请安装 Node.js 18+（https://nodejs.org/）。"

  pause

  exit /b 1

)

if not exist "node_modules\" (

  call :echo_yellow "  node_modules 不存在，正在执行 npm install ..."

  call npm install

  if errorlevel 1 (

    call :echo_red "npm install 失败。"

    pause

    exit /b 1

  )

  call :echo_green "  npm install 完成。"

) else (

  if not exist "node_modules\vite\package.json" (

    call :echo_yellow "  检测到关键依赖缺失，正在执行 npm install ..."

    call npm install

    if errorlevel 1 (

      call :echo_red "npm install 失败。"

      pause

      exit /b 1

    )

    call :echo_green "  npm install 完成。"

  ) else (

    call npm ls vite --depth=0 >nul 2>&1

    if errorlevel 1 (

      call :echo_yellow "  依赖可能不完整，正在执行 npm install ..."

      call npm install

      if errorlevel 1 (

        call :echo_red "npm install 失败。"

        pause

        exit /b 1

      )

      call :echo_green "  npm install 完成。"

    ) else (

      call :echo_green "  node_modules 已存在，跳过 npm install。"

    )

  )

)



echo.

call :echo_yellow "正在启动 npm run dev（端口 %PORT%）..."

call :echo_green "  本机打开: http://localhost:%PORT%   （局域网见下方 Vite Network 行）"

echo.

call npm run dev

exit /b %ERRORLEVEL%


