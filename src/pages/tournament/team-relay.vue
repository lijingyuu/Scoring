<template>
  <view class="scoreboard-page" :style="pageStyle">
    <view class="state-layer" v-if="loading">
      <text class="state-text">{{ t.loading }}</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text error">{{ t.loadFailed }}</text>
      <button class="retry-btn" @click="fetchDetail">{{ t.retry }}</button>
    </view>

    <template v-else>
      <view class="top-left-actions">
        <button class="action-btn side-action-btn" @click="goBack">{{ t.back }}</button>
      </view>

      <view class="top-flow-row">
        <view class="top-center-actions">
          <button class="action-btn center-action-btn" @click="undo" :disabled="!historyStack.length || scoreLocked">{{ t.undo }}</button>
          <button class="action-btn center-action-btn" @click="editLineup" :disabled="scoreStarted || syncing || synced">{{ t.lineup }}</button>
          <button class="action-btn center-action-btn sync-action-btn" @click="syncResult" :disabled="!matchEnded || syncing || synced || segmentSwitchPending">{{ t.sync }}</button>
        </view>

        <text class="top-score-anchor">{{ displayScore('left') }} : {{ displayScore('right') }}</text>

        <view class="match-info">
          <text class="match-rule">{{ t.base }} {{ baseScore }} / {{ t.target }} {{ targetScore }}</text>
          <text class="game-tag">{{ t.segmentPrefix }}{{ currentSegmentNo }}{{ t.segmentSuffix }}</text>
        </view>
      </view>

      <view class="game-wins-row">
        <view class="team-summary left-label">
          <view class="team-label">{{ displayTeamName('left') }}</view>
          <view class="team-name">{{ displayMemberNames('left') || t.unfilled }}</view>
        </view>
        <view class="team-summary right-label">
          <view class="team-label">{{ displayTeamName('right') }}</view>
          <view class="team-name">{{ displayMemberNames('right') || t.unfilled }}</view>
        </view>
      </view>

      <view class="main-panels">
        <view class="score-side left-side">
          <view class="team-panel">
            <view class="score-box" :class="{ disabled: scoreLocked }" @click="addScore('left')">
              <view class="score">{{ displayScore('left') }}</view>
            </view>
            <view class="serve-flag" :class="{ active: visualLeftSide === lastScoredSide }">{{ t.lastPoint }}</view>
          </view>
        </view>

        <view class="score-side right-side">
          <view class="team-panel">
            <view class="score-box" :class="{ disabled: scoreLocked }" @click="addScore('right')">
              <view class="score">{{ displayScore('right') }}</view>
            </view>
            <view class="serve-flag" :class="{ active: visualRightSide === lastScoredSide }">{{ t.lastPoint }}</view>
          </view>
        </view>
      </view>

      <view v-if="segmentSwitchPending" class="final-switch-overlay">
        <view class="final-switch-card">
          <text class="final-switch-title">{{ t.segmentEndedTitle }}</text>
          <text class="final-switch-tip">{{ t.segment }} {{ currentSegmentNo }} {{ t.cutoff }} {{ currentSegmentTarget }}</text>
          <button class="final-switch-btn" @click="advanceSegment">{{ t.nextSegment }}</button>
        </view>
      </view>

      <view v-if="matchEnded && !segmentSwitchPending" class="lock-mask">
        <view class="settlement-card">
          <text class="settlement-title">{{ t.ended }}</text>
          <text class="settlement-winner">{{ winnerText }}</text>
          <text class="settlement-score">{{ leftScore }} : {{ rightScore }}</text>
          <view class="settlement-actions">
            <button class="new-match-btn sync-btn" @click="syncResult" :disabled="syncing || synced">{{ t.sync }}</button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { navigateToTournamentSchedule } from './tournament-navigation'

import { requireMatchOperator } from '@/utils/match-guard'

const t = {
  loading: '\u6b63\u5728\u52a0\u8f7d\u63a5\u529b\u8d5b...',
  loadFailed: '\u63a5\u529b\u8d5b\u52a0\u8f7d\u5931\u8d25',
  retry: '\u91cd\u65b0\u52a0\u8f7d',
  back: '\u8fd4\u56de',
  title: '\u4eba\u5458\u6d41\u8f6c\u8ffd\u5206\u8d5b',
  base: '\u57fa\u51c6\u5206',
  target: '\u76ee\u6807\u5206',
  segment: '\u7b2c',
  segmentPrefix: '\u7b2c',
  segmentSuffix: '\u8d5b\u6bb5',
  cutoff: '\u6bb5\u622a\u6b62',
  lineup: '\u987a\u5e8f\u540d\u5355',
  leftTeam: '\u5de6\u961f',
  rightTeam: '\u53f3\u961f',
  unknownTeam: '\u672a\u77e5\u961f\u4f0d',
  unfilled: '\u672a\u586b\u5199',
  lastPoint: '\u00b7\u53d1\u7403',
  pending: '\u5f53\u524d\u9636\u6bb5\u5df2\u7ed3\u675f\uff0c\u8bf7\u8fdb\u5165\u4e0b\u4e00\u9636\u6bb5\u5e76\u4ea4\u6362\u573a\u5730',
  nextSegment: '\u4ea4\u6362\u573a\u5730\u5e76\u8fdb\u5165\u4e0b\u4e00\u8d5b\u6bb5',
  ended: '\u6bd4\u8d5b\u7ed3\u675f\uff0c\u8bf7\u540c\u6b65\u7ed3\u679c',
  historyEmpty: '\u9636\u6bb5\u7ed3\u675f\u6bd4\u5206\u4f1a\u663e\u793a\u5728\u8fd9\u91cc',
  undo: '\u64a4\u9500',
  sync: '\u540c\u6b65\u7ed3\u679c',
  missingMatchId: '\u7f3a\u5c11\u6bd4\u8d5bID',
  completeLineup: '\u8bf7\u5148\u5b8c\u6210\u987a\u5e8f\u540d\u5355',
  segmentEndedTitle: '\u9636\u6bb5\u7ed3\u675f',
  segmentEndedContentPrefix: '\u7b2c ',
  segmentEndedContentSuffix: ' \u6bb5\u5df2\u7ed3\u675f\uff0c\u662f\u5426\u8fdb\u5165\u4e0b\u4e00\u9636\u6bb5\u5e76\u4ea4\u6362\u573a\u5730\uff1f',
  notNow: '\u6682\u4e0d\u8fdb\u5165',
  initialSideTitle: '\u786e\u8ba4\u573a\u5730',
  initialSideConfirm: '\u9700\u8981\u6362\u8fb9',
  initialSideCancel: '\u4e0d\u6362\u8fb9',
  currentLeft: '\u5de6\u4fa7\u961f\u4f0d\uff1a',
  currentRight: '\u53f3\u4fa7\u961f\u4f0d\uff1a',
  initialSideQuestion: '\u76ee\u524d\u53cc\u65b9\u662f\u5426\u9700\u8981\u4ea4\u6362\u573a\u5730\uff1f',
  noWinner: '\u672a\u5206\u51fa\u80dc\u8d1f',
  synced: '\u7ed3\u7b97\u6210\u529f',
}

function buildBaseLandscapePageStyle() {
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

const pageStyle = buildBaseLandscapePageStyle()
const tournamentId = ref('')
const matchId = ref('')
const detail = ref({ leftTeam: {}, rightTeam: {}, items: [] })
const loading = ref(true)
const isError = ref(false)
const loadedOnce = ref(false)
const leftScore = ref(0)
const rightScore = ref(0)
const historyStack = ref([])
const segmentScores = ref([])
const segmentSwitchPending = ref(false)
const sidesSwapped = ref(false)
const lastScoredSide = ref('')
const syncing = ref(false)
const synced = ref(false)
const initialSidePromptShown = ref(false)

const items = computed(() => Array.isArray(detail.value.items) ? [...detail.value.items].sort((a, b) => Number(a.displayOrder || 0) - Number(b.displayOrder || 0)) : [])
const baseScore = computed(() => Number(detail.value.relayBaseScore || 10))
const targetScore = computed(() => Number(detail.value.relayTargetScore || baseScore.value * items.value.length))
const matchEnded = computed(() => Number(detail.value.matchStatus || 0) === 2 || (targetScore.value > 0 && (leftScore.value >= targetScore.value || rightScore.value >= targetScore.value)))
const scoreLocked = computed(() => matchEnded.value || syncing.value || synced.value || segmentSwitchPending.value)
const scoreStarted = computed(() => leftScore.value > 0 || rightScore.value > 0 || segmentScores.value.length > 0)
const winnerText = computed(() => {
  if (leftScore.value > rightScore.value) return teamName('left')
  if (rightScore.value > leftScore.value) return teamName('right')
  return t.unknownTeam
})
const currentSegmentIndex = computed(() => {
  if (!items.value.length) return 0
  return Math.min(segmentScores.value.length, items.value.length - 1)
})
const currentSegmentNo = computed(() => currentSegmentIndex.value + 1)
const currentSegmentTarget = computed(() => Math.min(baseScore.value * currentSegmentNo.value, targetScore.value))
const currentItem = computed(() => items.value[currentSegmentIndex.value] || {})
const visualLeftSide = computed(() => sidesSwapped.value ? 'right' : 'left')
const visualRightSide = computed(() => sidesSwapped.value ? 'left' : 'right')

function sideLabel(side) {
  return side === 'left' ? t.leftTeam : t.rightTeam
}

function originalSideOfVisual(visualSide) {
  return visualSide === 'left' ? visualLeftSide.value : visualRightSide.value
}

function teamName(side) {
  const team = side === 'left' ? detail.value.leftTeam : detail.value.rightTeam
  return team?.name || t.unknownTeam
}

function displayTeamName(visualSide) {
  return teamName(originalSideOfVisual(visualSide))
}

function scoreOf(side) {
  return side === 'left' ? leftScore.value : rightScore.value
}

function displayScore(visualSide) {
  return scoreOf(originalSideOfVisual(visualSide))
}

function membersOf(side) {
  return side === 'left' ? currentItem.value.leftMembers : currentItem.value.rightMembers
}

function memberNames(members) {
  return Array.isArray(members) ? members.map((member) => member?.name).filter(Boolean).join(' / ') : ''
}

function displayMemberNames(visualSide) {
  return memberNames(membersOf(originalSideOfVisual(visualSide)))
}

function storageKey() {
  return matchId.value ? 'team_relay_scoreboard_state_' + matchId.value : 'team_relay_scoreboard_state'
}

function buildScoreState() {
  return {
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    segmentScores: segmentScores.value.map((item) => ({ ...item })),
    segmentSwitchPending: segmentSwitchPending.value,
    sidesSwapped: sidesSwapped.value,
    lastScoredSide: lastScoredSide.value,
  }
}

function buildSnapshot() {
  return {
    ...buildScoreState(),
    historyStack: historyStack.value.map((item) => ({
      ...item,
      segmentScores: Array.isArray(item.segmentScores) ? item.segmentScores.map((score) => ({ ...score })) : [],
    })),
    synced: synced.value,
  }
}

function applyScoreState(snapshot) {
  leftScore.value = Number(snapshot?.leftScore || 0)
  rightScore.value = Number(snapshot?.rightScore || 0)
  segmentScores.value = Array.isArray(snapshot?.segmentScores) ? snapshot.segmentScores.map((item) => ({ ...item })) : []
  segmentSwitchPending.value = !!snapshot?.segmentSwitchPending
  sidesSwapped.value = !!snapshot?.sidesSwapped
  lastScoredSide.value = snapshot?.lastScoredSide || ''
}

function applySnapshot(snapshot) {
  applyScoreState(snapshot)
  historyStack.value = Array.isArray(snapshot?.historyStack) ? snapshot.historyStack.map((item) => ({
    ...item,
    segmentScores: Array.isArray(item.segmentScores) ? item.segmentScores.map((score) => ({ ...score })) : [],
  })) : []
  synced.value = !!snapshot?.synced
}

function saveStateToStorage() {
  try {
    uni.setStorageSync(storageKey(), buildSnapshot())
  } catch (error) {
    console.error('save relay cache failed', error)
  }
}

function clearStateFromStorage() {
  try {
    uni.removeStorageSync(storageKey())
  } catch (error) {
    console.error('clear relay cache failed', error)
  }
}

function restoreStateFromStorage() {
  try {
    const cache = uni.getStorageSync(storageKey())
    if (!cache || typeof cache !== 'object') return
    applySnapshot(cache)
  } catch (error) {
    console.error('restore relay cache failed', error)
  }
}

function lineupComplete() {
  return items.value.length > 0 && items.value.every((item) => {
    return Array.isArray(item.leftMembers) && item.leftMembers.length === 2
      && Array.isArray(item.rightMembers) && item.rightMembers.length === 2
  })
}

function applySavedScore() {
  const text = detail.value.scoreDisplay || ''
  const match = text.match(/^(\d+):(\d+)$/)
  if (!match) return
  leftScore.value = Number(match[1] || 0)
  rightScore.value = Number(match[2] || 0)
}

async function fetchDetail() {
  if (!matchId.value) return
  loading.value = !loadedOnce.value
  isError.value = false
  try {
    const data = await request('/api/v1/matches/' + matchId.value + '/team-lineup', { method: 'GET' })
    detail.value = data || { leftTeam: {}, rightTeam: {}, items: [] }
    if (Number(detail.value.matchStatus || 0) === 2) {
      synced.value = true
      applySavedScore()
      clearStateFromStorage()
    }
    loadedOnce.value = true
    if (!lineupComplete()) {
      uni.showToast({ title: t.completeLineup, icon: 'none' })
    } else {
      promptInitialSideSwap()
    }
  } catch (_) {
    isError.value = true
  } finally {
    loading.value = false
  }
}

function promptInitialSideSwap() {
  if (initialSidePromptShown.value || scoreStarted.value || matchEnded.value || synced.value) return
  initialSidePromptShown.value = true
  uni.showModal({
    title: t.initialSideTitle,
    content: t.currentLeft + teamName('left') + '\n' + t.currentRight + teamName('right') + '\n' + t.initialSideQuestion,
    confirmText: t.initialSideConfirm,
    cancelText: t.initialSideCancel,
    success: (res) => {
      if (!res.confirm) return
      sidesSwapped.value = !sidesSwapped.value
      saveStateToStorage()
    },
  })
}

function pushHistory() {
  historyStack.value.push(buildScoreState())
}

function appendCurrentSegmentScore() {
  const segmentNo = currentSegmentNo.value
  if (segmentScores.value.some((item) => item.segmentNo === segmentNo)) return
  segmentScores.value.push({
    segmentNo,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
  })
}

function buildRelaySegmentScores() {
  const scores = segmentScores.value.map((item) => ({ ...item }))
  if (!scores.some((item) => item.segmentNo === currentSegmentNo.value)) {
    scores.push({
      segmentNo: currentSegmentNo.value,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
    })
  }
  return scores.map((item) => {
    const winnerSide = Number(item.leftScore || 0) > Number(item.rightScore || 0) ? 'left' : 'right'
    return {
      gameNo: item.segmentNo,
      leftScore: item.leftScore,
      rightScore: item.rightScore,
      winnerSide,
    }
  })
}

function promptSegmentEnd() {
  uni.showModal({
    title: t.segmentEndedTitle,
    content: t.segmentEndedContentPrefix + currentSegmentNo.value + t.segmentEndedContentSuffix,
    confirmText: t.nextSegment,
    cancelText: t.notNow,
    success: (res) => {
      if (res.confirm) advanceSegment()
    },
  })
}

function addScore(visualSide) {
  if (scoreLocked.value) return
  if (!lineupComplete()) {
    editLineup()
    return
  }
  const side = originalSideOfVisual(visualSide)
  pushHistory()
  if (side === 'left') leftScore.value += 1
  else rightScore.value += 1
  lastScoredSide.value = side

  if (leftScore.value >= targetScore.value || rightScore.value >= targetScore.value) {
    appendCurrentSegmentScore()
    saveStateToStorage()
    uni.showToast({ title: t.ended, icon: 'success' })
    return
  }

  if (leftScore.value >= currentSegmentTarget.value || rightScore.value >= currentSegmentTarget.value) {
    segmentSwitchPending.value = true
    appendCurrentSegmentScore()
    saveStateToStorage()
    promptSegmentEnd()
    return
  }
  saveStateToStorage()
}

function advanceSegment() {
  if (!segmentSwitchPending.value || matchEnded.value) return

  segmentSwitchPending.value = false
  sidesSwapped.value = !sidesSwapped.value
  saveStateToStorage()
}

function undo() {
  if (syncing.value || synced.value) return
  const last = historyStack.value.pop()
  if (!last) return
  applyScoreState(last)
  saveStateToStorage()
}

async function syncResult() {
  if (syncing.value || synced.value || segmentSwitchPending.value) return
  const winnerSide = leftScore.value > rightScore.value ? 'left' : rightScore.value > leftScore.value ? 'right' : ''
  if (!winnerSide) {
    uni.showToast({ title: t.noWinner, icon: 'none' })
    return
  }
  syncing.value = true
  try {
    await request('/api/v1/matches/' + matchId.value + '/finish', {
      method: 'PUT',
      data: {
        winnerSide,
        leftScore: leftScore.value,
        rightScore: rightScore.value,
        leftGameWins: winnerSide === 'left' ? 1 : 0,
        rightGameWins: winnerSide === 'right' ? 1 : 0,
        gameScores: [{
          gameNo: 1,
          leftScore: leftScore.value,
          rightScore: rightScore.value,
          winnerSide,
        }],
        relaySegmentScores: buildRelaySegmentScores(),
      },
    })
    synced.value = true
    clearStateFromStorage()
    uni.showToast({ title: t.synced, icon: 'success' })
    setTimeout(() => {
      returnToTournamentSchedule()
    }, 500)
  } finally {
    syncing.value = false
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

function editLineup() {
  uni.navigateTo({
    url: '/pages/tournament/team-lineup?tournamentId=' + encodeURIComponent(tournamentId.value)
      + '&matchId=' + encodeURIComponent(matchId.value),
  })
}

function goBack() {
  uni.navigateBack()
}

onLoad(async (options) => {
  tournamentId.value = options?.tournamentId || ''
  matchId.value = options?.matchId || ''
  if (!matchId.value) {
    loading.value = false
    isError.value = true
    uni.showToast({ title: t.missingMatchId, icon: 'none' })
    return
  }

  const allowed = await requireMatchOperator(matchId.value)
  if (!allowed) {
    setTimeout(() => uni.navigateBack(), 1500)
    return
  }
  restoreStateFromStorage()
  fetchDetail()
})

onShow(() => {
  if (loadedOnce.value) fetchDetail()
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

.state-layer {
  position: absolute;
  inset: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  z-index: 30;
}

.state-text {
  font-size: 28rpx;
  color: rgba(255, 255, 255, 0.72);
}

.state-text.error {
  color: #ff8c00;
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
  left: 0;
  right: 0;
  z-index: 8;
  display: flex;
  align-items: flex-start;
  gap: 28rpx;
  padding: 0 36rpx;
  box-sizing: border-box;
  min-height: 72rpx;
  font-size: 23rpx;
  white-space: nowrap;
}

.game-tag {
  color: #ff8c00;
  font-weight: 700;
}

.game-wins {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
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
.retry-btn::after,
.final-switch-btn::after,
.new-match-btn::after {
  border: none;
}

.action-btn[disabled],
.new-match-btn[disabled] {
  background: rgba(150, 160, 170, 0.08);
  border-color: rgba(150, 160, 170, 0.46);
  color: rgba(170, 178, 186, 0.72);
  opacity: 1;
}

.sync-action-btn {
  border-color: #ffffff;
}

.side-action-btn {
  min-width: 80rpx;
  height: 44rpx;
  line-height: 44rpx;
  padding: 0 6rpx;
  font-size: 18rpx;
}

.top-left-actions .action-btn,
.top-center-actions .action-btn {
  margin: 0;
  min-width: 52rpx;
  height: 30rpx;
  line-height: 30rpx;
  padding: 0 6rpx;
  border-radius: 7rpx;
  font-size: 12rpx;
}

.main-panels {
  position: absolute;
  left: 0;
  right: 0;
  top: 40vh;
  bottom: 6vh;
  display: flex;
  padding: 0 36rpx;
  box-sizing: border-box;
  gap: 28rpx;
}

.score-side {
  position: relative;
  flex: 1;
  min-width: 0;
  display: flex;
  height: 100%;
}

.left-side {
  justify-content: flex-end;
}

.right-side {
  justify-content: flex-start;
}

.team-panel {
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-start;
  align-items: center;
  gap: 6rpx;
  transform: none;
}

.team-summary {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.team-label {
  display: block;
  width: 100%;
  min-width: 0;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.left-label {
  text-align: center;
}

.right-label {
  text-align: center;
}

.team-name {
  display: block;
  max-width: 92%;
  min-height: 36rpx;
  text-align: center;
  font-size: 23rpx;
  font-weight: 400;
  line-height: 1.3;
  flex-shrink: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.score-box {
  width: 100%;
  max-width: 560rpx;
  flex: 1;
  min-height: 0;
  height: auto;
  padding: 34rpx 16rpx;
  box-sizing: border-box;
  border: 4rpx solid #ff8c00;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.score-box.disabled {
  opacity: 0.5;
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

.final-switch-overlay,
.lock-mask {
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

.final-switch-card,
.settlement-card {
  width: 72vw;
  max-width: 620rpx;
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

.final-switch-title,
.settlement-title {
  line-height: 1.25;
  font-weight: 700;
}

.final-switch-title {
  font-size: 34rpx;
}

.settlement-title {
  font-size: 42rpx;
}

.final-switch-tip,
.settlement-winner {
  color: rgba(255, 255, 255, 0.86);
}

.final-switch-tip {
  font-size: 22rpx;
}

.settlement-winner {
  font-size: 28rpx;
}

.settlement-score {
  font-size: 94rpx;
  font-weight: 700;
  line-height: 1;
}

.final-switch-btn,
.new-match-btn,
.retry-btn {
  width: 100%;
  max-width: 360rpx;
  height: 70rpx;
  line-height: 70rpx;
  border-radius: 14rpx;
  border: none;
  background: rgba(255, 140, 0, 0.2);
  color: #ffffff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 140, 0, 0.55);
}

.final-switch-btn {
  font-size: 22rpx;
}

.new-match-btn,
.retry-btn {
  font-size: 28rpx;
}

.settlement-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 16rpx;
  margin-top: 10rpx;
}

.sync-btn {
  background: rgba(255, 255, 255, 0.1);
}
</style>
