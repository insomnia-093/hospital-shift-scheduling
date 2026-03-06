@echo off
setlocal enabledelayedexpansion

REM Usage: start-backend.bat [--dry-run]
set ROOT=%~dp0

if exist "%ROOT%\.env" (
  for /f "usebackq tokens=1,* delims==" %%A in ("%ROOT%\.env") do (
    set "k=%%A"
    set "v=%%B"
    if not "!k!"=="" (
      if not "!k:~0,1!"=="#" (
        set "!k!=!v!"
      )
    )
  )
)

if "%1"=="--dry-run" (
  echo [DRY] Backend: cd /d "%ROOT%" ^&^& mvn -q -DskipTests spring-boot:run
  echo [DRY] COZE_API_URL=%COZE_API_URL%
  echo [DRY] COZE_API_KEY defined=%COZE_API_KEY%
  echo [DRY] COZE_WORKFLOW_ID=%COZE_WORKFLOW_ID%
  exit /b 0
)

echo [INFO] Starting backend...
echo [INFO] Backend: http://localhost:9090
echo [INFO] COZE_API_URL=%COZE_API_URL%
echo [INFO] COZE_WORKFLOW_ID=%COZE_WORKFLOW_ID%

echo [INFO] If Maven fails, ensure JAVA_HOME and Maven are configured.
cd /d "%ROOT%"
set MAVEN_OPTS=-Dfile.encoding=UTF-8
mvn -q -DskipTests spring-boot:run

exit /b 0
