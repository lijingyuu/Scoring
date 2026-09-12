# 羽球/排球赛事记分系统 — 接口文档

> 版本：v1 | 更新时间：2026-07-27 | 后端：Spring Boot 3.3.5 | 前端：uni-app (Vue 3)

---

## 目录

- [1. 概述](#1-概述)
- [2. 认证机制](#2-认证机制)
- [3. 通用约定](#3-通用约定)
- [4. 认证接口](#4-认证接口)
- [5. 赛事接口](#5-赛事接口)
- [6. 比赛接口](#6-比赛接口)
- [7. 枚举字典](#7-枚举字典)
- [8. 数据模型速查](#8-数据模型速查)

---

## 1. 概述

| 项目 | 说明 |
|------|------|
| 基础路径 | `/api/v1` |
| 协议 | HTTP/1.1 |
| 数据格式 | JSON |
| 编码 | UTF-8 |
| 鉴权方式 | Bearer Token (JWT) |
| 开发环境端口 | `8080` |
| 微信小程序 | 直连 `VITE_API_BASE_URL` |
| H5/Web 开发 | Vite 代理 `/api` → `http://127.0.0.1:8080` |

---

## 2. 认证机制

### 2.1 获取 Token

微信小程序通过 `wx.login()` 获取临时 code，调用 [POST /auth/wechat-login](#41-微信登录) 换取 JWT。Web/H5 可使用 [POST /auth/register](#44-账号注册) 或 [POST /auth/password-login](#45-密码登录) 获取 JWT；PC 网页（admin-web）主推微信扫码登录（[4.6~4.9](#46-生成扫码登录小程序码pc-网页)），账号密码作为兼容保留。

开发环境仍有 `DevMockAuthFilter` 自动注入模拟 token，用于本地联调。

### 2.2 使用 Token

所有需认证的请求携带 Header：

```
Authorization: Bearer <token>
```

Token 有效期 **30 天**，前端存储在 `uni.getStorageSync('scoring_token')`。

### 2.3 权限标识

| 标识 | 含义 |
|------|------|
| 🔓 | 无需登录 |
| 🔒 | 需要登录（携带有效 Token） |

### 2.4 执裁会话锁（比赛独占）

比赛进行中的写操作要求调用方持有**该场比赛的执裁锁**。前端进入执裁页面（记分板 / 阵容填写 / 团体赛控制台）时调用 `POST /api/v1/matches/{id}/lock` 抢锁，成功后每 15s 调用 `POST /api/v1/matches/{id}/heartbeat` 续期；锁有效期 **75 秒**，超时未续期则其他设备可接管。锁由「当前用户 + 会话 token」共同标识：

```
X-Match-Lock-Token: <lockToken>
```

以下写接口除 `Authorization` 外还必须携带 `X-Match-Lock-Token`，且与服务器持有的 `match_record.lock_token`、当前用户三者一致、未过期，否则返回 `code=403`（`ForbiddenException`）：

`PUT /api/v1/matches/{id}/score`、`PUT /api/v1/matches/{id}/finish`、`PUT /api/v1/matches/{id}/restart`、`PUT /api/v1/matches/{id}/events`、`PUT /api/v1/matches/{id}/lineup-config`、`PUT /api/v1/matches/{id}/team-lineup`、`PUT /api/v1/matches/{id}/team-items/{itemCode}/start`、`PUT /api/v1/matches/{id}/team-match/settle`。

`report-meta`、`report-seal` 不强制执裁锁。锁的获取/续期/释放见 [6.15 执裁会话锁](#615-执裁会话锁比赛独占)。

---

## 3. 通用约定

### 3.1 统一响应格式

所有接口返回以下结构：

```json
{
  "code": 0,
  "message": "success",
  "data": <具体数据>
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | `0` = 成功，非零 = 业务错误 |
| `message` | string | 提示信息 |
| `data` | any | 响应数据（`null` 表示无返回体） |

> 前端 `request.js` 自动校验 `code === 0`，成功时直接 resolve `data` 字段。

### 3.2 错误处理

业务错误（如未登录、参数校验失败）返回非零 `code` 和对应 `message`。前端统一 toast 提示（可通过 `options.silent` 静默）。

### 3.3 ID 格式

所有实体 ID 使用 **雪花算法 (Snowflake)** 生成的 19 位整数，以字符串形式传输。

---

## 4. 认证接口

### 4.1 微信登录

```
POST /api/v1/auth/wechat-login  🔓
```

**请求体**

```json
{
  "code": "string (微信 wx.login() 返回的临时 code)"
}
```

**响应**

```json
{
  "token": "eyJhbG...",
  "profileCompleted": false
}
```

| 字段 | 说明 |
|------|------|
| `token` | JWT，后续请求放入 Authorization Header |
| `profileCompleted` | 是否已完善个人信息（昵称+头像） |

---

### 4.2 完善个人信息

```
POST /api/v1/auth/profile  🔒
```

**请求体**

```json
{
  "nickname": "string (必填，用户昵称)",
  "avatarUrl": "string (必填，头像 URL)"
}
```

**响应**

```json
{
  "id": "329847230984723",
  "nickname": "小明",
  "avatarUrl": "https://...",
  "profileCompleted": true
}
```

---

### 4.3 获取当前用户信息

```
GET /api/v1/users/me  🔒
```

**响应** — 同上 [完善个人信息](#42-完善个人信息) 的响应结构。

---

### 4.4 账号注册

```
POST /api/v1/auth/register  🔓
```

**请求体**

```json
{
  "username": "string",
  "password": "string",
  "nickname": "string (选填)"
}
```

**响应** — 同 [微信登录](#41-微信登录)，返回 JWT 和资料完善状态。

---

### 4.5 密码登录

```
POST /api/v1/auth/password-login  🔓
```

**请求体**

```json
{
  "username": "string",
  "password": "string"
}
```

**响应** — 同 [微信登录](#41-微信登录)，返回 JWT 和资料完善状态。

---

### 4.6 生成扫码登录小程序码（PC 网页）

```
POST /api/v1/auth/pc/qr-code  🔓
```

PC 网页（admin-web）发起微信扫码登录时调用。后端生成 32 位 hex 票据落库
（`web_login_session`，3 分钟有效期），并调微信 `getwxacodeunlimit`
（scene=ticket，page=`pages/auth/pc-confirm`）返回小程序码图片。

**响应**

```json
{
  "ticket": "a1b2c3d4...32位十六进制",
  "qrImage": "data:image/png;base64,...",
  "expireSeconds": 180
}
```

| 字段 | 说明 |
|------|------|
| `ticket` | 轮询凭证，同时是小程序码 scene |
| `qrImage` | 小程序码图片，可直接放入 `<img src>` |
| `expireSeconds` | 票据有效期（秒） |

> 限流：与登录接口同桶（默认 30 次/分钟/IP）。
> 前置条件：生产环境需在小程序后台「API IP 白名单」加入服务器出口 IP（否则报 40164）；
> `qr-check-path=true`（默认，正式模式）时要求 `pages/auth/pc-confirm` 已随正式版发布，否则报 41030。
> 临时灰度可在服务器环境加 `WECHAT_QR_CHECK_PATH=false` + `WECHAT_QR_ENV_VERSION=trial` 改出体验版码（需重启后端），详见后台管理文档。

---

### 4.7 轮询扫码状态（PC 网页）

```
GET /api/v1/auth/pc/status?ticket=xxx  🔓
```

**响应**

```json
{
  "status": "CREATED",
  "nickname": "创建者小明",
  "avatarUrl": "https://...",
  "token": "eyJhbG...",
  "profileCompleted": true
}
```

| `status` | 说明 |
|------|------|
| `CREATED` | 已出码，等待扫码 |
| `SCANNED` | 已扫码待确认，返回扫码人昵称/头像供 PC 核对 |
| `CONFIRMED` | 已确认授权，**本次一次性返回 token**（票据同时转 CONSUMED，防重放） |
| `CONSUMED` | token 已被取走，不再下发 |
| `EXPIRED` | 票据过期/无效，PC 端展示「点击刷新」 |

> 前端建议 1.5s 轮询；拿到 `token` 后与密码登录同样处理（存 token → `GET /users/me` → 进后台）。

---

### 4.8 上报扫码（小程序端）

```
POST /api/v1/auth/pc/scan  🔒
```

小程序落地页 `pages/auth/pc-confirm` 在 `onLoad` 解析 scene 后上报，驱动 PC 端展示
「已扫码，请在手机上确认」。幂等：重复上报或票据已推进时静默成功。

**请求体**

```json
{ "ticket": "string (32位hex)" }
```

---

### 4.9 确认授权（小程序端）

```
POST /api/v1/auth/pc/confirm  🔒
```

用户在手机上点击「确认授权」后调用，以当前小程序登录态为该票据点亮绿灯。
**确认人必须是上报扫码的本人**（防共享屏幕场景他人从同一张码进入替确认，导致 PC 以他人身份登录）；
仅当票据仍是 CREATED 且无人扫过时允许确认人直转（scan 上报完全丢失的容错，此时确认人即第一接触人）。

**请求体**

```json
{ "ticket": "string (32位hex)" }
```

**错误**（400）：`二维码无效` / `二维码已过期，请在电脑上刷新后重新扫码` / `二维码已被使用，请在电脑上重新发起登录` / `请使用扫码的微信号确认授权`

---

## 5. 赛事接口

### 5.1 赛事列表

```
GET /api/v1/tournaments  🔓
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `keyword` | string | 否 | 搜索词，模糊匹配赛事名称和地点 |

**响应** — `Tournament[]`

```json
[
  {
    "id": "329847230984723",
    "name": "2026 春季羽毛球赛",
    "location": "体育馆 A 馆",
    "status": 1,
    "sportType": 0,
    "tournamentType": 0,
    "participantType": 0,
    "teamMatchTemplate": 0,
    "knockoutSlots": 8,
    "knockoutRounds": 3,
    "qualifiersPerGroup": 2,
    "roundRobinRounds": 1,
    "bestOf": 3,
    "gamesToWin": 2,
    "pointsToWin": 21,
    "decidingPointsToWin": null,
    "enableDeuce": true,
    "capPoint": 30,
    "roundRuleEnabled": false,
    "favoriteCount": 12,
    "archived": false,
    "createTime": "2026-06-10T08:00:00",
    "favorite": true,
    "creator": false
  }
]
```

> `favorite` 和 `creator` 为当前登录用户的瞬态标记（未登录均为 `false`）。新增字段说明见 [7.3](#73-参赛者类型-participanttype) 和 [7.4](#74-团体赛模板-teammatchtemplate)。

---

### 5.2 创建赛事

```
POST /api/v1/tournaments  🔒
```

#### 5.2.1 羽毛球个人赛

```json
{
  "name": "string (必填)",
  "location": "string (选填)",
  "sportType": 0,
  "participantType": 0,
  "tournamentType": 0,
  "knockoutSlots": 8,
  "knockoutRounds": 3,
  "qualifiersPerGroup": 2,
  "players": [
    { "name": "张三", "seed": 1 },
    { "name": "李四", "seed": 2 }
  ],
  "rule": {
    "bestOf": 3,
    "gamesToWin": 2,
    "pointsToWin": 21,
    "decidingPointsToWin": null,
    "enableDeuce": true,
    "capPoint": 30
  }
}
```

#### 5.2.2 羽毛球团体赛（苏迪曼杯 5 项）

```json
{
  "name": "string (必填)",
  "location": "string (选填)",
  "sportType": 0,
  "participantType": 1,
  "teamMatchTemplate": 1,
  "tournamentType": 0,
  "knockoutSlots": 4,
  "knockoutRounds": 2,
  "teams": [
    {
      "name": "火箭队",
      "seed": 1,
      "members": [
        { "name": "队员A", "jerseyNumber": 1, "captain": true },
        { "name": "队员B", "jerseyNumber": 2 }
      ]
    }
  ],
  "rule": {
    "bestOf": 3,
    "gamesToWin": 2,
    "pointsToWin": 21,
    "decidingPointsToWin": null,
    "enableDeuce": true,
    "capPoint": 30
  }
}
```

> 团体赛模板(`teamMatchTemplate`)：`1`=苏迪曼杯5项(MS/WS/MD/WD/XD)，`2`=接力追分赛。五项各自独立记分，先赢3项者胜。
>
> 队伍 `seed`（选填）：与个人赛选手 `seed` 规则一致 —— 刻意设置种子的队伍按种子序蛇形保位（互不同组）；未设置种子的队伍随机抽签分堆。

#### 5.2.3 排球赛事

```json
{
  "name": "string (必填)",
  "location": "string (选填)",
  "sportType": 1,
  "tournamentType": 0,
  "knockoutSlots": 4,
  "knockoutRounds": 2,
  "teams": [
    {
      "name": "火箭队",
      "seed": 1,
      "members": [
        { "name": "队员A", "jerseyNumber": 1, "captain": true, "libero": false },
        { "name": "队员B", "jerseyNumber": 2, "captain": false, "libero": true }
      ]
    }
  ],
  "rule": {
    "bestOf": 5,
    "gamesToWin": 3,
    "pointsToWin": 25,
    "decidingPointsToWin": 15,
    "enableDeuce": true,
    "capPoint": 30
  }
}
```

#### 5.2.4 赛段规则与裁判密码（可选）

```json
{
  "roundRuleEnabled": true,
  "roundRules": [
    {
      "stageType": 0,
      "roundNum": 0,
      "rule": { "bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": null, "enableDeuce": true, "capPoint": 30 }
    },
    {
      "stageType": 1,
      "roundNum": 3,
      "rule": { "bestOf": 5, "gamesToWin": 3, "pointsToWin": 21, "decidingPointsToWin": null, "enableDeuce": true, "capPoint": 30 }
    }
  ],
  "refereePassword": "12345678"
}
```

| 字段 | 说明 |
|------|------|
| `knockoutRounds` | 淘汰赛轮数，和参赛数量、淘汰名额共同决定赛程规模 |
| `rule.decidingPointsToWin` | 决胜局目标分，排球默认 15 |
| `roundRuleEnabled` | 是否启用赛段级别规则 |
| `roundRules[].stageType` | `0`=小组赛，`1`=淘汰赛 |
| `roundRules[].roundNum` | 小组赛固定 `0`；淘汰赛使用轮次号 |
| `refereePassword` | 可选，创建赛事时初始化裁判验证密码 |

**响应**

```json
{
  "tournamentId": "329847230984723"
}
```

---

### 5.3 赛事详情

```
GET /api/v1/tournaments/{id}  🔓
```

**响应** — `TournamentDetailVO`，字段同 [赛事列表](#51-赛事列表) 中的单条记录。

---

### 5.4 收藏赛事

```
POST /api/v1/tournaments/{id}/favorite  🔒
```

**响应** — 无返回体 (`null`)

---

### 5.5 取消收藏

```
DELETE /api/v1/tournaments/{id}/favorite  🔒
```

**响应** — 无返回体 (`null`)

---

### 5.6 我的收藏

```
GET /api/v1/tournaments/mine/favorites  🔒
```

**响应** — `Tournament[]`，结构同 [赛事列表](#51-赛事列表)。

---

### 5.7 我创建的

```
GET /api/v1/tournaments/mine/created  🔒
```

**响应** — `Tournament[]`，结构同 [赛事列表](#51-赛事列表)。

---

### 5.8 淘汰赛对阵表

```
GET /api/v1/tournaments/{id}/bracket  🔓
```

**响应** — `TournamentBracketVO`

```json
{
  "id": "329847230984723",
  "name": "2026 春季赛",
  "status": 1,
  "sportType": 0,
  "tournamentType": 0,
  "knockoutSlots": 8,
  "currentStage": 2,
  "knockoutGenerated": true,
  "bestOf": 3,
  "gamesToWin": 2,
  "pointsToWin": 21,
  "enableDeuce": true,
  "capPoint": 30,
  "players": [
    {
      "id": "p1",
      "name": "张三",
      "seed": 1,
      "members": [{ "id": "m1", "name": "队员A", "jerseyNumber": 1, "captain": true, "libero": false }]
    }
  ],
  "matches": [
    {
      "id": "m1",
      "roundNum": 1,
      "matchIndex": 0,
      "status": 2,
      "leftPlayerId": "p1",
      "rightPlayerId": "p2",
      "winnerSide": "left",
      "scoreDisplay": "21:15,21:18",
      "nextMatchId": "m5"
    }
  ]
}
```

> `players` 为个人赛选手或排球队伍（含 `members`）；`matches` 通过 `nextMatchId` 串联淘汰树。

---

### 5.9 小组赛数据

```
GET /api/v1/tournaments/{id}/groups  🔓
```

**响应** — `TournamentGroupsVO`

```json
{
  "id": "329847230984723",
  "knockoutSlots": 8,
  "qualifiersPerGroup": 2,
  "groups": [
    {
      "groupNo": 1,
      "players": [ ... ],
      "matches": [
        { "id": "m1", "leftPlayerId": "p1", "rightPlayerId": "p2", "status": 2, "winnerSide": "left", "scoreDisplay": "21:15,21:18" }
      ]
    }
  ]
}
```

---

### 5.10 小组赛积分榜

```
GET /api/v1/tournaments/{id}/group-standings  🔓
```

**响应** — `GroupStandingsVO`

```json
{
  "id": "329847230984723",
  "knockoutSlots": 8,
  "qualifiersPerGroup": 2,
  "allGroupMatchesFinished": true,
  "hasUnresolvedTie": false,
  "groups": [
    {
      "groupNo": 1,
      "standings": [
        {
          "playerId": "p1",
          "playerName": "张三",
          "seedRank": 1,
          "rank": 1,
          "qualified": true,
          "tieUnresolved": false,
          "matchWins": 3,
          "matchLosses": 0,
          "gameWins": 6,
          "gameLosses": 0,
          "netGames": 6,
          "pointsFor": 126,
          "pointsAgainst": 80,
          "netPoints": 46
        }
      ]
    }
  ]
}
```

**排名规则**：胜场数 → 净胜局 → 净胜分 → 直接胜负 → 名字序

---

### 5.11 生成淘汰赛

```
POST /api/v1/tournaments/{id}/generate-knockout  🔒
```

> 仅用于「小组赛+淘汰赛」赛制。小组赛全部结束后，根据积分榜晋级者生成淘汰赛对阵。

**响应** — 无返回体 (`null`)

---

### 5.12 归档赛事

```
PUT /api/v1/tournaments/{id}/archive  🔒
```

> 仅创建者可操作。归档后赛事从主列表隐藏，移至「我的 → 归档」。

**响应** — 无返回体 (`null`)

---

### 5.13 取消归档

```
PUT /api/v1/tournaments/{id}/unarchive  🔒
```

> 仅创建者可操作。

**响应** — 无返回体 (`null`)

---

### 5.14 我的归档

```
GET /api/v1/tournaments/mine/archived  🔒
```

**响应** — `Tournament[]`，结构同 [赛事列表](#51-赛事列表)。

---

### 5.15 获取小组排名模板配置

```
GET /api/v1/tournaments/{id}/ranking-config  🔓
```

> 仅「小组赛+淘汰赛」(`tournamentType=1`) 与「纯循环赛」(`tournamentType=2`) 支持。归档赛事对创建者仍可读。返回当前生效的排名模板；若从未配置过，返回 `legacyDefault`（自定义模板，含系统兜底）。

**响应** — `TournamentRankingConfigVO`

```json
{
  "tournamentId": "329847230984723",
  "configVersion": 1,
  "template": "CUSTOM",
  "priorities": ["MATCH_WINS", "NET_GAMES", "NET_POINTS", "HEAD_TO_HEAD"],
  "systemFallbackCriterion": "POINT_WIN_RATE",
  "pointsSystemEnabled": false,
  "mathType": "DIFFERENCE",
  "twoWayTieH2HFirst": false,
  "withdrawPolicy": "NONE",
  "locked": false,
  "lockedAt": null,
  "creator": true
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `template` | string | 排名模板名，见 [7.7 排名模板](#77-排名模板) |
| `priorities` | string[] | 排名优先级序列（`Criterion` 枚举名，见 7.7） |
| `systemFallbackCriterion` | string\|null | 系统自动补的兜底判据（自定义模板缺小分判据时） |
| `pointsSystemEnabled` | boolean | 是否启用比赛积分制（仅 FIVB 排球） |
| `mathType` | string | `DIFFERENCE`（差值）或 `RATIO`（比率） |
| `twoWayTieH2HFirst` | boolean | 两人同分时是否优先比较直接胜负 |
| `withdrawPolicy` | string | 退赛处理策略，见 7.7 |
| `locked` | boolean | 是否已锁定（已有比赛结束后或手动锁定后） |
| `creator` | boolean | 当前用户是否为赛事创建者 |

---

### 5.16 保存小组排名模板配置

```
PUT /api/v1/tournaments/{id}/ranking-config  🔒
```

> 仅创建者可操作；仅「小组赛+淘汰赛」与「纯循环赛」支持。保存后**清空该赛事的晋级资格覆盖**（见 5.17）。一旦已有一场排名相关比赛结束，配置即锁定不可再改。

**请求体** — `UpdateTournamentRankingConfigReq`

```json
{
  "template": "FIVB_VOLLEYBALL",
  "priorities": null
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `template` | string | 预设模板名；为空或省略时按 `priorities` 自定义 |
| `priorities` | string[] | 自定义判据序列；传 `template` 时可为 `null`（沿用该预设） |

> 规则：`template` 与 `priorities` 至少提供一个。给了 `template` 且 `priorities` 为空 → 直接用该预设；给了 `priorities` → 强制落为 `CUSTOM`，其余属性（`mathType`/`twoWayTieH2HFirst`/`withdrawPolicy`/`pointsSystem`）继承自 `template` 对应的预设（未给 template 时继承 `legacyDefault`）。

**响应** — `TournamentRankingConfigVO`，结构同 [5.15](#515-获取小组排名模板配置)。

---

### 5.17 保存晋级资格覆盖

```
PUT /api/v1/tournaments/{id}/qualification-overrides  🔒
```

> 仅创建者或已认证裁判可操作；仅「小组赛+淘汰赛」(`tournamentType=1`) 支持；淘汰赛生成后不可再改。用于在小组赛结束、但出线名次存在无法用规则自动裁决的并列（`tieUnresolved`）时，由人工指定出线选手。

**请求体** — `UpdateQualificationOverridesReq`

```json
{
  "overrides": [
    { "groupNo": 1, "rankSlot": 2, "playerId": "p42" },
    { "groupNo": 3, "rankSlot": 1, "playerId": "p17" }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `overrides` | Item[] | 覆盖列表；传空数组或 `null` 表示清空全部覆盖 |
| `overrides[].groupNo` | int | 小组号 |
| `overrides[].rankSlot` | int | 出线名次（1..`qualifiersPerGroup`），仅限未决名次 |
| `overrides[].playerId` | string | 指定该名次的选手（必须属于该小组且处于未决并列） |

> 校验：必须覆盖**每个**存在未决并列的小组的**全部**未决名次，数量不能多也不能少；同一 (groupNo, rankSlot) 与同一 player 均不可重复。

**响应** — 无返回体 (`null`)

---

### 5.18 生成淘汰赛预览

```
POST /api/v1/tournaments/{id}/knockout-preview  🔒
```

> 仅创建者或已认证裁判可操作；仅「小组赛+淘汰赛」支持；淘汰赛生成后不可预览。用于在正式 `generate-knockout` 前预览首轮对阵（不含三四名赛）。

**响应** — `KnockoutPreviewVO`

```json
{
  "id": "329847230984723",
  "knockoutSlots": 8,
  "qualifiersPerGroup": 2,
  "allGroupMatchesFinished": true,
  "hasUnresolvedTie": false,
  "matches": [
    {
      "slotIndex": 0,
      "leftPlayer": { "playerId": "p1", "playerName": "张三", "groupNo": 1, "groupRank": 1, "seedRank": 1 },
      "rightPlayer": { "playerId": "p2", "playerName": "李四", "groupNo": 4, "groupRank": 2, "seedRank": null }
    }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `matches` | MatchVO[] | 首轮对阵（蛇形排法，两两配对） |
| `matches[].slotIndex` | int | 槽位序号 |
| `matches[].leftPlayer/rightPlayer` | ParticipantVO | 参赛方；轮空位为 `null` |
| `ParticipantVO.groupRank` | int | 小组名次 |
| `ParticipantVO.seedRank` | int\|null | 种子排名 |

---

### 5.19 编辑队伍（创建者）

```
PUT /api/v1/tournaments/{id}/teams/{participantId}  🔒
```

> 仅创建者可操作；归档赛事只读；仅团体赛（排球/羽毛球团体）支持。
> 有意不支持：新增/删除队伍、删除队员。校验与创建时一致：排球球衣号码必填且全队唯一、自由人必须带号码、全队恰好 1 名队长。

**请求体**

```json
{
  "name": "雷暴（选填，传则改队名）",
  "addMembers": [
    { "name": "新队员", "jerseyNumber": 7, "libero": false, "captain": false }
  ]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `name` | string\|null | 新队名；`null`/缺省表示不修改 |
| `addMembers` | TeamMemberEntry[]\|null | 追加队员；排球需 `jerseyNumber`，羽毛球忽略号码字段 |

**响应** — 空数据 `ApiResponse<Void>`；错误时 `message` 说明原因（如"球衣号码 7 已被使用"、"全队必须有且仅有1名队长"）。

**关联行为**：`GET /tournaments/{id}/teams` 响应新增 `creator` 布尔字段（当前用户是否创建者），前端据此显示/隐藏编辑入口；队名修改会实时反映到赛程与对阵（名称均从 `Player` 动态解析，无冗余副本）。`teams[].seedRank` 返回队伍种子序号（创建时填写，int\|null），与个人赛 `seedRank` 语义一致。

---

## 6. 比赛接口

> 进行中的比赛写接口（`score / finish / restart / events / lineup-config / team-lineup / team-items/{itemCode}/start / team-match/settle`）除 `Authorization` 外还要求请求头 `X-Match-Lock-Token`，详见 [2.4 执裁会话锁](#24-执裁会话锁比赛独占) 与 [6.15](#615-执裁会话锁比赛独占)。

### 6.1 校验比赛操作权限

```
GET /api/v1/matches/{id}/can-operate  🔒
```

校验当前用户是否有权操作该场比赛。权限条件：用户为赛事创建者，或已被授权为该赛事裁判，且赛事未归档。

**响应**

```json
true
```

| 值 | 说明 |
|---|---|
| `true` | 当前用户可以操作该比赛 |
| `false` | 当前用户无操作权限（非创建者、非授权裁判、赛事已归档，或未登录） |

> 前端在计分板、阵容填写、团体赛控制台等页面 onLoad 时调用此接口，无权限时返回上一页并提示用户先录入裁判身份。


### 6.2 结束比赛

```
PUT /api/v1/matches/{id}/finish  🔒
```

**请求体**

```json
{
  "winnerSide": "left",
  "leftScore": 2,
  "rightScore": 0,
  "retiredSide": null,
  "gameScores": [
    { "gameNo": 1, "leftScore": 21, "rightScore": 15, "winnerSide": "left" },
    { "gameNo": 2, "leftScore": 21, "rightScore": 18, "winnerSide": "left" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `winnerSide` | string | **是** | `"left"` 或 `"right"` |
| `leftScore` | int | **是** | 左侧赢得局数 |
| `rightScore` | int | **是** | 右侧赢得局数 |
| `retiredSide` | string | 否 | 弃权方 `"left"` / `"right"` |
| `leftGameWins` | int | 否 | (遗留字段) |
| `rightGameWins` | int | 否 | (遗留字段) |
| `gameScores` | array | 否 | 每局详细比分 |

**响应** — 无返回体 (`null`)

> 淘汰赛中，胜者会自动推进到 `nextMatchId` 对应的下一场比赛。

---

### 6.3 重新开始比赛

```
PUT /api/v1/matches/{id}/restart  🔒
```

**响应** — 无返回体 (`null`)

> 重置比赛为初始状态，清除所有比分、事件、阵容配置和主题配置。
> 如果胜者已晋级到下一场，同时清除下一场的晋级者。

---

### 6.4 更新比赛分数

```
PUT /api/v1/matches/{id}/score  🔒
```

**请求体**

```json
{
  "scoreDisplay": "21:15,21:18",
  "winnerId": "p1"
}
```

**响应** — 无返回体 (`null`)

> ⚠️ 此接口已被 [6.2 结束比赛](#62-结束比赛) 替代，前端当前未直接调用。

---

### 6.5 获取阵容配置

```
GET /api/v1/matches/{id}/lineup-config  🔓
```

**查询参数**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `gameNo` | int | **是** | 局号 |

**响应** — `MatchLineupConfigVO`

```json
{
  "gameNo": 1,
  "exists": true,
  "effectiveFromGameNo": 1,
  "config": {
    "serveSide": "left",
    "left": {
      "court": ["p1", "p2", "p3", "p4", "p5", "p6"],
      "middlePairIndexes": [2, 3],
      "libero1Id": "m5",
      "libero2Id": null
    },
    "right": {
      "court": ["p7", "p8", "p9", "p10", "p11", "p12"],
      "middlePairIndexes": [1, 2],
      "libero1Id": "m16",
      "libero2Id": null
    }
  }
}
```

> 如果 `gameNo` 对应的局没有配置，自动回退到最近一局的配置。

---

### 6.6 保存阵容配置

```
PUT /api/v1/matches/{id}/lineup-config  🔒
```

**请求体** — 结构同 [6.5 获取阵容配置](#65-获取阵容配置) 中的 `config`，外加 `gameNo` 和 `serveSide`：

```json
{
  "gameNo": 1,
  "serveSide": "left",
  "left": { "court": [...], "middlePairIndexes": [...], "libero1Id": "...", "libero2Id": "..." },
  "right": { "court": [...], "middlePairIndexes": [...], "libero1Id": "...", "libero2Id": "..." }
}
```

**响应** — 无返回体 (`null`)

---

### 6.7 获取比赛记录

```
GET /api/v1/matches/{id}/record  🔓
```

**响应** — `MatchRecordDetailVO`

```json
{
  "matchId": "m1",
  "tournamentId": "329847230984723",
  "tournamentName": "2026 春季赛",
  "roundNum": 2,
  "matchIndex": 1,
  "status": 2,
  "bestOf": 5,
  "gamesToWin": 3,
  "pointsToWin": 25,
  "enableDeuce": true,
  "capPoint": 30,
  "scoreDisplay": "3:1",
  "leftGameWins": 3,
  "rightGameWins": 1,
  "winnerSide": "left",
  "retiredSide": null,
  "left": {
    "id": "p1",
    "name": "火箭队",
    "members": [ ... ]
  },
  "right": {
    "id": "p2",
    "name": "星火队",
    "members": [ ... ]
  },
  "gameScores": [
    { "gameNo": 1, "leftScore": 25, "rightScore": 20, "winnerSide": "left" }
  ],
  "rosterSnapshot": {
    "leftMembers": [ ... ],
    "rightMembers": [ ... ]
  },
  "lineupSnapshots": [
    {
      "gameNo": 1,
      "serveSide": "left",
      "left": { "court": [ ... ] },
      "right": { "court": [ ... ] }
    }
  ],
  "events": [
    {
      "eventSeq": 1,
      "eventType": "substitution",
      "eventTypeLabel": "换人",
      "gameNo": 1,
      "leftScore": 12,
      "rightScore": 10,
      "serveSide": "left",
      "summary": "左队 #5 换下 #3",
      "detailLines": ["...", "..."],
      "createTime": "2026-06-15T10:30:00"
    }
  ]
}
```

---

### 6.8 批量保存比赛事件

```
PUT /api/v1/matches/{id}/events  🔒
```

**请求体**

```json
{
  "events": [
    {
      "eventSeq": 1,
      "eventType": "substitution",
      "gameNo": 1,
      "leftScore": 12,
      "rightScore": 10,
      "serveSide": "left",
      "payloadJson": "{\"inPlayerId\":\"m5\",\"outPlayerId\":\"m3\",\"side\":\"left\"}"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `eventSeq` | int | 是 | 全局事件序号（>0） |
| `eventType` | string | 是 | 事件类型（见 [7.4 事件类型](#74-match_event-事件类型)） |
| `gameNo` | int | 是 | 局号 |
| `leftScore` | int | 是 | 事件发生时左侧得分 |
| `rightScore` | int | 是 | 事件发生时右侧得分 |
| `serveSide` | string | 是 | 发球方 `"left"` / `"right"` |
| `payloadJson` | string | 是 | 事件负载，JSON 字符串 |

**响应** — 无返回体 (`null`)

> 采用批量 upsert（按 `match_id + event_seq` 去重），前端 800ms 防抖后批量提交。

---

### 6.9 获取团体赛阵容

```
GET /api/v1/matches/{id}/team-lineup  🔓
```

**响应** — `TeamMatchLineupVO`

```json
{
  "matchId": "m1",
  "tournamentId": "t1",
  "tournamentType": 0,
  "tournamentName": "2026 团体赛",
  "teamMatchTemplate": 1,
  "relayMemberCount": 6,
  "leftTeam": { "id": "p1", "name": "火箭队", "members": [...] },
  "rightTeam": { "id": "p2", "name": "星火队", "members": [...] },
  "items": [
    {
      "id": "item1",
      "itemCode": "MS",
      "itemName": "男单",
      "playerCount": 1,
      "status": 1,
      "winnerSide": null,
      "childMatchId": "child1",
      "childScoreDisplay": "21:15,21:18",
      "leftMemberIds": ["m1"],
      "rightMemberIds": ["m7"],
      "leftMembers": [{ "id": "m1", "name": "张三" }],
      "rightMembers": [{ "id": "m7", "name": "李四" }]
    }
  ],
  "savedLineupIds": { "left": ["m1","m2","m3","m4","m5","m6"], "right": ["m7","m8","m9","m10","m11","m12"] }
}
```

---

### 6.10 保存团体赛阵容

```
PUT /api/v1/matches/{id}/team-lineup  🔒
```

**请求体**

```json
{
  "items": [
    {
      "itemCode": "MS",
      "leftMemberIds": ["m1"],
      "rightMemberIds": ["m7"]
    },
    {
      "itemCode": "WS",
      "leftMemberIds": ["m2"],
      "rightMemberIds": ["m8"]
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `items[].itemCode` | string | 是 | 项目编码（苏杯：MS/WS/MD/WD/XD；接力：R1..RN） |
| `items[].leftMemberIds` | string[] | 是 | 左侧出场队员 ID 列表 |
| `items[].rightMemberIds` | string[] | 是 | 右侧出场队员 ID 列表 |

**响应** — `TeamMatchLineupVO`，结构同 [6.9](#69-获取团体赛阵容)。

---

### 6.11 开始团体赛子比赛

```
PUT /api/v1/matches/{id}/team-items/{itemCode}/start  🔒
```

> 为团体赛的某一单项（如男单 MS）创建或获取子比赛记录，返回导航到记分板所需的参数。

**响应** — `TeamMatchChildMatchVO`

```json
{
  "parentMatchId": "m1",
  "childMatchId": "child1",
  "itemCode": "MS",
  "itemName": "男单",
  "leftName": "张三",
  "rightName": "李四",
  "bestOf": 3,
  "gamesToWin": 2,
  "pointsToWin": 21,
  "enableDeuce": true,
  "capPoint": 30
}
```

---

### 6.12 结算团体赛

```
PUT /api/v1/matches/{id}/team-match/settle  🔒
```

> 手动结算团体赛（淘汰赛阶段一方达到 3 胜可提前结算，或全部子项结束自动结算）。结算后父比赛 status → 2，胜者晋级。

**响应** — 无返回体 (`null`)

---

### 6.13 主题配置接口（已废弃）

后端 `GET/PUT /api/v1/matches/{id}/theme-config` 已在 `MatchController` 中注释，不再注册为有效 API。当前记分板配色以本地设备存储和前端默认主题为准。

相关历史表和 DTO 暂时保留，不能据此推断接口可用。

---

### 6.14 战报签章

```
PUT /api/v1/matches/{id}/report-seal  🔒
```

> 仅创建者或已认证裁判可操作。用于将已填写完整的战报**封存**：封存后战报不可再修改，比赛不可再重启。幂等：已封存时重复调用直接返回成功。

**前置校验**（任一不满足抛错）：

1. 比赛状态必须为「已结束」(`status=2`/`3`)；
2. 战报元数据必须已通过 `PUT /api/v1/matches/{id}/report-meta`（见附录 A #34）填写完整——签名区须满足下列之一：
   - 双方队长签名 + 裁判签名 + 比赛日期；或
   - 双方队长签名 + 主裁签名 + 副裁签名。

**请求体** — 无

**响应** — 无返回体 (`null`)

**副作用**：在 `match_report_meta.meta_json` 的 `reportState` 中写入 `status="sealed"`、`sealedAt`、`sealedBy`。

> 战报元数据 (`meta_json`) 结构速览：`reportState`（draft/sealed + 封存时间/人）、`matchTypeLabel`、`matchTimeText`、`chiefRefereeName`/`assistantRefereeName`、`notes`、`initialCoinToss`（首局挑边）、`decidingSetCoinToss`（决胜局挑边）、`signatures`（标签）、`reportSignatures`（双方/裁判/主裁/副裁签名 + 日期）。

---

### 6.15 执裁会话锁（比赛独占）

> 同一场比赛同一时间仅允许一个执裁会话持有写锁。锁为每场比赛（`match_record`）一把；不同场次可由不同设备同时执裁。`lockToken` 由前端进入执裁页面时生成（优先 `crypto.randomUUID()`，兜底随机串）。

#### 6.15.1 获取执裁锁

```
POST /api/v1/matches/{id}/lock  🔒
```

**请求体** — `MatchLockReq`

```json
{ "lockToken": "uuid-xxx" }
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `lockToken` | string | **是** | 本次执裁会话 token（不可为空） |

**响应** — `MatchLockVO`

```json
{
  "success": true,
  "editable": true,
  "lockedByUserId": "u1",
  "lockExpireTime": "2026-08-29 12:00:00"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `success` | boolean | 是否抢锁成功（成功时 `editable=true`） |
| `editable` | boolean | 是否可编辑（当前恒等于 `success`） |
| `sameSession` | boolean | 是否同一执裁会话：token 与 userId 双重匹配才为 `true`；跨设备/换会话重取锁成功时为 `false`，前端据此走"新会话"恢复路径 |
| `lockedByUserId` | string | 当前锁持有者用户 ID |
| `lockExpireTime` | string | 锁过期时间（`yyyy-MM-dd HH:mm:ss`） |

抢锁成功条件（满足其一）：锁空闲或已过期；**当前用户与锁持有者是同一用户**（同 token 为同会话幂等重进，`sameSession=true`；换设备/新会话则重置 token，`sameSession=false`）。成功后写入 `locked_by_user_id=当前用户`、`lock_token=本次会话 token`、`lock_expire_time = now + 75s`。抢锁失败时 `success=false`，`lockedByUserId/lockExpireTime` 返回当前持有者信息，前端据此进入只读模式。> ⚠️ 2026-09 互斥锁修复：**创建者不再能无条件接管他人持有的锁**——持有期间任何其他用户（含创建者）都无法抢锁，避免裁判端记分互相覆盖。

#### 6.15.2 续期（心跳）

```
POST /api/v1/matches/{id}/heartbeat  🔒
```

**请求体**同 [6.15.1](#6151-获取执裁锁)，**响应**同 [6.15.1](#6151-获取执裁锁)。仅当 `lockedByUserId==当前用户` 且 `lockToken` 与锁一致时，把过期时间顺延 75s；否则返回 `success=false`（锁已被他人接管或已过期）。

#### 6.15.3 释放执裁锁

```
POST /api/v1/matches/{id}/release  🔒
```

**请求体**同 [6.15.1](#6151-获取执裁锁)，**响应** — 无返回体 (`null`)。仅当 `lockToken` 与当前锁一致时清空三个锁字段；页面卸载时前端自动调用。

---

## 7. 枚举字典

### 7.1 运动类型 (`sportType`)

| 值 | 含义 |
|----|------|
| `0` | 羽毛球 |
| `1` | 排球 |

### 7.2 赛制类型 (`tournamentType`)

| 值 | 含义 |
|----|------|
| `0` | 纯淘汰赛 |
| `1` | 小组赛 + 淘汰赛 |
| `2` | 纯循环赛（双循环由 `roundRobinRounds` 控制） |

### 7.3 参赛者类型 (`participantType`)

| 值 | 含义 |
|----|------|
| `0` | 个人赛（羽毛球单打） |
| `1` | 团体赛（排球/羽毛球团体） |

### 7.4 团体赛模板 (`teamMatchTemplate`)

| 值 | 含义 |
|----|------|
| `0` | 无（非团体赛） |
| `1` | 苏迪曼杯式 5 项（MS/WS/MD/WD/XD） |
| `2` | 接力追分赛 |

### 7.5 赛事 / 比赛状态 (`status`)

| 值 | 含义 |
|----|------|
| `0` | 未开始 |
| `1` | 进行中 |
| `2` | 已结束 |

### 7.6 `match_event` 事件类型

| `eventType` | 含义 | 说明 |
|-------------|------|------|
| `substitution` | 换人 | 排球自由人或普通换人 |
| `timeout` | 暂停 | 技术暂停或教练暂停 |
| `captain_change` | 队长更换 | 场上队长变更 |
| `side_switch` | 换边 | 双方交换场地 |
| `roster_snapshot` | 名单快照 | 记录当前双方在册队员 |
| `lineup_snapshot` | 阵容快照 | 记录当前场上站位 |

### 7.7 排名模板与判据 (`RankingConfig`)

**模板 (`template`)**

| 值 | 适用 |
|----|------|
| `CUSTOM` | 自定义判据序列 |
| `BWF_BADMINTON` | 羽毛球个人赛（BWF 规则） |
| `BADMINTON_COMMON_1` | 羽毛球个人赛常用模板一 |
| `BADMINTON_TEAM_COMMON_1` | 羽毛球团体赛（苏杯五项）常用模板一 |
| `BADMINTON_RELAY_COMMON_1` | 羽毛球团体赛（接力追分）常用模板一 |
| `CAMPUS_VOLLEYBALL` | 校园排球常用模板 |
| `VOLLEYBALL_COMMON_1` | 排球常用模板一 |
| `FIVB_VOLLEYBALL` | FIVB 排球（含 3-1-0 积分制） |

**判据 (`priorities` 取值，`Criterion` 枚举)**

| 值 | 含义 |
|----|------|
| `MATCH_WINS` | 胜场数 |
| `MATCH_WIN_DIFF` | 胜场差 |
| `MATCH_WIN_RATE` | 胜场率 |
| `MATCH_POINTS` | 比赛积分（FIVB 3-1-0） |
| `GAME_WINS` | 胜局数 |
| `NET_GAMES` | 净胜局 |
| `GAME_WIN_RATE` | 胜局率 |
| `NET_POINTS` | 净胜分 |
| `POINT_WIN_RATE` | 得失分率 |
| `HEAD_TO_HEAD` | 直接胜负（两人） |
| `TWO_WAY_HEAD_TO_HEAD` | 两人直接胜负 |
| `MULTI_HEAD_TO_HEAD` | 多人直接胜负（循环比较） |
| `TEAM_ITEM_WINS` / `TEAM_ITEM_NET_WINS` / `TEAM_ITEM_WIN_RATE` | 团体赛场内大分（子项胜场/净胜/率） |
| `TEAM_CHILD_GAME_WINS` / `TEAM_CHILD_NET_GAMES` / `TEAM_CHILD_GAME_WIN_RATE` | 团体赛场内局（子比赛局数汇总） |
| `TEAM_CHILD_NET_POINTS` / `TEAM_CHILD_POINT_WIN_RATE` | 团体赛局内小分 |

**差值/比率 (`mathType`)**

| 值 | 含义 |
|----|------|
| `DIFFERENCE` | 差值比较（净胜） |
| `RATIO` | 比率比较（得失比） |

**退赛策略 (`withdrawPolicy`)**

| 值 | 含义 |
|----|------|
| `NONE` | 不特殊处理 |
| `DELETE_ALL` | 删除退赛者及其全部比赛（羽毛球默认） |
| `FORFEIT_SINGLE` | 退赛场判负但保留其余（排球默认） |

---

## 8. 数据模型速查

### 8.1 数据库表

| 表名 | 实体 | 说明 |
|------|------|------|
| `app_user` | User | 用户（微信 openid 或 Web username、密码哈希、昵称、头像） |
| `tournament` | Tournament | 赛事（名称、赛制、规则参数、赛段规则开关、参赛类型、团体赛模板） |
| `tournament_favorite` | TournamentFavorite | 用户收藏关联 |
| `player` | Player | 参赛选手/队伍 |
| `tournament_team_member` | TournamentTeamMember | 队员详情（球衣号、队长、自由人） |
| `match_record` | MatchRecord | 比赛记录（局分、胜者、淘汰树链接） |
| `team_match_item` | TeamMatchItem | 团体赛子项目（出场名单、子比赛关联） |
| `match_event` | MatchEvent | 排球比赛事件（换人、暂停等） |
| `match_lineup_config` | MatchLineupConfig | 排球每局阵容 + 自由人绑定 |
| `match_report_meta` | MatchReportMeta | 比赛报告元数据（裁判、时间） |
| `tournament_referee_config` | TournamentRefereeConfig | 裁判密码配置 |
| `tournament_referee_grant` | TournamentRefereeGrant | 裁判授权记录 |
| `tournament_round_rule` | TournamentRoundRule | 赛段级别规则（小组赛/淘汰赛轮次） |
| `tournament_ranking_config` | TournamentRankingConfig | 小组排名模板配置（模板 + 优先级 + 锁定） |
| `tournament_qualification_override` | TournamentQualificationOverride | 晋级资格覆盖（人工指定出线名次） |
| `match_theme_config` | MatchThemeConfig | 历史配色主题表（接口已废弃） |
| `global_theme_config` | 无 | 全局配色主题表，仅 schema 保留 |

> `global_theme_config` 表存在于 schema 中，但当前没有实体、Mapper 和有效 API。

### 8.2 前端调用入口速查

| 页面 / 模块 | 调用的接口 |
|-------------|-----------|
| `store/auth.js` | `POST /auth/wechat-login`, `POST /auth/register`, `POST /auth/password-login`, `POST /auth/profile`, `GET /users/me` |
| `pages/index/index.vue` | `GET /tournaments`, `POST/DELETE /tournaments/{id}/favorite` |
| `pages/mine/index.vue` | `GET /tournaments/mine/favorites`, `GET /tournaments/mine/created`, `POST/DELETE favorite` |
| `pages/tournament/mine-list.vue` | `GET /tournaments/mine/favorites` 或 `GET /tournaments/mine/created` |
| `pages/tournament/archived.vue` | `GET /tournaments/mine/archived`, `PUT /tournaments/{id}/unarchive` |
| `pages/create/index.vue` | `POST /tournaments` |
| `pages/create/volleyball.vue` | `POST /tournaments` |
| `pages/tournament/detail.vue` | `GET /tournaments/{id}`, `POST/DELETE favorite`, `PUT archive/unarchive` |
| `pages/tournament/teams.vue` | `GET /tournaments/{id}/teams` |
| `pages/tournament/team-members.vue` | `GET /tournaments/{id}/teams` |
| `pages/tournament/team-edit.vue` | `GET /tournaments/{id}/teams`, `PUT /tournaments/{id}/teams/{participantId}` |
| `pages/tournament/bracket.vue` | `GET /tournaments/{id}/bracket` |
| `pages/tournament/groups.vue` | `GET .../groups`, `GET .../group-standings`, `GET .../bracket`, `POST .../generate-knockout` |
| `pages/tournament/team-match.vue` | `GET /matches/{id}/team-lineup`, `PUT /matches/{id}/team-match/settle` |
| `pages/tournament/team-lineup.vue` | `GET/PUT /matches/{id}/team-lineup` |
| `pages/tournament/team-relay.vue` | `GET /matches/{id}/team-lineup`, `PUT /matches/{id}/finish` |
| `pages/tournament/team-record.vue` | `GET /matches/{id}/team-lineup` |
| `pages/scoreboard/index.vue` | `PUT /matches/{id}/finish` |
| `pages/volleyball/lineup.vue` | `GET/PUT /matches/{id}/lineup-config`, `GET .../bracket` |
| `pages/volleyball/record.vue` | `GET /matches/{id}/record` |
| `pages/volleyball/composables/useScoreboard.js` | `PUT events`, `PUT restart`, `PUT finish`, `GET bracket` |
| `web/admin-web/`（www.eunomia.cc 后台） | `POST /auth/register`, `POST /auth/password-login`, `GET /users/me`, `GET /tournaments?keyword=`, `GET /tournaments/mine/created`, `GET /tournaments/mine/favorites`, `POST /tournaments` |

---

## 附录 A：接口总览

| # | 方法 | 路径 | 认证 | 说明 |
|---|------|------|------|------|
| 1 | `POST` | `/api/v1/auth/wechat-login` | 🔓 | 微信登录 |
| 2 | `POST` | `/api/v1/auth/register` | 🔓 | 账号注册 |
| 3 | `POST` | `/api/v1/auth/password-login` | 🔓 | 密码登录 |
| 4 | `POST` | `/api/v1/auth/profile` | 🔒 | 完善个人信息 |
| 5 | `GET` | `/api/v1/users/me` | 🔒 | 获取当前用户 |
| 6 | `POST` | `/api/v1/auth/pc/qr-code` | 🔓 | 生成扫码登录小程序码 |
| 7 | `GET` | `/api/v1/auth/pc/status` | 🔓 | 轮询扫码状态 |
| 8 | `POST` | `/api/v1/auth/pc/scan` | 🔒 | 上报扫码（小程序） |
| 9 | `POST` | `/api/v1/auth/pc/confirm` | 🔒 | 确认授权（小程序） |
| 10 | `GET` | `/api/v1/tournaments` | 🔓 | 赛事列表（支持 keyword 搜索） |
| 11 | `POST` | `/api/v1/tournaments` | 🔒 | 创建赛事 |
| 12 | `GET` | `/api/v1/tournaments/{id}` | 🔓 | 赛事详情 |
| 13 | `PUT` | `/api/v1/tournaments/{id}/archive` | 🔒 | 归档赛事 |
| 14 | `PUT` | `/api/v1/tournaments/{id}/unarchive` | 🔒 | 取消归档 |
| 15 | `POST` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 收藏赛事 |
| 16 | `DELETE` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 取消收藏 |
| 17 | `GET` | `/api/v1/tournaments/{id}/bracket` | 🔓 | 淘汰赛对阵表 |
| 18 | `GET` | `/api/v1/tournaments/{id}/groups` | 🔓 | 小组赛数据 |
| 19 | `GET` | `/api/v1/tournaments/{id}/group-standings` | 🔓 | 小组赛积分榜 |
| 20 | `GET` | `/api/v1/tournaments/{id}/teams` | 🔓 | 队伍/队员数据 |
| 21 | `POST` | `/api/v1/tournaments/{id}/generate-knockout` | 🔒 | 生成淘汰赛 |
| 22 | `POST` | `/api/v1/tournaments/{id}/referee-auth` | 🔒 | 裁判密码授权 |
| 23 | `GET` | `/api/v1/tournaments/{id}/referees` | 🔒 | 裁判授权列表 |
| 24 | `DELETE` | `/api/v1/tournaments/{id}/referees/{userId}` | 🔒 | 移除裁判授权 |
| 25 | `POST` | `/api/v1/tournaments/{id}/referee-password` | 🔒 | 设置/更新裁判密码 |
| 26 | `GET` | `/api/v1/tournaments/mine/favorites` | 🔒 | 我的收藏 |
| 27 | `GET` | `/api/v1/tournaments/mine/created` | 🔒 | 我创建的赛事 |
| 28 | `GET` | `/api/v1/tournaments/mine/archived` | 🔒 | 我的归档 |
| 29 | `PUT` | `/api/v1/matches/{id}/score` | 🔒 | 更新比赛分数（旧版，已废弃） |
| 30 | `GET` | `/api/v1/matches/{id}/can-operate` | 🔒 | 校验比赛操作权限 |
| 31 | `GET` | `/api/v1/matches/{id}/lineup-config?gameNo=<n>` | 🔓 | 获取阵容配置 |
| 32 | `GET` | `/api/v1/matches/{id}/record` | 🔓 | 获取比赛记录 |
| 33 | `GET` | `/api/v1/matches/{id}/team-lineup` | 🔓 | 获取团体赛阵容 |
| 34 | `PUT` | `/api/v1/matches/{id}/team-lineup` | 🔒 | 保存团体赛阵容 |
| 35 | `PUT` | `/api/v1/matches/{id}/team-items/{itemCode}/start` | 🔒 | 开始团体赛子比赛 |
| 36 | `PUT` | `/api/v1/matches/{id}/team-match/settle` | 🔒 | 结算团体赛 |
| 37 | `PUT` | `/api/v1/matches/{id}/lineup-config` | 🔒 | 保存阵容配置 |
| 38 | `PUT` | `/api/v1/matches/{id}/report-meta` | 🔒 | 保存比赛报告元数据 |
| 39 | `PUT` | `/api/v1/matches/{id}/events` | 🔒 | 批量保存比赛事件 |
| 40 | `PUT` | `/api/v1/matches/{id}/finish` | 🔒 | 结束比赛 |
| 41 | `PUT` | `/api/v1/matches/{id}/restart` | 🔒 | 重新开始比赛 |
| 42 | `GET` | `/api/v1/tournaments/{id}/ranking-config` | 🔓 | 获取小组排名模板配置 |
| 43 | `PUT` | `/api/v1/tournaments/{id}/ranking-config` | 🔒 | 保存小组排名模板配置 |
| 44 | `PUT` | `/api/v1/tournaments/{id}/qualification-overrides` | 🔒 | 保存晋级资格覆盖 |
| 45 | `POST` | `/api/v1/tournaments/{id}/knockout-preview` | 🔒 | 生成淘汰赛预览 |
| 46 | `PUT` | `/api/v1/matches/{id}/report-seal` | 🔒 | 战报签章 |
| 47 | `POST` | `/api/v1/matches/{id}/lock` | 🔒 | 获取比赛执裁锁 |
| 48 | `POST` | `/api/v1/matches/{id}/heartbeat` | 🔒 | 执裁锁心跳续期 |
| 49 | `POST` | `/api/v1/matches/{id}/release` | 🔒 | 释放比赛执裁锁 |
| 50 | `PUT` | `/api/v1/tournaments/{id}/teams/{participantId}` | 🔒 | 创建者编辑队伍（改队名/追加队员） |
