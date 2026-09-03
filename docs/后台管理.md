# ADMIN_WEB.md — 后台管理网页（www.eunomia.cc）

> **用途**: 记录 `admin-web/` 子项目的定位、结构、已完成功能与后续路线。
> **关联**: [[ARCHITECTURE.md]] · [[API.md]] · [[../API.md|API 契约]]
> **部署运维**: 见 `.agents/skills/eunomia-web-release/SKILL.md`（部署 runbook，不在本文档重复）

---

## 1. 定位

`admin-web/` 是一个**独立于 uni-app 小程序的 Vue 3 Web 应用**（不是小程序的 H5 构建），定位为：

> **用网页完成赛前录入和管理，现场操作继续交给小程序。**

典型场景：组织者坐在电脑前，用键盘粘贴选手名单、配置赛制规则、批量录入队伍，生成的赛事直接出现在小程序端供现场记分。

### 域名体系（同一台服务器 47.101.156.6）

| 域名 | 内容 | 技术 |
|------|------|------|
| `www.eunomia.cc` | 后台管理网页（本文档） | admin-web 静态文件 + nginx 反代 `/api/` |
| `api.eunomia.cc` | 后端 API + 记分播报音频静态资源 | Spring Boot (`scoring-backend`) |
| `product.eunomia.cc` | 产品介绍静态页 | `docs/product-guide/` 纯 HTML |

小程序首页（`src/pages/index/index.vue`）有指向 `www.eunomia.cc` 与 `product.eunomia.cc` 的入口文案。

### 与后端的关系

- **零后端改动**：nginx 将 `www.eunomia.cc/api/` 反代到 `127.0.0.1:8080`，同源调用，无 CORS 问题。
- **完全复用现有接口**：不新增任何后端接口，与小程序共用同一套 `/api/v1` 契约（见 [API.md](../API.md)）。
- **共用用户体系**：Web 用 `POST /auth/register` / `POST /auth/password-login`（小程序用 `wechat-login`），登录同一 `user` 表，因此**网页创建的赛事、收藏在小程序端直接可见**，反之亦然。

---

## 2. 技术栈与结构

```
admin-web/
├── index.html
├── vite.config.js              ← dev 代理 /api → 127.0.0.1:8080
├── package.json                ← vue 3.4 + vue-router 4.4 + vite 5.2（无 UI 库、无状态库）
└── src/
    ├── main.js                 ← 路由定义 + 全局前置守卫（meta.auth → /login）
    ├── App.vue                 ← 仅 RouterView + 401 全局跳转注册
    ├── styles.css              ← 全局设计系统（按钮/面板/表格/弹窗/抽屉）
    ├── services/
    │   └── api.js              ← fetch 封装：token 存 localStorage、code===0 校验、
    │                              401 失效自动清 token 跳登录、8 个 API 函数
    ├── views/
    │   ├── LoginView.vue       ← 登录/注册双 tab
    │   ├── LobbyView.vue       ← 赛事大厅（我创建 + 我收藏 + 全站搜索）
    │   └── CreateTournamentView.vue  ← 创建比赛（1250+ 行，最大页面）
    └── components/
        └── TournamentTable.vue ← 赛事列表表格（只读）

路由: / → /lobby（重定向） · /login · /lobby（需登录） · /create（需登录）
```

与小程序端的架构差异：无 Pinia/Vuex、无 uni-app、无 Storage 缓存层；`api.js` 用原生 `fetch` + `localStorage`，是最小化实现。

启动命令（仓库根目录 `package.json` 已封装）：

```bash
npm run admin:dev     # 开发（端口 5173，代理 /api）
npm run admin:build   # 构建 → admin-web/dist/
```

---

## 3. 已完成功能

> 开发轨迹：`48c259e 配套网页前端第一轮同步` → `4151ac2 网页端个人赛适配` → `a1b9a26 团体赛初步开发` → `4217bdb/63e89a3/8fbda37 分段规则` → `3378b6b 颜色布局优化`。与小程序的团体赛/分段规则功能**同期同步开发**，创建能力已对齐。

### 3.1 登录 / 注册（LoginView）

- 用户名 + 密码登录、注册（注册附昵称），调 `POST /auth/register` / `POST /auth/password-login`
- JWT 存 `localStorage`（key `scoring_admin_token`），路由守卫 + `fetchMe()` 双重校验
- 401/登录态失效：正则匹配错误文案 → 清 token → 全局跳 `/login`

### 3.2 赛事大厅（LobbyView）

- **我创建的赛事** `GET /tournaments/mine/created`、**我收藏的赛事** `GET /tournaments/mine/favorites`
- **全站搜索** `GET /tournaments?keyword=`（模糊匹配名称和地点），独立结果面板，可一键返回
- 列表列：赛事/地点、项目（羽毛球个人/团体/接力/排球）、赛制、状态、收藏数、创建时间

### 3.3 创建比赛（CreateTournamentView）

覆盖小程序创建页的**全部赛制能力**，桌面键鼠交互强化：

| 能力块 | 内容 |
|--------|------|
| 运动与形式 | 羽毛球个人 / 羽毛球团体（苏迪曼杯 5 项 · 接力追分赛）/ 排球（固定团体），切换时自动重置默认规则与排名模板 |
| 赛制 | 淘汰赛（1-10 轮，含轮数↔人数范围实时提示与校验）· 小组+淘汰（淘汰名额 4/8/16 + 每组出线 1/2）· 循环赛（单/双循环） |
| 排名规则 | 小组赛排名模板按运动/形式联动（BWF、FIVB、校园排球、接力等 8 种），显示规则描述 |
| 基础规则 | 局数、基础胜分、追分、封顶分、排球决胜局胜分；接力赛自动锁 1 局 10 分轮转 6 人 |
| **分段规则** | 抽屉式设计器：按"小组赛/16进8/…/决赛"自动生成比赛阶段池，赛段（segment）认领阶段，每段独立规则；与小程序的 `roundRules` 契约一致 |
| 季军赛 | 淘汰阶段 ≥4 方可选；启用分段规则时自动继承决赛段规则 |
| 名单录入 | 个人赛：粘贴板逐行解析种子（`1张三` / `1 张三` / `1.张三` 等格式）；团体：快捷添加队伍 + 队员粘贴（排球自动解析"姓名 号码"） |
| 队伍编辑 | 侧边栏编辑队名、成员增删改名、更换队长、排球号码自动补齐与查重、删除队伍确认弹窗 |
| 裁判密码 | 8 位数字，可选 |
| 校验 | 人数下限（排球 6 人/队、羽毛球 2 人/队）、号码重复、接力人数 ≥ 轮转人数、淘汰轮数↔人数匹配、分段规则全覆盖校验 |

创建成功 `POST /tournaments` 后提示 tournamentId 并跳回大厅。

### 3.4 部署（已上线）

- 纯静态部署到 `/opt/scoring/web/admin/releases/<timestamp>/` + `current` 软链，nginx `try_files` 兜底 history 路由，HTTPS 由 certbot 自动续期
- 发布/回滚流程详见 `.agents/skills/eunomia-web-release/`，此处不重复

---

## 4. 接口复用清单

| admin-web 函数 | 接口 | 鉴权 |
|----------------|------|------|
| `register` | `POST /auth/register` | 🔓 |
| `passwordLogin` | `POST /auth/password-login` | 🔓 |
| `fetchMe` | `GET /users/me` | 🔒 |
| `fetchCreatedTournaments` | `GET /tournaments/mine/created` | 🔒 |
| `fetchFavoriteTournaments` | `GET /tournaments/mine/favorites` | 🔒 |
| `searchTournaments` | `GET /tournaments?keyword=` | 🔓 |
| `createTournament` | `POST /tournaments` | 🔒 |

**尚未使用**但后续路线会用到的现成接口：`GET /tournaments/{id}`（详情）、`GET /tournaments/{id}/bracket|groups|group-standings|teams`（赛程视图）、`POST /tournaments/{id}/generate-knockout`、`POST /tournaments/{id}/knockout-preview`、`PUT /tournaments/{id}/qualification-overrides`、`POST/DELETE /tournaments/{id}/favorite`、`PUT archive/unarchive`、裁判管理 4 接口。

---

## 5. 后续开发路线

### P0 — 补齐"赛前管理"闭环（优先级最高）

1. **赛事详情 Web 视图**：当前创建完只能跳回大厅，Web 端无法查看任何赛事内容。新增 `/tournament/:id` 只读页：基本信息 + 参赛名单/队伍（`/teams`）+ 对阵图（`/bracket`）+ 小组积分榜（`/groups` + `/group-standings`），全部接口已存在且 🔓。同时把创建成功后的跳转从"回大厅"改为"进详情页"。
2. **列表行动作**：TournamentTable 加点击跳转详情；收藏/取消收藏按钮（接口现成）。
3. **赛程编排 Web 化**：小组赛出线后"生成淘汰赛"是最典型的桌面操作 —— 接 `generate-knockout` + `knockout-preview`（预览确认）+ `qualification-overrides`（人工指定出线）。

### P1 — 管理与运营能力

4. **赛事管理操作**：归档/取消归档（接口现成）；编辑赛事基础信息、删除赛事需**后端新增接口**（当前无 PUT/DELETE /tournaments/{id}）。
5. **裁判管理页**：设置裁判密码、查看/移除授权列表（4 个接口现成），Web 表格形式比小程序更适合管理。
6. **赛后导出**：积分榜/全部赛果导出 CSV 或打印友好的成绩册页面（可纯前端从 `group-standings`/`bracket` 数据渲染后 `window.print()`）。
7. **表格增强**：分页、按运动类型/状态筛选、排序、状态徽标配色。
8. **账号引导**：Web 与小程序共用 user 表天然数据互通，但缺"绑定"引导 —— 可在小程序"我的"页提示设置用户名密码，以便 Web 端登录同一账号。

### P2 — 工程质量与体验

9. **鉴权健壮性**：`api.js` 的 `isAuthFailure` 用错误文案正则判断登录失效，后端改文案即失效 —— 应改为按 HTTP 状态码/业务 code 判定；`fetch` 对非 JSON 响应（如 502 的 nginx HTML）需容错。
10. **组件抽取**：LobbyView 与 CreateTournamentView 各自复制了整套 `app-header`（品牌 + 导航 + 用户区），应抽 `AppHeader.vue`；CreateTournamentView 1250+ 行，分段规则抽屉、队伍编辑面板应拆为子组件或 composable。
11. **表单状态恢复**：创建页刷新丢失全部已录入名单，可加 localStorage 草稿。
12. **移动端适配**：当前桌面优先布局，手机浏览器观感未验证（可参考 product-guide 的响应式做法）。
13. **测试与规范**：无 lint / 测试；根仓库已有 vitest，可为 `api.js`、粘贴解析、分段规则归一化逻辑补单测。
14. **暗色模式 / 主题**：styles.css 是单套浅色主题，可选跟进小程序的多主题能力。
