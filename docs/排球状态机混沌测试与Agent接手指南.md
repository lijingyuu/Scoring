# 排球状态机混沌测试与 Agent 接手指南

> **适用对象**：后续接手本仓库的 AI Agent（无论是夜间跑批、白天复现还是自动修复 PR 的模型）。  
> **核心目标**：使接手 Agent 无需翻找代码或浪费大量 Token 盲目猜测，在 1 分钟内建立精准上下文，直接执行排球比赛混沌模拟测试、解析异常报告、定位状态机 Bug 并出具修复代码。

---

## 1. 架构速览与避坑警示（必读）

1. **排球业务逻辑在前端，不在后端**：
   - 核心规则状态机实现于：[`frontend/src/pages/volleyball/composables/useScoreboard.js`](../frontend/src/pages/volleyball/composables/useScoreboard.js) 与 [`frontend/src/pages/volleyball/match-state.js`](../frontend/src/pages/volleyball/match-state.js)。
   - 包括：1~6 号位顺时针轮转、自由人自动原进原出（后排上/前排下）、替补换人名额、队长离场选举、决胜局 8 分换边镜像、40 步历史栈撤销回滚等。
   - **⚠️ 严禁去测后端 HTTP 接口或搞微信小程序 UI 自动化**：后端只负责将事件流水存入 MySQL，不校验站位规则；微信小程序 UI 极其脆弱缓慢。本项目已搭建纯无头（Headless）状态机仿真体系，本机实测约 25~40 秒/场（瓶颈在全量状态序列化与存取回环重入，非毫秒级；测试基建的内存/超时修复已落地，过程细节可从 git 历史找回）。

2. **核心工具与文件位置**：
   - **仿真核心库**：`frontend/src/pages/volleyball/scoreboard-fuzzer.js`
   - **环境垫片/Mock**：`frontend/src/pages/volleyball/fuzzer-env.js`
   - **Vitest 集成用例**：`frontend/src/pages/volleyball/scoreboard-fuzzer.test.js`
   - **命令行执行脚本**：`frontend/scripts/run-fuzz.mjs`（单次）
   - **分块编排器**：`frontend/scripts/run-fuzz-batch.mjs`（百场级以上批量仿真必用，支持断点续跑）
   - **报告输出目录**：`outputs/fuzz-volleyball/`（包含 `fuzz-summary.json` 与 `fuzz-anomalies.json`，批量产物另见 `run-manifest.json` 与 `chunks/<baseSeed>/`）

---

## 2. 常用操作命令

所有命令均在项目根目录或 `frontend/` 目录下执行：

```powershell
# 1. 单场冒烟 / 指定 seed 复现（vitest 启动约 30 秒 + 仿真约 30 秒/场）
npm --prefix frontend run fuzz -- --matches 1 --seed 42

# 2. 少量冒烟（10 场以内可在单 vitest 进程内完成，约 0.5~5 分钟）
npm --prefix frontend run fuzz -- --matches 10

# 3. 大规模批量仿真：分块编排器（百场级以上必须用它，勿用 --matches 1000 单进程硬跑）
#    - 每块独立 vitest 进程（4GB 堆上限 / 块级超时默认 90 分钟），块结束整体释放内存
#    - 断点续跑：中途中断后重复执行同一命令，已完成块自动跳过
#    - 产物按 baseSeed 归档：chunks/<baseSeed>/chunk-XXX-*.json + 合并视图 fuzz-summary.json / fuzz-anomalies.json
cd frontend
node scripts/run-fuzz-batch.mjs --total 600 --chunk 100 --base-seed 20260916
#    进度查看：outputs/fuzz-volleyball/run-manifest.json

# 4. 通过 Vitest 框架执行测试套件
npm --prefix frontend test -- src/pages/volleyball/scoreboard-fuzzer.test.js
```

> ⚠️ **内存陷阱**：单 vitest 进程内每场仿真实测保留约 11MB+（历史遗留浮动垃圾），
> `--matches 1000` 单进程会堆内存超限崩溃。批量仿真请一律走 `run-fuzz-batch.mjs` 分块执行。

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

### 5.1 案例二：换边 + 撤销导致花名册错位（2026-09-17 批次）

* **现象**：600 场批次前 200 场出现 14 场 CRITICAL，全部发生在决胜局换边（`CONFIRM_SIDE_SWITCH`）之后，表现为场上重复球员（自由人克隆）或自由人被换入前排。
* **溯源要点**：`swapSides()` 会原地交换 `leftTeam`/`rightTeam` 花名册引用，但花名册不在历史快照内 → `undo()` 跨越换边边界后名册与球场错位；且 `getPlayerState` 对名册外球员兜底放行为 `BENCH_FREE`，异队/自由人 ID 可被当作普通替补换入。
* **修复**：`undo()` 检测屏侧翻转时同步换回花名册（`useScoreboard.js`）；名册外球员不可选；fuzzer 换人选择器与审计器按 `screenLeftParticipantSide` 解析名册。
* **回归单测**：`side-switch-undo.test.js`（4 用例，含红绿验证）；问题溯源的过程记录已归档至 git 历史。

---

## 6. 用户快捷派发 Prompt 模板（可直接复制使用）

### 场景 A：让新 Agent 自动跑测试并分析报告
> “请阅读 `docs/排球状态机混沌测试与Agent接手指南.md`。使用 `npm --prefix frontend run fuzz -- --matches 500` 运行 500 场排球状态机混沌测试，分析 `outputs/fuzz-volleyball/fuzz-anomalies.json` 中报告的所有异常，指出最严重的 1~2 个状态机漏洞的原因。”

### 场景 B：针对昨晚跑出的异常直接出修复代码
> “请阅读 `docs/排球状态机混沌测试与Agent接手指南.md`。昨晚已经跑完了 1000 场混沌仿真，请读取 `outputs/fuzz-volleyball/fuzz-anomalies.json` 中的异常轨迹，分析为什么会出现断言失败，并在 `frontend/src/pages/volleyball/composables/useScoreboard.js` 中实施修复，最后运行测试验证修复效果。”
