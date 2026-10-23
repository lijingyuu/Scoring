<template>
  <view class="scoreboard-page">
    <LockMask
      v-if="lockGuard.maskMode.value"
      :title="lockGuard.maskTitle.value"
      :message="lockGuard.maskMessage.value"
      :score="lockGuard.maskScore.value"
      :retry-label="lockGuard.maskMode.value === 'denied' ? '重试获取' : '重新获取执裁权'"
      :retry-disabled="lockGuard.retryDisabled.value"
      :retrying="lockGuard.retrying.value"
      @retry="lockGuard.retry"
      @back="lockGuard.back" />
    <view class="top-flow-row">
      <view class="top-center-actions">
      <button class="action-btn side-action-btn danger" @click="openRetireSheet" :disabled="isReadOnly || isLocked || isPromptActive">退赛</button>
      <button class="action-btn center-action-btn" @click="undo" :disabled="isReadOnly || !historyStack.length || isLocked || isPromptActive">撤销</button>
      <button class="action-btn center-action-btn" @click="switchSides" :disabled="isReadOnly || isLocked || isPromptActive">换边</button>
      <button class="action-btn icon-action-btn rules-btn" @click="openRulesModal" :disabled="isReadOnly || rulesLocked || isPromptActive">⚙</button>
      <button class="action-btn icon-action-btn sound-action-btn" :class="{ muted: isScoreMuted }" @click="toggleScoreMuted">
        <view class="sound-icon" :class="{ muted: isScoreMuted }">
          <image class="sound-icon-image" src="/static/sound-icon.png" mode="aspectFit"></image>
          <view class="sound-icon-slash"></view>
        </view>
      </button>
      </view>


      <view class="match-info">
      <text class="match-rule">{{ ruleText }}</text>
      <text class="game-tag">第 {{ currentGameNo }} 局</text>
      </view>
    </view>

    <view class="game-wins-row">
      <text class="game-wins">{{ leftGameWins }} : {{ rightGameWins }}</text>
    </view>


     <view class="main-panels">
      <view class="score-side left-side">

        <view class="team-panel">
          <text class="team-name">{{ leftTeam }}</text>
          <view class="score-box" :class="{ disabled: isReadOnly || isLocked || isPromptActive }" @click="addScore('left')">
            <view class="score">{{ leftScore }}</view>
          </view>
          <view class="serve-flag" :class="{ active: hasPointStarted && serveSide === 'left' }">·发球</view>
        </view>
      </view>

      <view class="score-side right-side">

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
import { onBackPress, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { guardProfileBeforeAction } from '@/store/auth'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'
import { useScoreAnnouncer } from '@/composables/useScoreAnnouncer'
import { useScoreboardState } from './use-scoreboard-state'
import { createMatchEventPipeline } from './match-events'
import { buildRecoveredState, decideEntrySource, resolveEntryTeamNames } from './badminton-recovery'

import { requireMatchOperator } from '@/utils/match-guard'
import {
  acquireMatchLockWithRetry,
  clearMatchLockToken,
  createMatchLockToken,
  loadMatchLockToken,
  matchLockHeader,
  releaseMatchLock,
  saveMatchLockToken,
  startMatchLockHeartbeat,
} from '@/utils/match-lock'
import { buildIndividualRecordUrl } from '@/pages/tournament/tournament-navigation'
import LockMask from '@/components/LockMask.vue'
import { useLockGuard } from '@/composables/use-lock-guard'

// 纯状态机与规则逻辑已提取至 ./use-scoreboard-state（单一事实源），
// 本页面只保留 UI 胶水：onLoad 流程、执裁权锁、确认弹窗、页面跳转与结算网络调用。
// 完局/退赛后的自动结算定时器经 onMatchEnded 回调注入；比分语音播报经 announceScore 注入。
// 2026-10 执法链路对齐排球：每个状态转移经 onAction 追加事件上云（match-events），
// 进入时按本地/服务端仲裁恢复（badminton-recovery），锁交接与分数交接配套。
const tournamentId = ref('')
const divisionId = ref('')
const pageSource = ref('')
const autoSettlementTimer = ref(null)
const isSyncingSettlement = ref(false)
const sessionLockToken = ref('')
const lockDenied = ref(false)
const lockDeniedMessage = ref('')
const sameSession = ref(false)
let routeOptions = {}
let stopHeartbeat = null
let eventPipeline = null
let lockReady = false

const scoreboard = useScoreboardState({
  announceScore,
  onMatchEnded: scheduleAutoSettlement,
  onAction: (descriptor) => {
    eventPipeline?.appendAction(descriptor)
  },
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
  switchSides,
  undo,
  handleFinalGameSideSwitch,
  confirmGameEnd,
  resetMatchState,
  resetFinalGameSideSwitchState,
  resetGameEndPromptState,
  clearCache,
  saveStateToStorage,
  restoreStateFromStorage,
  toOriginalSide,
  toOriginalGame,
  matchEvents,
  nextEventSeq,
  lastSyncedEventSeq,
  readStateCache,
  applyRecoveredState,
} = scoreboard
eventPipeline = createMatchEventPipeline({
  events: matchEvents,
  nextEventSeq,
  lastSyncedEventSeq,
  getMatchId: () => matchId.value,
  isReadOnly: () => isReadOnly.value,
  request,
  requestOptions: (extra) => matchLockRequestOptions(extra),
  persist: () => saveStateToStorage(),
})

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
// 执裁锁遮罩守卫：锁不可得（denied）/中途丢失（lost）→ 全屏遮罩取代旧只读模式，
// 重试走「探测 → 同账号顶替确认 → 抢锁 → 全量服务端恢复」
const lockGuard = useLockGuard({
  getMatchId: () => matchId.value,
  acquire: () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
  onRetrySuccess: async (result) => {
    beginLockSession(result)
    await loadMatchState(routeOptions)
  },
  onBack: () => leaveDeniedEntry(),
  buildScore: () => ({
    leftName: leftTeam.value,
    rightName: rightTeam.value,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
  }),
})

function enterReadOnly() {
  stopMatchLockHeartbeat()
  eventPipeline?.stop()
  if (isReadOnly.value) return
  isReadOnly.value = true
  clearAutoSettlementTimer()
  void lockGuard.showLost()
}

/** 首次抢锁失败：拒绝进入（全屏遮罩 + 重试/返回两个出口），不加载比分数据。 */
function denyMatchEntry(message) {
  stopMatchLockHeartbeat()
  isReadOnly.value = true
  lockDenied.value = true
  lockDeniedMessage.value = message
  lockReady = false
  clearAutoSettlementTimer()
  eventPipeline?.stop()
  lockGuard.showDenied(message)
}

// 抢锁成功后的会话副作用（setupMatchLock 成功分支与遮罩重试成功共用）
function beginLockSession(result) {
  sameSession.value = result?.sameSession === true
  if (result?.kicked === true) {
    uni.showToast({ title: '已在其他设备打开该比赛，本设备已接管执裁', icon: 'none', duration: 2500 })
  }
  isReadOnly.value = false
  lockDenied.value = false
  lockDeniedMessage.value = ''
  lockReady = true
  saveMatchLockToken(matchId.value, sessionLockToken.value)
  stopMatchLockHeartbeat()
  stopHeartbeat = startMatchLockHeartbeat(matchId.value, sessionLockToken.value, () => {
    enterReadOnly()
  })
}

async function setupMatchLock() {
  if (!matchId.value) return true
  // 锁 token 持久化：同一设备重进可被判为 sameSession，从而允许本地续裁优先于服务端重放
  sessionLockToken.value = loadMatchLockToken(matchId.value) || createMatchLockToken()
  lockDenied.value = false
  lockDeniedMessage.value = ''
  try {
    // 带确认抢锁：同账号另一设备持锁时先弹「顶替确认」，探测接口缺失则降级直抢（旧行为）
    const result = await lockGuard.acquireGuarded(
      () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
    )
    if (result?.canceled === true) {
      denyMatchEntry('已取消顶替，当前账号另一台设备正在执裁')
      return false
    }
    if (result?.success === true) {
      beginLockSession(result)
      return true
    }
    denyMatchEntry('当前比赛正由其他设备执裁，请稍后再试。')
    return false
  } catch (_) {
    denyMatchEntry('暂时无法取得执裁权，请稍后再试。')
    return false
  }
}
function leaveDeniedEntry() {
  navigateBackOrHome()
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
    uni.showToast({ title: '执裁权已丢失，不能结算比赛', icon: 'none' })
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
    // 结算前强制冲刷事件：保证服务端在 finish 之前已持有本次执裁的全部事件
    const eventsFlushed = await eventPipeline.flushPendingEventsImmediately()
    if (!eventsFlushed) {
      uni.showToast({ title: '比赛数据同步失败，请检查网络后重试', icon: 'none', duration: 2500 })
      return
    }
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
    eventPipeline.reset()
    // 排球同款：结算后立即交还执裁权并清 token，避免残锁阻塞其他裁判
    clearMatchLockToken(matchId.value)
    void releaseMatchLock(matchId.value, sessionLockToken.value)
    clearCache()
    setTimeout(() => leaveToRecord(), 1000)
  } catch (_) {
    // request handles toast
  } finally {
    isSyncingSettlement.value = false
  }
}

async function fetchMatchRecord() {
  if (!matchId.value) return null
  try {
    return await request('/api/v1/matches/' + matchId.value + '/record', { method: 'GET', silent: true })
  } catch (_) {
    // 记录接口失败不阻断执裁：退回本地缓存/空局面，事件仍会继续尝试上云
    return null
  }
}

/**
 * 进入比赛的数据源仲裁（排球 score-recovery 同款思路，见 badminton-recovery）：
 * - 服务端无事件 → 本地优先（部署过渡保护：改造前的老比赛不能被清零）；
 * - 已结算 → 引导战报页，不再重开；
 * - sameSession 且本地已同步水位不低于服务端 → 本地续裁（含未同步事件）；
 * - 其余（换设备 / 顶锁 / 锁过期 / 本地落后）→ 服务端事件重放。
 */
async function loadMatchState(options) {
  const record = await fetchMatchRecord()
  let cached = readStateCache()
  const source = decideEntrySource({ record, cached, sameSession: sameSession.value === true })

  applyRules({
    bestOf: Number(options?.bestOf || 3),
    gamesToWin: Number(options?.gamesToWin || 2),
    pointsToWin: Number(options?.pointsToWin || 21),
    enableDeuce: options?.enableDeuce == null ? true : options.enableDeuce !== '0',
    capPoint: Number(options?.capPoint || 30),
  })

  if (source === 'settled') {
    clearCache()
    eventPipeline.reset()
    uni.showToast({ title: '该场比赛已结算', icon: 'none' })
    setTimeout(() => leaveToRecord(), 800)
    return
  }

  if (source === 'restarted') {
    // 该场已被 restart 重开：云端事件被清，本地缓存属于旧的一局，
    // 整体丢弃，防止复活旧比分甚至 10 秒后把旧结果重新结算回去。
    clearCache()
    eventPipeline.reset()
    cached = null
  }

  if (source === 'server') {
    const recovered = buildRecoveredState(record)
    if (recovered) {
      applyRecoveredState(recovered)
      eventPipeline.restore({
        events: recovered.events,
        nextEventSeq: recovered.nextEventSeq,
        lastSyncedEventSeq: recovered.lastSyncedEventSeq,
      })
    }
  } else if (source === 'local' && restoreStateFromStorage()) {
    eventPipeline.restore({
      events: matchEvents.value,
      nextEventSeq: nextEventSeq.value,
      lastSyncedEventSeq: lastSyncedEventSeq.value,
    })
  } else {
    // fresh / restarted：路由队名【无条件】生效并先于落缓存（B-NEW-1）。
    // 队名 ref 初始值「左队/右队」为真值，不能用 !leftTeam 判断——
    // 上一版误用该守卫导致新比赛全程显示默认名并随事件上云扩散。
    const names = resolveEntryTeamNames(
      { leftTeam: leftTeam.value, rightTeam: rightTeam.value },
      options || {},
      { fresh: true },
    )
    leftTeam.value = names.leftTeam
    rightTeam.value = names.rightTeam
    matchStartTime.value = Date.now()
    saveStateToStorage()
  }

  if (source !== 'fresh' && source !== 'restarted') {
    // 恢复路径：恢复出的名字优先，仅恢复名为空时才用路由名兜底
    const names = resolveEntryTeamNames(
      { leftTeam: leftTeam.value, rightTeam: rightTeam.value },
      options || {},
      { fresh: false },
    )
    leftTeam.value = names.leftTeam
    rightTeam.value = names.rightTeam
  }

  // 刻意不在进入时写 init 基线：首个真实动作（加分/换边/退赛）的 runtime 自带规则与名称，
  // 避免「只是打开页面」就把 0:0 场次锁进签表守卫，也杜绝 record 拉取失败时误写云端。
  if (source !== 'settled' && eventPipeline.hasPendingEvents()) eventPipeline.scheduleFlush(500)
  if (isLocked.value) scheduleAutoSettlement()
}

function leaveToRecord() {
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
}

onLoad(async (options) => {
  routeOptions = options || {}
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
  // 排球 scoreboard 页同款语义：首次抢锁失败即拒绝进入（锁被他人持有时不降级进页面）
  if (!(await setupMatchLock())) return
  await loadMatchState(routeOptions)
})
onShow(() => {
  // lockReady 未就绪说明 onLoad 的首次抢锁还在进行中，绝不并发抢锁
  if (!lockReady || !matchId.value || isReadOnly.value || lockDenied.value || lockGuard.maskMode.value) return
  if (stopHeartbeat || isLocked.value) return
  // 前后台切换后若心跳已停（执裁权需重新取得），静默重取锁并续传未同步事件
  void setupMatchLock().then((acquired) => {
    if (acquired) eventPipeline.scheduleFlush(200)
  })
})

onUnmounted(() => {
  clearAutoSettlementTimer()
  stopMatchLockHeartbeat()
  eventPipeline?.stop()
  lockGuard.stop()
  destroyScoreAnnouncer()
})

onUnload(() => {
  stopMatchLockHeartbeat()
  eventPipeline?.stop()
  lockGuard.stop()
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
    if (!isReadOnly.value && eventPipeline.hasPendingEvents()) {
      // W-NEW-3：刚撤销回 0:0 就离开时，撤销事件可能尚未上云——
      // 此时清缓存会丢撤销事件且云端旧分复活。保留缓存（0:0+pending 撤销是正确事实）、
      // 同步发起冲刷（uni.request 已出栈，页面卸载不回收），下次进入自动续传。
      void eventPipeline.flushPendingEventsImmediately()
    }
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


.match-info {
   grid-column: 3;
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


.action-btn[disabled] {
  background: rgba(150, 160, 170, 0.08);
  border-color: rgba(150, 160, 170, 0.46);
  color: rgba(170, 178, 186, 0.72);
  opacity: 1;
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


.left-side .team-panel {
  margin-left: auto;
}

.right-side .team-panel {
  margin-right: auto;
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
