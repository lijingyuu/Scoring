# 羽毛球混沌测试建设前期调研报告（P0 交付物）

> 本文档系统性梳理 Eunomia 赛事系统中**羽毛球业务**（涵盖个人赛单双打、苏杯五项团体赛、接力追分团体赛、自定义多项团体赛）的代码实现面、状态模型、动作面与风险点，为羽毛球混沌测试建设提供精确事实基准。

---

## 1. 规则配置面与运动特征

### 1.1 支持的运动类型与标识
- `sportType`: `0`（羽毛球），`1`（排球）。
- `participantType`: `0`（个人赛，包含单打与双打），`1`（团体赛）。
- 前端承载面：
  - **常规个人赛与团体赛子比赛**：由通用记分板 [`frontend/src/pages/scoreboard/index.vue`](../frontend/src/pages/scoreboard/index.vue) 承载。
  - **接力追分赛**：由专属记分板 [`frontend/src/pages/tournament/team-relay.vue`](../frontend/src/pages/tournament/team-relay.vue) 配合纯逻辑库 [`frontend/src/pages/tournament/relay-scoring.js`](../frontend/src/pages/tournament/relay-scoring.js) 承载。
  - **团体赛控制台**：由 [`frontend/src/pages/tournament/team-match.vue`](../frontend/src/pages/tournament/team-match.vue) 承载。

### 1.2 羽毛球常规记分规则实现细节（`pages/scoreboard/index.vue`）
1. **局制（`bestOf`）**：
   - 默认支持 1 局（一局定胜负）、3 局（三局两胜）、5 局（五局三胜）。
   - 获胜局数门槛：`gamesToWin = Math.floor(bestOf / 2) + 1`。
2. **每局胜分（`pointsToWin`）**：
   - 默认 21 分，允许临场或建赛设置（支持 1~99 分，常见如 11 分、15 分、21 分、31 分等）。
3. **追分与封顶机制（`enableDeuce`, `capPoint`）**：
   - 当 `enableDeuce = true` 时，双方比分达到 `pointsToWin - 1` 平后进入 Deuce，必须领先 2 分才获胜（`myScore - oppScore >= 2`）。
   - **封顶分（`capPoint`）**：达到封顶分时直接获胜（无论是否领先 2 分）。默认 30 分封顶，可配置为 99（等同于无封顶）。
   - 当 `enableDeuce = false` 时，任一方先达到 `pointsToWin` 即胜出本局。
4. **决胜局换边机制（`finalGameSideSwitch`）**：
   - 当进入决胜局（`currentGameNo === bestOf`）且未曾处理过决胜局换边时：
   - 任一方小分达到 `threshold = Math.ceil(pointsToWin / 2)`（例如 21 分制的第 11 分）：
   - 系统立刻弹出换边提示窗（`isFinalGameSideSwitchPromptActive = true`），锁定小分增加。
   - 裁判可选择：**换边**（触发 `applySideSwitch()` 并记录已处理）或 **不换边继续**（仅记录已处理，保持半场）。
5. **局间自动换边机制（`shouldAutoSwitchBetweenGames`）**：
   - 三局两胜制下，第 1 局结束进入第 2 局、第 2 局结束进入第 3 局时，在裁判点击"换边继续"确认后，系统自动调用 `applySideSwitch()` 翻转球场。
6. **发球权流转规则**：
   - 得分方获得下一分发球权（`serveSide.value = side`）。
   - 局初第一球由首发发球方发球；局末获胜方在下局开局执发球权。
7. **11 分技术间歇**：
   - 当前前端通用记分板未设置强制性暂停倒计时弹窗，而是通过决胜局 11 分换边弹窗在决胜局提供操作拦截；常规局次内裁判可自发把握间歇。

---

## 2. 状态模型与持久化

### 2.1 通用记分板状态模型（`pages/scoreboard/index.vue`）
- **核心响应式状态**：
  - `leftTeam`, `rightTeam`: 左右半场当前显示的队伍/选手名称。
  - `leftScore`, `rightScore`: 当前小局左右小分（数字）。
  - `leftGameWins`, `rightGameWins`: 左右当前获胜大局数（数字）。
  - `currentGameNo`: 当前局号（从 1 开始）。
  - `gameScores`: 历史已结束各局成绩数组，元素结构 `{ gameNo, leftScore, rightScore, winnerSide }`。
  - `serveSide`: 发球权所在侧（`'left' | 'right'`）。
  - `sidesSwapped`: 场地是否发生过奇数次换边（布尔值，用于回传后端时坐标映射）。
  - `historyStack`: 撤销历史栈，存储完整的 `buildSnapshot()` 序列。
  - `retiredSide`: 退赛方（`'left' | 'right' | ''`）。
  - `matchEnded`: 比赛是否已终局（布尔值）。
  - `finalGameSideSwitchPending`, `finalGameSideSwitchHandled`: 决胜局换边状态机标记。
  - `gameEndPromptPending`, `gameEndPromptHandled`: 局间换边弹窗状态机标记。
  - `matchRules`: 当前使用的规则 `{ bestOf, gamesToWin, pointsToWin, enableDeuce, capPoint }`。

### 2.2 本地持久化与恢复路径
- **缓存 Key**：
  - 非排期单场：`'badminton_scoreboard_state'`。
  - 排期/赛程场次：`'badminton_scoreboard_state_' + matchId`。
- **缓存形态**：
  `{ ...snapshot, historyStack, isGodMode }`
- **恢复流程（`onLoad`）**：
  1. 读取 query 参数：`matchId`, `tournamentId`, `divisionId`, `source`。
  2. 权限校验 `requireMatchOperator(matchId)` 并尝试加排他锁 `acquireMatchLock`。
  3. 检查本地 Storage，若存在则调用 `restoreStateFromStorage()` 还原状态快照与历史栈。
  4. 若无本地缓存且为服务端排期比赛，向后端拉取比赛信息并初始化规则与选手。

---

## 3. 裁判动作面（Action Surface）

| 动作标识 | 触发函数 | 影响状态 | 约束与前置条件 |
|---|---|---|---|
| **常规加分** | `addScore(side)` | 对应小分 +1，发球权变更为该侧，推送历史栈，检测局胜/决胜局换边 | `!isReadOnly && !isLocked && !isPromptActive` |
| **撤销** | `undo()` | 弹出 `historyStack` 顶层快照并全面还原 | `!isReadOnly && historyStack.length > 0 && !isLocked && !isPromptActive` |
| **手动换边** | `switchSides()` | 左右队伍名、小分、胜局数、`gameScores` 内部所有小局得分及发球方全部左右对调，`sidesSwapped` 翻转 | `!isReadOnly && !isLocked && !isPromptActive` |
| **决胜局换边处理** | `handleFinalGameSideSwitch(bool)` | 解除待决状态；若 true 则对调全场，随后重新触发终局检测 | 必须在 `isFinalGameSideSwitchPromptActive` 期间操作 |
| **确认局结束** | `confirmGameEnd()` | 局号 +1，小分清零，下一局发球方赋予上局胜者，若三局两胜自动执行局间换边 | 必须在 `gameEndPromptPending` 期间操作 |
| **退赛** | `retire(side)` | 记录 `retiredSide`，对方胜局直接补满 `gamesToWin`，`matchEnded = true`，触发同步结算定时器 | `!isReadOnly && !isLocked && !isPromptActive` |
| **上帝模式微调** | `adjustScore(side, delta)` | 对应小分 +1 或 -1（不低于0），推送历史栈 | `isGodMode && !isReadOnly && !isLocked && !isPromptActive` |
| **上帝模式强行完局** | `manualFinishGame()` | 按当前领先侧直接调用 `finishGame(side)` | `isGodMode && leftScore !== rightScore` |
| **临场规则修改** | `saveRules()` | 修改 `matchRules` 并存盘 | 仅在比赛尚未发生任何得分（小分0:0且无历史局）时允许 |
| **同步结算** | `syncAndBack()` | 调用 `PUT /api/v1/matches/{id}/finish`，将还原为原初始方位的成绩提交后端 | 比赛必须处于锁定态（`isLocked`）且分出胜负 |

---

## 4. 羽毛球团体赛与接力追分赛机制

### 4.1 团体赛模板（`TeamMatchServiceImpl`）
1. **苏迪曼杯五项（`TEMPLATE_SUDIRMAN_5 = 1`）**：
   - 固定 5 项子比赛：
     - `1: MS`（男单，1人）
     - `2: WS`（女单，1人）
     - `3: MD`（男双，2人）
     - `4: WD`（女双，2人）
     - `5: XD`（混双，2人）
2. **自定义多项团体赛（`TEMPLATE_CUSTOM = 3`）**：
   - 支持配置 3 项、5 项或 7 项。
   - 子项可从 `{ S, D, MS, WS, MD, WD, XD }` 自由组合。
3. **接力追分赛（`TEMPLATE_RELAY = 2`）**：
   - 不生成独立子比赛数据库记录（`stageType=2`），父比赛作为整体由 `team-relay.vue` 记分。
   - 分段编码 `R1..RN`（`N` 为接力人数，通常 3~12 人，默认 6 人）。
   - **接力链式闭环规则**：每段均为双打，各队成员流转必须严格满足相邻相扣：
     $$(M_1, M_2) \to (M_2, M_3) \to \dots \to (M_N, M_1)$$
     所有首位队员互不重复。
   - **分段目标**：第 $k$ 段目标为 $k \times \text{baseScore}$（默认 10 分/段，6 人接力为 60 分封顶）。达标后领先方触发换段，上一段终点为下一段起点。

### 4.2 淘汰赛提前终局机制（Dead Rubber Early Settle）
- **核心业务逻辑**（见 [`MatchSettlementService.java`](../backend/src/main/java/com/scoring/backend/service/match/MatchSettlementService.java#L415)）：
  - 动态胜场门槛：`winThreshold = (totalItems / 2) + 1`。
  - 在淘汰赛阶段（`stageType === 1`），当一方已斩获 `winThreshold` 场胜利（如 5 项中获得 3 胜，或 3 项中获得 2 胜）时，**系统允许提前结算父场比赛**。
  - 剩余尚未进行的场次无需打完，父场比赛结果即可直接写入并向下推进晋级树。
  - 在小组循环赛阶段，为了保证小局/小分净胜统计的公平性，必须全部项目完赛才可结算。
  - **触发方式澄清（2026-09-18 混沌测试返工时核实）**：提前终局为**手动双路径**设计——
    一方过半胜后父场**不会自动结算**（自动路径仅在全部完赛时落定），须裁判经前端
    team-match.vue 弹窗手动触发 `PUT /matches/{id}/team-match/settle`（`earlyKnockout`
    豁免"须全完赛"校验）。手动提前终局后剩余子项保持未开赛状态（winnerSide=null、
    status≠2），且 `startChildMatch` 守卫拒绝在已结算父场再开子场。

---

## 5. 羽毛球四大排名模板机制（`engine/ranking/`）

在小组循环赛阶段，羽毛球针对不同赛事形态提供了 4 种专业的积分与破平（Tie-break）规则：

| 排名模板 | 适用场景 | 破平裁决次序 | 差额/比率计算 |
|---|---|---|---|
| `BWF_BADMINTON` | 羽毛球个人赛（世界羽联标准） | 胜场数 $\to$ 净胜局 $\to$ 净胜分 $\to$ 直胜关系 (H2H) | DIFFERENCE (得 - 失) |
| `BADMINTON_COMMON_1` | 羽毛球大众业余个人赛 | 胜场数 $\to$ 净胜局 $\to$ 得失分率 (得 / 失) | RATIO (得 / 失) |
| `BADMINTON_TEAM_COMMON_1` | 羽毛球团体赛（苏杯/自定义多项） | 胜场数 $\to$ 直胜关系 $\to$ 场内大分净胜 $\to$ 场内局净胜 $\to$ 局内小分净胜 | DIFFERENCE |
| `BADMINTON_RELAY_COMMON_1` | 羽毛球接力追分团体赛 | 胜场数 $\to$ 两队直胜关系 $\to$ 小分得失比率 | RATIO |

---

## 6. 不变式清单（羽毛球状态机与生命周期红线）

在执行混沌测试时，任何状态转换如果触犯下列红线，即判定为 `CRITICAL` 严重缺陷：

1. **小分非负与单调性**：小分、局胜数、局号不能为负数；正常得分动作必须使小分单调递增。
2. **胜分与封顶硬约束**：
   - 未达到 `pointsToWin`（开启 Deuce 时未达 `pointsToWin` 且未领先 2 分，或未达 `capPoint`）前，不可触发局完赛。
   - 一旦达到 `capPoint`，必须立即触发局完赛，严禁继续累积分数。
3. **发球方强一致**：常规加分后，发球指示灯所在侧必须与本分得分侧一致。
4. **决胜局换边待决一致性**：决胜局达到阈值后，必须进入换边待决态；在待决态消除前，不可执行后续加分。
5. **换边状态与坐标还原一致性**：
   - 换边后左右分数、队伍、局胜与小局历史必须对调。
   - 撤销换边后，各项指标必须 100% 镜像还原本位。
   - 提交后端时，`winnerSide`、`leftGameWins`、`rightGameWins` 必须结合 `sidesSwapped` 正确翻转回原始对阵。
6. **团体赛父子场胜负守恒**：父场大比分必须等于子场各单项胜负计数的代数和。
7. **接力赛接力链拓扑守恒**：接力赛对阵必须形成环状相邻相扣且无首位重复队员。
8. **淘汰树晋级槽位守恒**：胜者必须进入下一轮指定槽位，不可被覆写或丢失；若有季军赛，半决赛负者必须进入季军赛。
9. **同组回避守恒**：小组赛出线生成的淘汰赛对阵树中，同一小组第一名与第二名不能在第一轮相遇。

---

## 7. 高危风险点排查预警

结合排球混沌测试的历史踩坑经验，羽毛球模块有以下 4 处极高危风险场景：

1. **`sidesSwapped` 与深撤销的历史脱节风险**：
   - 在 `pages/scoreboard/index.vue` 中，`confirmGameEnd` 与 `handleFinalGameSideSwitch` 触发 `applySideSwitch` 时，若未在对调前保存历史栈，跨局撤销可能导致比分还原但队伍半场未还原（类似排球的案例二）。
2. **存取回环重入微任务竞态**：
   - 页面加载 `onLoad` 中存在异步 `guardProfileBeforeAction` 与 `acquireMatchLock`，若重入后未充分冲刷微任务即提取快照，可能读到初始空数据。
3. **淘汰赛提前终局后的僵尸子比赛**：
   - 当一方在 5 项中达到 3 胜触发提前结算后，若裁判继续误操作剩下的第 4、5 场并提交，后端是否会破坏已生成的晋级槽位？
4. **接力追分跨段连续撤销**：
   - 在已触发换段（分段目标达成并记录了上一段分段比分）后，裁判连续深撤销回到上一段，分段历史数组与当前分段号是否能正确倒流？
5. **记分板侧别参数防御观察（已在 Fuzzer 中登记并加固）**：
   - 通用记分板 `pages/scoreboard/index.vue` 的 `addScore(side)` 与 `adjustScore(side)` 使用了 `if (side === 'left') ... else ...`，若传入非法字符串会默认落入右队分支并导致 `serveSide` 赋为非法值。测试端 Fuzzer 针对此输入探针实施了显式边界校验，断言其必须被拦截（`hostileRejected` 达 100%）。
