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


     <view class="main-panels">
      <view class="score-side left-side">
        <view class="plus-orb" :class="{ disabled: isReadOnly || isLocked || isPromptActive }" @click="addScore('left')"><text>+1</text></view>
        <view class="score-pair">
          <view class="score-cell game">
            <text class="score-num game">{{ leftScore }}</text>
          </view>
          <view class="score-cell wins">
            <text class="score-num">{{ leftGameWins }}</text>
          </view>
        </view>
      </view>

      <view class="score-side right-side">
        <view class="score-pair">
          <view class="score-cell wins">
            <text class="score-num">{{ rightGameWins }}</text>
          </view>
          <view class="score-cell game">
            <text class="score-num game">{{ rightScore }}</text>
          </view>
        </view>
        <view class="plus-orb" :class="{ disabled: isReadOnly || isLocked || isPromptActive }" @click="addScore('right')"><text>+1</text></view>
      </view>
    </view>

    <view class="court-card">
      <view class="court-header">
        <text class="court-title">场地站位</text>
        <view class="first-serve-picker" v-if="showFirstServePicker">
          <text class="first-serve-label">谁先发球？</text>
          <button class="first-serve-btn" :class="{ active: serveSide === 'left' }" @click="pickFirstServe('left')">{{ leftTeam }}</button>
          <button class="first-serve-btn" :class="{ active: serveSide === 'right' }" @click="pickFirstServe('right')">{{ rightTeam }}</button>
        </view>
        <button class="court-swap-btn" v-if="canSwapCourt" @click="openCourtSwapSheet">交换站位</button>
      </view>
      <view class="court-board">
        <template v-if="!courtView.degraded">
          <view class="court-outline"></view>
          <view class="court-line long-left"></view>
          <view class="court-line short-left"></view>
          <view class="court-line short-right"></view>
          <view class="court-line long-right"></view>
          <view class="court-line singles-top"></view>
          <view class="court-line singles-bottom"></view>
          <view class="court-line center-left"></view>
          <view class="court-line center-right"></view>
          <view class="court-zone left">
            <view
              class="court-slot"
              v-for="slot in courtView.halves.left.slots"
              :key="'left-' + slot.court"
              :class="slotRoleClass(slot)"
            >
              <text class="slot-player" :class="{ empty: !slot.player }">{{ slot.player || '—' }}</text>
              <text class="slot-role-vertical" v-if="slot.role === 'server'">发球</text>
              <text class="slot-role-vertical receiver" v-else-if="slot.role === 'receiver'">接发</text>
            </view>
          </view>
          <view class="court-net"></view>
          <view class="court-zone right">
            <view
              class="court-slot"
              v-for="slot in courtView.halves.right.slots"
              :key="'right-' + slot.court"
              :class="slotRoleClass(slot)"
            >
              <text class="slot-player" :class="{ empty: !slot.player }">{{ slot.player || '—' }}</text>
              <text class="slot-role-vertical" v-if="slot.role === 'server'">发球</text>
              <text class="slot-role-vertical receiver" v-else-if="slot.role === 'receiver'">接发</text>
            </view>
          </view>
        </template>
        <template v-else>
          <view class="court-outline"></view>
          <view class="court-zone degraded left">
            <text class="court-team-name">{{ leftTeam }}</text>
            <text class="court-degraded-tip">双打 · 未录入站位</text>
          </view>
          <view class="court-net"></view>
          <view class="court-zone degraded right">
            <text class="court-team-name">{{ rightTeam }}</text>
            <text class="court-degraded-tip">双打 · 未录入站位</text>
          </view>
        </template>
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
        <view class="final-switch-body">
          <text class="final-switch-tip">第 {{ currentGameNo }} 局达到 {{ finalGameSideSwitchThreshold }} 分</text>
        </view>
        <view class="final-switch-actions">
          <button class="final-switch-btn secondary" @click="handleFinalGameSideSwitch(false)">不换边继续</button>
          <button class="final-switch-btn" @click="handleFinalGameSideSwitch(true)">换边</button>
        </view>
      </view>
    </view>

    <view v-if="isGameEndPromptActive" class="final-switch-overlay">
      <view class="final-switch-card">
        <text class="final-switch-title">第 {{ currentGameNo }} 局结束</text>
        <view class="final-switch-body">
          <text class="final-switch-tip">{{ leftTeam }} {{ leftScore }} : {{ rightScore }} {{ rightTeam }}</text>
        </view>
        <view class="final-switch-actions">
          <button class="final-switch-btn" @click="confirmGameEnd">换边继续</button>
        </view>
      </view>
    </view>

    <view v-if="courtPlacementPending && !isPromptActive" class="placement-overlay">
      <view class="placement-card">
        <text class="placement-title">双打开局站位</text>
        <text class="placement-tip">按挑边结果选择先发球方，并确认各队谁站右发球区（0 分从右发球区发球，接发球员斜对角站位）</text>
        <view class="placement-row">
          <text class="placement-row-label">先发球</text>
          <button class="placement-pick-btn" :class="{ active: placementDraft.serveSide === 'left' }" @click="placementDraft.serveSide = 'left'">{{ leftTeam }}</button>
          <button class="placement-pick-btn" :class="{ active: placementDraft.serveSide === 'right' }" @click="placementDraft.serveSide = 'right'">{{ rightTeam }}</button>
        </view>
        <view class="placement-row" v-for="side in ['left', 'right']" :key="'placement-' + side">
          <text class="placement-row-label">{{ side === 'left' ? leftTeam : rightTeam }}</text>
          <view class="placement-courts">
            <text class="placement-court">右发球区：{{ placementMember(side, 'right') }}</text>
            <text class="placement-court">左发球区：{{ placementMember(side, 'left') }}</text>
          </view>
          <button class="placement-swap-btn" @click="togglePlacementAnchor(side)">交换</button>
        </view>
        <button class="placement-confirm-btn" :disabled="!placementDraft.serveSide" @click="confirmCourtPlacement">确认，开始记分</button>
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
import { reactive, ref, computed, onUnmounted } from 'vue'
import { onBackPress, onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { guardProfileBeforeAction } from '@/store/auth'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'
import { useScoreAnnouncer } from '@/composables/useScoreAnnouncer'
import { useScoreboardState } from './use-scoreboard-state'
import { createMatchEventPipeline } from './match-events'
import { buildRecoveredState, decideEntrySource, resolveEntryTeamNames } from './badminton-recovery'
import { deriveCourtView, parseCourtMembersParam } from './court-position'

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
// 2026-10-25 E2E 实测回归修复：92374dc 重构误删本行，导致 setup 引用未定义的
// announceScore/isScoreMuted 白屏（无头 fuzzer 只驱动状态机，页面层未覆盖）
const { isMuted: isScoreMuted, toggleMuted: toggleScoreMuted, announceScore, destroyScoreAnnouncer } = useScoreAnnouncer()

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
  // 四半区站位状态与转移
  courtMode,
  courtPlaced,
  leftCourtMembers,
  rightCourtMembers,
  leftAnchorIndex,
  rightAnchorIndex,
  isDoublesCourt,
  courtPlacementPending,
  applyCourtContext,
  setupDoublesCourt,
  swapCourtPlayers,
  setFirstServeSide,
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
  // W2：离线会话中比赛被管理端 restart 清空 → 冲刺前探测发现后丢弃本地旧一局，防复活旧比分
  onServerEventsCleared: () => {
    clearCache()
    resetMatchState()
    eventPipeline.reset()
    uni.showToast({ title: '比赛已被重开，本地进度已清零', icon: 'none' })
  },
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
  // 探测头带本页 token 换回 sameToken：转交锁/幂等重进由服务端判定是否弹顶替确认
  getLockToken: () => sessionLockToken.value,
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

// ==== 四半区站位 UI 胶水 ====
// 沙盘视图全部由「比分奇偶 + 每侧锚点」纯函数推导（court-position.js），
// 页面不另存任何站位状态，与记分状态机同源
const courtView = computed(() => deriveCourtView({
  mode: courtMode.value,
  placed: courtPlaced.value,
  leftMembers: leftCourtMembers.value,
  rightMembers: rightCourtMembers.value,
  leftAnchor: leftAnchorIndex.value,
  rightAnchor: rightAnchorIndex.value,
  serveSide: serveSide.value,
  leftScore: leftScore.value,
  rightScore: rightScore.value,
  leftTeamName: leftTeam.value,
  rightTeamName: rightTeam.value,
}))

function slotRoleClass(slot) {
  return {
    'role-server': slot.role === 'server',
    'role-receiver': slot.role === 'receiver',
  }
}

// 单打挑边：仅第一局第一分前出现（规则上先发球方由挑边决定，此前恒默认左方）
const showFirstServePicker = computed(() => courtMode.value === 'singles'
  && Number(currentGameNo.value) === 1
  && !hasPointStarted.value
  && !gameScores.value.length
  && !isLocked.value
  && !isPromptActive.value)

function pickFirstServe(side) {
  if (side === serveSide.value) return
  scoreboard.setFirstServeSide(side)
}

// 双打开局站位录入（courtPlacementPending 为 true 时强制弹窗，确认后落 lineup_snapshot 事件）
const placementDraft = reactive({ serveSide: '', leftAnchor: 0, rightAnchor: 0 })

function placementMember(side, court) {
  const members = side === 'left' ? leftCourtMembers.value : rightCourtMembers.value
  if (members.length !== 2) return '—'
  const anchor = side === 'left' ? placementDraft.leftAnchor : placementDraft.rightAnchor
  const index = court === 'right' ? anchor : 1 - anchor
  return members[index] || '—'
}

function togglePlacementAnchor(side) {
  if (side === 'left') {
    placementDraft.leftAnchor = 1 - placementDraft.leftAnchor
  } else {
    placementDraft.rightAnchor = 1 - placementDraft.rightAnchor
  }
}

function confirmCourtPlacement() {
  if (!placementDraft.serveSide) return
  const ok = setupDoublesCourt({
    serveSide: placementDraft.serveSide,
    leftAnchor: placementDraft.leftAnchor,
    rightAnchor: placementDraft.rightAnchor,
  })
  if (!ok) {
    uni.showToast({ title: '站位确认失败，请重试', icon: 'none' })
  }
}

// 站位纠错入口（Law 12：站错区发现后纠正站位、比分有效；也用于局间重排 Law 11.6）
const canSwapCourt = computed(() => isDoublesCourt.value
  && courtPlaced.value
  && !isLocked.value
  && !isPromptActive.value
  && !isReadOnly.value)

function openCourtSwapSheet() {
  if (!canSwapCourt.value) return
  uni.showActionSheet({
    itemList: [`交换 ${leftTeam.value} 两人左右半区`, `交换 ${rightTeam.value} 两人左右半区`],
    success: (res) => {
      if (res.tapIndex === 0) swapCourtPlayers('left')
      if (res.tapIndex === 1) swapCourtPlayers('right')
    },
  })
}

/** team-match 双打子项经路由带成员名单与 playerCount；已恢复出的站位优先，路由只补位 */
function applyCourtContextFromRoute(options) {
  if (Number(options?.playerCount || 1) !== 2) return
  const leftMembers = parseCourtMembersParam(options?.leftMembers)
  const rightMembers = parseCourtMembersParam(options?.rightMembers)
  if (leftMembers.length !== 2 || rightMembers.length !== 2) return
  applyCourtContext({ mode: 'doubles', leftMembers, rightMembers })
}

/**
 * 个人赛双打：record 详情带 playerCountPerParticipant=2 与双方成员名单
 * （个人赛双打的成员明细存 tournament_team_member，随参赛方装配）。
 * 团体赛子项的 record 成员=全队 roster，不在此判定（由路由显式 playerCount 触发）。
 */
function applyCourtContextFromRecord(record) {
  if (Number(record?.playerCountPerParticipant || 1) !== 2) return
  const pickNames = (participant) => (Array.isArray(participant?.members)
    ? participant.members.map((member) => member?.name).filter(Boolean)
    : [])
  const leftMembers = pickNames(record?.left)
  const rightMembers = pickNames(record?.right)
  if (leftMembers.length !== 2 || rightMembers.length !== 2) return
  applyCourtContext({ mode: 'doubles', leftMembers, rightMembers })
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
  // W2：record 拉取失败（离线）的会话中，服务端状态未知——冲刷 pending 前管道会先探测，
  // 防「restart 清空 + 离线续传」把旧一局事件写进空事件表
  eventPipeline.setServerStateConfirmed(record != null)
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

  // 站位上下文（双打成员/模式）：路由（团体赛子项）与 record（个人赛双打）都只补位，
  // 已恢复出的站位（含锚点/已确认）优先不被覆盖
  applyCourtContextFromRoute(options)
  applyCourtContextFromRecord(record)

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
  lockGuard.stop()
  // B1 先冲刷后释锁：释锁后 PUT /events 必 403，未同步事件必须赶在释锁前上云，
  // 冲刷与释锁不能并发赛跑（释锁先到则事件全灭）。已出栈的请求不随页面卸载回收
  // （W-NEW-3 佐证），发起后即可继续卸载；冲刷失败释锁照发——releaseMatchLock
  // 本就吞错 best-effort，未同步事件留在缓存由下次进入续传，75s 租约兜底
  const flushed = eventPipeline ? eventPipeline.flushPendingEventsImmediately() : Promise.resolve()
  void flushed
    .catch(() => {})
    .finally(() => {
      releaseMatchLock(matchId.value, sessionLockToken.value)
      // 释锁后 in-flight 冲刷若失败，flushInternal 的 catch 在下方同步 stop() 之后、
      // 冲刷 promise 结算之时才 arm 重试定时器，而释锁后每次重试必 403（写守卫），
      // 会形成 ≤30s 一次的无界后台循环钉住闭包，故结算后补一次 stop 掐断；
      // 数据兜底仍是本地缓存 + 下次进入续传
      eventPipeline?.stop()
    })
  // stop 只清防抖/重试定时器，必须放在冲刷发起之后：冲刷已同步把请求出栈，
  // 若先 stop 会掐掉防抖定时器，还挂在防抖上的 pending 事件便再无触发时机
  eventPipeline?.stop()
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
    // W-NEW-3：刚撤销回 0:0 就离开时，撤销事件可能尚未上云——此时清缓存会丢撤销
    // 事件且云端旧分复活，故保留缓存（0:0+pending 撤销是正确事实）。冲刷不再由
    // 各出口自行发起，已统一收口到 onUnload 的「先冲刷后释锁」链（此处 return false
    // 放行离页后必经 onUnload），下次进入自动续传。
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
  top: 4px;
  left: 10rpx;
  z-index: 10;
  display: flex;
  align-items: center;
}

.top-flow-row {
  position: absolute;
  top: 4px;
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
  font-size: 11px;
  white-space: nowrap;
  pointer-events: none;
}

.game-tag {
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
  height: 24px;
  line-height: 24px;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 11px;
}

.top-center-actions .action-btn {
  margin: 0;
  min-width: 52rpx;
  height: 24px;
  line-height: 24px;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 11px;
}

.top-center-actions .center-action-btn {
  min-width: 52rpx;
}

.top-center-actions .icon-action-btn {
  min-width: 30rpx;
  width: 30rpx;
  padding: 0;
  font-size: 12px;
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
  height: 28.67%;
  display: flex;
  padding: 42px 36rpx 6px;
  box-sizing: border-box;
  gap: 10rpx;
}


.score-side {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: flex-start;
}

.left-side {
  justify-content: flex-end;
}

.right-side {
  justify-content: flex-start;
}

/* +1 加分圆钮（灰白描边椭圆，点击给该侧加一分），旁列局内比分格（橙框，局分格等比放大）与局分格 */
.plus-orb {
  flex-shrink: 0;
  width: 148px;
  height: 75px;
  /* 胶囊形：圆角=高度一半，左右端为半圆，比椭圆两端更圆润 */
  border-radius: 37.5px;
  border: 1px solid rgba(255, 255, 255, 0.3);
  background: rgba(255, 255, 255, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28px;
  font-weight: 400;
  color: rgba(255, 255, 255, 0.85);
}

.left-side .plus-orb {
  margin-right: 15px;
}

.right-side .plus-orb {
  margin-left: 15px;
}

.plus-orb.disabled {
  opacity: 0.4;
}

.score-pair {
  display: flex;
  align-items: flex-start;
  gap: 10rpx;
}

.score-cell.game {
  box-sizing: border-box;
  border: 2px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10rpx;
  height: 60px;
  padding: 0 10rpx;
  min-width: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-cell.wins {
  box-sizing: border-box;
  border: 2px solid rgba(255, 255, 255, 0.22);
  background: rgba(255, 255, 255, 0.04);
  border-radius: 10rpx;
  height: 40px;
  padding: 0 8rpx;
  min-width: 32px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.score-num {
  font-size: 22px;
  font-weight: 700;
  line-height: 1.1;
  color: rgba(255, 255, 255, 0.75);
}

.score-num.game {
  font-size: 32px;
  line-height: 1;
  color: #ffffff;
}

/* ==== 四半区沙盘（上比分、下沙盘）==== */
.court-card {
  position: absolute;
  left: 60rpx;
  right: 60rpx;
  top: 28.67%;
  bottom: 38px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-height: 0;
  z-index: 5;
}

.court-header {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  flex-shrink: 0;
}

.court-title {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 600;
  color: rgba(255, 255, 255, 0.55);
}

.court-swap-btn {
  flex-shrink: 0;
  height: 24px;
  line-height: 24px;
  padding: 0 12rpx;
  margin: 0 0 0 auto;
  font-size: 11px;
  color: #ff8c00;
  border: 1rpx solid rgba(255, 140, 0, 0.6);
  border-radius: 8rpx;
  background: rgba(255, 140, 0, 0.08);
}

.court-swap-btn::after,
.first-serve-btn::after,
.placement-pick-btn::after,
.placement-swap-btn::after,
.placement-confirm-btn::after {
  border: none;
}

.first-serve-picker {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.first-serve-label {
  flex-shrink: 0;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.55);
}

.first-serve-btn {
  height: 24px;
  line-height: 24px;
  padding: 0 12rpx;
  margin: 0;
  max-width: 200rpx;
  font-size: 10px;
  color: rgba(255, 255, 255, 0.8);
  border: 1rpx solid rgba(255, 255, 255, 0.25);
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.06);
  white-space: nowrap;
  overflow: hidden;
}

.first-serve-btn.active {
  color: #ff8c00;
  border-color: #ff8c00;
  background: rgba(255, 140, 0, 0.12);
}

/* 标准羽毛球场俯视线图（BWF 13.4m×6.1m 等比换算，横向拉伸适配横屏条带；
   竖线自左向右：后发球线 5.67% / 前发球线 41%（较标准 35.22% 更贴近网）/ 网 50% / 59% / 94.33%） */
.court-board {
  position: relative;
  flex: 1;
  min-height: 0;
  border-radius: 6rpx;
  background: rgba(94, 197, 121, 0.06);
}

.court-outline {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  border: 2px solid rgba(255, 255, 255, 0.35);
  border-radius: 6rpx;
  pointer-events: none;
}

.court-line {
  position: absolute;
  background: rgba(255, 255, 255, 0.25);
  pointer-events: none;
  /* 场地标线画在站位格上层，避免被半透明格底盖掉 */
  z-index: 3;
}

.court-line.long-left,
.court-line.long-right,
.court-line.short-left,
.court-line.short-right {
  top: 0;
  bottom: 0;
  width: 1px;
}

.court-line.long-left {
  left: 5.67%;
}

.court-line.long-right {
  left: 94.33%;
}

.court-line.short-left {
  left: 41%;
}

.court-line.short-right {
  left: 59%;
}

.court-line.singles-top,
.court-line.singles-bottom {
  left: 0;
  right: 0;
  height: 1px;
}

.court-line.singles-top {
  top: 7.54%;
}

.court-line.singles-bottom {
  top: 92.46%;
}

/* 中线：从本侧底线延伸到前发球线，不穿过两前发球线之间（不贯网） */
.court-line.center-left,
.court-line.center-right {
  top: 50%;
  width: 41%;
  height: 1px;
}

.court-line.center-left {
  left: 0;
}

.court-line.center-right {
  left: 59%;
}

.court-zone {
  position: absolute;
  top: 0;
  bottom: 0;
  display: flex;
  flex-direction: column;
  z-index: 1;
}

.court-zone.left {
  left: 5.67%;
  width: 35.33%;
}

.court-zone.right {
  left: 59%;
  width: 35.33%;
}

.court-zone.degraded {
  align-items: center;
  justify-content: center;
  gap: 3px;
}

.court-zone.degraded.left {
  left: 4%;
  width: 42%;
}

.court-zone.degraded.right {
  left: 54%;
  width: 42%;
}

.court-net {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 2px;
  margin-left: -1px;
  border-radius: 1px;
  z-index: 2;
  background: repeating-linear-gradient(
    180deg,
    rgba(255, 255, 255, 0.85) 0,
    rgba(255, 255, 255, 0.85) 6px,
    rgba(255, 255, 255, 0.2) 6px,
    rgba(255, 255, 255, 0.2) 12px
  );
}

.court-slot {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px dashed rgba(255, 255, 255, 0.14);
  border-radius: 4rpx;
  background: rgba(255, 255, 255, 0.03);
}

.court-slot.role-server {
  border: 2px solid #ff8c00;
}

.court-slot.role-receiver {
  border: 2px solid rgba(148, 163, 184, 0.55);
  background: rgba(148, 163, 184, 0.18);
}

/* 发球/接发纵向字：贴本区前发球线（靠网一侧），窄宽强制逐字竖排 */
.slot-role-vertical {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 20px;
  font-size: 18px;
  line-height: 22px;
  font-weight: 700;
  text-align: center;
  color: #ff8c00;
  word-break: break-all;
}

.court-zone.left .slot-role-vertical {
  right: 2px;
}

.court-zone.right .slot-role-vertical {
  left: 2px;
}

.slot-role-vertical.receiver {
  color: rgba(203, 213, 225, 0.8);
}

.slot-player {
  max-width: 92%;
  font-size: 32px;
  line-height: 38px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-player.empty {
  color: rgba(255, 255, 255, 0.2);
}

.court-team-name {
  max-width: 90%;
  font-size: 14px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.court-degraded-tip {
  font-size: 10px;
  color: rgba(255, 255, 255, 0.4);
}

/* ==== 双打开局站位录入弹窗 ==== */
.placement-overlay {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 24;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 10px 20rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.78);
}

.placement-card {
  width: 76vw;
  max-width: 640rpx;
  max-height: 90vh;
  display: flex;
  flex-direction: column;
  gap: 0;
  padding: 16px 26rpx;
  box-sizing: border-box;
  border-radius: 22rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.35);
  overflow: hidden;
}

.placement-title {
  flex-shrink: 0;
  font-size: 18px;
  line-height: 1.25;
  font-weight: 700;
  text-align: center;
}

.placement-body {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: 8px;
  overflow-y: auto;
}

.placement-tip {
  font-size: 12px;
  line-height: 1.4;
  color: rgba(255, 255, 255, 0.72);
  text-align: center;
}

.placement-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 6px 16rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.14);
  border-radius: 12rpx;
}

.placement-row-label {
  flex-shrink: 0;
  max-width: 160rpx;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.placement-pick-btn {
  flex: 1;
  height: 30px;
  line-height: 30px;
  margin: 0;
  min-width: 0;
  font-size: 12px;
  color: #ffffff;
  border: 1rpx solid rgba(255, 255, 255, 0.25);
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.06);
  white-space: nowrap;
  overflow: hidden;
}

.placement-pick-btn.active {
  color: #ff8c00;
  font-weight: 700;
  border-color: #ff8c00;
  background: rgba(255, 140, 0, 0.18);
}

.placement-courts {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.placement-court {
  font-size: 11px;
  line-height: 14px;
  color: rgba(255, 255, 255, 0.82);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.placement-swap-btn {
  flex-shrink: 0;
  height: 26px;
  line-height: 26px;
  padding: 0 14rpx;
  margin: 0;
  font-size: 11px;
  color: #ffffff;
  border: 1rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.08);
}

.placement-confirm-btn {
  flex-shrink: 0;
  height: 44px;
  line-height: 44px;
  margin: 10px 0 0;
  font-size: 15px;
  font-weight: 700;
  color: #ffffff;
  border: none;
  border-radius: 14rpx;
  background: rgba(255, 140, 0, 0.25);
  box-shadow: inset 0 0 0 1rpx rgba(255, 140, 0, 0.55);
}

.placement-confirm-btn[disabled] {
  opacity: 0.4;
}

.games-strip {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 16px;
  display: flex;
  justify-content: center;
  gap: 8rpx;
  z-index: 9;
}

.game-pill {
  display: flex;
  gap: 6rpx;
  padding: 3px 9rpx;
  border-radius: 7rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.35);
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 10px;
  line-height: 12px;
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
  padding: 10px 20rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.72);
}

.final-switch-card {
  width: 72vw;
  max-width: 560rpx;
  max-height: 90vh;
  border-radius: 22rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.16);
  background: #22364c;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.35), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
  padding: 16px 28rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  overflow: hidden;
}

.final-switch-title {
  flex-shrink: 0;
  font-size: 20px;
  line-height: 1.25;
  font-weight: 700;
}

.final-switch-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  overflow-y: auto;
}

.final-switch-tip {
  font-size: 13px;
  line-height: 1.4;
  text-align: center;
  color: rgba(255, 255, 255, 0.86);
}

.final-switch-actions {
  flex-shrink: 0;
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 18rpx;
  margin-top: 10px;
}

.final-switch-btn {
  flex: 1;
  max-width: 260rpx;
  height: 42px;
  line-height: 42px;
  border-radius: 14rpx;
  border: none;
  background: rgba(255, 140, 0, 0.2);
  color: #ffffff;
  font-size: 14px;
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
