# CLAUDE.md — AI 协作文档索引

> 阅读本文件了解项目全貌，然后按需 `@` 引用具体文档。

---

## 项目简介

「羽球/排球赛事记分」微信小程序 — 为校园班赛、院系比赛及社会俱乐部提供轻量级计分与赛程管理。支持**羽毛球**和**排球**双运动类型，含**苏迪曼杯式团体赛**和**接力追分赛**两种团体赛制，覆盖"创建赛事 → 生成签表 → 实时记分 → 自动晋级 → 赛后归档"的完整闭环。

- **前端**: uni-app (Vue 3 + Vite) → 微信小程序 + H5
- **后端**: Spring Boot 3.3.5 + MyBatis-Plus + MySQL 8.0
- **环境**: Java 17, Node.js ≥ 18

---

## 文档体系

| 文档 | 路径 | 用途 | 何时读 |
|------|------|------|--------|
| **架构地图** | `@docs/ARCHITECTURE.md` | 文件放哪、前后端分层、路由表 | 每次写代码前 |
| **核心类图** | `@docs/CLASS_DIAGRAMS.md` | 核心领域、赛程生成、记分结算、团体赛、排球状态协作 | 做说明书/产品介绍或理解核心模块 |
| **主要用例** | `@docs/USE_CASES.md` | 核心功能用例、参与者、主流程和验收核对点 | 对齐需求、检查实现是否符合预期 |
| **数据字典** | `@docs/DATABASE.md` | 17张表结构 + 所有枚举映射 | 写 SQL 或条件渲染 |
| **API 契约** | `@API.md` | 42个当前有效接口的入参/出参/鉴权 | 写前后端对接 |
| **业务规则** | `@docs/BUSINESS_RULES.md` | 体育规则、赛制流转、算法（含团体赛/接力赛/归档） | 写计分/排表逻辑 |
| **设计系统** | `@docs/UI_UX_DESIGN.md` | 颜色、字号、交互底线 | 写前端 UI |
| **测试策略** | `@docs/TESTING.md` | 测试覆盖说明 + 跑测命令 | 写测试 |
| **技术栈复盘** | `@docs/TECH_STACK.md` | 项目用到哪些技术点、在本项目里具体怎么落地 | 复盘/掌握技术点 |
| **复盘与答辩** | `@docs/INTERVIEW_PREP.md` | 项目亮点、踩坑复盘、设计决策答辩稿 | 求职/答辩/复盘 |
| **试跑清单** | `@docs/FIELD_TEST_CHECKLIST.md` | 真实比赛端到端试跑验收清单 | 找真实用户/上线前 |

---

## 快速启动

### 后端

```bash
cd backend
# 确保 MySQL 8.0 运行中 (端口 3306)
# 配置环境变量（或用 application.yml 默认值）
../.tools/apache-maven-3.9.9/bin/mvn spring-boot:run
# → http://127.0.0.1:8080
```

### 前端

```bash
npm install                              # 首次
npm run dev:mp-weixin                    # 微信小程序开发
# 微信开发者工具 → 导入 dist/dev/mp-weixin/
```

H5 开发：`npm run dev:h5`，Vite 自动代理 `/api` → `127.0.0.1:8080`。

---

## 环境变量（`.env.example`）

| 变量 | 说明 |
|------|------|
| `VITE_API_BASE_URL_DEVELOPMENT` | 前端开发 API 地址 |
| `DB_URL` | 数据库连接串 |
| `JWT_SECRET` | JWT 签名密钥 |
| `WECHAT_APP_ID` / `WECHAT_APP_SECRET` | 微信小程序凭证 |

---

## 关键约定

### 前后端通用
- 统一响应格式 `{ code: 0, message, data }`，`code === 0` 为成功
- 所有 ID 为雪花算法 19 位数字，**以字符串传输**
- 鉴权：Header `Authorization: Bearer <JWT>`，Token 有效期 30 天

### 后端
- Controller 只做**转发 + 鉴权**，不写业务逻辑
- Service 写核心业务，Engine 写独立算法（BracketEngine / RoundRobinEngine）
- `TeamMatchService` 处理团体赛阵容编排和子比赛创建
- 数据库迁移使用 Flyway，版本文件在 `db/migration/`（共 19 个版本）
- 开发环境有 `DevMockAuthFilter`（仅 dev profile），自动注入模拟 token

### 前端
- `pages/` 放路由页面（一个页面一个目录），`components/` 放可复用组件
- `utils/request.js` 自动注入 token、校验 code、toast 错误提示
- 计分板横屏强制 `pageOrientation: landscape`，轮次填写竖屏强制 `portrait`
- 排球记分核心逻辑在 `useScoreboard.js` composable (2200+ 行)，Phone/Pad 只做 UI 差异
- 代码编辑：<=5 行插入/修改用 apply_patch（上下文用真实代码行）；批量替换或跨文件修改用 Node .cjs 脚本（apply_patch 创建脚本 -> node 执行）
- 记分页缓存：本地缓存是当前设备的比赛恢复现场，权限拒绝或资料未完善时只拦截返回，不清缓存；仅在结算成功、重新开始、用户明确放弃当前记分时清 uni.setStorageSync 缓存，参考 volleyball/match-state.js 的 clearMatchState 模式

---

## 项目结构速查

```
Scoring/
├── docs/                          # ← 项目文档（本体系）
├── API.md                         # ← API 接口文档
├── backend/
│   ├── src/main/java/com/scoring/backend/
│   │   ├── controller/            # REST 接口层（3个Controller）
│   │   ├── service/               # 业务逻辑接口（含 TeamMatchService）
│   │   │   └── impl/              # 业务实现
│   │   ├── engine/                # 核心算法（BracketEngine, RoundRobinEngine, ranking/）
│   │   ├── domain/{entity,dto,vo}/ # 数据模型（16实体/17DTO/19VO）
│   │   ├── mapper/                # MyBatis-Plus 数据访问（16个Mapper）
│   │   ├── security/              # 鉴权拦截器
│   │   └── common/                # ApiResponse + 全局异常
│   └── src/main/resources/db/migration/  # Flyway 迁移脚本（V1~V19）
├── src/                           # 前端源码 (uni-app)
│   ├── pages/                     # 路由页面
│   │   ├── index/                 # 赛事大厅
│   │   ├── mine/                  # 个人中心
│   │   ├── create/                # 创建赛事（羽毛球+排球+团体赛）
│   │   ├── scoreboard/            # 羽毛球记分板
│   │   ├── tournament/            # 赛事详情/对阵图/小组赛/团体赛/归档
│   │   └── volleyball/            # 排球模块（记分板+轮次+记录）
│   ├── components/                # 共享组件
│   ├── utils/                     # request.js, interaction-guard.js, query.js 等
│   └── store/                     # auth.js 登录状态
├── src/pages.json                 # 页面路由 + TabBar 配置（当前生效）
└── vite.config.js                 # Vite + API 代理
```
