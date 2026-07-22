---
name: eunomia-web-release
description: Deploy and update the Eunomia admin-web (Vue static site) at https://www.eunomia.cc on the production server. Use when the user wants to update or deploy the admin web, the www.eunomia.cc site, rebuild admin-web, or verify/rollback the web deployment.
---

# Eunomia Web Release

## Fixed Context

- Project root: `D:\LJY\grade2\Scoring`
- Web app source: `admin-web/` (Vue 3 + Vite, vue-router history mode)
- Build output: `admin-web/dist`
- Production site: `https://www.eunomia.cc`
- Server public IP: `47.101.156.6`
- Web release root: `/opt/scoring/web/admin/releases`
- Web current symlink: `/opt/scoring/web/admin/current`
- nginx config: `/etc/nginx/sites-available/eunomia-admin-web` (enabled via `sites-enabled` symlink)
- HTTPS certificate: `/etc/letsencrypt/live/www.eunomia.cc/fullchain.pem` (certbot auto-renews)
- Backend on the same host: `127.0.0.1:8080`, systemd service `scoring-backend`

The site is pure static files. nginx serves them and reverse-proxies `/api/`
to the local backend, so the web app calls the API same-origin and no CORS
or backend change is ever needed for web-only updates.

Never write database passwords, WeChat AppSecret, JWT secret, or SSH private
keys into this skill or repo files.

## Quick Start

1. Read [REFERENCE.md](REFERENCE.md) before acting.
2. Build locally from the project root:

```powershell
npm.cmd --prefix admin-web run build
```

3. Generate the temporary SSH key with the eunomia-release script:

```powershell
powershell -ExecutionPolicy Bypass -File <repo>\.agents\skills\eunomia-release\scripts\new-temp-ssh-key.ps1
```

4. Ask the user to run the printed `authorized_keys` command on the server as `root`. Then test SSH with the printed `KEY_PATH`:

```powershell
ssh -i <KEY_PATH> -o StrictHostKeyChecking=accept-new -o BatchMode=yes root@47.101.156.6 "echo eunomia-ssh-ok"
```

5. Deploy only after the SSH test succeeds:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\deploy-web.ps1 -SshKeyPath <KEY_PATH>
```

6. Always remove the temporary SSH key after deployment or after aborting:

```powershell
powershell -ExecutionPolicy Bypass -File <repo>\.agents\skills\eunomia-release\scripts\remove-temp-ssh-key.ps1 -SshKeyPath <KEY_PATH> -KeyComment <COMMENT>
```

## Guardrails

- Use `npm.cmd`, never `npm` (PowerShell execution policy blocks `npm.ps1`).
- Treat any failed native command as a failed build; do not upload stale `admin-web/dist`.
- Web-only updates must NOT touch the backend: no jar upload, no `systemctl restart scoring-backend`, no database backup.
- Do not run certbot again on routine updates; the certificate already exists and auto-renews. certbot runs only inside first-time setup or when the domain changes.
- After temporary SSH deployment, run `remove-temp-ssh-key.ps1` and verify the key can no longer log in.
- Do not hand-write `ssh-keygen`; use the eunomia-release `new-temp-ssh-key.ps1` script.

## Common Checks

- `https://www.eunomia.cc/` returns HTTP 200.
- `https://www.eunomia.cc/api/v1/tournaments` returns JSON with `code: 0`.
- `https://api.eunomia.cc/api/v1/tournaments` still returns `code: 0` (backend untouched).
- Rollback and troubleshooting: see [REFERENCE.md](REFERENCE.md).
