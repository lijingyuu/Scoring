import { ref, reactive, computed } from 'vue'

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
 * 纯无头羽毛球记分板状态机驱动器
 * 严格遵照 pages/scoreboard/index.vue 的状态模型与业务逻辑
 */
export function createBadmintonScoreboard(scenario, env = {}) {
  const STORAGE_KEY = 'badminton_scoreboard_state'
  const matchId = ref(scenario.matchId || '')
  const tournamentId = ref(scenario.tournamentId || '')
  const divisionId = ref(scenario.divisionId || '')

  const leftTeam = ref(scenario.leftTeam || '左队')
  const rightTeam = ref(scenario.rightTeam || '右队')
  const leftScore = ref(0)
  const rightScore = ref(0)
  const leftGameWins = ref(0)
  const rightGameWins = ref(0)
  const currentGameNo = ref(1)
  const gameScores = ref([])
  const serveSide = ref(scenario.initialServeSide || 'left')
  const historyStack = ref([])
  const isGodMode = ref(false)
  const retiredSide = ref('')
  const matchEnded = ref(false)
  const matchStartTime = ref(Date.now())
  const matchDuration = ref('0分0秒')
  const winnerName = ref('')
  const sidesSwapped = ref(false)
  const finalGameSideSwitchPending = ref(false)
  const finalGameSideSwitchHandled = ref(false)
  const gameEndPromptPending = ref(false)
  const gameEndPromptHandled = ref(false)
  const isReadOnly = ref(false)

  const matchRules = ref({
    bestOf: scenario.rules?.bestOf || 3,
    gamesToWin: scenario.rules?.gamesToWin || 2,
    pointsToWin: scenario.rules?.pointsToWin || 21,
    enableDeuce: scenario.rules?.enableDeuce !== false,
    capPoint: scenario.rules?.capPoint || 30,
  })

  // 依赖存储接口（Mock 或内存 Storage）
  const storage = env.storage || (globalThis.uni
    ? {
        getStorageSync: (k) => globalThis.uni.getStorageSync(k),
        setStorageSync: (k, v) => globalThis.uni.setStorageSync(k, v),
        removeStorageSync: (k) => globalThis.uni.removeStorageSync(k),
      }
    : {
        _raw: new Map(),
        getStorageSync(k) { return this._raw.get(k) || '' },
        setStorageSync(k, v) { this._raw.set(k, v) },
        removeStorageSync(k) { this._raw.delete(k) },
      })

  function storageKey() {
    return matchId.value ? STORAGE_KEY + '_' + matchId.value : STORAGE_KEY
  }

  const isLocked = computed(() => !!retiredSide.value || matchEnded.value)
  const rulesLocked = computed(() => isLocked.value || leftScore.value !== 0 || rightScore.value !== 0 || gameScores.value.length > 0)
  const isBestOfThreeMatch = computed(() => Number(matchRules.value.bestOf || 3) === 3 && Number(matchRules.value.gamesToWin || 2) === 2)
  const finalGameSideSwitchThreshold = computed(() => Math.ceil(matchRules.value.pointsToWin / 2))

  function isFinalGameSideSwitchGame() {
    return !isLocked.value
      && Number(currentGameNo.value) === Number(matchRules.value.bestOf)
      && Number(finalGameSideSwitchThreshold.value) > 0
  }

  function needsFinalGameSideSwitch() {
    return isFinalGameSideSwitchGame()
      && !finalGameSideSwitchHandled.value
      && Math.max(Number(leftScore.value || 0), Number(rightScore.value || 0)) >= finalGameSideSwitchThreshold.value
  }

  const isFinalGameSideSwitchPromptActive = computed(() => finalGameSideSwitchPending.value || needsFinalGameSideSwitch())
  const isGameEndPromptActive = computed(() => gameEndPromptPending.value)
  const isPromptActive = computed(() => isFinalGameSideSwitchPromptActive.value || isGameEndPromptActive.value)

  function shouldPromptFinalGameSideSwitch(score) {
    return isFinalGameSideSwitchGame()
      && !finalGameSideSwitchHandled.value
      && Number(score) >= finalGameSideSwitchThreshold.value
  }

  function shouldAutoSwitchBetweenGames(nextGameNo) {
    return isBestOfThreeMatch.value && (Number(nextGameNo) === 2 || Number(nextGameNo) === 3)
  }

  function checkWinCondition(myScore, opponentScore) {
    if (myScore >= matchRules.value.capPoint) return true
    if (myScore >= matchRules.value.pointsToWin) {
      if (!matchRules.value.enableDeuce) return true
      return myScore - opponentScore >= 2
    }
    return false
  }

  function buildSnapshot() {
    return {
      leftTeam: leftTeam.value,
      rightTeam: rightTeam.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      leftGameWins: leftGameWins.value,
      rightGameWins: rightGameWins.value,
      currentGameNo: currentGameNo.value,
      gameScores: gameScores.value.map(game => ({ ...game })),
      serveSide: serveSide.value,
      retiredSide: retiredSide.value,
      matchEnded: matchEnded.value,
      matchStartTime: matchStartTime.value,
      matchDuration: matchDuration.value,
      winnerName: winnerName.value,
      sidesSwapped: sidesSwapped.value,
      finalGameSideSwitchPending: finalGameSideSwitchPending.value,
      finalGameSideSwitchHandled: finalGameSideSwitchHandled.value,
      gameEndPromptPending: gameEndPromptPending.value,
      gameEndPromptHandled: gameEndPromptHandled.value,
      matchRules: { ...matchRules.value },
    }
  }

  function applySnapshot(snapshot) {
    leftTeam.value = snapshot.leftTeam
    rightTeam.value = snapshot.rightTeam
    leftScore.value = Number(snapshot.leftScore || 0)
    rightScore.value = Number(snapshot.rightScore || 0)
    leftGameWins.value = Number(snapshot.leftGameWins || 0)
    rightGameWins.value = Number(snapshot.rightGameWins || 0)
    currentGameNo.value = Number(snapshot.currentGameNo || 1)
    gameScores.value = Array.isArray(snapshot.gameScores) ? snapshot.gameScores : []
    serveSide.value = snapshot.serveSide === 'right' ? 'right' : 'left'
    retiredSide.value = snapshot.retiredSide || ''
    matchEnded.value = !!snapshot.matchEnded
    matchStartTime.value = Number(snapshot.matchStartTime || Date.now())
    matchDuration.value = snapshot.matchDuration || '0分0秒'
    winnerName.value = snapshot.winnerName || ''
    sidesSwapped.value = !!snapshot.sidesSwapped
    finalGameSideSwitchPending.value = !!snapshot.finalGameSideSwitchPending
    finalGameSideSwitchHandled.value = !!snapshot.finalGameSideSwitchHandled
    gameEndPromptPending.value = !!snapshot.gameEndPromptPending
    gameEndPromptHandled.value = !!snapshot.gameEndPromptHandled
    if (snapshot.matchRules) {
      matchRules.value = { ...snapshot.matchRules }
    }
  }

  function pushHistory() {
    historyStack.value.push(buildSnapshot())
  }

  function saveStateToStorage() {
    try {
      storage.setStorageSync(storageKey(), {
        ...buildSnapshot(),
        historyStack: historyStack.value,
        isGodMode: isGodMode.value,
      })
    } catch (_) {
      // noop
    }
  }

  function restoreStateFromStorage() {
    try {
      const cache = storage.getStorageSync(storageKey())
      if (!cache || typeof cache !== 'object') return false
      applySnapshot(cache)
      historyStack.value = Array.isArray(cache.historyStack) ? cache.historyStack : []
      isGodMode.value = !!cache.isGodMode
      return true
    } catch (_) {
      return false
    }
  }

  function applySideSwitch() {
    const teamName = leftTeam.value
    leftTeam.value = rightTeam.value
    rightTeam.value = teamName

    const score = leftScore.value
    leftScore.value = rightScore.value
    rightScore.value = score

    const wins = leftGameWins.value
    leftGameWins.value = rightGameWins.value
    rightGameWins.value = wins

    gameScores.value = gameScores.value.map(game => ({
      gameNo: game.gameNo,
      leftScore: game.rightScore,
      rightScore: game.leftScore,
      winnerSide: game.winnerSide === 'left' ? 'right' : 'left',
    }))

    serveSide.value = serveSide.value === 'left' ? 'right' : 'left'
    sidesSwapped.value = !sidesSwapped.value
  }

  function finishGame(winnerSide) {
    const game = {
      gameNo: currentGameNo.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      winnerSide,
    }
    gameScores.value.push(game)

    if (winnerSide === 'left') {
      leftGameWins.value += 1
    } else {
      rightGameWins.value += 1
    }

    if (leftGameWins.value >= matchRules.value.gamesToWin || rightGameWins.value >= matchRules.value.gamesToWin) {
      winnerName.value = leftGameWins.value > rightGameWins.value ? leftTeam.value : rightTeam.value
      matchEnded.value = true
      saveStateToStorage()
      return
    }

    gameEndPromptPending.value = true
    gameEndPromptHandled.value = false
    saveStateToStorage()
  }

  function addScore(side) {
    if (side !== 'left' && side !== 'right') return false
    if (isReadOnly.value || isLocked.value || isGameEndPromptActive.value) return false
    if (needsFinalGameSideSwitch()) {
      finalGameSideSwitchPending.value = true
      saveStateToStorage()
      return false
    }
    if (finalGameSideSwitchPending.value) return false

    pushHistory()
    if (side === 'left') {
      leftScore.value += 1
    } else {
      rightScore.value += 1
    }
    serveSide.value = side

    const myScore = side === 'left' ? leftScore.value : rightScore.value
    const oppScore = side === 'left' ? rightScore.value : leftScore.value

    if (shouldPromptFinalGameSideSwitch(myScore)) {
      finalGameSideSwitchPending.value = true
      saveStateToStorage()
      return true
    }

    if (!isGodMode.value && checkWinCondition(myScore, oppScore)) {
      finishGame(side)
      return true
    }

    saveStateToStorage()
    return true
  }

  function adjustScore(side, delta) {
    if (side !== 'left' && side !== 'right') return false
    if (isReadOnly.value || !isGodMode.value || isLocked.value || isPromptActive.value) return false
    if (needsFinalGameSideSwitch()) {
      finalGameSideSwitchPending.value = true
      saveStateToStorage()
      return false
    }
    pushHistory()

    if (side === 'left') {
      leftScore.value = Math.max(0, leftScore.value + delta)
    } else {
      rightScore.value = Math.max(0, rightScore.value + delta)
    }
    if (delta > 0) {
      serveSide.value = side
    }
    if (delta > 0 && shouldPromptFinalGameSideSwitch(side === 'left' ? leftScore.value : rightScore.value)) {
      finalGameSideSwitchPending.value = true
      saveStateToStorage()
      return true
    }
    saveStateToStorage()
    return true
  }

  function switchSides() {
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    pushHistory()
    applySideSwitch()
    saveStateToStorage()
    return true
  }

  function handleFinalGameSideSwitch(shouldSwitch) {
    if (!isFinalGameSideSwitchPromptActive.value) return false
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = true
    if (shouldSwitch) {
      applySideSwitch()
    }
    if (!isGodMode.value && checkWinCondition(leftScore.value, rightScore.value)) {
      finishGame('left')
      return true
    }
    if (!isGodMode.value && checkWinCondition(rightScore.value, leftScore.value)) {
      finishGame('right')
      return true
    }
    saveStateToStorage()
    return true
  }

  function confirmGameEnd() {
    if (!gameEndPromptPending.value) return false
    gameEndPromptPending.value = false
    gameEndPromptHandled.value = true

    const lastGame = gameScores.value[gameScores.value.length - 1]
    const winnerSide = lastGame?.winnerSide === 'right' ? 'right' : 'left'

    currentGameNo.value += 1
    const nextGameNo = currentGameNo.value
    leftScore.value = 0
    rightScore.value = 0
    serveSide.value = winnerSide
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = false
    if (shouldAutoSwitchBetweenGames(nextGameNo)) {
      applySideSwitch()
    }
    saveStateToStorage()
    return true
  }

  function undo() {
    if (isReadOnly.value || !historyStack.value.length || isLocked.value || isPromptActive.value) return false
    const prev = historyStack.value.pop()
    applySnapshot(prev)
    saveStateToStorage()
    return true
  }

  function retire(side) {
    if (side !== 'left' && side !== 'right') return false
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    pushHistory()
    retiredSide.value = side
    if (side === 'left') {
      rightGameWins.value = matchRules.value.gamesToWin
      winnerName.value = rightTeam.value
    } else {
      leftGameWins.value = matchRules.value.gamesToWin
      winnerName.value = leftTeam.value
    }
    matchEnded.value = true
    saveStateToStorage()
    return true
  }

  function manualFinishGame() {
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    if (leftScore.value === rightScore.value) return false
    pushHistory()
    finishGame(leftScore.value > rightScore.value ? 'left' : 'right')
    return true
  }

  function toOriginalSide(side) {
    if (!sidesSwapped.value) return side
    return side === 'left' ? 'right' : 'left'
  }

  function toOriginalGame(game) {
    if (!sidesSwapped.value) return { ...game }
    return {
      gameNo: game.gameNo,
      leftScore: game.rightScore,
      rightScore: game.leftScore,
      winnerSide: toOriginalSide(game.winnerSide),
    }
  }

  return {
    leftTeam,
    rightTeam,
    leftScore,
    rightScore,
    leftGameWins,
    rightGameWins,
    currentGameNo,
    gameScores,
    serveSide,
    sidesSwapped,
    historyStack,
    retiredSide,
    matchEnded,
    isGodMode,
    isReadOnly,
    isLocked,
    rulesLocked,
    isPromptActive,
    isFinalGameSideSwitchPromptActive,
    isGameEndPromptActive,
    finalGameSideSwitchPending,
    finalGameSideSwitchHandled,
    gameEndPromptPending,
    gameEndPromptHandled,
    matchRules,
    finalGameSideSwitchThreshold,
    addScore,
    adjustScore,
    switchSides,
    undo,
    handleFinalGameSideSwitch,
    confirmGameEnd,
    retire,
    manualFinishGame,
    buildSnapshot,
    applySnapshot,
    saveStateToStorage,
    restoreStateFromStorage,
    toOriginalSide,
    toOriginalGame,
  }
}

/**
 * 核心羽毛球业务不变式断言与异常审计器
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

  // 5. 决胜局换边提示锁定
  if (context.wasDecidingGameSideSwitchThresholdHit && !scoreboard.finalGameSideSwitchHandled.value && !scoreboard.isLocked.value) {
    check(
      scoreboard.isFinalGameSideSwitchPromptActive.value,
      'DECIDING_GAME_SIDE_SWITCH_NOT_PROMPTED',
      `决胜局达到换边门槛但未触发换边弹窗提示`
    )
  }

  // 6. 弹窗锁定保护：当 isPromptActive 时不能进行后续加分
  if (context.lastActionAttemptedWhilePromptActive) {
    check(
      context.actionRejected,
      'PROMPT_ACTIVE_MUTATION_ACCEPTED',
      `弹窗处于待决状态时，非处理动作未被正确拦截`
    )
  }

  // 7. 终局不可变性：比赛结束后除撤销外的加分必须被拒绝
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

  return anomalies
}

/**
 * 单场羽毛球无头混沌模拟执行器
 */
export async function simulateBadmintonMatch(scenario, prng, options = {}) {
  const env = options.env || {}
  const scoreboard = createBadmintonScoreboard(scenario, env)
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
    capHits: 0,
    retired: false,
    decidingGameReached: false,
    gamesPlayed: 0,
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

    // 1) 存取回环重入模拟 (2%，仅在稳定无弹窗状态下)
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
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'GOD_ADJUST', anomalies: audit })
      scoreboard.isGodMode.value = false
      continue
    }

    // 6) 恶意越界输入探针 (3%)
    if (roll >= 0.24 && roll < 0.27) {
      matchStats.hostileProbes++
      const hostileSide = 'invalid_side'
      const accepted = scoreboard.addScore(hostileSide)
      if (!accepted) matchStats.hostileRejected++
      actionHistory.push({ step, action: 'HOSTILE_PROBE', accepted })
      context.lastAction = 'HOSTILE_PROBE'
      context.actionRejected = !accepted
      const audit = auditBadmintonInvariants(scoreboard, scenario, context)
      if (audit.length > 0) recordedAnomalies.push({ step, action: 'HOSTILE_PROBE', anomalies: audit })
      continue
    }

    // 7) 常规正常得分 (余下约 73%)
    const scoreSide = prng.choice(['left', 'right'])
    const beforeLeft = scoreboard.leftScore.value
    const beforeRight = scoreboard.rightScore.value
    const accepted = scoreboard.addScore(scoreSide)

    if (accepted) {
      context.lastAction = 'SCORE'
      context.lastScoredSide = scoreSide
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

  // 终局后进行恶意追加加分探针断言
  if (scoreboard.matchEnded.value) {
    const postEndAccepted = scoreboard.addScore('left')
    if (postEndAccepted) {
      recordedAnomalies.push({
        step: step + 1,
        action: 'POST_END_SCORE_ACCEPTED',
        anomalies: [{ severity: 'CRITICAL', type: 'TERMINAL_STATE_MUTATION_ACCEPTED', message: '终局后仍然接受加分' }],
      })
    }
  }

  matchStats.gamesPlayed = scoreboard.gameScores.value.length

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
    finalState: scoreboard.buildSnapshot(),
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

  // 汇总覆盖度证据
  const coverage = {
    totalReloads: 0,
    decidingGameSideSwitched: 0,
    decidingGameSideKept: 0,
    manualSideSwitches: 0,
    deepUndos: 0,
    hostileProbes: 0,
    hostileRejected: 0,
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

  // 产物落盘
  try {
    const fs = await import('node:fs')
    const path = await import('node:path')
    const outDir = process.env.FUZZ_SUMMARY_DIR
      ? path.resolve(process.env.FUZZ_SUMMARY_DIR)
      : options.outputDir
        ? path.resolve(options.outputDir)
        : path.resolve(process.cwd(), '../outputs/fuzz-badminton')

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
    // ignore fs errors
  }

  return {
    ...summary,
    criticalReports,
    suspiciousReports,
  }
}
