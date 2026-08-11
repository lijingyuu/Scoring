---
name: eunomia-product-web-release
description: Deploy and update the Eunomia public product introduction static website at product.eunomia.cc. Use when the user wants to publish, update, verify, roll back, or troubleshoot the product guide website under docs/product-guide.
---

# Eunomia Product Web Release

## Fixed Context

- Project root: `D:\LJY\grade2\Scoring`
- Product site source: `docs/product-guide/`
- Production site: `https://product.eunomia.cc`
- Server public IP: `47.101.156.6`
- Web release root: `/opt/scoring/web/product/releases`
- Web current symlink: `/opt/scoring/web/product/current`
- nginx config: `/etc/nginx/sites-available/eunomia-product-web`
- HTTPS certificate: `/etc/letsencrypt/live/product.eunomia.cc/fullchain.pem`
- Current first release observed: `/opt/scoring/web/product/releases/20260811-172314`

The site is pure static HTML/CSS/images. It does not need a frontend build,
backend restart, database backup, or `/api/` reverse proxy.

Never write database passwords, WeChat AppSecret, JWT secret, or SSH private
keys into this skill or repo files.

## Quick Start

1. Read [REFERENCE.md](REFERENCE.md) before acting.
2. Confirm the change scope is only `docs/product-guide/` static content.
3. Verify DNS before deployment:

```powershell
Resolve-DnsName product.eunomia.cc -Type A
```

Expected IP: `47.101.156.6`.

4. Generate the temporary SSH key with the existing eunomia-release script:

```powershell
powershell -ExecutionPolicy Bypass -File <repo>\.agents\skills\eunomia-release\scripts\new-temp-ssh-key.ps1
```

5. Ask the user to run the printed `authorized_keys` command on the server as
   `root`. Then test SSH with the printed `KEY_PATH`:

```powershell
ssh -i <KEY_PATH> -o StrictHostKeyChecking=accept-new -o BatchMode=yes root@47.101.156.6 "echo eunomia-ssh-ok"
```

6. Deploy after SSH succeeds:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\deploy-product-web.ps1 -SshKeyPath <KEY_PATH>
```

7. Always remove the temporary SSH key after deployment, failure, or abort:

```powershell
powershell -ExecutionPolicy Bypass -File <repo>\.agents\skills\eunomia-release\scripts\remove-temp-ssh-key.ps1 -SshKeyPath <KEY_PATH> -KeyComment <COMMENT>
```

## Guardrails

- Do not touch `scoring-backend`, `/opt/scoring/app`, MySQL, or backend env files.
- Do not modify the existing `www.eunomia.cc` admin-web nginx config.
- Do not add an `/api/` proxy unless the product site code actually starts calling APIs.
- Treat missing `docs/product-guide/index.html` as a hard stop.
- Use the existing temporary key scripts; do not hand-write `ssh-keygen`.
- After deployment, verify both HTTPS `200` and HTTP to HTTPS `301`.
- Clean local and remote temporary files after each run.

## Common Checks

- `https://product.eunomia.cc/` returns `200 OK`.
- `http://product.eunomia.cc/` redirects to `https://product.eunomia.cc/`.
- nginx config test succeeds with `nginx -t`.
- The live symlink points to the latest release under `/opt/scoring/web/product/releases`.
