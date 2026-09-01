# 🏐 羽球/排球赛事记分 — 微信小程序

> 为校园班赛、院系比赛及社会俱乐部量身定制的轻量级计分与赛程管理工具。支持**羽毛球**和**排球**双运动类型。

---

## 目录

1. [项目概览](#1-项目概览)
2. [技术栈架构](#2-技术栈架构)
3. [核心功能](#3-核心功能)
4. [开发历程与决策复盘](#4-开发历程与决策复盘)
5. [快速启动指南](#5-快速启动指南)
6. [项目文档体系](#6-项目文档体系)
7. [项目结构](#7-项目结构)
8. [后续规划](#8-后续规划)

---

## 1. 项目概览

### 背景与痛点

在高中、大学的班级/院系羽毛球和排球比赛中，长期依赖**手写记分表 + Excel 排赛程**的传统模式：

| 痛点 | 传统做法 | 后果 |
|------|---------|------|
| 计分效率低 | 纸质记分表，边打边划正字 | 容易记错、丢失、事后无法复盘 |
| 赛程排布易出错 | Excel 手动拉表，人工核对晋级 | 轮空处理容易遗漏，晋级路径不清晰 |
| 赛后统计难 | 各场比分散落各处 | 无法快速查看完整赛果和历史数据 |
| 排球操作复杂 | 手动跟踪轮转、自由人换人、队长变更 | 裁判负担重，过程记录基本不可能 |

### 解决方案

一款微信小程序，提供完整的赛事管理闭环：

- **实时记分牌** — 横屏防误触 UI + 快照栈撤销 + 本地离线容错
- **智能赛程管理** — 淘汰赛自动排表 + 轮空坍缩 + 赛果回传自动晋级
- **排球专业支持** — 阵容管理 + 自由人自动换人 + 队长指定 + 比赛事件流 + 赛后报告 PDF
- **双运动共享骨架** — 赛事大厅、对阵图、小组赛等基础设施在羽毛球和排球间共享
- **Web 赛事后台** — [www.eunomia.cc](https://www.eunomia.cc) 独立 Vue 3 后台：桌面端注册登录、赛事搜索、完整赛制创建（与小程序创建能力对齐），与小程序共用账号和数据

> 打完一场比赛 → 记分牌同步结算 → 胜者自动晋级下一轮 → 对阵图实时刷新。形成完整的**物理闭环**。

### 当前状态

| 维度 | 羽毛球 | 排球 |
|------|--------|------|
| 淘汰赛 | ✅ 已完成 | ✅ 已完成 |
| 小组赛+淘汰赛 | ✅ 已完成 | ✅ 已完成 |
| 实时记分板 | ✅ 已完成 | ✅ 已完成 |
| 阵容/轮转管理 | — | ✅ 已完成 |
| 自由人系统 | — | ✅ 已完成 |
| 队长管理 | — | ✅ 已完成 |
| 事件同步 | — | ✅ 已完成 |
| 赛后记录 PDF | — | ✅ 已完成 |
| 配色主题 | ✅ 已完成 | ✅ Phone/Pad 独立主题 |
| 裁判权限 | — | ✅ 已完成 |

---

## 2. 技术栈架构

### 技术选型一览

| 层级 | 技术 | 版本 | 选型理由 |
|------|------|------|---------|
| 前端框架 | **uni-app (Vue 3 + Vite)** | 3.0.0-alpha | 一套代码编译微信小程序 + H5，跨端成本极低 |
| 前端语言 | JavaScript (Vue Composition API) | — | 配合 uni-app 生态 |
| Web 后台 | **Vue 3 + vue-router + Vite** (`admin-web/`) | 3.4 / 4.4 / 5.2 | 独立静态站点部署 www.eunomia.cc，无 UI 库，fetch + localStorage 最小实现 |
| 后端框架 | **Spring Boot** | 3.3.5 | 主流 Java 企业级框架，生态成熟 |
| JDK | **Java 17** | 17 (LTS) | 长期支持版本 |
| ORM | **MyBatis-Plus** | 3.5.7 | Lambda QueryWrapper + 自动填充 + 雪花 ID |
| 数据库 | **MySQL** | 8.0 | 轻量结构化数据 |
| 数据库迁移 | **Flyway** | — | 19 个迁移版本，版本化 schema 管理 |
| 工具库 | **Hutool** | 5.8.30 | 集合、字符串、ID 生成等一站式工具 |
| 构建 | Maven | 3.9.9 | 标准 Java 项目构建 |

### 系统架构图

```
┌──────────────────────────────────────────────────────────┐
│                  微信小程序端 (uni-app)                     │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌────────────┐  │
│  │ 赛事大厅  │ │ 记分板   │ │ 对阵图   │ │ 排球模块    │  │
│  │ index    │ │scoreboard│ │ bracket  │ │ volleyball │  │
│  └────┬─────┘ └────┬─────┘ └────┬─────┘ └─────┬──────┘  │
│       │            │            │              │          │
│       └────────────┼────────────┼──────────────┘          │
│                    │            │                          │
│            request.js (uni.request)                        │
│         Storage 离线缓存兜底                                │
└────────────────────┼────────────────────────────────────────┘
                     │ HTTP / JSON · Bearer JWT
                     │
┌────────────────────┼────────────────────────────────────────┐
│             Spring Boot 3.3.5                                │
│                    │                                         │
│  ┌─────────────────┼──────────────────────────────┐         │
│  │           Controller 层 (3个)                    │         │
│  │  Auth · Tournament · Match                      │         │
│  └─────────────────┼──────────────────────────────┘         │
│  ┌─────────────────┼──────────────────────────────┐         │
│  │            Service 层 (5个)                      │         │
│  │  Auth · Tournament · Match · TeamMatch · User   │         │
│  │              ⬇                ⬇                  │         │
│  │  BracketEngine · RoundRobinEngine (核心算法)     │         │
│  └─────────────────┼──────────────────────────────┘         │
│  ┌─────────────────┼──────────────────────────────┐         │
│  │           Mapper 层 (16个 MyBatis-Plus)         │         │
│  └─────────────────┼──────────────────────────────┘         │
│                    │                                         │
│  ┌─────────────────┼──────────────────────────────┐         │
│  │   GlobalExceptionHandler · AuthInterceptor      │         │
│  │   RequestLoggingFilter · RequestRateLimiter     │         │
│  └────────────────────────────────────────────────┘         │
└────────────────────┼────────────────────────────────────────┘
                     │
             ┌───────┴───────┐
             │  MySQL 8.0    │
             │ scoring_mvp   │
             │ (17 张表)      │
             └───────────────┘
```

> 除上图主链路外，还有两个独立前端：**`admin-web/`（Vue 3 后台，www.eunomia.cc）** 走 nginx 同源反代 `/api/` 调用同一后端；**`docs/product-guide/`（纯静态产品介绍页，product.eunomia.cc）** 不调 API。详见 [`docs/ADMIN_WEB.md`](docs/ADMIN_WEB.md)。

---

## 3. 核心功能

### 模块一：智能计分板

#### 羽毛球记分板

横屏双栏布局，深色主题 (`#1A2A3A`) + 橙色强调色 (`#FF8C00`)：
- 点击分数区域直接 +1 分，发球权自动跟随
- 快照栈撤销（History Stack，最多 40 条），每次操作前自动保存完整状态
- 上帝模式（God Mode）允许 ±1 自由调整分数
- Storage 离线容错：按 matchId 区分缓存，断网 100% 可用
- 换边追踪：`sidesSwapped` 标记位贯穿快照、缓存、同步全链路

#### 排球记分板

横屏三栏布局（左队名单 | 中央比赛区 | 右队名单），Phone/Pad 独立组件适配：
- 核心逻辑集中在 `useScoreboard.js` composable (2200+ 行)
- Phone 和 Pad 共享同一套业务逻辑，仅 CSS 不同
- 比分区 + 小球场轮转面板 + 双方名单同屏协同
- 加分自动触发：发球权切换 → 顺时针轮转 → 自由人位置跟随 → 队长检查

### 模块二：赛事大厅与赛程管理

#### 一键创建赛事

羽毛球：粘贴选手名单（空格/换行/逗号分隔）→ 一键生成签表
排球：逐队添加球员（6-12人/队，含球衣号、队长、自由人标记）→ 校验后生成

#### BracketEngine — 淘汰赛排表算法

```
输入: n 个选手
Step 1 · 容量对齐 → n 向上取整到 2 的幂 p
Step 2 · 递归种子排序 → 相邻种子之和 = p + 1
Step 3 · 轮空坍缩 → 种子编号 > n 的位置自动判胜
Step 4 · 晋级链表 → next_match_id + next_match_slot 串联淘汰树
```

采用"链表指针"式设计：每场比赛独立更新，多场并行互不干扰。

#### RoundRobinEngine — 小组循环赛

- 自动生成组内循环赛程
- 积分榜计算：胜场 → 净胜局 → 净胜分 → H2H → 名字序
- 蛇形交叉排列出线者，避免同组首轮相遇

### 模块三：排球专业功能

#### 阵容管理系统

- 每局赛前填写首发 6 人站位（4/3/2, 5/6/1 固定两行三列）
- 自由人绑定：选副攻对角位 → 为每对副攻绑定自由人（可选/单绑/双绑）
- 局间自动继承上一局阵容，支持手动调整
- 换边操作完整交换两侧全部状态

#### 自由人自动联动

- 前排（4/3/2号位）永远不用自由人
- 1号位：发球方用副攻本人，失发球权后换自由人
- 5/6号位（后排非发球位）：自动换自由人
- 运行时追踪 `liberoRuntime`：role slot index + 当前占位球员

#### 场上队长

- 原始队长在场 → 自动设为场上队长
- 原始队长被换下 → 弹窗从场上 6 人中重新指定
- 原始队长回场 → 自动恢复
- 队长标识使用独立青蓝色，不与橙色体系混淆

#### 比赛事件流

- 6 种事件类型：换人、暂停、队长变更、换边、名单快照、阵容快照
- 前端 800ms 防抖批量提交，后端 `(match_id, event_seq)` 幂等 upsert
- 同步失败不阻塞现场操作，结算前强制追平

#### 赛后记录 PDF

- 纸质记录表风格渲染（米色背景 + 衬线字体）
- 含赛事信息、总比分、每局比分、双方名单、轮转图、暂停记录、签名区
- H5 端 `window.print()` 导出 PDF

---

## 4. 开发历程与决策复盘

### Phase 0 — 技术选型与基调奠定

| 决策点 | 选择 | 放弃的方案 | 理由 |
|--------|------|-----------|------|
| 前端框架 | uni-app (Vue 3) | 原生微信小程序 / Taro | 团队 Vue 背景，一套代码多端编译 |
| 后端架构 | 模块化单体 | 微服务 | MVP 阶段业务简单，模块化保留拆分可能 |
| ORM | MyBatis-Plus | JPA | Lambda QueryWrapper 可读性强，灵活性高 |
| 数据库 | MySQL 8.0 | PostgreSQL | 团队最熟悉 |

**确立的工程原则：**
- 统一 `ApiResponse<T>` 响应体：`{ code, message, data }`
- 全局异常拦截：业务异常 400，状态异常 500
- Controller 只做转发+鉴权，Service 写业务，Engine 写纯算法

### Phase 1 — 羽毛球 MVP

采用 MVP 策略，聚焦羽毛球单打淘汰赛计分闭环：
- 自底向上开发：先记分板（不依赖后端）→ 再赛事创建 → 再对阵图
- 引入快照栈撤销 + Storage 离线容错 + 上帝模式
- 封装 `request.js`：自动环境探测、token 注入、错误 toast

### Phase 2 — 后端算法破局

淘汰赛排表算法迭代 4 版：顺序排列 → 种子排序 → 轮空坍缩 → 晋级链表。编写 9 个单元测试用例覆盖边界场景。

### Phase 3 — 全栈贯通

联调解决 CORS 跨域、自动建库建表、赛果同步闭环、记分板刷新机制、换边安全修复等问题。

### Phase 4 — 排球模块全链路

详见 [`DevelopmentLog.md`](DevelopmentLog.md)，主要里程碑：
- **2026-06-06**: 排球第一阶段 — 赛事模型、创建页、记分板三栏布局、轮次填写
- **2026-06-09**: 记分板第二阶段 — 页面拆分（竖屏 lineup + 横屏 scoreboard）、Storage 防套娃
- **2026-06-10**: 自由人模块收口 — 每局局前配置 + 记分页自动联动 + 场上队长事件流
- **2026-06-10**: Pad 适配 — 六级尺寸带 + 组件分仓（ScoreboardPhone / ScoreboardPad）
- **2026-06-12**: 换边语义重构 — `screenLeftParticipantSide` 替换 `displaySideSwapped`
- **2026-06-13**: 调色链路 — Phone/Pad 独立默认主题 + 14 色 CSS 变量
- **2026-06-15**: 记录页收口 — 比赛报告 PDF 导出 + 赛前元数据采集
- **2026-06-18**: 鉴权收口 + 测试补齐 — DevMockAuthFilter 限 dev、资料补全校验、写权限回归

---

## 5. 快速启动指南

### 环境要求

| 工具 | 版本要求 |
|------|---------|
| Node.js | ≥ 18 |
| JDK | 17 |
| MySQL | 8.0 |
| Maven | 3.9+（项目已内置于 `.tools/`） |
| 微信开发者工具 | 最新稳定版 |

### 后端启动

```bash
cd backend

# 配置环境变量（或用 application.yml 默认值）
# 必需: DB_URL, DB_USERNAME, DB_PASSWORD, JWT_SECRET

# 启动
../.tools/apache-maven-3.9.9/bin/mvn spring-boot:run
# → http://127.0.0.1:8080
```

> 首次启动时 Flyway 自动执行数据库迁移，创建全部 17 张表。

### 前端启动

```bash
npm install                              # 首次
npm run dev:mp-weixin                    # 微信小程序开发模式
# 微信开发者工具 → 导入 dist/dev/mp-weixin/

# H5 开发
npm run dev:h5
# Vite 自动代理 /api → http://127.0.0.1:8080
```

### Web 后台启动（admin-web）

```bash
npm run admin:dev      # → http://localhost:5173，代理 /api → 127.0.0.1:8080
npm run admin:build    # 构建静态产物 → admin-web/dist/
```

> 与小程序共用后端和账号体系：小程序微信登录创建的账号需设置过用户名密码（或在后台注册新账号）后才能登录 Web 端。生产部署见 `.agents/skills/eunomia-web-release/`。

### 开发环境

- **微信小程序**: `request.js` 自动探测环境，直连 `VITE_API_BASE_URL_DEVELOPMENT`
- **H5 开发**: Vite 代理 `/api` → `127.0.0.1:8080`
- **Mock 登录**: 后端 dev profile 自动注入模拟 token（`DevMockAuthFilter`），无需真实微信登录
- **生产部署**: 见 `backend/deploy/` 目录下的部署脚本和配置

---

## 6. 项目文档体系

项目采用 **一入口 + 专题文档** 的文档架构，按需 `@` 引用：

| 文档 | 路径 | 用途 |
|------|------|------|
| **AI 入口** | `CLAUDE.md` | 构建命令、文档索引、关键约定 |
| **架构地图** | `docs/ARCHITECTURE.md` | 前后端分层、文件职责、路由总览 |
| **后台管理网页** | `docs/ADMIN_WEB.md` | admin-web（www.eunomia.cc）定位、结构、已完成功能与后续路线 |
| **核心类图** | `docs/CLASS_DIAGRAMS.md` | 核心领域、赛程生成、记分结算、团体赛、排名引擎类图 |
| **主要用例** | `docs/USE_CASES.md` | 核心功能用例、参与者、主流程和验收核对点 |
| **数据字典** | `docs/DATABASE.md` | 17 张表结构 + 全部枚举映射 |
| **API 契约** | `API.md` | 42 个当前有效接口的入参/出参/鉴权 |
| **业务规则** | `docs/BUSINESS_RULES.md` | 赛制流转、计分规则、排名/出线/战报/自由人/队长/换边算法 |
| **设计系统** | `docs/UI_UX_DESIGN.md` | 色彩变量、字号阶梯、交互底线 |
| **测试策略** | `docs/TESTING.md` | 测试覆盖说明 + 跑测命令 |
| **技术栈复盘** | `docs/TECH_STACK.md` | 项目用到哪些技术点、在本项目里具体怎么落地 |
| **复盘与答辩** | `docs/INTERVIEW_PREP.md` | 项目亮点、踩坑复盘、设计决策答辩稿 |
| **试跑清单** | `docs/FIELD_TEST_CHECKLIST.md` | 真实比赛端到端试跑验收清单 |

另外还有：
- [`DevelopmentLog.md`](DevelopmentLog.md) — 开发者开发日志（中文）
- [`PreLaunchAudit.md`](PreLaunchAudit.md) — 上线前审计报告（18 条逻辑链分析）
- [`src/pages/volleyball/README.md`](src/pages/volleyball/README.md) — 排球模块专项文档（1297 行）

---

## 7. 项目结构

```
Scoring/
├── CLAUDE.md                       # AI 协作文档入口
├── Readme.md                       # 本文件（人类入口）
├── API.md                          # REST API 完整文档
├── DevelopmentLog.md               # 开发者开发日志
├── PreLaunchAudit.md               # 上线前审计报告
│
├── docs/                           # 项目文档体系
│   ├── ARCHITECTURE.md             # 架构地图与目录规范
│   ├── DATABASE.md                 # 数据字典与状态枚举
│   ├── BUSINESS_RULES.md           # 核心业务状态机与算法
│   └── UI_UX_DESIGN.md             # 设计系统与交互规范
│
├── src/                            # 前端源代码 (uni-app)
│   ├── pages/
│   │   ├── index/index.vue         # 赛事大厅（Tab 首页）
│   │   ├── mine/index.vue          # 个人中心（Tab 我的）
│   │   ├── create/                 # 创建赛事（运动选择 + 羽毛球 + 排球）
│   │   ├── scoreboard/index.vue    # 羽毛球记分板（横屏）
│   │   ├── tournament/             # 赛事详情 + 对阵图 + 小组赛 + 队伍列表
│   │   └── volleyball/             # 排球模块
│   │       ├── scoreboard.vue      #   记分板入口（加载/错误/设备路由）
│   │       ├── lineup.vue          #   轮次填写（阵容编辑器）
│   │       ├── record.vue          #   比赛记录（PDF 导出）
│   │       ├── match-state.js      #   状态数据模型 + 持久化
│   │       ├── components/         #   ScoreboardPhone / ScoreboardPad
│   │       └── composables/        #   useScoreboard.js (2200+ 行核心)
│   ├── components/                 # 全局共享组件
│   │   ├── MatchCard.vue
│   │   ├── TournamentListCard.vue
│   │   └── ProfileGatePopup.vue
│   ├── utils/
│   │   ├── request.js              # HTTP 封装（自动 token + 错误 toast）
│   │   └── interaction-guard.js    # useDelayedTapGate / useActionLock
│   ├── store/auth.js               # 登录状态管理
│   ├── pages.json                  # 页面路由 + TabBar 配置
│   └── App.vue                     # 根组件
│
├── admin-web/                      # 后台管理网页 (Vue 3 + Vite，www.eunomia.cc)
│   └── src/
│       ├── main.js                 # 路由 + 登录守卫
│       ├── services/api.js         # fetch 封装 + token 管理
│       ├── views/                  # Login / Lobby / CreateTournament
│       └── components/TournamentTable.vue
│
├── backend/                        # 后端源代码 (Spring Boot)
│   ├── src/main/java/com/scoring/backend/
│   │   ├── controller/             # REST 接口层
│   │   │   ├── AuthController.java
│   │   │   ├── TournamentController.java  # 23 个赛事接口
│   │   │   └── MatchController.java       # 14 个比赛接口
│   │   ├── service/                # 业务逻辑层（接口 + 实现）
│   │   ├── engine/                 # 核心算法
│   │   │   ├── BracketEngine.java        # 淘汰赛排表
│   │   │   └── RoundRobinEngine.java     # 小组循环赛
│   │   ├── domain/
│   │   │   ├── entity/             # 数据实体 (17 张表)
│   │   │   ├── dto/                # 请求参数
│   │   │   └── vo/                 # 响应视图
│   │   ├── mapper/                 # MyBatis-Plus Mapper
│   │   ├── security/               # 鉴权拦截器 + AuthContext
│   │   ├── common/                 # ApiResponse + 全局异常
│   │   └── config/                 # CORS + MyBatis-Plus 配置
│   ├── src/test/java/              # 单元测试与集成测试
│   ├── src/main/resources/db/migration/  # Flyway 迁移 (V1~V19)
│   └── deploy/                     # 部署脚本 + Nginx 配置
│
├── dist/dev/mp-weixin/             # 微信小程序编译输出
├── vite.config.js                  # Vite + API 代理配置
└── package.json                    # 前端依赖
```

---

## 8. 后续规划

### Web 后台（admin-web · www.eunomia.cc）

已完成：注册登录、赛事大厅（我创建/我收藏/全站搜索）、与小程序对齐的全赛制创建（含分段规则、排名模板、裁判密码）并已上线。

| 优先级 | 方向 | 说明 |
|--------|------|------|
| P0 | 赛事详情 Web 视图 | 只读展示名单/对阵图/小组积分榜（接口现成），创建成功后跳详情而非回大厅 |
| P0 | 列表行动作 | 点击跳详情、收藏/取消收藏 |
| P0 | 赛程编排 | 生成淘汰赛 + 预览确认 + 人工指定出线（接口现成） |
| P1 | 管理操作 | 归档；编辑/删除赛事（需后端补接口）；裁判密码与授权管理 |
| P1 | 赛后导出 | 积分榜/成绩册 CSV / 打印页 |
| P2 | 工程质量 | 401 判定改按状态码、抽 AppHeader 组件、拆分 CreateTournamentView、表单草稿、移动端适配、补测试 |

### 其他方向

- **账号打通**：小程序端提供"设置用户名密码"入口，方便同一账号在 Web 后台登录
- **真实比赛试跑**：按 [`docs/FIELD_TEST_CHECKLIST.md`](docs/FIELD_TEST_CHECKLIST.md) 完成端到端验收
- **语音播报完善**：音频资源已迁移 api.eunomia.cc 远程托管，继续补齐播报场景

---

> **当前版本** · 2026 年 6 月 · 羽毛球 + 排球双运动完成 · "打完比赛自动晋级"物理闭环 + 排球专业规则全链路落地 🎉
> **2026 年 8 月增补** · Web 赛事后台 admin-web 上线（www.eunomia.cc）+ 产品介绍页上线（product.eunomia.cc）
