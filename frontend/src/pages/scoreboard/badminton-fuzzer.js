import { effectScope } from 'vue'
import { useScoreboardState } from './use-scoreboard-state.js'
import { setupFuzzerEnvironment } from './fuzzer-env.js'

/**
 * 羽毛球无头混沌 fuzzer —— 驱动【真实产品代码】
 *
 * 2026-09 P0-1 返工：删除原 createBadmintonScoreboard 手工副本（副本会随真实
 * 页面演进而静默腐化，夜跑全绿对产品零证明力），改为直接 import
 * pages/scoreboard/use-scoreboard-state.js（从 index.vue 提取的真实状态机，
 * 单一事实源）。storage 依赖经 ./fuzzer-env.js 垫片用内存实现 mock。
 *
 * 真实模块的 addScore/adjustScore 带侧别守卫并返回 boolean
 * （true=接受 / false=拒绝且无状态变更），全部审计断言基于该契约。
 */

/**
 * 确定性伪随机数发生器 (Mulberry32)
 * 只要 seed 一致，产生的所有随机数和动作流 100% 幂等可复现。
 */
export function createPRNG(initialSeed = 123456789) {
  let s = (initialSeed ^ 0xdeadbeef) >>> 0
  return {
    seed: initialSeed,
    next() {
      s = (s + 0x6d2b79f5) | 0
      let t = Math.imul(s ^ (s >>> 15), 1 | s)
      t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
      return ((t ^ (t >>> 14)) >>> 0) / 4294967296
    },
    randInt(min, max) {
      return Math.floor(this.next() * (max - min + 1)) + min
    },
    choice(arr) {
      if (!arr || !arr.length) return null
      return arr[this.randInt(0, arr.length - 1)]
    },
    randBool(p = 0.5) {
      return this.next() < p
    },
  }
}

/**
 * 随机生成羽毛球比赛场景（包含个人赛单双打、规则配置）
 */
export function generateBadmintonScenario(prng, matchIndex = 1) {
  const isDoubles = prng.randBool(0.4) // 40% 双打
  const bestOf = prng.choice([1, 3, 5])
  const gamesToWin = Math.floor(bestOf / 2) + 1
  const pointsToWin = prng.choice([11, 15, 21])
  const enableDeuce = prng.randBool(0.85) // 85% 开启 Deuce
  const capPoint = enableDeuce ? prng.choice([pointsToWin + 5, 30, 99]) : pointsToWin

  const leftTeamName = isDoubles
    ? `选手A${matchIndex}_1 / 选手A${matchIndex}_2`
    : `选手A_${matchIndex}`
  const rightTeamName = isDoubles
    ? `选手B${matchIndex}_1 / 选手B${matchIndex}_2`
    : `选手B_${matchIndex}`

  const initialServeSide = prng.choice(['left', 'right'])

  return {
    matchId: `badminton_fuzz_${matchIndex}_${prng.seed}`,
    tournamentId: `tournament_${matchIndex}`,
    divisionId: `division_${matchIndex}`,
    isDoubles,
    rules: {
      bestOf,
      gamesToWin,
      pointsToWin,
      enableDeuce,
      capPoint,
    },
    leftTeam: leftTeamName,
    rightTeam: rightTeamName,
    initialServeSide,
  }
}

/**
 * 按场景装配【真实】记分板状态机。
 * 初始化路径对齐真实页面 onLoad 的无缓存分支：applyRules 归一化规则 + 注入队名，
 * 发球方作为初始条件注入（真实页面固定 'left'，fuzzer 随机以覆盖双侧开局）。
 * 必须在调用前经 setupFuzzerEnvironment() 安装 globalThis.uni 垫片。
 */
function initializeScoreboardFromScenario(scoreboard, scenario) {
  scoreboard.matchId.value = scenario.matchId || ''
  scoreboard.applyRules(scenario.rules || {})
  scoreboard.leftTeam.value = scenario.leftTeam || '左队'
  scoreboard.rightTeam.value = scenario.rightTeam || '右队'
  scoreboard.serveSide.value = scenario.initialServeSide === 'right' ? 'right' : 'left'
  scoreboard.matchStartTime.value = Date.now()
}

/**
 * 基于真实 useScoreboardState 的场景装配器（测试用直入口）。
 * 每次调用重置无头 uni 垫片（全新内存 storage），保证用例间互不串扰。
 */
export function createBadmintonScoreboard(scenario) {
  setupFuzzerEnvironment()
  const scoreboard = useScoreboardState()
  initializeScoreboardFromScenario(scoreboard, scenario)
  return scoreboard
}

/**
 * 提取"受保护状态"的逐字段快照（JSON 序列化），用于断言拒绝性操作零漂移。
 * 刻意排除 4 个弹窗待决标志与 isGodMode：
 * - addScore 在 needsFinalGameSideSwitch（computed 待决、pending 尚未锁定）时的
 *   合法行为是把 pending 锁定为 true（弹窗显式化），这不是比分污染；
 * - 探针自身会临时切换 isGodMode。
 * 比分、局数、局号、发球权、队伍、完局记录、历史栈深度等全部纳入。
 */
function snapshotGuardedState(scoreboard) {
  return JSON.stringify({
    leftTeam: scoreboard.leftTeam.value,
    rightTeam: scoreboard.rightTeam.value,
    leftScore: scoreboard.leftScore.value,
    rightScore: scoreboard.rightScore.value,
    leftGameWins: scoreboard.leftGameWins.value,
    rightGameWins: scoreboard.rightGameWins.value,
    currentGameNo: scoreboard.currentGameNo.value,
    gameScores: scoreboard.gameScores.value,
    serveSide: scoreboard.serveSide.value,
    sidesSwapped: scoreboard.sidesSwapped.value,
    retiredSide: scoreboard.retiredSide.value,
    matchEnded: scoreboard.matchEnded.value,
    matchStartTime: scoreboard.matchStartTime.value,
    matchDuration: scoreboard.matchDuration.value,
    winnerName: scoreboard.winnerName.value,
    matchRules: scoreboard.matchRules.value,
    historyStackLength: scoreboard.historyStack.value.length,
  })
}

/**
 * 核心羽毛球业务不变式断言与异常审计器（审计对象为真实状态机实例）
 */
export function auditBadmintonInvariants(scoreboard, scenario, context = {}) {
  const anomalies = []
  const rules = scoreboard.matchRules.value

  function check(cond, type, message) {
    if (!cond) {
      anomalies.push({ severity: 'CRITICAL', type, message })
    }
  }

  // 1. 小分与局号非负
  check(
    scoreboard.leftScore.value >= 0 && scoreboard.rightScore.value >= 0,
    'SCORE_NEGATIVE',
    `小分出现负数: ${scoreboard.leftScore.value}:${scoreboard.rightScore.value}`
  )
  check(
    scoreboard.leftGameWins.value >= 0 && scoreboard.rightGameWins.value >= 0,
    'GAME_WINS_NEGATIVE',
    `胜局数出现负数: ${scoreboard.leftGameWins.value}:${scoreboard.rightGameWins.value}`
  )
  check(
    scoreboard.currentGameNo.value >= 1,
    'CURRENT_GAME_NO_INVALID',
    `当前局号必须>=1: ${scoreboard.currentGameNo.value}`
  )
  check(
    scoreboard.serveSide.value === 'left' || scoreboard.serveSide.value === 'right',
    'SERVE_SIDE_INVALID',
    `发球方非法: ${scoreboard.serveSide.value}`
  )

  // 2. 封顶分约束：小分不能超过封顶分
  if (rules.capPoint && rules.capPoint > 0) {
    check(
      scoreboard.leftScore.value <= rules.capPoint,
      'SCORE_OVER_CAP',
      `左队小分超过封顶分 ${rules.capPoint}: ${scoreboard.leftScore.value}`
    )
    check(
      scoreboard.rightScore.value <= rules.capPoint,
      'SCORE_OVER_CAP',
      `右队小分超过封顶分 ${rules.capPoint}: ${scoreboard.rightScore.value}`
    )
  }

  // 3. 终局状态下的单调性与胜负一致性
  if (scoreboard.matchEnded.value && !scoreboard.retiredSide.value) {
    const maxWins = Math.max(scoreboard.leftGameWins.value, scoreboard.rightGameWins.value)
    check(
      maxWins === rules.gamesToWin,
      'MATCH_END_GAMES_TO_WIN_MISMATCH',
      `终局时胜者胜局必须等于 gamesToWin(${rules.gamesToWin})，实测: ${scoreboard.leftGameWins.value}:${scoreboard.rightGameWins.value}`
    )
  }

  // 4. 发球权强一致：若上一个动作是常规得分，发球方必须属于得分方
  if (context.lastAction === 'SCORE' && context.lastScoredSide) {
    check(
      scoreboard.serveSide.value === context.lastScoredSide,
      'SERVE_OWNERSHIP_MISMATCH',
      `得分方 ${context.lastScoredSide} 未获得发球权，当前为 ${scoreboard.serveSide.value}`
    )
  }

  // 5. 决胜局换边提示锁定（P0-1 激活：SCORE/GOD_ADJUST 到达门槛时由动作循环置位）
  if (context.wasDecidingGameSideSwitchThresholdHit && !scoreboard.finalGameSideSwitchHandled.value && !scoreboard.isLocked.value) {
    check(
      scoreboard.isFinalGameSideSwitchPromptActive.value,
      'DECIDING_GAME_SIDE_SWITCH_NOT_PROMPTED',
      `决胜局达到换边门槛但未触发换边弹窗提示`
    )
  }

  // 6. 弹窗锁定保护（P0-1 激活：弹窗待决时探针置位，被拒且零漂移才算通过）
  if (context.lastActionAttemptedWhilePromptActive) {
    check(
      context.actionRejected,
      'PROMPT_ACTIVE_MUTATION_ACCEPTED',
      `弹窗处于待决状态时，非处理动作未被正确拦截`
    )
  }

  // 7. 终局不可变性（P0-1 激活：终局后探针置位，被拒且零漂移才算通过）
  if (context.lastActionAttemptedWhileEnded) {
    check(
      context.actionRejected,
      'TERMINAL_STATE_MUTATION_ACCEPTED',
      `比赛已结束后，加分或换边动作未被拦截`
    )
  }

  // 8. 撤销历史栈健康度
  for (let i = 0; i < scoreboard.historyStack.value.length; i++) {
    const snap = scoreboard.historyStack.value[i]
    if (Number.isNaN(snap.leftScore) || Number.isNaN(snap.rightScore)) {
      anomalies.push({
        severity: 'CRITICAL',
        type: 'HISTORY_STACK_NAN',
        message: `历史栈第 ${i} 层快照存在 NaN 比分`,
      })
    }
  }

  // 9. 已结束各局的胜利条件复核：gameScores 中每条完局记录都必须满足本场规则的胜利条件。
  // 断言与内核 checkWinCondition（finishGame 的唯一守卫，addScore 与 handleFinalGameSideSwitch
  // 均在非上帝模式下先通过它才完局）严格一致，用于捕捉终局判定逻辑漂移（例如 20 分即胜局）。
  for (let i = 0; i < scoreboard.gameScores.value.length; i++) {
    const game = scoreboard.gameScores.value[i]
    if (!game || typeof game !== 'object') continue
    const gameLeftScore = Number(game.leftScore || 0)
    const gameRightScore = Number(game.rightScore || 0)
    const winnerIsLeft = game.winnerSide !== 'right'
    const winScore = winnerIsLeft ? gameLeftScore : gameRightScore
    const loseScore = winnerIsLeft ? gameRightScore : gameLeftScore
    const pointsToWin = Number(rules.pointsToWin || 21)
    const enableDeuce = rules.enableDeuce !== false
    const capPoint = Number(rules.capPoint || 0)

    // 胜方必须满足与内核 checkWinCondition 完全相同的胜利条件
    const winnerMetWinCondition =
      (capPoint > 0 && winScore >= capPoint) ||
      (winScore >= pointsToWin && (!enableDeuce || winScore - loseScore >= 2))
    check(
      winnerMetWinCondition,
      'GAME_SCORE_WIN_CONDITION_VIOLATED',
      `第 ${game.gameNo ?? i + 1} 局完局记录不满足胜利条件(pointsToWin=${pointsToWin}, enableDeuce=${enableDeuce}, capPoint=${capPoint}): ${gameLeftScore}:${gameRightScore}, 胜方=${winnerIsLeft ? 'left' : 'right'}(${winScore}分)`
    )

    // 败方保守断言：不可能高于胜方，也不可能超过封顶分。
    // deuce 局胜方完局需净胜 >=2，败方必然更小；无 deuce 局先到 pointsToWin 即完局，败方到不了该分；
    // 唯一例外是上帝模式 adjustScore 抬分可能造出 cap:cap 平分完局，故允许相等（宁弱勿误报）。
    const loserOverLimit = loseScore > winScore || (capPoint > 0 && loseScore > capPoint)
    check(
      !loserOverLimit,
      'GAME_SCORE_WIN_CONDITION_VIOLATED',
      `第 ${game.gameNo ?? i + 1} 局完局记录败方分数异常(pointsToWin=${pointsToWin}, enableDeuce=${enableDeuce}, capPoint=${capPoint}): ${gameLeftScore}:${gameRightScore}, 败方=${winnerIsLeft ? 'right' : 'left'}(${loseScore}分) 高于胜方(${winScore}分)或超过封顶`
    )
  }

  // 10. 换边坐标还原一致性（P0-1 新增）：真实调用 toOriginalSide/toOriginalGame，
  //     按 sidesSwapped 与场景原始队名互证还原坐标不错位。
  //     依据不变式：所有换边路径（switchSides / confirmGameEnd 自动换边 / 决胜局换边）
  //     都在交换队伍的同时翻转 sidesSwapped，撤销/恢复成对还原，
  //     因此「还原坐标侧的当前队伍 === 场景原始队伍」必须恒成立。
  if (typeof scenario.leftTeam === 'string' && typeof scenario.rightTeam === 'string') {
    const leftSideOriginal = scoreboard.toOriginalSide('left')
    const rightSideOriginal = scoreboard.toOriginalSide('right')
    const teamAt = (side) => (side === 'left' ? scoreboard.leftTeam.value : scoreboard.rightTeam.value)
    check(
      (leftSideOriginal === 'left' || leftSideOriginal === 'right')
        && teamAt(leftSideOriginal) === scenario.leftTeam
        && teamAt(rightSideOriginal) === scenario.rightTeam,
      'ORIGINAL_COORDINATE_MISMATCH',
      `sidesSwapped=${scoreboard.sidesSwapped.value} 还原坐标后左队=${teamAt(leftSideOriginal)}(期望 ${scenario.leftTeam}), 右队=${teamAt(rightSideOriginal)}(期望 ${scenario.rightTeam})`
    )
    for (let i = 0; i < scoreboard.gameScores.value.length; i++) {
      const game = scoreboard.gameScores.value[i]
      if (!game || typeof game !== 'object') continue
      const restored = scoreboard.toOriginalGame(game)
      check(
        restored.gameNo === game.gameNo
          && Number(restored.leftScore || 0) + Number(restored.rightScore || 0) === Number(game.leftScore || 0) + Number(game.rightScore || 0)
          && restored.winnerSide === scoreboard.toOriginalSide(game.winnerSide),
        'ORIGINAL_GAME_COORDINATE_MISMATCH',
        `第 ${game.gameNo ?? i + 1} 局记录还原坐标错位: 原始 ${game.leftScore}:${game.rightScore} 胜方=${game.winnerSide} -> 还原 ${restored.leftScore}:${restored.rightScore} 胜方=${restored.winnerSide}`
      )
    }
  }

  return anomalies
}

/**
 * 单场羽毛球无头混沌模拟执行器（驱动真实 use-scoreboard-state）
 */
export async function simulateBadmintonMatch(scenario, prng, options = {}) {
  // 每场安装全新的内存 storage 垫片（单场隔离；真实模块运行时读取 globalThis.uni）
  setupFuzzerEnvironment()

  // 用独立 effectScope 收集本场全部 computed 副作用，赛后整体释放，
  // 避免无组件挂载的响应式副作用跨场次累积（对齐排球 fuzzer 的教训）
  const matchScope = effectScope()
  const scoreboard = matchScope.run(() => useScoreboardState())
  initializeScoreboardFromScenario(scoreboard, scenario)

  const actionHistory = []
  const recordedAnomalies = []
  let step = 0
  const maxSteps = options.maxSteps || 1000

  const matchStats = {
    reloads: 0,
    sideSwitchConfirmed: 0,
    sideSwitchKept: 0,
    manualSideSwitches: 0,
    deepUndos: 0,
    hostileProbes: 0,
    hostileRejected: 0,
    promptActiveProbes: 0,
    terminalProbes: 0,
    capHits: 0,
    retired: false,
    decidingGameReached: false,
    gamesPlayed: 0,
  }

  // 决胜局换边门槛到达标记：供审计断言 5（DECIDING_GAME_SIDE_SWITCH_NOT_PROMPTED）
  function markDecidingThresholdIfHit(context) {
    const threshold = Number(scoreboard.finalGameSideSwitchThreshold.value || 0)
    if (
      threshold > 0
      && Number(scoreboard.currentGameNo.value) === Number(scoreboard.matchRules.value.bestOf)
      && Math.max(Number(scoreboard.leftScore.value || 0), Number(scoreboard.rightScore.value || 0)) >= threshold
    ) {
      context.wasDecidingGameSideSwitchThresholdHit = true
    }
  }

  while (!scoreboard.matchEnded.value && step < maxSteps) {
    step++
    const context = {
      step,
      lastAction: '',
      lastScoredSide: '',
      wasDecidingGameSideSwitchThresholdHit: false,
      lastActionAttemptedWhilePromptActive: false,
      lastActionAttemptedWhileEnded: false,
      actionRejected: false,
    }

    // 检查是否达到决胜局
    if (scoreboard.currentGameNo.value === scoreboard.matchRules.value.bestOf) {
      matchStats.decidingGameReached = true
    }

    // 0) 弹窗锁定探针（P0-1 激活断言 6）：弹窗待决时尝试加分，
    //    真实模块的拒绝方式是"返回 false 且无操作"，故须同时断言零漂移。
    //    注：needsFinalGameSideSwitch（computed 待决未锁定）时 addScore 会把
    //    pending 锁定为 true（弹窗显式化），属合法行为，不计入受保护快照。
    if (scoreboard.isPromptActive.value && prng.randBool(0.35)) {
      matchStats.promptActiveProbes++
      const probeSide = prng.choice(['left', 'right'])
      const stateBeforeProbe = snapshotGuardedState(scoreboard)
      const accepted = scoreboard.addScore(probeSide)
      const driftFree = snapshotGuardedState(scoreboard) === stateBeforeProbe
      actionHistory.push({ step, action: 'PROMPT_ACTIVE_PROBE', side: probeSide, accepted, driftFree })

      context.lastAction = 'PROMPT_ACTIVE_PROBE'
      context.lastActionAttemptedWhilePromptActive = true
      context.actionRejected = !accepted && driftFree
      if (!accepted && !driftFree) {
        recordedAnomalies.push({
          step,
          action: 'PROMPT_ACTIVE_PROBE',
          anomalies: [{
            severity: 'CRITICAL',
            type: 'PROMPT_REJECT_STATE_DRIFT',
            message: `弹窗待决时加分被拒但受保护状态发生漂移: side=${probeSide}`,
          }],
        })
      }
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'PROMPT_ACTIVE_PROBE', anomalies: audit })
      continue
    }

    // 优先处理弹窗状态机
    if (scoreboard.isGameEndPromptActive.value) {
      actionHistory.push({ step, action: 'CONFIRM_GAME_END' })
      scoreboard.confirmGameEnd()
      context.lastAction = 'CONFIRM_GAME_END'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'CONFIRM_GAME_END', anomalies: audit })
      continue
    }

    if (scoreboard.isFinalGameSideSwitchPromptActive.value) {
      const shouldSwitch = prng.randBool(0.8) // 80% 确认换边，20% 保持半场
      if (shouldSwitch) {
        matchStats.sideSwitchConfirmed++
      } else {
        matchStats.sideSwitchKept++
      }
      actionHistory.push({ step, action: 'DECIDING_GAME_SWITCH', shouldSwitch })
      scoreboard.handleFinalGameSideSwitch(shouldSwitch)
      context.lastAction = 'DECIDING_GAME_SWITCH'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'DECIDING_GAME_SWITCH', anomalies: audit })
      continue
    }

    // 动作分支轮盘
    const roll = prng.next()

    // 1) 存取回环重入模拟 (2%，仅在稳定无弹窗状态下)：走真实的
    //    saveStateToStorage -> restoreStateFromStorage（applySnapshot + applyRules 归一化）
    if (roll < 0.02 && !scoreboard.isPromptActive.value) {
      scoreboard.saveStateToStorage()
      scoreboard.restoreStateFromStorage()
      matchStats.reloads++
      actionHistory.push({ step, action: 'REENTER_RELOAD' })
      context.lastAction = 'REENTER_RELOAD'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'REENTER_RELOAD', anomalies: audit })
      continue
    }

    // 2) 退赛猴子探针 (1%)
    if (roll >= 0.02 && roll < 0.03 && !scoreboard.isPromptActive.value && scoreboard.historyStack.value.length > 3) {
      const retireSide = prng.choice(['left', 'right'])
      actionHistory.push({ step, action: 'RETIRE', side: retireSide })
      scoreboard.retire(retireSide)
      matchStats.retired = true
      context.lastAction = 'RETIRE'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'RETIRE', anomalies: audit })
      break
    }

    // 3) 连续深撤销 (8%) 或 单步撤销 (4%)
    if (roll >= 0.03 && roll < 0.15 && scoreboard.historyStack.value.length > 0 && !scoreboard.isPromptActive.value) {
      const isDeep = prng.randBool(0.67) && scoreboard.historyStack.value.length >= 3
      const undoSteps = isDeep ? Math.min(scoreboard.historyStack.value.length, prng.randInt(2, 4)) : 1
      if (isDeep) matchStats.deepUndos++
      for (let u = 0; u < undoSteps; u++) {
        scoreboard.undo()
      }
      actionHistory.push({ step, action: 'UNDO', steps: undoSteps })
      context.lastAction = 'UNDO'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'UNDO', anomalies: audit })
      continue
    }

    // 4) 手动换边 (5%)
    if (roll >= 0.15 && roll < 0.20 && !scoreboard.isPromptActive.value) {
      scoreboard.switchSides()
      matchStats.manualSideSwitches++
      actionHistory.push({ step, action: 'SWITCH_SIDES' })
      context.lastAction = 'SWITCH_SIDES'
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'SWITCH_SIDES', anomalies: audit })
      continue
    }

    // 5) 上帝模式微调 (4%)
    if (roll >= 0.20 && roll < 0.24 && !scoreboard.isPromptActive.value) {
      scoreboard.isGodMode.value = true
      const adjustSide = prng.choice(['left', 'right'])
      const delta = prng.choice([1, -1])
      scoreboard.adjustScore(adjustSide, delta)
      actionHistory.push({ step, action: 'GOD_ADJUST', side: adjustSide, delta })
      context.lastAction = 'GOD_ADJUST'
      markDecidingThresholdIfHit(context)
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'GOD_ADJUST', anomalies: audit })
      scoreboard.isGodMode.value = false
      continue
    }

    // 6) 恶意越界输入探针 (3%)：恶意输入被接受必须产生 CRITICAL；
    //    被拒绝时还须断言状态零漂移（拒绝不能有副作用）。
    //    真实模块 addScore 首行侧别守卫（§7.5 修复）负责拦截。
    if (roll >= 0.24 && roll < 0.27) {
      matchStats.hostileProbes++
      const hostileSide = 'invalid_side'
      const snapshotProbeState = () => JSON.stringify({
        leftScore: scoreboard.leftScore.value,
        rightScore: scoreboard.rightScore.value,
        leftGameWins: scoreboard.leftGameWins.value,
        rightGameWins: scoreboard.rightGameWins.value,
        currentGameNo: scoreboard.currentGameNo.value,
        serveSide: scoreboard.serveSide.value,
        gameScoresLength: scoreboard.gameScores.value.length,
      })
      const stateBeforeProbe = snapshotProbeState()
      const accepted = scoreboard.addScore(hostileSide)
      const serveSidePolluted = scoreboard.serveSide.value !== 'left' && scoreboard.serveSide.value !== 'right'
      if (accepted || serveSidePolluted) {
        recordedAnomalies.push({
          step,
          action: 'HOSTILE_PROBE',
          anomalies: [{
            severity: 'CRITICAL',
            type: 'HOSTILE_INPUT_ACCEPTED',
            message: `恶意输入 addScore('${hostileSide}') 被接受: accepted=${accepted}, serveSide=${scoreboard.serveSide.value}`,
          }],
        })
      } else {
        matchStats.hostileRejected++
        const stateAfterProbe = snapshotProbeState()
        if (stateAfterProbe !== stateBeforeProbe) {
          recordedAnomalies.push({
            step,
            action: 'HOSTILE_PROBE',
            anomalies: [{
              severity: 'CRITICAL',
              type: 'HOSTILE_REJECT_STATE_DRIFT',
              message: `恶意输入被拒绝但状态发生漂移: before=${stateBeforeProbe}, after=${stateAfterProbe}`,
            }],
          })
        }
      }
      actionHistory.push({ step, action: 'HOSTILE_PROBE', accepted })
      context.lastAction = 'HOSTILE_PROBE'
      context.actionRejected = !accepted
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'HOSTILE_PROBE', anomalies: audit })
      continue
    }

    // 7) 常规正常得分 (余下约 73%)
    const scoreSide = prng.choice(['left', 'right'])
    const accepted = scoreboard.addScore(scoreSide)

    if (accepted) {
      context.lastAction = 'SCORE'
      context.lastScoredSide = scoreSide
      markDecidingThresholdIfHit(context)
      const currentScore = scoreSide === 'left' ? scoreboard.leftScore.value : scoreboard.rightScore.value
      if (currentScore === scoreboard.matchRules.value.capPoint) {
        matchStats.capHits++
      }
      actionHistory.push({
        step,
        action: 'SCORE',
        side: scoreSide,
        leftScore: scoreboard.leftScore.value,
        rightScore: scoreboard.rightScore.value,
        currentGameNo: scoreboard.currentGameNo.value,
      })
    }
    const audit = auditBadmintonInvariants(scoreboard, scenario, context)
    if (audit.length > 0) recordedAnomalies.push({ step, action: 'SCORE', anomalies: audit })
  }

  // 终局后的不可变性探针（P0-1 激活断言 7）：加分 / 上帝模式微调 / 换边
  // 必须全部被拒，且受保护状态逐字段零漂移。
  if (scoreboard.matchEnded.value) {
    matchStats.terminalProbes++
    const stateBeforeProbe = snapshotGuardedState(scoreboard)
    const postEndAccepted = scoreboard.addScore('left')
    const prevGodMode = scoreboard.isGodMode.value
    scoreboard.isGodMode.value = true
    const postEndAdjustAccepted = scoreboard.adjustScore('right', 1)
    scoreboard.isGodMode.value = prevGodMode
    const postEndSwitchAccepted = scoreboard.switchSides()
    const driftFree = snapshotGuardedState(scoreboard) === stateBeforeProbe
    actionHistory.push({
      step: step + 1,
      action: 'TERMINAL_PROBE',
      postEndAccepted,
      postEndAdjustAccepted,
      postEndSwitchAccepted,
      driftFree,
    })

    const terminalAnomalies = []
    if (postEndAccepted) {
      terminalAnomalies.push({
        severity: 'CRITICAL',
        type: 'TERMINAL_STATE_MUTATION_ACCEPTED',
        message: '终局后仍然接受加分',
      })
    }
    if (postEndAdjustAccepted || postEndSwitchAccepted) {
      terminalAnomalies.push({
        severity: 'CRITICAL',
        type: 'TERMINAL_STATE_MUTATION_ACCEPTED',
        message: `终局后仍然接受变更动作: adjust=${postEndAdjustAccepted}, switch=${postEndSwitchAccepted}`,
      })
    }
    if (!driftFree) {
      terminalAnomalies.push({
        severity: 'CRITICAL',
        type: 'TERMINAL_STATE_DRIFT',
        message: `终局后拒绝性调用导致受保护状态漂移`,
      })
    }
    if (terminalAnomalies.length > 0) {
      recordedAnomalies.push({ step: step + 1, action: 'TERMINAL_PROBE', anomalies: terminalAnomalies })
    }

    // 经审计器正式激活断言 7（actionRejected 汇总三个探针 + 零漂移）
    const terminalContext = {
      step: step + 1,
      lastActionAttemptedWhileEnded: true,
      actionRejected: !postEndAccepted && !postEndAdjustAccepted && !postEndSwitchAccepted && driftFree,
    }
    const terminalAudit = auditBadmintonInvariants(scoreboard, scenario, terminalContext)
    if (terminalAudit.length > 0) recordedAnomalies.push({ step: step + 1, action: 'TERMINAL_PROBE', anomalies: terminalAudit })
  }

  matchStats.gamesPlayed = scoreboard.gameScores.value.length
  const finalState = scoreboard.buildSnapshot()

  // 释放本场响应式副作用（终态已提取为纯快照）
  matchScope.stop()

  return {
    matchId: scenario.matchId,
    seed: prng.seed,
    rules: scenario.rules,
    totalSteps: step,
    matchStats,
    actionHistory,
    anomalies: recordedAnomalies,
    hasCritical: recordedAnomalies.some((a) => a.anomalies.some((item) => item.severity === 'CRITICAL')),
    hasSuspicious: recordedAnomalies.some((a) => a.anomalies.some((item) => item.severity === 'SUSPICIOUS')),
    finalState,
  }
}

/**
 * 批量羽毛球无头混沌测试执行器
 */
export async function runBadmintonFuzzerBatch(matchCount = 10, options = {}) {
  const baseSeed = options.baseSeed || 200000000
  const startTime = Date.now()
  const results = []
  const criticalReports = []
  const suspiciousReports = []

  let totalRallies = 0
  let totalUndos = 0
  let totalSideSwitches = 0

  for (let i = 1; i <= matchCount; i++) {
    const matchSeed = baseSeed + i * 9973
    const prng = createPRNG(matchSeed)
    const scenario = generateBadmintonScenario(prng, i)

    const matchResult = await simulateBadmintonMatch(scenario, prng, options)
    results.push(matchResult)

    for (const a of matchResult.actionHistory) {
      if (a.action === 'SCORE') totalRallies++
      if (a.action === 'UNDO') totalUndos++
      if (a.action === 'SWITCH_SIDES' || a.action === 'DECIDING_GAME_SWITCH') totalSideSwitches++
    }

    // 批级断言：恶意探针必须 100% 被拒绝（真实模块 addScore 首行侧别守卫，
    // §7.5 修复的持续回归监测）；一旦有探针被接受，该场计入 criticalMatches。
    const probeStats = matchResult.matchStats || {}
    if ((probeStats.hostileProbes || 0) !== (probeStats.hostileRejected || 0)) {
      criticalReports.push({
        ...matchResult,
        anomalies: [
          ...matchResult.anomalies,
          {
            step: matchResult.totalSteps,
            action: 'BATCH_HOSTILE_PROBE_CHECK',
            anomalies: [{
              severity: 'CRITICAL',
              type: 'HOSTILE_PROBE_NOT_REJECTED',
              message: `批级断言失败: hostileProbes=${probeStats.hostileProbes || 0} 与 hostileRejected=${probeStats.hostileRejected || 0} 不一致，存在未被拒绝的恶意探针`,
            }],
          },
        ],
      })
    } else if (matchResult.hasCritical) {
      criticalReports.push(matchResult)
    } else if (matchResult.hasSuspicious) {
      suspiciousReports.push(matchResult)
    }

    if (options.onProgress && i % Math.max(1, Math.floor(matchCount / 10)) === 0) {
      options.onProgress({
        current: i,
        total: matchCount,
        criticalCount: criticalReports.length,
        suspiciousCount: suspiciousReports.length,
      })
    }
  }

  const durationMs = Date.now() - startTime

  // 汇总覆盖度证据
  const coverage = {
    totalReloads: 0,
    decidingGameSideSwitched: 0,
    decidingGameSideKept: 0,
    manualSideSwitches: 0,
    deepUndos: 0,
    hostileProbes: 0,
    hostileRejected: 0,
    promptActiveProbes: 0,
    terminalProbes: 0,
    capHits: 0,
    retirements: 0,
    matchesDecidingGameReached: 0,
    gamesPlayedHistogram: {},
  }

  for (const r of results) {
    const ms = r.matchStats || {}
    coverage.totalReloads += ms.reloads || 0
    coverage.decidingGameSideSwitched += ms.sideSwitchConfirmed || 0
    coverage.decidingGameSideKept += ms.sideSwitchKept || 0
    coverage.manualSideSwitches += ms.manualSideSwitches || 0
    coverage.deepUndos += ms.deepUndos || 0
    coverage.hostileProbes += ms.hostileProbes || 0
    coverage.hostileRejected += ms.hostileRejected || 0
    coverage.promptActiveProbes += ms.promptActiveProbes || 0
    coverage.terminalProbes += ms.terminalProbes || 0
    coverage.capHits += ms.capHits || 0
    if (ms.retired) coverage.retirements++
    if (ms.decidingGameReached) coverage.matchesDecidingGameReached++
    const gp = String(ms.gamesPlayed || 0)
    coverage.gamesPlayedHistogram[gp] = (coverage.gamesPlayedHistogram[gp] || 0) + 1
  }

  const summary = {
    baseSeed,
    matchCount,
    durationMs,
    stats: {
      totalRallies,
      totalUndos,
      totalSideSwitches,
      criticalMatches: criticalReports.length,
      suspiciousMatches: suspiciousReports.length,
      cleanMatches: matchCount - criticalReports.length - suspiciousReports.length,
    },
    coverage,
    criticalSummary: criticalReports.map((r) => ({
      matchId: r.matchId,
      seed: r.seed,
      anomalies: r.anomalies.map((a) => ({ step: a.step, action: a.action, details: a.anomalies })),
    })),
    suspiciousSummary: suspiciousReports.map((r) => ({
      matchId: r.matchId,
      seed: r.seed,
      anomalies: r.anomalies.map((a) => ({ step: a.step, action: a.action, details: a.anomalies })),
    })),
  }

  // 产物落盘（写失败必须 console.error 留痕并计入返回值，不允许静默吞错；但不抛出中断批跑）
  const artifactWriteFailures = []
  const recordArtifactWriteFailure = (targetPath, err) => {
    const message = err && err.message ? err.message : String(err)
    artifactWriteFailures.push({ path: targetPath, message })
    console.error(`[badminton-fuzzer] 产物落盘失败: path=${targetPath} error=${message}`)
  }
  try {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const outDir = process.env.FUZZ_SUMMARY_DIR
      ? path.resolve(process.env.FUZZ_SUMMARY_DIR)
      : options.outputDir
        ? path.resolve(options.outputDir)
        : path.resolve(process.cwd(), '../outputs/fuzz-badminton')

    try {
      if (!fs.existsSync(outDir)) {
        fs.mkdirSync(outDir, { recursive: true })
      }
    } catch (err) {
      recordArtifactWriteFailure(outDir, err)
    }

    const summaryPath = path.join(outDir, 'fuzz-summary.json')
    try {
      fs.writeFileSync(summaryPath, JSON.stringify(summary, null, 2), 'utf8')
    } catch (err) {
      recordArtifactWriteFailure(summaryPath, err)
    }

    if (criticalReports.length > 0 || suspiciousReports.length > 0) {
      const anomaliesPath = path.join(outDir, 'fuzz-anomalies.json')
      try {
        fs.writeFileSync(
          anomaliesPath,
          JSON.stringify({ critical: criticalReports, suspicious: suspiciousReports }, null, 2),
          'utf8'
        )
      } catch (err) {
        recordArtifactWriteFailure(anomaliesPath, err)
      }
    }
  } catch (err) {
    // fs/path 模块加载或路径解析本身失败（如非 Node 环境），同样必须留痕
    recordArtifactWriteFailure('(artifact pipeline init)', err)
  }

  return {
    ...summary,
    criticalReports,
    suspiciousReports,
    artifactWriteFailures,
  }
}
