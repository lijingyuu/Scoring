import { vi } from 'vitest'
import { effectScope } from 'vue'
import {
  cloneCourt,
  cloneLiberoRuntime,
  cloneLiberoSetup,
  createEmptyLiberoRuntime,
  createEmptyMatchState,
  normalizeMatchState,
  saveMatchState,
  toggleSide,
} from './match-state'
import { useScoreboard } from './composables/useScoreboard'

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
 * 随机生成比赛规则与双队花名册大名单
 */
export function generateMatchScenario(prng, matchIndex = 1) {
  const bestOf = prng.choice([3, 5])
  const gamesToWin = Math.ceil(bestOf / 2)
  const pointsToWin = prng.choice([25, 21])
  const decidingPointsToWin = 15
  const enableDeuce = prng.randBool(0.9) // 90% 开启 Deuce
  const capPoint = prng.choice([30, 99]) // 30 分封顶或 99 无封顶

  function generateTeam(prefix, teamName) {
    const memberCount = prng.randInt(9, 12)
    const members = []
    for (let i = 1; i <= memberCount; i++) {
      members.push({
        id: `${prefix}_${i}`,
        name: `${teamName}-${i}号`,
        jerseyNumber: i,
        captain: i === 1, // 1 号固定为队长
        libero: i === 7 || i === 8, // 7、8 号固定为自由人
      })
    }
    return {
      id: `team_${prefix}`,
      name: teamName,
      members,
    }
  }

  const leftTeam = generateTeam('A', `战队A_${matchIndex}`)
  const rightTeam = generateTeam('B', `战队B_${matchIndex}`)

  // 生成合法首发阵容（前6名非自由人，即 1~6 号）
  // 站位对应 slotIndex 0~5 (分别为 4, 3, 2, 5, 6, 1 号位)
  // 选 2 号位(slot 2) 和 5 号位(slot 3) 作为副攻对角
  const leftStartingCourt = ['A_4', 'A_3', 'A_2', 'A_5', 'A_6', 'A_1']
  const rightStartingCourt = ['B_4', 'B_3', 'B_2', 'B_5', 'B_6', 'B_1']

  const initialServeSide = prng.choice(['left', 'right'])

  return {
    matchId: `match_fuzz_${matchIndex}_${prng.seed}`,
    tournamentId: `tournament_${matchIndex}`,
    rules: {
      bestOf,
      gamesToWin,
      pointsToWin,
      decidingPointsToWin,
      enableDeuce,
      capPoint,
    },
    leftTeam,
    rightTeam,
    initialLineup: {
      leftCourt: leftStartingCourt,
      rightCourt: rightStartingCourt,
      leftLiberoSetup: {
        pairIndexes: [2, 3], // 副攻对角
        libero1Id: 'A_7',
        libero2Id: prng.randBool(0.3) ? 'A_8' : '',
      },
      rightLiberoSetup: {
        pairIndexes: [2, 3],
        libero1Id: 'B_7',
        libero2Id: prng.randBool(0.3) ? 'B_8' : '',
      },
      serveSide: initialServeSide,
    },
  }
}

/**
 * 核心排球业务不变式断言与异常嗅探器
 * 检查当前状态是否违反规则或出现悬空/漂移
 */
export function auditStateInvariants(state, scenario, context = {}) {
  const anomalies = []

  const { leftTeam, rightTeam, rules } = scenario
  const leftCourt = state.leftCourt || []
  const rightCourt = state.rightCourt || []
  // 决胜局换边后 screenLeftParticipantSide 翻转，屏幕左右两侧对应的参赛队随之交换，
  // 自由人集合必须按当前屏侧解析，否则换边后前排自由人会被漏检
  const flipped = state.screenLeftParticipantSide === 'right'
  const leftSideTeam = flipped ? rightTeam : leftTeam
  const rightSideTeam = flipped ? leftTeam : rightTeam
  const leftLiberoIds = new Set(leftSideTeam.members.filter((m) => m.libero).map((m) => m.id))
  const rightLiberoIds = new Set(rightSideTeam.members.filter((m) => m.libero).map((m) => m.id))

  // ---- Invariant 1: 场上球员人数与唯一性 ----
  function checkCourtIntegrity(court, team, sideName) {
    if (court.length !== 6) {
      anomalies.push({
        severity: 'CRITICAL',
        type: 'INVALID_COURT_SIZE',
        message: `${sideName} 场上槽位数量异常: 期望 6，实际 ${court.length}`,
      })
    }
    const seen = new Set()
    for (let i = 0; i < 6; i++) {
      const pid = court[i]
      if (!pid) {
        anomalies.push({
          severity: 'CRITICAL',
          type: 'EMPTY_COURT_SLOT',
          message: `${sideName} ${i} 号槽位球员为空`,
        })
      } else if (seen.has(pid)) {
        anomalies.push({
          severity: 'CRITICAL',
          type: 'DUPLICATE_ON_COURT_PLAYER',
          message: `${sideName} 场上存在重复球员: ${pid}`,
        })
      }
      seen.add(pid)
    }
  }
  checkCourtIntegrity(leftCourt, leftTeam, '左队')
  checkCourtIntegrity(rightCourt, rightTeam, '右队')

  // ---- Invariant 2: 自由人规则硬不变式 ----
  // 前排槽位: slot 0 (4号位), slot 1 (3号位), slot 2 (2号位)
  // 自由人绝对不能出现在前排！
  function checkLiberoPositions(court, liberoIds, sideName) {
    for (let slot = 0; slot <= 2; slot++) {
      const pid = court[slot]
      if (liberoIds.has(pid)) {
        anomalies.push({
          severity: 'CRITICAL',
          type: 'LIBERO_IN_FRONT_ROW',
          message: `${sideName} 自由人 ${pid} 出现在前排槽位 ${slot} (规则严禁自由人进入前排)`,
        })
      }
    }
  }
  checkLiberoPositions(leftCourt, leftLiberoIds, '左队')
  checkLiberoPositions(rightCourt, rightLiberoIds, '右队')

  // 自由人不能是队长
  if (leftLiberoIds.has(state.leftCaptainMemberId)) {
    anomalies.push({
      severity: 'CRITICAL',
      type: 'LIBERO_AS_CAPTAIN',
      message: `左队将自由人 ${state.leftCaptainMemberId} 设为队长 (排球规则不允许自由人担任队长)`,
    })
  }
  if (rightLiberoIds.has(state.rightCaptainMemberId)) {
    anomalies.push({
      severity: 'CRITICAL',
      type: 'LIBERO_AS_CAPTAIN',
      message: `右队将自由人 ${state.rightCaptainMemberId} 设为队长 (排球规则不允许自由人担任队长)`,
    })
  }

  // ---- Invariant 3: 比分与终局断言 ----
  const leftScore = state.leftScore
  const rightScore = state.rightScore
  if (leftScore < 0 || rightScore < 0) {
    anomalies.push({
      severity: 'CRITICAL',
      type: 'NEGATIVE_SCORE',
      message: `比分出现负数: ${leftScore}:${rightScore}`,
    })
  }

  // 封顶分判定检查
  if (rules.capPoint && rules.capPoint < 99) {
    if (leftScore > rules.capPoint || rightScore > rules.capPoint) {
      anomalies.push({
        severity: 'CRITICAL',
        type: 'SCORE_EXCEEDS_CAP',
        message: `单局比分 ${leftScore}:${rightScore} 超过封顶分 ${rules.capPoint}`,
      })
    }
  }

  // 胜局数不能超标
  if (state.leftGameWins > rules.gamesToWin || state.rightGameWins > rules.gamesToWin) {
    anomalies.push({
      severity: 'CRITICAL',
      type: 'GAME_WINS_OVERFLOW',
      message: `胜局数溢出: 左胜 ${state.leftGameWins}, 右胜 ${state.rightGameWins}, gamesToWin=${rules.gamesToWin}`,
    })
  }

  // ---- Invariant 4: 历史栈深度检查 ----
  const stackDepth = state.historyStackDepth !== undefined ? state.historyStackDepth : (state.historyStack?.length || 0)
  if (stackDepth > 40) {
    anomalies.push({
      severity: 'SUSPICIOUS',
      type: 'HISTORY_STACK_OVERFLOW',
      message: `撤销历史栈深度 ${stackDepth} 超过上限 40`,
    })
  }

  return anomalies
}

/**
 * 从 useScoreboard 的响应式引用中提取轻量状态快照（防 OOM）
 */
export function extractScoreboardSnapshot(sb) {
  return {
    leftScore: sb.leftScore.value,
    rightScore: sb.rightScore.value,
    leftGameWins: sb.leftGameWins.value,
    rightGameWins: sb.rightGameWins.value,
    currentGameNo: sb.currentGameNo.value,
    gameScores: sb.gameScores.value ? sb.gameScores.value.map((g) => ({ ...g })) : [],
    serveSide: sb.serveSide.value,
    screenLeftParticipantSide: sb.screenLeftParticipantSide?.value || 'left',
    leftTimeouts: sb.leftTimeouts.value,
    rightTimeouts: sb.rightTimeouts.value,
    leftCourt: sb.leftCourt.value ? [...sb.leftCourt.value] : [],
    rightCourt: sb.rightCourt.value ? [...sb.rightCourt.value] : [],
    leftCaptainMemberId: sb.captainCandidateMemberId?.value || '',
    historyStackDepth: sb.historyStack.value ? sb.historyStack.value.length : 0,
    retiredSide: sb.retiredSide.value,
    matchEnded: sb.matchEnded.value,
    winnerName: sb.winnerName.value,
    lineupReady: sb.lineupReady.value,
    finalGameSideSwitchPending: sb.finalGameSideSwitchPending.value,
    isTransitioningToNextGame: sb.isTransitioningToNextGame.value,
  }
}

/**
 * 模拟执行一场完整的无头排球比赛
 */
export async function simulateVolleyballMatch(scenario, prng, options = {}) {
  const maxTotalActions = options.maxTotalActions || 200

  const { matchId, tournamentId, rules, leftTeam, rightTeam, initialLineup } = scenario

  // 构造初始缓存
  const cachedState = normalizeMatchState({
    tournamentId,
    matchId,
    screenLeftParticipantSide: 'left',
    leftScore: 0,
    rightScore: 0,
    leftGameWins: 0,
    rightGameWins: 0,
    currentGameNo: 1,
    gameScores: [],
    serveSide: initialLineup.serveSide,
    currentGameStartServeSide: initialLineup.serveSide,
    leftTimeouts: 2,
    rightTimeouts: 2,
    leftCourt: cloneCourt(initialLineup.leftCourt),
    rightCourt: cloneCourt(initialLineup.rightCourt),
    baseLeftCourt: cloneCourt(initialLineup.leftCourt),
    baseRightCourt: cloneCourt(initialLineup.rightCourt),
    leftLiberoSetup: cloneLiberoSetup(initialLineup.leftLiberoSetup),
    rightLiberoSetup: cloneLiberoSetup(initialLineup.rightLiberoSetup),
    leftLiberoRuntime: createEmptyLiberoRuntime(),
    rightLiberoRuntime: createEmptyLiberoRuntime(),
    leftCaptainMemberId: 'A_1',
    rightCaptainMemberId: 'B_1',
    lineupReady: true,
    matchEnded: false,
    retiredSide: '',
    winnerName: '',
    historyStack: [],
    matchEvents: [],
    nextEventSeq: 1,
    lastSyncedEventSeq: 0,
  })

  // 存入 storage 供 useScoreboard 加载
  saveMatchState(matchId, cachedState)

  // 准备 useScoreboard
  // 用独立 effectScope 收集本场全部 computed/watch/响应式副作用；
  // 赛后 scope.stop() 整体释放，否则无组件实例挂载的副作用会跨场次永久残留，
  // 数百场连续仿真即耗尽 worker 堆内存（Ineffective mark-compacts OOM）
  const matchScope = effectScope()
  const sb = matchScope.run(() => useScoreboard())

  // 注入 match record 到 request mock
  const { request } = await import('@/utils/request')
  request.mockImplementation((url) => {
    if (url.includes('/record')) {
      return Promise.resolve({
        tournamentId,
        matchId,
        ...rules,
        left: leftTeam,
        right: rightTeam,
        gameScores: [],
        events: [],
      })
    }
    return Promise.resolve({})
  })

  // 调用 onLoad 触发完整的进入与加载流程
  const { getRegisteredOnLoadHandler } = await import('./fuzzer-env')
  const onLoadHandler = getRegisteredOnLoadHandler()
  if (onLoadHandler) {
    await onLoadHandler({
      tournamentId,
      matchId,
      leftName: leftTeam.name,
      rightName: rightTeam.name,
      bestOf: String(rules.bestOf),
      gamesToWin: String(rules.gamesToWin),
      pointsToWin: String(rules.pointsToWin),
      decidingPointsToWin: String(rules.decidingPointsToWin),
      enableDeuce: rules.enableDeuce ? '1' : '0',
      capPoint: String(rules.capPoint),
      lockToken: 'mock-token',
    })
  } else {
    sb.pageQuery = { value: { tournamentId, matchId } }
    await sb.loadMatch()
  }

  // 快进一次让初始自由人就位
  vi.advanceTimersByTime(300)

  const actionHistory = []
  const recordedAnomalies = []
  let totalActionCount = 0

  // 检查初始状态不变式
  const initialSnapshot = extractScoreboardSnapshot(sb)
  const initialAnomalies = auditStateInvariants(initialSnapshot, scenario, { step: 0, phase: 'initial' })
  if (initialAnomalies.length) {
    recordedAnomalies.push({
      step: 0,
      action: 'INIT',
      anomalies: initialAnomalies,
      stateSnapshot: initialSnapshot,
    })
  }

  // 推进小局间轮换的辅助函数
  function handleBetweenGameTransition() {
    if (sb.matchEnded.value) return
    vi.clearAllTimers()

    // 模拟确认下一局轮次就绪
    sb.lineupReady.value = true
    sb.isTransitioningToNextGame.value = false
    sb.leftScore.value = 0
    sb.rightScore.value = 0
    sb.leftTimeouts.value = 2
    sb.rightTimeouts.value = 2
    if (sb.finalGameSideSwitchPending) {
      sb.finalGameSideSwitchPending.value = false
    }
  }

  // 比赛主循环
  while (!sb.matchEnded.value && totalActionCount < maxTotalActions) {
    totalActionCount++

    // 1. 决胜局 8 分换边弹窗优先处理
    if (sb.finalGameSideSwitchPending.value) {
      actionHistory.push({ step: totalActionCount, action: 'CONFIRM_SIDE_SWITCH', score: `${sb.leftScore.value}:${sb.rightScore.value}` })
      sb.confirmDisplaySideSwitch()
      vi.advanceTimersByTime(200)

      const switchSnapshot = extractScoreboardSnapshot(sb)
      const switchAnomalies = auditStateInvariants(switchSnapshot, scenario, { step: totalActionCount, action: 'CONFIRM_SIDE_SWITCH' })
      if (switchAnomalies.length) {
        recordedAnomalies.push({ step: totalActionCount, action: 'CONFIRM_SIDE_SWITCH', anomalies: switchAnomalies, stateSnapshot: switchSnapshot })
      }
      continue
    }

    // 2. 队长确认弹窗优先处理
    if (sb.isCaptainPromptActive.value) {
      actionHistory.push({ step: totalActionCount, action: 'CONFIRM_CAPTAIN', side: sb.captainPromptSide.value })
      sb.confirmCaptainSelection()
      vi.advanceTimersByTime(200)
      continue
    }

    // 3. 局间转换处理
    if (sb.isTransitioningToNextGame.value) {
      handleBetweenGameTransition()
      continue
    }

    // 4. Monte Carlo 动作生成
    const roll = prng.next()

    if (roll < 0.70) {
      // 70% 概率: 得分 (SCORE)
      const scoringSide = prng.choice(['left', 'right'])
      const scoreBefore = [sb.leftScore.value, sb.rightScore.value]
      const serveBefore = sb.serveSide.value

      sb.addScore(scoringSide)
      vi.advanceTimersByTime(200) // 越过 150ms 节流限制

      const scoreAfter = [sb.leftScore.value, sb.rightScore.value]
      const actionItem = {
        step: totalActionCount,
        action: 'SCORE',
        side: scoringSide,
        scoreBefore,
        scoreAfter,
        serveBefore,
        serveAfter: sb.serveSide.value,
      }
      actionHistory.push(actionItem)

      // 断言审计
      const scoreSnapshot = extractScoreboardSnapshot(sb)
      const stepAnomalies = auditStateInvariants(scoreSnapshot, scenario, actionItem)
      if (stepAnomalies.length) {
        recordedAnomalies.push({ step: totalActionCount, action: 'SCORE', anomalies: stepAnomalies, stateSnapshot: scoreSnapshot })
      }
    } else if (roll < 0.82) {
      // 12% 概率: 撤销 (UNDO)
      if (sb.historyStack.value && sb.historyStack.value.length > 0) {
        const undoTimes = prng.randBool(0.3) ? 2 : 1 // 30% 概率连续撤销 2 次
        for (let u = 0; u < undoTimes; u++) {
          if (!sb.historyStack.value.length) break
          sb.undo()
          vi.advanceTimersByTime(250)
        }
        actionHistory.push({
          step: totalActionCount,
          action: 'UNDO',
          times: undoTimes,
          scoreNow: `${sb.leftScore.value}:${sb.rightScore.value}`,
        })

        const undoSnapshot = extractScoreboardSnapshot(sb)
        const stepAnomalies = auditStateInvariants(undoSnapshot, scenario, { step: totalActionCount, action: 'UNDO' })
        if (stepAnomalies.length) {
          recordedAnomalies.push({ step: totalActionCount, action: 'UNDO', anomalies: stepAnomalies, stateSnapshot: undoSnapshot })
        }
      }
    } else if (roll < 0.94) {
      // 12% 概率: 常规换人 (SUBSTITUTION)
      const subSide = prng.choice(['left', 'right'])
      const currentCourt = subSide === 'left' ? sb.leftCourt.value : sb.rightCourt.value
      // 换边后屏幕左右对应的参赛队交换，必须按当前屏侧解析花名册，
      // 否则会向状态机送入异队球员 ID（真实裁判 UI 不可能产生该输入）
      const flipped = sb.screenLeftParticipantSide?.value === 'right'
      const team = (subSide === 'left') !== flipped ? leftTeam : rightTeam

      // 找替补球员 (符合 FIVB 规则)
      const benchPlayers = team.members.filter((m) => sb.canSelectBenchPlayer(subSide, m.id))
      if (benchPlayers.length > 0) {
        const inPlayer = prng.choice(benchPlayers)
        // 挑选可以换入该替补的合法槽位 (符合 FIVB 15.6 对位通道锁定，支持复合换人)
        const eligibleSlots = []
        for (let s = 0; s < 6; s++) {
          if (sb.canPlayerSubstituteSlot(subSide, inPlayer.id, s)) {
            eligibleSlots.push(s)
          }
        }

        if (eligibleSlots.length > 0) {
          const targetSlot = prng.choice(eligibleSlots)
          const outPlayerId = currentCourt[targetSlot]

          sb.selectBench(subSide, inPlayer.id)
          sb.handleCourtSlot(subSide, targetSlot)
          vi.advanceTimersByTime(200)

          actionHistory.push({
            step: totalActionCount,
            action: 'SUBSTITUTION',
            side: subSide,
            in: inPlayer.id,
            out: outPlayerId,
            slot: targetSlot,
          })

          const subSnapshot = extractScoreboardSnapshot(sb)
          const stepAnomalies = auditStateInvariants(subSnapshot, scenario, { step: totalActionCount, action: 'SUBSTITUTION' })
          if (stepAnomalies.length) {
            recordedAnomalies.push({ step: totalActionCount, action: 'SUBSTITUTION', anomalies: stepAnomalies, stateSnapshot: subSnapshot })
          }
        }
      }
    } else {
      // 6% 概率: 暂停 (TIMEOUT)
      const timeoutSide = prng.choice(['left', 'right'])
      const remaining = timeoutSide === 'left' ? sb.leftTimeouts.value : sb.rightTimeouts.value
      if (remaining > 0) {
        sb.openTimeoutSheet()
        vi.advanceTimersByTime(100)
        actionHistory.push({ step: totalActionCount, action: 'TIMEOUT', side: timeoutSide })
      }
    }
  }

  // 比赛结束结算检查
  const finalSnapshot = extractScoreboardSnapshot(sb)
  const finalAnomalies = auditStateInvariants(finalSnapshot, scenario, { step: totalActionCount, phase: 'final' })
  if (finalAnomalies.length) {
    recordedAnomalies.push({ step: totalActionCount, action: 'FINAL_AUDIT', anomalies: finalAnomalies, stateSnapshot: finalSnapshot })
  }

  // 彻底清理本场比赛的所有悬挂定时器、缓存、mock 调用记录与响应式副作用，防止跨比赛内存泄漏
  // 关键：uni.setStorageSync 等是 vi.fn mock，会永久保留每次调用的实参（全量状态 +
  // 40 条历史快照，约 3MB/次），不清的话单场 200 次保存即累积约 600MB，多场必然 OOM
  vi.clearAllTimers()
  vi.clearAllMocks()
  globalThis.uni.clearStorageSync()
  matchScope.stop()
  // 每场强制 major GC：本仿真每动作产生约数 MB 浮动垃圾（全量状态序列化、
  // 历史快照深拷贝），V8 惰性回收会让堆在线性涨满前触发 OOM；
  // 需要 NODE_OPTIONS=--expose-gc 注入 gc 能力（run-fuzz-batch.mjs 已注入）
  if (typeof globalThis.gc === 'function') globalThis.gc()

  return {
    matchId,
    seed: prng.seed,
    rules,
    winner: sb.winnerName.value,
    leftWins: sb.leftGameWins.value,
    rightWins: sb.rightGameWins.value,
    gameScores: sb.gameScores.value,
    totalActions: totalActionCount,
    actionHistory,
    anomalies: recordedAnomalies,
    hasCritical: recordedAnomalies.some((a) => a.anomalies.some((item) => item.severity === 'CRITICAL')),
    hasSuspicious: recordedAnomalies.some((a) => a.anomalies.some((item) => item.severity === 'SUSPICIOUS')),
  }
}

/**
 * 批量执行多场排球混沌比赛并统计输出
 */
export async function runFuzzerBatch(matchCount = 10, options = {}) {
  const baseSeed = options.baseSeed || Math.floor(Math.random() * 1000000)
  const startTime = Date.now()

  const results = []
  const criticalReports = []
  const suspiciousReports = []

  let totalRallies = 0
  let totalSubstitutions = 0
  let totalUndos = 0

  for (let i = 1; i <= matchCount; i++) {
    const matchSeed = baseSeed + i * 9973
    const prng = createPRNG(matchSeed)
    const scenario = generateMatchScenario(prng, i)

    if (i % 5 === 0 || i === 1) {
      console.log(`  [Batch] Simulating match ${i}/${matchCount}...`)
    }

    const matchResult = await simulateVolleyballMatch(scenario, prng, options)
    results.push(matchResult)

    // 动作统计
    for (const a of matchResult.actionHistory) {
      if (a.action === 'SCORE') totalRallies++
      if (a.action === 'SUBSTITUTION') totalSubstitutions++
      if (a.action === 'UNDO') totalUndos++
    }

    if (matchResult.hasCritical) {
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

  const summary = {
    baseSeed,
    matchCount,
    durationMs,
    stats: {
      totalRallies,
      totalSubstitutions,
      totalUndos,
      criticalMatches: criticalReports.length,
      suspiciousMatches: suspiciousReports.length,
      cleanMatches: matchCount - criticalReports.length - suspiciousReports.length,
    },
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

  // 自动将报告落盘到 outputs/fuzz-volleyball 供 Agent 审查
  try {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const outDir = path.resolve(process.cwd(), '../outputs/fuzz-volleyball')
    if (!fs.existsSync(outDir)) {
      fs.mkdirSync(outDir, { recursive: true })
    }
    fs.writeFileSync(path.join(outDir, 'fuzz-summary.json'), JSON.stringify(summary, null, 2), 'utf8')
    if (criticalReports.length > 0 || suspiciousReports.length > 0) {
      fs.writeFileSync(
        path.join(outDir, 'fuzz-anomalies.json'),
        JSON.stringify({ critical: criticalReports, suspicious: suspiciousReports }, null, 2),
        'utf8'
      )
    }
  } catch (_) {
    // 忽略文件系统错误
  }

  return {
    ...summary,
    criticalReports,
    suspiciousReports,
  }
}
