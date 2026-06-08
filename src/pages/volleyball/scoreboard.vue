<template>
  <view class="state-page" v-if="loading">
    <text class="state-text">正在加载排球记分牌...</text>
  </view>

  <view class="state-page" v-else-if="isError">
    <text class="state-text state-error">{{ errorText }}</text>
    <button class="retry-btn" @click="loadMatch">重新加载</button>
  </view>

  <view class="scoreboard-page" v-else>
    <view class="roster-panel left">
      <view class="roster-header">
        <text class="roster-team">{{ leftTeam.name }}</text>
        <text class="roster-meta">{{ leftGameWins }} 局</text>
      </view>
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
          <text class="roster-no">#{{ member.jerseyNumber }}</text>
          <view class="roster-main">
            <text class="roster-name">{{ member.name }}</text>
            <text class="roster-tags">
              <text v-if="member.captain">队长</text>
              <text v-if="member.libero">{{ member.captain ? ' / ' : '' }}自由人</text>
            </text>
          </view>
          <text class="roster-state">{{ isOnCourt('left', member.id) ? '在场' : '替补' }}</text>
        </view>
      </scroll-view>
    </view>

    <view class="center-panel">
      <view class="score-panel">
        <view class="score-top">
          <text class="game-pill">第 {{ currentGameNo }} 局</text>
          <text class="rule-pill">{{ info.bestOf === 5 ? '五局三胜' : '三局两胜' }}</text>
          <text class="target-pill">本局 {{ currentTargetPoints }} 分</text>
        </view>

        <view class="score-main">
          <view class="score-side" @click="addScore('left')">
            <text class="score-name">{{ leftTeam.name }}</text>
            <text class="score-value">{{ leftScore }}</text>
            <text class="serve-flag" :class="{ active: serveSide === 'left' }">发球</text>
          </view>

          <view class="score-center">
            <view class="set-score">{{ leftGameWins }} : {{ rightGameWins }}</view>
            <view class="action-list">
              <button class="action-btn" @click="undo" :disabled="!historyStack.length || isLocked">撤销</button>
              <button class="action-btn" @click="useTimeout('left')" :disabled="isLocked || leftTimeouts <= 0">主队暂停 {{ leftTimeouts }}</button>
              <button class="action-btn" @click="useTimeout('right')" :disabled="isLocked || rightTimeouts <= 0">客队暂停 {{ rightTimeouts }}</button>
              <button class="action-btn danger" @click="openRetireSheet" :disabled="isLocked">退赛</button>
            </view>
          </view>

          <view class="score-side right" @click="addScore('right')">
            <text class="score-name">{{ rightTeam.name }}</text>
            <text class="score-value">{{ rightScore }}</text>
            <text class="serve-flag" :class="{ active: serveSide === 'right' }">发球</text>
          </view>
        </view>

        <view class="set-strip" v-if="gameScores.length">
          <view class="set-pill" v-for="item in gameScores" :key="item.gameNo">
            <text>第{{ item.gameNo }}局</text>
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
              <view class="court-slot" v-for="(memberId, index) in leftCourt" :key="'left_' + index" @click="handleCourtSlot('left', index)">
                <text class="slot-pos">L{{ index + 1 }}</text>
                <text class="slot-no">{{ jerseyText('left', memberId) }}</text>
              </view>
            </view>
          </view>

          <view class="court-net">NET</view>

          <view class="court-half right">
            <view class="court-grid">
              <view class="court-slot" v-for="(memberId, index) in rightCourt" :key="'right_' + index" @click="handleCourtSlot('right', index)">
                <text class="slot-pos">R{{ index + 1 }}</text>
                <text class="slot-no">{{ jerseyText('right', memberId) }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view class="roster-panel right">
      <view class="roster-header">
        <text class="roster-team">{{ rightTeam.name }}</text>
        <text class="roster-meta">{{ rightGameWins }} 局</text>
      </view>
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
          <text class="roster-no">#{{ member.jerseyNumber }}</text>
          <view class="roster-main">
            <text class="roster-name">{{ member.name }}</text>
            <text class="roster-tags">
              <text v-if="member.captain">队长</text>
              <text v-if="member.libero">{{ member.captain ? ' / ' : '' }}自由人</text>
            </text>
          </view>
          <text class="roster-state">{{ isOnCourt('right', member.id) ? '在场' : '替补' }}</text>
        </view>
      </scroll-view>
    </view>

    <view class="lineup-mask" v-if="!lineupReady">
      <view class="lineup-panel" @touchmove.stop>
        <view class="lineup-header">
          <text class="lineup-title">设置首发与发球方</text>
          <text class="lineup-desc">先给双方排好 6 个站位，再选择本场初始发球方。</text>
        </view>

        <scroll-view class="lineup-scroll" scroll-y enable-flex>
        <view class="lineup-columns">
          <view class="lineup-side">
            <text class="lineup-team">{{ leftTeam.name }}</text>
            <view class="draft-slots">
              <view
                class="draft-slot"
                :class="{ active: draftActive.side === 'left' && draftActive.index === index }"
                v-for="(memberId, index) in draftLeftCourt"
                :key="'draft_left_' + index"
                @click="activateDraftSlot('left', index)"
              >
                <text class="draft-pos">L{{ index + 1 }}</text>
                <text class="draft-no">{{ jerseyText('left', memberId) }}</text>
              </view>
            </view>
            <scroll-view class="draft-roster" scroll-y>
              <view
                class="draft-member"
                :class="{ chosen: draftContains('left', member.id) }"
                v-for="member in leftTeam.members"
                :key="'draft_member_left_' + member.id"
                @click="assignDraftMember('left', member.id)"
              >
                <text>#{{ member.jerseyNumber }}</text>
                <text>{{ member.name }}</text>
              </view>
            </scroll-view>
          </view>

          <view class="lineup-side">
            <text class="lineup-team">{{ rightTeam.name }}</text>
            <view class="draft-slots">
              <view
                class="draft-slot"
                :class="{ active: draftActive.side === 'right' && draftActive.index === index }"
                v-for="(memberId, index) in draftRightCourt"
                :key="'draft_right_' + index"
                @click="activateDraftSlot('right', index)"
              >
                <text class="draft-pos">R{{ index + 1 }}</text>
                <text class="draft-no">{{ jerseyText('right', memberId) }}</text>
              </view>
            </view>
            <scroll-view class="draft-roster" scroll-y>
              <view
                class="draft-member"
                :class="{ chosen: draftContains('right', member.id) }"
                v-for="member in rightTeam.members"
                :key="'draft_member_right_' + member.id"
                @click="assignDraftMember('right', member.id)"
              >
                <text>#{{ member.jerseyNumber }}</text>
                <text>{{ member.name }}</text>
              </view>
            </scroll-view>
          </view>
        </view>

        <view class="serve-picker">
          <text class="serve-title">初始发球方</text>
          <view class="serve-options">
            <view class="serve-option" :class="{ active: draftServeSide === 'left' }" @click="draftServeSide = 'left'">{{ leftTeam.name }}</view>
            <view class="serve-option" :class="{ active: draftServeSide === 'right' }" @click="draftServeSide = 'right'">{{ rightTeam.name }}</view>
          </view>
        </view>

        </scroll-view>
        <view class="lineup-footer">
          <button class="confirm-btn" @click="confirmLineup">开始比赛</button>
        </view>
      </view>
    </view>

    <view class="settlement-mask" v-if="isLocked">
      <view class="settlement-card">
        <text class="settlement-title">{{ retiredSide ? '比赛已退赛结算' : '比赛结束' }}</text>
        <text class="settlement-winner">获胜方：{{ winnerName || '待定' }}</text>
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
import { computed, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const STORAGE_KEY = 'volleyball_scoreboard_state'

const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const tournamentId = ref('')
const matchId = ref('')
const info = ref({})
const leftTeam = ref({ name: '主队', members: [] })
const rightTeam = ref({ name: '客队', members: [] })

const leftScore = ref(0)
const rightScore = ref(0)
const leftGameWins = ref(0)
const rightGameWins = ref(0)
const currentGameNo = ref(1)
const gameScores = ref([])
const serveSide = ref('left')
const firstServeSide = ref('left')
const leftTimeouts = ref(2)
const rightTimeouts = ref(2)
const leftCourt = ref(Array(6).fill(''))
const rightCourt = ref(Array(6).fill(''))
const lineupReady = ref(false)
const historyStack = ref([])
const retiredSide = ref('')
const matchEnded = ref(false)
const winnerName = ref('')
const selectedBench = ref({ side: '', memberId: '' })

const draftLeftCourt = ref(Array(6).fill(''))
const draftRightCourt = ref(Array(6).fill(''))
const draftServeSide = ref('left')
const draftActive = ref({ side: 'left', index: 0 })

const currentTargetPoints = computed(() => {
  const finalGameNo = Number(info.value.bestOf || 3)
  return currentGameNo.value === finalGameNo ? 15 : 25
})

const isLocked = computed(() => !!retiredSide.value || matchEnded.value)

const scoreSummary = computed(() => {
  return gameScores.value.map(item => `${item.leftScore}:${item.rightScore}`).join(', ')
})

function storageKey() {
  return matchId.value ? STORAGE_KEY + '_' + matchId.value : STORAGE_KEY
}

function buildSnapshot() {
  return {
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    leftGameWins: leftGameWins.value,
    rightGameWins: rightGameWins.value,
    currentGameNo: currentGameNo.value,
    gameScores: gameScores.value.map(item => ({ ...item })),
    serveSide: serveSide.value,
    firstServeSide: firstServeSide.value,
    leftTimeouts: leftTimeouts.value,
    rightTimeouts: rightTimeouts.value,
    leftCourt: [...leftCourt.value],
    rightCourt: [...rightCourt.value],
    lineupReady: lineupReady.value,
    retiredSide: retiredSide.value,
    matchEnded: matchEnded.value,
    winnerName: winnerName.value,
  }
}

function applySnapshot(snapshot) {
  leftScore.value = Number(snapshot.leftScore || 0)
  rightScore.value = Number(snapshot.rightScore || 0)
  leftGameWins.value = Number(snapshot.leftGameWins || 0)
  rightGameWins.value = Number(snapshot.rightGameWins || 0)
  currentGameNo.value = Number(snapshot.currentGameNo || 1)
  gameScores.value = Array.isArray(snapshot.gameScores) ? snapshot.gameScores : []
  serveSide.value = snapshot.serveSide === 'right' ? 'right' : 'left'
  firstServeSide.value = snapshot.firstServeSide === 'right' ? 'right' : 'left'
  leftTimeouts.value = Number(snapshot.leftTimeouts ?? 2)
  rightTimeouts.value = Number(snapshot.rightTimeouts ?? 2)
  leftCourt.value = Array.isArray(snapshot.leftCourt) ? snapshot.leftCourt.slice(0, 6) : Array(6).fill('')
  rightCourt.value = Array.isArray(snapshot.rightCourt) ? snapshot.rightCourt.slice(0, 6) : Array(6).fill('')
  lineupReady.value = !!snapshot.lineupReady
  retiredSide.value = snapshot.retiredSide || ''
  matchEnded.value = !!snapshot.matchEnded
  winnerName.value = snapshot.winnerName || ''
}

function pushHistory() {
  historyStack.value.push(buildSnapshot())
}

function saveState() {
  uni.setStorageSync(storageKey(), {
    ...buildSnapshot(),
    historyStack: historyStack.value,
  })
}

function restoreState() {
  try {
    const cache = uni.getStorageSync(storageKey())
    if (!cache || typeof cache !== 'object') return false
    applySnapshot(cache)
    historyStack.value = Array.isArray(cache.historyStack) ? cache.historyStack : []
    return true
  } catch (_) {
    return false
  }
}

function memberMap(side) {
  const team = side === 'left' ? leftTeam.value : rightTeam.value
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
  return member ? '#' + member.jerseyNumber : '--'
}

function isOnCourt(side, memberId) {
  const court = side === 'left' ? leftCourt.value : rightCourt.value
  return court.includes(memberId)
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
  saveState()
}

function rotateCourt(side) {
  const source = side === 'left' ? leftCourt.value.slice() : rightCourt.value.slice()
  const rotated = [source[1], source[2], source[3], source[4], source[5], source[0]]
  if (side === 'left') {
    leftCourt.value = rotated
  } else {
    rightCourt.value = rotated
  }
}

function checkWinCondition(myScore, opponentScore) {
  return myScore >= currentTargetPoints.value && myScore - opponentScore >= 2
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
  saveState()
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
    saveState()
    return
  }

  currentGameNo.value += 1
  leftScore.value = 0
  rightScore.value = 0
  leftTimeouts.value = 2
  rightTimeouts.value = 2
  serveSide.value = initialServeForGame(currentGameNo.value)
  saveState()
}

function initialServeForGame(gameNo) {
  return gameNo % 2 === 1 ? firstServeSide.value : firstServeSide.value === 'left' ? 'right' : 'left'
}

function undo() {
  if (!historyStack.value.length || isLocked.value) return
  const snapshot = historyStack.value.pop()
  applySnapshot(snapshot)
  saveState()
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
  saveState()
}

function openRetireSheet() {
  if (isLocked.value) return
  uni.showActionSheet({
    itemList: [`${leftTeam.value.name} 退赛`, `${rightTeam.value.name} 退赛`],
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
    content: `确认 ${side === 'left' ? leftTeam.value.name : rightTeam.value.name} 退赛？`,
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
      saveState()
    },
  })
}

function resetMatch() {
  leftScore.value = 0
  rightScore.value = 0
  leftGameWins.value = 0
  rightGameWins.value = 0
  currentGameNo.value = 1
  gameScores.value = []
  serveSide.value = 'left'
  firstServeSide.value = 'left'
  leftTimeouts.value = 2
  rightTimeouts.value = 2
  leftCourt.value = Array(6).fill('')
  rightCourt.value = Array(6).fill('')
  lineupReady.value = false
  historyStack.value = []
  retiredSide.value = ''
  matchEnded.value = false
  winnerName.value = ''
  selectedBench.value = { side: '', memberId: '' }
  draftLeftCourt.value = Array(6).fill('')
  draftRightCourt.value = Array(6).fill('')
  draftServeSide.value = 'left'
  draftActive.value = { side: 'left', index: 0 }
  saveState()
}

function draftContains(side, memberId) {
  const draft = side === 'left' ? draftLeftCourt.value : draftRightCourt.value
  return draft.includes(memberId)
}

function activateDraftSlot(side, index) {
  draftActive.value = { side, index }
}

function assignDraftMember(side, memberId) {
  const draft = side === 'left' ? draftLeftCourt.value : draftRightCourt.value
  const active = draftActive.value.side === side ? draftActive.value.index : draft.findIndex(item => !item)
  const targetIndex = active >= 0 ? active : 0
  const existingIndex = draft.indexOf(memberId)
  if (existingIndex >= 0) {
    draft.splice(existingIndex, 1, '')
  }
  draft.splice(targetIndex, 1, memberId)
  draftActive.value = {
    side,
    index: Math.min(5, targetIndex + 1),
  }
}

function confirmLineup() {
  if (draftLeftCourt.value.some(item => !item) || draftRightCourt.value.some(item => !item)) {
    uni.showToast({ title: '请先补齐双方首发站位', icon: 'none' })
    return
  }
  leftCourt.value = [...draftLeftCourt.value]
  rightCourt.value = [...draftRightCourt.value]
  lineupReady.value = true
  firstServeSide.value = draftServeSide.value
  serveSide.value = draftServeSide.value
  saveState()
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

function normalizeTeam(participant) {
  return {
    id: participant?.id || '',
    name: participant?.name || '队伍',
    members: Array.isArray(participant?.members)
      ? participant.members.map((member) => ({
          id: member.id,
          name: member.name,
          jerseyNumber: Number(member.jerseyNumber || 0),
          libero: !!member.libero,
          captain: !!member.captain,
        }))
      : [],
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
    const match = (Array.isArray(data.matches) ? data.matches : []).find(item => item.id === matchId.value)
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

    draftLeftCourt.value = Array(6).fill('')
    draftRightCourt.value = Array(6).fill('')
    draftServeSide.value = 'left'
    draftActive.value = { side: 'left', index: 0 }

    if (!restoreState()) {
      resetMatch()
    }
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
  loadMatch()
})

onBackPress(() => {
  if (isLocked.value || !lineupReady.value) {
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
.roster-header,
.score-top,
.score-main,
.score-side,
.action-list,
.set-strip,
.court-header,
.court-board,
.court-grid,
.roster-item,
.draft-slots,
.serve-options,
.lineup-columns,
.settlement-actions {
  display: flex;
}

.state-page {
  width: 100vw;
  height: 100vh;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  gap: 20rpx;
  background: #13202d;
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
}

.roster-panel {
  width: 25vw;
  min-width: 250rpx;
  padding: 20rpx 18rpx;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.04);
  border-right: 1rpx solid rgba(255, 255, 255, 0.08);
}

.roster-panel.right {
  border-right: none;
  border-left: 1rpx solid rgba(255, 255, 255, 0.08);
}

.roster-header {
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18rpx;
}

.roster-team {
  font-size: 34rpx;
  font-weight: 800;
}

.roster-meta {
  color: #ffb347;
  font-size: 24rpx;
}

.roster-scroll {
  height: calc(100vh - 100rpx);
}

.roster-item {
  align-items: center;
  gap: 14rpx;
  padding: 14rpx 16rpx;
  margin-bottom: 10rpx;
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
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-tags {
  display: block;
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.56);
  font-size: 20rpx;
}

.roster-state {
  color: rgba(255, 255, 255, 0.68);
  font-size: 22rpx;
}

.center-panel {
  flex: 1;
  min-width: 0;
  padding: 18rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.score-panel,
.court-card,
.lineup-panel,
.settlement-card {
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 140, 0, 0.16);
}

.score-panel {
  padding: 18rpx 22rpx;
}

.score-top,
.court-header {
  align-items: center;
  justify-content: center;
  gap: 12rpx;
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
}

.score-value {
  font-size: 108rpx;
  line-height: 1;
  font-weight: 800;
  margin-top: 18rpx;
}

.serve-flag {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.36);
  font-size: 24rpx;
}

.serve-flag.active {
  color: #ffb347;
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
.confirm-btn,
.settlement-btn {
  border: none;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 24rpx;
}

.action-btn::after,
.confirm-btn::after,
.settlement-btn::after {
  border: none;
}

.action-btn {
  height: 58rpx;
  line-height: 58rpx;
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

.court-title {
  font-size: 28rpx;
  font-weight: 700;
}

.court-tip {
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
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

.court-half.right {
  border-color: rgba(82, 196, 26, 0.26);
}

.court-net {
  width: 54rpx;
  border-radius: 18rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.16), rgba(255, 255, 255, 0.08));
  color: rgba(255, 255, 255, 0.5);
  font-size: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  letter-spacing: 2rpx;
}

.court-grid {
  width: 100%;
  height: 100%;
  flex-wrap: wrap;
  gap: 12rpx;
}

.court-slot {
  width: calc(50% - 6rpx);
  height: calc(33.333% - 8rpx);
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
}

.slot-no {
  margin-top: 8rpx;
  font-size: 42rpx;
  font-weight: 800;
  color: #ffffff;
}

.lineup-mask,
.settlement-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  background: rgba(0, 0, 0, 0.68);
  z-index: 50;
  padding: 20rpx;
  box-sizing: border-box;
}

.lineup-panel {
  width: 100%;
  max-height: 92vh;
  padding: 20rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.lineup-header {
  text-align: center;
  flex-shrink: 0;
}

.lineup-title {
  display: block;
  font-size: 30rpx;
  font-weight: 800;
}

.lineup-desc {
  display: block;
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 20rpx;
}

.lineup-scroll {
  flex: 1;
  min-height: 0;
  margin-top: 16rpx;
}

.lineup-columns {
  gap: 14rpx;
}

.lineup-side {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.lineup-team {
  text-align: center;
  font-size: 24rpx;
  font-weight: 700;
}

.draft-slots {
  flex-wrap: wrap;
  gap: 8rpx;
}

.draft-slot {
  width: calc(33.333% - 8rpx);
  min-height: 78rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.draft-slot.active {
  border-color: rgba(255, 140, 0, 0.5);
  background: rgba(255, 140, 0, 0.14);
}

.draft-pos {
  color: rgba(255, 255, 255, 0.5);
  font-size: 16rpx;
}

.draft-no {
  margin-top: 4rpx;
  font-size: 24rpx;
  font-weight: 800;
}

.draft-roster {
  height: 260rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.04);
  padding: 8rpx;
  box-sizing: border-box;
}

.draft-member {
  display: flex;
  justify-content: space-between;
  gap: 8rpx;
  padding: 10rpx 12rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.05);
  margin-bottom: 8rpx;
  font-size: 20rpx;
}

.draft-member.chosen {
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
}

.serve-picker {
  margin-top: 14rpx;
}

.serve-title {
  display: block;
  text-align: center;
  color: rgba(255, 255, 255, 0.68);
  font-size: 20rpx;
}

.serve-options {
  justify-content: center;
  gap: 10rpx;
  margin-top: 10rpx;
}

.serve-option {
  min-width: 160rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}

.serve-option.active {
  background: #ff8c00;
  color: #13202d;
  font-weight: 700;
}

.lineup-footer {
  margin-top: 14rpx;
  flex-shrink: 0;
}

.confirm-btn {
  width: 100%;
  height: 64rpx;
  line-height: 64rpx;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 24rpx;
  font-weight: 800;
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
