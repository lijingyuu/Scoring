param(
  [string]$HostName = "47.101.156.6",
  [string]$User = "root",
  [string]$Domain = "product.eunomia.cc",
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
$source = Join-Path $root "docs\product-guide"
if (!(Test-Path (Join-Path $source "index.html"))) {
  throw "Product guide index not found: $source"
}
if (!(Test-Path $SshKeyPath)) {
  throw "SSH key not found: $SshKeyPath"
}

$remote = "${User}@${HostName}"
$remoteDist = "/tmp/eunomia-product-web-dist"
$remoteScript = "/tmp/eunomia-deploy-product-web.sh"
$nginxName = "eunomia-product-web"
$releaseRoot = "/opt/scoring/web/product"

$bashLines = @(
  '#!/usr/bin/env bash',
  'set -euo pipefail',
  '',
  'STAMP="$(date +%Y%m%d-%H%M%S)"',
  "DOMAIN=`"${Domain}`"",
  "SERVER_IP=`"${HostName}`"",
  "UPLOAD_DIR=`"${remoteDist}`"",
  "RELEASE_DIR=`"${releaseRoot}/releases/`${STAMP}`"",
  "CURRENT_LINK=`"${releaseRoot}/current`"",
  "NGINX_CONF=`"/etc/nginx/sites-available/${nginxName}`"",
  '',
  'if [[ ! -f "${UPLOAD_DIR}/index.html" ]]; then',
  '  echo "Missing uploaded product page at ${UPLOAD_DIR}"',
  '  exit 1',
  'fi',
  '',
  'WEB_IP="$(getent hosts "${DOMAIN}" | awk ''{print $1}'' | head -n1)"',
  'if [[ "${WEB_IP}" != "${SERVER_IP}" ]]; then',
  '  echo "DNS for ${DOMAIN} not ready (resolved: ${WEB_IP})."',
  '  echo "Expected A record: product -> ${SERVER_IP}"',
  '  exit 1',
  'fi',
  '',
  'mkdir -p "${RELEASE_DIR}"',
  'cp -a "${UPLOAD_DIR}/." "${RELEASE_DIR}/"',
  'chmod -R a+rX "${RELEASE_DIR}"',
  'ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"',
  '',
  'cat > "${NGINX_CONF}" <<''NGINX''',
  'server {',
  '    listen 80;',
  "    server_name ${Domain};",
  "    root ${releaseRoot}/current;",
  '    index index.html;',
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
  "ln -sfn `"`${NGINX_CONF}`" /etc/nginx/sites-enabled/${nginxName}",
  'nginx -t',
  'systemctl reload nginx',
  'curl -fsS --max-time 10 "http://${DOMAIN}/" -o /dev/null',
  'certbot --nginx -d "${DOMAIN}" --redirect --non-interactive --agree-tos',
  'curl -fsS --max-time 10 "https://${DOMAIN}/" -o /dev/null',
  '',
  'echo "Release: ${RELEASE_DIR}"',
  "rm -rf `"`${UPLOAD_DIR}`" ${remoteScript}"
)
$bash = $bashLines -join "`n"

$tempScript = Join-Path $env:TEMP "eunomia-deploy-product-web.sh"
$utf8NoBom = New-Object System.Text.UTF8Encoding($false)
[System.IO.File]::WriteAllText($tempScript, $bash, $utf8NoBom)

try {
  Write-Host "Uploading product guide..."
  Invoke-Checked "ssh" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $remote, "rm -rf ${remoteDist}")
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", "-r", $source, "${remote}:${remoteDist}")

  Write-Host "Uploading deploy script..."
  Invoke-Checked "scp" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $tempScript, "${remote}:${remoteScript}")

  Write-Host "Running remote deployment..."
  Invoke-Checked "ssh" @("-i", $SshKeyPath, "-o", "StrictHostKeyChecking=accept-new", $remote, "bash ${remoteScript}")
} finally {
  Remove-Item -LiteralPath $tempScript -Force -ErrorAction SilentlyContinue
}

Write-Host "OK: product web deployed to https://${Domain}"
