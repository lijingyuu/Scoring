# TESTING.md — 测试指南

> **用途**: 新成员或 AI 知道怎么跑测试、测试架构、覆盖盲区。
> **关联**: [[ARCHITECTURE.md]] · [[DATABASE.md]]

---

## 1. 运行测试

### 后端

```bash
cd backend

# 全量测试
mvn test

# 指定单个测试类
mvn -Dtest=BracketEngineTest test

# 指定一组测试
mvn "-Dtest=MatchWriteAuthIntegrationTest,MatchLineupConfigIntegrationTest,MatchEventIntegrationTest" test

# 跳过集成测试，只跑单元测试
mvn test -DexcludedGroups=integration
```

当前源码静态统计：**235 个后端测试声明**（按 `@Test` / `@ParameterizedTest` 统计）。实际通过情况以本地执行 `mvn test` 为准；`MatchThemeConfigIntegrationTest` 因配色接口废弃保持禁用。

### 前端

```bash
npm.cmd test                 # Windows 下运行全部前端单元测试（vitest）
npm.cmd run test:watch       # Windows 下 watch 模式
```

Windows 沙箱中固定使用 `npm.cmd`，避免 PowerShell 对 `npm.ps1` 的执行策略拦截。

当前源码静态统计：**160 个前端用例**（7 个测试文件）。实际通过情况以本地执行 `npm test` 为准。
测试框架：vitest + jsdom + `@vue/test-utils`。
配置：`vitest.config.js`，路径别名 `@` → `src/`。

---

## 2. 测试架构

### 2.1 分类

| 类型 | 位置 | 特点 |
|------|------|------|
| **单元测试（白盒）** | `*Test.java` | 纯 Java，不启动 Spring，使用 Mockito mock 依赖 |
| **集成测试（黑盒）** | `*IntegrationTest.java` | `@SpringBootTest` + H2 内存库 + `MockMvc` |
| **前端单元测试** | `src/**/*.test.js` | 纯函数测试，vitest |

### 2.2 集成测试配置

所有集成测试共享以下模式：

```java
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:<unique_db_name>;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.flyway.enabled=false",                    // 禁用 Flyway
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:schema-h2.sql",  // 用 H2 兼容 schema
    "app.rate-limit.enabled=false",                   // 禁用限流
    "app.auth.jwt-secret=test-secret"
})
```

关键约定：
- **每个集成测试类用独立的 H2 数据库名**（`DB_CLOSE_DELAY=-1` 保持连接），避免测试间数据污染
- **Flyway 禁用**，改用 `schema-h2.sql` 直接建表（H2 不完全兼容 MySQL DDL）
- **AuthService 用 `@MockBean` mock**，`verifyToken` 返回固定 userId
- **限流器禁用**，避免干扰功能测试

### 2.3 测试辅助工具

```java
// Mock AuthService 模拟已登录用户
@MockBean
private AuthService authService;

@BeforeEach
void setUp() {
    when(authService.verifyToken(anyString())).thenReturn("user-1");
}

// 构建测试用户
private User buildUser(String id, String openid, boolean profileCompleted) {
    User user = new User();
    user.setId(id);
    user.setOpenid(openid);
    user.setNickname(id);
    user.setAvatarUrl("https://example.com/avatar.png");
    user.setProfileCompleted(profileCompleted);
    return user;
}
```

---

## 3. 测试覆盖现状

### 3.1 后端覆盖

| 模块 | 测试数 | 覆盖质量 | 类型 |
|------|--------|---------|------|
| `BracketEngine` | 15 | ⭐⭐⭐⭐⭐ | 单元 |
| `RoundRobinEngine` | 21 | ⭐⭐⭐⭐ | 单元 |
| `ApiResponse` | 3 | ⭐⭐ | 单元 |
| `TournamentService` (Mock) | 6 | ⭐⭐ | 单元 |
| `MatchServiceImpl` (Mock) | 23 | ⭐⭐⭐⭐ | 单元 |
| `AuthServiceImpl` (Mock) | 13 | ⭐⭐⭐⭐ | 单元 |
| `UserServiceImpl` (Mock) | 6 | ⭐⭐⭐⭐ | 单元 |
| `TournamentController` (集成) | 32 | ⭐⭐⭐⭐⭐ | 集成 |
| `BadmintonTeamTournament` (集成) | 16 | ⭐⭐⭐⭐ | 集成 |
| `RoundRule` (集成) | 8 | ⭐⭐⭐⭐ | 集成 |
| `MatchWriteAuth` (集成) | 13 | ⭐⭐⭐⭐⭐ | 集成 |
| `MatchEvent` (集成) | 3 | ⭐⭐⭐⭐ | 集成 |
| `MatchLineupConfig` (集成) | 9 | ⭐⭐⭐⭐⭐ | 集成 |
| `MatchFinish` (集成) | 12 | ⭐⭐⭐⭐⭐ | 集成 |
| `TournamentFavorite` (集成) | 8 | ⭐⭐⭐⭐ | 集成 |
| `MatchThemeConfig` (集成) | 6 | ⛔ 已禁用 | 集成 |
| `RequestRateLimit` (集成) | ~2 | ⭐⭐⭐ | 集成 |

### 3.2 前端覆盖

| 模块 | 测试数 | 状态 |
|------|--------|------|
| `match-state.js` | 40 | ✅ — 状态创建/归一化/换边/旧缓存迁移/边界值 |
| `relay-scoring.js` | 76 | ✅ — 接力分段计算/链条验证/段切换/分数快照 |
| `tournament-navigation.js` | 15 | ✅ — 路由归一化/页面栈遍历/跳转解析 |
| `groups-data.js` | 4 | ✅ — 小组赛数据聚合/排名计算 |
| `volleyball-team.js` | 8 | ✅ — 排序逻辑/队长查找/空输入 |
| `interaction-guard.js` | 10 | ✅ — useDelayedTapGate 时序/useActionLock 锁定与释放 |
| `match-guard.js` | 7 | ✅ — requireMatchOperator 权限守卫 |
| `request.js` | 0 | ❌ — 需要 mock uni API，待补 |

---

## 4. 已知问题

| 问题 | 说明 |
|------|------|
| `MatchThemeConfigIntegrationTest` 被 `@Disabled` | 配色接口 `PUT/GET /theme-config` 已废弃（前端改为硬编码），测试随接口一起禁用 |
| 后端测试统计 | 源码内约 235 个测试声明；执行结果以 `mvn test` 为准 |
| 前端测试已配置 vitest | ✅ `npm test` 可用；源码内 7 个测试文件、160 个用例 |
| 集成测试 mock 了 AuthService | 真实微信 code→openid→JWT 链路未覆盖，需 E2E 测试或手动验证 |

---

## 5. 补测优先级路线图

### ✅ 本轮已完成（P0 + P1 全覆盖）

**P0 后端白盒**
- `MatchServiceImplTest` — finishMatch 7种校验 + 晋级 + 退赛 + 事件幂等 → 12 用例
- `AuthServiceImplTest` — JWT 签发/验证 + mock openid + token 篡改 → 7 用例
- `UserServiceImplTest` — 获取/更新 profile + trim + 用户不存在 → 6 用例

**P0 前端纯函数**
- `match-state.test.js` — 状态归一化/换边/旧缓存迁移/URL → 40 用例
- `volleyball-team.test.js` — 排序/队长查找/空输入 → 8 用例
- `interaction-guard.test.js` — useDelayedTapGate 时序/useActionLock → 10 用例
- vitest + `npm test` 脚本 → 前端测试基础设施

**P1 后端集成测试**
- `MatchFinishIntegrationTest` — finishMatch 正常3:1 + 决赛结束赛事 + 退赛 + 负局分拒绝 + wrongWinnerSide + 平局拒绝 + winnerMismatch + 缺gameScores → 8 用例
- `TournamentFavoriteIntegrationTest` — 收藏/幂等/取消/我的收藏/空列表/我创建的/多用户独立 → 7 用例
- `RoundRobinEngineTest` 补全 — 3人/6人/ID唯一性/roundNum+stageType/每轮分布/1人抛异常/空列表抛异常 → 当前 21 用例

### 📋 待补（后续）

| 优先级 | 模块 | 预计测试数 | 难度 |
|--------|------|-----------|------|
| P1 | `buildRotationGrid` 单元测试 | ~8 | 高（300行复杂逻辑） |
| P1 | TeamMatchServiceImpl 单元测试 | ~10 | 中 |
| P1 | 团体赛回合集成测试 | ~8 | 中 |
| P2 | `request.js` | ~5 | 高（需 mock uni API） |
| P2 | 接力赛记分板集成测试 | ~6 | 中 |
| P2 | E2E 测试（微信登录→创建赛事→记分→结算） | ~3 | 高 |

---

## 6. 编写测试约定

### 单元测试

```java
// 命名: <被测类名>Test.java
// 方法命名: <方法名>_<场景>_<预期结果>
// 使用 @ExtendWith(MockitoExtension.class)
// 只 mock 外部依赖（Mapper），不 mock 被测类

@Test
void finishMatch_withValidRequest_shouldUpdateMatchAndPropagateWinner() {
    // Given
    // When
    // Then
}
```

### 集成测试

```java
// 命名: <被测模块>IntegrationTest.java
// 每个测试独立准备数据（@BeforeEach 清理 + 插入）
// 使用 MockMvc 模拟 HTTP 请求
// 用 jsonPath 断言响应
// 用 Mapper 直接读数据库断言副作用
```

### 前端测试（规划）

```js
// 命名: <被测模块>.test.js
// 使用 vitest + jsdom
// 只测纯函数，不测 Vue 组件渲染
```

---

## 7. 测试数据清理

集成测试使用 H2 内存数据库，每次 `@BeforeEach` 显式删除所有表数据：

```java
@BeforeEach
void setUp() {
    matchEventMapper.delete(new QueryWrapper<>());
    matchLineupConfigMapper.delete(new QueryWrapper<>());
    matchRecordMapper.delete(new QueryWrapper<>());
    tournamentTeamMemberMapper.delete(new QueryWrapper<>());
    playerMapper.delete(new QueryWrapper<>());
    tournamentMapper.delete(new QueryWrapper<>());
    userMapper.delete(new QueryWrapper<>());
    // ... 插入基础测试数据
}
```

> 注意：删除顺序必须从子表到父表（外键依赖），或使用 H2 的 `SET REFERENTIAL_INTEGRITY FALSE`。

## 8. 小组赛排名模板的最小验证

新增默认模板时，至少验证以下闭环：

1. 前端排名模式选项只包含当前赛制允许的 criterion，并排除其他赛制的层级指标。
2. 前端小组排名表只显示当前模板对应的统计列。
3. 后端 `RankingConfig.preset()` 的优先级、数学类型、退赛策略和 JSON 回显一致。
4. `GroupStandingEngineTest` 覆盖模板优先级；若涉及多队并列，增加实际比分排序用例。
5. 若模板通过创建页或自定义页保存，补一个创建/更新后 GET 回显的集成验证。

排名模板变更的最小命令示例：

```powershell
npm.cmd test -- src/pages/ranking/ranking-options.test.js src/pages/tournament/groups-data.test.js
cd backend
mvn -Dtest=GroupStandingEngineTest test
```
