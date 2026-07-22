# AGENTS.md — Codex 协作入口

> Codex 开始处理本项目任务时，先读本文件，再按需读 `CLAUDE.md` 和 `docs/` 下的专题文档。

## 文档入口

| 文档 | 用途 |
|------|------|
| `CLAUDE.md` | 项目总览、启动命令、关键约定 |
| `docs/ARCHITECTURE.md` | 前后端分层、目录职责、路由和接口速查 |
| `docs/DATABASE.md` | 14 张表结构、状态码和枚举 |
| `API.md` | 当前有效 REST API 契约 |
| `docs/BUSINESS_RULES.md` | 赛制、计分、晋级、排球专项规则 |
| `docs/UI_UX_DESIGN.md` | 前端视觉与交互约定 |

## 工作规则

- 先核对代码事实，再相信文档；发现冲突时，以当前代码和迁移脚本为准。
- 不新增无关文档；用户明确要求文档工作时，优先修正现有文档的一致性。
- 后端改动遵循 Controller → Service → Engine/Mapper 分层，Controller 不写业务逻辑。
- 前端改动遵循 `src/pages/` 路由页、`src/components/` 全局组件、页面专属组件就近放置。
- 在 Codex Windows 沙箱中运行前端 npm 脚本时，固定使用 `npm.cmd`，例如 `npm.cmd test`、`npm.cmd run build:h5`，避免 PowerShell 对 `npm.ps1` 的执行策略拦截。
- 如果 `apply_patch` 在 Codex Windows 沙箱中失败，优先使用 Node.js 脚本进行 UTF-8 文件修改；避免用 PowerShell `ConvertTo-Json` 或大段 here-string 改中文/JSON 文件，防止 BOM、乱码或格式重排。
- 排球记分核心逻辑在 `src/pages/volleyball/composables/useScoreboard.js`，Phone/Pad 组件只处理 UI 差异。
- 当前后端 `theme-config` 接口已废弃，配色以本地设备存储和前端默认值为准。
- 代码编辑策略：<=5 行的小改动优先用 apply_patch（上下文必须是真实代码行，不能是标签名或摘要）；多行/跨文件/批量替换用 Node.js .cjs 脚本通过 apply_patch 创建于 workspace root 再 node 执行；避免 Node -e 内联和 PowerShell here-string 操作中文/JSON 文件。
- 记分页缓存清理：离开记分页的每条路径（结算成功、返回、权限拒绝）都必须调用清理函数清除 uni.setStorageSync 写入的缓存，参考排球 clearMatchState 模式。
