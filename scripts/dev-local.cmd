@echo off
setlocal
title dev-local launcher

REM ============================================================
REM Local dev launcher: backend + mp-weixin frontend watch build
REM (keep this file ASCII-only: cmd.exe parses batch files in the
REM  ANSI codepage, UTF-8 Chinese text here breaks line parsing)
REM
REM Usage:
REM   scripts\dev-local.cmd          backend: dev profile + in-memory H2
REM                                  (no MySQL/secret needed; data resets on
REM                                   restart; wechat login uses dev mock)
REM   scripts\dev-local.cmd mysql    backend: real MySQL config from
REM                                   backend\local-env.ps1 + mock wechat
REM                                   login (no IP whitelist needed)
REM   scripts\dev-local.cmd mysql wx backend: real MySQL + real wechat login
REM                                   (public IP must be in appid whitelist)
REM
REM Each service opens in its own console window. Stop with Ctrl+C or
REM by closing the window. See scripts\dev-local.md (Chinese guide).
REM ============================================================

set "ROOT=%~dp0.."

echo [dev-local] Starting backend (one window will pop up)...

if /i "%1"=="mysql" goto backend_mysql

:backend_h2
start "scoring-backend" cmd /k "cd /d %ROOT%\backend&& set SPRING_FLYWAY_ENABLED=false&& set SPRING_SQL_INIT_MODE=always&& set SPRING_SQL_INIT_SCHEMA_LOCATIONS=classpath:schema-h2.sql&& mvn spring-boot:run -Dspring-boot.run.profiles=dev"
goto wait_backend

:backend_mysql
if /i "%2"=="wx" goto backend_mysql_wx
start "scoring-backend" cmd /k "cd /d %ROOT%\backend&& powershell -NoProfile -ExecutionPolicy Bypass -File %ROOT%\scripts\start-backend.ps1 -MockWechat"
goto wait_backend

:backend_mysql_wx
start "scoring-backend" cmd /k "cd /d %ROOT%\backend&& powershell -NoProfile -ExecutionPolicy Bypass -File start-local.ps1"
goto wait_backend

:wait_backend
REM The mp frontend preflight (prepare-mp-weixin-env.mjs) requires the
REM backend to be reachable, so wait here before starting that window.
echo [dev-local] Waiting for backend :8080 (max ~90s)...
set /a TRIES=0
:wait_loop
set /a TRIES+=1
curl -s -o nul -m 2 http://127.0.0.1:8080/api/v1/tournaments && goto backend_ready
if %TRIES% GEQ 45 (
  echo [dev-local] WARNING: backend not ready after ~90s. Starting frontend anyway;
  echo [dev-local] if the mp window exits with "Backend is not reachable", rerun this script.
  goto frontend
)
timeout /t 2 /nobreak >nul
goto wait_loop

:backend_ready
echo [dev-local] Backend is ready.

:frontend
REM dev:mp-weixin:local auto-detects the LAN IP and writes .env.local.
REM The mp API base is baked at build time: rerun this script after
REM changing Wi-Fi/network.
start "scoring-mp-weixin" cmd /k "cd /d %ROOT%\frontend&& npm run dev:mp-weixin:local"

echo.
echo ===== Started =====
echo   [win1] scoring-backend    backend :8080
echo   [win2] scoring-mp-weixin  mp watch build - frontend\dist\dev\mp-weixin
echo.
echo ===== WeChat DevTools =====
echo   1. Import project dir: frontend\dist\dev\mp-weixin
echo   2. Details - Local Settings - check
echo      "Do not verify legal domains / TLS / HTTPS certificates"
echo      (required, otherwise http://LAN-IP:8080 requests are blocked)
echo   3. Wait for the first watch build to finish, then hit Compile.
echo      Frontend code changes hot-rebuild; press Compile to refresh.
echo.
echo ===== Notes =====
echo   - H2 mode (default): no secrets needed; complete profile on first
echo     login; backend restart wipes data
echo   - mysql mode: local-env.ps1 real MySQL, data persists; wechat login
echo     is mocked by default (no IP whitelist needed). Pass "wx" as the
echo     second argument for real wechat login (public IP must be whitelisted
echo     in the mp.weixin.qq.com console, e.g. "dev-local.cmd mysql wx")
echo   - After changing network (IP change): close the scoring-mp-weixin
echo     window and rerun this script
echo.
pause
