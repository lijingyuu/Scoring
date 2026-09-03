# TECH_STACK.md — 技术栈复盘与掌握指南

> **用途**: 面向项目作者本人（软件工程学生）的「技术点复盘」文档。逐个讲清楚本项目**用到了哪些技术/库/框架/设计模式，以及在本项目里具体是怎么落地的**——重点是"在我这个项目里怎么用"，而不是这项技术本身是干嘛的（那些随手可查）。
> **定位**: 学习/复盘文档，不是开发规范。写作原则：每一条都要落到**真实文件路径 + 关键代码片段**。
> **关联**: [[ARCHITECTURE.md]] · [[DATABASE.md]] · [[BUSINESS_RULES.md]] · [[TESTING.md]]

---

## 0. 项目技术全景一句话

**后端** Spring Boot 3.3.5（Java 17）+ MyBatis-Plus 3.5.7 + MySQL 8.0 + Flyway（19 个迁移版本）+ JWT（auth0 java-jwt）+ Hutool + H2（测试）＋ **前端** uni-app（Vue 3 + Vite）+ Vitest。一个「无 Pinia、无 Docker、无前端重型框架」的轻量级前后端分离赛事记分系统。

---

## 1. 后端技术点

### 1.1 Spring Boot 3.3.5 与 starter

**怎么用的**：以 `spring-boot-starter-parent` 做版本仲裁（`backend/pom.xml`），靠 starter 拉起三大能力：

| starter | 在本项目实际用途 |
|---------|-----------------|
| `spring-boot-starter-web` | 提供 `@RestController`、`HandlerInterceptor`、`OncePerRequestFilter`、`CorsRegistry` |
| `spring-boot-starter-validation` | `@Valid` + `jakarta.validation.*`（`@NotBlank`/`@NotNull`/`@Positive`），见 `CreateTournamentReq`、`SaveMatchEventsReq` |
| `spring-boot-starter-test` | JUnit 5 + Mockito + Spring Test + MockMvc |

**Java 17 特性落地**（`<java.version>17</java.version>`）：
- `record`：`BracketEngine.GroupRank/KnockoutPlan`、`RankingConfig.PointsSystem`
- `switch` 表达式：`GroupStandingEngine.compareScalar`、`RankingConfig.preset`
- 文本块：集成测试里的 JSON 请求体 `"""..."""`
- `List.of()` / `.toList()`

**关键文件**：`backend/pom.xml`、`ScoringBackendApplication.java`

---

### 1.2 MyBatis-Plus 3.5.7（ORM）

**怎么用的**：用 `mybatis-plus-spring-boot3-starter`（专门适配 Spring Boot 3 的变体），实体注解 + `BaseMapper` 免写大部分 SQL。

**实体注解体系**（`domain/entity/*.java`）：
- `@TableName("app_user")`：`user` 是 MySQL 保留字，映射到 `app_user`（`User.java`）
- `@TableId(type = IdType.ASSIGN_ID)`：雪花算法 19 位数字 ID
- `@TableField("snake_case")`：显式映射下划线列
- `@TableField(exist = false)`：非持久化字段（`Tournament.favorite`/`creator` 是查询时临时填充的布尔标记）
- `@TableField(fill = FieldFill.INSERT / INSERT_UPDATE)`：配合自动填充

**自动填充**：`config/MybatisMetaObjectHandler.java` 实现 `MetaObjectHandler`，用 `strictInsertFill`/`strictUpdateFill` 填 `createTime/updateTime`。

**自定义 SQL**（不走 `BaseMapper` 默认方法）：
```java
// TournamentMapper — 悲观锁（并发写比分时串行化）
@Select("SELECT * FROM tournament WHERE id = #{id} FOR UPDATE")
Tournament selectByIdForUpdate(@Param("id") String id);

// 原子自增收藏数
@Update("UPDATE tournament SET favorite_count = favorite_count + 1 WHERE id = #{id}")
```

**条件构造器**：
- `LambdaQueryWrapper<User>().eq(User::getOpenid, openid)` — 类型安全、方法引用传列
- `QueryWrapper<MatchEvent>().eq("match_id",...).in("event_seq", ...)` — 字符串列名

**全局配置**（`application.yml`）：`id-type: assign_id`、`map-underscore-to-camel-case: true`、`log-impl: StdOutImpl`；启动类 `@MapperScan("com.scoring.backend.mapper")` 统一扫描。

**⚠️ 两个重要澄清（别张冠李戴）**：
1. **没有用逻辑删除** `@TableLogic`（全库无 `deleted` 字段，收藏取消是物理 `delete`）。
2. **没有用分页插件**（无 `MybatisPlusInterceptor` / `PaginationInnerInterceptor` / `IPage`）。

**ID 细节**：实体入库走 `ASSIGN_ID`（雪花 19 位数字），但 **Engine 生成比赛记录时用的是 Hutool `IdUtil.simpleUUID()`**（32 位无横线 UUID），两者都存进 `VARCHAR(32)`。

---

### 1.3 Flyway 数据库迁移（19 个版本）

**怎么用的**：版本化 schema 演进，`V<版本号>__<描述>.sql` 命名，位于 `db/migration/`。

**V1~V19 演进脉络**（一句话概括每个版本解决了什么问题）：

| 版本 | 内容 | 对应产品能力 |
|------|------|-------------|
| V1 | 5 张核心表 | 基础 MVP |
| V2 | `sport_type` + `tournament_team_member` | 排球支持 |
| V3 | `match_lineup_config` | 排球每局阵容 |
| V4 | `match_event` | 事件流同步 |
| V5/V6 | 配色主题表 | （后废弃，配色改前端硬编码） |
| V7 | `match_report_meta` | 战报元数据 |
| V8 | 裁判配置/授权两表 | 裁判鉴权 |
| V9 | `round_robin_rounds` | 循环赛 |
| V10 | `archived` | 归档 |
| V11 | `participant_type` | 个人/团体 |
| V12 | `team_match_template` | 苏杯/接力追分 |
| V13 | `team_match_item` | 团体赛子比赛 |
| V14 | `username/password_hash` | 账号密码登录 |
| V15 | `tournament_round_rule` | 逐轮规则 |
| V16 | `loser_next_match_*`、`third_place_*` | 三四名决赛 |
| V17 | `tournament_ranking_config` | 排名模板持久化 |
| V18 | `tournament_qualification_override` | 手动晋级覆盖 |
| V19 | `meta_json` 扩容 MEDIUMTEXT | 战报拆分主裁/副裁 |

**多环境切换**（关键工程实践）：
- **生产**：MySQL + Flyway 开启（`validate-on-migrate: false`，`spring.sql.init.mode: never` 避免双重建表）。
- **测试**：**禁用 Flyway**，改用 H2 方言的 `schema-h2.sql`（`BOOLEAN` 而非 `TINYINT(1)`），在集成测试 `@TestPropertySource` 里显式写 `spring.flyway.enabled=false` + `spring.sql.init.schema-locations=classpath:schema-h2.sql`。

---

### 1.4 JWT（auth0 java-jwt 4.4.0）

**怎么用的**：无状态登录凭证，HS256 对称签名。

```java
// AuthServiceImpl 构造时创建算法与校验器，作为单例字段复用
this.algorithm = Algorithm.HMAC256(resolveJwtSecret(authProperties));
this.verifier  = JWT.require(algorithm).build();

// 签发
return JWT.create().withClaim("userId", userId).withExpiresAt(expireAt).sign(algorithm);

// 校验
jwt.getClaim("userId").asString();  // 捕获 JWTVerificationException → 登录态失效
```

- **过期时间**：`app.auth.jwt-expire-seconds` 默认 `2592000`（30 天）。
- **生产守护**：`ProductionStartupChecker` 强制 `jwt-secret` ≥ 32 字符且非默认值，否则启动失败。

---

### 1.5 鉴权四件套分工（本项目最有代表性的设计）

把「解析谁在访问」和「判定允不允许」拆成四个组件各司其职：

| 组件 | 职责 | 关键点 |
|------|------|--------|
| `AuthContext` | `ThreadLocal<String>` 存请求级 userId | 只有 `set/get/clear` 三个静态方法 |
| `AuthInterceptor` | 读 `Authorization: Bearer` → `verifyToken` → 塞进 ThreadLocal | **只解析不强制**，无 token 也放行 |
| `AuthGuard.requireUserId()` | 真正「强制」点 | 空就抛 `UnauthorizedException("请先登录")` |
| `DevMockAuthFilter` | 仅 dev，注入 mock token | `HttpServletRequestWrapper` 覆写 `getHeader`，解决 H5 无法 `uni.login` 的调试痛点 |

**核心思想（值得掌握）**：读接口用可空的 `AuthContext.getUserId()`（游客可看），写接口用 `authGuard.requireUserId()`（必须登录）——"拦截器只解析、Guard 才强制"。

**密码哈希**：`cn.hutool.crypto.digest.BCrypt`（`BCrypt.hashpw` / `BCrypt.checkpw`），不是 MD5/SHA。

---

### 1.6 Lombok（⚠️ 引入了但没真正用）

`pom.xml` 声明了 `lombok`（`<optional>true</optional>`），但**全仓库零 import、零 `@Data/@Getter/@Slf4j`**——实体/DTO/VO 全部手写 getter/setter，日志用 `org.slf4j.LoggerFactory`。复盘时如实写"Lombok 属于引入未采用"。

---

### 1.7 Hutool 5.8.30 工具类归位

| 工具类 | 本项目实际用途 | 位置 |
|--------|---------------|------|
| `StrUtil` | 判空/trim/用户名规范化 | AuthServiceImpl、DevMockAuthFilter |
| `BCrypt` | 密码哈希 | AuthServiceImpl |
| `HttpUtil` | 调微信 `jscode2session` 换 openid | AuthServiceImpl.fetchOpenid |
| `JSONUtil/JSONObject/JSONArray` | 解析微信返回、排名规则 `toJson/fromJson`、解析 `gameScores` | RankingConfig、GroupStandingEngine |
| `CollUtil` | 集合判空 | BracketEngine、RoundRobinEngine |
| `Assert` | 前置条件校验 | BracketEngine、RoundRobinEngine |
| `IdUtil.simpleUUID()` | 生成比赛记录 ID | BracketEngine、RoundRobinEngine |

---

### 1.8 测试体系（H2 + MockMvc + Mockito）

**怎么用的**（三层测试架构，见 [[TESTING.md]]）：

1. **纯算法单测**：`BracketEngineTest`/`RoundRobinEngineTest`/`GroupStandingEngineTest`，无 Spring 依赖。
2. **Service 单测**：`@ExtendWith(MockitoExtension.class)` + `@Mock UserMapper`，手动 `new` Service，`ArgumentCaptor` 抓 insert 对象、`assertThrows` 断言异常。
3. **集成测试**：`@SpringBootTest + @AutoConfigureMockMvc + @TestPropertySource`（H2 内存库 + 禁用 Flyway + 禁用限流）+ `@MockBean AuthService` 绕过真实 JWT + `MockMvc` 发请求 + `jsonPath` 断言 + Mapper 直查库断言副作用。

---

### 1.9 统一响应 / 全局异常 / 请求日志 / 限流器

- **统一响应** `common/ApiResponse.java`：`{code, message, data}`，`code===0` 成功。前端 `request.js` 靠它判定。
- **全局异常** `GlobalExceptionHandler`（`@RestControllerAdvice`）异常→HTTP code 映射：`IllegalArgumentException→400`、`UnauthorizedException→401`、`TooManyRequestsException→429`、`MethodArgumentNotValidException→400`、兜底 `Exception→500`。
- **请求日志** `RequestLoggingFilter`（`OncePerRequestFilter`）：记录 `method/path/status/costMs/clientIp`。
- **限流器** `RequestRateLimiter`：**手写固定窗口计数器**，`ConcurrentHashMap<String, Counter>` + `synchronized`，登录端点/写方法分别限流，超限抛 429。配套 `ClientIpResolver` 解析 `X-Forwarded-For`。

---

### 1.10 Engine 层独立算法（纯算法、无 DB 依赖）

**BracketEngine（淘汰赛）**：
- **种子排表递归** `buildSeedOrder`：分治递归，`p=8 → [1,8,4,5,2,7,3,6]`，保证相邻两两配对和为 `p+1`（1/2 号分上下半区）。
- **2 的幂扩容 + 轮空**：补到 2 的幂，种子序号 > n 的位置留 null 当 bye，`propagateWinnerToParent` 递归坍缩轮空。
- **父子链**：`nextMatchId`/`nextMatchSlot`，决赛 `nextMatchId=null`。

**RoundRobinEngine（循环赛）**：**圆桌轮转法** `rotateKeepingFirst`（固定第一个，其余轮转），奇数人补 null 当 bye，单/双循环靠 `swapHomeAway` 交换主客。

**GroupStandingEngine + RankingConfig（排名）**：可插拔积分规则（`Criterion` 指标 + `Template` 预设 + `MathType` 差值/比值 + `WithdrawPolicy` 退赛策略），核心是「逐级分块比较 + 相互战绩递归」，`BigDecimal` 算胜负率（分母 0 用哨兵值 `999999.0000`）。

> 设计价值：把赛程生成/排名从 Service 抽成**无 Spring Bean、无 DB** 的纯算法，可脱离容器单测。

---

### 1.11 事务与并发控制

- 全部用 `@Transactional(rollbackFor = Exception.class)`（显式扩大为所有异常回滚，默认只回滚 RuntimeException）。
- 配合**悲观锁**：`updateMatchResult` 先 `SELECT ... FOR UPDATE` 再改比分，串行化并发写。
- 位置集中在 Service 写方法（`MatchServiceImpl`/`TeamMatchServiceImpl`/`TournamentServiceImpl`/`UserServiceImpl`）。

---

### 1.12 CORS / 拦截器注册 / 配置类

- `WebMvcConfig`（`WebMvcConfigurer`）：`addInterceptors` 注册限流器 + 鉴权器（`/api/**`，限流在前鉴权在后）；`addCorsMappings` 从 `CorsProperties` 读白名单。
- 配置类全用 `@ConfigurationProperties(prefix="app.*")`：`AuthProperties`/`WechatProperties`/`RateLimitProperties`/`CorsProperties`。
- `ProductionStartupChecker`（`@Profile("prod")` + `ApplicationRunner`）：启动时强校验生产必需项（JWT_SECRET、微信凭证、CORS 白名单）。

---

## 2. 前端技术点

### 2.1 uni-app（一套 Vue 代码多端编译）

**怎么用的**：
- `src/pages.json`：24 个页面路由 + 2 个 TabBar + `globalStyle.navigationStyle: custom`。
- **横竖屏**：`pageOrientation: "landscape"` 给计分板类页面（`scoreboard/index`、`volleyball/scoreboard`、`team-relay`、`signature/index`）；`portrait` 给填写类页面（`volleyball/lineup`、`volleyball/record`、`team-record` 等）。
- `src/manifest.json`：`appid`、`vueVersion: "3"`、`mp-weixin.resizable`。
- **条件编译** `// #ifdef H5 ... // #endif`（`useScoreboard.js`、`record.vue`）。
- **uni.* API 落地**：`uni.request/login/getStorageSync/setStorageSync/removeStorageSync/showToast/navigateBack/createCanvasContext/canvasToTempFilePath/getFileSystemManager/$emit/$on`。
- **rpx** 单位 + `env(safe-area-inset-*)` 适配刘海屏。

---

### 2.2 Vue 3 组合式 API + composable 模式

**怎么用的**：
- `<script setup>` + `ref/reactive/computed/watch`；`onLoad/onShow/onUnload/onBackPress/onReady/onResize` 来自 `@dcloudio/uni-app`，`onMounted/onUnmounted` 来自 `vue`。
- **核心 composable** `useScoreboard.js`（2200+ 行）：整块排球计分状态机（比分/局分/阵容/发球权/暂停/事件队列/历史栈）都在这里，几十个 `ref` + 十几个 `computed`，返回一个大对象；`ScoreboardPad.vue`/`ScoreboardPhone.vue` 只从 `ctx` 取数据渲染。
- **小型 composable**（`utils/interaction-guard.js`）：
  - `useDelayedTapGate(source, delay)`：弹窗弹出后延迟 120ms 才允许点击，防误触。
  - `useActionLock(defaultDuration)`：动作锁防连点。

> 设计价值：**Phone/Pad 只做 UI 差异，逻辑 100% 复用**——这是 composable 模式最典型的落地场景。

---

### 2.3 无 Pinia/Vuex —— `reactive()` 轻量 store

`package.json` 无 pinia/vuex。全局状态是 `src/store/auth.js` 里的**模块级 `reactive({...})` 单例** + 导出函数操作 state（"模块即 store"）：
- 对外只暴露 `ensureAuth/requireProfile/guardProfileBeforeAction/submitProfile/bootstrapAuth` 等函数。
- token 持久化到 `uni.setStorageSync('scoring_token')`。
- **Promise 化的授权流程**：`ensureAuth`/`requireProfile` 用模块级 Promise 做「去重 + 等待弹窗结果」，避免重复弹窗。

---

### 2.4 Vite + Vitest + jsdom

- `vite.config.js`：`@dcloudio/vite-plugin-uni` + dev 代理 `/api → http://127.0.0.1:8080`。
- `vitest.config.js`：`test.environment: 'jsdom'`、`globals: true`、路径别名 `@ → src`。
- 测试脚本：`npm test` = `vitest run`；共 9 个 `*.test.js`。

---

### 2.5 纯函数模块设计（可单测的逻辑抽取）

把与 UI 无关的规则抽成纯函数，JS 单测零环境成本：
- `match-state.js`：状态工厂 / 深拷贝规范化 / 换边 / 历史快照 / Storage 读写（key = `volleyball_scoreboard_state_<matchId>`）。
- `relay-scoring.js`：接力分段计算 / 链条生成与校验 / 视觉边↔逻辑边。
- `knockout-bracket-layout.js`：对阵图缩放平移的 `clamp` 数学。
- `groups-data.js`、`ranking-options.js`、`tournament-navigation.js`、`volleyball-team.js`、`query.js`。

---

### 2.6 设备自适应（isTablet + 六级尺寸带 + clamp 流体尺寸）

- **isTablet 判定**：`Math.min(windowWidth, windowHeight) >= 720`（以短边判断 Pad）。
- **六级尺寸带**：`phone / pad-portrait-sm(≤820) / pad-portrait-lg / pad-landscape-sm(≤1228) / pad-landscape-md(≤1400) / pad-landscape-lg`，拼成 class 挂根节点，CSS 按类名差异化。
- **`clamp()` 流体尺寸 + CSS 变量**：`--score-value-text: clamp(36px, 7.2vmin, 96px)`，按尺寸带/`@media` 覆盖。
- `syncWindowMetrics`：`uni.getWindowInfo()` + `onResize` 实时更新窗口尺寸。

---

### 2.7 状态持久化（Storage 缓存 + 撤销历史栈）

- 计分状态写 `uni.setStorageSync`，读时 `normalizeMatchState` 兜底非法数据。
- **清缓存规范**（CLAUDE.md 强调）：本地缓存是当前设备的比赛恢复现场，权限拒绝或资料未完善时只拦截返回、不清缓存；仅在结算成功、重新开始、用户明确放弃时调 `clearMatchState(matchId)`。
- **撤销历史栈**：每次改分/暂停/退赛前 `pushHistory()` 存快照，`slice(-40)` 限 40 条；`undo()` 弹出栈顶恢复 + 重新持久化 + 尽快同步。

---

### 2.8 事件同步防抖（800ms 批量提交 + 幂等 upsert）

**怎么用的**（弱网/高频记分场景的经典范式）：
- **前端**：`appendMatchEvent` 生成带自增 `seq` 的事件，`scheduleEventFlush(800)` 防抖批量提交，成功才标 `synced`；换边/撤销等关键动作缩短到 200ms。
- **后端**：按 `(match_id, event_seq)` 幂等 upsert——已存在的 seq 跳过，重放不产生重复记录。

> 这个「前端防抖 + seq 自增 + 后端按 seq 去重」的跨端协议，是最值得掌握的分布式/弱网场景范式之一。

---

### 2.9 canvas 签名

**怎么用的**（`pages/signature/index.vue`）：**双 canvas 方案**——可见 canvas 触摸绘制（`@touchstart/move/end` 采集轨迹点 + `strokes` 存笔画），离屏 canvas 导出（`canvasToTempFilePath` 固定 960×320）；横竖屏切换时 `scaleStrokeList` 按比例缩放已有笔画重绘；图片转 base64 后通过 `uni.$emit` 回传（事件 key 由 `signature-capture.js` 生成）。

---

## 3. 前后端通用契约

- **统一响应**：后端 `ApiResponse{code:0,message,data}` ↔ 前端 `request.js` 判 `body.code===0` 才 resolve `body.data`，否则 toast + reject（`silent` 参数抑制 toast）。
- **ID 约定**：雪花 19 位数字字符串 + Engine 的 Hutool UUID，统一 `VARCHAR(32)`、JSON 以字符串传输。
- **Bearer Token**：前端 `request.js` 自动读 storage 注入 `Authorization: Bearer`，后端 `AuthInterceptor` 解析；401/登录态失效识别后清 token 重登。
- **路径别名**：前端 `@ → src`。

---

## 4. 最值得重点掌握的 15 个技术点（按项目内重要度排序）

1. **鉴权四件套分工**（拦截器只解析塞 ThreadLocal、Guard 才强制、Context 存用户、Mock 过滤器注入）——关注点分离最典型的落地。
2. **JWT 签发/校验闭环**（HMAC256 + claim + 过期 + 拦截器联动 + 生产 secret 守护）。
3. **统一响应 + 全局异常处理**：`{code,message,data}` 协议如何让前后端错误处理变简单。
4. **composable 模式**：2200 行 `useScoreboard.js` 状态机如何与 UI 解耦（Phone/Pad 复用）。
5. **纯函数抽取 + Vitest 单测**：把规则算成无依赖纯函数，测试成本极低。
6. **事件防抖批量同步 + 幂等 upsert**：前端 800ms 防抖 + seq，后端按 event_seq 去重。
7. **MyBatis-Plus 注解体系**：`@TableName/@TableId(ASSIGN_ID)/@TableField(fill)/@TableField(exist=false)` + `MetaObjectHandler` 自动填充。
8. **悲观锁 + @Transactional**：`SELECT ... FOR UPDATE` + `rollbackFor=Exception.class` 保证并发写比分安全。
9. **Engine 层算法**：种子排表递归、循环赛圆桌轮转、积分榜逐级分块 + 相互战绩递归。
10. **Flyway 多环境切换**：生产 MySQL + Flyway，测试禁用 Flyway 用 `schema-h2.sql`。
11. **手写限流器**：固定窗口 + `ConcurrentHashMap` + 拦截器。
12. **Vue3 无框架轻量 store**：`reactive()` 模块单例替代 Pinia，含 Promise 化授权流程。
13. **设备自适应**：`isTablet` 短边判定 + 六级尺寸带 + `clamp()` + CSS 变量主题。
14. **canvas 签名**：双 canvas（交互绘制 + 离屏导出）+ 笔画缩放重绘 + base64 上传。
15. **Hutool 工具库正确用法**：`StrUtil/BCrypt/HttpUtil/JSONUtil/CollUtil/IdUtil/Assert` 各归其位。

---

## 5. 认知纠偏（容易误判的点）

| 说法 | 实际情况 |
|------|---------|
| "用了 Lombok" | ❌ `pom.xml` 声明了但**零使用**，全手写 getter/setter |
| "MyBatis-Plus 逻辑删除" | ❌ 未使用 `@TableLogic`，收藏取消是物理 delete |
| "MyBatis-Plus 分页插件" | ❌ 未配置，无 `IPage` |
| "用了 Pinia/Vuex" | ❌ 用 `reactive()` 模块单例替代 |
| "ID 都是雪花算法" | ⚠️ 实体入库是雪花 ID，但 Engine 生成比赛记录用 Hutool `simpleUUID()` |
| "所有异常都回滚" | ⚠️ 用 `rollbackFor=Exception.class` 才覆盖受检异常，默认只回滚 RuntimeException |
