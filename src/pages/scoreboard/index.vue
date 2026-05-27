<template>
  <view class="scoreboard-page">
    <view class="top-left-actions">
      <button class="action-btn side-action-btn" @click="handleBack">返回</button>
    </view>

    <view class="top-center-actions">
      <button class="action-btn center-action-btn" @click="undo" :disabled="!historyStack.length || isLocked">撤销</button>
      <button class="action-btn center-action-btn" @click="switchSides" :disabled="isLocked">换边</button>
      <button class="action-btn center-action-btn" :class="{ active: isGodMode }" @click="toggleGodMode">上帝模式</button>
      <button class="action-btn icon-action-btn rules-btn" @click="openRulesModal" :disabled="isLocked">⚙</button>
    </view>

    <view class="top-right-actions">
      <button class="action-btn side-action-btn danger" @click="openRetireSheet" :disabled="isLocked">退赛</button>
    </view>

    <view class="match-info">
      <text class="game-tag">第 {{ currentGameNo }} 局</text>
      <text class="match-rule">{{ ruleText }}</text>
      <text class="game-wins">{{ leftGameWins }} : {{ rightGameWins }}</text>
    </view>

    <view class="god-finish-row" v-if="isGodMode && !isLocked">
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
          <view class="score-box" :class="{ disabled: isLocked }" @click="addScore('left')">
            <view class="score">{{ leftScore }}</view>
          </view>
          <view class="serve-flag" :class="{ active: serveSide === 'left' }">·发球</view>
        </view>
      </view>

      <view class="score-side right-side">
        <view v-if="isGodMode" class="god-edge-controls right-edge">
          <button class="mini-btn" @click.stop="adjustScore('right', 1)">+1</button>
          <button class="mini-btn" @click.stop="adjustScore('right', -1)">-1</button>
        </view>

        <view class="team-panel">
          <text class="team-name">{{ rightTeam }}</text>
          <view class="score-box" :class="{ disabled: isLocked }" @click="addScore('right')">
            <view class="score">{{ rightScore }}</view>
          </view>
          <view class="serve-flag" :class="{ active: serveSide === 'right' }">·发球</view>
        </view>
      </view>
    </view>

    <view class="games-strip" v-if="gameScores.length">
      <view class="game-pill" v-for="game in gameScores" :key="game.gameNo">
        <text>第{{ game.gameNo }}局</text>
        <text>{{ game.leftScore }}:{{ game.rightScore }}</text>
      </view>
    </view>

    <view v-if="isLocked" class="lock-mask">
      <scroll-view class="settlement-scroll" scroll-y>
        <view class="settlement-card">
          <text class="settlement-title">{{ lockTitle }}</text>
          <text class="settlement-winner">获胜方：{{ winnerName || '待定' }}</text>
          <text class="settlement-score">{{ leftGameWins }} : {{ rightGameWins }}</text>
          <text class="settlement-games">{{ scoreSummary || '暂无局分' }}</text>
          <text class="settlement-duration">总用时：{{ matchDuration }}</text>
          <view class="settlement-actions">
            <button class="new-match-btn" @click="resetMatch">重新开始</button>
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
import { computed, reactive, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

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
const matchDuration = ref('00:00')
const winnerName = ref('')
const matchId = ref('')
const sidesSwapped = ref(false)

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
const scoreSummary = computed(() => {
  return gameScores.value.map(game => `${game.leftScore}:${game.rightScore}`).join(', ')
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
  const mm = String(Math.floor(totalSeconds / 60)).padStart(2, '0')
  const ss = String(totalSeconds % 60).padStart(2, '0')
  return `${mm}:${ss}`
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
  matchDuration.value = snapshot.matchDuration || '00:00'
  winnerName.value = snapshot.winnerName || ''
  sidesSwapped.value = !!snapshot.sidesSwapped
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
  const capPoint = Math.max(pointsToWin + 1, Math.min(99, Number(rule.capPoint || 30)))
  matchRules.value = {
    bestOf,
    gamesToWin: Number(rule.gamesToWin || Math.floor(bestOf / 2) + 1),
    pointsToWin,
    enableDeuce: rule.enableDeuce !== false && rule.enableDeuce !== '0',
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

function addScore(side) {
  if (isLocked.value) return
  ensureStartTime()
  pushHistory()

  if (side === 'left') {
    leftScore.value += 1
  } else {
    rightScore.value += 1
  }
  serveSide.value = side

  if (!isGodMode.value && checkWinCondition(
    side === 'left' ? leftScore.value : rightScore.value,
    side === 'left' ? rightScore.value : leftScore.value
  )) {
    finishGame(side)
    return
  }

  saveStateToStorage()
}

function adjustScore(side, delta) {
  if (!isGodMode.value || isLocked.value) return
  ensureStartTime()
  pushHistory()

  if (side === 'left') {
    leftScore.value = Math.max(0, leftScore.value + delta)
  } else {
    rightScore.value = Math.max(0, rightScore.value + delta)
  }
  saveStateToStorage()
}

function manualFinishGame() {
  if (isLocked.value) return
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
    return
  }

  currentGameNo.value += 1
  leftScore.value = 0
  rightScore.value = 0
  serveSide.value = winnerSide
  saveStateToStorage()
}

function undo() {
  if (!historyStack.value.length || isLocked.value) return
  const prev = historyStack.value.pop()
  applySnapshot(prev)
  saveStateToStorage()
}

function switchSides() {
  if (isLocked.value) return
  pushHistory()

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
  saveStateToStorage()
}

function openRetireSheet() {
  if (isLocked.value) return
  uni.showActionSheet({
    itemList: [`${leftTeam.value} 退赛`, `${rightTeam.value} 退赛`],
    success: (res) => {
      if (res.tapIndex === 0) retire('left')
      if (res.tapIndex === 1) retire('right')
    },
  })
}

function retire(side) {
  if (isLocked.value) return

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
      matchDuration.value = '00:00'
      winnerName.value = ''
      sidesSwapped.value = false
      matchStartTime.value = Date.now()
      saveStateToStorage()
    },
  })
}

function toggleGodMode() {
  isGodMode.value = !isGodMode.value
  saveStateToStorage()
}

function openRulesModal() {
  if (leftScore.value !== 0 || rightScore.value !== 0 || gameScores.value.length > 0) {
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
  if (tempRules.capPoint <= tempRules.pointsToWin) {
    tempRules.capPoint = Math.min(99, tempRules.pointsToWin + 1)
  }
}

function setTempCap(value) {
  tempRules.capPoint = Math.max(tempRules.pointsToWin + 1, Math.min(99, Number(value) || tempRules.pointsToWin + 1))
}

function saveRules() {
  applyRules(tempRules)
  showRulesModal.value = false
  saveStateToStorage()
}

function closeRulesModal() {
  showRulesModal.value = false
}

function handleBack() {
  if (showRulesModal.value) {
    closeRulesModal()
    return
  }

  if (canLeaveWithoutResult.value) {
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
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (_) {
    // request handles toast
  }
}

onLoad((options) => {
  if (options?.matchId) matchId.value = options.matchId

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
})

onBackPress(() => {
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

.top-left-actions,
.top-center-actions,
.top-right-actions {
  position: absolute;
  top: 14rpx;
  z-index: 10;
  display: flex;
  align-items: center;
}

.top-left-actions {
  left: 10rpx;
}

.top-center-actions {
  left: 50%;
  transform: translateX(-50%);
  gap: 6rpx;
  max-width: 76vw;
  justify-content: center;
}

.top-right-actions {
  right: 10rpx;
}

.match-info {
  position: absolute;
  top: 78rpx;
  left: 50%;
  transform: translateX(-50%);
  z-index: 8;
  display: flex;
  align-items: center;
  gap: 18rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 22rpx;
  white-space: nowrap;
}

.god-finish-row {
  position: absolute;
  top: 118rpx;
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
  background: #ff8c00;
  color: #1a2a3a;
  font-weight: 600;
}

.action-btn.danger {
  border-color: #ffffff;
  color: #ff8c00;
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

.main-panels {
  width: 100%;
  height: 100%;
  display: flex;
  padding: 126rpx 36rpx 78rpx;
  box-sizing: border-box;
  gap: 28rpx;
}

.main-panels.god-layout {
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
  justify-content: center;
  align-items: center;
  gap: 12rpx;
  transform: translateY(30rpx);
}

.god-layout .team-panel {
  width: calc(100% - 112rpx);
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
  font-size: 36rpx;
  font-weight: 600;
  line-height: 1.4;
  flex-shrink: 0;
}

.score {
  max-width: 90%;
  font-size: 104rpx;
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
  font-size: 26rpx;
  font-weight: 600;
  line-height: 32rpx;
  min-height: 32rpx;
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
  background: rgba(0, 0, 0, 0.58);
  display: flex;
  justify-content: center;
  align-items: center;
  z-index: 20;
  padding: 20rpx;
  box-sizing: border-box;
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
  border: 2rpx solid rgba(255, 140, 0, 0.6);
  background: linear-gradient(180deg, #22364c 0%, #172637 100%);
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.35);
  padding: 34rpx 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.settlement-title {
  font-size: 44rpx;
  font-weight: 700;
}

.settlement-winner {
  font-size: 30rpx;
  color: #ffb347;
  font-weight: 600;
}

.settlement-score {
  font-size: 94rpx;
  font-weight: 700;
  line-height: 1;
}

.settlement-games,
.settlement-duration {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.86);
}

.settlement-actions {
  width: 100%;
  display: flex;
  gap: 16rpx;
  margin-top: 10rpx;
}

.new-match-btn {
  margin-top: 10rpx;
  width: 100%;
  max-width: 420rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #1a2a3a;
  font-size: 28rpx;
  font-weight: 700;
}

.sync-btn {
  background: #52c41a;
}

.rules-modal-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.6);
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
  border: 2rpx solid rgba(255, 140, 0, 0.5);
  background: #22364c;
  box-shadow: 0 16rpx 48rpx rgba(0, 0, 0, 0.4);
  overflow: hidden;
}

.rules-modal-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16rpx 24rpx 12rpx;
  border-bottom: 1rpx solid rgba(255, 140, 0, 0.25);
  flex-shrink: 0;
}

.rules-modal-title {
  font-size: 26rpx;
  font-weight: 700;
}

.rules-modal-close {
  font-size: 34rpx;
  color: rgba(255, 255, 255, 0.55);
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
  border: 1rpx solid rgba(255, 140, 0, 0.4);
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
  color: rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.06);
  padding: 0 10rpx;
}

.toggle-option.toggle-active {
  background: #ff8c00;
  color: #1a2a3a;
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
  color: #ff8c00;
  background: rgba(255, 140, 0, 0.12);
  border: 1rpx solid rgba(255, 140, 0, 0.35);
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
  border: 1rpx solid rgba(255, 140, 0, 0.35);
  border-radius: 10rpx;
  padding: 0 4rpx;
}

.rules-modal-footer {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
  padding: 14rpx 24rpx 18rpx;
  border-top: 1rpx solid rgba(255, 140, 0, 0.25);
  flex-shrink: 0;
}

.rules-save-btn {
  background: #ff8c00;
  color: #1a2a3a;
  font-weight: 600;
  border: none;
}
</style>
