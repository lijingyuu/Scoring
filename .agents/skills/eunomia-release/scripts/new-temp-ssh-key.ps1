param(
  [string]$UserProfilePath = $env:USERPROFILE
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($UserProfilePath)) {
  throw "USERPROFILE is empty; cannot choose a stable .ssh directory"
}

$stamp = Get-Date -Format "yyyyMMdd-HHmmss"
$sshDir = Join-Path $UserProfilePath ".ssh"
$keyPath = Join-Path $sshDir "eunomia_release_$stamp"
$comment = "codex-eunomia-release-$stamp"

New-Item -ItemType Directory -Force -Path $sshDir | Out-Null
if (Test-Path $keyPath -PathType Leaf) { throw "SSH key already exists: $keyPath" }

& ssh-keygen -t ed25519 -C $comment -f $keyPath -N '""'
if ($LASTEXITCODE -ne 0) { throw "ssh-keygen failed with exit code $LASTEXITCODE" }

$publicKey = (Get-Content "$keyPath.pub" -Raw).Trim()

Write-Host "KEY_PATH=$keyPath"
Write-Host "COMMENT=$comment"
Write-Host ""
Write-Host "Run this on the server as root:"
Write-Host "mkdir -p ~/.ssh && chmod 700 ~/.ssh && printf '%s\n' '$publicKey' >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"