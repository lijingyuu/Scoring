# Backend launcher used by scripts\dev-local.cmd (mysql modes).
# Loads backend\local-env.ps1 for real MySQL config, optionally clears
# WeChat credentials so the dev profile uses mock openid login
# (no WeChat API call -> no IP-whitelist requirement).
#
# Usage:
#   powershell -File start-backend.ps1 -MockWechat   # MySQL + mock login
#   powershell -File start-backend.ps1               # MySQL + real wechat

param(
  # Clear WeChat credentials and force the dev profile so login falls back
  # to mock openid (no WeChat API call, no IP whitelist requirement).
  [switch]$MockWechat
)

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoDir = Split-Path -Parent $scriptDir

. (Join-Path $repoDir 'backend\local-env.ps1')

if ($MockWechat) {
  $env:WECHAT_APP_ID = ''
  $env:WECHAT_APP_SECRET = ''
  # Mock openid requires the dev profile (AuthServiceImpl checks it before
  # falling back to mock); dev also enables DevMockAuthFilter (H5 only).
  $env:SPRING_PROFILES_ACTIVE = 'dev'
  # dev yml hardcodes an in-memory H2 datasource, which would override the
  # DB_URL placeholder and crash Flyway (MySQL syntax on H2). Re-pin the
  # real MySQL connection through Spring's canonical env variables.
  $env:SPRING_DATASOURCE_URL = $env:DB_URL
  $env:SPRING_DATASOURCE_USERNAME = $env:DB_USERNAME
  $env:SPRING_DATASOURCE_PASSWORD = $env:DB_PASSWORD
  $env:SPRING_DATASOURCE_DRIVERCLASSNAME = 'com.mysql.cj.jdbc.Driver'
  # Keep Flyway on: now that the datasource is pinned to real MySQL, migrations
  # target MySQL (not H2) and stay in sync with the repo. Disabling it made the
  # local schema silently freeze (e.g. missing tournament.force_full_games ->
  # "Unknown column" 500 on /api/v1/tournaments).
  $env:SPRING_FLYWAY_ENABLED = 'true'
  Write-Host '[dev-local] WeChat credentials cleared -> dev mock openid login (no IP whitelist needed)'
}

Push-Location (Join-Path $repoDir 'backend')
try {
  mvn spring-boot:run
} finally {
  Pop-Location
}
