# 🏐 羽球/排球赛事记分 — 微信小程序

为校园班赛、院系比赛及社会俱乐部量身定制的轻量级计分与赛程管理工具。支持**羽毛球**与**排球**双运动类型，覆盖"创建赛事 → 生成签表 → 实时记分 → 自动晋级 → 赛后归档"完整闭环。

## 快速启动

| 端 | 目录 | 命令 |
|---|---|---|
| 后端 Spring Boot | `backend/` | `cd backend && mvn spring-boot:run`（需 MySQL 8.0 + `backend/local-env.ps1`，见下） |
| 前端 uni-app 小程序 | `frontend/` | `cd frontend && npm install && npm run dev:mp-weixin`（微信开发者工具导入 `frontend/dist/dev/mp-weixin/`） |
| H5 开发 | `frontend/` | `cd frontend && npm run dev:h5`（Vite 代理 `/api` → `127.0.0.1:8080`） |
| Web 后台 | `web/admin-web/` | `cd web/admin-web && npm install && npm run dev`（http://localhost:5173） |
| 产品介绍站 | `web/product-web/` | 纯静态，`deploy-product-web.ps1` 发布 |

**环境要求**：Node ≥ 18 · JDK 17 · MySQL 8.0 · Maven 3.9+（系统安装，`mvn` 在 PATH）· 微信开发者工具

**后端本地启动**：复制 `backend/local-env.example.ps1` → `backend/local-env.ps1` 并填 `DB_URL/DB_USERNAME/DB_PASSWORD/JWT_SECRET/WECHAT_APP_ID/WECHAT_APP_SECRET`，然后运行 `backend/start-local.ps1`。

## 仓库结构

```
├─ frontend/      # uni-app 小程序（独立项目，npm 命令在 frontend/ 下执行）
├─ web/
│  ├─ admin-web/  # 后台管理（Vue 3 + Vite，www.eunomia.cc，nginx 反代 /api/ 复用后端接口）
│  └─ product-web/# 产品介绍静态站（纯 HTML，product.eunomia.cc，不调 API）
├─ backend/       # Spring Boot + MyBatis-Plus + Flyway（api.eunomia.cc）
├─ docs/          # 项目文档（详见下）
├─ scripts/
│  ├─ deploy/     # 发布/回滚 runbook（6 个 .ps1 + 5 个服务器端 .sh + nginx/systemd）
│  └─ tools/      # 音频生成工具
└─ .env.example   # 环境变量模板
```

## 文档索引

| 文档 | 内容 | 何时读 |
|---|---|---|
| [`docs/架构.md`](docs/架构.md) | 目录规范与分层、路由、数据流、部署拓扑 | 写代码前 / 了解整体 |
| [`docs/数据库.md`](docs/数据库.md) | 17 张表字段字典 + 枚举 + 表关系 | 写 SQL / 条件渲染 |
| [`docs/业务规则.md`](docs/业务规则.md) | 状态机、赛制流转、排名/晋级算法 | 写计分/排表逻辑 |
| [`docs/类图.md`](docs/类图.md) | 类依赖与模块结构 | 改接口/找调用链 |
| [`docs/用例.md`](docs/用例.md) | 参与者画像、验收核对点、需求风险点 | 对齐需求/验收 |
| [`docs/API接口.md`](docs/API接口.md) | 全部接口契约（入参/出参/鉴权/锁） | 写前后端对接 |
| [`docs/界面设计.md`](docs/界面设计.md) | 设计系统与交互规范 | 写前端 UI |
| [`docs/测试策略.md`](docs/测试策略.md) | 跑测命令、测试架构、已知问题、手动验收 | 写测试/验收 |
| [`docs/技术栈.md`](docs/技术栈.md) | 技术点在项目里怎么落地 | 复盘/掌握技术 |
| [`docs/后台管理.md`](docs/后台管理.md) | admin-web 定位、功能、接口复用、路线 | 改 web/admin-web |

## 部署

三个域名 + 微信小程序，全部发布资产集中在 `scripts/deploy/`：

| 端 | 域名 | 服务器落地 | 发布脚本 |
|---|---|---|---|
| 后端 | `api.eunomia.cc` | `/opt/scoring/app/releases` + `current` 软链（systemd `scoring-backend`） | `deploy-backend.ps1` |
| admin-web | `www.eunomia.cc` | `/opt/scoring/web/admin/releases` + `current` | `deploy-web.ps1` |
| product-web | `product.eunomia.cc` | `/opt/scoring/web/product/releases` + `current` | `deploy-product-web.ps1` |
| 小程序 | —（微信平台） | 无服务器，`build:mp-weixin` 后经微信开发者工具上传审核 | `prepare-release.ps1` 校验产物 |

生产服务器：`47.101.156.6`（Ubuntu 22.04，无 Docker，jar 直部署）。详见 [`docs/架构.md`](docs/架构.md) 部署一节。