param(
  [string]$HostName = "47.101.156.6",
  [string]$User = "root",
  [string]$Domain = "www.eunomia.cc",
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
$dist = Join-Path $root "admin-web\dist"
if (!(Test-Path (Join-Path $dist "index.html"))) {
  throw "admin-web dist not found: $dist. Run 'npm.cmd --prefix admin-web run build' first."
}
if (!(Test-Path $SshKeyPath)) {
  throw "SSH key not found: $SshKeyPath"
}

$remote = "${User}@${HostName}"
$remoteDist = "/tmp/eunomia-admin-web-dist"
$remoteScript = "/tmp/eunomia-deploy-web.sh"

$bashLines = @(
  '#!/usr/bin/env bash',
  'set -euo pipefail',
  '',
  'STAMP="$(date +%Y%m%d-%H%M%S)"',
  'DOMAIN="www.eunomia.cc"',
  'SERVER_IP="47.101.156.6"',
  'RELEASE_DIR="/opt/scoring/web/admin/releases/${STAMP}"',
  'CURRENT_LINK="/opt/scoring/web/admin/current"',
  'NGINX_CONF="/etc/nginx/sites-available/eunomia-admin-web"',
  '',
  'if [[ ! -f /tmp/eunomia-admin-web-dist/index.html ]]; then',
  '  echo "Missing uploaded dist at /tmp/eunomia-admin-web-dist"',
  '  exit 1',
  'fi',
  '',
  '# DNS must point to this server before certbot can issue a certificate.',
  'WEB_IP="$(getent hosts "${DOMAIN}" | awk ''{print $1}'' | head -n1)"',
  'if [[ "${WEB_IP}" != "${SERVER_IP}" ]]; then',
  '  echo "DNS for ${DOMAIN} not ready (resolved: ''${WEB_IP}'')."',
  '  echo "Add A record: www -> ${SERVER_IP}, wait for propagation, then rerun."',
  '  exit 1',
  'fi',
  '',
  'mkdir -p "${RELEASE_DIR}"',
  'cp -a /tmp/eunomia-admin-web-dist/. "${RELEASE_DIR}/"',
  'chmod -R a+rX "${RELEASE_DIR}"',
  'ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"',
  '',
  'cat > "${NGINX_CONF}" <<''NGINX''',
  'server {',
  '    listen 80;',
  '    server_name www.eunomia.cc;',
  '    root /opt/scoring/web/admin/current;',
  '    index index.html;',
  '',
  '    location /api/ {',
  '        proxy_pass http://127.0.0.1:8080;',
  '        proxy_set_header Host $host;',
  '        proxy_set_header X-Real-IP $remote_addr;',
  '        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;',
  '        proxy_set_header X-Forwarded-Proto $scheme;',
  '    }',
  '',
  '    location /assets/ {',
  '        expires 7d;',
  '        add_header Cache-Control "public";',
  '        try_files $uri =404;',
  '    }',
  '',
  '    location / {',
  '        try_files $uri $uri/ /index.html;',
  '    }',
  '}',
  'NGINX',
  '',
  'ln -sfn "${NGINX_CONF}" /etc/nginx/sites-enabled/eunomia-admin-web',
  'nginx -t',
  'systemctl reload nginx',
  '',
  'curl -fsS --max-time 10 "http://${DOMAIN}/" -o /dev/null',
  '',
  '# Issue HTTPS certificate (account already exists from api.eunomia.cc).',
  'certbot --nginx -d "${DOMAIN}" --redirect --non-interactive --agree-tos',
  '',
  'curl -fsS --max-time 10 "https://${DOMAIN}/" -o /dev/null',
  'curl -fsS --max-time 10 "https://${DOMAIN}/api/v1/tournaments" -o /tmp/eunomia-web-api-check.json',
  '',
  'echo "Release: ${RELEASE_DIR}"',
  'rm -rf /tmp/eunomia-admin-web-dist /tmp/eunomia-deploy-web.sh'
)
$bash = $bashLines -join "`n"

$tempScript = Join-Path $env:TEMP "eunomia-deploy-web.sh"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($tempScript, $bash, $utf8NoBom)

try {
  Write-Host "Uploading admin-web dist..."
  Invoke-Checked "ssh" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $remote, "rm -rf ${remoteDist}")
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", "-r", $dist, "${remote}:${remoteDist}")

  Write-Host "Uploading deploy script..."
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $tempScript, "${remote}:${remoteScript}")

  Write-Host "Running remote deployment..."
  Invoke-Checked "ssh" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $remote, "bash ${remoteScript}")
} finally {
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
}

Write-Host "OK: admin-web deployed to https://${Domain}"
