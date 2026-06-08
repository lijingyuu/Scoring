param(
  [switch]$ValidateOnly
)

$ErrorActionPreference = 'Stop'

$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$configPath = Join-Path $scriptDir 'local-env.ps1'
$repoDir = Split-Path $scriptDir -Parent
$mvnPath = Join-Path $repoDir '.tools\apache-maven-3.9.9\bin\mvn.cmd'

if (-not (Test-Path $configPath)) {
  Write-Host ''
  Write-Host 'Missing local config: backend/local-env.ps1' -ForegroundColor Yellow
  Write-Host 'Copy backend/local-env.example.ps1 to backend/local-env.ps1, then fill in your real secrets.' -ForegroundColor Yellow
  Write-Host ''
  exit 1
}

. $configPath

if ([string]::IsNullOrWhiteSpace([Environment]::GetEnvironmentVariable('SPRING_FLYWAY_BASELINE_ON_MIGRATE', 'Process'))) {
  $env:SPRING_FLYWAY_BASELINE_ON_MIGRATE = 'true'
}

$requiredVars = @(
  'DB_URL',
  'DB_USERNAME',
  'DB_PASSWORD',
  'JWT_SECRET',
  'WECHAT_APP_ID',
  'WECHAT_APP_SECRET'
)

$missing = @()
foreach ($name in $requiredVars) {
  $value = [Environment]::GetEnvironmentVariable($name, 'Process')
  if ([string]::IsNullOrWhiteSpace($value)) {
    $missing += $name
  }
}

if ($missing.Count -gt 0) {
  Write-Host ''
  Write-Host 'These environment variables are still missing:' -ForegroundColor Red
  foreach ($name in $missing) {
    Write-Host " - $name" -ForegroundColor Red
  }
  Write-Host ''
  exit 1
}

if ($ValidateOnly) {
  Write-Host 'Local test config looks good.' -ForegroundColor Green
  exit 0
}

Push-Location $scriptDir
try {
  if (Test-Path $mvnPath) {
    & $mvnPath 'spring-boot:run'
    exit $LASTEXITCODE
  }

  & mvn 'spring-boot:run'
  exit $LASTEXITCODE
} finally {
  Pop-Location
}
