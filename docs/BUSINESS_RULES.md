# BUSINESS_RULES.md — 核心业务状态机与算法

> **用途**: 把最烧脑的体育规则固化下来，不让 AI 踩坑。
> **关联**: [[DATABASE.md]] · [[ARCHITECTURE.md]]

---

## 1. 赛制推进流转

### 1.1 整体状态机

```
创建赛事 (status=0)
  → 生成签表/赛程 → status=1 (进行中)
  → 小组赛(如有) → 全部结束 → 生成淘汰赛
  → 淘汰赛逐轮推进
  → 决赛结束 → status=2 (已结束)
```

### 1.2 单场比赛闭环（核心链路）

```
双方选手就位 → status 0→1 (进行中)
  ↓
记分板操作: 加分 → 换边 → 暂停 → 换人(排球)
  ↓
一方达到获胜条件 → finishMatch
  ↓
后端 finishMatch():
  1. 更新当前比赛: status=2, winnerId, gameScores, scoreDisplay
  2. 查 next_match_id（淘汰赛晋级链表）
     ├─ 有下一场 → 填入胜者到 nextMatchSlot(left/right)
     │              双方就位 → 下一场 status 自动变 1
     └─ 无下一场 → 这是决赛 → 赛事 status=2 (已结束)
```

### 1.3 淘汰赛晋级链表

```
match A: next_match_id = match C, next_match_slot = "left"
match B: next_match_id = match C, next_match_slot = "right"
match C: next_match_id = null (决赛)

A 结束 → winnerId 写入 C.leftPlayerId
B 结束 → winnerId 写入 C.rightPlayerId
C.leftPlayerId ≠ null AND C.rightPlayerId ≠ null → C.status = 1 (自动激活)
```

### 1.4 小组赛出线 → 淘汰赛

```
小组赛全部结束 → 用户点击"生成淘汰赛"
  → 校验: 所有小组赛已完成、无未解决平局
  → collectQualifiers(): 收集每个小组的出线者
  → 蛇形交叉排列: 第1名按组号升序、第2名按组号降序(避免同组)
  → BracketEngine.generateKnockoutBracketBySlots()
  → tournament.currentStage = 1 (淘汰赛阶段)
  → tournament.knockoutGenerated = true
```

---

## 2. 小组赛积分与排名

### 2.1 积分计算

每场比赛数据从 `match_record` 读取 `game_scores` JSON，累加：
- `matchWins` / `matchLosses` — 胜场/负场
- `gameWins` / `gameLosses` — 胜局/负局
- `pointsFor` / `pointsAgainst` — 得分/失分

### 2.2 排名优先级（从高到低）

```
1. 胜场数 (matchWins)
2. C 值 (净胜局 = gameWins - gameLosses)
3. Z 值 (净胜分 = pointsFor - pointsAgainst)
4. H2H (相互胜负关系)
5. 种子排名 (seedRank)
6. 名字字母序 (last resort)
```

### 2.3 平局检测

`markRanksAndTies()` 中，如果多名选手胜场、净胜局、净胜分完全相同且卡在出线边缘 → `tieUnresolved = true` → 阻止生成淘汰赛。

2 人完全相同时会先看 H2H：如果双方直接交手有胜者，则不标记未解决；如果没有 H2H 胜者，仍会标记为未解决。

---

## 3. 淘汰赛排表算法（BracketEngine）

### 3.1 算法流程

```
输入: n 个选手 (已随机 shuffle)

Step 1 · 容量对齐
  n → 向上取整到 2的幂 p (例: 6人 → p=8)

Step 2 · 递归种子排序
  构建平衡二叉树种子序列
  不变式: 每对相邻种子之和 = p + 1
  例: p=8 → [1, 8, 4, 5, 2, 7, 3, 6]

Step 3 · 安置选手
  - 有种子排名的选手 → 精确放入对应槽位
  - 无种子排名的选手 → 随机填入剩余非 bye 槽位
  - seedOrder 中值 > n 的位置 → 轮空 (bye)

Step 4 · 轮空坍缩
  bye 槽位 → 直接晋级
  propagateWinnerToParent() → 递归传播到父节点

Step 5 · 晋级链表
  每场记录 next_match_id + next_match_slot (left/right)
  决赛: next_match_id = null
```

### 3.2 小组赛出线后的蛇形排法

```
第1名: 组1-1, 组2-1, 组3-1, 组4-1 (按组号升序)
第2名: 组4-2, 组3-2, 组2-2, 组1-2 (按组号降序)
findOpponentIndex() 避免同组第1名和第2名在淘汰赛首轮相遇
```

---

## 4. 羽毛球专项规则

### 4.1 基本计分

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `pointsToWin` | 21 | 每局目标分 |
| `bestOf` | 3 | 三局两胜 |
| `gamesToWin` | 2 | 赢2局获胜 |
| `enableDeuce` | true | 启用追分 |
| `capPoint` | 30 | 封顶分 |

### 4.2 获胜条件

```
checkWinCondition(myScore, opponentScore):
  return myScore >= pointsToWin
    AND (myScore - opponentScore) >= 2   ← 必须领先 ≥2 分
```

### 4.3 追分与封顶

- **正常追分**: 20:20 → 继续打到一方领先2分为止
- **封顶**: 29:29 → 先到30分者胜（`capPoint = 30`）

### 4.4 单打淘汰赛特点

- 选手=个人，player 表一行即一个参赛者
- 创建赛事时粘贴选手名单（空格/换行/逗号分隔）
- 记分板：横屏双栏布局，点击己方分数区 +1分，发球权跟随

---

## 5. 排球专项规则

### 5.1 基本计分

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `pointsToWin` | 25 (前几局), 15 (决胜局) | 每局目标分 |
| `bestOf` | 5 | 五局三胜 |
| `gamesToWin` | 3 | 赢3局获胜 |
| `enableDeuce` | true | 启用追分 |
| `capPoint` | 30 | 封顶分 |

### 5.2 决胜局判定

```js
currentTargetPoints = (currentGameNo === bestOf) ? 15 : 25
```

### 5.3 发球权与轮转

```
接发方得分 → 顺时针轮转 → 发球权切换
发球方得分 → 不轮转
```

**场上位置映射** (court 数组 -> 球衣号码位):
```js
SLOT_POSITIONS = [4, 3, 2, 5, 6, 1]  // 数组索引 → 实际号位
// 前排: slot 0-2 (4/3/2号位)
// 后排: slot 3-5 (5/6/1号位)
```

### 5.4 换边机制（三种场景）

| 场景 | 时机 | 效果 |
|------|------|------|
| **局间换边** | 每局结束 → 下一局轮次填写前 | `swapMatchStateSides()` 交换全部状态 |
| **决胜局8分换边** | 决胜局任一方达到8分 | 弹窗提示→确认后交换两侧显示+记录 `side_switch` 事件 |
| **手动换边** | 首局/决胜局 lineup 页 | 用户点击"换边"按钮 |

**核心数据结构**: `screenLeftParticipantSide` — 记录"当前屏幕左侧对应的是原始 left 还是 right 参赛方"。所有事件的 side 字段均使用**参与者侧**，与屏幕显示位置无关。

---

## 6. 自由人系统（仅排球）

### 6.1 核心概念

自由人（Libero）是专门的后排防守球员，**不能**在前排、**不能**发球、**不能**进攻。

### 6.2 数据结构

```js
// 局前配置
liberoSetup = {
  pairIndexes: [2, 3],    // 两个副攻在 court 数组中的索引（必须对角位）
  libero1Id: 'm5',        // 自由人1 memberId
  libero2Id: 'm7',        // 自由人2 memberId (可选)
}

// 运行时状态（比赛中动态追踪）
liberoRuntime = {
  role1SlotIndex: 2,      // 副攻1当前在哪个 slot
  role2SlotIndex: 3,      // 副攻2当前在哪个 slot
  role1PlayerId: 'm2',    // 当前占据 role1 的是哪个球员
  role2PlayerId: 'm3',    // 当前占据 role2 的是哪个球员
}
```

### 6.3 自动换人规则

```
自由人上场条件:
  1. 该位置在后排 (slot 0-2 = 前排，slot 3-5 = 后排)
  2. 该位置不是发球方自己的 1号位 (slot 5)
     → 只有接发方在 1号位才可用自由人

触发时机:
  - 每局开始时 → buildInitialLiberoRuntime()
  - 每次轮转后 → rotateTeamLiberoRuntime()
  - 每次手动换人后 → settleTeamLibero()

关键节点:
  - 到 4号位(slot 0) → 必须换回副攻本人
  - 到 1号位(slot 5) → 发球方用副攻本人，失发球权后换自由人
```

### 6.4 自由人设置流程（lineup 页）

```
1. 6个位置全部填满 → "开始添加自由人"
2. 点击一个副攻位置 → 自动配对对角位
3. 从下方队员列表选择自由人1/自由人2
4. 规则: 可不绑、单绑、双绑，同一人可双绑两个副攻
```

---

## 7. 队长管理（仅排球）

### 7.1 原始队长 vs 场上队长

- **原始队长**: 队伍 `captain` 标记（静态，创建赛事时指定）
- **场上队长**: 比赛中当前在场上的队长（动态运行态）

### 7.2 队长状态同步算法

```
syncCaptainState():
  for each side:
    if (原始队长在场上):
      自动设为场上队长 (无需用户操作)
    else if (当前场上队长在场上):
      保持不变
    else:
      弹窗 → 从场上6人中手动选择新队长
```

### 7.3 换人联动

```
手动换人 → 如果场上队长被换下:
  1. 先记录 substitution 事件
  2. 阻断后续记分
  3. 弹窗要求重新确认场上队长
  4. 记录 captain_change 事件（source: "auto"）

原始队长重新回场 → 自动恢复为场上队长
```

---

## 8. 退赛处理

```
retire(side):
  retiredSide = side
  对方 gameWins = gamesToWin (直接判胜)
  matchEnded = true
  isLocked = true (UI 锁定)
```

---

## 9. 撤销机制（记分板通用）

### 9.1 快照栈

```
每次操作前: pushHistory()  →  完整状态快照压栈
点击撤销:   historyStack.pop()  →  恢复上一步
栈上限:     40 条（超出 slice(-40)）
```

### 9.2 防套娃

```
pushHistory 保存的快照不包含 historyStack 自身
→ 避免 "历史里嵌历史" 导致 Storage 爆炸
```

---

## 10. 赛事规则参数校验

### 10.1 羽毛球创建校验

- 选手 ≥ 2 人
- `knockoutSlots` 为 2 的幂（4/8/16）
- `qualifiersPerGroup` ∈ {1, 2}
- 每组人数 > 出线人数

### 10.2 排球创建校验

- 队伍 ≥ 2 队
- 每队 6-12 人
- 球衣号码队内唯一且有效
- 恰好 1 名队长
- `bestOf` ∈ {3, 5}，`gamesToWin` = floor(bestOf/2) + 1

---

## 11. 事件同步策略（排球）

### 11.1 同步流程

```
appendMatchEvent():
  → matchEvents.push({ syncStatus: 'pending' })
  → scheduleEventFlush(800ms)  ← 800ms 防抖批量提交

flushPendingEvents():
  → PUT /matches/{id}/events
  → 后端按 (match_id, event_seq) 幂等 upsert
  → 成功: syncStatus → 'synced'
  → 失败: 不阻塞现场操作

finish 前强制:
  → flushPendingEvents() ← 确保所有事件落库后再结束比赛
```

### 11.2 Bootstrap 事件

进入比赛时自动确保两条基础事件：
1. `roster_snapshot` — 双方完整名单快照
2. `lineup_snapshot` — 每局开局站位快照

---

## 12. 羽毛球团体赛（苏迪曼杯式）

### 12.1 赛制概述

> 适用于 `participantType=1` + `sportType=0` + `teamMatchTemplate=1`

双方各派一支队伍，进行 **5 个单项**（男单 MS、女单 WS、男双 MD、女双 WD、混双 XD），**先赢得 3 项者获胜**。

### 12.2 创建与阵容

```
创建赛事:
  1. 创建者指定 N 支队伍（每队含多名队员）
  2. tournament.participantType = 1
  3. tournament.teamMatchTemplate = 1 (苏迪曼杯)
  4. 生成对阵表 → 每轮一场团体赛（parent match）

阵容编排 (team-lineup.vue):
  1. 双方从各自队员列表中选择每项的出场队员
  2. 单打项目（MS/WS）：每方选 1 人
  3. 双打项目（MD/WD/XD）：每方选 2 人（不可跨项目重复）
  4. 保存 → 生成 5 条 team_match_item 记录
```

### 12.3 子比赛创建与记分

```
开始单项比赛:
  1. 用户点击某项（如"男单"） → PUT /matches/{id}/team-items/MS/start
  2. 后端创建 child_match（match_record, stageType=2）
  3. 返回 childMatchId → 前端导航到普通羽毛球记分板

单项结束:
  1. 记分板调用 PUT /matches/{childMatchId}/finish
  2. 后端更新 team_match_item.status=2 + winnerSide
  3. 后端检查父比赛是否满足结算条件
```

### 12.4 团体赛结算

```
自动结算（子比赛结束时）:
  1. finishParentTeamMatchIfSettled() 统计所有子项
  2. 条件: 所有 5 项已完成 OR 淘汰赛阶段一方达到 3 胜
  3. 胜方 = 赢项多的一方（leftWins vs rightWins）
  4. 父比赛 status → 2, winnerId = 胜方队伍 ID
  5. 淘汰赛中胜者自动晋级

手动结算（淘汰赛提前结算）:
  1. 前端"直接结算"按钮 → PUT /matches/{id}/team-match/settle
  2. 后端校验: 淘汰赛阶段 + 一方 ≥ 3 胜
  3. 立即结算剩余未完成项目视为无效
```

### 12.5 出线规则

```
循环赛: 按胜场数排名（胜一场得 1 分）
淘汰赛: 5 项中先赢 3 项者晋级
```

---

## 13. 羽毛球接力追分赛

### 13.1 赛制概述

> 适用于 `teamMatchTemplate=2`（接力追分赛）

每队 **N 名队员**（默认 6 人，3 ≤ N ≤ 12），按固定顺序**接力上场**。每段由**相邻两名队员**组成：队员1+队员2、队员2+队员3、...、队员N+队员1（闭环）。

比赛只计**总分**，不分局。目标分 = `pointsPerSegment × N 段`。先达到目标分的一方获胜。

### 13.2 阵容编排

```
接力链验证 (validateRelayChain):
  1. 每段是一个 pair [firstId, secondId]
  2. 相邻段共享: 当前段的 secondId == 下一段的 firstId
  3. 末段回环: 末段的 secondId == 首段的 firstId
  4. 首成员唯一: 每段的首成员（firstId）不得重复
  5. 最少 3 段

阵容保存:
  1. 前端生成 order[] → buildRelayItemsFromOrders() 生成 items
  2. 后端保存到 team_match_item（itemCode=R1..RN）
  3. 所有段共享同一目标分
```

### 13.3 记分规则

```
计分:
  - R1 段开始，双方从 0:0 开始累计计分
  - 每达到 segmentTarget = baseScore × currentSegmentNo 时自动切换下一段
  - 到达 targetScore 方获胜

段切换:
  1. 任一方达到 segmentTarget → segmentSwitchPending = true
  2. 弹窗提示"进入下一段"
  3. 记录当前比分到 segmentScores[]
  4. 继续计分，目标更新为下一段

换边:
  - 双方可在任意时刻手动换边（sidesSwapped toggle）
  - 不影响逻辑分（始终以原始 left/right 为准）

结算:
  - 一方达到 targetScore → 比赛结束
  - 同步到后端: gameScores 存储为 relaySegmentScores
```

### 13.4 赛事创建特殊处理

```
applyRelayRule():
  - bestOf = 1, gamesToWin = 1 (无局分概念)
  - enableDeuce = false (不追分)
  - capPoint = relayMemberCount（复用字段表示接力人数）
  - 校验: 每队报名人数 ≥ relayMemberCount
```

---

## 14. 纯循环赛

### 14.1 赛制概述

> 适用于 `tournamentType=2`

与「小组赛+淘汰赛」不同，纯循环赛**没有淘汰赛阶段**。所有选手/队伍互相交手一次（单循环）或两次（双循环），最终排名即为比赛结果。

### 14.2 参数

| 字段 | 说明 |
|------|------|
| `tournamentType=2` | 纯循环赛 |
| `roundRobinRounds=1` | 单循环 |
| `roundRobinRounds=2` | 双循环（主客各一场） |

### 14.3 排名与结算

```
排名规则（同小组赛 §2.2）:
  胜场数 → 净胜局 → 净胜分 → H2H → 种子排名 → 名字序

结算:
  - 所有循环赛打完 → 赛事自动结束 (status=2)
  - 不生成淘汰赛对阵表
  - 最终排名直接显示在 groups 页面
```

---

## 15. 赛段规则

> 适用于创建赛事时启用 `roundRuleEnabled=true` 的赛事。

### 15.1 规则粒度

```
tournament 级规则
  bestOf / gamesToWin / pointsToWin / decidingPointsToWin / enableDeuce / capPoint
  → 默认适用于所有比赛

tournament_round_rule 赛段规则:
  stageType=0, roundNum=0     → 小组赛统一规则
  stageType=1, roundNum=N     → 淘汰赛第 N 轮规则
```

### 15.2 规则解析

```
TournamentRuleResolver.resolveForMatch(tournament, match):
  1. tournament.roundRuleEnabled != true → 使用 tournament 级规则
  2. 团体赛子比赛(stageType=2) → 先定位父比赛，再按父比赛赛段解析
  3. 小组赛 → 查 stageType=0 + roundNum=0
  4. 淘汰赛 → 查 stageType=1 + match.roundNum
  5. 找不到匹配规则 → 回退 tournament 级规则
```

### 15.3 创建校验

- `roundRules[].stageType` 只接受 `0` 或 `1`
- 小组赛规则使用 `roundNum=0`
- 淘汰赛规则使用实际轮次号
- `gamesToWin = floor(bestOf / 2) + 1`
- `decidingPointsToWin` 必须在 `1..pointsToWin` 范围内

---

## 16. 赛事归档

### 16.1 功能概述

> 适用于已结束的赛事（status=2）

创建者可将已结束的赛事归档，从主列表隐藏但仍可恢复。

```
归档:
  - PUT /tournaments/{id}/archive
  - 仅创建者可操作
  - 赛事 archived = 1
  - 从赛事大厅和"我的创建"中隐藏
  - 移至 "我的 → 归档" 页面

取消归档:
  - PUT /tournaments/{id}/unarchive
  - 恢复至正常显示
  - 可在"我的 → 归档"中找到并操作
```

---

## 17. 羽毛球三局两胜自动换边

### 17.1 局间换边确认

```
每局结束后:
  1. finishGame(winnerSide) → 检测是否最后一场
  2. 若非最后一场:
     - gameEndPromptPending = true
     - UI 阻断，弹窗显示"第 X 局结束"和本局比分
     - 用户点击"换边继续"后:
       a. currentGameNo += 1
       b. 重置分数为 0:0
       c. serveSide 设为上一局获胜方
       d. bestOf=3 的第 2、3 局调用 applySideSwitch() 交换左右显示
       e. 保存状态，继续下一局
  3. 若最后一场 → 进入比赛结束流程
```

### 17.2 决胜局中点换边

```
触发条件:
  - 当前为最后一局（bestOf=1 时为第 1 局，bestOf=3 时为第 3 局）
  - 任一方得分达到 ceil(pointsToWin / 2)
    - 21 分制 → 11 分
    - 15 分制 → 8 分
    - 11 分制 → 6 分
  - finalGameSideSwitchHandled = false

弹窗流程:
  1. shouldPromptFinalGameSideSwitch(score) → finalGameSideSwitchPending = true
  2. UI 阻断: 所有按钮 disabled，记分暂停
  3. 弹窗提示"第 X 局达到 N 分，是否交换场地？"
  4. 用户选择:
     - 不换边继续 → finalGameSideSwitchHandled = true，继续记分
     - 换边 → applySideSwitch() + finalGameSideSwitchHandled = true，继续记分
  5. 恢复正常记分
```

### 17.3 自动结算

```
比赛结束后的自动行为:
  1. 比赛锁定（isLocked = true）
  2. scheduleAutoSettlement() → 10 秒倒计时
  3. 倒计时结束 → 自动 syncAndBack() 同步到后端
  4. 页面卸载 → clearAutoSettlementTimer() 取消计时器
  5. 用户也可手动点击"同步结算"提前触发
```

---

## 18. 比赛操作权限

### 18.1 权限模型

所有比赛（match）的写入操作（记分、退赛、重置、结束、保存事件、填写对阵名单等）均须经过双重守卫：

1. **视图层守卫**（bracket.vue / groups.vue）：前端根据 canOperateMatches 标志拦截无权限的比赛点击，提示用户先录入裁判身份。
2. **页面层守卫**（各记分板 / 阵容页）：进入页面的 onLoad 中调用 GET /matches/{id}/can-operate，后端校验通过后才允许渲染操作 UI。

### 18.2 can-operate 接口

```
GET /api/v1/matches/{id}/can-operate  🔒
```

后端逻辑（MatchServiceImpl.canOperateMatch()）：

1. 比赛不存在 → false
2. 赛事已归档 → false
3. 当前用户为赛事创建者 → true
4. 当前用户已被授予裁判权限（tournament_referee_grant 表中存在记录） → true
5. 以上均不满足 → false

前端的 requireMatchOperator(matchId) 封装了 ensureAuth() + 调用此接口 + 失败 toast 的统一守卫逻辑。

### 18.3 裁判验证流程

赛事详情页（detail.vue）提供「裁判验证」入口：

1. 非创建者用户点击 → 弹出密码输入面板
2. 输入 8 位数字密码 → POST /tournaments/{id}/referee-auth
3. 后端校验通过 → 在 tournament_referee_grant 表中写入授权记录
4. 此后该用户在 bracket / groups 视图中 canOperateMatches=true，可操作所有比赛

### 18.4 登录入口

当前后端支持三种身份入口：

1. 小程序微信登录：`POST /auth/wechat-login`
2. Web 账号注册：`POST /auth/register`
3. Web 密码登录：`POST /auth/password-login`

三者最终都签发同一种 JWT；后续比赛操作权限仍统一走 `AuthInterceptor → AuthContext → AuthGuard`。

### 18.5 归档只读

赛事归档后，所有 canOperateMatches 返回 false。前端在 bracket / groups 中拦截比赛操作，提示「已归档，只读查看」；已完成的排球比赛仍可进入记录页查看详情。
