<template>
  <view class="state-page" :class="{ 'landscape-preview': useLandscapePreview }" :style="previewPageStyle" v-if="loading">
    <text class="state-text">正在加载排球记分牌...</text>
  </view>

  <view class="state-page" :class="{ 'landscape-preview': useLandscapePreview }" :style="previewPageStyle" v-else-if="isError">
    <text class="state-text state-error">{{ errorText }}</text>
    <button class="retry-btn" @click="loadMatch">重新加载</button>
  </view>

  <view class="scoreboard-page" :class="{ 'landscape-preview': useLandscapePreview }" :style="previewPageStyle" v-else>
    <view class="roster-panel left">
      <view class="column-head roster-head">
        <text class="roster-team">{{ leftDisplayTeamName }}</text>
        <text class="roster-meta">{{ leftGameWins }} 局</text>
      </view>
      <view class="column-body roster-body">
        <scroll-view class="roster-scroll" scroll-y>
          <view
            class="roster-item"
            :class="{
              active: selectedBench.side === 'left' && selectedBench.memberId === member.id,
              oncourt: isOnCourt('left', member.id),
            }"
            v-for="member in leftTeam.members"
            :key="member.id"
            @click="selectBench('left', member.id)"
          >
            <text class="roster-no">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: isOnCourt('left', member.id) }">{{ member.name }}</text>
            </view>
            <text class="roster-tags" v-if="member.captain">队长</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="center-panel">
      <view class="column-head center-head">
        <view class="score-top">
          <text class="game-pill">第 {{ currentGameNo }} 局</text>
          <text class="rule-pill">{{ info.bestOf === 5 ? '五局三胜' : '三局两胜' }}</text>
          <text class="target-pill">本局 {{ currentTargetPoints }} 分</text>
        </view>
      </view>

      <view class="column-body center-body">
        <view class="score-panel">
          <view class="score-main">
            <view class="score-side" @click="addScore('left')">
              <text class="score-name">{{ leftDisplayTeamName }}</text>
              <text class="score-value">{{ leftScore }}</text>
              <text class="serve-flag" v-if="serveSide === 'left'">发球</text>
            </view>

            <view class="score-center">
              <view class="set-score">{{ leftGameWins }} : {{ rightGameWins }}</view>
              <view class="action-list">
                <button class="action-btn" @click="undo" :disabled="!historyStack.length || isLocked">撤销</button>
                <button class="action-btn" @click="openTimeoutSheet" :disabled="isLocked || (leftTimeouts <= 0 && rightTimeouts <= 0)">暂停</button>
                <button class="action-btn danger" @click="openRetireSheet" :disabled="isLocked">退赛</button>
              </view>
            </view>

            <view class="score-side right" @click="addScore('right')">
              <text class="score-name">{{ rightDisplayTeamName }}</text>
              <text class="score-value">{{ rightScore }}</text>
              <text class="serve-flag" v-if="serveSide === 'right'">发球</text>
            </view>
          </view>

          <view class="set-strip" v-if="gameScores.length">
            <view class="set-pill" v-for="item in gameScores" :key="item.gameNo">
              <text>第 {{ item.gameNo }} 局</text>
              <text>{{ item.leftScore }}:{{ item.rightScore }}</text>
            </view>
          </view>
        </view>

        <view class="court-card">
          <view class="court-header">
            <text class="court-title">场上轮转</text>
            <text class="court-tip">先点替补，再点场上号码完成换人</text>
          </view>

          <view class="court-board">
            <view class="court-half">
              <view class="court-grid">
                <view class="court-slot" v-for="item in leftCourtDisplaySlots" :key="item.key" @click="handleCourtSlot('left', item.dataIndex)">
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no">{{ jerseyText('left', item.memberId) }}</text>
                </view>
              </view>
            </view>

            <view class="court-net"></view>

            <view class="court-half right">
              <view class="court-grid">
                <view class="court-slot" v-for="item in rightCourtDisplaySlots" :key="item.key" @click="handleCourtSlot('right', item.dataIndex)">
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no">{{ jerseyText('right', item.memberId) }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="roster-panel right">
      <view class="column-head roster-head">
        <text class="roster-team">{{ rightDisplayTeamName }}</text>
        <text class="roster-meta">{{ rightGameWins }} 局</text>
      </view>
      <view class="column-body roster-body">
        <scroll-view class="roster-scroll" scroll-y>
          <view
            class="roster-item"
            :class="{
              active: selectedBench.side === 'right' && selectedBench.memberId === member.id,
              oncourt: isOnCourt('right', member.id),
            }"
            v-for="member in rightTeam.members"
            :key="member.id"
            @click="selectBench('right', member.id)"
          >
            <text class="roster-no">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: isOnCourt('right', member.id) }">{{ member.name }}</text>
            </view>
            <text class="roster-tags" v-if="member.captain">队长</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="settlement-mask" v-if="isLocked">
      <view class="settlement-card">
        <text class="settlement-title">{{ retiredSide ? '比赛已退赛结束' : '比赛结束' }}</text>
        <text class="settlement-winner">获胜方：{{ winnerDisplayName || '待定' }}</text>
        <text class="settlement-score">{{ leftGameWins }} : {{ rightGameWins }}</text>
        <text class="settlement-games">{{ scoreSummary || '暂无局分' }}</text>
        <view class="settlement-actions">
          <button class="settlement-btn ghost" @click="resetMatch">重新开始</button>
          <button class="settlement-btn" @click="syncAndBack" v-if="matchId">同步结算</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onUnmounted, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import {
  buildLineupUrl,
  clearMatchState,
  cloneCourt,
  createEmptyMatchState,
  formatTeamName,
  loadMatchState,
  normalizeMatchState,
  normalizeTeam,
  saveMatchState,
  toggleSide,
} from './match-state'

const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const tournamentId = ref('')
const matchId = ref('')
const info = ref({})
const leftTeam = ref({ name: '主队', members: [] })
const rightTeam = ref({ name: '客队', members: [] })
const pageQuery = ref({})

const leftScore = ref(0)
const rightScore = ref(0)
const leftGameWins = ref(0)
const rightGameWins = ref(0)
const currentGameNo = ref(1)
const gameScores = ref([])
const serveSide = ref('left')
const currentGameStartServeSide = ref('left')
const leftTimeouts = ref(2)
const rightTimeouts = ref(2)
const leftCourt = ref(Array(6).fill(''))
const rightCourt = ref(Array(6).fill(''))
const baseLeftCourt = ref(Array(6).fill(''))
const baseRightCourt = ref(Array(6).fill(''))
const lineupReady = ref(false)
const historyStack = ref([])
const retiredSide = ref('')
const matchEnded = ref(false)
const winnerName = ref('')
const selectedBench = ref({ side: '', memberId: '' })

const isH5PortraitPreview = ref(false)
const previewScale = ref(1)
const previewOffsetX = ref(0)
const previewOffsetY = ref(0)

const currentTargetPoints = computed(() => {
  const finalGameNo = Number(info.value.bestOf || 3)
  return currentGameNo.value === finalGameNo ? 15 : 25
})

const isLocked = computed(() => !!retiredSide.value || matchEnded.value)
const leftDisplayTeamName = computed(() => formatTeamName(leftTeam.value.name))
const rightDisplayTeamName = computed(() => formatTeamName(rightTeam.value.name))
const winnerDisplayName = computed(() => formatTeamName(winnerName.value))
const leftCourtDisplaySlots = computed(() => buildCourtDisplaySlots('left', leftCourt.value))
const rightCourtDisplaySlots = computed(() => buildCourtDisplaySlots('right', rightCourt.value))
const useLandscapePreview = computed(() => isH5PortraitPreview.value && lineupReady.value)
const previewPageStyle = computed(() => {
  if (!useLandscapePreview.value) return ''
  return {
    transform: `translate(${previewOffsetX.value}px, ${previewOffsetY.value}px) scale(${previewScale.value})`,
    transformOrigin: 'top left',
  }
})

const scoreSummary = computed(() => gameScores.value.map((item) => `${item.leftScore}:${item.rightScore}`).join(', '))

function updateH5PortraitPreview() {
  // #ifdef H5
  const viewportWidth = window.innerWidth
  const viewportHeight = window.innerHeight
  isH5PortraitPreview.value = viewportHeight > viewportWidth
  if (!isH5PortraitPreview.value) {
    previewScale.value = 1
    previewOffsetX.value = 0
    previewOffsetY.value = 0
    return
  }
  const designWidth = 1280
  const designHeight = 720
  const scale = Math.min(viewportWidth / designWidth, viewportHeight / designHeight)
  previewScale.value = scale
  previewOffsetX.value = (viewportWidth - designWidth * scale) / 2
  previewOffsetY.value = (viewportHeight - designHeight * scale) / 2
  // #endif
}

function buildCourtDisplaySlots(side, court) {
  const labels = side === 'right'
    ? ['2号位', '1号位', '3号位', '6号位', '4号位', '5号位']
    : ['5号位', '4号位', '6号位', '3号位', '1号位', '2号位']
  const order = side === 'right'
    ? [1, 0, 2, 5, 3, 4]
    : [4, 3, 5, 2, 0, 1]
  return order.map((dataIndex, index) => ({
    key: `${side}_${dataIndex}`,
    dataIndex,
    label: labels[index],
    memberId: court[dataIndex] || '',
  }))
}

function buildSnapshot() {
  return normalizeMatchState({
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    leftGameWins: leftGameWins.value,
    rightGameWins: rightGameWins.value,
    currentGameNo: currentGameNo.value,
    gameScores: gameScores.value.map((item) => ({ ...item })),
    serveSide: serveSide.value,
    currentGameStartServeSide: currentGameStartServeSide.value,
    leftTimeouts: leftTimeouts.value,
    rightTimeouts: rightTimeouts.value,
    leftCourt: leftCourt.value,
    rightCourt: rightCourt.value,
    baseLeftCourt: baseLeftCourt.value,
    baseRightCourt: baseRightCourt.value,
    draftLeftCourt: baseLeftCourt.value,
    draftRightCourt: baseRightCourt.value,
    draftServeSide: serveSide.value,
    lineupReady: lineupReady.value,
    retiredSide: retiredSide.value,
    matchEnded: matchEnded.value,
    winnerName: winnerName.value,
    historyStack: historyStack.value,
  })
}

function applyState(state) {
  const normalized = normalizeMatchState(state)
  leftScore.value = normalized.leftScore
  rightScore.value = normalized.rightScore
  leftGameWins.value = normalized.leftGameWins
  rightGameWins.value = normalized.rightGameWins
  currentGameNo.value = normalized.currentGameNo
  gameScores.value = normalized.gameScores
  serveSide.value = normalized.serveSide
  currentGameStartServeSide.value = normalized.currentGameStartServeSide
  leftTimeouts.value = normalized.leftTimeouts
  rightTimeouts.value = normalized.rightTimeouts
  leftCourt.value = cloneCourt(normalized.leftCourt)
  rightCourt.value = cloneCourt(normalized.rightCourt)
  baseLeftCourt.value = cloneCourt(normalized.baseLeftCourt)
  baseRightCourt.value = cloneCourt(normalized.baseRightCourt)
  lineupReady.value = normalized.lineupReady
  retiredSide.value = normalized.retiredSide
  matchEnded.value = normalized.matchEnded
  winnerName.value = normalized.winnerName
  historyStack.value = normalized.historyStack
}

function persistState() {
  saveMatchState(matchId.value, buildSnapshot())
}

function memberMap(side) {
  const team = side === 'right' ? rightTeam.value : leftTeam.value
  const map = new Map()
  for (const member of team.members || []) {
    map.set(member.id, member)
  }
  return map
}

function memberById(side, memberId) {
  if (!memberId) return null
  return memberMap(side).get(memberId) || null
}

function jerseyText(side, memberId) {
  const member = memberById(side, memberId)
  return member ? String(member.jerseyNumber) : '--'
}

function isOnCourt(side, memberId) {
  const court = side === 'right' ? rightCourt.value : leftCourt.value
  return court.includes(memberId)
}

function pushHistory() {
  historyStack.value.push(buildSnapshot())
}

function selectBench(side, memberId) {
  if (!lineupReady.value || isLocked.value) return
  if (isOnCourt(side, memberId)) return
  const same = selectedBench.value.side === side && selectedBench.value.memberId === memberId
  selectedBench.value = same ? { side: '', memberId: '' } : { side, memberId }
}

function handleCourtSlot(side, index) {
  if (!lineupReady.value || isLocked.value) return
  if (selectedBench.value.side !== side || !selectedBench.value.memberId) return
  pushHistory()
  if (side === 'left') {
    leftCourt.value.splice(index, 1, selectedBench.value.memberId)
  } else {
    rightCourt.value.splice(index, 1, selectedBench.value.memberId)
  }
  selectedBench.value = { side: '', memberId: '' }
  persistState()
}

function rotateCourt(side) {
  const source = side === 'right' ? rightCourt.value.slice() : leftCourt.value.slice()
  const rotated = [source[1], source[2], source[3], source[4], source[5], source[0]]
  if (side === 'right') {
    rightCourt.value = rotated
  } else {
    leftCourt.value = rotated
  }
}

function checkWinCondition(myScore, opponentScore) {
  return myScore >= currentTargetPoints.value && myScore - opponentScore >= 2
}

function goToNextLineup() {
  const nextServeSide = toggleSide(currentGameStartServeSide.value)
  const state = buildSnapshot()
  state.lineupReady = false
  state.draftLeftCourt = cloneCourt(baseLeftCourt.value)
  state.draftRightCourt = cloneCourt(baseRightCourt.value)
  state.draftServeSide = nextServeSide
  saveMatchState(matchId.value, state)
  uni.redirectTo({
    url: buildLineupUrl({
      ...pageQuery.value,
      serveSide: nextServeSide,
    }),
  })
}

function finishGame(winnerSide) {
  gameScores.value.push({
    gameNo: currentGameNo.value,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    winnerSide,
  })

  if (winnerSide === 'left') {
    leftGameWins.value += 1
  } else {
    rightGameWins.value += 1
  }

  if (leftGameWins.value >= Number(info.value.gamesToWin || 2) || rightGameWins.value >= Number(info.value.gamesToWin || 2)) {
    winnerName.value = leftGameWins.value > rightGameWins.value ? leftTeam.value.name : rightTeam.value.name
    matchEnded.value = true
    persistState()
    return
  }

  currentGameNo.value += 1
  leftScore.value = 0
  rightScore.value = 0
  leftTimeouts.value = 2
  rightTimeouts.value = 2
  selectedBench.value = { side: '', memberId: '' }
  persistState()
  goToNextLineup()
}

function addScore(side) {
  if (!lineupReady.value || isLocked.value) return
  pushHistory()

  if (side === 'left') {
    leftScore.value += 1
  } else {
    rightScore.value += 1
  }

  if (serveSide.value !== side) {
    rotateCourt(side)
    serveSide.value = side
  }

  const myScore = side === 'left' ? leftScore.value : rightScore.value
  const opponentScore = side === 'left' ? rightScore.value : leftScore.value
  if (checkWinCondition(myScore, opponentScore)) {
    finishGame(side)
    return
  }
  persistState()
}

function undo() {
  if (!historyStack.value.length || isLocked.value) return
  const snapshot = historyStack.value.pop()
  applyState(snapshot)
  persistState()
}

function useTimeout(side) {
  if (isLocked.value) return
  if (side === 'left') {
    if (leftTimeouts.value <= 0) return
    pushHistory()
    leftTimeouts.value -= 1
  } else {
    if (rightTimeouts.value <= 0) return
    pushHistory()
    rightTimeouts.value -= 1
  }
  persistState()
}

function openTimeoutSheet() {
  if (isLocked.value) return
  const options = []
  const sides = []
  if (leftTimeouts.value > 0) {
    options.push(`${leftDisplayTeamName.value}暂停`)
    sides.push('left')
  }
  if (rightTimeouts.value > 0) {
    options.push(`${rightDisplayTeamName.value}暂停`)
    sides.push('right')
  }
  if (!options.length) return
  uni.showActionSheet({
    itemList: options,
    success: (res) => {
      const side = sides[res.tapIndex]
      if (side) useTimeout(side)
    },
  })
}

function retire(side) {
  if (isLocked.value) return
  uni.showModal({
    title: '确认退赛',
    content: `确认 ${side === 'left' ? leftDisplayTeamName.value : rightDisplayTeamName.value} 退赛？`,
    success: (res) => {
      if (!res.confirm) return
      pushHistory()
      retiredSide.value = side
      if (side === 'left') {
        rightGameWins.value = Number(info.value.gamesToWin || 2)
        winnerName.value = rightTeam.value.name
      } else {
        leftGameWins.value = Number(info.value.gamesToWin || 2)
        winnerName.value = leftTeam.value.name
      }
      matchEnded.value = true
      persistState()
    },
  })
}

function openRetireSheet() {
  if (isLocked.value) return
  uni.showActionSheet({
    itemList: [`${leftDisplayTeamName.value}退赛`, `${rightDisplayTeamName.value}退赛`],
    success: (res) => {
      if (res.tapIndex === 0) retire('left')
      if (res.tapIndex === 1) retire('right')
    },
  })
}

function resetMatch() {
  clearMatchState(matchId.value)
  uni.redirectTo({
    url: buildLineupUrl(pageQuery.value),
  })
}

async function syncAndBack() {
  if (!matchId.value) {
    uni.showToast({ title: '缺少比赛 ID', icon: 'none' })
    return
  }

  let winnerSide = ''
  if (retiredSide.value) {
    winnerSide = retiredSide.value === 'left' ? 'right' : 'left'
  } else if (leftGameWins.value > rightGameWins.value) {
    winnerSide = 'left'
  } else if (rightGameWins.value > leftGameWins.value) {
    winnerSide = 'right'
  }

  if (!winnerSide) {
    uni.showToast({ title: '未分出胜负，无法同步', icon: 'none' })
    return
  }

  try {
    await request('/api/v1/matches/' + matchId.value + '/finish', {
      method: 'PUT',
      data: {
        winnerSide,
        leftScore: leftScore.value,
        rightScore: rightScore.value,
        leftGameWins: leftGameWins.value,
        rightGameWins: rightGameWins.value,
        gameScores: gameScores.value,
        retiredSide: retiredSide.value || null,
      },
    })
    uni.showToast({ title: '结算成功', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1000)
  } catch (_) {
    // request handles toast
  }
}

async function loadMatch() {
  if (!tournamentId.value || !matchId.value) {
    isError.value = true
    errorText.value = '缺少比赛参数'
    loading.value = false
    return
  }

  loading.value = true
  isError.value = false

  try {
    const data = await request('/api/v1/tournaments/' + tournamentId.value + '/bracket', { method: 'GET' })
    info.value = {
      id: data.id,
      bestOf: Number(data.bestOf || 3),
      gamesToWin: Number(data.gamesToWin || 2),
    }
    const match = (Array.isArray(data.matches) ? data.matches : []).find((item) => item.id === matchId.value)
    if (!match) {
      throw new Error('未找到比赛记录')
    }

    const participantMap = new Map()
    for (const participant of Array.isArray(data.players) ? data.players : []) {
      participantMap.set(participant.id, normalizeTeam(participant))
    }

    leftTeam.value = participantMap.get(match.leftPlayerId) || { name: '主队', members: [] }
    rightTeam.value = participantMap.get(match.rightPlayerId) || { name: '客队', members: [] }

    if (leftTeam.value.members.length < 6 || rightTeam.value.members.length < 6) {
      throw new Error('双方队伍都至少需要 6 名队员')
    }

    const cached = loadMatchState(matchId.value)
    if (!cached || !cached.lineupReady) {
      uni.redirectTo({
        url: buildLineupUrl(pageQuery.value),
      })
      return
    }
    applyState(cached)
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载排球记分牌失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  tournamentId.value = options?.tournamentId || ''
  matchId.value = options?.matchId || ''
  pageQuery.value = {
    tournamentId: options?.tournamentId || '',
    matchId: options?.matchId || '',
    leftName: options?.leftName || '',
    rightName: options?.rightName || '',
    bestOf: options?.bestOf || '',
    gamesToWin: options?.gamesToWin || '',
    pointsToWin: options?.pointsToWin || '',
    enableDeuce: options?.enableDeuce || '',
    capPoint: options?.capPoint || '',
  }
  updateH5PortraitPreview()
  // #ifdef H5
  window.addEventListener('resize', updateH5PortraitPreview)
  // #endif
  loadMatch()
})

onUnmounted(() => {
  // #ifdef H5
  window.removeEventListener('resize', updateH5PortraitPreview)
  // #endif
})

onBackPress(() => {
  if (isLocked.value) {
    return false
  }
  uni.showToast({
    title: '比赛进行中，请先完成结算',
    icon: 'none',
    duration: 2000,
  })
  return true
})
</script>

<style scoped>
.state-page,
.scoreboard-page,
.score-top,
.score-main,
.score-side,
.action-list,
.set-strip,
.court-header,
.court-board,
.roster-item,
.settlement-actions {
  display: flex;
}

.state-page {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, #122131 0%, #0d1823 100%);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
}

.state-text {
  color: rgba(255, 255, 255, 0.76);
  font-size: 28rpx;
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}

.scoreboard-page {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, #122131 0%, #0d1823 100%);
  color: #ffffff;
  align-items: stretch;
}

.state-page.landscape-preview,
.scoreboard-page.landscape-preview {
  position: fixed;
  top: 0;
  left: 0;
  width: 1280px;
  height: 720px;
  overflow: hidden;
}

.roster-panel {
  width: 25%;
  flex: 0 0 25%;
  min-width: 0;
  padding: 18rpx 18rpx 20rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.04);
  border-right: 1rpx solid rgba(255, 255, 255, 0.08);
  display: flex;
  flex-direction: column;
}

.roster-panel.right {
  border-right: none;
  border-left: 1rpx solid rgba(255, 255, 255, 0.08);
}

.column-head {
  height: 72rpx;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.column-body {
  flex: 1;
  min-height: 0;
}

.roster-head {
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.roster-team {
  font-size: 34rpx;
  font-weight: 800;
  white-space: nowrap;
}

.roster-meta {
  color: #ffb347;
  font-size: 24rpx;
  white-space: nowrap;
}

.roster-scroll {
  height: 100%;
  min-height: 0;
}

.roster-item {
  align-items: center;
  gap: 14rpx;
  padding: 12rpx 16rpx;
  margin-bottom: 8rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid transparent;
}

.roster-item.oncourt {
  border-color: rgba(255, 140, 0, 0.3);
}

.roster-item.active {
  background: rgba(255, 140, 0, 0.16);
  border-color: rgba(255, 140, 0, 0.45);
}

.roster-no {
  width: 78rpx;
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.roster-main {
  flex: 1;
  min-width: 0;
}

.roster-name {
  display: inline-block;
  font-size: 28rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-name.oncourt {
  color: #ffb347;
}

.roster-tags {
  display: inline-block;
  margin-left: 12rpx;
  color: rgba(255, 255, 255, 0.56);
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

.center-panel {
  flex: 1;
  min-width: 0;
  padding: 18rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.center-head {
  justify-content: center;
  margin-bottom: 16rpx;
}

.center-body {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: 16rpx;
}

.score-panel,
.court-card,
.settlement-card {
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 140, 0, 0.16);
}

.score-panel {
  padding: 18rpx 22rpx;
}

.score-top {
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  flex-wrap: nowrap;
}

.game-pill,
.rule-pill,
.target-pill,
.set-pill {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 20rpx;
  white-space: nowrap;
}

.game-pill {
  color: #ffb347;
}

.score-main {
  align-items: stretch;
  margin-top: 16rpx;
  gap: 18rpx;
}

.score-side {
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 260rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 2rpx solid rgba(255, 140, 0, 0.26);
}

.score-side.right {
  border-color: rgba(82, 196, 26, 0.26);
}

.score-name {
  font-size: 34rpx;
  font-weight: 700;
  white-space: nowrap;
}

.score-value {
  font-size: 108rpx;
  line-height: 1;
  font-weight: 800;
  margin-top: 18rpx;
}

.serve-flag {
  margin-top: 12rpx;
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 700;
  white-space: nowrap;
}

.score-center {
  width: 240rpx;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 16rpx;
}

.set-score {
  text-align: center;
  font-size: 56rpx;
  font-weight: 800;
  color: #ffffff;
}

.action-list {
  flex-direction: column;
  gap: 10rpx;
}

.action-btn,
.settlement-btn {
  border: none;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 24rpx;
}

.action-btn::after,
.settlement-btn::after {
  border: none;
}

.action-btn {
  height: 58rpx;
  line-height: 58rpx;
  white-space: nowrap;
}

.action-btn.danger {
  color: #ff7a45;
  border: 1rpx solid rgba(255, 122, 69, 0.35);
}

.set-strip {
  justify-content: center;
  gap: 10rpx;
  margin-top: 16rpx;
  flex-wrap: wrap;
}

.set-pill {
  display: inline-flex;
  gap: 8rpx;
}

.court-card {
  flex: 1;
  min-height: 0;
  padding: 18rpx 20rpx;
  box-sizing: border-box;
}

.court-header {
  align-items: center;
  justify-content: center;
  gap: 12rpx;
}

.court-title {
  font-size: 28rpx;
  font-weight: 700;
  white-space: nowrap;
}

.court-tip {
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
  white-space: nowrap;
}

.court-board {
  height: calc(100% - 56rpx);
  margin-top: 16rpx;
  align-items: stretch;
  gap: 12rpx;
}

.court-half {
  flex: 1;
  padding: 14rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.05);
}

.court-net {
  width: 4rpx;
  align-self: stretch;
  margin-top: -8rpx;
  margin-bottom: -8rpx;
  background: rgba(255, 255, 255, 0.3);
}

.court-grid {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
}

.court-slot {
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.08);
  border: 1rpx solid rgba(255, 140, 0, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.slot-pos {
  color: rgba(255, 255, 255, 0.45);
  font-size: 20rpx;
  white-space: nowrap;
}

.slot-no {
  margin-top: 8rpx;
  font-size: 42rpx;
  font-weight: 800;
  color: #ffffff;
  white-space: nowrap;
}

.settlement-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(0, 0, 0, 0.68);
  z-index: 50;
  padding: 20rpx;
  box-sizing: border-box;
}

.settlement-card {
  width: 560rpx;
  padding: 28rpx;
  box-sizing: border-box;
  text-align: center;
}

.settlement-title {
  display: block;
  font-size: 34rpx;
  font-weight: 800;
}

.settlement-winner {
  display: block;
  margin-top: 12rpx;
  color: #ffb347;
  font-size: 26rpx;
}

.settlement-score {
  display: block;
  margin-top: 18rpx;
  font-size: 82rpx;
  font-weight: 800;
  line-height: 1;
}

.settlement-games {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.76);
  font-size: 24rpx;
}

.settlement-actions {
  gap: 14rpx;
  margin-top: 24rpx;
}

.settlement-btn {
  flex: 1;
  height: 70rpx;
  line-height: 70rpx;
  background: #ff8c00;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.settlement-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}
</style>
