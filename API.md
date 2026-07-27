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

微信小程序通过 `wx.login()` 获取临时 code，调用 [POST /auth/wechat-login](#41-微信登录) 换取 JWT。Web/H5 可使用 [POST /auth/register](#44-账号注册) 或 [POST /auth/password-login](#45-密码登录) 获取 JWT。

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

**排名规则**：胜场数 → 净胜局 → 净胜分 → 种子排名

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

## 6. 比赛接口

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

---

## 附录 A：接口总览

| # | 方法 | 路径 | 认证 | 说明 |
|---|------|------|------|------|
| 1 | `POST` | `/api/v1/auth/wechat-login` | 🔓 | 微信登录 |
| 2 | `POST` | `/api/v1/auth/register` | 🔓 | 账号注册 |
| 3 | `POST` | `/api/v1/auth/password-login` | 🔓 | 密码登录 |
| 4 | `POST` | `/api/v1/auth/profile` | 🔒 | 完善个人信息 |
| 5 | `GET` | `/api/v1/users/me` | 🔒 | 获取当前用户 |
| 6 | `GET` | `/api/v1/tournaments` | 🔓 | 赛事列表（支持 keyword 搜索） |
| 7 | `POST` | `/api/v1/tournaments` | 🔒 | 创建赛事 |
| 8 | `GET` | `/api/v1/tournaments/{id}` | 🔓 | 赛事详情 |
| 9 | `PUT` | `/api/v1/tournaments/{id}/archive` | 🔒 | 归档赛事 |
| 10 | `PUT` | `/api/v1/tournaments/{id}/unarchive` | 🔒 | 取消归档 |
| 11 | `POST` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 收藏赛事 |
| 12 | `DELETE` | `/api/v1/tournaments/{id}/favorite` | 🔒 | 取消收藏 |
| 13 | `GET` | `/api/v1/tournaments/{id}/bracket` | 🔓 | 淘汰赛对阵表 |
| 14 | `GET` | `/api/v1/tournaments/{id}/groups` | 🔓 | 小组赛数据 |
| 15 | `GET` | `/api/v1/tournaments/{id}/group-standings` | 🔓 | 小组赛积分榜 |
| 16 | `GET` | `/api/v1/tournaments/{id}/teams` | 🔓 | 队伍/队员数据 |
| 17 | `POST` | `/api/v1/tournaments/{id}/generate-knockout` | 🔒 | 生成淘汰赛 |
| 18 | `POST` | `/api/v1/tournaments/{id}/referee-auth` | 🔒 | 裁判密码授权 |
| 19 | `GET` | `/api/v1/tournaments/{id}/referees` | 🔒 | 裁判授权列表 |
| 20 | `DELETE` | `/api/v1/tournaments/{id}/referees/{userId}` | 🔒 | 移除裁判授权 |
| 21 | `POST` | `/api/v1/tournaments/{id}/referee-password` | 🔒 | 设置/更新裁判密码 |
| 22 | `GET` | `/api/v1/tournaments/mine/favorites` | 🔒 | 我的收藏 |
| 23 | `GET` | `/api/v1/tournaments/mine/created` | 🔒 | 我创建的赛事 |
| 24 | `GET` | `/api/v1/tournaments/mine/archived` | 🔒 | 我的归档 |
| 25 | `PUT` | `/api/v1/matches/{id}/score` | 🔒 | 更新比赛分数（旧版，已废弃） |
| 26 | `GET` | `/api/v1/matches/{id}/can-operate` | 🔒 | 校验比赛操作权限 |
| 27 | `GET` | `/api/v1/matches/{id}/lineup-config?gameNo=<n>` | 🔓 | 获取阵容配置 |
| 28 | `GET` | `/api/v1/matches/{id}/record` | 🔓 | 获取比赛记录 |
| 29 | `GET` | `/api/v1/matches/{id}/team-lineup` | 🔓 | 获取团体赛阵容 |
| 30 | `PUT` | `/api/v1/matches/{id}/team-lineup` | 🔒 | 保存团体赛阵容 |
| 31 | `PUT` | `/api/v1/matches/{id}/team-items/{itemCode}/start` | 🔒 | 开始团体赛子比赛 |
| 32 | `PUT` | `/api/v1/matches/{id}/team-match/settle` | 🔒 | 结算团体赛 |
| 33 | `PUT` | `/api/v1/matches/{id}/lineup-config` | 🔒 | 保存阵容配置 |
| 34 | `PUT` | `/api/v1/matches/{id}/report-meta` | 🔒 | 保存比赛报告元数据 |
| 35 | `PUT` | `/api/v1/matches/{id}/events` | 🔒 | 批量保存比赛事件 |
| 36 | `PUT` | `/api/v1/matches/{id}/finish` | 🔒 | 结束比赛 |
| 37 | `PUT` | `/api/v1/matches/{id}/restart` | 🔒 | 重新开始比赛 |
