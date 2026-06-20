---
name: eunomia-release
description: Release and update the Eunomia WeChat mini program and its Spring Boot backend. Use when the user wants to deploy Eunomia, update the server, build the WeChat mini program, prepare review/upload artifacts, verify api.eunomia.cc, or recover a failed release.
---

# Eunomia Release

## Fixed Context

- Project root: `D:\LJY\grade2\Scoring`
- Mini program AppID: `wx8113b05d52ef52b3`
- Production API: `https://api.eunomia.cc`
- Server public IP: `47.101.156.6`
- Backend systemd service: `scoring-backend`
- Backend deploy root: `/opt/scoring/app`
- Backend env file: `/opt/scoring/backend/deploy/prod-env.sh`
- Backend jar output: `backend/target/backend-0.0.1-SNAPSHOT.jar`
- Mini program output: `dist/build/mp-weixin`

Never write database passwords, WeChat AppSecret, JWT secret, or SSH private keys into this skill or repo files.

## Quick Start

1. Read [REFERENCE.md](REFERENCE.md) before acting. It contains the full release runbook, temporary SSH flow, WeChat upload notes, privacy notes, and rollback steps.
2. Inspect current scope: backend-only, mini-program-only, or both.
3. Prepare local artifacts:

```powershell
powershell -ExecutionPolicy Bypass -File .agents\skills\eunomia-release\scripts\prepare-release.ps1
```

4. If backend deployment is needed, obtain temporary SSH key access from the user, then run:

```powershell
powershell -ExecutionPolicy Bypass -File .agents\skills\eunomia-release\scripts\deploy-backend.ps1 -SshKeyPath C:\Users\lijin\.ssh\<key-name>
```

5. Verify server:

```text
systemctl is-active scoring-backend
curl -i https://api.eunomia.cc/api/v1/tournaments
nginx -t
certbot certificates -d api.eunomia.cc
```

6. Tell the user to open `D:\LJY\grade2\Scoring\dist\build\mp-weixin` in WeChat DevTools, preview on device, upload, submit review, and manually publish after approval.

## Guardrails

- Do not deploy if built mini program contains `47.101.156.6`, `127.0.0.1`, or `10.4.117.181`.
- Do not claim the app collects no personal information. It uses openid/user identifier, nickname, avatar, created tournaments, and favorites.
- Do not manually kill the backend Java process. Use `systemctl restart scoring-backend`.
- Do not automatically roll back database migrations. Ask first and inspect backups.
- After temporary SSH deployment, remove the server authorized key and verify that key can no longer log in.

## Common Checks

- `.env.local` must contain `VITE_API_BASE_URL=https://api.eunomia.cc`.
- `src/utils/request.js` must separate dev/prod with `import.meta.env.DEV`.
- WeChat request legal domain must include `https://api.eunomia.cc`.
- Backend verification endpoint is `/api/v1/tournaments`, not `/health`.
