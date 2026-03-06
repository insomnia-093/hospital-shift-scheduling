@echo off
setlocal ENABLEDELAYEDEXPANSION
cd /d %~dp0

if not exist ".env" (
  echo [ERROR] .env not found in project root.
  pause
  exit /b 1
)

for /f "usebackq tokens=1,* delims==" %%A in (".env") do (
  set "k=%%A"
  set "v=%%B"
  if not "!k!"=="" (
    if not "!k:~0,1!"=="#" (
      set "!k!=!v!"
    )
  )
)

echo [INFO] COZE_API_URL=%COZE_API_URL%
echo [INFO] COZE_WORKFLOW_ID=%COZE_WORKFLOW_ID%

python --version >nul 2>nul
if errorlevel 1 (
  echo [ERROR] Python not found in PATH.
  pause
  exit /b 1
)

if not exist "coze_api_server.py" (
  echo [ERROR] coze_api_server.py not found.
  pause
  exit /b 1
)

echo [INFO] Starting Coze proxy on %COZE_API_URL% ...
python coze_api_server.py
