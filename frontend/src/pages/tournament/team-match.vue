<template>
  <view class="page" :style="pageStyle">
    <LockMask
      v-if="lockGuard.maskMode.value"
      :title="lockGuard.maskTitle.value"
      :message="lockGuard.maskMessage.value"
      :retry-label="lockGuard.maskMode.value === 'denied' ? '重试获取' : '重新获取执裁权'"
      :retry-disabled="lockGuard.retryDisabled.value"
      :retrying="lockGuard.retrying.value"
      @retry="lockGuard.retry"
      @back="lockGuard.back" />
    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载团体赛...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text error">团体赛加载失败</text>
      <button class="retry-btn" @click="fetchDetail">重新加载</button>
    </view>

    <template v-else>
      <view class="header">
        <view class="header-top">
          <text class="back-btn" @click="goBack">返回</text>
          <button v-if="parentMatchFinished" class="lineup-btn" @click="openTeamRecord">查看战报</button>
          <button v-else class="lineup-btn" :disabled="isReadOnly" @click="editLineup">对阵名单填写</button>
        </view>
      </view>

      <scroll-view class="content" scroll-y>
        <view class="score-panel">
          <view class="team-side">
            <text class="team-label">左队</text>
            <text class="team-name">{{ teamName('left') }}</text>
          </view>
          <view class="score-box">
            <text class="score-hint">总比分</text>
            <text class="score">{{ leftTeamWins }} : {{ rightTeamWins }}</text>
          </view>
          <view class="team-side right">
            <text class="team-label">右队</text>
            <text class="team-name">{{ teamName('right') }}</text>
          </view>
        </view>

        <view class="item-list">
          <view class="item-card" v-for="item in sortedItems" :key="item.itemCode">
            <view class="item-head">
              <view>
                <text class="item-title">{{ item.itemName || item.itemCode }}</text>
              </view>
              <text class="item-status" v-if="!item.winnerSide" :class="statusClass(item)">{{ statusText(item) }}</text>
            </view>

            <view class="versus-row">
              <view class="member-side" :class="{ winner: item.winnerSide === 'left' }">
                <text class="members" :class="{ single: isSinglesItem(item) }">{{ memberNames(item.leftMembers) || '待填写' }}</text>
              </view>
              <text class="vs">{{ centerScoreText(item) }}</text>
              <view class="member-side right" :class="{ winner: item.winnerSide === 'right' }">
                <text class="members" :class="{ single: isSinglesItem(item) }">{{ memberNames(item.rightMembers) || '待填写' }}</text>
              </view>
            </view>

            <view class="score-row" v-if="item.childScoreDisplay">
              <text class="score-text">{{ item.childScoreDisplay }}</text>
            </view>

            <button class="start-btn" v-if="!item.winnerSide" :disabled="isReadOnly || isItemDisabled(item)" :loading="startingCode === item.itemCode" @click="startItem(item)">
              {{ buttonText(item) }}
            </button>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import {
  acquireMatchLockWithRetry,
  createMatchLockToken,
  loadMatchLockToken,
  matchLockHeader,
  releaseMatchLock,
  saveMatchLockToken,
  startMatchLockHeartbeat,
} from '@/utils/match-lock'
import LockMask from '@/components/LockMask.vue'
import { useLockGuard } from '@/composables/use-lock-guard'
import { guardProfileBeforeAction } from '@/store/auth'
import { navigateBackOrHome } from '@/utils/back-navigation'
import { request } from '@/utils/request'
import { buildMatchQuery } from '@/utils/query'
import { buildTeamRecordUrl, navigateToTournamentSchedule } from './tournament-navigation'

import { requireMatchOperator } from '@/utils/match-guard'

function buildBasePortraitPageStyle() {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === 'function' ? uni.getWindowInfo() : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    const statusBarHeight = Number(info?.statusBarHeight)
    safeTopPx = Number.isFinite(safeInsetTop) && safeInsetTop > 0 ? safeInsetTop : (Number.isFinite(statusBarHeight) ? statusBarHeight : 0)
  } catch (_) {
    // noop
  }
  return { boxSizing: 'border-box', paddingTop: safeTopPx + 'px' }
}

const pageStyle = buildBasePortraitPageStyle()
const tournamentId = ref('')
const matchId = ref('')
const divisionId = ref('')
const detail = ref({ leftTeam: {}, rightTeam: {}, items: [] })
const loading = ref(true)
const isError = ref(false)
const startingCode = ref('')
const settling = ref(false)
const promptOpen = ref(false)
const loadedOnce = ref(false)
const returningFromChildScoreboard = ref(false)
const sessionLockToken = ref('')
const isReadOnly = ref(false)
let stopHeartbeat = null

const sortedItems = computed(() => {
  const items = Array.isArray(detail.value.items) ? detail.value.items : []
  return [...items].sort((a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0))
})
const leftTeamWins = computed(() => sortedItems.value.filter((item) => item.winnerSide === 'left').length)
const rightTeamWins = computed(() => sortedItems.value.filter((item) => item.winnerSide === 'right').length)
const allItemsFinished = computed(() => {
  const items = sortedItems.value
  return items.length > 0 && items.every((item) => item.winnerSide === 'left' || item.winnerSide === 'right')
})
const parentMatchFinished = computed(() => {
  const status = Number(detail.value.matchStatus || 0)
  return status === 2 || status === 3 || detail.value.winnerSide === 'left' || detail.value.winnerSide === 'right'
})
const totalItemCount = computed(() => sortedItems.value.length)
const winThreshold = computed(() => Math.floor(totalItemCount.value / 2) + 1)
const canSettleEarly = computed(() => {
  return Number(detail.value.stageType || 0) === 1
    && Number(detail.value.tournamentType || 0) !== 2
    && !parentMatchFinished.value
    && !allItemsFinished.value
    && (leftTeamWins.value >= winThreshold.value || rightTeamWins.value >= winThreshold.value)
})
const hasSavedProgress = computed(() => {
  if (parentMatchFinished.value) return false
  const items = Array.isArray(detail.value.items) ? detail.value.items : []
  return items.some((item) => {
    const leftMembers = Array.isArray(item?.leftMembers) ? item.leftMembers : []
    const rightMembers = Array.isArray(item?.rightMembers) ? item.rightMembers : []
    return Number(item?.status || 0) > 0
      || !!item?.childMatchId
      || !!item?.winnerSide
      || leftMembers.length > 0
      || rightMembers.length > 0
  })
})
const resumeNoticeShown = ref(false)

function stopMatchLockHeartbeat() {
  if (!stopHeartbeat) return
  stopHeartbeat()
  stopHeartbeat = null
}

function enterReadOnly() {
  stopMatchLockHeartbeat()
  if (isReadOnly.value) return
  isReadOnly.value = true
  void lockGuard.showLost()
}

function showContinueNotice(content) {
  return new Promise((resolve) => {
    uni.showModal({
      title: '继续执裁',
      content,
      showCancel: false,
      confirmText: '继续执裁',
      success: () => resolve(true),
      fail: () => resolve(true),
    })
  })
}

// 抢锁成功后的会话副作用（setupMatchLock 成功分支与遮罩重试成功共用）
function beginLockSession(result) {
  // 顶替同账号旧设备的统一提示（进入与遮罩重试共用；guard 不再重复弹）
  if (result?.kicked === true) {
    uni.showToast({ title: '已在其他设备打开该比赛，本设备已接管执裁', icon: 'none', duration: 2500 })
  }
  isReadOnly.value = false
  stopMatchLockHeartbeat()
  saveMatchLockToken(matchId.value, sessionLockToken.value)
  stopHeartbeat = startMatchLockHeartbeat(matchId.value, sessionLockToken.value, () => {
    enterReadOnly()
  })
}

async function setupMatchLock() {
  // 锁 token 持久化：同一设备重进复用同一执裁会话标识
  sessionLockToken.value = loadMatchLockToken(matchId.value) || createMatchLockToken()
  try {
    // 带确认抢锁：同账号另一设备持锁时先弹「顶替确认」，探测接口缺失则降级直抢（旧行为）
    const result = await lockGuard.acquireGuarded(
      () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
    )
    if (result?.canceled === true) {
      lockGuard.showDenied('已取消顶替，当前账号另一台设备正在执裁')
      return false
    }
    if (result?.success === true) {
      beginLockSession(result)
      return true
    }
    lockGuard.showDenied('当前团体赛正由其他设备操作，暂时无法取得执裁权')
    return false
  } catch (_) {
    lockGuard.showDenied('暂时无法取得执裁权，请稍后再试')
    return false
  }
}

// 执裁锁遮罩守卫：锁不可得/中途丢失 → 全屏遮罩（取代旧只读模式），重试走顶替确认链路
const lockGuard = useLockGuard({
  getMatchId: () => matchId.value,
  acquire: () => acquireMatchLockWithRetry(matchId.value, sessionLockToken.value),
  onRetrySuccess: async (result) => {
    // 重抢成功必须重新拉取服务端数据：绝不能带着遮罩期间的旧内存状态继续操作
    beginLockSession(result)
    await fetchDetail()
  },
  onBack: () => goBack(),
})
function matchLockRequestOptions(extra = {}) {
  return {
    ...extra,
    header: {
      ...(extra.header || {}),
      ...matchLockHeader(sessionLockToken.value),
    },
  }
}

function teamName(side) {
  const team = side === 'left' ? detail.value.leftTeam : detail.value.rightTeam
  return team?.name || '未知队伍'
}

function memberNames(members) {
  return Array.isArray(members) ? members.map((member) => member?.name).filter(Boolean).join(' / ') : ''
}

function isSinglesItem(item) {
  return Number(item?.playerCount || 1) === 1
}

function itemReady(item) {
  const count = Number(item.playerCount || 1)
  return Array.isArray(item.leftMembers) && item.leftMembers.length === count
    && Array.isArray(item.rightMembers) && item.rightMembers.length === count
}

function statusText(item) {
  if (item.winnerSide === 'left') return teamName('left') + ' 胜'
  if (item.winnerSide === 'right') return teamName('right') + ' 胜'
  if (!itemReady(item)) return '待填写'
  if (Number(item.status || 0) === 1 || item.childMatchId) return '进行中'
  return '待开始'
}

function statusClass(item) {
  return {
    finished: item.winnerSide === 'left' || item.winnerSide === 'right',
    pending: !itemReady(item),
  }
}

function centerScoreText(item) {
  if (item.childLeftGameWins !== undefined && item.childLeftGameWins !== null
    && item.childRightGameWins !== undefined && item.childRightGameWins !== null) {
    return item.childLeftGameWins + ':' + item.childRightGameWins
  }
  return 'vs'
}

function actionText(item) {
  if (item.childMatchId) return '继续比赛'
  if (parentMatchFinished.value) return '团体赛已结束'
  return '开始比赛'
}

function buttonText(item) {
  if (parentMatchFinished.value && !item.childMatchId) return '团体赛已结束'
  return itemReady(item) ? actionText(item) : '先完成对阵名单'
}

function isItemDisabled(item) {
  return startingCode.value === item.itemCode || settling.value || (parentMatchFinished.value && !item.childMatchId)
}

function leadingTeamName() {
  return leftTeamWins.value >= rightTeamWins.value ? teamName('left') : teamName('right')
}

function leadingTeamWins() {
  return Math.max(leftTeamWins.value, rightTeamWins.value)
}

function maybePromptEarlySettlement() {
  if (isReadOnly.value || !canSettleEarly.value || promptOpen.value) return
  promptOpen.value = true
  uni.showModal({
    title: '是否直接结算',
    content: teamName('left') + ' 胜 ' + teamName('right') + '\n' + leadingTeamName() + ' 已取得 ' + leadingTeamWins() + ' 场胜利。你可以直接结算本场团体赛，也可以继续打完剩余项目。',
    cancelText: '继续打完',
    confirmText: '直接结算',
    success: async (res) => {
      if (res.confirm) {
        await settleTeamMatch()
      }
    },
    complete: () => {
      promptOpen.value = false
    },
  })
}

async function settleTeamMatch() {
  if (isReadOnly.value || settling.value) return
  settling.value = true
  try {
    await request('/api/v1/matches/' + matchId.value + '/team-match/settle', matchLockRequestOptions({ method: 'PUT' }))
    stopMatchLockHeartbeat()
    openTeamRecord()
  } finally {
    settling.value = false
  }
}

function returnToTournamentSchedule() {
  navigateToTournamentSchedule({
    pages: typeof getCurrentPages === 'function' ? getCurrentPages() : [],
    tournamentId: tournamentId.value,
    tournamentType: detail.value?.tournamentType,
    divisionId: divisionId.value,
    uniApi: uni,
  })
}

function goBack() {
  navigateBackOrHome()
}

function openScoreboard(params) {
  const query = buildMatchQuery(params)
  returningFromChildScoreboard.value = true
  uni.navigateTo({
    url: '/pages/scoreboard/index?' + query,
    fail: () => {
      returningFromChildScoreboard.value = false
    },
  })
}

async function editLineup() {
  if (isReadOnly.value) return
  if (!(await guardProfileBeforeAction('请先完善个人资料，再填写对阵名单'))) return
  stopMatchLockHeartbeat()
  await releaseMatchLock(matchId.value, sessionLockToken.value)
  uni.navigateTo({
    url: '/pages/tournament/team-lineup?tournamentId=' + encodeURIComponent(tournamentId.value)
      + '&matchId=' + encodeURIComponent(matchId.value)
      // 带上组别：名单页保存后若栈内比赛页未命中需 redirect 新建，否则新页面会丢组别
      + (divisionId.value ? '&divisionId=' + encodeURIComponent(divisionId.value) : ''),
    fail: () => {
      void setupMatchLock()
    },
  })
}

function openTeamRecord() {
  uni.navigateTo({
    url: buildTeamRecordUrl({
      tournamentId: tournamentId.value,
      matchId: matchId.value,
      divisionId: divisionId.value,
    }),
  })
}

async function fetchDetail() {
  if (!matchId.value) return
  loading.value = !loadedOnce.value
  isError.value = false
  try {
    const data = await request('/api/v1/matches/' + matchId.value + '/team-lineup', { method: 'GET' })
    detail.value = data || { leftTeam: {}, rightTeam: {}, items: [] }
    loadedOnce.value = true
    if (!isReadOnly.value && !resumeNoticeShown.value && hasSavedProgress.value) {
      resumeNoticeShown.value = true
      await showContinueNotice('本场比赛已有保存的比赛进度，继续执裁将从当前进度进入。')
    }
    const shouldReturnAfterChild = returningFromChildScoreboard.value
    returningFromChildScoreboard.value = false
    if (shouldReturnAfterChild && parentMatchFinished.value) {
      openTeamRecord()
      return
    }
    setTimeout(maybePromptEarlySettlement, 0)
  } catch (_) {
    isError.value = true
  } finally {
    loading.value = false
  }
}

async function startItem(item) {
  if (isReadOnly.value) return
  if (parentMatchFinished.value && !item.childMatchId) {
    uni.showToast({ title: '团体赛已结束', icon: 'none' })
    return
  }
  if (!itemReady(item)) {
    uni.showToast({ title: '请先完成该项目对阵名单', icon: 'none' })
    return
  }
  if (!(await guardProfileBeforeAction('请先完善个人资料，再进入记分'))) return
  if (startingCode.value) return
  startingCode.value = item.itemCode
  try {
    const data = await request('/api/v1/matches/' + matchId.value + '/team-items/' + item.itemCode + '/start', matchLockRequestOptions({ method: 'PUT' }))
    openScoreboard({
      matchId: data.childMatchId,
      source: 'teamMatch',
      leftName: data.leftName,
      rightName: data.rightName,
      bestOf: data.bestOf || 3,
      gamesToWin: data.gamesToWin || 2,
      pointsToWin: data.pointsToWin || 21,
      decidingPointsToWin: data.decidingPointsToWin || '',
      enableDeuce: data.enableDeuce === false ? '0' : '1',
      capPoint: data.capPoint || 30,
    })
  } finally {
    startingCode.value = ''
  }
}

onLoad(async (options) => {
  tournamentId.value = options?.tournamentId || ''
  matchId.value = options?.matchId || ''
  divisionId.value = options?.divisionId || ''
  if (!matchId.value) {
    loading.value = false
    isError.value = true
    uni.showToast({ title: '缺少比赛ID', icon: 'none' })
    return
  }
  if (!(await guardProfileBeforeAction('请先完善个人资料，再进入团体赛操作'))) {
    navigateBackOrHome()
    return
  }

  const allowed = await requireMatchOperator(matchId.value)
  if (!allowed) {
    setTimeout(() => navigateBackOrHome(), 1500)
    return
  }
  // 首抢失败（拒绝遮罩）不加载任何数据；重试成功由 onRetrySuccess 重新拉取
  if (!(await setupMatchLock())) return
  fetchDetail()
})

onShow(async () => {
  // 遮罩期间不自动重抢：重新取锁必须由遮罩按钮走「顶替确认」链路
  if (loadedOnce.value && !lockGuard.maskMode.value) {
    if (!stopHeartbeat) {
      const acquired = await setupMatchLock()
      if (!acquired) return
    }
    fetchDetail()
  }
})

onUnload(() => {
  stopMatchLockHeartbeat()
  lockGuard.stop()
  void releaseMatchLock(matchId.value, sessionLockToken.value)
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  height: 100vh;
  background: #13202d;
  color: #fff;
  display: flex;
  flex-direction: column;
}

.state-layer {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
}

.state-text {
  color: rgba(255, 255, 255, 0.72);
  font-size: 28rpx;
}

.state-text.error {
  color: #ff8c00;
}

.retry-btn,
.lineup-btn,
.start-btn {
  border: none;
}

.retry-btn::after,
.lineup-btn::after,
.start-btn::after {
  border: none;
}

.retry-btn {
  width: 240rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  background: #ff8c00;
  color: #13202d;
  font-weight: 800;
}

.header {
  flex-shrink: 0;
  padding: 0 24rpx 18rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 18rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
}

.lineup-btn {
  width: 190rpx;
  height: 64rpx;
  line-height: 64rpx;
  margin: 0;
  padding: 0;
  border-radius: 14rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 25rpx;
}

.content {
  flex: 1;
  min-height: 0;
  height: 0;
}

.score-panel {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.06);
  display: grid;
  grid-template-columns: 1fr 150rpx 1fr;
  align-items: center;
  gap: 16rpx;
}

.team-side {
  min-width: 0;
}

.team-side.right {
  text-align: right;
}

.score-hint {
  display: block;
  color: rgba(255, 255, 255, 0.52);
  font-size: 32rpx;
  line-height: 1.25;
}

.team-label {
  display: block;
  color: rgba(255, 255, 255, 0.52);
  font-size: 32rpx;
  line-height: 1.25;
}

.team-name {
  display: block;
  margin-top: 6rpx;
  font-size: 42rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-box {
  text-align: center;
}

.score {
  display: block;
  margin-top: 6rpx;
  color: #ff8c00;
  font-size: 42rpx;
  font-weight: 900;
  line-height: 1.25;
}

.item-list {
  padding: 0 24rpx 32rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.item-card {
  padding: 22rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.055);
  border: 1rpx solid rgba(255, 140, 0, 0.12);
}

.item-head,
.versus-row,
.score-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.item-title {
  display: block;
  color: rgba(255, 255, 255, 0.52);
  font-size: 32rpx;
  font-weight: 400;
  line-height: 1.25;
}

.item-status {
  flex-shrink: 0;
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.76);
  font-size: 22rpx;
  font-weight: 800;
}

.item-status.finished {
  color: #7ee787;
  background: rgba(76, 217, 100, 0.15);
}

.item-status.pending {
  color: #ffcc66;
  background: rgba(255, 204, 102, 0.12);
}

.versus-row {
  margin-top: 20rpx;
  align-items: center;
}

.member-side {
  flex: 1;
  min-width: 0;
}

.member-side.right {
  text-align: right;
}

.member-side.winner .members {
  color: #ffb347;
}

.members {
  display: block;
  min-height: 45rpx;
  font-size: 33rpx;
  font-weight: 700;
  line-height: 1.35;
}

.members.single {
  font-size: 38rpx;
}

.vs {
  align-self: center;
  color: #ff8c00;
  font-size: 33rpx;
  line-height: 1.35;
  font-weight: 900;
}

.score-row {
  margin-top: 16rpx;
  justify-content: center;
}

.score-text {
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 700;
}

.start-btn {
  margin-top: 20rpx;
  width: 100%;
  height: 76rpx;
  line-height: 76rpx;
  border: 2rpx solid rgba(255, 179, 71, 0.72);
  border-radius: 16rpx;
  background: transparent;
  color: #ffb347;
  font-size: 27rpx;
  font-weight: 900;
}

.start-btn[disabled] {
  opacity: 0.72;
}
</style>
