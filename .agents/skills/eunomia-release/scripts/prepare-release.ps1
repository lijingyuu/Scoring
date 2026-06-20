Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")
Set-Location $root

Write-Host "== Eunomia release preparation =="
Write-Host "Root: $root"

Write-Host "`n== Git status =="
git status --short

Write-Host "`n== Environment check =="
$envLocal = Join-Path $root ".env.local"
if (!(Test-Path $envLocal)) {
  throw ".env.local not found"
}
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
mvn test

Write-Host "`n== Backend package =="
mvn package -DskipTests
Pop-Location

Write-Host "`n== Mini program build =="
npm.cmd run build:mp-weixin

Write-Host "`n== Built domain check =="
$dist = Join-Path $root "dist\build\mp-weixin"
if (!(Test-Path $dist)) {
  throw "dist/build/mp-weixin not found"
}

$builtText = & rg "https://api\.eunomia\.cc|http://47\.101\.156\.6|127\.0\.0\.1|10\.4\.117\.181" $dist
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

