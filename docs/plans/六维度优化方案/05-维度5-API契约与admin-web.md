# 维度5 优化方案：API 契约一致性 + admin-web

- **日期**：2026-10-26（夜间自动作业 · 单元 E，第 4 轮）
- **事实源**：`PROJECT-DIAGNOSIS-20261026.md` §2/§3.5/§4（诊断评级 **A**），关键发现本轮已亲读代码/配置复核
- **适用对象**：大三学生单人开发；项目真实投产在用，同时作为面试项目经历
- **所用软件工程方法**：契约即代码（Contract as Code / SSoT）、快照测试（Snapshot Testing）、加权决策矩阵、SMART、HTTP 语义规范（RFC 9110/8594）、试点-铺开、ADR、WBS、风险登记册

---

## 1. 现状与问题清单

### 1.1 资产盘点（六维度第二强项，改造不得破坏）

| 资产 | 证据 |
|---|---|
| **63 端点双向核对：假文档 0、未记录 0**；抽 10 个核心端点字段全部一致（含 2026-10 新增 `sameToken`/`kicked`） | 诊断 §3.5 |
| 统一信封 `ApiResponse` 100% 覆盖；错误码 ≡ HTTP 状态码，无 magic number | 诊断 §3.5 |
| 权限判定全部服务端强制，admin-web **零前端授权**（前端不藏任何权限逻辑） | 诊断 §3.5 |
| camel↔snake 转换层单一清晰 | 诊断 §3.5 |
| admin-web 自带 `check:sfc`（`scripts/check-sfc-bindings.mjs`，SFC 绑定自定义检查——存在但未接 CI，本轮亲证 scripts 仅 `dev/build/check:sfc` 三条） | ✅ 本轮亲读 `web/admin-web/package.json` |

### 1.2 问题清单

| 级别 | 问题 | 位置/量化 | 亲证状态 |
|---|---|---|---|
| **P2-①** | **`GET /auth/pc/status` 是带副作用的 GET**：`pollStatus` 在 `@Transactional` 内原子转移 CONFIRMED→CONSUMED 并**一次性签发 token**——浏览器预取/预渲染/代理预取会在真实轮询前抢走 token，登录偶发失败且难复现 | `service/auth/PcLoginService.java:122-127`（✅ 本轮亲读：方法注释自述「并发轮询只有一个能拿到 token」——但 GET 语义允许的不是应用内并发，而是**传输层任意预取**） | ✅ |
| P3-② | 附录 A 缺 13 个组别端点（手写文档欠账；本轮粗计 `docs/API接口.md` 有 77 个 `###/####` 标题，与 63 端点口径并存） | `docs/API接口.md` | ✅（口径已注） |
| P3-③ | `team-items/start` 用 PUT 做非幂等创建（HTTP 语义违规：PUT 应幂等） | 诊断 §3.5 | 报告证据 |
| P3-④ | `createTournament` 返回裸 `Map<String,String>` 非 VO（契约无类型、无文档结构） | `TournamentController.java:67`（✅ 维度2 轮 grep 亲证签名 `ApiResponse<Map<String, String>>`） | ✅ |
| P3-⑤ | admin-web token 存 localStorage（XSS 面）；无 `.env` 文档 | `web/admin-web/` | 报告证据 |
| P3-⑥ | **nginx /api 反代配置不在仓库**（配置只活在服务器上——「最后一公里卫生」的运维版） | ✅ 本轮 find 全仓 0 命中 nginx*.conf | ✅ |
| P3-⑦ | 下线端点无 410 过渡机制（本次 pc/status、team-items/start 改造恰好需要它） | 诊断 §3.5 | 报告证据 |
| 跨界-⑧ | admin-web **零测试零 CI**（✅ 本轮亲证 0 测试文件；CI 落地主体归维度6，本方案负责 admin-web 侧的工程化内容） | `web/admin-web/` | ✅ |

### 1.3 问题模式归纳（软件工程视角）

1. **「纪律型零漂移」缺机制保障**：63 端点零漂移是诊断报告人工双向核对的产物（一次性审计），不是机制的产物。契约的事实源始终是代码，手写文档是派生物——**派生物没有守门就必然再漂移**，13 个缺失端点正是漂移的第一批。诊断报告自己给出的根治建议（springdoc/OpenAPI 从代码生成）方向正确，本方案将其落地并加「快照守门」。
2. **副作用 GET 是分布式时代的经典语义陷阱**：GET 的合同是「安全且幂等」，任何中间层（浏览器预取、CDN、杀毒软件、admin-web 的 HTTP 库缓存）都有权随意重放 GET——一次性 token 被「合法地偷走」。修复必须 verb 变更 + 过渡期双端协调，不是改个注解那么简单。
3. **运维配置出仓 = 部署不可复现**：nginx 配置只在服务器上，与维度6 的 `.env.example` 缺项同属「验证面/运行面不重合」结构性问题（诊断 §4.2）在运维层的投影。

---

## 2. 目标与非目标（SMART 原则）

### 2.1 目标

| # | 目标 | 度量 | 截止 |
|---|---|---|---|
| G1 | 消除副作用 GET 与语义违规 verb | `/auth/pc/status`、`team-items/start` 改 POST；旧 verb 返回 410 + `Sunset` 说明头过渡 2 周后移除；admin-web `api.js` 与小程序侧同步；手测扫码登录全流程 | 1 周 |
| G2 | 契约即代码 | springdoc 生成 OpenAPI 3 JSON；**快照守门**：CI 中生成物与 `openapi-snapshot.json` 比对，契约变更必须显式 regen；`/v3/*`、`/swagger-ui/**` 在 prod 不对外暴露 | 3 周 |
| G3 | 手写文档转型 | 附录 A 补齐 13 个组别端点（从生成物回填）；`docs/API接口.md` 定位改写：**快照=结构事实源，手写=语义/时序/生命周期说明** | 3 周 |
| G4 | admin-web 工程化 | nginx 配置入库（含 CSP 头与 `/api` 反代）；`.env.example`；vitest + @vue/test-utils 最小测试通路（登录轮询状态机 1 个组件测试）；`check:sfc` 与 `vite build` 进 CI（落地归维度6）；token 存储决策 ADR | 3 周 |
| G5 | 端点生命周期规范 | 文档新增「端点生命周期」节：活跃 / 弃用（响应带 `Deprecation`+`Sunset` 头）/ 下线（410）；新增端点四同步 checklist（代码+快照+文档+错误码） | 4 周 |
| G6 | `createTournament` 返回 VO 化 | `Map<String,String>` → `CreateTournamentVO`（字段以现 Map 键为准，前端读取点核对 0 变更或同步） | 1 周 |

### 2.2 非目标

- ❌ 不引入 API 网关 / BFF 层（单实例单体，负收益）
- ❌ 不做 Pact 双向契约测试、schemathesis 全端点 fuzz（后端已有混沌仿真 + 5 个 fuzzer 覆盖业务语义层，重复投资）
- ❌ 不把 admin-web 改造成 TypeScript 全量重构（增量可选：仅新文件 TS——若引入成本另行评估，本方案不绑定）
- ❌ 不追求 OpenAPI 注解 100% 覆盖（核心端点语义标注优先，结构信息自动生成已足够）

---

## 3. 候选策略（3 个）

### 策略 A：手写修补（最小主义）

**思路**：补齐 13 个端点文档、修 P2/P3 的 verb 与 VO 问题、admin-web 保持现状。

**优点**：~6h；文档立即完整。

**缺点**：**漂移根因不动**——下一次新增端点，文档又落后（本项目月均新增端点 ≥2，`sameToken`/`kicked` 就是 2026-10 刚加的）；「63 端点零漂移」的成果没有机制锁定，下次审计可能就不是零了；admin-web 继续零守门。

**成本**：⭐（6h）｜投产价值：中｜面试价值：低｜风险：低

### 策略 B：契约即代码 + 快照守门 + admin-web 工程化 ★ **推荐**

**思路**：三件事：①把「代码」正式立为契约唯一事实源（springdoc 生成 OpenAPI），手写文档降级为语义层并补齐；②用**快照测试**锁契约——任何端点/字段/错误码变化都会改生成 JSON，CI 比对快照，变更必须显式 regen（把「无声契约漂移」变成「CI 红的显式决策」）；③admin-web 补齐最小工程化（配置入库、CI 接入、测试通路、决策 ADR）。

**理论依据**：
- 契约即代码 / Single Source of Truth：派生物（文档、类型）从事实源（代码）生成，守门比对生成物；
- 快照测试（Snapshot Testing，Jest/AST 社区成熟实践移植到 API 面）：以「变更可见性」替代「人工核对纪律」；
- HTTP 语义规范（RFC 9110：GET 必须安全；RFC 8594：`Sunset` 头；410 Gone 语义）——用标准而非自造约定管理端点退役；
- 12-Factor Config：`.env.example` 与配置入库；
- CSP（Content-Security-Policy）：localStorage XSS 面的减损层。

**优点**：漂移从「审计发现」变「CI 拦截」；13 端点欠账由生成物一次性根治；admin-web 进入验证面（诊断 §4.2 结构性问题的 admin 分支关闭）；端点退役从此有标准流程（本次两个 verb 改造即首个实战）。

**缺点**：springdoc 依赖引入与 VO 标注是渐进工作；快照初次生成的基线需一次人工审读（确认生成物与现有文档语义一致）；admin-web vitest 通路搭建 2h。

**成本**：⭐⭐⭐（~19h/4 周）｜投产价值：高｜面试价值：高（API 治理是后端面试高频话题且有标准可引）｜风险：中低

### 策略 C：全链路契约体系

**思路**：在 B 之上再加：schemathesis 全端点模糊测试、openapi-typescript 为 admin-web/小程序生成请求类型、Pact 消费者驱动契约、Prism mock server 供前端并行开发。

**优点**：理论上「契约驱动全流程」。

**缺点**：①schemathesis 与既有混沌仿真/fuzzer 在「发现非法输入」上职能重叠，在「业务状态机合法性」上反而不如后者（它不懂羽毛球赛制）；②类型生成对 JS 主导的两个前端收益打折（小程序是 uni-app JS）；③Pact 双向契约解决的是「多团队并行」的协调问题——单人单仓不存在此问题；④mock server 在「后端先行」的单人节奏里用不上。**每个子项单独看都好，合起来是给单人项目套大厂流程**。

**成本**：⭐⭐⭐⭐（30h+）｜投产价值：中｜面试价值：中（讲不出取舍就是背诵）｜风险：中

---

## 4. 加权决策矩阵与选型结论

权重沿用全局（投产 0.30 / 面试 0.25 / 成本 0.25 / 风险 0.10 / 可维护 0.10）。

| 评分维度（权重） | 策略 A 手写修补 | 策略 B 契约即代码+守门 | 策略 C 全链路体系 |
|---|---|---|---|
| 投产价值（0.30） | 3.0 | **4.5** | 3.5 |
| 面试展示价值（0.25） | 2.0 | **4.5** | 3.5 |
| 实现成本可行性（0.25） | **5.0** | 4.0 | 1.5 |
| 风险安全度（0.10） | 4.0 | **4.5** | 3.5 |
| 长期可维护性（0.10） | 2.0 | **4.5** | 4.0 |
| **加权总分** | 3.20 | **4.40** | 3.20 |

**选型结论：策略 B**。决定性论证：本维度的 A 级评价建立在「人工审计的偶然」上而非「机制的必然」上——B 用快照守门把偶然变成必然；C 的全部有价值的子集（结构生成、类型可选）已被 B 包含，其专属部分（Pact/mock/全量 fuzz）解决的问题在本项目**不存在**。

---

## 5. 选定策略 B 的分阶段实施计划（WBS）

### Phase 0：契约快修（第 1 周，~4h）——两个 verb 改造是端点生命周期的首个实战

| 任务 | 内容 | 涉及文件 |
|---|---|---|
| T0.1 | **`/auth/pc/status` GET→POST**（与维度2 T2.1 同一提交）：`AuthController` 映射改 `@PostMapping`；旧 GET 路径保留 2 周：返回 410 + 响应头 `Deprecation: true`、`Sunset: <日期>` + body 提示「请升级 admin-web」；admin-web `api.js` 调用改 POST（发版同步）；小程序侧 grep 确认无此调用（该端点是 PC 扫码轮询，小程序应无引用——动手前确认） | `PcLoginService.java`（仅出参注释）、`AuthController`、`web/admin-web/src/**/api.js`、`docs/API接口.md` |
| T0.2 | **`team-items/start` PUT→POST**：同上模式（PUT 做非幂等创建违反 RFC 9110 幂等语义）；调用方同步 | 对应 Controller + 前端调用点 |
| T0.3 | **`createTournament` VO 化**：`Map<String,String>` → `CreateTournamentVO`（字段=现 Map 键，逐键核对小程序读取点 `grep createTournament` 前端）；VO 进入 G2 的 OpenAPI 标注范围 | `TournamentController.java:67` + 新 VO |
| T0.4 | **端点生命周期规范落纸**：`docs/API接口.md` 增「端点生命周期」节（活跃/弃用/下线三态 + Sunset 头约定 + 410 语义） | `docs/API接口.md` |

- **验收标准**：admin-web 扫码登录与 team-items 流程手测通过；旧 verb 返回 410+头（curl 留档）；前端无调用残留；全量回归绿。
- **测试策略**：verb 变更补 controller 用例（新 POST 路径 200/410 分支各 1）；现有 24 个 controller 测试类同步改名修正。
- **风险与回滚**：双端联动是主要风险——安排后端与 admin-web 同日发布（eunomia-release 流程）；410 过渡期兜底（2 周后仍有旧调用则延长并排查来源）。回滚双端各自 revert。
- **工时**：4h。

### Phase 1：OpenAPI 契约即代码 + 快照守门（第 2~3 周，~8h）

| 任务 | 内容 | 涉及文件 |
|---|---|---|
| T1.1 | **引入 springdoc**：`springdoc-openapi-starter-webmvc-ui`（Boot 3 适配版，匹配 3.3.5）；基础配置：标题/版本/统一信封说明；**prod 暴露控制**——`springdoc.api-docs.enabled=false` + `springdoc.swagger-ui.enabled=false`（默认关闭，dev profile 打开；或 nginx 屏蔽 `/v3/*`、`/swagger-ui/**`——两道都上，纵深） | `pom.xml`、`application.yml`/`application-dev.yml`、nginx 配置（Phase 2 入库件） |
| T1.2 | **快照守门测试** `OpenApiSnapshotTest`：测试内用 `MockMvc` + springdoc 端点导出 JSON，与 `src/test/resources/openapi-snapshot.json` 规范化比对（键排序后 diff）；快照 regen 命令写入测试注释（`-DupdateSnapshot` 或脚本）。**效果**：改了任何端点/DTO 字段/错误码而不 regen 快照 → CI 红 → 契约变更被强制显式化 | 新增测试 + 快照文件 |
| T1.3 | **首次基线人工审读**：生成物 vs 现有文档抽样比对（诊断已做过 63 端点核对，审读重点是新增语义标注的准确性）；把快照作为 PR 资产入库 | 快照文件 |
| T1.4 | **VO 语义标注渐进**：核心 10 端点的 VO/Req 补 `@Schema(description=...)`（结构信息自动生成已有，标注只补「为什么」）；与维度2 Phase 0 的 DTO 校验注解同一批文件，顺手协同 | 各 VO/Req |
| T1.5 | **附录 A 补齐与文档转型**：13 个组别端点从生成物回填结构；`docs/API接口.md` 头部声明新分工：「结构与字段以 `openapi-snapshot.json` 为事实源（CI 守门），本文件承载语义、时序、权限说明与端点生命周期」 | `docs/API接口.md` |
| T1.6 | （可选加分项，1h）admin-web 若引入 TS 则接 openapi-typescript 生成请求类型；纯 JS 则跳过不勉强 | `web/admin-web/` |

- **验收标准**：快照测试绿且「故意加字段不 regen」实测红过一次；`/v3/api-docs` 在 prod 配置下 404；文档 63+13 端点齐全。
- **测试策略**：快照测试本体 + prod 暴露控制的启动冒烟（curl 404 断言并入发布前检查单）。
- **风险与回滚**：springdoc 扫描对启动时间影响（毫秒级，实测确认）；快照 diff 噪声（规范化排序解决，首次调稳）；回滚依赖移除即可。
- **工时**：8h。

### Phase 2：admin-web 工程化（第 2~3 周，~5h；CI 挂载动作与维度6 同一 PR）

| 任务 | 内容 | 涉及文件 |
|---|---|---|
| T2.1 | **nginx 配置入库**：从服务器导出现行配置 → `deploy/nginx/admin-web.conf`（含 `/api` 反代、`try_files` SPA 回退、gzip、**CSP 响应头**、HSTS）；仓库与服务器同步流程写入运维手册（eunomia-web-release skill 的部署段补「配置来源=仓库」） | `deploy/nginx/`（新增）+ 运维文档 |
| T2.2 | **`.env.example`**：`VITE_API_BASE_URL` 等实际使用的变量落纸（对照 `import.meta.env` 使用点 grep 全量） | `web/admin-web/.env.example`（新增） |
| T2.3 | **最小测试通路**：引入 vitest + @vue/test-utils；首个测试选**登录轮询状态机**（PENDING/CONFIRMED/CONSUMED/EXPIRED 映射 UI——纯逻辑、与 Phase 0 改造直接相关、回归价值最高）；目标是「通路存在」而非覆盖率 | `web/admin-web/src/**/__tests__/` |
| T2.4 | **CI 接入**（需求归本方案、落地在维度6 PR）：`check:sfc` + `vite build` + `vitest run` 三步 job | `.github/workflows/ci.yml`（F 单元执行） |
| T2.5 | **token 存储决策 ADR**：保留 localStorage（理由：静态站 + 反代跨域下 httpOnly cookie 需要 credentials/CORS/CSRF 三件套复杂度；减损层=CSP 头 + 7 天 JWT（维度2）+ 无第三方脚本）；重评条件：引入第三方脚本或发现 XSS 即改 cookie | `docs/adr/ADR-admin-web-token-storage.md` |

- **验收标准**：nginx 配置 diff 服务器仅注释差异；CI job 绿；登录轮询测试真实捕获过一次人为破坏（守门自测）；ADR 入库。
- **风险与回滚**：CSP 头过紧会破坏现站——先 `Content-Security-Policy-Report-Only` 观察一周再强制；nginx 配置误同步用仓库版本为准回滚。
- **工时**：5h。

### Phase 3：治理机制固化（第 4 周，~2h）

- 端点新增四同步 checklist（代码+快照 regen+文档语义+错误码）挂 `docs/API接口.md` 头部与 PR 模板（若有）；
- 季度对照：快照 diff 回顾（季度内契约变更全记录天然可审计——快照的额外红利）。

### 工时与排期总览

| Phase | 内容 | 工时 | 日历 |
|---|---|---|---|
| 0 | 契约快修（verb/VO/生命周期） | 4h | 第 1 周 |
| 1 | OpenAPI + 快照守门 + 文档转型 | 8h | 第 2~3 周 |
| 2 | admin-web 工程化 | 5h | 第 2~3 周 |
| 3 | 治理固化 | 2h | 第 4 周 |
| **合计** | | **~19h** | **4 周** |

---

## 6. 与其他维度的依赖与协同

| 维度 | 关系 | 说明 |
|---|---|---|
| 维度2 安全（B） | 同一任务 | T0.1 = 维度2 T2.1（pc/status POST），同一提交一次回归；T1.4 VO 标注与 B 的 DTO 校验注解同文件协同 |
| 维度6 CI（F） | 落地载体 | 快照测试进 backend job；admin-web `check:sfc/build/vitest` 三步 job 由 F 统一排布；与 F 的 `.env.example` 补全（速览 #7）同批完成 |
| 维度4 前端（D） | 时序约定 | T0.3 createTournament VO 化**先于** D 的 pkg-create 分层（维度4 §6 已排「先 E 后 D」）；小程序侧若有 pc/status 引用在 T0.1 排查时一并确认 |
| 维度1/3 | 弱关联 | 维度1 Phase 3c 拆 TournamentServiceImpl 时若改内部用例归集，不影响 Controller 契约（快照守门正好是「外部行为零变化」的机器证明）；维度3 无直接交集 |

---

## 7. 面试叙述要点（STAR 法）

- **S（情境）**：投产中的 63 端点 API + Vue admin-web；审计确认手写文档当前零漂移（人工双向核对），但发现 13 个组别端点缺失、一个「GET 里做一次性 token 转移」的副作用 GET（浏览器预取会合法偷走登录 token）、admin-web 零测试零 CI 且 nginx 配置只活在服务器上。
- **T（任务）**：把「靠审计纪律维持的契约一致性」变成「靠机制维持」，并让 admin-web 进入验证面。
- **A（行动）**：
  1. 立代码为契约唯一事实源：springdoc 生成 OpenAPI 3，手写文档降级为语义层并从生成物回填补齐；
  2. 快照守门：生成 JSON 入库为快照，CI 比对——契约变更必须显式 regen，无声漂移在 push 时变红；
  3. 用 HTTP 标准治理端点退役：RFC 8594 `Sunset` 头 + 410 Gone 过渡期，首个实战就是副作用 GET 的 POST 化（双端同日发布）；
  4. admin-web 最小工程化：配置入库（CSP 头顺带减损 localStorage XSS 面）、测试通路从登录状态机起步、token 存储写 ADR 而非口头共识。
- **R（结果）**：契约从「审计时一致」到「CI 强制一致」；文档 63→76 端点全覆盖；副作用 GET 与 PUT 非幂等创建清零；admin-web 从零守门到三步 CI。
- **可被追问的深度点**：为什么快照测试比人工双向核对可靠（可持续、可审计、零成本复跑）；为什么不用 Pact（单仓单人无多团队协调问题）；GET 安全性契约的具体含义（中间层有权缓存/预取）；Report-Only CSP 的灰度用法。

---

## 8. 参考资料（均为真实可查出处）

1. OpenAPI Specification 3.1（spec.openapis.org）
2. springdoc-openapi 官方文档（springdoc.org）——Spring Boot 3 集成与暴露控制
3. RFC 9110 HTTP Semantics——方法安全性/幂等性定义；410 Gone
4. RFC 8594 The Sunset HTTP Header Field
5. MDN / W3C —— Content-Security-Policy 与 Report-Only 模式
6. The Twelve-Factor App 之 Config（12factor.net/config）——`.env.example` 依据
7. Snapshot Testing 实践（Jest 文档 Snapshot Testing 章节——方法论源头）
8. 本仓 `PROJECT-DIAGNOSIS-20261026.md` §2/§3.5/§4——问题清单与「springdoc 根治手写漂移」的原始建议（本方案将其落地）

---

*单元 E 完毕。执行者备注：本方案选型=策略 B（契约即代码+快照守门+admin-web 工程化），一句话理由：本维度的 A 级成绩建立在人工审计的偶然上——快照守门把「契约一致」从偶然变成 CI 强制的必然，且副作用 GET 的修复恰好成为端点生命周期机制的第一个实战案例。*

