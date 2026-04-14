@echo off
chcp 65001 >nul 2>&1
set "ROOT=%~dp0"
set "INI=%ROOT%mysql-dev-my.ini"
set "MYSQLD=C:\Program Files\MySQL\MySQL Server 8.4\bin\mysqld.exe"
if not exist "%MYSQLD%" (
  echo [错误] 未找到 "%MYSQLD%"，请用 winget 安装 Oracle.MySQL 或修改本脚本中的 MYSQLD。
  pause
  exit /b 1
)
if not exist "%INI%" (
  echo [错误] 未找到 "%INI%"
  pause
  exit /b 1
)
echo [MySQL] 配置: %INI%
echo [MySQL] 数据目录: %ROOT%.devtools\mysql-data
echo [MySQL] 若 3306 已被占用，请先结束已有 mysqld 进程。
start "mysqld-dev" /MIN "%MYSQLD%" --defaults-file="%INI%"
echo [MySQL] 已尝试启动 mysqld。首次使用需先初始化数据目录，见 README。
exit /b 0
