# 羽球/排球赛事记分系统 — 接口文档

> 版本：v1 | 更新时间：2026-06-15 | 后端：Spring Boot 3.3.5 | 前端：uni-app (Vue 3)

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

微信小程序通过 `wx.login()` 获取临时 code，调用 [POST /auth/wechat-login](#41-微信登录) 换取 JWT。

Web 开发环境由 `DevMockAuthFilter` 自动注入模拟 token，无需真实微信登录。

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
    "knockoutSlots": 8,
    "qualifiersPerGroup": 2,
    "bestOf": 3,
    "gamesToWin": 2,
    "pointsToWin": 21,
    "enableDeuce": true,
    "capPoint": 30,
    "favoriteCount": 12,
    "createTime": "2026-06-10T08:00:00",
    "favorite": true,
    "creator": false
  }
]
```

> `favorite` 和 `creator` 为当前登录用户的瞬态标记（未登录均为 `false`）。

---

### 5.2 创建赛事

```
POST /api/v1/tournaments  🔒
```

#### 5.2.1 羽毛球赛事

```json
{
  "name": "string (必填)",
  "location": "string (选填)",
  "sportType": 0,
  "tournamentType": 0,
  "knockoutSlots": 8,
  "qualifiersPerGroup": 2,
  "players": [
    { "name": "张三", "seed": 1 },
    { "name": "李四", "seed": 2 }
  ],
  "rule": {
    "bestOf": 3,
    "gamesToWin": 2,
    "pointsToWin": 21,
    "enableDeuce": true,
    "capPoint": 30
  }
}
```

#### 5.2.2 排球赛事

```json
{
  "name": "string (必填)",
  "location": "string (选填)",
  "sportType": 1,
  "tournamentType": 0,
  "knockoutSlots": 4,
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
    "enableDeuce": true,
    "capPoint": 30
  }
}
```

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

**排名规则**：胜场数 → 净胜局 → 净胜分 → 种子排名

---

### 5.11 生成淘汰赛

```
POST /api/v1/tournaments/{id}/generate-knockout  🔒
```

> 仅用于「小组赛+淘汰赛」赛制。小组赛全部结束后，根据积分榜晋级者生成淘汰赛对阵。

**响应** — 无返回体 (`null`)

---

## 6. 比赛接口

### 6.1 结束比赛

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

### 6.2 重新开始比赛

```
PUT /api/v1/matches/{id}/restart  🔒
```

**响应** — 无返回体 (`null`)

> 重置比赛为初始状态，清除所有比分、事件、阵容配置和主题配置。
> 如果胜者已晋级到下一场，同时清除下一场的晋级者。

---

### 6.3 更新比赛分数

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

> ⚠️ 此接口已被 [6.1 结束比赛](#61-结束比赛) 替代，前端当前未直接调用。

---

### 6.4 获取阵容配置

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

### 6.5 保存阵容配置

```
PUT /api/v1/matches/{id}/lineup-config  🔒
```

**请求体** — 结构同 [6.4 获取阵容配置](#64-获取阵容配置) 中的 `config`，外加 `gameNo` 和 `serveSide`：

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

### 6.6 获取比赛记录

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

### 6.7 批量保存比赛事件

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

### 6.8 获取主题配置

```
GET /api/v1/matches/{id}/theme-config  🔓
```

**响应** — `MatchThemeConfigVO`

```json
{
  "matchId": "m1",
  "theme": { "themeBase": "#1a1a2e", ... },
  "phoneTheme": {
    "themeBase": "#1a1a2e",
    "themeBg": "#16213e",
    "themeCard": "#0f3460",
    "themeText": "#e94560",
    "themeAccent": "#533483",
    "themeBorder": "#2a2a4a",
    "themeSuccess": "#00b894",
    "themeDanger": "#d63031",
    "themeWarning": "#fdcb6e",
    "themeInfo": "#74b9ff",
    "themeLight": "#dfe6e9",
    "themeDark": "#2d3436",
    "themeOverlay": "#00000080",
    "themeShadow": "#00000040"
  },
  "padTheme": { ... }
}
```

> `theme` 为旧版遗留字段，实际使用时取 `phoneTheme` 或 `padTheme`。

---

### 6.9 保存主题配置

```
PUT /api/v1/matches/{id}/theme-config  🔒
```

**请求体**

```json
{
  "device": "phone",
  "theme": {
    "themeBase": "#1a1a2e",
    "themeBg": "#16213e",
    "themeCard": "#0f3460",
    "themeText": "#e94560",
    "themeAccent": "#533483",
    "themeBorder": "#2a2a4a",
    "themeSuccess": "#00b894",
    "themeDanger": "#d63031",
    "themeWarning": "#fdcb6e",
    "themeInfo": "#74b9ff",
    "themeLight": "#dfe6e9",
    "themeDark": "#2d3436",
    "themeOverlay": "#00000080",
    "themeShadow": "#00000040"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `device` | string | 是 | `"phone"` 或 `"pad"` |
| `theme` | object | 是 | 14 个 CSS 颜色变量 |

**响应** — 无返回体 (`null`)

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

### 7.3 赛事 / 比赛状态 (`status`)

| 值 | 含义 |
|----|------|
| `0` | 未开始 |
| `1` | 进行中 |
| `2` | 已结束 |

### 7.4 `match_event` 事件类型

| `eventType` | 含义 | 说明 |
|-------------|------|------|
| `substitution` | 换人 | 排球自由人或普通换人 |
| `timeout` | 暂停 | 技术暂停或教练暂停 |
| `captain_change` | 队长更换 | 场上队长变更 |
| `side_switch` | 换边 | 双方交换场地 |
| `roster_snapshot` | 名单快照 | 记录当前双方在册队员 |
| `lineup_snapshot` | 阵容快照 | 记录当前场上站位 |

---

## 8. 数据模型速查

### 8.1 数据库表

| 表名 | 实体 | 说明 |
|------|------|------|
| `app_user` | User | 用户（微信 openid、昵称、头像） |
| `tournament` | Tournament | 赛事（名称、赛制、规则参数） |
| `tournament_favorite` | TournamentFavorite | 用户收藏关联 |
| `player` | Player | 参赛选手/队伍 |
| `tournament_team_member` | TournamentTeamMember | 队员详情（球衣号、队长、自由人） |
| `match_record` | MatchRecord | 比赛记录（局分、胜者、淘汰树链接） |
| `match_event` | MatchEvent | 排球比赛事件（换人、暂停等） |
| `match_lineup_config` | MatchLineupConfig | 排球每局阵容 + 自由人绑定 |
| `match_theme_config` | MatchThemeConfig | 记分板配色主题 |

### 8.2 前端调用入口速查

| 页面 / 模块 | 调用的接口 |
|-------------|-----------|
| `store/auth.js` | `POST /auth/wechat-login`, `POST /auth/profile`, `GET /users/me` |
| `pages/index/index.vue` | `GET /tournaments`, `POST/DELETE /tournaments/{id}/favorite` |
| `pages/mine/index.vue` | `GET /tournaments/mine/favorites`, `GET /tournaments/mine/created`, `POST/DELETE favorite` |
| `pages/create/index.vue` | `POST /tournaments` |
| `pages/create/volleyball.vue` | `POST /tournaments` |
| `pages/tournament/detail.vue` | `GET /tournaments/{id}`, `POST/DELETE favorite` |
| `pages/tournament/bracket.vue` | `GET /tournaments/{id}/bracket` |
| `pages/tournament/groups.vue` | `GET .../groups`, `GET .../group-standings`, `GET .../bracket`, `POST .../generate-knockout` |
| `pages/scoreboard/index.vue` | `PUT /matches/{id}/finish` |
| `pages/volleyball/lineup.vue` | `GET/PUT /matches/{id}/lineup-config`, `GET .../bracket` |
| `pages/volleyball/record.vue` | `GET /matches/{id}/record` |
| `pages/volleyball/composables/useScoreboard.js` | `GET/PUT theme-config`, `PUT events`, `PUT restart`, `PUT finish`, `GET bracket` |

---

## 附录 A：接口总览

| # | 方法 | 路径 | 认证 | 说明 |
|---|------|------|------|------|
| 1 | `POST` | `/api/v1/auth/wechat-login` | 🔓 | 微信登录 |
| 2 | `POST` | `/api/v1/auth/profile` | 🔒 | 完善个人信息 |
| 3 | `GET` | `/api/v1/users/me` | 🔒 | 获取当前用户 |
| 4 | `GET` | `/api/v1/tournaments` | 🔓 | 赛事列表（支持 keyword 搜索） |
| 5 | `POST` | `/api/v1/tournaments` | 🔒 | 创建赛事 |
| 6 | `GET` | `/api/v1/tournaments/{id}` | 🔓 | 赛事详情 |
| 7 | `POST` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 收藏赛事 |
| 8 | `DELETE` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 取消收藏 |
| 9 | `GET` | `/api/v1/tournaments/mine/favorites` | 🔒 | 我的收藏 |
| 10 | `GET` | `/api/v1/tournaments/mine/created` | 🔒 | 我创建的 |
| 11 | `GET` | `/api/v1/tournaments/{id}/bracket` | 🔓 | 淘汰赛对阵表 |
| 12 | `GET` | `/api/v1/tournaments/{id}/groups` | 🔓 | 小组赛数据 |
| 13 | `GET` | `/api/v1/tournaments/{id}/group-standings` | 🔓 | 小组赛积分榜 |
| 14 | `POST` | `/api/v1/tournaments/{id}/generate-knockout` | 🔒 | 生成淘汰赛 |
| 15 | `PUT` | `/api/v1/matches/{id}/score` | 🔒 | 更新比赛分数（旧版，已废弃） |
| 16 | `PUT` | `/api/v1/matches/{id}/finish` | 🔒 | 结束比赛 |
| 17 | `PUT` | `/api/v1/matches/{id}/restart` | 🔒 | 重新开始比赛 |
| 18 | `GET` | `/api/v1/matches/{id}/lineup-config` | 🔓 | 获取阵容配置 |
| 19 | `PUT` | `/api/v1/matches/{id}/lineup-config` | 🔒 | 保存阵容配置 |
| 20 | `GET` | `/api/v1/matches/{id}/record` | 🔓 | 获取比赛记录 |
| 21 | `PUT` | `/api/v1/matches/{id}/events` | 🔒 | 批量保存比赛事件 |
| 22 | `GET` | `/api/v1/matches/{id}/theme-config` | 🔓 | 获取主题配置 |
| 23 | `PUT` | `/api/v1/matches/{id}/theme-config` | 🔒 | 保存主题配置 |
