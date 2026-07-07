# AGENTS.md — Codex 协作入口

> Codex 开始处理本项目任务时，先读本文件，再按需读 `CLAUDE.md` 和 `docs/` 下的专题文档。

## 文档入口

| 文档 | 用途 |
|------|------|
| `CLAUDE.md` | 项目总览、启动命令、关键约定 |
| `docs/ARCHITECTURE.md` | 前后端分层、目录职责、路由和接口速查 |
| `docs/DATABASE.md` | 13 张表结构、状态码和枚举 |
| `API.md` | 当前有效 REST API 契约 |
| `docs/BUSINESS_RULES.md` | 赛制、计分、晋级、排球专项规则 |
| `docs/UI_UX_DESIGN.md` | 前端视觉与交互约定 |

## 工作规则

- 先核对代码事实，再相信文档；发现冲突时，以当前代码和迁移脚本为准。
- 不新增无关文档；用户明确要求文档工作时，优先修正现有文档的一致性。
- 后端改动遵循 Controller → Service → Engine/Mapper 分层，Controller 不写业务逻辑。
- 前端改动遵循 `src/pages/` 路由页、`src/components/` 全局组件、页面专属组件就近放置。
- 排球记分核心逻辑在 `src/pages/volleyball/composables/useScoreboard.js`，Phone/Pad 组件只处理 UI 差异。
- 当前后端 `theme-config` 接口已废弃，配色以本地设备存储和前端默认值为准。