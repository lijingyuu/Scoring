param(
  [string]$HostName = "47.101.156.6",
  [string]$User = "root",
  [Parameter(Mandatory = $true)]
  [string]$SshKeyPath
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$root = Resolve-Path (Join-Path $PSScriptRoot "..\..\..\..")
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

$bash = @'
#!/usr/bin/env bash
set -euo pipefail

STAMP="$(date +%Y%m%d-%H%M%S)"
ENV_FILE=/opt/scoring/backend/deploy/prod-env.sh
NEW_JAR=/tmp/eunomia-backend-new.jar
RELEASE_DIR="/opt/scoring/app/releases/${STAMP}"

if [[ ! -f "${NEW_JAR}" ]]; then
  echo "Missing ${NEW_JAR}"
  exit 1
fi

set -a
. "${ENV_FILE}"
set +a

DB_NAME="$(printf '%s' "${DB_URL}" | sed -n 's#.*127.0.0.1:3306/\([^?]*\).*#\1#p')"
if [[ -z "${DB_NAME}" ]]; then
  echo "Cannot parse DB name from DB_URL"
  exit 1
fi

mkdir -p /opt/scoring/backups "${RELEASE_DIR}"
mysqldump --no-tablespaces -u"${DB_USERNAME}" -p"${DB_PASSWORD}" "${DB_NAME}" \
  | gzip > "/opt/scoring/backups/${DB_NAME}-${STAMP}.sql.gz"

cp "${NEW_JAR}" "${RELEASE_DIR}/backend.jar"
chown -R scoring:scoring "${RELEASE_DIR}"
ln -sfn "${RELEASE_DIR}" /opt/scoring/app/current

systemctl restart scoring-backend
sleep 25

systemctl is-active scoring-backend
curl -fsS --max-time 10 https://api.eunomia.cc/api/v1/tournaments >/tmp/eunomia-api-check.json
nginx -t
certbot certificates -d api.eunomia.cc >/tmp/eunomia-cert-check.txt

echo "Release: ${RELEASE_DIR}"
echo "Backup: /opt/scoring/backups/${DB_NAME}-${STAMP}.sql.gz"
rm -f "${NEW_JAR}" /tmp/eunomia-deploy-backend.sh
'@

$tempScript = Join-Path $env:TEMP "eunomia-deploy-backend.sh"
Set-Content -LiteralPath $tempScript -Value $bash -Encoding utf8

Write-Host "Uploading backend jar..."
scp -i $SshKeyPath -o StrictHostKeyChecking=accept-new $jar "${remote}:${remoteJar}"

Write-Host "Uploading deploy script..."
scp -i $SshKeyPath -o StrictHostKeyChecking=accept-new $tempScript "${remote}:${remoteScript}"

Write-Host "Running remote deployment..."
ssh -i $SshKeyPath -o StrictHostKeyChecking=accept-new $remote "bash ${remoteScript}"

Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
Write-Host "OK: backend deployed and verified."

