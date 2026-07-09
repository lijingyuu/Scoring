param(
  [string]$HostName = "47.101.156.6",
  [string]$User = "root",
  [Parameter(Mandatory = $true)]
  [string]$SshKeyPath,
  [Parameter(Mandatory = $true)]
  [string]$KeyComment
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

if (!(Test-Path $SshKeyPath)) { throw "SSH key not found: $SshKeyPath" }
if ($KeyComment -notmatch "^[A-Za-z0-9._@+-]+$") { throw "Unsafe SSH key comment: $KeyComment" }

$remote = "${User}@${HostName}"

Write-Host "Removing remote authorized key..."
& ssh -i $SshKeyPath -o StrictHostKeyChecking=accept-new -o BatchMode=yes $remote "sed -i.bak '/${KeyComment}/d' ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys; grep -q '${KeyComment}' ~/.ssh/authorized_keys && echo still-present || echo removed"
if ($LASTEXITCODE -ne 0) { throw "Failed to remove remote authorized key" }

Write-Host "Verifying key can no longer log in..."
& ssh -i $SshKeyPath -o StrictHostKeyChecking=accept-new -o BatchMode=yes $remote "echo should-not-login"
if ($LASTEXITCODE -eq 0) { throw "Temporary key can still log in; do not delete local key yet" }

Write-Host "Deleting local temporary key files..."
Remove-Item -LiteralPath $SshKeyPath -Force
$publicKeyPath = "$SshKeyPath.pub"
if (Test-Path $publicKeyPath) { Remove-Item -LiteralPath $publicKeyPath -Force }

Write-Host "OK: temporary SSH key removed locally and remotely."