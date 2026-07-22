# Eunomia Web Release Runbook

Detailed operating procedure for deploying and updating the Eunomia admin-web
static site at `https://www.eunomia.cc`.

## How The Site Works

- `admin-web/` is a standalone Vue 3 + Vite app (login, tournament lobby,
  create tournament). It is NOT the uni-app H5 build.
- All API calls use the relative prefix `/api/v1` (`admin-web/src/services/api.js`).
- vue-router uses history mode, so nginx must fall back unknown paths to
  `index.html`.
- nginx serves `https://www.eunomia.cc` from `/opt/scoring/web/admin/current`
  and proxies `/api/` to the backend at `http://127.0.0.1:8080`. Because the
  API is same-origin, browser CORS never applies.
- Releases live in `/opt/scoring/web/admin/releases/<timestamp>/`; the
  `current` symlink selects the live one, same pattern as the backend.

The nginx server block (written by the deploy script, already installed):

```nginx
server {
    listen 80;
    server_name www.eunomia.cc;
    root /opt/scoring/web/admin/current;
    index index.html;

    location /api/ {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }

    location /assets/ {
        expires 7d;
        add_header Cache-Control "public";
        try_files $uri =404;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

certbot added the HTTPS (443) listener and the 80 -> 443 redirect on top of
this block. Config path: `/etc/nginx/sites-available/eunomia-admin-web`,
enabled through `/etc/nginx/sites-enabled/eunomia-admin-web`.

## Routine Update Flow

1. Confirm the change scope is web-only. If backend API contracts changed,
   coordinate with the `eunomia-release` backend flow first.
2. Build:

```powershell
npm.cmd --prefix admin-web run build
```

   Expected output: `admin-web/dist/index.html` plus `admin-web/dist/assets/`.
   If the build fails, stop; never upload an old dist.
3. Create the temporary SSH key (eunomia-release script), ask the user to
   append the printed public key on the server as `root`, then test login.
4. Deploy:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\deploy-web.ps1 -SshKeyPath <KEY_PATH>
```

   The script uploads `admin-web/dist` to `/tmp/eunomia-admin-web-dist`,
   creates `/opt/scoring/web/admin/releases/<timestamp>/`, copies the files,
   switches the `current` symlink, rewrites the nginx config, runs `nginx -t`,
   reloads nginx, and verifies the page and the API over HTTPS.
5. Verify from the outside:

```powershell
curl.exe -sS -o NUL -w "%{http_code}" https://www.eunomia.cc/
curl.exe -sS https://www.eunomia.cc/api/v1/tournaments
```

   Expect `200` and JSON with `"code":0`. If local TLS fails inside the Codex
   sandbox, rerun with network approval or verify from the server via SSH.
6. Remove the temporary SSH key and verify login with it now fails.

## First-Time Setup (already done; kept for rebuilds)

Only needed if the server is rebuilt from scratch:

1. DNS: add an A record, host `www`, value `47.101.156.6`, at the domain
   provider. The domain is already ICP-filed, subdomains need no extra filing.
2. Ensure Aliyun security group allows inbound 80 and 443.
3. Build and run `deploy-web.ps1`; its remote script checks DNS resolution,
   installs the nginx config, then runs:

```bash
certbot --nginx -d www.eunomia.cc --redirect --non-interactive --agree-tos
```

   certbot reuses the existing Let's Encrypt account from `api.eunomia.cc`.

## Rollback

The deploy script keeps every release under
`/opt/scoring/web/admin/releases/`. To roll back:

```bash
ls -lt /opt/scoring/web/admin/releases
ln -sfn /opt/scoring/web/admin/releases/<previous-release> /opt/scoring/web/admin/current
nginx -t && systemctl reload nginx
curl -fsS https://www.eunomia.cc/ -o /dev/null && echo OK
```

No service restart is needed; nginx serves through the symlink.

## Troubleshooting

- Deploy script stops with "DNS for www.eunomia.cc not ready": the A record is
  missing or still propagating. Wait a few minutes and rerun.
- certbot fails on first setup: DNS does not resolve to this server yet, or
  port 80 is blocked by the Aliyun security group.
- Page loads but `/api/` returns 502: the backend is down. Check
  `systemctl status scoring-backend` on the server; the web deploy itself
  never touches it.
- Browser shows an old version after deploy: hashed assets are cached for 7
  days but `index.html` is not; a hard refresh (Ctrl+F5) is enough.
- `npm` blocked by PowerShell execution policy: use `npm.cmd`.
- HTTPS fails months later: check `certbot certificates` and
  `systemctl status certbot.timer` (or the certbot cron) on the server;
  renewal is automatic but can break if port 80 gets closed.

## What This Skill Must Not Do

- Do not restart, stop, or reconfigure `scoring-backend`.
- Do not modify `/opt/scoring/app` (backend releases) or any database.
- Do not change the `api.eunomia.cc` nginx config or certificate.
- Do not store secrets in skill files; read server state only via SSH when
  deploying.
