# 🔍 记分系统 上线前完整审计报告

> 生成日期：2026-06-18
> 分支：dev/mock-logi

---

## 一、项目全貌

| 维度 | 详情 |
|------|------|
| 后端 | Spring Boot 3.3.5 / Java 17 / MyBatis-Plus / MySQL / Flyway |
| 前端 | uni-app (Vue 3 + Vite) / 微信小程序 + H5 |
| 运动类型 | 羽毛球(淘汰赛/小组+淘汰) + 排球(淘汰赛/小组+淘汰) |
| 部署 | 无Docker，直接jar部署，通过环境变量配置 |
| 数据库表 | 10张 (7个Flyway迁移) |
| Java源文件 | 65个(主) + 8个(测试) |
| API端点 | 3个Controller, 约20个端点 |

---

## 二、18条完整逻辑链分析

---

### 🔗 链路1：用户认证与授权链

```
前端 ensureAuth() → uni.login获取code → POST /auth/wechat-login
→ AuthServiceImpl.loginWithCode() → fetchOpenid(微信API)
→ 查/创User → signToken(JWT HMAC256) → 返回token
↓
每次请求: AuthInterceptor.preHandle() → 解析Authorization头
→ authService.verifyToken() → AuthContext.setUserId()
↓
Controller: AuthGuard.requireUserId() → AuthContext.getUserId()
→ 空则抛UnauthorizedException
↓
请求结束: AuthInterceptor.afterCompletion() → AuthContext.clear()
```

**🔴 潜在问题:**

1. **ThreadLocal泄漏风险** — `AuthContext.clear()` 只在 `afterCompletion` 中调用。如果请求处理中抛出未被拦截的 `Error`（如 `OutOfMemoryError`），ThreadLocal 不会被清理。线程池复用场景下，下一个请求可能拿到上一个用户的 userId。建议加 `Filter` 做 finally 清理。

2. **JWT无刷新机制** — token 默认30天过期，没有 refresh token。30天后用户必须重新登录，体验割裂。

3. **`DevMockAuthFilter` 注入Authorization头的安全问题** — 使用 `HttpServletRequestWrapper.getHeader()` 覆盖，但 `AuthInterceptor` 中直接读 `request.getHeader("Authorization")`，如果某些中间件缓存了原始header，mock可能失效。实测可行但脆弱。

4. **DevMockAuthFilter 硬编码用户ID** — `selectById("2060587136674361346")` 写死了特定用户ID，换数据库就失效。

**🟡 冗余:**

- `AuthContext` 的 `setUserId`/`getUserId`/`clear` 只有一处调用，ThreadLocal 包装过于薄。可以直接在 AuthInterceptor 的 request attribute 中传递。

---

### 🔗 链路2：赛事创建链（羽毛球）

```
POST /api/v1/tournaments → createTournament()
→ requireCompletedProfile() → 校验资料完整性
→ 校验参数(name、players≥2)
→ 过滤无效选手 → 构建Tournament实体
→ applyRule() → 校验 bestOf/gamesToWin/pointsToWin/enableDeuce/capPoint
→ applyTournamentType():
   ├─ 淘汰赛: 直接标记 STAGE_KNOCKOUT + knockoutGenerated=true
   └─ 小组赛: 校验 knockoutSlots(2的幂)/qualifiersPerGroup(1或2)/分组合理性
→ tournamentMapper.insert()
→ buildPlayers() → assignGroups()(小组赛)
→ playerMapper.insert()逐个
→ BracketEngine 或 RoundRobinEngine.generateGroupMatches()
→ matchRecordMapper.insert()逐个
→ 更新 tournament status=1
```

**🔴 潜在问题:**

1. **无事务原子性** — 虽然有 `@Transactional`，但 `playerMapper.insert()` 和 `matchRecordMapper.insert()` 是逐条插入。如果选手有20个，前10个插入成功、第11个失败，整体回滚正常工作。但如果数据库连接在循环中耗尽，部分数据可能残留。(MyBatis-Plus 逐条 insert 不走 batch，性能差)

2. **assignGroups 蛇形分组算法简单但无种子保护** — 种子排名为null的选手按名字排序。如果所有选手都没有种子，分组完全是字母序，可能造成死亡之组。

3. **`applyTournamentType` 中淘汰赛不校验 power of two** — 淘汰赛创建时 `knockoutSlots` 设为 null，直接依赖 BracketEngine 的 `calcPowerOfTwoCapacity` 自动补齐为2的幂。但当选手数刚好等于2的幂时（如8人），没问题；非2的幂（如7人），BracketEngine 补齐到8，有1个 bye。这部分逻辑是正确但隐式的，容易在维护时出错。

**🟡 冗余:**

- `applyRule` 和 `applyVolleyballRule` 有大量重复的 set 操作，可抽取公共方法。

---

### 🔗 链路3：赛事创建链（排球）

```
POST /api/v1/tournaments (sportType=1 或 有teams字段)
→ isVolleyballRequest() 判断 → createVolleyballTournament()
→ normalizeTeams() → validateTeamMembers()(6-12人, 唯一号码, 1名队长)
→ 构建Tournament → applyVolleyballRule()(只支持3局2胜/5局3胜)
→ tournamentMapper.insert()
→ buildTeamParticipants() → assignGroups()
→ playerMapper.insert()逐个
→ insertTeamMembers() → tournamentTeamMemberMapper.insert()逐个
→ 生成matches
→ 更新status=1
```

**🔴 潜在问题:**

1. **volleyball 硬编码 `pointsToWin=25`, `capPoint=99`** — `applyVolleyballRule` 中固定了排球每局25分、封顶99分。如果未来有不同赛制（如沙滩排球21分），需要改代码。应该支持配置化。

2. **`validateTeamMembers` 要求恰好1名队长** — 但 captain 字段是 `Boolean`，默认 null 也是合法的。如果前端忘记传 captain，所有队员 captain=null，校验通过（captainCount=0），抛出"必须指定1名队长"。错误信息明确但兜底逻辑缺失。

3. **libero 无上限校验** — 只校验了号码唯一性和队长数量，没有限制自由人数量。实际排球规则通常最多2名自由人。当前可以标任意数量为自由人。

**🟡 冗余:**

- `createTournament` 和 `createVolleyballTournament` 有 ~60% 重复代码（构建Tournament、assignGroups、生成matches、更新status），应抽取公共方法。

---

### 🔗 链路4：赛事列表浏览链

```
GET /api/v1/tournaments?keyword=xxx
→ listTournaments()
→ 有关键字: like name OR like location, order by createTime desc
→ 无关键字: order by favoriteCount desc, createTime desc
→ decorateTournamentFlags(): 批量查收藏状态 + 是否创建者
```

**🔴 潜在问题:**

1. **LIKE 查询无索引保护** — `name LIKE '%keyword%'` 和 `location LIKE '%keyword%'` 是前缀通配，无法使用普通B-tree索引。数据量大时需要 MySQL 全文索引或 Elasticsearch。

2. **分页缺失** — 列表接口没有分页参数，全量返回。赛事超过几百个时性能问题明显。

3. **`decorateTournamentFlags` 的N+1问题已规避** — 使用批量 IN 查询，这一点做得不错。

---

### 🔗 链路5：收藏/取消收藏链

```
POST /tournaments/{id}/favorite → favoriteTournament()
→ requireCompletedProfile() → requireTournament()
→ 查重(已收藏则幂等返回)
→ insert favorite → increaseFavoriteCount(自定义SQL)

DELETE /tournaments/{id}/favorite → unfavoriteTournament()
→ 同理 → delete → decreaseFavoriteCount
```

**🔴 潜在问题:**

1. **收藏计数并发不一致** — `increaseFavoriteCount` 和 `decreaseFavoriteCount` 使用的是 `SET favorite_count = favorite_count + 1`，这是原子的。但如果业务流程出现异常回滚(数据库回滚了insert但没回滚计数器更新)，计数会不一致。实际上是因为 MyBatis-Plus 自定义SQL不走事务，需确认 mapper XML 中的 SQL 是否在同一事务中。

2. **无唯一约束** — `tournament_favorite` 表没有 `(user_id, tournament_id)` 唯一索引。代码用先查后插方式防重，但在并发下可能插入重复记录（虽然 `@Transactional` 降低了概率，但默认隔离级别 READ_COMMITTED 下仍可能幻读）。

---

### 🔗 链路6：赛事详情链

```
GET /tournaments/{id} → getTournamentDetail()
→ requireTournament() → 映射到 TournamentDetailVO
→ 设置 creator(是否创建者) + favorite(是否已收藏)
```

**🟡 冗余:**

- `getTournamentDetail` 没有包含 players 或 matches 数据。前端需要额外调用 bracket/groups 接口。如果是详情页，通常期望看到参赛者。

---

### 🔗 链路7：淘汰赛对阵链

```
GET /tournaments/{id}/bracket → getBracket()
→ 查 tournament + players + matches(淘汰赛阶段)
→ attachTeamMembersIfNeeded()(排球)
→ 填充响应
```

**🔴 潜在问题:**

1. **无缓存** — 每次请求都查3张表。对于正在进行的赛事，前端可能频繁轮询。建议加短时缓存(如5秒)。

---

### 🔗 链路8：小组赛+积分榜链

```
GET /tournaments/{id}/groups → getGroups()
→ 查 tournament + players(group by groupNo) + matches(group by groupNo)
→ 每个group: attachTeamMembersIfNeeded()
→ 返回分组结构

GET /tournaments/{id}/group-standings → getGroupStandings()
→ loadPlayers() + loadGroupMatches()
→ buildStandingsVO():
   → buildGroupStandings(): 计算胜负场/局/分
   → compareStanding(): 胜场→净胜局→净胜分→H2H→名字
   → markRanksAndTies(): 排名 + 出线标记 + 平局检测
```

**🔴 潜在问题:**

1. **积分榜Tie检测逻辑有边界BUG** — `markRanksAndTies` 中：
```java
if (tied.size() < 3) continue; // 2人或以下不检测
```
这意味着：如果2人积分完全相同且正好卡在出线边缘（1人出线1人不），系统不会标记`tieUnresolved=true`。`generateKnockout` 会认为无争议，但实际上2人平局如何决定出线没有处理。这是**严重的业务逻辑缺陷**。

2. **H2H仅在胜场/净胜局/净胜分完全相同时才生效** — 这是正确的优先顺序，但如果H2H平局（循环胜负），`compareStanding` 降级到 `playerName.compareTo`（按名字字母序），这不够公平。

3. **点球统计依赖 gameScores JSON** — `applyPointStats` 解析 `match.getGameScores()`。如果 gameScores 为空（仅记录赢家没记录每局分数），净胜分始终为0。这对旧比赛是不公平的降级策略。

---

### 🔗 链路9：淘汰赛生成链（小组出线后）

```
POST /tournaments/{id}/generate-knockout → generateKnockout()
→ selectByIdForUpdate (行锁)
→ 校验: 创建者、小组赛模式、未生成过淘汰赛
→ 校验: 所有小组赛已完成、无未解决平局
→ collectQualifiers(): 收集qualified且无tie的选手
→ buildKnockoutSlots(): 蛇形交叉排列
   - 第1名按组号升序
   - 第2名按组号降序排 + 避免同组对战(findOpponentIndex)
→ BracketEngine.generateKnockoutBracketBySlots()
→ 更新 tournament: currentStage=KNOCKOUT, knockoutGenerated=true
```

**🔴 潜在问题:**

1. **`findOpponentIndex` 的避免同组可能失败** — 如果只有2组出线(qualifiersPerGroup=1)，第1名本身就来自不同组，没问题。但如果有4组各出2人(8人淘汰赛)，第2名排完后需要配对，`findOpponentIndex` 找第一个非同组的第2名。存在一种极端情况：如果某个第2名只与同组第1名配对不冲突，它可能找不到非同组的第2名（-1返回），抛异常。这在数学上大概率不会发生但代码没有兜底（如放宽同组限制）。

2. **selectByIdForUpdate 行锁范围** — 只锁了 tournament 行，但 generateKnockout 过程中还读取了 players 和 groupMatches。这些数据在锁外可能被修改（不过小组赛完成后不太可能被改，风险低）。

---

### 🔗 链路10：比赛记分更新链（羽毛球 - updateMatchResult）

```
PUT /matches/{id}/score → updateMatchResult()
→ 校验 matchId + winnerId
→ 查match → requireCreatorTournament(只有创建者可操作)
→ 更新当前match: scoreDisplay, winnerId, status=2
→ 如果无下一场比赛:
   ├─ 小组赛淘汰赛(stageType=0, tournamentType=1): 不结束赛事(等小组全部打完)
   └─ 纯淘汰赛: 设置 tournament status=2(已结束)
→ 如果有下一场: 更新下一场的 leftPlayerId/rightPlayerId
```

**🔴 潜在问题:**

1. **小组赛结束判断不准确** — `Integer.valueOf(0).equals(current.getStageType()) && Integer.valueOf(1).equals(tournament.getTournamentType())` 这个判断是：小组赛阶段的小组+淘汰赛模式，单场结束不结束赛事。但如果这是小组赛的最后一场呢？没有检查。实际上应该在所有小组赛都结束后才能进入淘汰赛，但单场胜利不应标记整个赛事为 "已结束"。逻辑正确，但 `tournament.status=2` 的用途模糊——到底表示 "赛事结束" 还是 "某阶段结束"？

2. **`requireCreatorTournament` 的权限模型过于严格** — 只有创建者可以修改比分。这在多裁判场景下不适用。未来需要考虑基于角色的权限。

3. **没有并发控制** — 两人同时操作同一场比赛，后者覆盖前者。没有乐观锁(version字段)。虽然有行锁但未使用 `selectByIdForUpdate`。

---

### 🔗 链路11：比赛记分更新链（排球 - finishMatch）

```
PUT /matches/{id}/finish → finishMatch()
→ 详细校验见 validateFinishReq()(总分/局数/赢家一致性)
→ buildScoreDisplay()(格式: "25:23, 21:25, 25:20")
→ 更新match(status=2, scoreDisplay, winnerId, gameWins, gameScores, retiredSide)
→ 传播赢家到下一场 → 同上链路的赛事结束逻辑
```

**🔴 潜在问题:**

1. **`validateFinishReq` 的退赛逻辑不够清晰** — `gameScores` 为空且 `retiredSide` 非空时跳过局分校验。但如果退赛方赢了前几局怎么办？没有记录最终局分。

2. **`buildScoreDisplay` 与 `gameScores` 数据冗余** — `scoreDisplay` 存储格式化字符串（如 "25:23, 21:25"），`gameScores` 存储JSON数组。如果两者不一致，以谁为准？代码中读取侧用的是 `gameScores`，但 `scoreDisplay` 是给人看的。冗余度较高。

---

### 🔗 链路12：比赛事件保存链

```
PUT /matches/{id}/events → saveMatchEvents()
→ 校验权限 + 事件列表非空
→ 排序(按eventSeq) + 去重校验
→ 查已有事件 → 跳过已存在的 eventSeq
→ 逐条 insert 新事件
```

**🔴 潜在问题:**

1. **跳过已存在而非更新** — 同名 `eventSeq` 直接 `continue` 跳过。如果前端想修改已有事件的 payload，做不到。这是一个设计限制，可能是有意的（事件不可变），但文档里没说明。

2. **没有校验事件的业务连续性** — 例如：timeout事件后比分应该没有变化；substitution必须在lineup_snapshot之后。当前只校验字段格式，不校验业务逻辑。

---

### 🔗 链路13：阵容配置保存链（排球）

```
PUT /matches/{id}/lineup-config → saveLineupConfig()
→ validateAndNormalizeSaveLineupReq():
   → ensureLineupConfigEditable(): 已完成局不可编辑，后续局已配置则锁定
   → loadTeamMemberScopes(): 每个队伍的成员白名单
   → validateTeamLineupConfig(): 场上6人必须来自队伍、自由人不在场上
→ 查已有配置 → insert 或 update
```

**🔴 潜在问题:**

1. **`ensureLineupConfigEditable` 的锁定逻辑有漏洞** — `latestSavedGameNo > gameNo` 会阻止编辑更早的局，但如果用户跳过一局没配置（如gameNo=1配置了，gameNo=2没配置，gameNo=3配置了），想回去配置gameNo=2会被 `latestSavedGameNo=3 > 2` 拦截。这是一个过于严格的限制。

2. **中间对约束条件校验** — `normalizeMiddlePairIndexes` 要求2个索引且不能重复且必须在 OPPOSITE_SLOT_MAP 中（0-5）。`isOppositePair` 校验是否为对位。这里有双重校验，但 `normalizeMiddlePairIndexes` 排序了索引，如果原始输入是 [5,0] 被排成 [0,5]，然后 `isOppositePair` 检查 `OPPOSITE_SLOT_MAP.get(0).equals(5)` → true。如果原始输入是 [0,1]（不对位），`isOppositePair` 检查 `OPPOSITE_SLOT_MAP.get(0).equals(1)` → `5.equals(1)` → false。逻辑正确。

---

### 🔗 链路14：比赛记录详情链

```
GET /matches/{id}/record → getMatchRecordDetail()
→ 查 match + tournament
→ 查 participants(Player表) + teamMembers
→ 查 events + lineupConfigs + reportMeta
→ 构建复杂响应:
   ├─ 基本比赛信息
   ├─ participantRecord(含成员列表)
   ├─ rosterSnapshot(从event中取或fallback到participant)
   ├─ lineupSnapshots(合并event快照+config)
   ├─ eventRecords(含中文摘要)
   ├─ reportMeta + reportRender(报告渲染数据)
```

**🔴 潜在问题:**

1. **N+1问题** — 一次查询访问了6张表（match, tournament, player, teamMember, event, lineupConfig, reportMeta），但都是一次性加载。设计合理。

2. **`buildRotationGrid` 的复杂度极高** — 包含 libero slot逻辑、substitution应用、secondary jersey显示、MB-libero对位等。这是整个系统中最复杂的只读逻辑，约300行。建议拆解为独立的 `RotationGridBuilder` 类。

3. **防御性编程带来的静默数据丢失** — 多处 try-catch 返回空列表（如 `parseGameScores`, `parseStringList`, `parseIntegerList`）。如果数据库中的JSON格式因升级而变化，错误被静默吞掉，用户看到空数据而非错误提示。

---

### 🔗 链路15：比赛重开链

```
PUT /matches/{id}/restart → restartMatch()
→ 删 event + lineupConfig + reportMeta (物理删除)
→ 重置 match (scoreDisplay/winnerId/gameWins/gameScores/status/retiredSide → null/0)
→ 不重置: leftPlayerId/rightPlayerId/nextMatchId/nextMatchSlot
```

**🔴 潜在问题:**

1. **物理删除不可恢复** — events、lineupConfigs、reportMeta 被直接 delete。没有软删除，没有审计日志。如果误操作，数据永久丢失。

2. **不处理下一场比赛的状态** — 如果当前比赛的赢家已经传播到下一场（设置了nextMatch的leftPlayerId/rightPlayerId），restart不会清除下一场的选手。这会导致下一场比赛的选手仍然是旧的赢家。这是一个**严重的数据一致性问题**。

3. **没有检查比赛是否已被引用** — 如果淘汰赛决赛已完成，重开半决赛会导致决赛的选手数据不准确。

---

### 🔗 链路16：报告元数据链

```
PUT /matches/{id}/report-meta → saveMatchReportMeta()
→ 查已有 → insert或update
→ buildReportMetaJson(): 构建含猜边/签名/备注的JSON

读取侧: getMatchRecordDetail() → buildReportMetaRecord() + buildReportRender()
```

**🟡 冗余:**

- `saveMatchReportMeta` 和 `getMatchRecordDetail` 中的 `buildReportMetaJson`/`buildReportMetaRecord` 存在类似的字段映射。如果JSON结构变化，需要同时改两处。

---

### 🔗 链路17：限流链

```
RequestRateLimitInterceptor.preHandle()
→ 检查 enabled? → OPTIONS放行
→ /auth/wechat-login: enforceLimit(login bucket)
→ 写操作(POST/PUT/DELETE): enforceLimit(write bucket)
→ RequestRateLimiter.allow(): 滑动窗口计数器
```

**🔴 潜在问题:**

1. **内存泄漏风险** — `RequestRateLimiter.counters` 是 `ConcurrentHashMap`，只在 size>4096 时触发清理（cleanupExpired）。如果攻击者用不同IP持续发送少量请求，计数器不会超过4096就永远不会清理，导致内存泄漏。应该用定时任务清理或使用 Caffeine/Guava Cache 的过期机制。

2. **IP获取可被伪造** — `X-Forwarded-For` 头可以被客户端任意设置。虽然取第一个IP（最靠近客户端的），但如果反向代理没有正确设置，攻击者可以伪造IP绕过限流。

3. **限流粒度太粗** — 所有写操作共享一个 write bucket（60次/分钟）。创建赛事、更新比分、收藏都是同样限制。如果需要频繁更新比分（排球每得一分就是一次），60次/分钟可能不够。

---

### 🔗 链路18：请求日志链

```
RequestLoggingFilter.doFilterInternal()
→ 记录开始时间 → chain.doFilter()
→ 成功: log.info (method/path/status/costMs/clientIp)
→ 异常: log.error (method/path/status/costMs/clientIp/message + stacktrace)
```

**🔴 潜在问题:**

1. **异常时getStatus()可能不准** — catch 块中 `response.getStatus()` 在异常抛出时可能还未设置（默认200）。应该单独处理。

2. **日志量问题** — 生产环境下所有 API 请求都记录 info 日志，高并发时日志量巨大（14天保留可能不够）。

---

## 三、跨链路通用问题

### 🔴 严重

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 1 | **无乐观锁/并发控制** | 比分/阵容可能被覆盖 | MatchRecord/Tournament 加 `version` 字段 + `@Version` |
| 2 | **物理删除不可恢复(restart)** | 误操作数据永久丢失 | 加 `deleted` 软删除字段 |
| 3 | **restart不清理下一场选手** | 淘汰赛数据不一致 | restart时同步清除下游match的选手 |
| 4 | **2人平局卡出线边缘未检测** | 小组出线判定有bug | `markRanksAndTies` 中 tied.size()==2 也应检测 |
| 5 | **限流器内存泄漏** | 生产环境OOM | 改用Caffeine Cache自动过期 |

### 🟡 中等

| # | 问题 | 影响 | 建议 |
|---|------|------|------|
| 6 | **缺少分页** | 赛事列表全量返回 | 加分页参数(pageNum/pageSize) |
| 7 | **ThreadLocal清理不彻底** | 潜在用户串号 | Filter的finally块清理 |
| 8 | **LIKE查询无索引** | 搜索性能差 | 加全文索引或ES |
| 9 | **写操作限流60次/分可能不足** | 排球频繁得分受限 | 提高write限制或细分bucket |
| 10 | **JWT无刷新机制** | 30天后强制重登 | 加入refresh token |
| 11 | **防御性try-catch吞错误** | JSON解析失败静默返回空 | 至少log.warn |
| 12 | **收藏无唯一约束** | 并发下可能重复 | 加unique索引(user_id, tournament_id) |

### 🟢 轻度/建议

| # | 问题 | 建议 |
|---|------|------|
| 13 | `createTournament` 和 `createVolleyballTournament` 40%重复 | 抽取公共方法 |
| 14 | `applyRule` 和 `applyVolleyballRule` 重复 | 统一为 RuleValidator |
| 15 | 排球规则硬编码(pointsToWin=25, capPoint=99) | 改为可配置 |
| 16 | `buildRotationGrid` 300行过于复杂 | 拆分为独立类 |
| 17 | 无Docker部署方案 | 编写 Dockerfile + docker-compose.yml |
| 18 | 无健康检查端点 | 加 `/health` 或 `/actuator/health` |
| 19 | 无API版本管理 | 当前硬编码 `/api/v1`，升级困难 |
| 20 | 测试覆盖仅8个测试类 | 核心链路（创建赛事/记分/淘汰赛生成）应有集成测试 |
| 21 | 废弃代码未清理 | MatchThemeConfig 相关代码被注释但保留，增加维护负担 |
| 22 | CORS配置默认值可能是开发用 | `application-prod.yml` 中 CORS 示例用 example.com，上线前需改 |
| 23 | `ProductionStartupChecker` 仅检查JWT/微信配置 | 还应检查DB连接/Flyway状态 |

---

## 四、数据流关系图

```
用户 ─→ Auth(微信登录) ─→ JWT Token
  ↓
赛事大厅 ─→ listTournaments ─→ 搜索/筛选
  ↓
创建赛事 ─→ Tournament + Players + TeamMembers + Matches
  ↓                      ↓
淘汰赛对阵 ←── BracketEngine   小组循环 ←── RoundRobinEngine
  ↓                      ↓
记分板 ─→ updateScore/finishMatch ─→ 传播赢家 ─→ 下一场
  ↓                      ↓
比赛记录 ←── events + lineupConfigs + reportMeta
  ↓
小组积分榜 ─→ buildStandings ─→ 出线判定 ─→ generateKnockout
  ↓
淘汰赛阶段 ─→ 持续到决赛 ─→ 赛事结束(status=2)
```

---

## 五、上线Checklist

| 事项 | 状态 |
|------|------|
| ⬜ 修改 `application-prod.yml` 中 CORS origins | 待确认 |
| ⬜ 配置生产环境变量(JWT_SECRET, WECHAT_APP_ID, DB_URL等) | 待确认 |
| ⬜ 数据库 `tournament_favorite` 加唯一索引 | **建议** |
| ⬜ 修复 `markRanksAndTies` 的2人平局bug | **强烈建议** |
| ⬜ 修复 `restartMatch` 不清除下游选手的bug | **强烈建议** |
| ⬜ 限流器改Caffeine或加定时清理 | **建议** |
| ⬜ 加分页 | **建议** |
| ⬜ 加健康检查端点 | **建议** |
| ⬜ 清理废弃的 ThemeConfig 代码 | **建议** |
| ⬜ 确认Flyway迁移在生产库能正常执行 | **必须** |
| ⬜ 配置日志保留策略(当前14天200MB) | 待确认 |
| ⬜ 确认MySQL字符集为utf8mb4 | 待确认 |

---

*审计范围：全量Java源码 + 前端auth/request层 + 配置文件。未覆盖前端业务页面组件。*
