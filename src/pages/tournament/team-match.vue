<template>
  <view class="page" :style="pageStyle">
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
          <button class="lineup-btn" @click="editLineup">对阵名单填写</button>
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

            <button class="start-btn" v-if="!item.winnerSide" :disabled="isItemDisabled(item)" :loading="startingCode === item.itemCode" @click="startItem(item)">
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
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { buildMatchQuery } from '@/utils/query'
import { navigateToTournamentSchedule } from './tournament-navigation'

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
const detail = ref({ leftTeam: {}, rightTeam: {}, items: [] })
const loading = ref(true)
const isError = ref(false)
const startingCode = ref('')
const settling = ref(false)
const promptOpen = ref(false)
const loadedOnce = ref(false)
const returningFromChildScoreboard = ref(false)

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
const parentMatchFinished = computed(() => Number(detail.value.matchStatus || 0) === 2 || Number(detail.value.matchStatus || 0) === 3)
const canSettleEarly = computed(() => {
  return Number(detail.value.stageType || 0) === 1
    && !parentMatchFinished.value
    && !allItemsFinished.value
    && (leftTeamWins.value >= 3 || rightTeamWins.value >= 3)
})

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

function continueStorageKey() {
  return 'team_match_continue_remaining_' + matchId.value
}

function hasChosenContinue() {
  try {
    return uni.getStorageSync(continueStorageKey()) === '1'
  } catch (_) {
    return false
  }
}

function rememberContinue() {
  try {
    uni.setStorageSync(continueStorageKey(), '1')
  } catch (_) {
    // noop
  }
}

function clearContinueChoice() {
  try {
    uni.removeStorageSync(continueStorageKey())
  } catch (_) {
    // noop
  }
}

function leadingTeamName() {
  return leftTeamWins.value >= 3 ? teamName('left') : teamName('right')
}

function maybePromptEarlySettlement() {
  if (!canSettleEarly.value || promptOpen.value || hasChosenContinue()) return
  promptOpen.value = true
  uni.showModal({
    title: '是否直接结算',
    content: leadingTeamName() + ' 已取得 3 场胜利。你可以直接结算本场团体赛，也可以继续打完剩余项目。',
    cancelText: '继续打完',
    confirmText: '直接结算',
    success: async (res) => {
      if (res.confirm) {
        await settleTeamMatch()
      } else {
        rememberContinue()
      }
    },
    complete: () => {
      promptOpen.value = false
    },
  })
}

async function settleTeamMatch() {
  if (settling.value) return
  settling.value = true
  try {
    await request('/api/v1/matches/' + matchId.value + '/team-match/settle', { method: 'PUT' })
    clearContinueChoice()
    returnToTournamentSchedule()
  } finally {
    settling.value = false
  }
}

function returnToTournamentSchedule() {
  navigateToTournamentSchedule({
    pages: typeof getCurrentPages === 'function' ? getCurrentPages() : [],
    tournamentId: tournamentId.value,
    tournamentType: detail.value?.tournamentType,
    uniApi: uni,
  })
}

function goBack() {
  uni.navigateBack()
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

function editLineup() {
  uni.navigateTo({
    url: '/pages/tournament/team-lineup?tournamentId=' + encodeURIComponent(tournamentId.value)
      + '&matchId=' + encodeURIComponent(matchId.value),
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
    const shouldReturnAfterChild = returningFromChildScoreboard.value
    returningFromChildScoreboard.value = false
    if (shouldReturnAfterChild && parentMatchFinished.value) {
      clearContinueChoice()
      returnToTournamentSchedule()
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
  if (parentMatchFinished.value && !item.childMatchId) {
    uni.showToast({ title: '团体赛已结束', icon: 'none' })
    return
  }
  if (!itemReady(item)) {
    uni.showToast({ title: '请先完成该项目对阵名单', icon: 'none' })
    return
  }
  if (startingCode.value) return
  startingCode.value = item.itemCode
  try {
    const data = await request('/api/v1/matches/' + matchId.value + '/team-items/' + item.itemCode + '/start', { method: 'PUT' })
    openScoreboard({
      matchId: data.childMatchId,
      leftName: data.leftName,
      rightName: data.rightName,
      bestOf: data.bestOf || 3,
      gamesToWin: data.gamesToWin || 2,
      pointsToWin: data.pointsToWin || 21,
      enableDeuce: data.enableDeuce === false ? '0' : '1',
      capPoint: data.capPoint || 30,
    })
  } finally {
    startingCode.value = ''
  }
}

onLoad((options) => {
  tournamentId.value = options?.tournamentId || ''
  matchId.value = options?.matchId || ''
  if (!matchId.value) {
    loading.value = false
    isError.value = true
    uni.showToast({ title: '缺少比赛ID', icon: 'none' })
    return
  }
  fetchDetail()
})

onShow(() => {
  if (loadedOnce.value) fetchDetail()
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
