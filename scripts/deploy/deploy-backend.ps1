param(
  [string]$HostName = "47.101.156.6",
  [string]$User = "root",
  [Parameter(Mandatory = $true)]
  [string]$SshKeyPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

function Resolve-ProjectRoot {
  $current = Resolve-Path $PSScriptRoot
  while ($null -ne $current) {
    if ((Test-Path (Join-Path $current "backend\pom.xml")) -and (Test-Path (Join-Path $current "package.json"))) {
      return $current
    }
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
$jar = Join-Path $root "backend\target\backend-0.0.1-SNAPSHOT.jar"
if (!(Test-Path $jar)) {
  throw "Backend jar not found: $jar. Run prepare-release.ps1 first."
}
if (!(Test-Path $SshKeyPath)) {
  throw "SSH key not found: $SshKeyPath"
}

$remote = "${User}@${HostName}"
$remoteJar = "/tmp/eunomia-backend-new.jar"
$remoteScript = "/tmp/eunomia-deploy-backend.sh"

$bashLines = @(
  '#!/usr/bin/env bash',
  'set -euo pipefail',
  '',
  'STAMP="$(date +%Y%m%d-%H%M%S)"',
  'ENV_FILE=/opt/scoring/backend/deploy/prod-env.sh',
  'NEW_JAR=/tmp/eunomia-backend-new.jar',
  'RELEASE_DIR="/opt/scoring/app/releases/${STAMP}"',
  '',
  'if [[ ! -f "${NEW_JAR}" ]]; then',
  '  echo "Missing ${NEW_JAR}"',
  '  exit 1',
  'fi',
  '',
  'set -a',
  '. "${ENV_FILE}"',
  'set +a',
  'UPLOAD_DIR="${UPLOAD_DIR:-/opt/scoring/uploads}"',
  'UPLOAD_PUBLIC_BASE_URL="${UPLOAD_PUBLIC_BASE_URL:-https://api.eunomia.cc}"',
  'export UPLOAD_DIR UPLOAD_PUBLIC_BASE_URL',
  '',
  'DB_NAME="$(printf ''%s'' "${DB_URL}" | sed -n ''s#.*127.0.0.1:3306/\([^?]*\).*#\1#p'')"',
  'if [[ -z "${DB_NAME}" ]]; then',
  '  echo "Cannot parse DB name from DB_URL"',
  '  exit 1',
  'fi',
  '',
  'mkdir -p /opt/scoring/backups "${RELEASE_DIR}" "${UPLOAD_DIR}/avatars"',
  'chown -R scoring:scoring "${UPLOAD_DIR}"',
  'mysqldump --no-tablespaces -u"${DB_USERNAME}" -p"${DB_PASSWORD}" "${DB_NAME}" \',
  '  | gzip > "/opt/scoring/backups/${DB_NAME}-${STAMP}.sql.gz"',
  '',
  'cp "${NEW_JAR}" "${RELEASE_DIR}/backend.jar"',
  'chown -R scoring:scoring "${RELEASE_DIR}"',
  'ln -sfn "${RELEASE_DIR}" /opt/scoring/app/current',
  '',
  'systemctl restart scoring-backend',
  'sleep 25',
  '',
  'systemctl is-active scoring-backend',
  'curl -fsS --max-time 10 https://api.eunomia.cc/api/v1/tournaments >/tmp/eunomia-api-check.json',
  'curl -sS --max-time 10 -X POST -F "file=@/etc/hosts;type=image/png" https://api.eunomia.cc/api/v1/files/avatars | grep ''"code":401'' >/tmp/eunomia-upload-endpoint-check.txt',
  'nginx -t',
  'certbot certificates -d api.eunomia.cc >/tmp/eunomia-cert-check.txt',
  '',
  'echo "Release: ${RELEASE_DIR}"',
  'echo "Backup: /opt/scoring/backups/${DB_NAME}-${STAMP}.sql.gz"',
  'rm -f "${NEW_JAR}" /tmp/eunomia-deploy-backend.sh'
)
$bash = $bashLines -join "`n"

$tempScript = Join-Path $env:TEMP "eunomia-deploy-backend.sh"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($tempScript, $bash, $utf8NoBom)

try {
  Write-Host "Uploading backend jar..."
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $jar, "${remote}:${remoteJar}")

  Write-Host "Uploading deploy script..."
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $tempScript, "${remote}:${remoteScript}")

  Write-Host "Running remote deployment..."
  Invoke-Checked "ssh" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $remote, "bash ${remoteScript}")
} finally {
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
}

Write-Host "OK: backend deployed and verified."
