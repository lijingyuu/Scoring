<template>
  <view class="scoreboard-page">
    <view class="top-flow-row">
      <view class="top-center-actions">
      <button class="action-btn side-action-btn danger" @click="openRetireSheet" :disabled="isLocked || isPromptActive">退赛</button>
      <button class="action-btn center-action-btn" @click="undo" :disabled="!historyStack.length || isLocked || isPromptActive">撤销</button>
      <button class="action-btn center-action-btn god-mode-btn" :class="{ active: isGodMode }" @click="toggleGodMode" :disabled="isPromptActive">上帝模式</button>
      <button class="action-btn center-action-btn" @click="switchSides" :disabled="isLocked || isPromptActive">换边</button>
      <button class="action-btn icon-action-btn rules-btn" @click="openRulesModal" :disabled="rulesLocked || isPromptActive">⚙</button>
      <button class="action-btn icon-action-btn sound-action-btn" :class="{ muted: isScoreMuted }" @click="toggleScoreMuted">
        <view class="sound-icon" :class="{ muted: isScoreMuted }">
          <view class="sound-icon-speaker"></view>
          <view class="sound-icon-wave wave-one"></view>
          <view class="sound-icon-wave wave-two"></view>
          <view class="sound-icon-slash"></view>
        </view>
      </button>
      </view>

      <text class="top-score-anchor">{{ leftGameWins }} : {{ rightGameWins }}</text>

      <view class="match-info">
      <text class="match-rule">{{ ruleText }}</text>
      <text class="game-tag">第 {{ currentGameNo }} 局</text>
      </view>
    </view>

    <view class="game-wins-row">
      <text class="game-wins">{{ leftGameWins }} : {{ rightGameWins }}</text>
    </view>

    <view class="god-finish-row" v-if="isGodMode && !isLocked && !isPromptActive">
      <button class="action-btn end-btn" @click="manualFinishGame">结束本局</button>
    </view>

    <view class="main-panels" :class="{ 'god-layout': isGodMode }">
      <view class="score-side left-side">
        <view v-if="isGodMode" class="god-edge-controls left-edge">
          <button class="mini-btn" @click.stop="adjustScore('left', 1)">+1</button>
          <button class="mini-btn" @click.stop="adjustScore('left', -1)">-1</button>
        </view>

        <view class="team-panel">
          <text class="team-name">{{ leftTeam }}</text>
          <view class="score-box" :class="{ disabled: isLocked || isPromptActive }" @click="addScore('left')">
            <view class="score">{{ leftScore }}</view>
          </view>
          <view class="serve-flag" :class="{ active: hasPointStarted && serveSide === 'left' }">·发球</view>
        </view>
      </view>

      <view class="score-side right-side">
        <view v-if="isGodMode" class="god-edge-controls right-edge">
          <button class="mini-btn" @click.stop="adjustScore('right', 1)">+1</button>
          <button class="mini-btn" @click.stop="adjustScore('right', -1)">-1</button>
        </view>

        <view class="team-panel">
          <text class="team-name">{{ rightTeam }}</text>
          <view class="score-box" :class="{ disabled: isLocked || isPromptActive }" @click="addScore('right')">
            <view class="score">{{ rightScore }}</view>
          </view>
          <view class="serve-flag" :class="{ active: hasPointStarted && serveSide === 'right' }">·发球</view>
        </view>
      </view>
    </view>

    <view class="games-strip" v-if="gameScores.length">
      <view class="game-pill" v-for="game in gameScores" :key="game.gameNo">
        <text>第{{ game.gameNo }}局</text>
        <text>{{ game.leftScore }}:{{ game.rightScore }}</text>
      </view>
    </view>

    <view v-if="isFinalGameSideSwitchPromptActive" class="final-switch-overlay">
      <view class="final-switch-card">
        <text class="final-switch-title">是否交换场地？</text>
        <text class="final-switch-tip">第 {{ currentGameNo }} 局达到 {{ finalGameSideSwitchThreshold }} 分</text>
        <view class="final-switch-actions">
          <button class="final-switch-btn secondary" @click="handleFinalGameSideSwitch(false)">不换边继续</button>
          <button class="final-switch-btn" @click="handleFinalGameSideSwitch(true)">换边</button>
        </view>
      </view>
    </view>

    <view v-if="isGameEndPromptActive" class="final-switch-overlay">
      <view class="final-switch-card">
        <text class="final-switch-title">第 {{ currentGameNo }} 局结束</text>
        <text class="final-switch-tip">{{ leftTeam }} {{ leftScore }} : {{ rightScore }} {{ rightTeam }}</text>
        <view class="final-switch-actions">
          <button class="final-switch-btn" @click="confirmGameEnd">换边继续</button>
        </view>
      </view>
    </view>

    <view v-if="isLocked" class="lock-mask">
      <scroll-view class="settlement-scroll" scroll-y>
        <view class="settlement-card">
          <text class="settlement-title">{{ lockTitle }}</text>
          <view class="settlement-teams">
            <text class="settlement-team-name" :class="{ winner: leftGameWins > rightGameWins }">{{ leftTeam }}</text>
            <text class="settlement-team-sep">胜</text>
            <text class="settlement-team-name" :class="{ winner: rightGameWins > leftGameWins }">{{ rightTeam }}</text>
          </view>
          <text class="settlement-score">{{ leftGameWins }} : {{ rightGameWins }}</text>
          <text class="settlement-duration">总用时：{{ matchDuration }}</text>
          <view class="settlement-actions">
            <button class="new-match-btn sync-btn" @click="syncAndBack" v-if="matchId">同步结算</button>
          </view>
        </view>
      </scroll-view>
    </view>

    <view v-if="showRulesModal" class="rules-modal-mask" @click="closeRulesModal">
      <view class="rules-modal" @click.stop>
        <view class="rules-modal-header">
          <text class="rules-modal-title">临场规则设置</text>
          <text class="rules-modal-close" @click="closeRulesModal">×</text>
        </view>

        <view class="rules-modal-body">
          <view class="rules-form-item">
            <text class="rules-label">局制</text>
            <view class="rules-toggle wide">
              <view class="toggle-option" :class="{ 'toggle-active': tempRules.bestOf === 1 }" @click="setTempBestOf(1)">一局</view>
              <view class="toggle-option" :class="{ 'toggle-active': tempRules.bestOf === 3 }" @click="setTempBestOf(3)">三局</view>
              <view class="toggle-option" :class="{ 'toggle-active': tempRules.bestOf === 5 }" @click="setTempBestOf(5)">五局</view>
            </view>
          </view>

          <view class="rules-form-item">
            <text class="rules-label">基础胜分</text>
            <view class="rules-stepper">
              <view class="stepper-btn" @click="setTempPoints(tempRules.pointsToWin - 1)">-</view>
              <input class="stepper-input" type="number" :value="tempRules.pointsToWin" @input="setTempPoints(Number($event.detail.value) || 1)" />
              <view class="stepper-btn" @click="setTempPoints(tempRules.pointsToWin + 1)">+</view>
            </view>
          </view>

          <view class="rules-form-item">
            <text class="rules-label">追分机制</text>
            <view class="rules-toggle">
              <view class="toggle-option" :class="{ 'toggle-active': tempRules.enableDeuce }" @click="tempRules.enableDeuce = true">开启</view>
              <view class="toggle-option" :class="{ 'toggle-active': !tempRules.enableDeuce }" @click="tempRules.enableDeuce = false">关闭</view>
            </view>
          </view>

          <view class="rules-form-item" v-if="tempRules.enableDeuce">
            <text class="rules-label">封顶分</text>
            <view class="rules-stepper">
              <view class="stepper-btn" @click="setTempCap(tempRules.capPoint - 1)">-</view>
              <input class="stepper-input" type="number" :value="tempRules.capPoint" @input="setTempCap(Number($event.detail.value) || tempRules.pointsToWin + 1)" />
              <view class="stepper-btn" @click="setTempCap(tempRules.capPoint + 1)">+</view>
            </view>
          </view>
        </view>

        <view class="rules-modal-footer">
          <button class="action-btn" @click="closeRulesModal">取消</button>
          <button class="action-btn rules-save-btn" @click="saveRules">保存</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, reactive, ref, onUnmounted } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { guardProfileBeforeAction } from '@/store/auth'
import { request } from '@/utils/request'

import { requireMatchOperator } from '@/utils/match-guard'
import { buildIndividualRecordUrl } from '@/pages/tournament/tournament-navigation'

const STORAGE_KEY = 'badminton_scoreboard_state'

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
const matchId = ref('')
const tournamentId = ref('')
const pageSource = ref('')
const sidesSwapped = ref(false)
const finalGameSideSwitchPending = ref(false)
const finalGameSideSwitchHandled = ref(false)
const gameEndPromptPending = ref(false)
const gameEndPromptHandled = ref(false)
const autoSettlementTimer = ref(null)
const isSyncingSettlement = ref(false)
const isScoreMuted = ref(false)

const matchRules = ref({
  bestOf: 3,
  gamesToWin: 2,
  pointsToWin: 21,
  enableDeuce: true,
  capPoint: 30,
})

const showRulesModal = ref(false)
const tempRules = reactive({
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

function clearAutoSettlementTimer() {
  if (!autoSettlementTimer.value) return
  clearTimeout(autoSettlementTimer.value)
  autoSettlementTimer.value = null
}

function scheduleAutoSettlement() {
  clearAutoSettlementTimer()
  if (!matchId.value || !isLocked.value) return
  autoSettlementTimer.value = setTimeout(() => {
    autoSettlementTimer.value = null
    syncAndBack()
  }, 10000)
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
  if (isLocked.value || isGameEndPromptActive.value) return
  if (needsFinalGameSideSwitch()) {
    lockFinalGameSideSwitch()
    return
  }
  if (finalGameSideSwitchPending.value) return
  ensureStartTime()
  pushHistory()

  if (side === 'left') {
    leftScore.value += 1
  } else {
    rightScore.value += 1
  }
  serveSide.value = side

  const myScore = side === 'left' ? leftScore.value : rightScore.value
  const opponentScore = side === 'left' ? rightScore.value : leftScore.value
  if (shouldPromptFinalGameSideSwitch(myScore)) {
    lockFinalGameSideSwitch()
    return
  }

  if (!isGodMode.value && checkWinCondition(myScore, opponentScore)) {
    finishGame(side)
    return
  }

  saveStateToStorage()
}

function adjustScore(side, delta) {
  if (!isGodMode.value || isLocked.value || isPromptActive.value) return
  if (needsFinalGameSideSwitch()) {
    lockFinalGameSideSwitch()
    return
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
    return
  }
  saveStateToStorage()
}

function manualFinishGame() {
  if (isLocked.value || isPromptActive.value) return
  if (leftScore.value === rightScore.value) {
    uni.showToast({ title: '平局不能结束本局', icon: 'none' })
    return
  }

  uni.showModal({
    title: '确认结束本局',
    content: `当前比分 ${leftScore.value}:${rightScore.value}`,
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
      pushHistory()
      finishGame(leftScore.value > rightScore.value ? 'left' : 'right')
    },
  })
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
    scheduleAutoSettlement()
    return
  }

  gameEndPromptPending.value = true
  gameEndPromptHandled.value = false
  saveStateToStorage()
}

function confirmGameEnd() {
  if (!gameEndPromptPending.value) return
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
}

function undo() {
  if (!historyStack.value.length || isLocked.value || isPromptActive.value) return
  const prev = historyStack.value.pop()
  applySnapshot(prev)
  saveStateToStorage()
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
  if (isLocked.value || isPromptActive.value) return
  pushHistory()
  applySideSwitch()
  saveStateToStorage()
}

function handleFinalGameSideSwitch(shouldSwitch) {
  if (!isFinalGameSideSwitchPromptActive.value) return
  finalGameSideSwitchPending.value = false
  finalGameSideSwitchHandled.value = true
  if (shouldSwitch) {
    applySideSwitch()
  }
  if (!isGodMode.value && checkWinCondition(leftScore.value, rightScore.value)) {
    finishGame('left')
    return
  }
  if (!isGodMode.value && checkWinCondition(rightScore.value, leftScore.value)) {
    finishGame('right')
    return
  }
  saveStateToStorage()
}

function openRetireSheet() {
  if (isLocked.value || isPromptActive.value) return
  uni.showActionSheet({
    itemList: [`${leftTeam.value} 退赛`, `${rightTeam.value} 退赛`],
    success: (res) => {
      if (res.tapIndex === 0) retire('left')
      if (res.tapIndex === 1) retire('right')
    },
  })
}

function retire(side) {
  if (isLocked.value || isPromptActive.value) return

  uni.showModal({
    title: '确认退赛',
    content: `确认${side === 'left' ? leftTeam.value : rightTeam.value}退赛？`,
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
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
      scheduleAutoSettlement()
    },
  })
}

function resetMatch() {
  uni.showModal({
    title: '确认重置',
    content: '确认清空当前比赛数据？',
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
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
      clearAutoSettlementTimer()
      matchStartTime.value = Date.now()
      saveStateToStorage()
    },
  })
}

function toggleGodMode() {
  if (isPromptActive.value) return
  isGodMode.value = !isGodMode.value
  saveStateToStorage()
}

function toggleScoreMuted() {
  isScoreMuted.value = !isScoreMuted.value
}

function openRulesModal() {
  if (isPromptActive.value) return
  if (rulesLocked.value) {
    uni.showToast({ title: '已有比分后不能临场改规则', icon: 'none', duration: 2500 })
    return
  }

  tempRules.bestOf = matchRules.value.bestOf
  tempRules.gamesToWin = matchRules.value.gamesToWin
  tempRules.pointsToWin = matchRules.value.pointsToWin
  tempRules.enableDeuce = matchRules.value.enableDeuce
  tempRules.capPoint = matchRules.value.capPoint
  showRulesModal.value = true
}

function setTempBestOf(bestOf) {
  tempRules.bestOf = bestOf
  tempRules.gamesToWin = Math.floor(bestOf / 2) + 1
}

function setTempPoints(value) {
  tempRules.pointsToWin = Math.max(1, Math.min(99, Number(value) || 1))
  if (tempRules.enableDeuce && tempRules.capPoint <= tempRules.pointsToWin) {
    tempRules.capPoint = Math.min(99, tempRules.pointsToWin + 1)
  }
}

function setTempCap(value) {
  const fallback = tempRules.enableDeuce ? tempRules.pointsToWin + 1 : tempRules.pointsToWin
  const minCapPoint = tempRules.enableDeuce ? tempRules.pointsToWin + 1 : 1
  tempRules.capPoint = Math.max(minCapPoint, Math.min(99, Number(value) || fallback))
}

function saveRules() {
  applyRules(tempRules)
  resetFinalGameSideSwitchState()
  resetGameEndPromptState()
  showRulesModal.value = false
  saveStateToStorage()
}

function closeRulesModal() {
  showRulesModal.value = false
}

function handleBack() {
  if (isPromptActive.value) {
    uni.showToast({
      title: '请先处理当前弹窗',
      icon: 'none',
      duration: 2000,
    })
    return
  }

  if (showRulesModal.value) {
    closeRulesModal()
    return
  }

 if (canLeaveWithoutResult.value) {
   clearCache()
   uni.navigateBack()
   return
 }

  uni.showToast({
    title: '比赛已开始，请先撤销到0:0再返回',
    icon: 'none',
    duration: 2500,
  })
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

async function syncAndBack() {
  clearAutoSettlementTimer()
  if (isSyncingSettlement.value) return
  if (!matchId.value) {
    uni.showToast({ title: '非赛程比赛，无法同步', icon: 'none' })
    return
  }

  let currentWinner = null
  if (retiredSide.value) {
    currentWinner = retiredSide.value === 'left' ? 'right' : 'left'
  } else if (leftGameWins.value > rightGameWins.value) {
    currentWinner = 'left'
  } else if (rightGameWins.value > leftGameWins.value) {
    currentWinner = 'right'
  }

  if (!currentWinner) {
    uni.showToast({ title: '未分出胜负，无法同步', icon: 'none' })
    return
  }

  const originalScores = gameScores.value.map(toOriginalGame)
  const sendLeftWins = sidesSwapped.value ? rightGameWins.value : leftGameWins.value
  const sendRightWins = sidesSwapped.value ? leftGameWins.value : rightGameWins.value

  try {
    isSyncingSettlement.value = true
    await request('/api/v1/matches/' + matchId.value + '/finish', {
      method: 'PUT',
      data: {
        winnerSide: toOriginalSide(currentWinner),
        leftScore: sidesSwapped.value ? rightScore.value : leftScore.value,
        rightScore: sidesSwapped.value ? leftScore.value : rightScore.value,
        leftGameWins: sendLeftWins,
        rightGameWins: sendRightWins,
        gameScores: originalScores,
        retiredSide: retiredSide.value ? toOriginalSide(retiredSide.value) : null,
      },
    })
    uni.showToast({ title: '结算成功', icon: 'success' })
    clearCache()
    setTimeout(() => {
      if (pageSource.value === 'teamMatch') {
        uni.navigateBack()
        return
      }
      uni.redirectTo({
        url: buildIndividualRecordUrl({
          tournamentId: tournamentId.value,
          matchId: matchId.value,
        }),
      })
    }, 1000)
  } catch (_) {
    // request handles toast
  } finally {
    isSyncingSettlement.value = false
  }
}

onLoad(async (options) => {
  if (options?.matchId) matchId.value = options.matchId
  if (options?.tournamentId) tournamentId.value = options.tournamentId
  if (options?.source) pageSource.value = options.source
  if (!(await guardProfileBeforeAction('请先完善个人资料，再进入记分'))) {
    clearCache()
    uni.navigateBack()
    return
  }
  const allowed = await requireMatchOperator(matchId.value)
 if (!allowed) {
   clearCache()
   setTimeout(() => uni.navigateBack(), 1500)
   return
 }

  applyRules({
    bestOf: Number(options?.bestOf || 3),
    gamesToWin: Number(options?.gamesToWin || 2),
    pointsToWin: Number(options?.pointsToWin || 21),
    enableDeuce: options?.enableDeuce == null ? true : options.enableDeuce !== '0',
    capPoint: Number(options?.capPoint || 30),
  })

  const leftNameFromRoute = options?.leftName ? decodeURIComponent(options.leftName) : ''
  const rightNameFromRoute = options?.rightName ? decodeURIComponent(options.rightName) : ''
  const hasCache = restoreStateFromStorage()

  if (!hasCache) {
    leftTeam.value = leftNameFromRoute || '左队'
    rightTeam.value = rightNameFromRoute || '右队'
    matchStartTime.value = Date.now()
    saveStateToStorage()
  } else {
    if (!leftTeam.value) leftTeam.value = leftNameFromRoute || '左队'
    if (!rightTeam.value) rightTeam.value = rightNameFromRoute || '右队'
  }

  if (isLocked.value) {
    scheduleAutoSettlement()
  }
})

onUnmounted(() => {
  clearAutoSettlementTimer()
})

onBackPress(() => {
  if (isPromptActive.value) {
    uni.showToast({
      title: '请先处理当前弹窗',
      icon: 'none',
      duration: 2000,
    })
    return true
  }

  if (showRulesModal.value) {
    closeRulesModal()
    return true
  }

  if (canLeaveWithoutResult.value) {
    return false
  }

  uni.showToast({
    title: '比赛已开始，请先撤销到0:0再返回',
    icon: 'none',
    duration: 2500,
  })
  return true
})
</script>

<style scoped>
.scoreboard-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  background: #1a2a3a;
  color: #ffffff;
  overflow: hidden;
}

.top-left-actions {
  position: absolute;
  top: 14rpx;
  left: 10rpx;
  z-index: 10;
  display: flex;
  align-items: center;
}

.top-flow-row {
  position: absolute;
  top: 14rpx;
  left: 0;
  right: 0;
  z-index: 10;
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  min-width: 0;
  pointer-events: none;
}

.top-center-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 5rpx;
  min-width: 0;
  pointer-events: auto;
  transform: translateX(-26rpx);
}

.top-score-anchor {
  color: transparent;
  font-size: 23rpx;
  font-weight: 700;
  white-space: nowrap;
  pointer-events: none;
}

.match-info {
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 10rpx;
  min-width: 0;
  color: rgba(255, 255, 255, 0.78);
  font-size: 15rpx;
  white-space: nowrap;
  pointer-events: none;
}

.game-wins-row {
  position: absolute;
  top: 60rpx;
  left: 50%;
  transform: translateX(-50%);
  z-index: 8;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  white-space: nowrap;
}

.god-finish-row {
  position: absolute;
  top: 88rpx;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9;
}

.game-tag,
.game-wins {
  color: #ff8c00;
  font-weight: 700;
}

.action-btn {
  min-width: 82rpx;
  height: 48rpx;
  line-height: 48rpx;
  padding: 0 8rpx;
  border: 1px solid #ff8c00;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 18rpx;
}

.action-btn::after,
.mini-btn::after,
.new-match-btn::after {
  border: none;
}

.action-btn.active {
  border-color: #b84747;
  background: rgba(184, 71, 71, 0.14);
  color: #b84747;
  font-weight: 600;
}

.action-btn.danger {
  border-color: #b84747;
  color: #b84747;
}

.god-mode-btn {
  border-color: #b84747;
  color: #b84747;
}

.god-mode-btn.active {
  border-color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.action-btn[disabled] {
  background: rgba(150, 160, 170, 0.08);
  border-color: rgba(150, 160, 170, 0.46);
  color: rgba(170, 178, 186, 0.72);
  opacity: 1;
}

.end-btn {
  min-width: 128rpx;
  border-color: #ff4d4f;
  color: #ff4d4f;
}

.rules-btn {
  border-color: #ff8c00;
  color: #ff8c00;
}

.sound-action-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
}

.sound-action-btn.muted {
  border-color: #b84747;
  color: #b84747;
}

.icon-action-btn {
  min-width: 48rpx;
  width: 48rpx;
  padding: 0;
  font-size: 26rpx;
  font-weight: 700;
}

.side-action-btn {
  min-width: 80rpx;
  height: 44rpx;
  line-height: 44rpx;
  padding: 0 6rpx;
  font-size: 18rpx;
}

.center-action-btn {
  min-width: 82rpx;
}

.top-left-actions .action-btn {
  margin: 0;
  min-width: 52rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 12rpx;
}

.top-center-actions .action-btn {
  margin: 0;
  min-width: 52rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 12rpx;
}

.top-center-actions .center-action-btn {
  min-width: 52rpx;
}

.top-center-actions .icon-action-btn {
  min-width: 30rpx;
  width: 30rpx;
  padding: 0;
  font-size: 17rpx;
}

.sound-icon {
  position: relative;
  width: 22rpx;
  height: 22rpx;
  display: block;
  color: currentColor;
  box-sizing: border-box;
}

.sound-icon-speaker {
  position: absolute;
  left: 2rpx;
  top: 8rpx;
  width: 6rpx;
  height: 7rpx;
  background: currentColor;
  border-radius: 1rpx;
}

.sound-icon-speaker::before {
  content: '';
  position: absolute;
  left: 5rpx;
  top: -4rpx;
  width: 0;
  height: 0;
  border-top: 7rpx solid transparent;
  border-bottom: 7rpx solid transparent;
  border-left: 9rpx solid currentColor;
}

.sound-icon-wave {
  position: absolute;
  border: 2rpx solid currentColor;
  border-left-color: transparent;
  border-bottom-color: transparent;
  border-radius: 50%;
  transform: rotate(45deg);
  box-sizing: border-box;
}

.sound-icon-wave.wave-one,
.wave-one {
  left: 12rpx;
  top: 7rpx;
  width: 7rpx;
  height: 7rpx;
}

.sound-icon-wave.wave-two,
.wave-two {
  left: 11rpx;
  top: 4rpx;
  width: 13rpx;
  height: 13rpx;
}

.sound-icon-slash {
  position: absolute;
  left: 2rpx;
  top: 10rpx;
  width: 20rpx;
  height: 2rpx;
  background: currentColor;
  border-radius: 999rpx;
  opacity: 0;
  transform: rotate(-45deg);
  transform-origin: center;
}

.sound-icon.muted .sound-icon-wave {
  opacity: 0;
}

.sound-icon.muted .sound-icon-slash {
  opacity: 1;
}

.main-panels {
  width: 100%;
  height: 100%;
  display: flex;
  padding: 84rpx 36rpx 78rpx;
  box-sizing: border-box;
  gap: 28rpx;
}

.main-panels.god-layout {
  padding-top: 144rpx;
  padding-left: 18rpx;
  padding-right: 18rpx;
  gap: 18rpx;
}

.score-side {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
}

.left-side {
  justify-content: flex-end;
}

.right-side {
  justify-content: flex-start;
}

.team-panel {
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  gap: 12rpx;
  transform: none;
}

.god-layout .team-panel {
  width: calc(100% - 112rpx);
}

.god-layout .team-name {
  font-size: 23rpx;
}

.god-layout .score {
  font-size: 68rpx;
}

.left-side .team-panel {
  margin-left: auto;
}

.right-side .team-panel {
  margin-right: auto;
}

.god-edge-controls {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  z-index: 11;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.left-edge {
  left: 0;
}

.right-edge {
  right: 0;
}

.mini-btn {
  width: 88rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: 1px solid #ff8c00;
  background: rgba(255, 140, 0, 0.15);
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.score-box {
  width: 100%;
  max-width: 560rpx;
  height: 80%;
  padding: 34rpx 16rpx;
  box-sizing: border-box;
  border: 4rpx solid #ff8c00;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 0;
}

.score-box.disabled {
  opacity: 0.5;
}

.team-name {
  text-align: center;
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.4;
  flex-shrink: 0;
}

.score {
  max-width: 90%;
  font-size: 78rpx;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: 2rpx;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  transform: none;
}

.serve-flag {
  max-width: 90%;
  color: #ff8c00;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 24rpx;
  min-height: 24rpx;
  opacity: 0;
  margin-top: 2rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  transition: opacity 0.15s ease;
}

.serve-flag.active {
  opacity: 1;
}

.games-strip {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 18rpx;
  display: flex;
  justify-content: center;
  gap: 8rpx;
  z-index: 9;
}

.game-pill {
  display: flex;
  gap: 6rpx;
  padding: 5rpx 9rpx;
  border-radius: 7rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 15rpx;
}

.lock-mask {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 20;
  padding: 20rpx;
  box-sizing: border-box;
}

.final-switch-overlay {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 25;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.72);
}

.final-switch-card {
  width: 72vw;
  max-width: 560rpx;
  border-radius: 22rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.35), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
  padding: 34rpx 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.final-switch-title {
  font-size: 42rpx;
  line-height: 1.25;
  font-weight: 700;
}

.final-switch-tip {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.86);
}

.final-switch-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 18rpx;
}

.final-switch-btn {
  flex: 1;
  max-width: 260rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: rgba(255, 140, 0, 0.2);
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 140, 0, 0.55);
}

.final-switch-btn.secondary {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.28);
}

.final-switch-btn::after {
  border: none;
}

.settlement-scroll {
  width: 100%;
  max-height: 92vh;
}

.settlement-card {
  width: 72vw;
  max-width: 640rpx;
  margin: 0 auto;
  border-radius: 24rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.35), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
  padding: 24rpx 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.settlement-title {
  font-size: 28rpx;
  font-weight: 700;
}

.settlement-teams {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  color: rgba(255, 255, 255, 0.92);
  font-size: 27rpx;
  font-weight: 600;
  line-height: 1.25;
}

.settlement-team-name {
  max-width: 260rpx;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  text-align: center;
}

.settlement-team-name.winner {
  color: #ff8c00;
  font-weight: 700;
}

.settlement-team-sep {
  flex-shrink: 0;
  color: #ffffff;
}

.settlement-score {
  font-size: 42rpx;
  font-weight: 700;
  line-height: 1;
}

.settlement-duration {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.86);
}

.settlement-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 16rpx;
  margin-top: 4rpx;
}

.new-match-btn {
  margin-top: 4rpx;
  width: 100%;
  max-width: 420rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-size: 25rpx;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.28);
}

.sync-btn {
  background: rgba(255, 255, 255, 0.1);
}

.rules-modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 30;
}

.rules-modal {
  width: 84vw;
  max-width: 680rpx;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  border-radius: 20rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.4), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
  overflow: hidden;
}

.rules-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx 12rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

.rules-modal-title {
  font-size: 26rpx;
  font-weight: 700;
}

.rules-modal-close {
  font-size: 34rpx;
  color: rgba(255, 255, 255, 0.72);
  padding: 8rpx;
}

.rules-modal-body {
  padding: 16rpx 22rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  overflow-y: auto;
}

.rules-form-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
}

.rules-label {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.86);
  flex-shrink: 0;
}

.rules-toggle {
  display: flex;
  border-radius: 10rpx;
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.18);
}

.rules-toggle.wide {
  flex: 1;
}

.toggle-option {
  min-width: 72rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.72);
  background: rgba(255, 255, 255, 0.06);
  padding: 0 10rpx;
}

.toggle-option.toggle-active {
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-weight: 600;
}

.rules-stepper {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.stepper-btn {
  width: 40rpx;
  height: 40rpx;
  line-height: 40rpx;
  text-align: center;
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.12);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 8rpx;
}

.stepper-input {
  width: 64rpx;
  height: 40rpx;
  text-align: center;
  font-size: 24rpx;
  font-weight: 600;
  color: #ffffff;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 255, 255, 0.18);
  border-radius: 10rpx;
  padding: 0 4rpx;
}

.rules-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  padding: 14rpx 24rpx 18rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.12);
  flex-shrink: 0;
}

.rules-modal-footer .action-btn {
  border: none;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.86);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.rules-save-btn {
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-weight: 600;
  border: none;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.28);
}
</style>
