Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-ProjectRoot {
  $current = Resolve-Path $PSScriptRoot
  while ($null -ne $current) {
    if (
      (Test-Path (Join-Path $current "backend\pom.xml")) -and
      (Test-Path (Join-Path $current "package.json")) -and
      (Test-Path (Join-Path $current "src\utils\request.js"))
    ) { return $current }
    $parent = Split-Path $current -Parent
    if ([string]::IsNullOrWhiteSpace($parent) -or $parent -eq $current) { break }
    $current = $parent
  }
  throw "Cannot locate project root from $PSScriptRoot"
}

function Invoke-Checked {
  param(
    [Parameter(Mandatory = $true)]
    [string]$FilePath,
    [string[]]$Arguments = @()
  )
  & $FilePath @Arguments
  if ($LASTEXITCODE -ne 0) {
    throw "Command failed with exit code ${LASTEXITCODE}: $FilePath $($Arguments -join ' ')"
  }
}

$root = Resolve-ProjectRoot
Set-Location $root

Write-Host "== Eunomia release preparation =="
Write-Host "Root: $root"

Write-Host "`n== Git status =="
Invoke-Checked "git" @("status", "--short")

Write-Host "`n== Environment check =="
$envLocal = Join-Path $root ".env.local"
if (!(Test-Path $envLocal)) { throw ".env.local not found" }
$envText = Get-Content $envLocal -Raw
if ($envText -notmatch "VITE_API_BASE_URL=https://api\.eunomia\.cc") {
  throw ".env.local must contain VITE_API_BASE_URL=https://api.eunomia.cc"
}

$requestJs = Join-Path $root "src\utils\request.js"
$requestText = Get-Content $requestJs -Raw
if ($requestText -notmatch "import\.meta\.env\.DEV") {
  throw "src/utils/request.js must use import.meta.env.DEV to separate dev/prod API base URLs"
}

Write-Host "`n== Backend tests =="
Push-Location (Join-Path $root "backend")
try {
  Invoke-Checked "mvn" @("test")
  Write-Host "`n== Backend package =="
  Invoke-Checked "mvn" @("package", "-DskipTests")
} finally {
  Pop-Location
}

Write-Host "`n== Mini program build =="
Invoke-Checked "npm.cmd" @("run", "build:mp-weixin")

Write-Host "`n== Built domain check =="
$dist = Join-Path $root "dist\build\mp-weixin"
if (!(Test-Path $dist)) { throw "dist/build/mp-weixin not found" }

$builtText = & rg "https://api\.eunomia\.cc|http://47\.101\.156\.6|127\.0\.0\.1|10\.4\.117\.181" $dist
if ($LASTEXITCODE -gt 1) { throw "rg failed while checking built mini program output" }
Write-Host $builtText

if ($builtText -match "http://47\.101\.156\.6|127\.0\.0\.1|10\.4\.117\.181") {
  throw "Built mini program contains a dev or IP API host"
}
if ($builtText -notmatch "https://api\.eunomia\.cc") {
  throw "Built mini program does not contain https://api.eunomia.cc"
}

Write-Host "`nOK: release artifacts are ready."
Write-Host "Backend jar: backend\target\backend-0.0.1-SNAPSHOT.jar"
Write-Host "Mini program: dist\build\mp-weixin"