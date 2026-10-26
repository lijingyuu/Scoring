@echo off
setlocal
chcp 65001 >nul
title dev-local launcher

REM ============================================================
REM 本地联调一键启动：后端 + 小程序前端 watch 构建
REM
REM 用法：
REM   scripts\dev-local.cmd          后端用 dev profile + H2 内存库
REM                                  （无需 MySQL/密钥，重启数据清零，
REM                                   微信登录走 dev mock openid）
REM   scripts\dev-local.cmd mysql    后端改用 backend\local-env.ps1
REM                                  的真实 MySQL + 微信 AppID 配置
REM
REM 两个服务各占一个自动弹出的终端窗口，Ctrl+C 或直接关窗即停止。
REM ============================================================

set "ROOT=%~dp0.."

echo [dev-local] 启动后端与小程序前端（两个窗口将自动弹出）...

if /i "%1"=="mysql" goto backend_mysql

:backend_h2
start "scoring-backend" cmd /k "cd /d %ROOT%\backend&& set SPRING_FLYWAY_ENABLED=false&& set SPRING_SQL_INIT_MODE=always&& set SPRING_SQL_INIT_SCHEMA_LOCATIONS=classpath:schema-h2.sql&& mvn spring-boot:run -Dspring-boot.run.profiles=dev"
goto frontend

:backend_mysql
start "scoring-backend" cmd /k "cd /d %ROOT%\backend&& powershell -NoProfile -ExecutionPolicy Bypass -File start-local.ps1"
goto frontend

:frontend
REM dev:mp-weixin:local 会自动探测本机局域网 IP 写入 .env.local，
REM 小程序端 API 地址在构建时固化——换 WiFi 后需关窗重跑本脚本
start "scoring-mp-weixin" cmd /k "cd /d %ROOT%\frontend&& npm run dev:mp-weixin:local"

echo.
echo ===== 已启动 =====
echo   [窗口1] scoring-backend    后端 :8080（就绪需约 20~40 秒）
echo   [窗口2] scoring-mp-weixin  小程序 watch 构建（产出 frontend\dist\dev\mp-weixin）
echo.
echo ===== 微信开发者工具操作 =====
echo   1. 导入项目：目录选 frontend\dist\dev\mp-weixin，AppID 用项目 AppID 或测试号
echo   2. 右上角「详情」-「本地设置」- 勾选
echo      【不校验合法域名、web-view（业务域名）、TLS 版本以及 HTTPS 证书】
echo      （必须勾，否则 http://局域网IP:8080 的请求会被拦）
echo   3. 首次等 watch 构建产出 dist 目录后再导入/编译；改前端代码自动热重编译，
echo      开发者工具里点「编译」刷新即可
echo.
echo ===== 注意 =====
echo   - H2 模式（默认）：无需任何密钥；微信登录走 dev mock，首次登录需在
echo     小程序内补全资料；重启后端 = 数据清零
echo   - mysql 模式：使用 local-env.ps1 的真实配置，数据持久
echo   - 电脑换网络（IP 变了）后：关掉 scoring-mp-weixin 窗口，重跑本脚本
echo.
pause
