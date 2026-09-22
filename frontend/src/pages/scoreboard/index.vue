<template>
  <view class="scoreboard-page">
    <view v-if="isReadOnly" class="readonly-banner">当前比赛正由其他设备执裁，您已进入只读模式</view>
    <view class="top-flow-row">
      <view class="top-center-actions">
      <button class="action-btn side-action-btn danger" @click="openRetireSheet" :disabled="isReadOnly || isLocked || isPromptActive">退赛</button>
      <button class="action-btn center-action-btn" @click="undo" :disabled="isReadOnly || !historyStack.length || isLocked || isPromptActive">撤销</button>
      <button class="action-btn center-action-btn god-mode-btn" :class="{ active: isGodMode }" @click="toggleGodMode" :disabled="isReadOnly || isPromptActive">上帝模式</button>
      <button class="action-btn center-action-btn" @click="switchSides" :disabled="isReadOnly || isLocked || isPromptActive">换边</button>
      <button class="action-btn icon-action-btn rules-btn" @click="openRulesModal" :disabled="isReadOnly || rulesLocked || isPromptActive">⚙</button>
      <button class="action-btn icon-action-btn sound-action-btn" :class="{ muted: isScoreMuted }" @click="toggleScoreMuted">
        <view class="sound-icon" :class="{ muted: isScoreMuted }">
          <image class="sound-icon-image" src="/static/sound-icon.png" mode="aspectFit"></image>
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

    <view class="god-finish-row" v-if="isGodMode && !isReadOnly && !isLocked && !isPromptActive">
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
          <view class="score-box" :class="{ disabled: isReadOnly || isLocked || isPromptActive }" @click="addScore('left')">
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
          <view class="score-box" :class="{ disabled: isReadOnly || isLocked || isPromptActive }" @click="addScore('right')">
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
            <button class="new-match-btn sync-btn" @click="syncAndBack" v-if="matchId" :disabled="isReadOnly">同步结算</button>
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
import { reactive, ref, onUnmounted } from 'vue'
import { onBackPress, onLoad, onUnload } from '@dcloudio/uni-app'
import { guardProfileBeforeAction } from '@/store/auth'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'
import { useScoreAnnouncer } from '@/composables/useScoreAnnouncer'
import { useScoreboardState } from './use-scoreboard-state'

import { requireMatchOperator } from '@/utils/match-guard'
import { acquireMatchLock, createMatchLockToken, matchLockHeader, releaseMatchLock, startMatchLockHeartbeat } from '@/utils/match-lock'
import { buildIndividualRecordUrl } from '@/pages/tournament/tournament-navigation'

// 纯状态机与规则逻辑已提取至 ./use-scoreboard-state（单一事实源），
// 本页面只保留 UI 胶水：onLoad 流程、执裁权锁、确认弹窗、页面跳转与结算网络调用。
// 完局/退赛后的自动结算定时器经 onMatchEnded 回调注入；比分语音播报经 announceScore 注入。
const tournamentId = ref('')
const divisionId = ref('')
const pageSource = ref('')
const autoSettlementTimer = ref(null)
const isSyncingSettlement = ref(false)
const sessionLockToken = ref('')
let stopHeartbeat = null
const { isMuted: isScoreMuted, toggleMuted: toggleScoreMuted, announceScore, destroyScoreAnnouncer } = useScoreAnnouncer()

const scoreboard = useScoreboardState({
  announceScore,
  onMatchEnded: scheduleAutoSettlement,
})

const {
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
  isReadOnly,
  matchRules,
  isLocked,
  rulesLocked,
  hasPointStarted,
  isFinalGameSideSwitchPromptActive,
  isGameEndPromptActive,
  isPromptActive,
  finalGameSideSwitchThreshold,
  lockTitle,
  ruleText,
  canLeaveWithoutResult,
  applyRules,
  addScore,
  adjustScore,
  switchSides,
  undo,
  handleFinalGameSideSwitch,
  confirmGameEnd,
  toggleGodMode,
  resetMatchState,
  resetFinalGameSideSwitchState,
  resetGameEndPromptState,
  clearCache,
  saveStateToStorage,
  restoreStateFromStorage,
  toOriginalSide,
  toOriginalGame,
} = scoreboard

const showRulesModal = ref(false)
const tempRules = reactive({
  bestOf: 3,
  gamesToWin: 2,
  pointsToWin: 21,
  enableDeuce: true,
  capPoint: 30,
})

function clearAutoSettlementTimer() {
  if (!autoSettlementTimer.value) return
  clearTimeout(autoSettlementTimer.value)
  autoSettlementTimer.value = null
}

function scheduleAutoSettlement() {
  clearAutoSettlementTimer()
  if (!matchId.value || !isLocked.value || isReadOnly.value) return
  autoSettlementTimer.value = setTimeout(() => {
    autoSettlementTimer.value = null
    syncAndBack()
  }, 10000)
}

function stopMatchLockHeartbeat() {
  if (!stopHeartbeat) return
  stopHeartbeat()
  stopHeartbeat = null
}

function enterReadOnly(message) {
  stopMatchLockHeartbeat()
  if (isReadOnly.value) return
  isReadOnly.value = true
  clearAutoSettlementTimer()
  if (message) {
    uni.showModal({
      title: '只读模式',
      content: message,
      showCancel: false,
    })
  }
}

async function setupMatchLock() {
  if (!matchId.value) return true
  sessionLockToken.value = createMatchLockToken()
  try {
    const result = await acquireMatchLock(matchId.value, sessionLockToken.value)
    if (result?.success === true || result?.editable === true) {
      isReadOnly.value = false
      stopMatchLockHeartbeat()
      stopHeartbeat = startMatchLockHeartbeat(matchId.value, sessionLockToken.value, () => {
        enterReadOnly('执裁会话已超时，操作权已交接。')
      })
      return true
    }
    enterReadOnly('当前比赛正由其他设备执裁，您已进入只读模式。')
    return false
  } catch (_) {
    enterReadOnly('暂时无法取得执裁权，您已进入只读模式。')
    return false
  }
}

function matchLockRequestOptions(extra = {}) {
  return {
    ...extra,
    header: {
      ...(extra.header || {}),
      ...matchLockHeader(sessionLockToken.value),
    },
  }
}

function openRetireSheet() {
  if (isReadOnly.value || isLocked.value || isPromptActive.value) return
  uni.showActionSheet({
    itemList: [`${leftTeam.value} 退赛`, `${rightTeam.value} 退赛`],
    success: (res) => {
      if (res.tapIndex === 0) retire('left')
      if (res.tapIndex === 1) retire('right')
    },
  })
}

function retire(side) {
  if (isReadOnly.value || isLocked.value || isPromptActive.value) return

  uni.showModal({
    title: '确认退赛',
    content: `确认${side === 'left' ? leftTeam.value : rightTeam.value}退赛？`,
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
      scoreboard.retire(side)
    },
  })
}

function resetMatch() {
  if (isReadOnly.value) return
  uni.showModal({
    title: '确认重置',
    content: '确认清空当前比赛数据？',
    confirmText: '确认',
    cancelText: '取消',
    success: (res) => {
      if (!res.confirm) return
      clearAutoSettlementTimer()
      resetMatchState()
    },
  })
}

function manualFinishGame() {
  if (isReadOnly.value || isLocked.value || isPromptActive.value) return
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
      scoreboard.manualFinishGame()
    },
  })
}

function openRulesModal() {
  if (isReadOnly.value || isPromptActive.value) return
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
  if (isReadOnly.value) return
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

 if (isReadOnly.value) {
   navigateBackOrHome()
   return
 }

 if (canLeaveWithoutResult.value) {
   clearCache()
   navigateBackOrHome()
   return
 }

  uni.showToast({
    title: '比赛已开始，请先撤销到0:0再返回',
    icon: 'none',
    duration: 2500,
  })
}

async function syncAndBack() {
  clearAutoSettlementTimer()
  if (isReadOnly.value) {
    uni.showToast({ title: '只读模式不能结算比赛', icon: 'none' })
    return
  }
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
    await request('/api/v1/matches/' + matchId.value + '/finish', matchLockRequestOptions({
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
    }))
    uni.showToast({ title: '结算成功', icon: 'success' })
    stopMatchLockHeartbeat()
    clearCache()
    setTimeout(() => {
      if (pageSource.value === 'teamMatch') {
        navigateBackOrHome()
        return
      }
      uni.redirectTo({
        url: buildIndividualRecordUrl({
          tournamentId: tournamentId.value,
          matchId: matchId.value,
          divisionId: divisionId.value,
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
  if (options?.divisionId) divisionId.value = options.divisionId
  if (options?.source) pageSource.value = options.source
  if (!(await guardProfileBeforeAction('请先完善个人资料，再进入记分'))) {
    clearCache()
    navigateBackOrHome()
    return
  }
  const allowed = await requireMatchOperator(matchId.value)
 if (!allowed) {
   clearCache()
   setTimeout(() => navigateBackOrHome(), 1500)
   return
 }
  await setupMatchLock()

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
  stopMatchLockHeartbeat()
  destroyScoreAnnouncer()
})

onUnload(() => {
  stopMatchLockHeartbeat()
  void releaseMatchLock(matchId.value, sessionLockToken.value)
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

  if (isReadOnly.value || canLeaveWithoutResult.value) {
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

.readonly-banner {
  position: absolute;
  top: 84rpx;
  left: 20rpx;
  right: 20rpx;
  z-index: 20;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  background: rgba(255, 193, 7, 0.18);
  border: 1rpx solid rgba(255, 193, 7, 0.5);
  color: #ffe082;
  font-size: 24rpx;
  text-align: center;
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
  box-sizing: border-box;
}

.sound-icon-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
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

.sound-icon.muted .sound-icon-image {
  opacity: 0.45;
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

