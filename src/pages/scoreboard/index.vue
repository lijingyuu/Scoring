<template>
  <view class="scoreboard-page">
    <view class="top-left-actions">
      <button class="action-btn side-action-btn danger" @click="retire('left')" :disabled="isLocked">左侧退赛</button>
    </view>

    <view class="top-center-actions">
      <button class="action-btn center-action-btn" @click="undo" :disabled="!historyStack.length || isLocked">撤销</button>
      <button class="action-btn center-action-btn" @click="switchSides" :disabled="isLocked">换边</button>
      <button class="action-btn center-action-btn end-btn" @click="endMatch" :disabled="isLocked">结束比赛</button>
      <button class="action-btn center-action-btn" :class="{ active: isGodMode }" @click="toggleGodMode">上帝模式</button>
    </view>

    <view class="top-right-actions">
      <button class="action-btn side-action-btn danger" @click="retire('right')" :disabled="isLocked">右侧退赛</button>
    </view>

    <view class="main-panels">
      <view class="team-panel">
        <view v-if="isGodMode" class="god-controls">
          <button class="mini-btn" @click.stop="adjustScore('left', 1)">+1</button>
          <button class="mini-btn" @click.stop="adjustScore('left', -1)">-1</button>
        </view>

        <text class="team-name">{{ leftTeam }}</text>
        <view class="score-box" :class="{ disabled: isLocked }" @click="addScore('left')">
          <view class="score">{{ leftScore }}</view>
          <view class="serve-flag" v-if="serveSide === 'left'">● 发球</view>
        </view>
      </view>

      <view class="team-panel">
        <view v-if="isGodMode" class="god-controls">
          <button class="mini-btn" @click.stop="adjustScore('right', 1)">+1</button>
          <button class="mini-btn" @click.stop="adjustScore('right', -1)">-1</button>
        </view>

        <text class="team-name">{{ rightTeam }}</text>
        <view class="score-box" :class="{ disabled: isLocked }" @click="addScore('right')">
          <view class="score">{{ rightScore }}</view>
          <view class="serve-flag" v-if="serveSide === 'right'">● 发球</view>
        </view>
      </view>
    </view>

    <view v-if="isLocked" class="lock-mask">
      <scroll-view class="settlement-scroll" scroll-y>
      <view class="settlement-card">
        <text class="settlement-title">{{ lockTitle }}</text>
        <text class="settlement-winner">🏆 获胜方：{{ winnerName || '待定' }}</text>
        <text class="settlement-score">{{ leftScore }} : {{ rightScore }}</text>
        <text class="settlement-duration">⏱ 总用时：{{ matchDuration }}</text>
        <view class="settlement-actions">
          <button class="new-match-btn" @click="resetMatch">重新开始</button>
          <button class="new-match-btn sync-btn" @click="syncAndBack" v-if="matchId">同步结算</button>
        </view>
      </view>
      </scroll-view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const STORAGE_KEY = 'badminton_scoreboard_state'

function storageKey() {
  return matchId.value
    ? STORAGE_KEY + '_' + matchId.value
    : STORAGE_KEY
}

const leftTeam = ref('左队')
const rightTeam = ref('右队')
const leftScore = ref(0)
const rightScore = ref(0)
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

const isLocked = computed(() => !!retiredSide.value || matchEnded.value)
const lockTitle = computed(() => {
  if (retiredSide.value === 'left') return `${leftTeam.value} 已退赛`
  if (retiredSide.value === 'right') return `${rightTeam.value} 已退赛`
  if (matchEnded.value) return '比赛结束'
  return ''
})

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
    serveSide: serveSide.value,
    retiredSide: retiredSide.value,
    matchEnded: matchEnded.value,
    matchStartTime: matchStartTime.value,
    matchDuration: matchDuration.value,
    winnerName: winnerName.value,
    sidesSwapped: sidesSwapped.value
  }
}

function applySnapshot(snapshot) {
  leftTeam.value = snapshot.leftTeam
  rightTeam.value = snapshot.rightTeam
  leftScore.value = snapshot.leftScore
  rightScore.value = snapshot.rightScore
  serveSide.value = snapshot.serveSide
  retiredSide.value = snapshot.retiredSide || ''
  matchEnded.value = !!snapshot.matchEnded
  matchStartTime.value = Number(snapshot.matchStartTime || Date.now())
  matchDuration.value = snapshot.matchDuration || '00:00'
  winnerName.value = snapshot.winnerName || ''
  sidesSwapped.value = !!snapshot.sidesSwapped
}

function pushHistory() {
  historyStack.value.push(buildSnapshot())
}

function saveStateToStorage() {
  const payload = {
    leftTeam: leftTeam.value,
    rightTeam: rightTeam.value,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    serveSide: serveSide.value,
    historyStack: historyStack.value,
    isGodMode: isGodMode.value,
    retiredSide: retiredSide.value,
    matchEnded: matchEnded.value,
    matchStartTime: matchStartTime.value,
    matchDuration: matchDuration.value,
    winnerName: winnerName.value,
    sidesSwapped: sidesSwapped.value
  }

  try {
    uni.setStorageSync(storageKey(), payload)
  } catch (error) {
    console.error('保存本地缓存失败:', error)
  }
}

function restoreStateFromStorage() {
  try {
    const cache = uni.getStorageSync(storageKey())

    if (!cache || typeof cache !== 'object') return false

    leftTeam.value = cache.leftTeam || leftTeam.value
    rightTeam.value = cache.rightTeam || rightTeam.value
    leftScore.value = Number(cache.leftScore ?? leftScore.value)
    rightScore.value = Number(cache.rightScore ?? rightScore.value)
    serveSide.value = cache.serveSide === 'right' ? 'right' : 'left'
    historyStack.value = Array.isArray(cache.historyStack) ? cache.historyStack : []
    isGodMode.value = !!cache.isGodMode
    retiredSide.value = cache.retiredSide === 'left' || cache.retiredSide === 'right' ? cache.retiredSide : ''
    matchEnded.value = !!cache.matchEnded
    matchStartTime.value = Number(cache.matchStartTime || Date.now())
    matchDuration.value = cache.matchDuration || '00:00'
    winnerName.value = cache.winnerName || ''
    sidesSwapped.value = !!cache.sidesSwapped
    return true
  } catch (error) {
    console.error('恢复本地缓存失败:', error)
    return false
  }
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

  serveSide.value = serveSide.value === 'left' ? 'right' : 'left'
  sidesSwapped.value = !sidesSwapped.value
  saveStateToStorage()
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
      winnerName.value = side === 'left' ? rightTeam.value : leftTeam.value
      matchDuration.value = formatDuration(Date.now() - matchStartTime.value)
      saveStateToStorage()
    }
  })
}

function endMatch() {
  if (isLocked.value) return

  uni.showModal({
    title: '确认结束',
    content: '确认结束本局比赛？',
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
      ensureStartTime()
      pushHistory()
      if (leftScore.value > rightScore.value) {
        winnerName.value = leftTeam.value
      } else if (rightScore.value > leftScore.value) {
        winnerName.value = rightTeam.value
      } else {
        winnerName.value = '平局'
      }
      matchDuration.value = formatDuration(Date.now() - matchStartTime.value)
      matchEnded.value = true
      saveStateToStorage()
    }
  })
}

function resetMatch() {
  uni.showModal({
    title: '确认重置',
    content: '确认清空当前数据，开始新对局？',
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
      leftScore.value = 0
      rightScore.value = 0
      serveSide.value = 'left'
      historyStack.value = []
      matchEnded.value = false
      retiredSide.value = ''
      matchDuration.value = '00:00'
      winnerName.value = ''
      sidesSwapped.value = false
      matchStartTime.value = Date.now()
      saveStateToStorage()
    }
  })
}

function toggleGodMode() {
  isGodMode.value = !isGodMode.value
  saveStateToStorage()
}

async function syncAndBack() {
  if (!matchId.value) {
    uni.showToast({ title: '非赛程比赛，无法同步', icon: 'none' })
    return
  }

  // 确定当前界面上的胜者侧（left/right）
  let currentWinner = null
  if (retiredSide.value) {
    // 退赛：未退赛的一方为胜者
    currentWinner = retiredSide.value === 'left' ? 'right' : 'left'
  } else if (leftScore.value > rightScore.value) {
    currentWinner = 'left'
  } else if (rightScore.value > leftScore.value) {
    currentWinner = 'right'
  }

  if (!currentWinner) {
    uni.showToast({ title: '平局无法同步', icon: 'none' })
    return
  }

  // 如果换过边（奇数次），当前左右和原始左右对调，胜者和分数都需反向映射
  const winnerSide = sidesSwapped.value
    ? (currentWinner === 'left' ? 'right' : 'left')
    : currentWinner
  const sendLeftScore = sidesSwapped.value ? rightScore.value : leftScore.value
  const sendRightScore = sidesSwapped.value ? leftScore.value : rightScore.value

  try {
    await request('/api/v1/matches/' + matchId.value + '/finish', {
      method: 'PUT',
      data: {
        winnerSide: winnerSide,
        leftScore: sendLeftScore,
        rightScore: sendRightScore,
      },
    })
    uni.showToast({ title: '结算成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (_) {
    // request 内部已弹 toast
  }
}

onLoad((options) => {
  if (options?.matchId) {
    matchId.value = options.matchId
  }

  const leftNameFromRoute = options?.leftName ? decodeURIComponent(options.leftName) : ''
  const rightNameFromRoute = options?.rightName ? decodeURIComponent(options.rightName) : ''

  const hasCache = restoreStateFromStorage()

  // 有缓存就用缓存的队名；无缓存就用路由参数；路由参数为空时兜底显示"左队"/"右队"
  if (!hasCache) {
    leftTeam.value = leftNameFromRoute || '左队'
    rightTeam.value = rightNameFromRoute || '右队'
    matchStartTime.value = Date.now()
    saveStateToStorage()
  } else {
    // 缓存恢复后，如果队名为空则回填
    if (!leftTeam.value) leftTeam.value = leftNameFromRoute || '左队'
    if (!rightTeam.value) rightTeam.value = rightNameFromRoute || '右队'
  }
})
</script>

<style scoped>
.scoreboard-page {
  position: relative;
  width: 100vw;
  height: 100vh;
  background: #1A2A3A;
  color: #FFFFFF;
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
  max-width: 64vw;
  flex-wrap: wrap;
  justify-content: center;
}

.top-right-actions {
  right: 10rpx;
}

.action-btn {
  min-width: 82rpx;
  height: 48rpx;
  line-height: 48rpx;
  padding: 0 8rpx;
  border: 1px solid #FF8C00;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #FFFFFF;
  font-size: 18rpx;
}

.action-btn::after {
  border: none;
}

.action-btn.active {
  background: #FF8C00;
  color: #1A2A3A;
  font-weight: 600;
}

.action-btn.danger {
  border-color: #FFFFFF;
  color: #FF8C00;
}

.end-btn {
  border-color: #ff4d4f;
  color: #ff4d4f;
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
  padding: 96rpx 36rpx 36rpx;
  box-sizing: border-box;
  gap: 28rpx;
}

.team-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 20rpx;
}

.god-controls {
  display: flex;
  gap: 14rpx;
}

.mini-btn {
  width: 96rpx;
  height: 56rpx;
  line-height: 56rpx;
  border-radius: 10rpx;
  border: 1px solid #FF8C00;
  background: rgba(255, 140, 0, 0.15);
  color: #FFFFFF;
  font-size: 24rpx;
}

.mini-btn::after {
  border: none;
}

.score-box {
  width: 100%;
  max-width: 560rpx;
  height: 78%;
  padding: 24rpx 16rpx;
  box-sizing: border-box;
  border: 4rpx solid #FF8C00;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 18rpx;
  user-select: none;
}

.score-box.disabled {
  opacity: 0.5;
}

.team-name {
  text-align: center;
  font-size: 36rpx;
  font-weight: 600;
  color: #FFFFFF;
  line-height: 1.4;
  flex-shrink: 0;
}

.score {
  max-width: 90%;
  font-size: 130rpx;
  line-height: 1.05;
  font-weight: 700;
  letter-spacing: 2rpx;
}

.serve-flag {
  max-width: 90%;
  color: #FF8C00;
  font-size: 28rpx;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  color: #FFB347;
  font-weight: 600;
}

.settlement-score {
  font-size: 94rpx;
  font-weight: 700;
  line-height: 1;
  color: #FFFFFF;
}

.settlement-duration {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.86);
}

.new-match-btn {
  margin-top: 10rpx;
  width: 100%;
  max-width: 420rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: #FF8C00;
  color: #1A2A3A;
  font-size: 28rpx;
  font-weight: 700;
}

.new-match-btn::after {
  border: none;
}

.settlement-actions {
  width: 100%;
  display: flex;
  gap: 16rpx;
  margin-top: 10rpx;
}

.sync-btn {
  background: #52c41a;
}
</style>
