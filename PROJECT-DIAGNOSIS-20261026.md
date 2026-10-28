# Eunomia 全项目代码级诊断报告

- **日期**：2026-10-26
- **范围**：整个仓库（backend 147 主代码 Java 文件/19,693 行 + 49 测试类/24,161 行；frontend 35 个 .vue/28,640 行 + 30 个测试文件/8,870 行；web 三站 85 文件；31 个 Flyway 迁移/21 张表；docs 22 篇文档）
- **执行方式**：主 agent（任务分配、监督复核）+ 6 个只读 scout 子 agent 并行分维度扫描，全部发现要求 file:line 证据
- **分级标准**：P0 致命（可利用漏洞/数据损坏/发布事故）｜P1 严重（逻辑缺陷/结构性问题）｜P2 中等（可维护性/一致性隐患）｜P3 轻微（风格债）

---

## 0. 诊断方法（业界对标）

本次采用「分层审计 + 六维度矩阵」方法，对标业界通行做法：

1. **三阶段分层法**（结构 → 缺陷 → 安全）：先摸清项目结构与功能，再找既有/潜在缺陷，最后专项安全审计（对应 Softjourn 等审计服务的 Phase 1-3 模型）。
2. **微信官方《小程序安全指引》**：以「未授权/授权不严接口、敏感信息泄露、注入、上传下载」为安全维度的检查基线。
3. **可复现审计清单**（CodeAnt 2026 源码审计清单方法论）：安全、代码质量、架构、可维护性四域清单化，按严重度分级输出。
4. **执行纪律**：每个维度由独立子 agent 穷举扫描（非抽样），主 agent 对 P0/P1 级发现逐条复核证据，并与项目既有知识库（docs/archive/审查与修复记录.md）交叉对照，区分「新发现」与「已知欠账」。

**六维度分工**：① 后端架构与代码质量 ② 后端安全 ③ 小程序前端质量 ④ 数据库与数据访问层 ⑤ API 契约一致性 + admin-web ⑥ 测试体系、CI 与工程卫生。

---

## 1. 总体结论

**总评级：A-（高纪律代码库，无已知被利用风险，欠账集中在「最后一公里卫生」与 CI 盲区，另有一颗待拆除的发布地雷）**

| 维度 | 评级 | 一句话结论 |
|---|---|---|
| 后端架构与代码质量 | B+ | 分层铁律贯彻到位；9 个 500+ 行巨类、常量跨 6 类复制、prod YAML 有缩进地雷 |
| 后端安全 | B+ | IDOR 防护体系化、注入面干净；JWT 30 天不可吊销、部分写 DTO 无校验 |
| 数据库与数据访问 | **A** | 迁移链健康、并发保护完备（FOR UPDATE+CAS+唯一键）；仅运维尾巴 |
| 小程序前端 | A- | 请求层零绕过、分包齐备；21 份复制函数+注释乱码+481 处硬编码色值 |
| API 契约 + admin-web | **A** | 63 端点零假文档零漏记、字段抽查全对；仅语义小瑕疵 |
| 测试、CI 与工程卫生 | A- | 测试是全仓最强项（混沌仿真体系罕见）；CI 有三个结构性盲区 |

**本报告最重要的一条发现**：`backend/src/main/resources/application-prod.yml:27-31` 存在 YAML 缩进错误（line 27 为 4 空格缩进，line 28-31 为 5 空格），`write-limit-per-minute` 等四个键会触发 SnakeYAML 解析失败——**下次按当前 HEAD 构建发布 prod，后端将启动即崩溃**。CI 从不激活 prod profile，故无人兜底。已由主 agent 读文件亲自证实。

---

## 2. 要害发现速览（按行动优先级）

### 🔴 立即处理（P1 最高优先，半天内）

1. **application-prod.yml:27-31 缩进错误 = 发布事故地雷**（A1 发现，主 agent 复核证实）
   - 证据：`login-limit-per-minute`（4 空格）之后 `write-limit-per-minute`/`pc-status-*`/`trust-proxy-headers`（5 空格），YAML 块映射子键缩进不一致 → `mapping values are not allowed here`。
   - 线上仍在运行说明线上 jar 早于该错误引入；但 HEAD 已带病。修复：统一 4 空格缩进，并本地 `mvn spring-boot:run -Dspring-boot.run.profiles=prod`（连测试环境变量）验证可启动。

2. **CI 从不执行 Flyway 迁移，H2 schema 与生产 schema 双轨且已漂移**（A6 发现，主 agent 复核证实）
   - 证据：`.github/workflows/ci.yml:32-33`；全部集成测试 `spring.flyway.enabled=false` + 手工 `schema-h2.sql`（如 `BadmintonTeamTournamentIntegrationTest.java:59-66`）。Flyway 迁移建 21 张表，schema-h2.sql 只有 20 张（缺 `match_runtime_state`，grep 0 命中）。
   - 影响：迁移文件的语法/约束错误只能上线时暴露；两套 schema 会持续漂移。

### 🟠 短期（本周）

3. **CI 缺 `build:mp-weixin` 验证**：`ci.yml:53-54` 只跑 `build:h5`；`uni.getStorageSync` 等 wx 专属调用与分包配置错误只有人肉 `prepare-release.ps1` 兜底。
4. **Flyway `validate-on-migrate: false`**（`application.yml:17-19`）：已应用迁移被改动生产不报错，checksum 纪律失效。
5. **兜底 `Exception.class` 处理器把框架 400/413 类异常吞成 500**（`GlobalExceptionHandler.java:74-79`）：JSON 畸形、缺参、上传超限全部变「系统异常」，污染告警语义（archive §11-9 已实测记录 404→500 现象，补分支仍列 §4.2 待办）。
6. **GET /auth/pc/status 是带副作用的 GET**（`PcLoginService.java:126-127`）：CONFIRMED 窗口内任何预取会抢走一次性 token。改 POST 或拆「探测+领取」。
7. **环境变量模板落后于实现**：`.env.example`/`local-env.example.ps1` 缺 7 个实际使用的变量（`JWT_EXPIRE_SECONDS`、`UPLOAD_DIR`、`DB_POOL_*` 等），且模板 DB_URL 缺 `connectionCollation=utf8mb4_general_ci`（这是文档写明的事故复发防御）。
8. **写 DTO 无校验**：`UpdateTournamentTeamReq`、`UpdateQualificationOverridesReq`、`SaveTeamMatchLineupReq` 全类无 `@Size/@NotBlank`，Controller 未挂 `@Valid`。

### 🟡 中期（本月）

9. **JWT 30 天有效且无吊销机制**（`AuthProperties.java:9`、`AuthServiceImpl.java:219-226`）：token 泄露即长期冒充，改密/踢人无效。缩短至 7 天+静默刷新，或签名掺 tokenVersion。
10. **admin-web 零测试零 CI**（`web/admin-web/package.json` 仅 dev/build/check:sfc）：生产站回归全靠手点；`check:sfc` 存在却没接 CI。
11. **前端 21 份 `buildBasePortraitPageStyle` 内联复制 + 22 处注释乱码**（`utils/base-page-layout.js:43` 定义却无人引用；乱码导致「为何复制」的决策说明不可读）。同一提交内解决。
12. **样式零变量化**：481 处硬编码色值/34 文件，`uni.scss` 还是脚手架默认值；排球记分板的 CSS 变量体系是现成范式。
13. **读模型 JSON 容错静默降级不落日志**（8 处，如 `MatchServiceImpl.java:626-628`、`JsonUtils.java:21-23`）：脏数据让积分/战报静默变 0，线上无迹可查。

### 🟢 已被既有流程覆盖/确认为安全的事项（无需行动）

- **git 历史密钥泄露 —— 已轮换，历史泄露物无价值**（主 agent 哈希复核：77624fb 提交中的 DB_PASSWORD/JWT_SECRET/WECHAT_APP_SECRET 与当前磁盘值全部不同；archive §4.3 明确记录「泄露物已全部无价值」，filter-repo 清史是纯卫生欠账）。
- SQL 注入面：全仓 0 处 `${}` 用户可控拼接；上传三层防护（白名单+魔数+UUID）；IDOR 全链路 Guard 收口；CORS prod 启动检查拒绝 `*`。
- 并发热点全部有保护（FOR UPDATE 悲观锁 + CAS + 唯一键兜底），无裸读改写窗口。

---

## 3. 分维度详情

### 3.1 后端架构与代码质量（B+）

**好的方面（有证据）**：Controller 极薄（3 个 Controller 全部「鉴权+单行委托」，0 处注入 Mapper）；54 处 `@Transactional(rollbackFor=Exception.class)` 纪律严格且无自调用失效；零 System.out/printStackTrace/TODO；prod 启动器拒绝弱密钥/通配 CORS（`ProductionStartupChecker.java:34-44`）；文档↔代码口径核对高度吻合。

**问题清单**：

| 级别 | 发现 | 位置 |
|---|---|---|
| P1 | prod YAML 缩进地雷（见速览 #1） | `application-prod.yml:27-31` |
| P1 | 兜底异常处理器吞框架异常为 500 | `GlobalExceptionHandler.java:74-79` |
| P2 | 赛制常量跨 6+ 类复制（`SPORT_BADMINTON`×3、`STAGE_GROUP`×5…），注释自认双份 | `TournamentCreationFactory.java:59-94` 等 7 处 |
| P2 | 9 个 500+ 行巨型类：TournamentCreationFactory 1583（构造器 14 参）、TournamentServiceImpl 1385、MatchDetailAssembler 1229、GroupStandingEngine 982、MatchServiceImpl 959、MatchSettlementService 878、TeamMatchServiceImpl 781、TournamentRankingService 774、TournamentDrawService 577 | service 各处 |
| P2 | JSON 容错静默降级 8 处无日志 | `MatchServiceImpl.java:626-628` 等 |
| P2 | `requireMatchForUpdate` 等助手方法 4 类复制 | `MatchServiceImpl.java:510` 等 |
| P3 | 中文写成 Unicode 转义 48 处（2 文件）；业务日志近零（log.warn 全库 1 处）；缩进杂散；命名小瑕疵 | `TeamMatchServiceImpl.java` 等 |

> 对照：巨类拆分在 archive §4.2 已列 backlog（当时数字 1323/1239/1055/882，现已涨到 1583/1385/1229/959 —— **欠账在变大而非收敛**）。

### 3.2 后端安全（B+）

**鉴权模型**：`AuthInterceptor` 只解析 token 不拦截；每个写端点显式 `AuthGuard.requireUserId()`（401），归属校验收拢在 `TournamentAccessGuard`/`MatchAccessGuard`（创建者 + 赛事级 grant 双级模型，不信任任何前端参数）。白名单仅 5 个 auth 端点且均有限流。

**问题清单**：

| 级别 | 发现 | 位置 |
|---|---|---|
| P2 | JWT 30 天不可吊销（payload 仅 userId+exp，无 jti/tokenVersion） | `AuthProperties.java:9`、`AuthServiceImpl.java:219-226` |
| P2 | 写 DTO 无 @Valid/长度约束 | `UpdateTournamentTeamReq.java:9-28` 等 + `TournamentController.java:130-147` |
| P2 | 限流与口令锁定为单机内存态（重启清零；横向扩容会失效） | `FailureLockTracker.java` 类注释 |
| P2 | 当前真实 AppSecret/DB 密码明文存于本机 `backend/local-env.ps1`（不在仓库，但属开发机泄露面） | `local-env.ps1:3-6` |
| P3 | 裁判口令纯数字 8 位（有锁定+限流兜底）；匿名 GET 可枚举 creatorUserId；异常文案回显 tournamentId | `TournamentRefereeService.java:46` 等 |

**未发现问题**（负空间同样重要）：SQL 注入 0、分页攻击面 0（列表固定 LIMIT 200）、错误堆栈泄露 0（500 统一文案）、敏感日志 0、DevMock 严格 @Profile("dev")、无高危依赖（Boot 3.3.5、无 fastjson/log4j2）。

### 3.3 数据库与数据访问层（A）

**五条「读-改-写」热点链路逐一核查（记分/完赛晋级/撤销重开/扫码登录/收藏），未发现可落地的并发窗口**：记分入口 `selectByIdForUpdate` 串行化 + `uk_match_event_seq` 唯一键 409 自愈（`MatchServiceImpl.java:308,341-371`）；晋级父→子逐级锁（`MatchSettlementService.java:119-123`）；扫码登录全 CAS 条件更新（`PcLoginService.java:89-116`）。

| 级别 | 发现 | 位置 |
|---|---|---|
| P2 | Flyway validate 关闭（见速览 #4） | `application.yml:17-19` |
| P2 | `match_record` 热表无任何时间列（V1 建表后至今未补，排查与排序无据） | `V1__init_schema.sql:76-79` |
| P2 | `match_runtime_state` 死表（主代码 0 引用；archive §4.3 记录「已拍板保留」——需补接线计划或改为明确删除） | `V21__add_match_runtime_state.sql` |
| P3 | 死索引 `idx_match_lock_expire_time`；V18 迁移风格异类（TIMESTAMP 2038 隐患，已被 V26 收敛）；枚举载体 TINYINT/VARCHAR 混用；循环单条 INSERT 3-4 处；锁序靠注释约定无集中守护 | 各处 |

**统计**：31 个迁移无断号无冲突；外键类列索引 100% 覆盖；V25 主动清理冗余索引；循环内查询 N+1 = 0；无界查询 = 0（统一 `LIMIT LIST_MAX_ROWS`）；实体↔DDL 抽 8 张表零漂移。

### 3.4 小程序前端（A-）

**好的方面**：全库仅 1 处 `uni.request`（`utils/request.js:66`，零绕过）+ 401 静默重登闭环；错误分级处理范本（`match-guard.js:4-11` 网络失败可重试 vs 无权限不可重试）；事件上云管道完备（800ms 防抖+指数退避+409 自愈+缓存防膨胀）；26 页全部注册且与文档 1:1；3 分包+preloadRule+lazyCodeLoading；细粒度 ref 更新（无大对象赋值）；测试与实现同目录共置。

| 级别 | 发现 | 位置 |
|---|---|---|
| P2 | 21 份 `buildBasePortraitPageStyle` 内联复制（util 存在无人引用，约 800 行重复） | `utils/base-page-layout.js:43` vs 21 个 .vue |
| P2 | 中文注释乱码 22+ 文件（`?????`，决策说明不可读） | `index.vue:77-79` 等 |
| P2 | 481 处硬编码色值/34 文件，uni.scss 为脚手架默认 | `uni.scss:18-21` |
| P3 | 首页「无关键词不出列表」与文档描述不符；请求层无通用防重复提交；快照缓存无 schemaVersion；36 个音频文件占主包；onShow 并发 fetchProfile 无单飞；token 明文存 storage（平台惯例，低风险） | 各处 |

**巨型页面结论**：14 个 800+ 行页面属「逻辑厚但已分层」（状态机/恢复/锁均已抽为可测模块）；**唯一无配套提取模块的是 `pkg-create/index.vue`（2285 行）**，是最值得下一个分解的页面。

### 3.5 API 契约一致性 + admin-web（A）

**63 个端点双向核对：假文档 0、未记录 0**；抽 10 个核心端点字段全部一致（含 2026-10 新增的 `sameToken`/`kicked` 等）；统一信封 100% 覆盖、错误码≡HTTP 状态无 magic number；权限判定全部服务端强制，admin-web 零前端授权；camel↔snake 转换层单一清晰。

| 级别 | 发现 | 位置 |
|---|---|---|
| P2 | GET /auth/pc/status 带副作用（见速览 #6） | `PcLoginService.java:126-127` |
| P3 | 附录 A 缺 13 个组别端点；`team-items/start` 用 PUT 做非幂等创建；createTournament 返回裸 Map 非 VO；admin-web token 存 localStorage（XSS 面）；admin-web 无 .env 文档；nginx /api 反代配置不在仓库；下线端点无 410 过渡 | 各处 |

**建议**：引入 springdoc/OpenAPI 从代码生成接口文档，根治手写漂移。

### 3.6 测试体系、CI 与工程卫生（A-）

**资产**：后端 49 测试类/498 用例/24,161 行（controller 24 + engine 6 + service 12）；前端 30 文件/488 用例含 5 个 fuzzer；夜间千场混沌仿真有连续产物（2026-10-17~26）；断言质量高（真断言非过拟合，BracketEngineTest 钉死 slots 序列）。

| 级别 | 发现 | 位置 |
|---|---|---|
| P1 | CI 不跑 Flyway + schema 双轨已漂移（见速览 #2） | `ci.yml:32-33`、`schema-h2.sql` |
| P2 | CI 无 build:mp-weixin（见速览 #3） | `ci.yml:53-54` |
| P2 | admin-web 零测试零 CI | `web/admin-web/` |
| P2 | 环境变量模板缺 7 项 + DB_URL 缺 connectionCollation（见速览 #7） | `.env.example` |
| P3 | gitleaks 用浮动 latest 镜像无钉版；根 node_modules 为误跑 vitest 残留；测试策略.md 静态计数易过期 | 各处 |

**无测试裸奔清单**：`WechatAccessTokenManager`、`JsonUtils`、`AuthInterceptor`、前端 `request.js`/`share.js`/`base-page-layout.js`、全部 .vue 页面、admin-web 整站。

---

## 4. 跨维度系统性观察（主 agent 综合）

1. **这个仓库的核心竞争力是「纪律」而非「技巧」**：薄 Controller、事务/锁序注释、Guard 收口、测试同置、文档↔代码双向一致（63 端点零漂移）、迁移自文档化。六份子报告不约而同给出「显著高于同规模项目」的评价。
2. **欠账的模式是「最后一公里卫生」**：乱码注释、21 份复制函数、481 硬编码色值、Unicode 转义中文、死索引死表——都是不影响正确性的慢变量债。真正结构性问题只有一个：**CI 验证面与生产运行面不重合**（prod profile 不验证、Flyway 不执行、mp-weixin 不构建、admin-web 不进 CI、H2 与 MySQL 双轨）。prod YAML 地雷正是这个盲区的直接产物。
3. **自查体系成熟，但有「记录≠收敛」倾向**：archive §4.2/4.3 的 backlog 与本次发现重合度高（巨类、VO 收口、错误外泄、死表、清史），其中巨类欠账在过去一个月还在变大（1323→1583）。建议给 backlog 加「恶化报警」：同一文件二次审计行数增长 >15% 即升级优先级。
4. **静默降级是这个代码库最典型的反模式**：8 处 JSON 容错 catch、吞框架异常为 500、Flyway validate 关闭、乱码注释——共同特征是「失败不发声」。补日志/补校验类工作收益极高。

---

## 5. 整改路线图

### 立即（今天，~1 小时）
- [ ] 修复 `application-prod.yml:27-31` 缩进（4 空格对齐），本地以 prod profile 启动验证
- [ ] 顺手删除根目录 `node_modules/`（vitest 误跑残留）

### 本周（每项 ≤ 半天）
- [ ] CI 增 `build:mp-weixin` 与 admin-web `check:sfc + vite build`
- [ ] prod 开启 Flyway `validate-on-migrate`（dev 可保留关闭）
- [ ] 三个写 DTO 补 `@Size/@NotBlank` + Controller 挂 `@Valid`
- [ ] `.env.example`/`local-env.example.ps1` 同步全部 `${VAR}` 并带 connectionCollation
- [ ] GlobalExceptionHandler 补 `HttpMessageNotReadable`/`MissingParameter`/`MaxUploadSize` → 400/413 分支
- [ ] `/auth/pc/status` 改 POST（文档+admin-web api.js 同步）

### 本月
- [ ] CI 加「迁移可执行性」job（H2 跑 Flyway 或 Testcontainers MySQL），消灭 schema 双轨
- [ ] JWT 收紧：7 天 + 静默刷新，或签名掺 tokenVersion
- [ ] 消灭 21 份 base-page-layout 副本 + 恢复 22 处乱码注释（同一提交）
- [ ] 8 处 JSON 静默降级补 `log.warn`
- [ ] 赛制常量下沉单一枚举类（SPORT_*/STAGE_*/TYPE_*）
- [ ] `match_record` 补时间列（V32）+ 处置死索引

### 季度
- [ ] 巨类拆分（后端 TournamentCreationFactory 按运动拆 / MatchDetailAssembler 按渲染块拆；前端 pkg-create/index.vue 抽 create-form.js）——**注意 backlog 恶化趋势**
- [ ] 样式变量层（uni.scss 品牌色/语义色），排球记分板 CSS 变量体系推广
- [ ] springdoc/OpenAPI 自动生成接口文档，替代手写附录 A
- [ ] WechatAccessTokenManager / request.js / AuthInterceptor 补直接单测
- [ ] 音频资源迁分包/CDN；首页列表分页

---

## 6. 附录

### 6.1 主 agent 复核记录（监督职责）

| 子 agent 发现 | 复核方式 | 结论 |
|---|---|---|
| A2（安全）：local-env.ps1 曾否入库未验证 | `git log --all` + 历史与磁盘值 md5 对比 | 曾入库(77624fb)，但三密钥**均已轮换**，历史泄露物无价值；降级为已知欠账（archive §4.3 有记录） |
| A1（架构）：prod YAML 缩进疑似致命 | 亲读 `application-prod.yml` 全文 | **证实**：27 行 4 空格 vs 28-31 行 5 空格，SnakeYAML 必然报错 |
| A6（测试）：schema-h2 缺 match_runtime_state | grep 计数 | **证实**：0 命中，20 vs 21 张表 |
| A1/A4/A6：巨类/死表/VO收口等 | 对照 docs/archive/审查与修复记录.md §4.2-4.3 | 多数与既有 backlog 重合（说明自查有效），但巨类在恶化 |

### 6.2 方法论参考

- [Secure Code Audits in 2026: Checklist and Best Practices — CodeAnt AI](https://codeant.ai/blogs/source-code-audit-checklist-best-practices-for-secure-code)
- [Software Code Audit Services — Softjourn（三阶段审计模型）](https://softjourn.com/code-audit-services)
- [微信小程序安全指引 — 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/framework/security.html)
- [微信小程序安全指引·常见风险及处理建议](https://developers.weixin.qq.com/miniprogram/security/basic/risk.html)
- [微信小程序安全开发、测试记录 — Mysticbinary](https://www.cnblogs.com/mysticbinary/p/18683598)
- [What do you use to audit your codebase? — Reddit r/vibecoding](https://www.reddit.com/r/vibecoding/comments/1mha702/what_do_you_use_to_audit_your_codebase)

### 6.3 执行记录

- 6 个 scout 子 agent 并行扫描（后端架构 43 次工具调用 / 后端安全 55 / 前端 70 / 数据库 78 / API契约 59 / 测试CI 68），首轮 3 个因并发上限失败后重派成功
- 主 agent 独立完成：git 敏感文件跟踪审计、密钥轮换哈希验证、prod YAML 复核、schema 漂移复核、既有知识库交叉对照
