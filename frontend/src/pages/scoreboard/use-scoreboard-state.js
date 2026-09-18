import { computed, ref } from 'vue'

/**
 * 通用记分板（羽毛球等）核心状态机 composable
 *
 * 2026-09 从 pages/scoreboard/index.vue 原样提取（行为保持重构：移动而非复制，
 * 单一事实源，index.vue 现在是消费本模块的薄封装）：
 * - 比分/局数/发球权/历史栈等全部状态、快照构建与恢复、本地缓存读写
 *   （uni.setStorageSync / getStorageSync / removeStorageSync 保留真实调用，
 *   无头测试经 pages/scoreboard/fuzzer-env.js 垫片 mock）
 * - 加分、撤销、换边、退赛、完局、临场规则归一化等全部状态转移与胜利条件判定
 *
 * 页面保留 UI 胶水：onLoad 流程、执裁权锁、uni.showModal/showActionSheet 确认、
 * 页面跳转、结算网络调用。完局/退赛后的自动结算定时器与播报音频经回调注入：
 * - onMatchEnded：比赛结束（完局或退赛落定）后同步触发，页面用它调度
 *   scheduleAutoSettlement；模块本身不持有任何定时器
 * - announceScore：加分后的比分语音播报（fire-and-forget，不影响状态）
 *
 * 唯一的产品行为变更（docs/badminton-chaos-调研.md §7.5 登记缺陷的修复）：
 * addScore(side) / adjustScore(side, delta) 增加侧别守卫——side 不是
 * 'left'/'right' 时直接返回 false 且不产生任何状态变更，杜绝非法字符串
 * 污染 serveSide。
 *
 * 各状态转移方法返回 boolean（true=动作被接受，false=被拒绝/无操作），
 * 页面模板调用处不读取返回值，行为不受影响；无头 fuzzer 依赖该返回值审计。
 */
const STORAGE_KEY = 'badminton_scoreboard_state'

export function useScoreboardState(options = {}) {
  const announceScore = typeof options.announceScore === 'function' ? options.announceScore : null
  const onMatchEnded = typeof options.onMatchEnded === 'function' ? options.onMatchEnded : null

  const matchId = ref('')
  const leftTeam = ref('左队')
  const rightTeam = ref('右队')
  const leftScore = ref(0)
  const rightScore = ref(0)
  const leftGameWins = ref(0)
  const rightGameWins = ref(0)
  const currentGameNo = ref(1)
  const gameScores = ref([])
  const serveSide = ref('left')
  const historyStack = ref([])
  const isGodMode = ref(false)
  const retiredSide = ref('')
  const matchEnded = ref(false)
  const matchStartTime = ref(0)
  const matchDuration = ref('0分0秒')
  const winnerName = ref('')
  const sidesSwapped = ref(false)
  const finalGameSideSwitchPending = ref(false)
  const finalGameSideSwitchHandled = ref(false)
  const gameEndPromptPending = ref(false)
  const gameEndPromptHandled = ref(false)
  const isReadOnly = ref(false)

  const matchRules = ref({
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  })

  const isLocked = computed(() => !!retiredSide.value || matchEnded.value)
  const rulesLocked = computed(() => isLocked.value || leftScore.value !== 0 || rightScore.value !== 0 || gameScores.value.length > 0)
  const hasPointStarted = computed(() => leftScore.value + rightScore.value > 0)
  const isBestOfThreeMatch = computed(() => Number(matchRules.value.bestOf || 3) === 3 && Number(matchRules.value.gamesToWin || 2) === 2)
  const isFinalGameSideSwitchPromptActive = computed(() => finalGameSideSwitchPending.value || needsFinalGameSideSwitch())
  const isGameEndPromptActive = computed(() => gameEndPromptPending.value)
  const isPromptActive = computed(() => isFinalGameSideSwitchPromptActive.value || isGameEndPromptActive.value)
  const finalGameSideSwitchThreshold = computed(() => Math.ceil(matchRules.value.pointsToWin / 2))
  const lockTitle = computed(() => {
    if (retiredSide.value === 'left') return `${leftTeam.value} 已退赛`
    if (retiredSide.value === 'right') return `${rightTeam.value} 已退赛`
    if (matchEnded.value) return '比赛结束'
    return ''
  })
  const ruleText = computed(() => {
    const matchText = matchRules.value.bestOf === 5
      ? '五局三胜'
      : matchRules.value.bestOf === 1
        ? '一局定胜负'
        : '三局两胜'
    const deuce = matchRules.value.enableDeuce ? `${matchRules.value.capPoint}分封顶` : '无追分'
    return `${matchText} / ${matchRules.value.pointsToWin}分 / ${deuce}`
  })
  const canLeaveWithoutResult = computed(() => {
    if (isLocked.value) return true
    return leftScore.value === 0
      && rightScore.value === 0
      && leftGameWins.value === 0
      && rightGameWins.value === 0
      && gameScores.value.length === 0
  })

  function storageKey() {
    return matchId.value ? STORAGE_KEY + '_' + matchId.value : STORAGE_KEY
  }

  function formatDuration(ms) {
    const totalSeconds = Math.max(0, Math.floor(ms / 1000))
    const minutes = Math.floor(totalSeconds / 60)
    const seconds = totalSeconds % 60
    return `${minutes}分${seconds}秒`
  }

  function ensureStartTime() {
    if (!matchStartTime.value || Number.isNaN(matchStartTime.value)) {
      matchStartTime.value = Date.now()
    }
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
      applyRules(snapshot.matchRules)
    }
  }

  function pushHistory() {
    historyStack.value.push(buildSnapshot())
  }

  function saveStateToStorage() {
    try {
      uni.setStorageSync(storageKey(), {
        ...buildSnapshot(),
        historyStack: historyStack.value,
        isGodMode: isGodMode.value,
      })
    } catch (error) {
      console.error('保存本地缓存失败:', error)
    }
  }

  function clearCache() {
    try {
      uni.removeStorageSync(storageKey())
    } catch (_) {
    }
  }

  function restoreStateFromStorage() {
    try {
      const cache = uni.getStorageSync(storageKey())
      if (!cache || typeof cache !== 'object') return false
      applySnapshot(cache)
      historyStack.value = Array.isArray(cache.historyStack) ? cache.historyStack : []
      isGodMode.value = !!cache.isGodMode
      return true
    } catch (error) {
      console.error('恢复本地缓存失败:', error)
      return false
    }
  }

  function applyRules(rule) {
    const bestOf = normalizeBestOf(Number(rule.bestOf || 3))
    const pointsToWin = Math.max(1, Math.min(99, Number(rule.pointsToWin || 21)))
    const enableDeuce = rule.enableDeuce !== false && rule.enableDeuce !== '0'
    const rawCapPoint = Number(rule.capPoint || (enableDeuce ? 30 : pointsToWin))
    const capPoint = enableDeuce
      ? Math.max(pointsToWin + 1, Math.min(99, rawCapPoint))
      : Math.max(1, Math.min(99, rawCapPoint))
    matchRules.value = {
      bestOf,
      gamesToWin: Number(rule.gamesToWin || Math.floor(bestOf / 2) + 1),
      pointsToWin,
      enableDeuce,
      capPoint,
    }
  }

  function normalizeBestOf(value) {
    if (value === 1 || value === 3 || value === 5) return value
    return 3
  }

  function checkWinCondition(myScore, opponentScore) {
    if (myScore >= matchRules.value.capPoint) return true
    if (myScore >= matchRules.value.pointsToWin) {
      if (!matchRules.value.enableDeuce) return true
      return myScore - opponentScore >= 2
    }
    return false
  }

  function isGamePointScore(myScore, opponentScore) {
    return !checkWinCondition(myScore, opponentScore) && checkWinCondition(myScore + 1, opponentScore)
  }

  function isMatchPointScore(side, myScore, opponentScore) {
    if (!isGamePointScore(myScore, opponentScore)) return false
    const currentWins = side === 'left' ? leftGameWins.value : rightGameWins.value
    return currentWins + 1 >= Number(matchRules.value.gamesToWin || 2)
  }

  function shouldAutoSwitchBetweenGames(nextGameNo) {
    return isBestOfThreeMatch.value && (Number(nextGameNo) === 2 || Number(nextGameNo) === 3)
  }

  function shouldPromptFinalGameSideSwitch(score) {
    return isFinalGameSideSwitchGame()
      && !finalGameSideSwitchHandled.value
      && Number(score) >= finalGameSideSwitchThreshold.value
  }

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

  function lockFinalGameSideSwitch() {
    finalGameSideSwitchPending.value = true
    saveStateToStorage()
  }

  function resetFinalGameSideSwitchState() {
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = false
  }

  function resetGameEndPromptState() {
    gameEndPromptPending.value = false
    gameEndPromptHandled.value = false
  }

  function addScore(side) {
    if (side !== 'left' && side !== 'right') return false
    if (isReadOnly.value || isLocked.value || isGameEndPromptActive.value) return false
    if (needsFinalGameSideSwitch()) {
      lockFinalGameSideSwitch()
      return false
    }
    if (finalGameSideSwitchPending.value) return false
    ensureStartTime()
    pushHistory()
    const isServiceOver = serveSide.value !== side

    if (side === 'left') {
      leftScore.value += 1
    } else {
      rightScore.value += 1
    }
    serveSide.value = side

    const myScore = side === 'left' ? leftScore.value : rightScore.value
    const opponentScore = side === 'left' ? rightScore.value : leftScore.value
    const isMatchPoint = isMatchPointScore(side, myScore, opponentScore)
    const isGamePoint = !isMatchPoint && isGamePointScore(myScore, opponentScore)
    if (announceScore) {
      void announceScore(side, myScore, opponentScore, {
        isServiceOver,
        isGamePoint,
        isMatchPoint,
      })
    }
    if (shouldPromptFinalGameSideSwitch(myScore)) {
      lockFinalGameSideSwitch()
      return true
    }

    if (!isGodMode.value && checkWinCondition(myScore, opponentScore)) {
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
      lockFinalGameSideSwitch()
      return false
    }
    ensureStartTime()
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
      lockFinalGameSideSwitch()
      return true
    }
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
      matchDuration.value = formatDuration(Date.now() - matchStartTime.value)
      matchEnded.value = true
      saveStateToStorage()
      if (onMatchEnded) onMatchEnded()
      return
    }

    gameEndPromptPending.value = true
    gameEndPromptHandled.value = false
    saveStateToStorage()
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
    resetFinalGameSideSwitchState()
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

  function retire(side) {
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false

    ensureStartTime()
    pushHistory()
    retiredSide.value = side
    if (side === 'left') {
      rightGameWins.value = matchRules.value.gamesToWin
      winnerName.value = rightTeam.value
    } else {
      leftGameWins.value = matchRules.value.gamesToWin
      winnerName.value = leftTeam.value
    }
    matchDuration.value = formatDuration(Date.now() - matchStartTime.value)
    matchEnded.value = true
    saveStateToStorage()
    if (onMatchEnded) onMatchEnded()
    return true
  }

  function resetMatchState() {
    if (sidesSwapped.value) {
      const teamName = leftTeam.value
      leftTeam.value = rightTeam.value
      rightTeam.value = teamName
    }
    leftScore.value = 0
    rightScore.value = 0
    leftGameWins.value = 0
    rightGameWins.value = 0
    currentGameNo.value = 1
    gameScores.value = []
    serveSide.value = 'left'
    historyStack.value = []
    matchEnded.value = false
    retiredSide.value = ''
    matchDuration.value = '0分0秒'
    winnerName.value = ''
    sidesSwapped.value = false
    resetFinalGameSideSwitchState()
    resetGameEndPromptState()
    matchStartTime.value = Date.now()
    saveStateToStorage()
  }

  function toggleGodMode() {
    if (isReadOnly.value || isPromptActive.value) return
    isGodMode.value = !isGodMode.value
    saveStateToStorage()
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
    // 状态
    matchId,
    leftTeam,
    rightTeam,
    leftScore,
    rightScore,
    leftGameWins,
    rightGameWins,
    currentGameNo,
    gameScores,
    serveSide,
    historyStack,
    isGodMode,
    retiredSide,
    matchEnded,
    matchStartTime,
    matchDuration,
    winnerName,
    sidesSwapped,
    finalGameSideSwitchPending,
    finalGameSideSwitchHandled,
    gameEndPromptPending,
    gameEndPromptHandled,
    isReadOnly,
    matchRules,
    // 派生态
    isLocked,
    rulesLocked,
    hasPointStarted,
    isBestOfThreeMatch,
    isFinalGameSideSwitchPromptActive,
    isGameEndPromptActive,
    isPromptActive,
    finalGameSideSwitchThreshold,
    lockTitle,
    ruleText,
    canLeaveWithoutResult,
    // 状态转移
    addScore,
    adjustScore,
    manualFinishGame,
    finishGame,
    confirmGameEnd,
    undo,
    switchSides,
    handleFinalGameSideSwitch,
    retire,
    resetMatchState,
    toggleGodMode,
    // 快照与缓存
    buildSnapshot,
    applySnapshot,
    saveStateToStorage,
    restoreStateFromStorage,
    clearCache,
    // 规则
    applyRules,
    checkWinCondition,
    // 坐标还原
    toOriginalSide,
    toOriginalGame,
  }
}
