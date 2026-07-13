# ARCHITECTURE.md — 架构地图与目录规范

> **用途**: 让 AI 知道"什么东西该写在哪里"，防止乱建文件、乱放组件。
> **关联**: [[DATABASE.md]] · [[API.md]] · [[BUSINESS_RULES.md]] · [[UI_UX_DESIGN.md]]

---

## 1. 前端结构映射

### 1.1 目录职责边界

```
src/
├── pages/               ← 路由页面（一个页面对应 pages.json 中一条路由）
│   │                      每个页面是一个目录，主文件为 index.vue
│   │                      页面级子组件放在当前页面目录下
│   ├── index/           ← 赛事大厅（首页 Tab）
│   ├── mine/            ← 我的（个人中心 Tab）
│   ├── create/          ← 创建赛事（羽毛球 + 运动选择 + 排球）
│   ├── scoreboard/      ← 羽毛球记分板（横屏）
│   ├── tournament/      ← 赛事详情 + 对阵图 + 小组赛 + 团体赛 + 队伍列表
│   │                       （含 team-match / team-lineup / team-relay 团体赛页面）
│   └── volleyball/      ← 排球模块（记分板 + 轮次填写 + 比赛记录）
│       ├── components/  ← 排球页面专属组件（ScoreboardPad/ScoreboardPhone）
│       └── composables/ ← 排球页面专属 composable（useScoreboard.js）
│
├── components/          ← 全局可复用组件
│   │                      规则：纯展示/通用交互，不含页面级业务逻辑
│   ├── MatchCard.vue          ← 比赛卡片
│   ├── TournamentListCard.vue ← 赛事列表卡片
│   └── ProfileGatePopup.vue   ← 资料补全拦截弹窗
│
├── utils/               ← 工具函数，纯逻辑，不含 Vue 组件
│   ├── request.js             ← HTTP 封装（自动 token / 错误 toast / 环境探测）
│   ├── interaction-guard.js   ← useDelayedTapGate / useActionLock
│   ├── volleyball-team.js     ← 排球队伍工具函数
│   ├── base-page-layout.js    ← 安全区域 + 竖屏页面基础样式
│   └── query.js               ← URL 查询参数构建
│
├── store/               ← 全局状态管理（仅 auth）
│   └── auth.js                ← 登录/Token/资料补全状态
│
├── config/              ← 应用配置常量
├── static/              ← 静态资源（图片等）
│
├── App.vue              ← 根组件
├── main.js              ← 入口文件
├── pages.json           ← 页面路由 + TabBar + 全局样式配置
├── manifest.json        ← uni-app 应用配置
└── uni.scss             ← 全局 SCSS 变量
```

### 1.2 关键设计决策

| 决策 | 说明 |
|------|------|
| **页面=目录** | 每个路由页面一个独立目录，子组件就近放置 |
| **组件分层** | 全局复用 → `components/`，页面专属 → `pages/xxx/components/` |
| **逻辑抽取** | 复杂业务逻辑抽为 composable（如 `useScoreboard.js` 2200+行） |
| **纯函数工具** | `match-state.js` 不依赖 Vue，纯函数，可被任何上下文调用 |
| **无 Vuex/Pinia** | 当前规模不需要，`store/auth.js` 用 `reactive()` 即够 |

---

## 2. 后端层级规约

### 2.1 分层架构

```
┌─────────────────────────────────────────┐
│              Controller 层               │
│  职责：转发请求 + 鉴权，不写业务逻辑      │
│  文件：TournamentController              │
│        MatchController                   │
│        AuthController                    │
├─────────────────────────────────────────┤
│              Service 层                  │
│  职责：核心业务逻辑、事务管理             │
│  文件：TournamentService (接口)          │
│        TournamentServiceImpl (实现)      │
│        MatchService / MatchServiceImpl   │
│        AuthService / AuthServiceImpl     │
│        UserService / UserServiceImpl     │
├─────────────────────────────────────────┤
│              Engine 层（独立算法）        │
│  职责：纯算法，不依赖 Service/Mapper     │
│  文件：BracketEngine.java               │
│        RoundRobinEngine.java            │
├─────────────────────────────────────────┤
│              Mapper 层                   │
│  职责：数据访问，MyBatis-Plus 封装       │
│  文件：13个 Mapper 接口                  │
├─────────────────────────────────────────┤
│              Domain 层                   │
│  entity/  ← 数据库实体（@TableName）    │
│  dto/     ← 前端请求参数                 │
│  vo/      ← 前端响应视图                 │
├─────────────────────────────────────────┤
│              Infrastructure 层           │
│  common/   ← ApiResponse + 全局异常      │
│  security/ ← AuthInterceptor + AuthGuard │
│  config/   ← CORS / MyBatis-Plus / 限流  │
└─────────────────────────────────────────┘
```

### 2.2 各层铁律

| 层 | 允许做的事 | 禁止做的事 |
|----|-----------|-----------|
| **Controller** | 参数校验 (`@Valid`)、调用 Service、返回 `ApiResponse` | 写业务逻辑、直接调 Mapper |
| **Service** | 业务编排、事务管理 (`@Transactional`)、调用 Engine/Mapper | 处理 HTTP 请求/响应 |
| **Engine** | 纯算法计算、返回数据结构 | 访问数据库、依赖 Spring Bean |
| **Mapper** | 数据库 CRUD、自定义 SQL | 写业务判断 |

### 2.3 包结构速查

```
com.scoring.backend/
├── ScoringBackendApplication.java    ← Spring Boot 入口
├── common/
│   ├── ApiResponse.java              ← 统一响应体 {code, message, data}
│   ├── GlobalExceptionHandler.java   ← @ControllerAdvice
│   └── RequestLoggingFilter.java     ← 请求日志
├── config/
│   ├── WebMvcConfig.java             ← CORS + 拦截器注册
│   ├── AuthProperties.java           ← JWT 配置
│   ├── WechatProperties.java         ← 微信配置
│   └── ProductionStartupChecker.java ← 生产环境启动检查
├── controller/
│   ├── AuthController.java           ← /auth/*
│   ├── TournamentController.java     ← /tournaments/*
│   └── MatchController.java          ← /matches/*（含团体赛 lineup + settle）
├── domain/
│   ├── entity/                       ← 13 个实体；数据库共 14 张表（match_theme_config 已废弃）
│   ├── dto/                          ← 12 个请求 DTO
│   └── vo/                           ← 16 个响应 VO
├── engine/
│   ├── BracketEngine.java            ← 淘汰赛种子排表 + 轮空坍缩
│   └── RoundRobinEngine.java         ← 小组循环赛程生成
├── mapper/                           ← 13 个 MyBatis-Plus Mapper
├── security/
│   ├── AuthInterceptor.java          ← 解析 Authorization Header → AuthContext
│   ├── AuthGuard.java                ← requireUserId() 强制鉴权
│   ├── AuthContext.java              ← ThreadLocal 用户 ID
│   └── DevMockAuthFilter.java        ← 开发环境自动注入 mock token（仅 dev）
└── service/
    ├── AuthService.java / impl/
    ├── TournamentService.java / impl/
    ├── MatchService.java / impl/
    ├── TeamMatchService.java / impl/  ← 团体赛阵容编排 + 子比赛创建
    └── UserService.java / impl/
```

---

## 3. 路由总览

### 3.1 前端页面路由（pages.json）

| # | 路径 | 页面 | 强制方向 | 传参 | Tab |
|---|------|------|----------|------|-----|
| 1 | `pages/index/index` | 赛事大厅 | — | 无 | ✅ |
| 2 | `pages/mine/index` | 我的 | — | 无 | ✅ |
| 3 | `pages/create/index` | 创建羽毛球 | — | 无 | — |
| 4 | `pages/create/sport` | 选择运动 | — | 无 | — |
| 5 | `pages/create/volleyball` | 创建排球 | — | 无 | — |
| 6 | `pages/tournament/detail` | 赛事详情 | — | `?id=<tournamentId>` | — |
| 7 | `pages/scoreboard/index` | 羽毛球记分板 | **横屏** | `?matchId=<id>&leftName=...&rightName=...` | — |
| 8 | `pages/volleyball/lineup` | 排球轮次填写 | **竖屏** | `?matchId=<id>` | — |
| 9 | `pages/volleyball/scoreboard` | 排球记分板 | **横屏** | `?matchId=<id>` | — |
| 10 | `pages/volleyball/record` | 比赛记录 | **竖屏** | `?matchId=<id>` | — |
| 11 | `pages/tournament/bracket` | 对阵图 | — | `?id=<tournamentId>` | — |
| 12 | `pages/tournament/groups` | 小组赛/循环赛 | — | `?id=<tournamentId>` | — |
| 13 | `pages/tournament/team-match` | 团体赛主页 | — | `?tournamentId=<id>&matchId=<id>` | — |
| 14 | `pages/tournament/team-lineup` | 团体赛阵容编辑 | — | `?tournamentId=<id>&matchId=<id>` | — |
| 15 | `pages/tournament/team-relay` | 接力赛记分板 | **横屏** | `?tournamentId=<id>&matchId=<id>` | — |
| 16 | `pages/tournament/mine-list` | 我的列表（收藏/创建） | — | `?type=<favorites|created>` | — |
| 17 | `pages/tournament/archived` | 归档赛事 | — | 无 | — |

### 3.2 页面间核心流转

```
创建赛事（选运动类型）
  ├── 羽毛球个人 → pages/create/index → POST /tournaments → 赛事详情
  ├── 羽毛球团体 → pages/create/index → POST /tournaments → 赛事详情
  └── 排球       → pages/create/volleyball → POST /tournaments → 赛事详情
                                                                    ↓
赛事详情 → 对阵图 (bracket) / 小组赛 (groups)
              ↓                        ↓
         点击比赛卡片              点击比赛卡片
              ↓                        ↓
    ┌─ 个人赛 → 记分板           排球轮次填写 (lineup)
    │                                  ↓ 确认首发
    └─ 团体赛 → team-match         排球记分板 (scoreboard)
                  ↓                      ↓ 比赛结束
               team-lineup (排阵)     比赛记录 (record)
                  ↓
               ┌─ 苏杯 → 子比赛 → scoreboard → 回 team-match
               └─ 接力 → team-relay (记分板) → 同步结算

归档: 我的 → mine-list → archived → 取消归档
```

### 3.3 后端 API 路由

| 方法 | 路径 | Controller | 认证 |
|------|------|-----------|------|
| `POST` | `/api/v1/auth/wechat-login` | AuthController | 🔓 |
| `POST` | `/api/v1/auth/profile` | AuthController | 🔒 |
| `GET` | `/api/v1/users/me` | AuthController | 🔒 |
| `GET` | `/api/v1/tournaments` | TournamentController | 🔓 |
| `POST` | `/api/v1/tournaments` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/{id}` | TournamentController | 🔓 |
| `POST` | `/api/v1/tournaments/{id}/favorite` | TournamentController | 🔒 |
| `DELETE` | `/api/v1/tournaments/{id}/favorite` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/mine/favorites` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/mine/created` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/{id}/bracket` | TournamentController | 🔓 |
| `GET` | `/api/v1/tournaments/{id}/groups` | TournamentController | 🔓 |
| `GET` | `/api/v1/tournaments/{id}/group-standings` | TournamentController | 🔓 |
| `GET` | `/api/v1/tournaments/{id}/teams` | TournamentController | 🔓 |
| `POST` | `/api/v1/tournaments/{id}/generate-knockout` | TournamentController | 🔒 |
| `POST` | `/api/v1/tournaments/{id}/referee-auth` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/{id}/referees` | TournamentController | 🔒 |
| `DELETE` | `/api/v1/tournaments/{id}/referees/{userId}` | TournamentController | 🔒 |
| `POST` | `/api/v1/tournaments/{id}/referee-password` | TournamentController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/score` | MatchController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/finish` | MatchController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/restart` | MatchController | 🔒 |
| `GET` | `/api/v1/matches/{id}/lineup-config?gameNo=<n>` | MatchController | 🔓 |
| `PUT` | `/api/v1/matches/{id}/lineup-config` | MatchController | 🔒 |
| `GET` | `/api/v1/matches/{id}/record` | MatchController | 🔓 |
| `PUT` | `/api/v1/matches/{id}/events` | MatchController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/report-meta` | MatchController | 🔒 |
| `PUT` | `/api/v1/tournaments/{id}/archive` | TournamentController | 🔒 |
| `PUT` | `/api/v1/tournaments/{id}/unarchive` | TournamentController | 🔒 |
| `GET` | `/api/v1/tournaments/mine/archived` | TournamentController | 🔒 |
| `GET` | `/api/v1/matches/{id}/team-lineup` | MatchController | 🔓 |
| `PUT` | `/api/v1/matches/{id}/team-lineup` | MatchController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/team-items/{itemCode}/start` | MatchController | 🔒 |
| `PUT` | `/api/v1/matches/{id}/team-match/settle` | MatchController | 🔒 |

> 完整接口文档见 [[API.md]]，共 34 个有效接口（不含已废弃的主题配置接口）

---

## 4. 数据流模式

### 4.1 前端请求链路

```
页面组件
  → utils/request.js
    → 自动注入 Authorization Header (从 Storage 读 token)
    → uni.request()
    → 校验 res.statusCode === 200
    → 校验 body.code === 0
    → resolve(body.data)                    ← 成功
    → uni.showToast(body.message) + reject  ← 失败
  → 页面组件拿到纯净 data
```

### 4.2 鉴权链路

```
前端 ensureAuth()
  → uni.login() 获取微信 code
  → POST /auth/wechat-login { code }
  → 后端 fetchOpenid(微信API) → 查/创 User → signToken(JWT HMAC256)
  → 前端存储 token 到 Storage

后续每次请求:
  → AuthInterceptor.preHandle()
    → 解析 Authorization: Bearer <token>
    → authService.verifyToken()
    → AuthContext.setUserId()
  → Controller 中 AuthGuard.requireUserId() 强制鉴权
  → afterCompletion → AuthContext.clear()
```

### 4.3 排球记分板数据流

```
Backend REST API
  ↓ GET bracket / lineup-config / record
match-state.js（纯函数，Storage 缓存，matchId 为 key）
  ↓ createEmptyMatchState() / loadMatchState()
useScoreboard.js（2200+ 行 composable，全部业务逻辑）
  ↓ reactive(ctx) — ref 自动解包
scoreboard.vue（薄路由层，v-if="ctx.isTablet" 设备分流）
  ├── ScoreboardPhone.vue（手机 UI）
  └── ScoreboardPad.vue（Pad UI）
```

---

## 5. 部署架构

```
微信小程序 ←→ Nginx (:443) ←→ Spring Boot (:8080) ←→ MySQL 8.0 (:3306)
                  │
            api.eunomia.cc
```

- **无 Docker**，直接 jar 部署
- 生产服务器: `47.101.156.6`
- 后端部署脚本: `backend/deploy/deploy-prod.sh`
- Nginx 配置: `backend/deploy/nginx/scoring-api.conf.example`
- systemd 服务: `backend/deploy/systemd/scoring-backend.service`
