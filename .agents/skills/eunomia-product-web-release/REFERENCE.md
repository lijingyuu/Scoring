# Eunomia Product Web Release Runbook

Detailed operating procedure for deploying and updating the Eunomia public
product introduction static site at `https://product.eunomia.cc`.

## How The Site Works

- Source files live in `docs/product-guide/`.
- It is a static documentation-style product website: HTML, CSS, JavaScript,
  and screenshots.
- There is no build step. The deploy artifact is the source directory itself.
- nginx serves the site from `/opt/scoring/web/product/current`.
- Releases are stored under `/opt/scoring/web/product/releases/<timestamp>/`.
- `current` is a symlink to the active release.
- HTTPS is managed by certbot and Let's Encrypt.

The initial production deployment completed on 2026-08-11:

- Release: `/opt/scoring/web/product/releases/20260811-172314`
- Domain: `product.eunomia.cc`
- Certificate: `/etc/letsencrypt/live/product.eunomia.cc/fullchain.pem`
- Certificate expiry observed at deployment: 2026-11-09

## Routine Update Flow

1. Confirm changes are limited to product guide static files. Typical files:
   `docs/product-guide/*.html`, `docs/product-guide/assets/guide.css`,
   `docs/product-guide/assets/guide-scale.js`, and screenshots.
2. Verify source exists:

```powershell
Test-Path docs\product-guide\index.html
```

3. Check that the site is still static:

```powershell
rg -n "fetch\(|axios|/api/|/api/v1|http://|https://" docs\product-guide
```

No matches is the expected result. If matches appear, inspect them before
deploying and decide whether nginx needs a proxy or whether the links are
ordinary external links.

4. Generate a temporary SSH key using
   `.agents/skills/eunomia-release/scripts/new-temp-ssh-key.ps1`.
5. Ask the user to append the printed public key command on the server as
   `root`.
6. Test SSH.
7. Run `scripts/deploy-product-web.ps1 -SshKeyPath <KEY_PATH>`.
8. Remove the temporary SSH key with
   `.agents/skills/eunomia-release/scripts/remove-temp-ssh-key.ps1`.

## Deploy Script Behavior

`scripts/deploy-product-web.ps1`:

- verifies `docs/product-guide/index.html`;
- verifies the SSH key file exists;
- writes a temporary remote bash script without BOM;
- removes stale `/tmp/eunomia-product-web-dist`;
- uploads `docs/product-guide` to `/tmp/eunomia-product-web-dist`;
- creates `/opt/scoring/web/product/releases/<timestamp>/`;
- copies the uploaded files into the release directory;
- switches `/opt/scoring/web/product/current`;
- writes `/etc/nginx/sites-available/eunomia-product-web`;
- enables it through `/etc/nginx/sites-enabled/eunomia-product-web`;
- runs `nginx -t`;
- reloads nginx;
- verifies HTTP;
- runs certbot for `product.eunomia.cc`;
- verifies HTTPS;
- removes remote temp files.

## nginx Shape

The product site does not proxy API traffic.

```nginx
server {
    listen 80;
    server_name product.eunomia.cc;
    root /opt/scoring/web/product/current;
    index index.html;

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

certbot adds the HTTPS server block and HTTP to HTTPS redirect.

## Verification

Preferred server-side verification:

```bash
curl -I --max-time 10 https://product.eunomia.cc/
curl -I --max-time 10 http://product.eunomia.cc/
```

Expected:

- HTTPS returns `HTTP/1.1 200 OK`.
- HTTP returns `HTTP/1.1 301 Moved Permanently`.
- HTTP `Location` points to `https://product.eunomia.cc/`.

Local Windows `curl.exe` may fail with a Schannel credential error in this
Codex environment. If that happens, prefer server-side curl or browser
verification instead of treating it as a site failure.

## Rollback

Rollback does not touch backend services or the database.

```bash
ls -lt /opt/scoring/web/product/releases
ln -sfn /opt/scoring/web/product/releases/<previous-release> /opt/scoring/web/product/current
nginx -t && systemctl reload nginx
curl -fsS https://product.eunomia.cc/ -o /dev/null && echo OK
```

## First-Time Domain Setup

Already done as of the initial deployment, but needed again if moving domains:

1. In Aliyun DNS, add an A record:
   - Host record: `product`
   - Type: `A`
   - Value: `47.101.156.6`
2. Ensure ECS security group allows inbound TCP `80` and `443`.
3. Wait for DNS propagation.
4. Run deployment so certbot can issue the HTTPS certificate.

## What This Skill Must Not Do

- Do not restart `scoring-backend`.
- Do not upload backend jars.
- Do not run Maven or mini-program builds.
- Do not modify `/opt/scoring/app`.
- Do not modify MySQL or server env files.
- Do not change `api.eunomia.cc` or `www.eunomia.cc` configs.
