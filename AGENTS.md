# AGENTS.md — Codex 协作入口

> Codex 开始处理本项目任务时，先读本文件，再按需读 `CLAUDE.md` 和 `docs/` 下的专题文档。

## 文档入口

| 文档 | 用途 |
|------|------|
| `CLAUDE.md` | 项目总览、启动命令、关键约定 |
| `docs/ARCHITECTURE.md` | 前后端分层、目录职责、路由和接口速查 |
| `docs/CLASS_DIAGRAMS.md` | 核心领域、赛程、记分、团体赛、排球状态类图 |
| `docs/USE_CASES.md` | 核心功能用例、参与者、主流程和验收核对点 |
| `docs/DATABASE.md` | 15 张表结构、状态码和枚举 |
| `API.md` | 当前有效 REST API 契约 |
| `docs/BUSINESS_RULES.md` | 赛制、计分、晋级、排球专项规则 |
| `docs/UI_UX_DESIGN.md` | 前端视觉与交互约定 |

## 工作规则

- 先核对代码事实，再相信文档；发现冲突时，以当前代码和迁移脚本为准。
- 处理跨前后端的数据流问题时，先把“保存 → 回显 → 禁改/权限”的闭环理清；优先跑最小相关测试，确认失败点后再全量验证，避免被旧测试 helper 或状态重置误导。
- 不新增无关文档；用户明确要求文档工作时，优先修正现有文档的一致性。
- 后端改动遵循 Controller → Service → Engine/Mapper 分层，Controller 不写业务逻辑。
- 前端改动遵循 `src/pages/` 路由页、`src/components/` 全局组件、页面专属组件就近放置。
- 在 Codex Windows 沙箱中运行前端 npm 脚本时，固定使用 `npm.cmd`，例如 `npm.cmd test`、`npm.cmd run build:h5`，避免 PowerShell 对 `npm.ps1` 的执行策略拦截。
- 测试遇到沙箱/权限拦截时，先判断是否已经足够说明问题：前端 `npm.cmd test` 或构建如果被执行策略、文件锁、`dist/build` 权限拦截，不反复申请提升权限或清理重跑；在最终答复中明确写出“未验证，原因是 xxx”即可。后端 Maven 若因 `backend/target` 写入被拒绝，只在任务确实需要后端验证时申请一次提升权限，用户中断或拒绝后立即停止重试并报告。
- 涉及跨前后端的新链路时，先用最小探针锁定闭环，再跑大测试：例如创建页字段变更先核对“前端 payload → DTO → Service 保存 → GET 回显”，不要一上来全量构建或全量测试。能用 `rg`、局部文件读取、单个集成测试确认的，就不要扩大范围。
- 工作流变慢时，优先降低不确定性而不是增加操作次数：先说明当前假设、将要改的文件和验收点；遇到中文乱码、补丁上下文不匹配、沙箱权限失败时，马上切换到项目约定的替代手段，并把失败原因记录下来，不在同一路径上重复试错。
- 前端 H5 构建如果报 `EPERM: operation not permitted, unlink/mkdir 'dist\build\h5...'`，优先判断为旧构建产物或 Windows 沙箱文件权限问题，不要反复直接重跑；先清理生成目录 `Remove-Item -LiteralPath 'D:\LJY\grade2\Scoring\dist\build' -Recurse -Force`，必要时只为清理/重建生成目录申请一次提升权限，然后再跑 `npm.cmd run build:h5`。
- 小幅前端展示微调（只改布局、颜色、字号、间距等样式，不改逻辑/接口/路由）完成后默认不跑完整编译或测试；只做必要的代码核对，除非用户明确要求验证。
- 连续做多轮小 UI 调整时，先用 `rg` 和局部文件读取确认文案/样式是否落点正确，把多次微调合并后最后只跑一次必要构建；如果构建只因 `dist/build` 产物权限失败，按上一条清理产物后重跑，不把它当作源码问题继续排查。
- 如果 `apply_patch` 在 Codex Windows 沙箱中失败，优先使用 Node.js 脚本进行 UTF-8 文件修改；避免用 PowerShell `ConvertTo-Json` 或大段 here-string 改中文/JSON 文件，防止 BOM、乱码或格式重排。
- 排球记分核心逻辑在 `src/pages/volleyball/composables/useScoreboard.js`，Phone/Pad 组件只处理 UI 差异。
- 当前后端 `theme-config` 接口已废弃，配色以本地设备存储和前端默认值为准。
- 代码编辑策略：<=5 行的小改动优先用 apply_patch（上下文必须是真实代码行，不能是标签名或摘要）；跨文件/批量替换可使用通过 apply_patch 创建的 Node.js .cjs 脚本，但 Windows 沙箱中优先采用多个定点补丁；脚本写入遇到 EPERM 时立即切换到 apply_patch，不重复重试；避免 Node -e 内联和 PowerShell here-string 操作中文/JSON 文件。
- 记分页缓存清理：离开记分页的每条路径（结算成功、返回、权限拒绝）都必须调用清理函数清除 uni.setStorageSync 写入的缓存，参考排球 clearMatchState 模式。

## 排名模板新增规则

- 新增或修改小组赛排名默认模板时，先按 `sportType`、`participantType`、`teamMatchTemplate` 建立独立排名模式；不得只根据 criterion 名称猜测赛制，也不得让一个赛制的模板选项、默认值或展示列影响另一个赛制。
- 模板落地必须形成闭环：前端模式与选项、创建页默认模板、分组页自定义入口与展示列、后端 `Template`/preset、模板解析与自定义回退、小组排名引擎、保存回显测试。
- 模板优先级必须先写成明确业务顺序，再映射到代码。例如接力追分赛常用模板一是“胜场数 → 两队直胜 → 小分得失比”，不能复用苏杯的场内大分和场内局。
- 开始实现前先列出改动文件、数据来源和验收点；验证优先运行排名选项/展示列的前端局部测试与 `GroupStandingEngineTest`，只有闭环不明确时再扩大到集成测试。
- Windows 环境下如果已知 Maven 会因 `backend/target` 写入受限，直接为最小后端测试申请一次权限；若出现 `EPERM` 或补丁上下文不匹配，记录原因后切换路径，不在同一路径重复试错。
