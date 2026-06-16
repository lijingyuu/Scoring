# 排球计分板模块 · Volleyball Scoreboard

> 一个完整的排球比赛计分系统，支持手机端与 Pad 端差异化适配、换人、自由人管理、换边、队长指定、主题调色、比赛记录 PDF 导出。

---

## 目录

- [1. 架构概览](#1-架构概览)
- [2. 文件结构](#2-文件结构)
- [3. 页面路由](#3-页面路由)
- [4. 核心数据流](#4-核心数据流)
- [5. 设备适配策略](#5-设备适配策略)
- [6. 功能详解](#6-功能详解)
  - [6.1 记分牌核心 (Scoreboard)](#61-记分牌核心-scoreboard)
  - [6.2 轮次填写 (Lineup)](#62-轮次填写-lineup)
  - [6.3 比赛记录 (Record)](#63-比赛记录-record)
  - [6.4 赛事创建 (Create)](#64-赛事创建-create)
- [7. 状态管理](#7-状态管理)
- [8. 比赛规则引擎](#8-比赛规则引擎)
- [9. 自由人系统](#9-自由人系统)
- [10. 队长管理](#10-队长管理)
- [11. 换边机制](#11-换边机制)
- [12. 主题调色板](#12-主题调色板)
- [13. 事件同步](#13-事件同步)
- [14. CSS 架构](#14-css-架构)
- [15. 关键设计决策与踩坑记录](#15-关键设计决策与踩坑记录)
- [16. 后端接口依赖](#16-后端接口依赖)

---

## 1. 架构概览

```
┌─────────────────────────────────────────────────────────────────┐
│                      src/pages/volleyball/                       │
├─────────────────────────────────────────────────────────────────┤
│  scoreboard.vue          ← 入口/编排层（加载/错误态 + 设备路由）  │
│       │                                                          │
│       ├── ScoreboardPhone.vue   ← 手机端计分板 UI               │
│       ├── ScoreboardPad.vue     ← Pad 端计分板 UI               │
│       └── composables/useScoreboard.js ← 核心逻辑（2200+ 行）    │
│                                                                   │
│  lineup.vue               ← 轮次填写页（首发阵容+自由人绑定）    │
│  record.vue               ← 比赛记录页（PDF 导出）               │
│  match-state.js           ← 状态数据模型与持久化工具             │
├─────────────────────────────────────────────────────────────────┤
│                  src/pages/create/volleyball.vue                  │
│                    ← 排球赛事创建（队伍+成员+赛制）              │
└─────────────────────────────────────────────────────────────────┘
```

**技术栈**: uni-app (Vue 3 Composition API) · 微信小程序 + H5 双端 · 纯前端状态机

**核心思想**: `scoreboard.vue` 只是一个 12 行的薄路由层 — 它调用 `useScoreboard()` composable，通过 `reactive()` 包裹返回值使所有 ref 自动解包，然后根据 `ctx.isTablet` 分发给 `ScoreboardPhone` 或 `ScoreboardPad`。两个 UI 组件共享完全相同的逻辑层，仅 CSS 不同。

---

## 2. 文件结构

```
src/pages/volleyball/
├── scoreboard.vue                      # 入口页（12行模板，加载/错误/设备路由）
├── lineup.vue                          # 轮次填写页（1658行，含完整阵容编辑器）
├── record.vue                          # 比赛记录页（868行，含 PDF 导出）
├── match-state.js                      # 状态数据模型：court、libero、历史栈、持久化
│
├── components/
│   ├── ScoreboardPad.vue               # Pad 端计分板（1383行）
│   └── ScoreboardPhone.vue             # 手机端计分板（1258行）
│
└── composables/
    └── useScoreboard.js                # 核心业务逻辑（2213行）
        ├── 比赛状态：分数、局数、发球方、暂停
        ├── 阵容管理：court、自由人、换人
        ├── 设备适配：isTablet、orientation、sizeBand、pageClassNames
        ├── 历史栈：撤销支持（最多40条）
        ├── 事件同步：批量 event flush（800ms 防抖）
        ├── 队长确认：弹窗选择流程
        ├── 换边提示：决胜局8分自动触发
        └── 主题调色：14色 CSS变量 + RGB微调 + 存后端
```

**辅助依赖**:
```
src/utils/
├── interaction-guard.js                # useDelayedTapGate / useActionLock
└── request.js                          # HTTP 封装（自动 token、错误 toast）
```

---

## 3. 页面路由

| 路径 | 页面 | 强制方向 | 说明 |
|------|------|----------|------|
| `pages/volleyball/scoreboard` | 记分板 | **横屏** (landscape) | 入口根据设备自动路由到 Phone/Pad |
| `pages/volleyball/lineup` | 轮次填写 | **竖屏** (portrait) | 首发站位 + 自由人绑定 |
| `pages/volleyball/record` | 比赛记录 | **竖屏** (portrait) | 完整比赛报告 + PDF导出 |
| `pages/create/volleyball` | 创建比赛 | 无限制 | 赛制选择 + 队伍编辑器 |

**页面间流转**:
```
创建比赛 → 赛事详情 → 进入比赛 → lineup（轮次填写）
                                    ↓ 确认
                                scoreboard（记分牌）
                                    ↓ 每局结束
                                lineup（下一局轮次）
                                    ↓ 比赛结束
                                record（比赛记录）
```

---

## 4. 核心数据流

```
                    ┌──────────────┐
                    │   Backend    │
                    │  (REST API)  │
                    └──────┬───────┘
                           │ GET bracket / lineup-config / theme-config
                           │ PUT lineup-config / events / finish / restart
                    ┌──────▼───────┐
                    │ match-state  │  ← 本地 Storage 缓存（Match ID 为 key）
                    │ .js          │     normalizeMatchState() 防御性恢复
                    └──────┬───────┘
                           │ createEmptyMatchState()
                           │ loadMatchState() / saveMatchState()
                    ┌──────▼────────┐
                    │ useScoreboard │  ← 2200+ 行 composable
                    │ .js           │     所有业务逻辑在此
                    └──────┬────────┘
                           │ reactive(ctx) — ref 自动解包
                    ┌──────▼────────┐
                    │ scoreboard    │  ← 薄路由层
                    │ .vue          │     v-if="!ctx.isTablet" → Phone
                    └──────┬────────┘     v-else → Pad
               ┌───────────┴───────────┐
       ┌───────▼──────┐        ┌───────▼──────┐
       │ Scoreboard   │        │ Scoreboard   │
       │ Phone.vue    │        │ Pad.vue      │
       │ (共享 props) │        │ (共享 props) │
       └──────────────┘        └──────────────┘
```

**关键设计**: `scoreboard.vue` 中使用 `reactive(useScoreboard())` 包裹 composable 返回值，使得模板中可直接写 `ctx.leftScore` 而非 `ctx.leftScore.value`。两个 UI 组件通过 `defineProps({ ctx })` 接收同一个响应式对象。

---

## 5. 设备适配策略

### 5.1 设备判定

```js
// useScoreboard.js L237
isTablet = Math.min(windowWidth, windowHeight) >= 720  // CSS像素
```

### 5.2 六级尺寸带 (Size Bands)

| Band | 条件 | 典型设备 |
|------|------|----------|
| `phone` | 短边 < 720px | 手机 |
| `pad-portrait-sm` | 竖屏宽度 ≤ 820px | iPad mini 竖屏 |
| `pad-portrait-lg` | 竖屏宽度 > 820px | iPad Pro 竖屏 |
| `pad-landscape-sm` | 横屏宽度 ≤ 1228px | iPad 横屏 |
| `pad-landscape-md` | 1228–1400px | iPad Pro 11" 横屏 |
| `pad-landscape-lg` | > 1400px | iPad Pro 12.9" 横屏 |

每个 band 对应不同的 CSS 变量覆盖，通过 `pageClassNames` 注入到根元素。

### 5.3 Phone vs Pad 关键差异

| 维度 | Phone | Pad |
|------|-------|-----|
| **球场渲染** | 简单矩形 + 玻璃边框 | 完整球场底色 + 白色边线 + 球网 + 进攻线伪元素 |
| **阴影** | 无面板阴影 | `box-shadow: 0 14px 34px` 三层面板 |
| **队长选择** | 3 列网格 | `pad-landscape-lg` 下 4 列网格 |
| **主题配色** | 暖橙强调色 `#EC822F` / 青色队长色 | 琥珀强调色 `#F4A53A` / 绿色队长色 |
| **CSS 变量** | `clamp()` 较小上限 | `clamp()` 较大上限，额外 `.is-tablet` 覆盖层 |

### 5.4 H5 竖屏预览 (Landscape Preview)

H5 端在竖屏设备上打开横屏记分板时，使用 CSS `transform: scale()` 将 1280×720 的设计尺寸缩放适配到实际视口：

```js
// useScoreboard.js L667-685
scale = Math.min(viewportW / 1280, viewportH / 720)
```

---

## 6. 功能详解

### 6.1 记分牌核心 (Scoreboard)

#### 布局结构 (三栏)

```
┌──────────┬──────────────────────────────┬──────────┐
│ 左队名单  │        中央面板               │ 右队名单  │
│          │  ┌──────────────────────┐   │          │
│ 球员列表  │  │ 局号/规则/已完成局分/  │   │ 球员列表  │
│ (scroll) │  │ 撤销/退赛             │   │ (scroll) │
│          │  ├──────────────────────┤   │          │
│          │  │    比分面板           │   │          │
│          │  │ 左队分 : 右队分       │   │          │
│          │  │  总比分 + 暂停按钮    │   │          │
│          │  ├──────────────────────┤   │          │
│          │  │    球场轮转面板       │   │          │
│          │  │  左半场 | 网 | 右半场  │   │          │
│          │  │  3×2 球员站位格       │   │          │
│          │  └──────────────────────┘   │          │
└──────────┴──────────────────────────────┴──────────┘
```

#### 加分流程 (addScore)

```
点击己方比分区域
  → pushHistory()                    // 保存撤销点
  → score += 1
  → if (发球方 !== 得分方):
       rotateCourt(得分方)            // 顺时针轮转
       rotateTeamLiberoRuntime()     // 自由人位置跟随轮转
       serveSide = 得分方
  → settleAllLiberoStates()          // 自动交换自由人
  → if (决胜局 && 得分 === 8):
       openFinalGameSideSwitchPrompt() // 触发换边提示
  → if (checkWinCondition):
       finishGame()                   // 可能结束比赛或进入下一局
  → persistState()
```

#### 换人流程 (Substitution)

```
1. 点击左侧名单中的替补队员 → selectedBench = { side, memberId }
2. 点击球场上目标位置的 slot → handleCourtSlot(side, dataIndex)
   → pushHistory()
   → court[dataIndex] = inMemberId
   → settleTeamLibero(side)          // 自动处理自由人位置
   → syncCaptainState()              // 如果原队长被换下，弹窗选择新队长
   → appendMatchEvent('substitution', ...)
```

#### 锁定状态 (isLocked)

当比赛结束（正常结束或退赛），整个界面进入锁定态：
- 比分区域不可点击
- 换人和暂停按钮变灰
- 弹出结算遮罩层，显示胜者、总比分、局分细节
- "重新开始"按钮带 10 秒倒计时（防误触）
- "同步结算"按钮提交最终结果到后端

---

### 6.2 轮次填写 (Lineup)

#### 功能流程

```
主页面 (setupPage === 'main')
  ├── 选择发球方（左队 / 右队）
  ├── 首局/决胜局：显示"换边"按钮
  ├── 队伍入口卡片（显示 已填/6 进度）
  └── "开始比赛" → 保存到后端 → 跳转记分板

编辑器 (setupPage === 'left' | 'right')
  ├── 3×2 站位网格（对应排球场 1-6 号位）
  ├── 顺时针/逆时针轮转按钮
  ├── 右侧球员名单（scroll-view）
  ├── 自由人设置流程：
  │   1. "开始添加自由人" → 进入副攻选择模式
  │   2. 点击副攻位置（自动配对对角位）
  │   3. "确定" → 进入自由人绑定模式
  │   4. 为每个副攻选择对应的自由人
  └── "完成" → 返回主页面
```

#### 站位位置映射

```js
SLOT_POSITIONS = [4, 3, 2, 5, 6, 1]  // 数组索引 → 实际球衣号位
SLOT_OPPOSITE_MAP = { 0:5, 1:4, 2:3, 3:2, 4:1, 5:0 }  // 对角位
```

#### 轮转算法

```js
// 顺时针：0→1, 1→2, 2→5, 3→0, 4→3, 5→4
// 逆时针：0→3, 1→0, 2→1, 3→4, 4→5, 5→2
```

#### 换边 (side swap)

在首局或决胜局开始前显示"换边"按钮，调用 `swapMatchStateSides()` 交换两支队伍在屏幕上的左右位置，同时交换所有相关状态（court、libero setup、captain、serve side 等）。

---

### 6.3 比赛记录 (Record)

#### 功能
- 从后端 `GET /matches/{id}/record` 获取完整比赛数据
- 以"纸质记录表"风格渲染（米色背景 + 衬线字体）
- 包含：赛事信息、总分、每局比分、双方名单、每局轮转图、暂停记录、签名区
- **PDF 导出**: H5 端使用 `window.print()` + `@media print` 样式；小程序端待实现

#### PDF 打印优化
```css
@media print {
  .toolbar { display: none; }    /* 隐藏工具栏 */
  .page { background: #fff; }    /* 白色背景 */
  .paper { box-shadow: none; }   /* 去阴影 */
}
```

---

### 6.4 赛事创建 (Create)

排球赛事创建 `pages/create/volleyball.vue` 提供：

- **赛制选择**: 纯淘汰赛 / 小组赛+淘汰赛
- **局数**: 三局两胜 / 五局三胜
- **队伍编辑器**:
  - 每队至少 6 名球员（最多 12 名）
  - 球衣号码唯一性校验
  - 队长指定（恰好 1 名）
  - "填入测试样例"快速填充开发数据
- **校验**: 队名非空、每队 ≥6 人、号码有效且不重复、恰好 1 名队长

---

## 7. 状态管理

### 核心状态字段

```js
// 比赛信息
tournamentId, matchId, info { bestOf, gamesToWin }
leftTeam, rightTeam          // { name, members[{ id, name, jerseyNumber, captain, libero }] }

// 比分状态
leftScore, rightScore        // 当前局比分
leftGameWins, rightGameWins  // 赢得局数
currentGameNo                // 第几局
gameScores                   // [{ gameNo, leftScore, rightScore, winnerSide }]
serveSide                    // 'left' | 'right'
leftTimeouts, rightTimeouts  // 剩余暂停次数（每局2次）

// 阵容状态
leftCourt, rightCourt        // [memberId × 6] 场上球员
baseLeftCourt, baseRightCourt // 基础站位（用于自由人判断）
leftLiberoSetup, rightLiberoSetup    // { pairIndexes[], libero1Id, libero2Id }
leftLiberoRuntime, rightLiberoRuntime // { role1SlotIndex, role2SlotIndex, role1PlayerId, role2PlayerId }

// 队长
leftCaptainMemberId, rightCaptainMemberId

// 事件
matchEvents[]                // 所有比赛事件（换人、暂停等）
nextEventSeq, lastSyncedEventSeq

// 流程控制
lineupReady, finalGameSideSwitchPending, finalGameSideSwitchHandled
retiredSide, matchEnded, winnerName

// 历史栈
historyStack[]               // 最多 40 条历史快照，支持撤销

// 设备
windowWidth, windowHeight
```

### 持久化策略

- **Key**: `volleyball_scoreboard_state_{matchId}`
- **存储**: `uni.setStorageSync()`（微信小程序 10MB 限制）
- **恢复**: `normalizeMatchState()` 对所有字段进行防御性校验和默认值填充
- **历史栈**: 最多保留 40 条，超出则 `slice(-40)`

### 撤销机制

```js
undo() {
  const snapshot = historyStack.pop()    // LIFO
  applyState(snapshot)                   // 恢复全部状态
  syncCaptainState()                      // 检查队长是否需要重新指定
  persistState()
}
```

---

## 8. 比赛规则引擎

### 排球计分规则

```js
currentTargetPoints = computed(() => {
  // 前几局 25 分，决胜局 15 分
  return currentGameNo === bestOf ? 15 : 25
})

checkWinCondition(myScore, opponentScore) {
  // 先到目标分 且 领先 ≥2 分
  return myScore >= currentTargetPoints && (myScore - opponentScore) >= 2
}
```

### 单局结束 → 比赛结束判定

```
finishGame(winnerSide):
  gameScores.push(当前局比分)
  winnerSide === 'left' ? leftGameWins++ : rightGameWins++
  
  if (leftGameWins >= gamesToWin || rightGameWins >= gamesToWin):
    matchEnded = true   // 比赛结束
  else:
    currentGameNo += 1  // 进入下一局
    resetScore()
    goToNextLineup()    // 跳转到轮次填写页，发球方轮换
```

### 退赛处理

```
retire(side):
  retiredSide = side
  对方 gameWins = gamesToWin（直接判胜）
  matchEnded = true
```

---

## 9. 自由人系统

自由人（Libero）是排球中专门的防守球员，只能在后排替换，不能发球或进攻。

### 9.1 数据结构

```js
// 自由人配置（赛前设置）
liberoSetup = {
  pairIndexes: [2, 3],    // 两个副攻的数组索引（必须是对角位）
  libero1Id: 'm5',        // 自由人1 的成员ID
  libero2Id: 'm7',        // 自由人2 的成员ID
}

// 自由人运行时状态（比赛中动态）
liberoRuntime = {
  role1SlotIndex: 2,      // 角色1 当前所在 slot
  role2SlotIndex: 3,      // 角色2 当前所在 slot
  role1PlayerId: 'm2',    // 角色1 当前在场上的球员ID
  role2PlayerId: 'm3',    // 角色2 当前在场上的球员ID
}
```

### 9.2 核心算法

#### 自由人使用判定 `shouldRoleUseLibero(side, slotIndex, liberoId)`

```js
if (!liberoId) return false           // 没有绑定自由人
if (isFrontSlot(slotIndex)) return false  // 前排不用自由人（slot 0-2）
if (slotIndex === 5) {
  return serveSide !== actualSide     // 1号位：仅在非发球方时用自由人
}
return true
```

#### 自由人自动交换 `settleTeamLibero(side)`

每局开始时或每次轮转后自动调用，根据当前 serve side 决定两个自由人角色应该出手在哪个 slot，自动将 court 中的对应位置替换为自由人 ID。

#### 优先级规则 `compareLiberoAssignmentPriority`

```
1. 应使用自由人的角色 > 不应使用的
2. 两者都在 1号位 时，优先级相同
3. 否则 slot 编号大的优先（后排优先）
```

### 9.3 Runtime 初始化

`buildInitialLiberoRuntime(side)` — 从 lineup config 构建 initial runtime：
1. 从 baseCourt 找到两个副攻
2. 按球衣号码排序确定 role1/role2
3. 检测当前 court 中副攻实际位置
4. 确保 role1 和 role2 的 slotIndex 是对角位

---

## 10. 队长管理

### 场上队长指定

比赛中，如果原始队长不在场上（被换下），需要从场上 6 人中重新指定：

```
syncCaptainState():
  for each side:
    if (原始队长在场上):
      自动设为队长（无需用户操作）
    else if (当前有队长且在场上):
      保持不变
    else:
      加入 captainPromptQueue → 弹窗选择
```

### 弹窗 UI

- 显示 6 名场上队员（3×2 或 4×2 网格）
- 每项显示位置标签 + 号码 + 姓名
- 选中项高亮，点击"确定"确认

---

## 11. 换边机制

### 三种换边场景

| 场景 | 时机 | 效果 |
|------|------|------|
| **局间换边** | 每局结束 → 进入下一局轮次填写 | 自动 `swapMatchStateSides()` + 发球方轮换 |
| **决胜局中换边** | 决胜局任一方达到 8 分 | 弹窗提示，确认后交换两侧显示 |
| **手动换边** | 轮次填写页首局/决胜局 | 用户点击"换边"按钮 |

### 决胜局换边交互

```
得分达到 8 → finalGameSideSwitchPending = true
  → 弹窗："请双方队员交换场地" + 当前比分
  → "保持当前位置"（5秒倒计时后可用，不换边继续）
  → "确定"（执行换边：swapSides + 记录 side_switch 事件）
```

---

## 12. 主题调色板

### 14 色 CSS 变量

```js
THEME_DEBUG_TOKENS = [
  'themeBase',        // 主背景
  'themeBaseDeep',    // 深背景
  'themeAccent',      // 强调色（按钮、高亮）
  'themeAccentInk',   // 强调色上文字
  'captain',          // 队长高亮色
  'courtSurface',     // 球场底色
  'rightScoreAccent', // 右侧比分边框色
  'dangerAccent',     // 危险按钮色
  'textStrong',       // 主白色文字
  'surfaceGlass',     // 面板玻璃色
  'shadowColor',      // 阴影色
  'overlayMask',      // 遮罩色
  'courtSlotAccent',  // 球场slot描边色
  'rotationPanelSurface', // 轮次面板背景
]
```

### Phone/Pad 独立默认主题

两套独立配色通过 `themeDevice` (derived from `isTablet`) 自动切换：
- **Phone**: 暖橙强调色 `#EC822F` + 青色队长色 `#2EC6FD`
- **Pad**: 琥珀强调色 `#F4A53A` + 绿色队长色 `#739C69`

### 存储层级

```
localStorage (per-device key)
  ↓ fallback
Server (per-device theme-config API)
  ↓ fallback
Server legacy theme (旧版单主题)
  ↓ fallback
Hard-coded defaults
```

### 调试工具

右下角浮动"调色"按钮：
- 14 色 HEX 输入框
- RGB 三通道滑块微调
- "重置" → 恢复默认
- "存后端" → `PUT /theme-config`
- "复制变量" → 复制完整 CSS 变量到剪贴板

---

## 13. 事件同步

### 事件类型

| eventType | 含义 | 触发时机 |
|-----------|------|----------|
| `roster_snapshot` | 名单快照 | 比赛加载时（确保有一条） |
| `lineup_snapshot` | 阵容快照 | 每局开始时 |
| `substitution` | 换人 | 点击 court slot 完成换人 |
| `timeout` | 暂停 | 点击暂停按钮 |
| `captain_change` | 队长更换 | 队长确认弹窗确认 |
| `side_switch` | 换边 | 决胜局8分换边 / 手动换边 |

### 同步策略

```js
appendMatchEvent() → matchEvents.push({ syncStatus: 'pending' })
                  → scheduleEventFlush(800ms)  // 800ms 防抖批量提交

flushPendingEvents()
  → PUT /matches/{id}/events
  → 更新 syncStatus: 'synced'
  → lastSyncedEventSeq = max(syncedSeqs)
```

### Bootstrap 事件

`ensureBootstrapEvents()` 确保：
- 必须有一条 `roster_snapshot` 事件（记录双方完整名单）
- 每局必须有一条 `lineup_snapshot` 事件（记录开局站位）

---

## 14. CSS 架构

### 变量层级

```
:root 默认（Phone 端）
  └── .is-tablet 覆盖（Pad 端更大字体/间距）
      ├── .pad-landscape-sm 微调
      ├── .pad-landscape-md 微调
      └── .pad-landscape-lg 微调
  └── @media (max-width: 1400px) 回退
  └── @media (max-width: 1100px) 回退
```

### 核心设计原则

1. **全 `clamp()` 流体尺寸**: 所有间距、字号、圆角使用 `clamp(min, preferred, max)` 基于 `vmin`，确保在任何屏幕比例下都可用
2. **CSS 自定义属性**: 所有颜色通过 `--theme-*` 和 `--*-rgb` 变量驱动，支持运行时动态修改
3. **Phone 与 Pad 共享模板结构**: 模板 HTML 几乎完全相同，差异仅通过 CSS 类名 `.is-tablet` 覆盖实现
4. **避免硬编码**: 没有固定 `px` 值（除了 `border: 1px` 和极小值）

### Phone/Pad CSS 关键差异

| 属性 | Phone | Pad |
|------|-------|-----|
| 球队面板 `box-shadow` | 无 | `0 14px 34px` |
| 球场 `background` | 无 (透明 glass) | `var(--court-surface)` 绿色 |
| 球场 `border` | 无 | `var(--court-line-width) solid` 白线 |
| 球网 `width` | 仅线条 | 更粗线条 |
| 进攻线 (伪元素) | 无 | `::after` 白色虚线 |
| 球场 slot `background` | `rgba(glass, 0.08)` | `transparent` |
| 队长选择网格 | 3 列 | 4 列 (pad-landscape-lg) |
| 编队按钮 | 纵向排列 | 横向排列 |

---

## 15. 关键设计决策与踩坑记录

### 决策 1: reactive() 包裹 composable

`scoreboard.vue` 中使用 `reactive(useScoreboard())` 而非直接使用 composable 返回值。这使得模板中可以直接写 `ctx.leftScore` 而非 `ctx.leftScore.value`，大幅简化模板代码。代价是需要显式返回所有需要的 computed/ref。

### 决策 2: 单一 composable 承载全部逻辑

所有业务逻辑集中在 `useScoreboard.js` (2200+ 行)，而非分散到多个 composable。考虑到排球记分的强耦合性（换人联动自由人联动队长），这样避免了循环依赖和状态同步问题。

### 决策 3: match-state.js 纯函数

`match-state.js` 不包含任何 Vue 响应式逻辑，全部为纯函数。可以被 lineup.vue、scoreboard、后端测试等任何上下文安全调用。

### 踩坑 1: displaySideSwapped 模式

由于"换边"操作本质上是交换两队在场上的物理位置，内部使用 `screenLeftParticipantSide` 跟踪"屏幕左侧是哪支队伍"。`swapMatchStateSides()` 交换所有相关的 court、libero、captain 等状态，确保数据结构一致性。

### 踩坑 2: 自由人 Runtime 初始化

`buildInitialLiberoRuntime` 需要在没有历史 runtime 数据时推断初始状态，处理了多种边界情况：court 中副攻已被替换、两个 role 指向同一个 slot、对角位计算等。

### 踩坑 3: WeChat button 硬编码宽度

微信小程序 `<button>` 组件有硬编码的 `min-width: 120rpx`，CSS 无法完全覆盖。解决方案：将步进器的 `<button>` 改为 `<view>`（仅在不涉及原生能力的按钮）。

### 踩坑 4: `windowWidth`/`windowHeight` 获取兼容

uni-app 不同版本 API 不一致：
```js
try { uni.getWindowInfo() }     // 新版 API
catch { uni.getSystemInfoSync() } // 旧版 fallback
```

---

## 16. 后端接口参考

> 基础路径: `/api/v1` · 协议: HTTP/1.1 · 数据格式: JSON · 鉴权: Bearer Token (JWT)

### 16.1 通用约定

**统一响应格式**：

```json
{
  "code": 0,
  "message": "success",
  "data": <具体数据>
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | int | `0` = 成功，非零 = 业务错误 |
| `message` | string | 提示信息 |
| `data` | any | 响应数据（`null` 表示无返回体） |

前端 `request.js` 自动校验 `code === 0`，成功时直接 resolve `data` 字段；失败时统一 toast 提示（可通过 `options.silent` 静默）。所有实体 ID 使用雪花算法生成的 19 位整数，以字符串形式传输。

---

### 16.2 记分板直接调用的接口

#### 16.2.1 获取赛事对阵表（进入记分板时）

```
GET /api/v1/tournaments/{id}/bracket  🔓
```

**响应** — `TournamentBracketVO`

```json
{
  "id": "329847230984723",
  "name": "2026 春季排球赛",
  "status": 1,
  "sportType": 1,
  "tournamentType": 0,
  "bestOf": 5,
  "gamesToWin": 3,
  "players": [
    {
      "id": "p1",
      "name": "火箭队",
      "seed": 1,
      "members": [
        { "id": "m1", "name": "队员A", "jerseyNumber": 1, "captain": true, "libero": false },
        { "id": "m2", "name": "队员B", "jerseyNumber": 5, "captain": false, "libero": true }
      ]
    }
  ],
  "matches": [
    {
      "id": "m1",
      "roundNum": 1,
      "matchIndex": 0,
      "status": 2,
      "leftPlayerId": "p1",
      "rightPlayerId": "p2",
      "winnerSide": "left",
      "scoreDisplay": "25:20,25:22,25:18",
      "nextMatchId": "m5"
    }
  ]
}
```

**调用位置**: `useScoreboard.js → loadMatch()` / `lineup.vue → loadMatch()`

**使用方式**: 前端从 `players` 中按 `match.leftPlayerId` / `match.rightPlayerId` 匹配双方队伍，用 `bestOf` 和 `gamesToWin` 初始化规则引擎。

---

#### 16.2.2 获取阵容配置

```
GET /api/v1/matches/{id}/lineup-config?gameNo={gameNo}  🔓
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `gameNo` | int | 是 | 局号 |

**响应** — `MatchLineupConfigVO`

```json
{
  "gameNo": 1,
  "exists": true,
  "effectiveFromGameNo": 1,
  "config": {
    "serveSide": "left",
    "left": {
      "court": ["m1", "m2", "m3", "m4", "m5", "m6"],
      "middlePairIndexes": [2, 3],
      "libero1Id": "m10",
      "libero2Id": null
    },
    "right": {
      "court": ["m7", "m8", "m9", "m11", "m12", "m13"],
      "middlePairIndexes": [1, 2],
      "libero1Id": "m20",
      "libero2Id": null
    }
  }
}
```

| 字段 | 说明 |
|------|------|
| `effectiveFromGameNo` | 实际生效的局号（若请求局无配置则回退到最近一局） |
| `court` | 长度为 6 的 memberId 数组，对应 4-3-2-5-6-1 号位 |
| `middlePairIndexes` | 两个副攻在 court 中的索引（必须是对角位） |
| `libero1Id` / `libero2Id` | 自由人 memberId（可为 null） |

**调用位置**: `lineup.vue → loadMatch()`

**缓存策略**: 若本地已有未提交的 lineup draft（`hasLocalLineupDraft`），优先使用本地数据而非远程配置。

---

#### 16.2.3 保存阵容配置

```
PUT /api/v1/matches/{id}/lineup-config  🔒
```

**请求体**

```json
{
  "gameNo": 1,
  "serveSide": "left",
  "left": {
    "court": ["m1", "m2", "m3", "m4", "m5", "m6"],
    "middlePairIndexes": [2, 3],
    "libero1Id": "m10",
    "libero2Id": null
  },
  "right": {
    "court": ["m7", "m8", "m9", "m11", "m12", "m13"],
    "middlePairIndexes": [1, 2],
    "libero1Id": "m20",
    "libero2Id": null
  }
}
```

**响应** — 无返回体 (`null`)

**调用位置**: `lineup.vue → confirmLineup()`

**前置校验**: 前端确保双方 `court` 均填满 6 人、自由人设置已完成 sanitize，才发起请求。

---

#### 16.2.4 批量保存比赛事件

```
PUT /api/v1/matches/{id}/events  🔒
```

**请求体**

```json
{
  "events": [
    {
      "eventSeq": 1,
      "eventType": "substitution",
      "gameNo": 1,
      "leftScore": 12,
      "rightScore": 10,
      "serveSide": "left",
      "payloadJson": "{\"side\":\"left\",\"outMemberId\":\"m3\",\"inMemberId\":\"m15\"}"
    }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `eventSeq` | int | 是 | 全局事件序号（>0），从 1 开始递增 |
| `eventType` | string | 是 | 事件类型（见下方事件类型表） |
| `gameNo` | int | 是 | 所在局号 |
| `leftScore` | int | 是 | 事件发生时左侧得分 |
| `rightScore` | int | 是 | 事件发生时右侧得分 |
| `serveSide` | string | 是 | `"left"` 或 `"right"` |
| `payloadJson` | string | 是 | 事件负载，JSON 字符串 |

**事件类型与 payload 结构**：

| eventType | payload 字段 | 说明 |
|-----------|-------------|------|
| `substitution` | `{ side, outMemberId, inMemberId }` | 换人 |
| `timeout` | `{ side }` | 暂停 |
| `captain_change` | `{ side, captainMemberId, originalCaptainMemberId, source }` | 队长更换，source: `"auto"` / `"manual"` |
| `side_switch` | `{ reason, screenLeftParticipantSide }` | 换边，reason: `"deciding_game_mid_switch"` / `"between_games"` / `"manual"` |
| `roster_snapshot` | `{ leftMembers, rightMembers }` | 双方完整名单快照 |
| `lineup_snapshot` | `{ left: {court, middlePairIndexes, libero1Id, libero2Id}, right: {...}, serveSide }` | 开局阵容快照 |

**同步策略**：

- 前端 800ms 防抖后批量提交（`scheduleEventFlush(800)`）
- 后端按 `(match_id, event_seq)` 去重 upsert
- 同步成功后 `syncStatus` 从 `"pending"` 变为 `"synced"`
- `finish` 接口调用前会强制 `flushPendingEvents()` 确保所有事件已落库

**调用位置**: `useScoreboard.js → flushPendingEvents()`

---

#### 16.2.5 结束比赛

```
PUT /api/v1/matches/{id}/finish  🔒
```

**请求体**

```json
{
  "winnerSide": "left",
  "leftScore": 2,
  "rightScore": 0,
  "leftGameWins": 3,
  "rightGameWins": 0,
  "retiredSide": null,
  "gameScores": [
    { "gameNo": 1, "leftScore": 25, "rightScore": 20, "winnerSide": "left" },
    { "gameNo": 2, "leftScore": 25, "rightScore": 22, "winnerSide": "left" },
    { "gameNo": 3, "leftScore": 25, "rightScore": 18, "winnerSide": "left" }
  ]
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `winnerSide` | string | 是 | `"left"` 或 `"right"` |
| `leftScore` | int | 是 | 左侧赢得局数 |
| `rightScore` | int | 是 | 右侧赢得局数 |
| `leftGameWins` | int | 是 | 左侧赢得局数（同 leftScore） |
| `rightGameWins` | int | 是 | 右侧赢得局数（同 rightScore） |
| `retiredSide` | string | 否 | 弃权方 `"left"` / `"right"`，正常结束时为 null |
| `gameScores` | array | 否 | 每局详细比分 |

**响应** — 无返回体 (`null`)

> 淘汰赛中，胜者会自动推进到 `nextMatchId` 对应的下一场比赛。

**调用位置**: `useScoreboard.js → syncAndBack()`

**前置条件**: 所有 pending events 已同步成功；比赛已分出胜负（正常或退赛）。

---

#### 16.2.6 重新开始比赛

```
PUT /api/v1/matches/{id}/restart  🔒
```

**响应** — 无返回体 (`null`)

> 重置比赛为初始状态，清除所有比分、事件、阵容配置和主题配置。如果胜者已晋级到下一场，同时清除下一场的晋级者。

**调用位置**: `useScoreboard.js → resetMatch()`

**交互保护**: 按钮带 10 秒倒计时（`resetMatchCountdown`），仅在比赛锁定态（`isLocked`）下可用，防止误触。

---

#### 16.2.7 获取主题配置

```
GET /api/v1/matches/{id}/theme-config  🔓
```

**响应** — `MatchThemeConfigVO`

```json
{
  "matchId": "m1",
  "theme": { "themeBase": "#1a1a2e", "..." },
  "phoneTheme": {
    "themeBase": "#003E50",
    "themeBaseDeep": "#00123A",
    "themeAccent": "#EC822F",
    "themeAccentInk": "#194955",
    "captain": "#2EC6FD",
    "courtSurface": "#194955",
    "rightScoreAccent": "#F49227",
    "dangerAccent": "#F49227",
    "textStrong": "#EEFFE0",
    "surfaceGlass": "#002F00",
    "shadowColor": "#000000",
    "overlayMask": "#07121C",
    "courtSlotAccent": "#F49227",
    "rotationPanelSurface": "#005058"
  },
  "padTheme": {
    "themeBase": "#225F6E",
    "themeBaseDeep": "#143843",
    "themeAccent": "#F4A53A",
    "themeAccentInk": "#194955",
    "captain": "#739C69",
    "courtSurface": "#1E4F2B",
    "rightScoreAccent": "#52C41A",
    "dangerAccent": "#FF7A45",
    "textStrong": "#FFFFFF",
    "surfaceGlass": "#FFFFFF",
    "shadowColor": "#000000",
    "overlayMask": "#07121C",
    "courtSlotAccent": "#008F8D",
    "rotationPanelSurface": "#225F6E"
  }
}
```

> `theme` 为旧版遗留字段，实际使用时取 `phoneTheme` 或 `padTheme`（14 个 CSS 颜色变量）。

**调用位置**: `useScoreboard.js → loadThemeDraftFromServer()`

**加载时机**: 进入记分板时，在 `loadMatch()` 中调用。若服务端无配置则 fallback 到本地 Storage 或硬编码默认值。

---

#### 16.2.8 保存主题配置

```
PUT /api/v1/matches/{id}/theme-config  🔒
```

**请求体**

```json
{
  "device": "pad",
  "theme": {
    "themeBase": "#225F6E",
    "themeBaseDeep": "#143843",
    "themeAccent": "#F4A53A",
    "themeAccentInk": "#194955",
    "captain": "#739C69",
    "courtSurface": "#1E4F2B",
    "rightScoreAccent": "#52C41A",
    "dangerAccent": "#FF7A45",
    "textStrong": "#FFFFFF",
    "surfaceGlass": "#FFFFFF",
    "shadowColor": "#000000",
    "overlayMask": "#07121C",
    "courtSlotAccent": "#008F8D",
    "rotationPanelSurface": "#225F6E"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `device` | string | 是 | `"phone"` 或 `"pad"` |
| `theme` | object | 是 | 14 个 CSS 颜色变量（均为 `#RRGGBB` 格式） |

**响应** — 无返回体 (`null`)

**调用位置**: `useScoreboard.js → saveThemeDraftToServer()`

**触发方式**: 用户在调色板中点击"存后端"按钮。

---

#### 16.2.9 获取比赛记录

```
GET /api/v1/matches/{id}/record  🔓
```

**响应** — `MatchRecordDetailVO`

```json
{
  "matchId": "m1",
  "tournamentId": "329847230984723",
  "tournamentName": "2026 春季赛",
  "roundNum": 2,
  "matchIndex": 1,
  "status": 2,
  "bestOf": 5,
  "gamesToWin": 3,
  "pointsToWin": 25,
  "enableDeuce": true,
  "capPoint": 30,
  "scoreDisplay": "3:1",
  "leftGameWins": 3,
  "rightGameWins": 1,
  "winnerSide": "left",
  "retiredSide": null,
  "left": { "id": "p1", "name": "火箭队", "members": [...] },
  "right": { "id": "p2", "name": "星火队", "members": [...] },
  "gameScores": [
    { "gameNo": 1, "leftScore": 25, "rightScore": 20, "winnerSide": "left" }
  ],
  "rosterSnapshot": {
    "leftMembers": [...],
    "rightMembers": [...]
  },
  "lineupSnapshots": [
    {
      "gameNo": 1,
      "serveSide": "left",
      "left": { "court": [...] },
      "right": { "court": [...] }
    }
  ],
  "events": [
    {
      "eventSeq": 1,
      "eventType": "substitution",
      "eventTypeLabel": "换人",
      "gameNo": 1,
      "leftScore": 12,
      "rightScore": 10,
      "serveSide": "left",
      "summary": "火箭队 #15 换下 #3",
      "detailLines": ["...", "..."],
      "createTime": "2026-06-15T10:30:00"
    }
  ]
}
```

**调用位置**: `record.vue → loadRecord()`

**前端渲染**: 后端返回的 `rosterSnapshot`、`lineupSnapshots`、`events` 等字段由后端预计算为 `reportRender` 结构（含 header、roster、games、signatures、coinTossBlocks、notes），前端直接渲染。

---

### 16.3 赛事创建接口

#### 创建排球赛事

```
POST /api/v1/tournaments  🔒
```

**请求体**

```json
{
  "name": "2026 春季排球赛",
  "location": "体育馆A馆",
  "sportType": 1,
  "tournamentType": 0,
  "knockoutSlots": 8,
  "qualifiersPerGroup": 2,
  "teams": [
    {
      "name": "火箭队",
      "members": [
        { "name": "队员A", "jerseyNumber": 1, "captain": true, "libero": false },
        { "name": "队员B", "jerseyNumber": 5, "captain": false, "libero": true },
        { "name": "队员C", "jerseyNumber": 7, "captain": false, "libero": false }
      ]
    }
  ],
  "rule": {
    "bestOf": 5,
    "gamesToWin": 3
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `name` | string | 是 | 赛事名称 |
| `location` | string | 否 | 比赛地点 |
| `sportType` | int | 是 | 固定为 `1`（排球） |
| `tournamentType` | int | 是 | `0` = 纯淘汰赛，`1` = 小组+淘汰 |
| `knockoutSlots` | int | 否 | 淘汰赛名额（4/8/16），仅 tournamentType=1 时有效 |
| `qualifiersPerGroup` | int | 否 | 每组出线名额（1/2），仅 tournamentType=1 时有效 |
| `teams` | array | 是 | 队伍列表（至少 2 支，每队至少 6 人） |
| `members[].jerseyNumber` | int | 是 | 球衣号码（队内唯一） |
| `members[].captain` | bool | 是 | 是否为队长（每队恰好 1 名） |
| `members[].libero` | bool | 否 | 是否为自由人 |
| `rule.bestOf` | int | 是 | `3` = 三局两胜，`5` = 五局三胜 |
| `rule.gamesToWin` | int | 是 | 赢得局数阈值 = `floor(bestOf/2) + 1` |

**响应**

```json
{
  "tournamentId": "329847230984723"
}
```

**调用位置**: `pages/create/volleyball.vue → createTournament()`

**前端校验**（发起请求前）: 赛事名称非空、≥2 支队伍、每队 ≥6 人、球衣号码有效且队内不重复、恰好 1 名队长。

---

### 16.4 接口调用时序

```
赛事创建:
  POST /tournaments  →  返回 tournamentId

进入轮次填写:
  GET  /tournaments/{id}/bracket          → 获取赛事信息 + 队伍成员
  GET  /matches/{id}/lineup-config        → 获取已有阵容配置（如有）
  PUT  /matches/{id}/lineup-config        → 保存用户填写的阵容
                                            → 跳转记分板

记分板运行中:
  GET  /tournaments/{id}/bracket          → 加载比赛（含队伍信息）
  GET  /matches/{id}/theme-config         → 加载配色主题
  PUT  /matches/{id}/events               → 批量同步比赛事件（800ms防抖）
  PUT  /matches/{id}/theme-config         → (可选) 保存调色结果

比赛结束:
  PUT  /matches/{id}/events               → 强制 flush 所有 pending 事件
  PUT  /matches/{id}/finish               → 提交最终比分，推进晋级

重新开始:
  PUT  /matches/{id}/restart              → 清除所有数据 → 跳转 lineup

查看记录:
  GET  /matches/{id}/record               → 获取完整比赛报告
```

---

### 16.5 前端请求封装

```js
// src/utils/request.js
// 自动注入 Authorization: Bearer <token>
// 自动校验 code === 0，失败 toast 提示
// options.silent: true → 静默失败（用于事件同步等后台操作）
// 成功时直接 resolve response.data.data

// 使用示例:
const data = await request('/api/v1/tournaments/' + id + '/bracket', { method: 'GET' })
await request('/api/v1/matches/' + id + '/events', {
  method: 'PUT',
  data: { events },
  silent: true  // 静默模式
})
```

### 16.6 事件同步完整字段说明

记分板在整个比赛过程中产生的所有事件，最终通过 `PUT /matches/{id}/events` 批量提交。以下是每种事件类型在 `payloadJson` 中的完整 JSON 结构：

| eventType | payloadJson 内容 | 产生时机 |
|-----------|-----------------|----------|
| `roster_snapshot` | `{ leftMembers: [{id,name,jerseyNumber,captain,libero}], rightMembers: [...] }` | 比赛首次加载时（ensureBootstrapEvents） |
| `lineup_snapshot` | `{ left: {court, middlePairIndexes, libero1Id, libero2Id}, right: {...}, serveSide }` | 每局开始时 |
| `substitution` | `{ side: "left"/"right", outMemberId, inMemberId }` | 用户完成换人操作 |
| `timeout` | `{ side: "left"/"right" }` | 用户点击暂停 |
| `captain_change` | `{ side, captainMemberId, originalCaptainMemberId, source: "auto"/"manual" }` | 队长自动/手动变更 |
| `side_switch` | `{ reason: "deciding_game_mid_switch"/"between_games"/"manual", screenLeftParticipantSide }` | 换边操作 |

> **参与者侧 vs 屏幕侧**: 所有事件中的 `side` 字段均使用**参与者侧**（participant side），即 `left` = 原始主队、`right` = 原始客队，与屏幕显示位置无关。`appendMatchEvent()` 内部通过 `getParticipantSideByScreenSide()` 完成转换。

---

## 附录 A: Phone vs Pad 视觉对比

| 维度 | Phone (ScoreboardPhone) | Pad (ScoreboardPad) |
|------|------------------------|---------------------|
| 默认强调色 | `#EC822F` (暖橙) | `#F4A53A` (琥珀) |
| 默认队长色 | `#2EC6FD` (青蓝) | `#739C69` (绿) |
| 右侧比分边框 | `#F49227` | `#52C41A` (绿) |
| 球场底色 | 无（透明面板） | `#1E4F2B` (深绿) |
| 球场线条 | 无 | 白色边框 + 进攻线伪元素 |
| 面板阴影 | 无 | `0 14px 34px` |
| 编队面板布局 | 纵向列表 | 横向卡片 |
| 调色面板布局 | `max-width: 100vw-24px` 全宽 | `max-width: 32vw` 限制宽度 |

## 附录 B: 文件行数统计

| 文件 | 行数 | 主要职责 |
|------|------|----------|
| `composables/useScoreboard.js` | 2,213 | 核心业务逻辑 |
| `lineup.vue` | 1,658 | 轮次填写 + 阵容编辑器 |
| `components/ScoreboardPad.vue` | 1,383 | Pad 端 UI |
| `components/ScoreboardPhone.vue` | 1,258 | Phone 端 UI |
| `record.vue` | 868 | 比赛记录 + PDF 导出 |
| `match-state.js` | 352 | 数据模型 + 持久化 |
| `create/volleyball.vue` | 729 | 赛事创建 |
| `scoreboard.vue` | 73 | 入口路由层 |
| **合计** | **~8,534** | |
