# Eunomia Release Runbook

This file contains the operating procedure for releasing the Eunomia WeChat mini program and backend.

## What The Skill Should Automate

The agent should automate:

- local backend test and jar packaging
- local WeChat mini program build
- production API host checks in built output
- temporary SSH key generation and cleanup
- backend upload to the Aliyun server
- MySQL backup before backend switch
- backend release directory creation and symlink switch
- `scoring-backend` restart
- nginx, HTTPS, and API verification

The user should manually do:

- append the printed temporary public key command on the server
- WeChat DevTools preview and upload
- WeChat public platform submit review
- final publish after review approval

Reason: root server access and WeChat upload/review/publish are account-side external actions. The user should keep final control of those actions.

## Current Production Facts

- WeChat mini program name: Eunomia schedule and scoring assistant
- AppID: `wx8113b05d52ef52b3`
- API legal request domain: `https://api.eunomia.cc`
- Server IP: `47.101.156.6`
- Server OS used during first release: Ubuntu 22.04
- Backend service: `scoring-backend`
- Java runtime: Java 17
- MySQL database name: parsed from `DB_URL`, currently expected to be `scoring_mvp`
- Backend release root: `/opt/scoring/app/releases`
- Backend current symlink: `/opt/scoring/app/current`
- Backend env file: `/opt/scoring/backend/deploy/prod-env.sh`
- Backup directory: `/opt/scoring/backups`
- HTTPS certificate path: `/etc/letsencrypt/live/api.eunomia.cc/fullchain.pem`

Do not assume secrets. Read them only from the server env file when executing remote deployment. Never print or save them.

## Lessons From Failed Runs

Avoid repeating these failure modes:

- Do not generate temporary keys under `%TEMP%`. Windows OpenSSH may refuse to load them because of ACLs, causing `Load key ... Permission denied`.
- Do not hand-write `ssh-keygen -N ""` in chat or ad hoc shell commands. Use `scripts/new-temp-ssh-key.ps1`; it uses the quoting that worked on this Windows setup.
- Do not trust `$ErrorActionPreference = "Stop"` for native commands such as `mvn`, `npm.cmd`, `git`, `scp`, `ssh`, or `rg`. Wrap them and check `$LASTEXITCODE`.
- Do not use Windows PowerShell `Set-Content -Encoding utf8` for the remote bash script. It can write a UTF-8 BOM and trigger `/usr/bin/env` shebang warnings on Linux.
- Do not leave temporary SSH cleanup as a manual memory task. Run `remove-temp-ssh-key.ps1` and verify login fails.

## Preflight Questions

Ask only when the answer cannot be discovered from files or server state.

Recommended defaults:

- If backend code changed, deploy backend.
- If `src`, `package.json`, `pages.json`, `manifest.json`, or `.env.local` changed, rebuild mini program.
- If database migration files changed, backup database before restart and watch Flyway logs.
- If only frontend changed, do not restart backend.
- If only backend changed, still rebuild mini program only when API contract changes affect frontend behavior.

Before deployment, verify:

- `.env.local` has `VITE_API_BASE_URL=https://api.eunomia.cc`
- `src/utils/request.js` uses `import.meta.env.DEV` so production builds do not use LAN/dev URL
- WeChat public platform has request legal domain `https://api.eunomia.cc`
- server `https://api.eunomia.cc/api/v1/tournaments` returns JSON with `code: 0`

## Local Release Preparation

From repo root:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\prepare-release.ps1
```

The script must stop immediately if any native command fails. Expected output:

- `backend\target\backend-0.0.1-SNAPSHOT.jar`
- `dist\build\mp-weixin`

## Temporary SSH Access

Generate the key with the bundled script:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\new-temp-ssh-key.ps1
```

It prints:

- `KEY_PATH`
- `COMMENT`
- the exact server command the user should run as `root`

After the user appends the public key on the server, test login:

```powershell
ssh -i <KEY_PATH> -o StrictHostKeyChecking=accept-new -o BatchMode=yes root@47.101.156.6 "echo eunomia-ssh-ok"
```

Never ask the user to paste the root password into the chat.

## Backend Deployment

After SSH test succeeds:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\deploy-backend.ps1 -SshKeyPath <KEY_PATH>
```

The remote deployment must:

- upload jar to `/tmp/eunomia-backend-new.jar`
- create compressed MySQL backup with `mysqldump --no-tablespaces`
- create `/opt/scoring/app/releases/<timestamp>/backend.jar`
- chown release directory to `scoring:scoring`
- switch `/opt/scoring/app/current`
- restart `scoring-backend`
- verify HTTPS API
- verify nginx config
- verify certificate presence
- remove remote temp files

## Required SSH Cleanup

Always run this after deploy succeeds, deploy fails, or the release is aborted after key creation:

```powershell
powershell -ExecutionPolicy Bypass -File <skill-dir>\scripts\remove-temp-ssh-key.ps1 -SshKeyPath <KEY_PATH> -KeyComment <COMMENT>
```

Success means:

- the key line is removed from server `~/.ssh/authorized_keys`
- login with the key fails
- local private/public key files are deleted

## Manual WeChat DevTools Upload

Open/import:

```text
D:\LJY\grade2\Scoring\dist\build\mp-weixin
```

Use AppID:

```text
wx8113b05d52ef52b3
```

Before upload:

- compile in WeChat DevTools
- preview on real device
- test login
- test tournament list
- test create tournament
- test scoring entry

Suggested upload version:

```text
1.0.1
```

Suggested upload remark:

```text
Eunomia update: bug fixes and improvements to tournament creation, tournament list, and scoring experience.
```

Chinese upload remark:

```text
Eunomia 赛程计分助手更新：修复问题，优化赛事创建、赛事列表和记分体验。
```

## Review Note

Use this for WeChat review:

```text
本小程序使用微信账号一键登录，无账号密码登录入口，审核人员可直接使用微信登录。

测试路径：
1. 打开小程序进入赛事大厅
2. 可查看赛事列表、搜索赛事、进入赛事详情
3. 如需测试收藏或创建比赛，请按提示选择头像并填写昵称
4. 点击“创建比赛”，可选择羽毛球或排球
5. 创建后可进入赛事详情、赛程、队伍名单、记分页面等核心功能

说明：
首次使用创建/收藏功能时，需要完善头像和昵称。
```

## Privacy Reminder

Do not claim "no personal information collected".

The app currently processes:

- WeChat openid or user identifier
- nickname
- avatar
- user-created tournaments
- favorites and creator relationship

It currently does not appear to process:

- phone number
- ID card
- precise location
- contacts
- payment information
- microphone/camera capture, except user choosing avatar through WeChat avatar capability

## Rollback

If the new backend release is bad:

1. SSH into the server.
2. List releases:

```bash
ls -lt /opt/scoring/app/releases
```

3. Pick previous release directory.
4. Switch symlink:

```bash
ln -sfn /opt/scoring/app/releases/<previous-release> /opt/scoring/app/current
systemctl restart scoring-backend
systemctl status scoring-backend --no-pager
curl -i https://api.eunomia.cc/api/v1/tournaments
```

If database migrations are irreversible, do not attempt database rollback automatically. Ask the user first and inspect backups.

## Common Problems

- `npm` blocked by PowerShell execution policy: use `npm.cmd`.
- Maven dependency download blocked: rerun with network approval.
- `mysqldump` complains about `PROCESS`: use `--no-tablespaces`.
- `/health` may not exist in the app: verify with `/api/v1/tournaments`.
- Built mini program uses LAN URL: check `.env.local` and `src/utils/request.js`.
- HTTPS fails after cert renewal: check nginx config and certbot status.