/**
 * 羽毛球双打「四半区站位」无头混沌引擎（2026-10 新增）
 *
 * 与 badminton-fuzzer.js 的关系：那个 fuzzer 的 isDoubles 只是队名拼接（状态机
 * 仍按单打驱动，无站位上下文）；本引擎把双打站位全链路拉进混沌——
 * 装配（applyCourtContext + setupDoublesCourt）→ 加分锚点转移 → 撤销回滚 →
 * 换边镜像 → 局间流转（含第 N≥2 局开局站位重选面板：pending 硬锁探针 + 确认清位）→
 * 站位纠错 → 降级路径。
 *
 * 审计策略（三层）：
 * 1. 差分影子模型：独立实现一套 BWF 站位转移镜像（不 import 被测代码），
 *    与真实状态机逐步全等比对（比分/发球权/锚点/局流转/换边）；
 * 2. 规则不变量：对 deriveCourtView 输出校验 Law 11.1（偶右奇左）、Law 9.1.3/11.1.4
 *    （斜对角同名半区）、Law 11.3.1（发球方得分同一发球员换区）、锚点-发球员一致性；
 * 3. 防御断言：非法参数动作必须被拒绝且零状态漂移；降级/录入态门控正确；
 *    快照与事件 runtime 往返不丢失站位。
 *
 * 复用 badminton-fuzzer 的 createPRNG 与 storage/effectScope 隔离经验；
 * FUZZ_MATCHES / FUZZ_SUMMARY_DIR 环境变量惯例与其保持一致。
 */
import { effectScope } from 'vue'
import { setupFuzzerEnvironment } from './fuzzer-env'
import { useScoreboardState } from './use-scoreboard-state'
import { deriveCourtView } from './court-position'
import { runtimeToSnapshot } from './badminton-recovery'

import { createPRNG } from './badminton-fuzzer'

export { createPRNG }

const MAX_STEPS_PER_MATCH = 600

/** 双打站位混沌场景：规则 + 成员 + 初始站位 + 动作权重 + 是否跳过录入（降级路径）。 */
export function generateDoublesCourtScenario(prng, matchIndex = 1) {
  const bestOf = prng.choice([1, 3, 5])
  const gamesToWin = Math.floor(bestOf / 2) + 1
  const pointsToWin = prng.choice([11, 15, 21])
  const enableDeuce = prng.randBool(0.85)
  const capPoint = enableDeuce ? prng.choice([pointsToWin + 5, 30, 99]) : pointsToWin
  const leftMembers = [`甲${matchIndex}一`, `甲${matchIndex}二`]
  const rightMembers = [`乙${matchIndex}一`, `乙${matchIndex}二`]
  return {
    matchId: `court_fuzz_${matchIndex}_${prng.seed}`,
    rules: { bestOf, gamesToWin, pointsToWin, enableDeuce, capPoint },
    leftMembers,
    rightMembers,
    initialServeSide: prng.choice(['left', 'right']),
    initialLeftAnchor: prng.randInt(0, 1),
    initialRightAnchor: prng.randInt(0, 1),
    // 8% 场次跳过开局录入：覆盖降级显示与「无锚点副作用」路径
    skipPlacement: prng.randBool(0.08),
    weights: {
      score: 0.62,
      undo: 0.1,
      swap: 0.09,
      manualSwitch: 0.04,
      retire: 0.01,
      hostile: 0.03,
    },
  }
}

/**
 * 影子模型：独立实现的 BWF 双打站位状态转移（屏幕坐标，与状态机 refs 同坐标系）。
 * 刻意不引用 court-position.js / use-scoreboard-state.js，保证差分有效性。
 */
function createMirror(scenario) {
  const placed = !scenario.skipPlacement
  return {
    placed,
    leftScore: 0,
    rightScore: 0,
    leftGameWins: 0,
    rightGameWins: 0,
    currentGameNo: 1,
    gameScores: [],
    serveSide: placed ? scenario.initialServeSide : 'left',
    leftAnchor: placed ? scenario.initialLeftAnchor : 0,
    rightAnchor: placed ? scenario.initialRightAnchor : 0,
    sidesSwapped: false,
    retiredSide: '',
    matchEnded: false,
    gameEndPromptPending: false,
    courtPending: false,
    finalPending: false,
    finalHandled: false,
    leftMembers: [...scenario.leftMembers],
    rightMembers: [...scenario.rightMembers],
    history: [],
    // 供连续性校验：最近一次稳定态的发球员名（屏幕坐标）
    lastServerName: '',
  }
}

function mirrorSnapshot(mirror) {
  return {
    placed: mirror.placed,
    leftScore: mirror.leftScore,
    rightScore: mirror.rightScore,
    leftGameWins: mirror.leftGameWins,
    rightGameWins: mirror.rightGameWins,
    currentGameNo: mirror.currentGameNo,
    gameScores: mirror.gameScores.map((g) => ({ ...g })),
    serveSide: mirror.serveSide,
    leftAnchor: mirror.leftAnchor,
    rightAnchor: mirror.rightAnchor,
    sidesSwapped: mirror.sidesSwapped,
    retiredSide: mirror.retiredSide,
    matchEnded: mirror.matchEnded,
    lastServerName: mirror.lastServerName,
    leftMembers: [...mirror.leftMembers],
    rightMembers: [...mirror.rightMembers],
  }
}

function mirrorScoreOf(mirror, side) {
  return side === 'left' ? mirror.leftScore : mirror.rightScore
}

function mirrorWinCondition(mirror, rules, myScore, opponentScore) {
  if (myScore >= rules.capPoint) return true
  if (myScore >= rules.pointsToWin) {
    if (!rules.enableDeuce) return true
    return myScore - opponentScore >= 2
  }
  return false
}

function mirrorPushHistory(mirror) {
  mirror.history.push(mirrorSnapshot(mirror))
}

/** addScore 成功后的镜像转移（调用方保证动作被接受）。 */
function mirrorApplyScore(mirror, rules, side) {
  mirrorPushHistory(mirror)
  const wasServing = mirror.serveSide === side
  if (side === 'left') mirror.leftScore += 1
  else mirror.rightScore += 1
  // Law 11.1.5/11.3.1：发球方得分 → 该侧两人互换左右半区（锚点翻转）
  if (mirror.placed && wasServing) {
    if (side === 'left') mirror.leftAnchor = 1 - mirror.leftAnchor
    else mirror.rightAnchor = 1 - mirror.rightAnchor
  }
  mirror.serveSide = side

  const myScore = mirrorScoreOf(mirror, side)
  const opponentScore = mirrorScoreOf(mirror, side === 'left' ? 'right' : 'left')
  if (mirrorWinCondition(mirror, rules, myScore, opponentScore)) {
    mirror.gameScores.push({
      gameNo: mirror.currentGameNo,
      leftScore: mirror.leftScore,
      rightScore: mirror.rightScore,
      winnerSide: side,
    })
    if (side === 'left') mirror.leftGameWins += 1
    else mirror.rightGameWins += 1
    if (mirror.leftGameWins >= rules.gamesToWin || mirror.rightGameWins >= rules.gamesToWin) {
      mirror.matchEnded = true
    } else {
      mirror.gameEndPromptPending = true
    }
  }
}

function mirrorApplySideSwitch(mirror) {
  const s = mirror.leftScore
  mirror.leftScore = mirror.rightScore
  mirror.rightScore = s
  const w = mirror.leftGameWins
  mirror.leftGameWins = mirror.rightGameWins
  mirror.rightGameWins = w
  mirror.gameScores = mirror.gameScores.map((g) => ({
    gameNo: g.gameNo,
    leftScore: g.rightScore,
    rightScore: g.leftScore,
    winnerSide: g.winnerSide === 'left' ? 'right' : 'left',
  }))
  mirror.serveSide = mirror.serveSide === 'left' ? 'right' : 'left'
  const a = mirror.leftAnchor
  mirror.leftAnchor = mirror.rightAnchor
  mirror.rightAnchor = a
  const m = mirror.leftMembers
  mirror.leftMembers = mirror.rightMembers
  mirror.rightMembers = m
  mirror.sidesSwapped = !mirror.sidesSwapped
}

function mirrorApplyConfirmGameEnd(mirror) {
  mirror.gameEndPromptPending = false
  const lastGame = mirror.gameScores[mirror.gameScores.length - 1]
  const winnerSide = lastGame?.winnerSide === 'right' ? 'right' : 'left'
  mirror.currentGameNo += 1
  mirror.leftScore = 0
  mirror.rightScore = 0
  mirror.serveSide = winnerSide
  mirror.finalPending = false
  mirror.finalHandled = false
  if (mirror.currentGameNo >= 2) {
    mirrorApplySideSwitch(mirror)
  }
  // 双打第 N≥2 局：开局站位重选面板待决（与状态机 confirmGameEnd 对齐；
  // 未录入站位的降级路径不弹面板）
  if (mirror.placed) {
    mirror.courtPending = true
  }
}

function mirrorApplyUndo(mirror) {
  const prev = mirror.history.pop()
  if (!prev) return
  Object.assign(mirror, prev, { history: mirror.history })
}

function readCourtView(scoreboard) {
  return deriveCourtView({
    mode: scoreboard.courtMode.value,
    placed: scoreboard.courtPlaced.value,
    leftMembers: scoreboard.leftCourtMembers.value,
    rightMembers: scoreboard.rightCourtMembers.value,
    leftAnchor: scoreboard.leftAnchorIndex.value,
    rightAnchor: scoreboard.rightAnchorIndex.value,
    serveSide: scoreboard.serveSide.value,
    leftScore: scoreboard.leftScore.value,
    rightScore: scoreboard.rightScore.value,
    leftTeamName: scoreboard.leftTeam.value,
    rightTeamName: scoreboard.rightTeam.value,
  })
}

function findRoleSlot(view, side, role) {
  if (!view || !view.halves) return null
  return view.halves[side].slots.find((slot) => slot.role === role) || null
}

/** 含站位字段的全量保护快照：拒绝性操作必须零漂移。 */
function snapshotGuardedCourtState(scoreboard) {
  return JSON.stringify({
    leftScore: scoreboard.leftScore.value,
    rightScore: scoreboard.rightScore.value,
    leftGameWins: scoreboard.leftGameWins.value,
    rightGameWins: scoreboard.rightGameWins.value,
    currentGameNo: scoreboard.currentGameNo.value,
    gameScores: scoreboard.gameScores.value,
    serveSide: scoreboard.serveSide.value,
    sidesSwapped: scoreboard.sidesSwapped.value,
    courtMode: scoreboard.courtMode.value,
    courtPlaced: scoreboard.courtPlaced.value,
    leftMembers: scoreboard.leftCourtMembers.value,
    rightMembers: scoreboard.rightCourtMembers.value,
    leftAnchor: scoreboard.leftAnchorIndex.value,
    rightAnchor: scoreboard.rightAnchorIndex.value,
    matchEnded: scoreboard.matchEnded.value,
    retiredSide: scoreboard.retiredSide.value,
    historyStackLength: scoreboard.historyStack.value.length,
  })
}

/**
 * 站位不变量审计器：差分 + BWF 规则断言。
 * 返回异常列表（severity: CRITICAL / SUSPICIOUS）。
 */
export function auditCourtInvariants(scoreboard, mirror, scenario, context = {}) {
  const anomalies = []
  const check = (cond, type, message, severity = 'CRITICAL') => {
    if (!cond) anomalies.push({ severity, type, message })
  }
  const rules = scoreboard.matchRules.value

  // 1) 差分：镜像 vs 真实状态机（屏幕坐标逐字段）
  check(mirror.leftScore === scoreboard.leftScore.value, 'DIFF_LEFT_SCORE',
    `leftScore mirror=${mirror.leftScore} actual=${scoreboard.leftScore.value}`)
  check(mirror.rightScore === scoreboard.rightScore.value, 'DIFF_RIGHT_SCORE',
    `rightScore mirror=${mirror.rightScore} actual=${scoreboard.rightScore.value}`)
  check(mirror.serveSide === scoreboard.serveSide.value, 'DIFF_SERVE_SIDE',
    `serveSide mirror=${mirror.serveSide} actual=${scoreboard.serveSide.value}`)
  check(mirror.leftAnchor === scoreboard.leftAnchorIndex.value, 'DIFF_LEFT_ANCHOR',
    `leftAnchor mirror=${mirror.leftAnchor} actual=${scoreboard.leftAnchorIndex.value}`)
  check(mirror.rightAnchor === scoreboard.rightAnchorIndex.value, 'DIFF_RIGHT_ANCHOR',
    `rightAnchor mirror=${mirror.rightAnchor} actual=${scoreboard.rightAnchorIndex.value}`)
  check(mirror.placed === scoreboard.courtPlaced.value, 'DIFF_PLACED',
    `placed mirror=${mirror.placed} actual=${scoreboard.courtPlaced.value}`)
  check(mirror.sidesSwapped === scoreboard.sidesSwapped.value, 'DIFF_SIDES_SWAPPED',
    `sidesSwapped mirror=${mirror.sidesSwapped} actual=${scoreboard.sidesSwapped.value}`)
  check(mirror.currentGameNo === scoreboard.currentGameNo.value, 'DIFF_GAME_NO',
    `gameNo mirror=${mirror.currentGameNo} actual=${scoreboard.currentGameNo.value}`)
  check(mirror.matchEnded === scoreboard.matchEnded.value, 'DIFF_MATCH_ENDED',
    `matchEnded mirror=${mirror.matchEnded} actual=${scoreboard.matchEnded.value}`)
  check(mirror.courtPending === scoreboard.nextGameCourtPlacementPending.value, 'DIFF_COURT_PENDING',
    `courtPending mirror=${mirror.courtPending} actual=${scoreboard.nextGameCourtPlacementPending.value}`)
  check(JSON.stringify(mirror.leftMembers) === JSON.stringify(scoreboard.leftCourtMembers.value),
    'DIFF_LEFT_MEMBERS', `leftMembers mirror=${JSON.stringify(mirror.leftMembers)} actual=${JSON.stringify(scoreboard.leftCourtMembers.value)}`)
  check(JSON.stringify(mirror.rightMembers) === JSON.stringify(scoreboard.rightCourtMembers.value),
    'DIFF_RIGHT_MEMBERS', `rightMembers mirror=${JSON.stringify(mirror.rightMembers)} actual=${JSON.stringify(scoreboard.rightCourtMembers.value)}`)

  // 2) 规则不变量（只在已录入站位且未锁定的进行态校验）
  const stable = mirror.placed && !mirror.matchEnded && !mirror.retiredSide && !context.promptActive
  if (stable) {
    const view = readCourtView(scoreboard)
    check(!view.degraded, 'VIEW_UNEXPECTED_DEGRADED', '已录入站位但视图降级')
    const servingSide = mirror.serveSide
    const servingScore = servingSide === 'left' ? mirror.leftScore : mirror.rightScore
    const expectedCourt = servingScore % 2 === 0 ? 'right' : 'left' // 独立计算，不用 serverCourtName
    const serverSlot = findRoleSlot(view, servingSide, 'server')
    const receiverSlot = findRoleSlot(view, servingSide === 'left' ? 'right' : 'left', 'receiver')
    check(!!serverSlot, 'LAW_SERVER_MISSING', `发球侧无 server 槽位 (score ${servingScore})`)
    check(!!receiverSlot, 'LAW_RECEIVER_MISSING', '接发侧无 receiver 槽位')
    if (serverSlot) {
      // Law 11.1.6：每轮发球都从与分数对应的发球区发出（偶右奇左）
      check(serverSlot.court === expectedCourt, 'LAW_PARITY_SERVE_COURT',
        `server court=${serverSlot.court} 期望=${expectedCourt} (score=${servingScore})`)
      // 锚点-发球员一致性：发球员必须是发球侧「与 parity 对应半区」的成员
      const members = servingSide === 'left' ? mirror.leftMembers : mirror.rightMembers
      const anchor = servingSide === 'left' ? scoreboard.leftAnchorIndex.value : scoreboard.rightAnchorIndex.value
      const expectedPlayer = expectedCourt === 'right' ? members[anchor] : members[1 - anchor]
      check(serverSlot.player === expectedPlayer, 'LAW_SERVER_FROM_ANCHOR',
        `server=${serverSlot.player} 期望=${expectedPlayer} (anchor=${anchor}, court=${expectedCourt})`)
      // Law 11.3.1：发球方得分后同一发球员换区继续发
      if (context.serverContinuityExpected && mirror.lastServerName) {
        check(serverSlot.player === mirror.lastServerName, 'LAW_SAME_SERVER_CONTINUITY',
          `发球方得分后发球员改变: ${mirror.lastServerName} -> ${serverSlot.player}`)
      }
    }
    // Law 9.1.3 / 11.1.4：接发球员与发球员斜对角（同名半区）
    if (serverSlot && receiverSlot) {
      check(receiverSlot.court === serverSlot.court, 'LAW_DIAGONAL_RECEIVER',
        `receiver court=${receiverSlot.court} 应与 server court=${serverSlot.court} 同名半区（斜对角）`)
    }
    // 记录稳定态发球员供下一步连续性校验
    if (serverSlot) mirror.lastServerName = serverSlot.player
  } else {
    mirror.lastServerName = ''
  }

  // 3) 录入态门控：仅全新比赛强制弹窗
  const hasProgress = scoreboard.leftScore.value + scoreboard.rightScore.value > 0
    || scoreboard.gameScores.value.length > 0
    || scoreboard.leftGameWins.value + scoreboard.rightGameWins.value > 0
  const expectedPending = scoreboard.isDoublesCourt.value && !scoreboard.courtPlaced.value
    && !hasProgress && !scoreboard.isLocked.value && !scoreboard.isReadOnly.value
  check(scoreboard.courtPlacementPending.value === expectedPending, 'GATE_PLACEMENT_PENDING',
    `placementPending=${scoreboard.courtPlacementPending.value} 期望=${expectedPending}`)

  // 4) 快照/runtime 往返（抽检）
  if (context.roundtripProbe) {
    const snapshot = scoreboard.buildSnapshot()
    const runtime = scoreboard.buildEventRuntime()
    const backFromRuntime = runtimeToSnapshot(runtime)
    const courtKeys = ['mode', 'placed', 'leftMembers', 'rightMembers', 'leftAnchor', 'rightAnchor']
    for (const key of courtKeys) {
      check(JSON.stringify(snapshot.court?.[key]) === JSON.stringify(backFromRuntime?.court?.[key]),
        'ROUNDTRIP_COURT_MISMATCH', `快照与 runtime 往返不一致: ${key} snapshot=${JSON.stringify(snapshot.court?.[key])} runtime=${JSON.stringify(backFromRuntime?.court?.[key])}`)
    }
  }

  return anomalies
}

/**
 * 单场双打站位混沌：装配 → 随机动作流 → 逐步审计。
 * 返回 { actionHistory, anomalies, matchStats, hasCritical, hasSuspicious, finalState, totalSteps }。
 */
export async function simulateDoublesCourtMatch(scenario, prng, options = {}) {
  setupFuzzerEnvironment()
  const activeScope = effectScope()
  const actionHistory = []
  const recordedAnomalies = []
  const seenAnomalyKeys = new Set()
  const matchStats = {
    rallies: 0,
    undos: 0,
    swaps: 0,
    manualSwitches: 0,
    decidingSwitches: 0,
    decidingSwitchTrue: 0,
    decidingSwitchFalse: 0,
    gameEnds: 0,
    retires: 0,
    hostileProbes: 0,
    hostileRejected: 0,
    roundtripProbes: 0,
    unexpected: [],
    scoreRejected: 0,
    swapRejected: 0,
    undoRejected: 0,
    switchRejected: 0,
    retireRejected: 0,
    courtPendingProbes: 0,
    courtPendingConfirms: 0,
    maxStepsHit: false,
    deuceGames: 0,
    capHitGames: 0,
  }
  let diagStep = 0
  const recordUnexpected = (type, message) => {
    matchStats.unexpected.push({ step: diagStep, type, message })
  }

  const pushAnomalies = (stepNo, action, audit) => {
    if (!audit || audit.length === 0) return
    const fresh = audit.filter((item) => {
      const key = item.type + '|' + item.message
      if (seenAnomalyKeys.has(key)) return false
      seenAnomalyKeys.add(key)
      return true
    })
    if (fresh.length > 0) recordedAnomalies.push({ step: stepNo, action, anomalies: fresh })
  }

  const scoreboard = activeScope.run(() => useScoreboardState({ onAction: () => {} }))
  scoreboard.matchId.value = scenario.matchId
  scoreboard.applyRules(scenario.rules)
  scoreboard.leftTeam.value = scenario.leftMembers.join('/')
  scoreboard.rightTeam.value = scenario.rightMembers.join('/')

  const mirror = createMirror(scenario)
  const applied = scoreboard.applyCourtContext({
    mode: 'doubles',
    leftMembers: scenario.leftMembers,
    rightMembers: scenario.rightMembers,
  })
  if (applied && !scenario.skipPlacement) {
    scoreboard.setupDoublesCourt({
      serveSide: scenario.initialServeSide,
      leftAnchor: scenario.initialLeftAnchor,
      rightAnchor: scenario.initialRightAnchor,
    })
  }

  const weights = scenario.weights
  const promptActive = () => scoreboard.isPromptActive.value
  const locked = () => scoreboard.isLocked.value

  let step = 0
  while (step < MAX_STEPS_PER_MATCH && !locked()) {
    step += 1
    diagStep = step
    if (step === MAX_STEPS_PER_MATCH && !locked()) matchStats.maxStepsHit = true

    // 弹窗态：只能确认（局结束 / 决胜局技术换边）
    if (scoreboard.gameEndPromptPending.value) {
      scoreboard.confirmGameEnd()
      mirrorApplyConfirmGameEnd(mirror)
      matchStats.gameEnds += 1
      actionHistory.push({ step, action: 'CONFIRM_GAME_END' })
      pushAnomalies(step, 'CONFIRM_GAME_END',
        auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }
    if (scoreboard.isFinalGameSideSwitchPromptActive.value) {
      const shouldSwitch = prng.randBool(0.6)
      scoreboard.handleFinalGameSideSwitch(shouldSwitch)
      if (shouldSwitch) {
        // 技术换边：镜像换边（状态机内部 applySideSwitch，不推撤销栈）
        mirrorApplySideSwitch(mirror)
        matchStats.decidingSwitches += 1
        matchStats.decidingSwitchTrue += 1
      } else {
        matchStats.decidingSwitchFalse += 1
      }
      mirror.finalPending = false
      mirror.finalHandled = true
      // 技术换边后可能直接完局（比分已到终点）
      const leftWin = mirrorWinCondition(mirror, scenario.rules, mirror.leftScore, mirror.rightScore)
      const rightWin = mirrorWinCondition(mirror, scenario.rules, mirror.rightScore, mirror.leftScore)
      if (leftWin || rightWin) {
        const winner = leftWin ? 'left' : 'right'
        mirror.gameScores.push({
          gameNo: mirror.currentGameNo, leftScore: mirror.leftScore, rightScore: mirror.rightScore, winnerSide: winner,
        })
        if (winner === 'left') mirror.leftGameWins += 1
        else mirror.rightGameWins += 1
        if (mirror.leftGameWins >= scenario.rules.gamesToWin || mirror.rightGameWins >= scenario.rules.gamesToWin) {
          mirror.matchEnded = true
        } else {
          mirror.gameEndPromptPending = true
        }
      }
      actionHistory.push({ step, action: `DECIDING_GAME_SWITCH:${shouldSwitch}` })
      pushAnomalies(step, 'DECIDING_GAME_SWITCH',
        auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }

    // 局间站位重选面板（双打第 N≥2 局）：轮盘在 pending 时先探针后确认再继续。
    // 探针：面板期间任何 mutation 必须被拒且零漂移（COURT_PENDING_MUTATION_ACCEPTED
    // = CRITICAL 红线）；确认按随机锚点写入（含与继承值相同的情形），镜像同步。
    if (scoreboard.nextGameCourtPlacementPending.value) {
      matchStats.courtPendingProbes += 1
      const guard = snapshotGuardedCourtState(scoreboard)
      const probeSide = prng.choice(['left', 'right'])
      const allRejected = !scoreboard.addScore(probeSide)
        && !scoreboard.undo()
        && !scoreboard.switchSides()
        && !scoreboard.retire(probeSide)
        && !scoreboard.swapCourtPlayers(probeSide)
      const driftFree = guard === snapshotGuardedCourtState(scoreboard)
      if (!allRejected || !driftFree) {
        pushAnomalies(step, 'COURT_PENDING_PROBE', [{
          severity: 'CRITICAL',
          type: 'COURT_PENDING_MUTATION_ACCEPTED',
          message: `站位面板待决时 mutation 被接受或状态漂移: allRejected=${allRejected}, driftFree=${driftFree}`,
        }])
      }
      const leftAnchor = prng.randInt(0, 1)
      const rightAnchor = prng.randInt(0, 1)
      const confirmed = scoreboard.confirmNextGameCourtPlacement({ leftAnchor, rightAnchor })
      if (confirmed) {
        matchStats.courtPendingConfirms += 1
        mirror.leftAnchor = leftAnchor
        mirror.rightAnchor = rightAnchor
        mirror.courtPending = false
      } else {
        pushAnomalies(step, 'COURT_PENDING_CONFIRM', [{
          severity: 'CRITICAL',
          type: 'COURT_PENDING_CONFIRM_REJECTED',
          message: '站位面板待决时确认动作被拒',
        }])
      }
      actionHistory.push({ step, action: 'CONFIRM_NEXT_GAME_COURT', confirmed })
      pushAnomalies(step, 'CONFIRM_NEXT_GAME_COURT',
        auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }

    const roll = prng.next()
    if (roll < weights.hostile) {
      // 恶意探针：非法参数必须被拒绝且零漂移
      matchStats.hostileProbes += 1
      const guard = snapshotGuardedCourtState(scoreboard)
      const hostile = prng.choice([
        () => scoreboard.addScore('middle'),
        () => scoreboard.swapCourtPlayers('bad-side'),
        () => scoreboard.setupDoublesCourt({ serveSide: 'middle' }),
        () => scoreboard.setFirstServeSide(''),
      ])
      const accepted = hostile()
      const after = snapshotGuardedCourtState(scoreboard)
      if (accepted === false && guard === after) {
        matchStats.hostileRejected += 1
      }
      actionHistory.push({ step, action: 'HOSTILE_PROBE', accepted: !!accepted })
      pushAnomalies(step, 'HOSTILE_PROBE',
        auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }
    if (roll < weights.hostile + weights.undo) {
      const guard = snapshotGuardedCourtState(scoreboard)
      const canUndo = scoreboard.historyStack.value.length > 0 && !promptActive() && !locked()
      const accepted = scoreboard.undo()
      if (accepted) {
        matchStats.undos += 1
        mirrorApplyUndo(mirror)
      } else {
        matchStats.undoRejected += 1
        if (canUndo) recordUnexpected('UNEXPECTED_UNDO_REJECTION',
          'undo 被拒但栈内有历史且无弹窗/锁定')
      }
      if (!accepted && guard !== snapshotGuardedCourtState(scoreboard)) {
        pushAnomalies(step, 'UNDO', [{
          severity: 'CRITICAL', type: 'REJECTED_UNDO_DRIFT', message: '被拒的 undo 产生状态漂移',
        }])
      }
      actionHistory.push({ step, action: 'UNDO', accepted: !!accepted })
      pushAnomalies(step, 'UNDO', auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }
    if (roll < weights.hostile + weights.undo + weights.swap) {
      const side = prng.choice(['left', 'right'])
      const swapAllowed = scoreboard.isDoublesCourt.value && scoreboard.courtPlaced.value
        && !promptActive() && !locked()
      const accepted = scoreboard.swapCourtPlayers(side)
      if (accepted) {
        matchStats.swaps += 1
        if (side === 'left') mirror.leftAnchor = 1 - mirror.leftAnchor
        else mirror.rightAnchor = 1 - mirror.rightAnchor
      } else {
        matchStats.swapRejected += 1
        if (swapAllowed) recordUnexpected('UNEXPECTED_SWAP_REJECTION',
          'swap(' + side + ') 被拒但已录入站位且无弹窗/锁定')
      }
      actionHistory.push({ step, action: `SWAP:${side}`, accepted: !!accepted })
      pushAnomalies(step, 'SWAP', auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }
    if (roll < weights.hostile + weights.undo + weights.swap + weights.manualSwitch) {
      const switchAllowed = !promptActive() && !locked()
      const accepted = scoreboard.switchSides()
      if (accepted) {
        matchStats.manualSwitches += 1
        mirrorPushHistory(mirror)
        mirrorApplySideSwitch(mirror)
      } else if (switchAllowed) {
        matchStats.switchRejected += 1
        recordUnexpected('UNEXPECTED_SWITCH_REJECTION', 'switchSides 被拒但无弹窗/锁定')
      }
      actionHistory.push({ step, action: 'SWITCH_SIDES', accepted: !!accepted })
      pushAnomalies(step, 'SWITCH_SIDES', auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }
    if (roll < weights.hostile + weights.undo + weights.swap + weights.manualSwitch + weights.retire) {
      const side = prng.choice(['left', 'right'])
      const retireAllowed = !promptActive() && !locked()
      const accepted = scoreboard.retire(side)
      if (accepted) {
        matchStats.retires += 1
        mirrorPushHistory(mirror)
        mirror.retiredSide = side
        if (side === 'left') mirror.rightGameWins = scenario.rules.gamesToWin
        else mirror.leftGameWins = scenario.rules.gamesToWin
        mirror.matchEnded = true
      } else if (retireAllowed) {
        matchStats.retireRejected += 1
        recordUnexpected('UNEXPECTED_RETIRE_REJECTION', 'retire(' + side + ') 被拒但无弹窗/锁定')
      }
      actionHistory.push({ step, action: `RETIRE:${side}`, accepted: !!accepted })
      pushAnomalies(step, 'RETIRE', auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: promptActive() }))
      continue
    }

    // SCORE：发球方得分时校验「同一发球员」连续性
    const side = prng.choice(['left', 'right'])
    const serverContinuityExpected = mirror.serveSide === side
    const wasPrompt = promptActive()
    const accepted = scoreboard.addScore(side)
    if (accepted) {
      matchStats.rallies += 1
      mirrorApplyScore(mirror, scenario.rules, side)
    } else if (!wasPrompt && !scoreboard.finalGameSideSwitchPending.value) {
      matchStats.scoreRejected += 1
      recordUnexpected('UNEXPECTED_SCORE_REJECTION',
        'addScore(' + side + ') 被拒但无弹窗/锁定/决胜局待决')
    }
    actionHistory.push({ step, action: `SCORE:${side}`, accepted: !!accepted })
    const roundtripProbe = prng.randBool(0.08)
    if (roundtripProbe) matchStats.roundtripProbes += 1
    pushAnomalies(step, 'SCORE', auditCourtInvariants(scoreboard, mirror, scenario, {
      promptActive: wasPrompt || promptActive(),
      serverContinuityExpected,
      roundtripProbe,
    }))
  }

  for (const game of mirror.gameScores) {
    const winnerScore = Math.max(game.leftScore, game.rightScore)
    if (winnerScore > scenario.rules.pointsToWin) matchStats.deuceGames += 1
    if (winnerScore >= scenario.rules.capPoint) matchStats.capHitGames += 1
    if (winnerScore > scenario.rules.capPoint) {
      recordUnexpected('SCORE_OVER_CAP',
        '局分 ' + game.leftScore + ':' + game.rightScore + ' 超过封顶 ' + scenario.rules.capPoint)
    }
  }
  const finalAudit = auditCourtInvariants(scoreboard, mirror, scenario, { promptActive: false, roundtripProbe: true })
  matchStats.roundtripProbes += 1
  pushAnomalies(step, 'FINAL', finalAudit)

  activeScope.stop()

  const hasCritical = recordedAnomalies.some((record) => record.anomalies.some((a) => a.severity === 'CRITICAL'))
  const hasSuspicious = recordedAnomalies.some((record) => record.anomalies.some((a) => a.severity === 'SUSPICIOUS'))
  return {
    matchId: scenario.matchId,
    actionHistory,
    anomalies: recordedAnomalies,
    matchStats,
    totalSteps: step,
    hasCritical,
    hasSuspicious,
    finalState: {
      leftScore: scoreboard.leftScore.value,
      rightScore: scoreboard.rightScore.value,
      leftGameWins: scoreboard.leftGameWins.value,
      rightGameWins: scoreboard.rightGameWins.value,
      matchEnded: scoreboard.matchEnded.value,
      retiredSide: scoreboard.retiredSide.value,
      placed: scoreboard.courtPlaced.value,
      lastServerName: mirror.lastServerName,
      gameScores: mirror.gameScores,
      rules: scenario.rules,
      skipPlacement: scenario.skipPlacement,
      unexpected: matchStats.unexpected,
    },
  }
}

/**
 * 批量运行：聚合 summary 并可选落盘（node:fs 动态导入，浏览器环境不落盘）。
 * 与 runBadmintonFuzzerBatch 的产物约定一致（CRITICAL 红线 / SUSPICIOUS 观察项）。
 */
export async function runCourtChaosBatch(matchCount = 10, options = {}) {
  const baseSeed = options.baseSeed || 310000000
  const startTime = Date.now()
  const results = []
  const criticalReports = []
  const suspiciousReports = []
  const stats = {
    cleanMatches: 0,
    suspiciousMatches: 0,
    criticalMatches: 0,
    totalRallies: 0,
    totalUndos: 0,
    totalSwaps: 0,
    totalManualSwitches: 0,
    totalDecidingSwitches: 0,
    totalGameEnds: 0,
    totalRetires: 0,
    totalHostileProbes: 0,
    totalHostileRejected: 0,
    totalRoundtripProbes: 0,
    skipPlacementMatches: 0,
    finishedMatches: 0,
    totalScoreRejected: 0,
    totalSwapRejected: 0,
    totalUndoRejected: 0,
    totalSwitchRejected: 0,
    totalRetireRejected: 0,
    totalCourtPendingProbes: 0,
    totalCourtPendingConfirms: 0,
    maxStepsHitMatches: 0,
    totalDeuceGames: 0,
    totalCapHitGames: 0,
    decidingSwitchTrue: 0,
    decidingSwitchFalse: 0,
    unexpectedEvents: [],
  }

  for (let i = 1; i <= matchCount; i++) {
    const prng = createPRNG(baseSeed + i * 7919)
    const scenario = generateDoublesCourtScenario(prng, i)
    if (scenario.skipPlacement) stats.skipPlacementMatches += 1

    const result = await simulateDoublesCourtMatch(scenario, prng, options)
    results.push(result)

    stats.totalRallies += result.matchStats.rallies
    stats.totalUndos += result.matchStats.undos
    stats.totalSwaps += result.matchStats.swaps
    stats.totalManualSwitches += result.matchStats.manualSwitches
    stats.totalDecidingSwitches += result.matchStats.decidingSwitches
    stats.totalGameEnds += result.matchStats.gameEnds
    stats.totalRetires += result.matchStats.retires
    stats.totalHostileProbes += result.matchStats.hostileProbes
    stats.totalHostileRejected += result.matchStats.hostileRejected
    stats.totalRoundtripProbes += result.matchStats.roundtripProbes
    stats.totalScoreRejected += result.matchStats.scoreRejected
    stats.totalSwapRejected += result.matchStats.swapRejected
    stats.totalUndoRejected += result.matchStats.undoRejected
    stats.totalSwitchRejected += result.matchStats.switchRejected
    stats.totalRetireRejected += result.matchStats.retireRejected
    stats.totalCourtPendingProbes += result.matchStats.courtPendingProbes
    stats.totalCourtPendingConfirms += result.matchStats.courtPendingConfirms
    stats.totalDeuceGames += result.matchStats.deuceGames
    stats.totalCapHitGames += result.matchStats.capHitGames
    stats.decidingSwitchTrue += result.matchStats.decidingSwitchTrue
    stats.decidingSwitchFalse += result.matchStats.decidingSwitchFalse
    if (result.matchStats.maxStepsHit) stats.maxStepsHitMatches += 1
    if (result.finalState.matchEnded) stats.finishedMatches += 1
    if (result.matchStats.unexpected.length > 0) {
      stats.unexpectedEvents.push({
        matchId: result.matchId,
        events: result.matchStats.unexpected.slice(0, 10),
      })
    }

    // 批级断言：恶意探针必须 100% 被拒绝
    const probeMismatch = result.matchStats.hostileProbes !== result.matchStats.hostileRejected
    if (probeMismatch) {
      criticalReports.push({
        ...result,
        anomalies: [...result.anomalies, {
          step: result.totalSteps,
          action: 'BATCH_HOSTILE_PROBE_CHECK',
          anomalies: [{
            severity: 'CRITICAL',
            type: 'HOSTILE_PROBE_NOT_REJECTED',
            message: `hostileProbes=${result.matchStats.hostileProbes} hostileRejected=${result.matchStats.hostileRejected}`,
          }],
        }],
      })
    } else if (result.hasCritical) {
      criticalReports.push(result)
    } else if (result.hasSuspicious) {
      suspiciousReports.push(result)
    } else {
      stats.cleanMatches += 1
    }

    if (options.onProgress && i % Math.max(1, Math.floor(matchCount / 10)) === 0) {
      options.onProgress({ current: i, total: matchCount, critical: criticalReports.length, suspicious: suspiciousReports.length })
    }
  }

  stats.criticalMatches = criticalReports.length
  stats.suspiciousMatches = suspiciousReports.length
  const summary = {
    kind: 'badminton-court-chaos',
    matchCount,
    baseSeed,
    durationMs: Date.now() - startTime,
    stats,
    criticalReports: criticalReports.slice(0, 20),
    suspiciousReports: suspiciousReports.slice(0, 20),
  }
  if (options.includeMatchDetails) {
    summary.matchDetails = results.map((r) => ({
      matchId: r.matchId,
      totalSteps: r.totalSteps,
      matchStats: r.matchStats,
      finalState: r.finalState,
    }))
  }

  if (options.outputDir) {
    try {
      const fs = await import('node:fs')
      const path = await import('node:path')
      fs.mkdirSync(options.outputDir, { recursive: true })
      fs.writeFileSync(
        path.join(options.outputDir, 'court-chaos-summary.json'),
        JSON.stringify(summary, null, 2),
        'utf8',
      )
    } catch (error) {
      summary.journalError = String(error)
    }
  }
  return summary
}
