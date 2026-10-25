import { computed, ref } from 'vue'
import {
  isDoublesCourtReady,
  nextCourtAnchorsAfterScore,
  normalizeAnchorIndex,
  normalizeCourtMembers,
} from './court-position'

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
 * addScore(side) 增加侧别守卫——side 不是
 * 'left'/'right' 时直接返回 false 且不产生任何状态变更，杜绝非法字符串
 * 污染 serveSide。
 *
 * 2026-10-13 行为修正：
 * - 移除上帝模式（isGodMode / adjustScore / manualFinishGame / toggleGodMode），
 *   裁判改分一律走正常加分与撤销通道；
 * - 局间换边修正为羽毛球规则：自第 2 局起每局结束都自动换边
 *   （此前仅三局两胜的第 2/3 局换边，五局三胜的第 2/3/4 局不换边）。
 *
 * 各状态转移方法返回 boolean（true=动作被接受，false=被拒绝/无操作），
 * 页面模板调用处不读取返回值，行为不受影响；无头 fuzzer 依赖该返回值审计。
 */
const STORAGE_KEY = 'badminton_scoreboard_state'

export function useScoreboardState(options = {}) {
  const announceScore = typeof options.announceScore === 'function' ? options.announceScore : null
  const onMatchEnded = typeof options.onMatchEnded === 'function' ? options.onMatchEnded : null
  const onAction = typeof options.onAction === 'function' ? options.onAction : null

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
  // 四半区站位状态（2026-10 双打沙盘）：mode/members 为屏幕坐标，锚点=站在本方
  // 右发球区的成员下标（Law 11.1.5/11.3.1 转移规则见 court-position.js）
  const courtMode = ref('singles')
  const courtPlaced = ref(false)
  const leftCourtMembers = ref([])
  const rightCourtMembers = ref([])
  const leftAnchorIndex = ref(0)
  const rightAnchorIndex = ref(0)
  // 事件水位与事件列表：随本地快照一起持久化，供恢复与增量上云续用
  const matchEvents = ref([])
  const nextEventSeq = ref(1)
  const lastSyncedEventSeq = ref(0)

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
  const hasMatchProgress = computed(() => hasPointStarted.value
    || gameScores.value.length > 0
    || leftGameWins.value > 0
    || rightGameWins.value > 0)
  const isDoublesCourt = computed(() => isDoublesCourtReady({
    mode: courtMode.value,
    leftMembers: leftCourtMembers.value,
    rightMembers: rightCourtMembers.value,
  }))
  // 双打初始站位待录入：仅在「全新比赛」（无比分/无局分/未结束）且可操作时强制弹窗，
  // 已有进度的旧比赛（恢复缺 pos）走降级显示，不中途追问裁判；
  // 只读态（失权/观赛）一律不弹——弹窗无关闭出口，只读下点确认必被拒
  const courtPlacementPending = computed(() => isDoublesCourt.value
    && !courtPlaced.value
    && !hasMatchProgress.value
    && !isLocked.value
    && !isReadOnly.value)
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

  /**
   * 事件运行时快照（参赛方原始坐标）。
   *
   * 屏幕坐标经 applySideSwitch 会翻转队名与比分，事件与恢复一律换算回原始坐标，
   * 保证换边后事件语义稳定（与 toOriginalSide/toOriginalGame 同一套约定）。
   * 站位 court 同样按原始坐标落 runtime（换边镜像成员与锚点），
   * 恢复侧 runtimeToSnapshot 再镜像回屏幕坐标。
   */
  function buildEventRuntime() {
    const swapped = sidesSwapped.value
    return {
      leftTeam: swapped ? rightTeam.value : leftTeam.value,
      rightTeam: swapped ? leftTeam.value : rightTeam.value,
      leftScore: swapped ? rightScore.value : leftScore.value,
      rightScore: swapped ? leftScore.value : rightScore.value,
      leftGameWins: swapped ? rightGameWins.value : leftGameWins.value,
      rightGameWins: swapped ? leftGameWins.value : rightGameWins.value,
      currentGameNo: currentGameNo.value,
      gameScores: gameScores.value.map(game => toOriginalGame(game)),
      serveSide: swapped ? toOriginalSide(serveSide.value) : serveSide.value,
      sidesSwapped: swapped,
      retiredSide: retiredSide.value ? toOriginalSide(retiredSide.value) : '',
      matchEnded: matchEnded.value,
      winnerName: winnerName.value,
      matchStartTime: matchStartTime.value,
      matchDuration: matchDuration.value,
      finalGameSideSwitchPending: finalGameSideSwitchPending.value,
      finalGameSideSwitchHandled: finalGameSideSwitchHandled.value,
      gameEndPromptPending: gameEndPromptPending.value,
      gameEndPromptHandled: gameEndPromptHandled.value,
      matchRules: { ...matchRules.value },
      court: buildCourtState(swapped),
    }
  }

  /** 站位快照：屏幕坐标；swapped=true 时输出原始坐标（成员/锚点随参赛方镜像）。 */
  function buildCourtState(swapped) {
    const court = {
      mode: courtMode.value,
      placed: courtPlaced.value,
      leftMembers: [...leftCourtMembers.value],
      rightMembers: [...rightCourtMembers.value],
      leftAnchor: leftAnchorIndex.value,
      rightAnchor: rightAnchorIndex.value,
    }
    if (!swapped) return court
    return {
      mode: court.mode,
      placed: court.placed,
      leftMembers: court.rightMembers,
      rightMembers: court.leftMembers,
      leftAnchor: court.rightAnchor,
      rightAnchor: court.leftAnchor,
    }
  }

  function applyCourtSnapshot(court) {
    if (!court || typeof court !== 'object') return false
    const mode = court.mode === 'doubles' ? 'doubles' : 'singles'
    const leftMembers = normalizeCourtMembers(court.leftMembers)
    const rightMembers = normalizeCourtMembers(court.rightMembers)
    courtMode.value = mode
    leftCourtMembers.value = leftMembers
    rightCourtMembers.value = rightMembers
    leftAnchorIndex.value = normalizeAnchorIndex(court.leftAnchor)
    rightAnchorIndex.value = normalizeAnchorIndex(court.rightAnchor)
    courtPlaced.value = !!court.placed && isDoublesCourtReady({ mode, leftMembers, rightMembers })
    return true
  }

  /**
   * 状态转移落定后向事件管道派发动作（页面把它接到 match-events 的 appendAction）。
   * 模块本身不发起任何网络请求，保持可无头测试。
   */
  function emitAction(action, detail = {}) {
    if (!onAction) return
    try {
      onAction({
        action,
        runtime: buildEventRuntime(),
        prevRuntime: detail.prevRuntime || null,
        scoringSide: detail.scoringSide || '',
        retiredSide: detail.retiredSide || '',
        courtSide: detail.courtSide || '',
      })
    } catch (error) {
      console.error('追加比赛事件失败:', error)
    }
  }

  /**
   * 应用由服务端记录重建的恢复态（badminton-recovery.buildRecoveredState 的返回值）。
   * snapshot / historyStack 已是屏幕坐标，落状态后回写本地缓存。
   */
  function applyRecoveredState(recovered) {
    if (!recovered || !recovered.snapshot) return false
    applySnapshot(recovered.snapshot)
    historyStack.value = Array.isArray(recovered.historyStack)
      ? recovered.historyStack.map(item => ({
        ...item,
        gameScores: Array.isArray(item.gameScores) ? item.gameScores.map(game => ({ ...game })) : [],
      }))
      : []
    matchEvents.value = Array.isArray(recovered.events)
      ? recovered.events.map(item => ({ ...item, payload: item.payload || {} }))
      : []
    nextEventSeq.value = Math.max(1, Number(recovered.nextEventSeq || 1))
    lastSyncedEventSeq.value = Math.max(0, Number(recovered.lastSyncedEventSeq || 0))
    saveStateToStorage()
    return true
  }

  /** 只读本地缓存快照（不落状态），供进入时仲裁本地与服务端数据源。 */
  function readStateCache() {
    try {
      const cache = uni.getStorageSync(storageKey())
      return cache && typeof cache === 'object' ? cache : null
    } catch (error) {
      console.error('读取本地缓存失败:', error)
      return null
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
      court: buildCourtState(false),
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
    // 旧快照（无 court 字段）保留当前站位值：单打默认视图不受影响，
    // 双打旧比赛由此走降级显示
    if (snapshot.court && typeof snapshot.court === 'object') {
      applyCourtSnapshot(snapshot.court)
    }
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
        matchEvents: matchEvents.value,
        nextEventSeq: nextEventSeq.value,
        lastSyncedEventSeq: lastSyncedEventSeq.value,
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
      matchEvents.value = Array.isArray(cache.matchEvents) ? cache.matchEvents : []
      nextEventSeq.value = Math.max(1, Number(cache.nextEventSeq || 1))
      lastSyncedEventSeq.value = Math.max(0, Number(cache.lastSyncedEventSeq || 0))
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
    // 羽毛球规则：自第 2 局起每局结束双方交换场地（bestOf=1 无局间流转；此前仅三局两胜换边）
    return Number(nextGameNo) >= 2
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
    const prevRuntime = buildEventRuntime()
    pushHistory()
    const isServiceOver = serveSide.value !== side

    if (side === 'left') {
      leftScore.value += 1
    } else {
      rightScore.value += 1
    }
    // 站位转移（Law 11.1.5/11.3.1）：发球方得分 → 该侧两人互换左右发球区
    // （同一发球员换区继续发）；接发方得分不换位。推演细则见 court-position.js
    if (isDoublesCourt.value && courtPlaced.value && !isServiceOver) {
      const anchors = nextCourtAnchorsAfterScore({
        scoringSide: side,
        serveSide: serveSide.value,
        leftAnchor: leftAnchorIndex.value,
        rightAnchor: rightAnchorIndex.value,
      })
      leftAnchorIndex.value = anchors.left
      rightAnchorIndex.value = anchors.right
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
      emitAction('score', { prevRuntime, scoringSide: toOriginalSide(side) })
      return true
    }

    if (checkWinCondition(myScore, opponentScore)) {
      finishGame(side)
      emitAction('score', { prevRuntime, scoringSide: toOriginalSide(side) })
      return true
    }

    saveStateToStorage()
    emitAction('score', { prevRuntime, scoringSide: toOriginalSide(side) })
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
    const prevRuntime = buildEventRuntime()
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
    // 羽毛球规则自第 2 局起每局结束必换边，故 between_games 事件同时充当「局边界」标记
    emitAction('game_end', { prevRuntime })
    return true
  }

  function undo() {
    if (isReadOnly.value || !historyStack.value.length || isLocked.value || isPromptActive.value) return false
    const prev = historyStack.value.pop()
    applySnapshot(prev)
    saveStateToStorage()
    emitAction('undo')
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
    // 站位随参赛方走：换边只交换网两侧场地，不互换左右半区（Law 8），
    // 故成员名单与锚点跟随屏幕左右镜像，锚点数值本身不变
    const members = leftCourtMembers.value
    leftCourtMembers.value = rightCourtMembers.value
    rightCourtMembers.value = members
    const anchor = leftAnchorIndex.value
    leftAnchorIndex.value = rightAnchorIndex.value
    rightAnchorIndex.value = anchor
  }

  function switchSides() {
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    const prevRuntime = buildEventRuntime()
    pushHistory()
    applySideSwitch()
    saveStateToStorage()
    emitAction('side_switch_manual', { prevRuntime })
    return true
  }

  function handleFinalGameSideSwitch(shouldSwitch) {
    if (!isFinalGameSideSwitchPromptActive.value) return false
    const prevRuntime = buildEventRuntime()
    finalGameSideSwitchPending.value = false
    finalGameSideSwitchHandled.value = true
    if (shouldSwitch) {
      applySideSwitch()
    }
    // 决胜局技术换边无论选哪边都落事件（不计入撤销栈）：runtime 里的
    // finalGameSideSwitchHandled/sidesSwapped 就是真实结果，
    // 否则换设备恢复后弹窗会重问、判分被再次打断。
    if (checkWinCondition(leftScore.value, rightScore.value)) {
      finishGame('left')
      emitAction('side_switch_technical', { prevRuntime })
      return true
    }
    if (checkWinCondition(rightScore.value, leftScore.value)) {
      finishGame('right')
      emitAction('side_switch_technical', { prevRuntime })
      return true
    }
    saveStateToStorage()
    emitAction('side_switch_technical', { prevRuntime })
    return true
  }

  function retire(side) {
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false

    ensureStartTime()
    const prevRuntime = buildEventRuntime()
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
    emitAction('retire', { prevRuntime, retiredSide: toOriginalSide(side) })
    return true
  }

  function resetMatchState() {
    if (sidesSwapped.value) {
      const teamName = leftTeam.value
      leftTeam.value = rightTeam.value
      rightTeam.value = teamName
      // 站位成员与队名同源，换回原朝向时一并镜像回来
      const members = leftCourtMembers.value
      leftCourtMembers.value = rightCourtMembers.value
      rightCourtMembers.value = members
      const anchor = leftAnchorIndex.value
      leftAnchorIndex.value = rightAnchorIndex.value
      rightAnchorIndex.value = anchor
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
    // 完整重置后双打回到待录入站位（模式与成员名单保留，属于比赛身份信息）
    courtPlaced.value = false
    leftAnchorIndex.value = 0
    rightAnchorIndex.value = 0
    resetFinalGameSideSwitchState()
    resetGameEndPromptState()
    matchStartTime.value = Date.now()
    saveStateToStorage()
  }

  /**
   * 进入比赛时从路由注入站位上下文（team-match 双打子项带成员名单与 playerCount）。
   * 已恢复出的站位（含锚点/已确认状态）优先——本函数只补位，不覆盖：
   * 换设备恢复、同设备续裁后路由再带同名参数也不会冲掉既有锚点。
   */
  function applyCourtContext(context) {
    if (!context || typeof context !== 'object') return false
    if (context.mode !== 'doubles') return false
    if (isDoublesCourt.value) return false
    const leftMembers = normalizeCourtMembers(context.leftMembers)
    const rightMembers = normalizeCourtMembers(context.rightMembers)
    if (!isDoublesCourtReady({ mode: 'doubles', leftMembers, rightMembers })) return false
    courtMode.value = 'doubles'
    leftCourtMembers.value = leftMembers
    rightCourtMembers.value = rightMembers
    // placed 维持现状：全新比赛为 false（弹录入框），有进度的旧比赛保持 false 走降级
    saveStateToStorage()
    return true
  }

  /**
   * 双打开局站位确认（录入框提交）：定先发球方与两侧谁站右发球区。
   * 0:0 的站位列 lineup_snapshot 事件（类型在后端白名单内），
   * 与既有「不在进入时写 init 基线」不冲突——这是真实状态变更。
   */
  function setupDoublesCourt({ serveSide: nextServeSide, leftAnchor, rightAnchor } = {}) {
    if (!isDoublesCourt.value || courtPlaced.value) return false
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    if (nextServeSide !== 'left' && nextServeSide !== 'right') return false
    const prevRuntime = buildEventRuntime()
    ensureStartTime()
    courtPlaced.value = true
    serveSide.value = nextServeSide
    leftAnchorIndex.value = normalizeAnchorIndex(leftAnchor)
    rightAnchorIndex.value = normalizeAnchorIndex(rightAnchor)
    saveStateToStorage()
    emitAction('court_setup', { prevRuntime })
    return true
  }

  /**
   * 站位纠错：交换某一侧两人的左右半区（Law 12 发现站错区：纠正站位、比分有效）。
   * 也用于局间重排（Law 11.6 允许新一局任一队员先发——交换右区成员即换发球员）。
   * 不入撤销栈：误触再点一次即换回。
   */
  function swapCourtPlayers(side) {
    if (side !== 'left' && side !== 'right') return false
    if (!isDoublesCourt.value || !courtPlaced.value) return false
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    const prevRuntime = buildEventRuntime()
    if (side === 'left') {
      leftAnchorIndex.value = 1 - leftAnchorIndex.value
    } else {
      rightAnchorIndex.value = 1 - rightAnchorIndex.value
    }
    saveStateToStorage()
    emitAction('court_swap', { prevRuntime, courtSide: side })
    return true
  }

  /**
   * 单打挑边：第一局第一分前指定先发球方（规则上由挑边决定，此前默认左方）。
   * 不追加事件：0:0 尚无任何已同步事件，首个得分动作的 runtime 自带结果。
   */
  function setFirstServeSide(side) {
    if (side !== 'left' && side !== 'right') return false
    if (isReadOnly.value || isLocked.value || isPromptActive.value) return false
    if (hasMatchProgress.value) return false
    serveSide.value = side
    saveStateToStorage()
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
    // 四半区站位状态
    courtMode,
    courtPlaced,
    leftCourtMembers,
    rightCourtMembers,
    leftAnchorIndex,
    rightAnchorIndex,
    matchEvents,
    nextEventSeq,
    lastSyncedEventSeq,
    // 派生态
    isLocked,
    rulesLocked,
    hasPointStarted,
    hasMatchProgress,
    isDoublesCourt,
    courtPlacementPending,
    isFinalGameSideSwitchPromptActive,
    isGameEndPromptActive,
    isPromptActive,
    finalGameSideSwitchThreshold,
    lockTitle,
    ruleText,
    canLeaveWithoutResult,
    // 状态转移
    addScore,
    finishGame,
    confirmGameEnd,
    undo,
    switchSides,
    handleFinalGameSideSwitch,
    retire,
    resetMatchState,
    // 站位状态转移
    applyCourtContext,
    setupDoublesCourt,
    swapCourtPlayers,
    setFirstServeSide,
    // 快照与缓存
    buildSnapshot,
    applySnapshot,
    saveStateToStorage,
    restoreStateFromStorage,
    clearCache,
    readStateCache,
    applyRecoveredState,
    buildEventRuntime,
    // 规则
    applyRules,
    checkWinCondition,
    // 坐标还原
    toOriginalSide,
    toOriginalGame,
  }
}
