# 排球状态机混沌测试与 Agent 接手指南

> **适用对象**：后续接手本仓库的 AI Agent（无论是夜间跑批、白天复现还是自动修复 PR 的模型）。  
> **核心目标**：使接手 Agent 无需翻找代码或浪费大量 Token 盲目猜测，在 1 分钟内建立精准上下文，直接执行排球比赛混沌模拟测试、解析异常报告、定位状态机 Bug 并出具修复代码。

---

## 1. 架构速览与避坑警示（必读）

1. **排球业务逻辑在前端，不在后端**：
   - 核心规则状态机实现于：[`frontend/src/pages/volleyball/composables/useScoreboard.js`](../frontend/src/pages/volleyball/composables/useScoreboard.js) 与 [`frontend/src/pages/volleyball/match-state.js`](../frontend/src/pages/volleyball/match-state.js)。
   - 包括：1~6 号位顺时针轮转、自由人自动原进原出（后排上/前排下）、替补换人名额、队长离场选举、决胜局 8 分换边镜像、40 步历史栈撤销回滚等。
   - **⚠️ 严禁去测后端 HTTP 接口或搞微信小程序 UI 自动化**：后端只负责将事件流水存入 MySQL，不校验站位规则；微信小程序 UI 极其脆弱缓慢。本项目已搭建纯无头（Headless）状态机仿真体系，单场比赛仅耗时数毫秒。

2. **核心工具与文件位置**：
   - **仿真核心库**：`frontend/src/pages/volleyball/scoreboard-fuzzer.js`
   - **环境垫片/Mock**：`frontend/src/pages/volleyball/fuzzer-env.js`
   - **Vitest 集成用例**：`frontend/src/pages/volleyball/scoreboard-fuzzer.test.js`
   - **命令行执行脚本**：`frontend/scripts/run-fuzz.mjs`
   - **报告输出目录**：`outputs/fuzz-volleyball/`（包含 `fuzz-summary.json` 与 `fuzz-anomalies.json`）

---

## 2. 常用操作命令

所有命令均在项目根目录或 `frontend/` 目录下执行：

```powershell
# 1. 快速运行 10 场冒烟测试（约 3~5 秒）
npm --prefix frontend run fuzz -- --matches 10

# 2. 运行 100 场中规模混沌仿真（约 30 秒）
npm --prefix frontend run fuzz -- --matches 100

# 3. 运行 1000 场大规模夜间仿真（约 3 分钟）
npm --prefix frontend run fuzz -- --matches 1000

# 4. 精准复现某一个已知发生异常的比赛（例如 seed 为 42）
npm --prefix frontend run fuzz -- --matches 1 --seed 42

# 5. 通过 Vitest 框架执行测试套件
npm --prefix frontend test -- src/pages/volleyball/scoreboard-fuzzer.test.js
```

---

## 3. 异常产物结构解析

当测试器运行完毕后，会自动在 `outputs/fuzz-volleyball/` 生成两份关键文件：

### 1) `fuzz-summary.json`（总体统计与异常索引）
```json
{
  "baseSeed": 1001,
  "matchCount": 1000,
  "stats": {
    "totalRallies": 128400,
    "totalSubstitutions": 24300,
    "totalUndos": 18200,
    "criticalMatches": 12,
    "suspiciousMatches": 5,
    "cleanMatches": 983
  },
  "criticalSummary": [
    { "matchId": "match_fuzz_1_10974", "seed": 10974, "anomalies": [...] }
  ]
}
```

### 2) `fuzz-anomalies.json`（异常详单与复现动作时序）
包含了所有断言违规场的完整回放链路：
- `seed`: 随机种子（用于 100% 幂等复现）
- `rules`: 赛制参数（bestOf, pointsToWin, capPoint 等）
- `actionHistory`: **从第 1 球到报错球的全部动作流水时序**（如 `SCORE`, `SUBSTITUTION`, `UNDO`, `TIMEOUT`, `CONFIRM_SIDE_SWITCH` 及当时的入参与槽位）
- `anomalies`: 具体违反的业务不变式信息（如 `DUPLICATE_ON_COURT_PLAYER`, `LIBERO_IN_FRONT_ROW` 等）
- `stateSnapshot`: 报错时刻的状态快照（场上球员站位、自由人 runtime、比分、发球方等）

---

## 4. 接手 Agent 的标准作业程序 (SOP)

如果你是一个刚被唤醒接手此任务的 Agent，请严格按照以下 5 步工作流操作：

```
[第 1 步: 读取报告] ──> [第 2 步: 单场复现] ──> [第 3 步: 源码溯源] ──> [第 4 步: 补全单测] ──> [第 5 步: 修复与验证]
```

### 步骤 1：读取并归纳异常报告
- 读取 `outputs/fuzz-volleyball/fuzz-anomalies.json` 中的 `critical` 列表。
- 提取代表性异常类型（例如：重复球员、自由人位置违规、比分超上限等）。

### 步骤 2：使用 Seed 单场高保真复现
在终端运行单场指定 seed 命令，观察报错输出：
```powershell
npm --prefix frontend run fuzz -- --matches 1 --seed <ANOMALY_SEED>
```

### 步骤 3：查看 Action 时序，反查源码
- 查看 `actionHistory` 在出错前发生的操作。重点观察：是否有换人（`SUBSTITUTION`）、连续撤销（`UNDO`）、或决胜局换边（`CONFIRM_SIDE_SWITCH`）。
- 结合 [`useScoreboard.js`](../frontend/src/pages/volleyball/composables/useScoreboard.js) 中对应函数定位逻辑漏洞（例如 `selectBench`, `handleCourtSlot`, `rotateCourt`, `settleTeamLibero`, `undo`）。

### 步骤 4：编写最小单元复现测试
- 在 `frontend/src/pages/volleyball/` 下编写一个聚焦该问题的简单回归单测（参考 `match-state.test.js`），确保在未修复时该单测失败（Red）。

### 步骤 5：修改代码并通过验证
- 修改 `useScoreboard.js` 或 `match-state.js`。
- 重新运行回归单测及全量单测：
  ```powershell
  npm --prefix frontend test
  ```
- 确认全绿（Green），生成清晰的 commit 说明。

---

## 5. 经典实战案例参考（双副攻克隆漏洞）

在测试器初次运行中曾抓获一个典型案例，可作为 Agent 排查思维的范本：

* **现象**：第 48 步得分时，报 `DUPLICATE_ON_COURT_PLAYER`，左队场上出现了 2 个 `A_5` 球员。
* **动作轨迹溯源**：
  1. 首发阵容中，副攻 `A_5` 绑定了自由人 `A_8`，自由人自动替换 `A_5` 上场，`A_5` 在场下待命。
  2. 第 41 步执行换人：裁判点击替补席把 `A_5` 换到了 1 号槽位（3号位）。因为 `selectBench` 仅判断了 `!isOnCourt(memberId)`，由于 `A_5` 此时未在场上，系统误认为他是普通可用替补。
  3. 第 48 步执行得分换发球：轮转触发，自由人顺时针转到了前排 4 号位，触发自由人“原进原出”，系统又自动把绑定的 `A_5` 放回了原副攻槽位！
* **修复要点**：在替补名单或 `selectBench` 中，不仅要排除当前在场球员，还必须排除**“当前正被自由人替代在场下的副攻球员”**（该队员正处于自由人激活链路中，不能作为常规替补换上其他位置）。

---

## 6. 用户快捷派发 Prompt 模板（可直接复制使用）

### 场景 A：让新 Agent 自动跑测试并分析报告
> “请阅读 `docs/排球状态机混沌测试与Agent接手指南.md`。使用 `npm --prefix frontend run fuzz -- --matches 500` 运行 500 场排球状态机混沌测试，分析 `outputs/fuzz-volleyball/fuzz-anomalies.json` 中报告的所有异常，指出最严重的 1~2 个状态机漏洞的原因。”

### 场景 B：针对昨晚跑出的异常直接出修复代码
> “请阅读 `docs/排球状态机混沌测试与Agent接手指南.md`。昨晚已经跑完了 1000 场混沌仿真，请读取 `outputs/fuzz-volleyball/fuzz-anomalies.json` 中的异常轨迹，分析为什么会出现断言失败，并在 `frontend/src/pages/volleyball/composables/useScoreboard.js` 中实施修复，最后运行测试验证修复效果。”
