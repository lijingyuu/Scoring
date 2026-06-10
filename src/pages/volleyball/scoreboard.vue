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
              'captain-active': isCurrentCaptain('left', member.id),
            }"
            v-for="member in leftTeam.members"
            :key="member.id"
            @click="selectBench('left', member.id)"
          >
            <text class="roster-no" :class="{ oncourt: isOnCourt('left', member.id), captain: isCurrentCaptain('left', member.id) }">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: isOnCourt('left', member.id), captain: isCurrentCaptain('left', member.id) }">{{ member.name }}</text>
            </view>
            <text class="roster-tags" v-if="member.captain">队长</text>
          </view>
        </scroll-view>
      </view>
    </view>

    <view class="center-panel">
      <view class="column-head center-head">
        <view class="score-top">
          <view class="score-top-main">
            <text class="game-pill">第 {{ currentGameNo }} 局</text>
            <text class="rule-pill">{{ info.bestOf === 5 ? '五局三胜' : '三局两胜' }}</text>
            <text class="target-pill">本局 {{ currentTargetPoints }} 分</text>
          </view>
          <view class="score-top-actions">
            <button class="action-btn top-action-btn" @click="undo" :disabled="!historyStack.length || isLocked">撤销</button>
            <button class="action-btn danger top-action-btn" @click="openRetireSheet" :disabled="isLocked">退赛</button>
          </view>
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
              <button class="action-btn pause-action-btn" @click="openTimeoutSheet" :disabled="isLocked || (leftTimeouts <= 0 && rightTimeouts <= 0)">暂停</button>
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

          <view class="captain-confirm-overlay" v-if="isCaptainPromptActive">
            <view class="captain-confirm-card">
              <text class="captain-confirm-title">请确认{{ captainPromptTeamName }}场上队长</text>
              <text class="captain-confirm-tip">当前只允许从这 6 名场上队员中选择</text>
              <view class="captain-confirm-list">
                <button
                  v-for="member in captainPromptCandidates"
                  :key="member.id"
                  class="captain-option-btn"
                  :class="{ active: captainCandidateMemberId === member.id }"
                  @click="captainCandidateMemberId = member.id"
                >
                  <text class="captain-option-pos">{{ member.positionLabel }}</text>
                  <text class="captain-option-member">{{ member.jerseyNumber }}号 {{ member.name }}</text>
                </button>
              </view>
              <button class="captain-confirm-btn" @click="confirmCaptainSelection">确定</button>
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
                <view
                  class="court-slot"
                  :class="{ 'captain-active': isCurrentCaptain('left', item.memberId) }"
                  v-for="item in leftCourtDisplaySlots"
                  :key="item.key"
                  @click="handleCourtSlot('left', item.dataIndex)"
                >
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no" :class="{ libero: item.isLibero, captain: isCurrentCaptain('left', item.memberId) }">{{ jerseyText('left', item.memberId) }}</text>
                </view>
              </view>
            </view>

            <view class="court-net"></view>

            <view class="court-half right">
              <view class="court-grid">
                <view
                  class="court-slot"
                  :class="{ 'captain-active': isCurrentCaptain('right', item.memberId) }"
                  v-for="item in rightCourtDisplaySlots"
                  :key="item.key"
                  @click="handleCourtSlot('right', item.dataIndex)"
                >
                  <text class="slot-pos">{{ item.label }}</text>
                  <text class="slot-no" :class="{ libero: item.isLibero, captain: isCurrentCaptain('right', item.memberId) }">{{ jerseyText('right', item.memberId) }}</text>
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
              'captain-active': isCurrentCaptain('right', member.id),
            }"
            v-for="member in rightTeam.members"
            :key="member.id"
            @click="selectBench('right', member.id)"
          >
            <text class="roster-no" :class="{ oncourt: isOnCourt('right', member.id), captain: isCurrentCaptain('right', member.id) }">{{ member.jerseyNumber }}</text>
            <view class="roster-main">
              <text class="roster-name" :class="{ oncourt: isOnCourt('right', member.id), captain: isCurrentCaptain('right', member.id) }">{{ member.name }}</text>
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
  buildHistoryEntry,
  buildLineupUrl,
  clearMatchState,
  cloneCourt,
  cloneLiberoSetup,
  cloneLiberoRuntime,
  createEmptyLiberoRuntime,
  createEmptyMatchState,
  formatTeamName,
  loadMatchState,
  MAX_HISTORY_ENTRIES,
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
const leftLiberoSetup = ref({ pairIndexes: [], libero1Id: '', libero2Id: '' })
const rightLiberoSetup = ref({ pairIndexes: [], libero1Id: '', libero2Id: '' })
const leftLiberoRuntime = ref(createEmptyLiberoRuntime())
const rightLiberoRuntime = ref(createEmptyLiberoRuntime())
const leftCaptainMemberId = ref('')
const rightCaptainMemberId = ref('')
const matchEvents = ref([])
const nextEventSeq = ref(1)
const lastSyncedEventSeq = ref(0)
const lineupReady = ref(false)
const historyStack = ref([])
const retiredSide = ref('')
const matchEnded = ref(false)
const winnerName = ref('')
const selectedBench = ref({ side: '', memberId: '' })
const captainPromptQueue = ref([])
const captainCandidateMemberId = ref('')

const isH5PortraitPreview = ref(false)
const previewScale = ref(1)
const previewOffsetX = ref(0)
const previewOffsetY = ref(0)

let eventFlushTimer = null
let eventFlushPromise = null

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
const captainPromptSide = computed(() => captainPromptQueue.value[0] || '')
const isCaptainPromptActive = computed(() => !!captainPromptSide.value)
const captainPromptCandidates = computed(() => buildOnCourtMembers(captainPromptSide.value))
const captainPromptTeamName = computed(() => {
  if (captainPromptSide.value === 'left') return leftDisplayTeamName.value
  if (captainPromptSide.value === 'right') return rightDisplayTeamName.value
  return ''
})
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
    ? [2, 5, 1, 4, 0, 3]
    : [3, 0, 4, 1, 5, 2]
  return order.map((dataIndex, index) => ({
    key: `${side}_${dataIndex}`,
    dataIndex,
    label: labels[index],
    memberId: court[dataIndex] || '',
    isLibero: isActiveLiberoOnSlot(side, dataIndex, court[dataIndex] || ''),
  }))
}

const SLOT_OPPOSITE_MAP = {
  0: 5,
  1: 4,
  2: 3,
  3: 2,
  4: 1,
  5: 0,
}

function getCourtBySide(side) {
  return side === 'right' ? rightCourt.value : leftCourt.value
}

function setCourtBySide(side, court) {
  if (side === 'right') {
    rightCourt.value = court
  } else {
    leftCourt.value = court
  }
}

function getBaseCourtBySide(side) {
  return side === 'right' ? baseRightCourt.value : baseLeftCourt.value
}

function getLiberoSetupBySide(side) {
  return side === 'right' ? rightLiberoSetup.value : leftLiberoSetup.value
}

function getLiberoRuntimeBySide(side) {
  return side === 'right' ? rightLiberoRuntime.value : leftLiberoRuntime.value
}

function setLiberoRuntimeBySide(side, runtime) {
  const normalized = cloneLiberoRuntime(runtime)
  if (side === 'right') {
    rightLiberoRuntime.value = normalized
  } else {
    leftLiberoRuntime.value = normalized
  }
}

function getCaptainBySide(side) {
  return side === 'right' ? rightCaptainMemberId.value : leftCaptainMemberId.value
}

function setCaptainBySide(side, memberId) {
  if (side === 'right') {
    rightCaptainMemberId.value = memberId || ''
  } else {
    leftCaptainMemberId.value = memberId || ''
  }
}

function originalCaptainMemberId(side) {
  const team = side === 'right' ? rightTeam.value : leftTeam.value
  return (team.members || []).find((member) => member.captain)?.id || ''
}

function buildOnCourtMembers(side) {
  if (!side) return []
  const court = getCourtBySide(side)
  const positionLabels = ['4号位', '3号位', '2号位', '5号位', '6号位', '1号位']
  return court
    .map((memberId, index) => {
      const member = memberById(side, memberId)
      if (!member) return null
      return {
        ...member,
        slotIndex: index,
        positionLabel: positionLabels[index] || '',
      }
    })
    .filter(Boolean)
}

function isCurrentCaptain(side, memberId) {
  return !!memberId && getCaptainBySide(side) === memberId
}

function removeCaptainPrompt(side) {
  if (!side) return
  captainPromptQueue.value = captainPromptQueue.value.filter((item) => item !== side)
  captainCandidateMemberId.value = captainPromptCandidates.value[0]?.id || ''
}

function ensureCaptainPrompt(side) {
  if (!side) return
  if (!captainPromptQueue.value.includes(side)) {
    captainPromptQueue.value = [...captainPromptQueue.value, side]
  }
  if (!captainCandidateMemberId.value) {
    captainCandidateMemberId.value = buildOnCourtMembers(side)[0]?.id || ''
  }
}

function clonePayload(payload) {
  return JSON.parse(JSON.stringify(payload || {}))
}

function hasPendingEvents() {
  return matchEvents.value.some((item) => item.syncStatus !== 'synced')
}

function appendMatchEvent(type, payload, options = {}) {
  const event = {
    seq: nextEventSeq.value,
    type,
    gameNo: currentGameNo.value,
    leftScore: leftScore.value,
    rightScore: rightScore.value,
    serveSide: serveSide.value,
    payload: clonePayload(payload),
    syncStatus: 'pending',
  }
  matchEvents.value.push(event)
  nextEventSeq.value += 1
  if (options.scheduleFlush !== false) {
    scheduleEventFlush()
  }
  return event
}

function scheduleEventFlush(delay = 800) {
  if (!matchId.value || !hasPendingEvents()) return
  if (eventFlushTimer) {
    clearTimeout(eventFlushTimer)
  }
  eventFlushTimer = setTimeout(() => {
    eventFlushTimer = null
    flushPendingEvents()
  }, delay)
}

async function flushPendingEvents() {
  if (!matchId.value || !hasPendingEvents()) {
    return true
  }
  if (eventFlushPromise) {
    return eventFlushPromise
  }

  const pendingEvents = matchEvents.value
    .filter((item) => item.syncStatus !== 'synced')
    .map((item) => ({
      eventSeq: item.seq,
      eventType: item.type,
      gameNo: item.gameNo,
      leftScore: item.leftScore,
      rightScore: item.rightScore,
      serveSide: item.serveSide,
      payloadJson: JSON.stringify(item.payload || {}),
    }))

  if (!pendingEvents.length) {
    return true
  }

  eventFlushPromise = request('/api/v1/matches/' + matchId.value + '/events', {
    method: 'PUT',
    data: { events: pendingEvents },
    silent: true,
  })
    .then(() => {
      const syncedSeqs = new Set(pendingEvents.map((item) => item.eventSeq))
      let maxSyncedSeq = lastSyncedEventSeq.value
      matchEvents.value = matchEvents.value.map((item) => {
        if (!syncedSeqs.has(item.seq)) return item
        maxSyncedSeq = Math.max(maxSyncedSeq, item.seq)
        return {
          ...item,
          syncStatus: 'synced',
        }
      })
      lastSyncedEventSeq.value = maxSyncedSeq
      persistState()
      return true
    })
    .catch(() => false)
    .finally(() => {
      eventFlushPromise = null
    })

  return eventFlushPromise
}

function getRuntimeRoleEntries(runtime, setup) {
  return [
    {
      slotField: 'role1SlotIndex',
      playerField: 'role1PlayerId',
      liberoId: setup.libero1Id || '',
    },
    {
      slotField: 'role2SlotIndex',
      playerField: 'role2PlayerId',
      liberoId: setup.libero2Id || '',
    },
  ].map((item) => ({
    ...item,
    slotIndex: runtime[item.slotField],
    playerId: runtime[item.playerField] || '',
  }))
}

function getBoundLiberoIds(setup) {
  return [setup.libero1Id || '', setup.libero2Id || ''].filter(Boolean)
}

function isOppositePair(slotIndex, oppositeIndex) {
  return Number.isInteger(slotIndex) && slotIndex >= 0 && slotIndex < 6 && oppositeSlotIndex(slotIndex) === oppositeIndex
}

function buildRoleSeeds(side, setup) {
  const baseCourt = cloneCourt(getBaseCourtBySide(side))
  return setup.pairIndexes
    .map((slotIndex) => {
      const memberId = baseCourt[slotIndex] || ''
      const member = memberById(side, memberId)
      return {
        slotIndex,
        memberId,
        jerseyNumber: Number(member?.jerseyNumber || 999),
      }
    })
    .filter((item) => item.memberId)
    .sort((left, right) => {
      if (left.jerseyNumber !== right.jerseyNumber) {
        return left.jerseyNumber - right.jerseyNumber
      }
      return left.slotIndex - right.slotIndex
    })
}

function runtimeHasDuplicateCourtMembers(side) {
  const seen = new Set()
  for (const memberId of cloneCourt(getCourtBySide(side)).filter(Boolean)) {
    if (seen.has(memberId)) {
      return true
    }
    seen.add(memberId)
  }
  return false
}

function isLiberoRuntimeComplete(runtime) {
  return (
    runtime.role1SlotIndex >= 0 &&
    runtime.role2SlotIndex >= 0 &&
    !!runtime.role1PlayerId &&
    !!runtime.role2PlayerId
  )
}

function isTeamLiberoRuntimeValid(side, runtime, setup) {
  if (setup.pairIndexes.length !== 2) {
    return false
  }
  if (!isOppositePair(setup.pairIndexes[0], setup.pairIndexes[1])) {
    return false
  }
  if (!isLiberoRuntimeComplete(runtime)) {
    return false
  }
  if (!isOppositePair(runtime.role1SlotIndex, runtime.role2SlotIndex)) {
    return false
  }

  const memberIds = new Set((side === 'right' ? rightTeam.value.members : leftTeam.value.members).map((member) => member.id))
  const boundLiberoIds = new Set(getBoundLiberoIds(setup))
  if (!memberIds.has(runtime.role1PlayerId) || !memberIds.has(runtime.role2PlayerId)) {
    return false
  }
  if (runtime.role1PlayerId === runtime.role2PlayerId) {
    return false
  }
  if (boundLiberoIds.has(runtime.role1PlayerId) || boundLiberoIds.has(runtime.role2PlayerId)) {
    return false
  }
  return !runtimeHasDuplicateCourtMembers(side)
}

function oppositeSlotIndex(slotIndex) {
  return SLOT_OPPOSITE_MAP[slotIndex] ?? -1
}

function rotateSlotIndex(slotIndex) {
  if (!Number.isInteger(slotIndex) || slotIndex < 0 || slotIndex >= 6) {
    return -1
  }
  const nextSlotIndexMap = {
    0: 1,
    1: 2,
    2: 5,
    3: 0,
    4: 3,
    5: 4,
  }
  return nextSlotIndexMap[slotIndex] ?? -1
}

function isFrontSlot(slotIndex) {
  return slotIndex >= 0 && slotIndex <= 2
}

function detectRoleSlotIndex(currentCourt, playerId, liberoId, fallbackSlotIndex) {
  const playerIndex = playerId ? currentCourt.indexOf(playerId) : -1
  if (playerIndex >= 0) {
    return playerIndex
  }
  const liberoIndex = liberoId ? currentCourt.indexOf(liberoId) : -1
  if (liberoIndex >= 0) {
    return liberoIndex
  }
  return fallbackSlotIndex
}

function buildInitialLiberoRuntime(side) {
  const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
  if (setup.pairIndexes.length !== 2) {
    return createEmptyLiberoRuntime()
  }

  const currentCourt = cloneCourt(getCourtBySide(side))
  const seeds = buildRoleSeeds(side, setup)

  if (seeds.length !== 2) {
    return createEmptyLiberoRuntime()
  }

  const runtime = createEmptyLiberoRuntime()
  runtime.role1SlotIndex = detectRoleSlotIndex(currentCourt, seeds[0].memberId, setup.libero1Id, seeds[0].slotIndex)
  runtime.role2SlotIndex = detectRoleSlotIndex(currentCourt, seeds[1].memberId, setup.libero2Id, seeds[1].slotIndex)

  if (runtime.role1SlotIndex === runtime.role2SlotIndex) {
    runtime.role2SlotIndex = oppositeSlotIndex(runtime.role1SlotIndex)
  }
  if (runtime.role1SlotIndex < 0 && runtime.role2SlotIndex >= 0) {
    runtime.role1SlotIndex = oppositeSlotIndex(runtime.role2SlotIndex)
  }
  if (runtime.role2SlotIndex < 0 && runtime.role1SlotIndex >= 0) {
    runtime.role2SlotIndex = oppositeSlotIndex(runtime.role1SlotIndex)
  }
  if (runtime.role1SlotIndex < 0) {
    runtime.role1SlotIndex = seeds[0].slotIndex
  }
  if (runtime.role2SlotIndex < 0) {
    runtime.role2SlotIndex = seeds[1].slotIndex
  }

  runtime.role1PlayerId = seeds[0].memberId
  runtime.role2PlayerId = seeds[1].memberId

  const role1CurrentMember = currentCourt[runtime.role1SlotIndex] || ''
  if (role1CurrentMember && role1CurrentMember !== setup.libero1Id) {
    runtime.role1PlayerId = role1CurrentMember
  }
  const role2CurrentMember = currentCourt[runtime.role2SlotIndex] || ''
  if (role2CurrentMember && role2CurrentMember !== setup.libero2Id) {
    runtime.role2PlayerId = role2CurrentMember
  }

  return cloneLiberoRuntime(runtime)
}

function ensureTeamLiberoRuntime(side) {
  const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
  const currentRuntime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
  if (setup.pairIndexes.length !== 2) {
    const hasRuntime =
      currentRuntime.role1SlotIndex >= 0 ||
      currentRuntime.role2SlotIndex >= 0 ||
      currentRuntime.role1PlayerId ||
      currentRuntime.role2PlayerId
    if (hasRuntime) {
      setLiberoRuntimeBySide(side, createEmptyLiberoRuntime())
      return true
    }
    return false
  }

  if (isTeamLiberoRuntimeValid(side, currentRuntime, setup)) {
    return false
  }

  setLiberoRuntimeBySide(side, buildInitialLiberoRuntime(side))
  return true
}

function ensureAllLiberoRuntimeReady() {
  const leftChanged = ensureTeamLiberoRuntime('left')
  const rightChanged = ensureTeamLiberoRuntime('right')
  return leftChanged || rightChanged
}

function rotateTeamLiberoRuntime(side) {
  const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
  runtime.role1SlotIndex = rotateSlotIndex(runtime.role1SlotIndex)
  runtime.role2SlotIndex = rotateSlotIndex(runtime.role2SlotIndex)
  setLiberoRuntimeBySide(side, runtime)
}

function shouldRoleUseLibero(side, slotIndex, liberoId) {
  if (!liberoId) {
    return false
  }
  if (isFrontSlot(slotIndex)) {
    return false
  }
  if (slotIndex === 5) {
    return serveSide.value !== side
  }
  return true
}

function compareLiberoAssignmentPriority(left, right) {
  if (left.shouldUseLibero !== right.shouldUseLibero) {
    return left.shouldUseLibero ? 1 : -1
  }
  if (left.slotIndex === 5 && right.slotIndex !== 5) {
    return 1
  }
  if (right.slotIndex === 5 && left.slotIndex !== 5) {
    return -1
  }
  return right.slotIndex - left.slotIndex
}

function buildLiberoAssignments(side) {
  const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
  if (setup.pairIndexes.length !== 2) {
    return []
  }

  const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
  return getRuntimeRoleEntries(runtime, setup)
    .filter((role) => role.slotIndex >= 0 && role.slotIndex < 6)
    .map((role) => ({
      ...role,
      shouldUseLibero: shouldRoleUseLibero(side, role.slotIndex, role.liberoId),
    }))
}

function isActiveLiberoOnSlot(side, slotIndex, memberId) {
  if (!memberId) {
    return false
  }

  const assignments = buildLiberoAssignments(side)
  const liberoAssignmentMap = new Map()
  for (const assignment of assignments) {
    if (!assignment.shouldUseLibero || !assignment.liberoId) {
      continue
    }
    const current = liberoAssignmentMap.get(assignment.liberoId)
    if (!current || compareLiberoAssignmentPriority(assignment, current) > 0) {
      liberoAssignmentMap.set(assignment.liberoId, assignment)
    }
  }

  return assignments.some((assignment) => {
    return (
      assignment.slotIndex === slotIndex &&
      assignment.liberoId === memberId &&
      assignment.shouldUseLibero &&
      liberoAssignmentMap.get(assignment.liberoId) === assignment
    )
  })
}

function settleTeamLibero(side) {
  ensureTeamLiberoRuntime(side)

  const setup = cloneLiberoSetup(getLiberoSetupBySide(side))
  if (setup.pairIndexes.length !== 2) {
    return false
  }

  const runtime = cloneLiberoRuntime(getLiberoRuntimeBySide(side))
  const court = cloneCourt(getCourtBySide(side))
  const boundLiberoIds = new Set(getBoundLiberoIds(setup))
  let changed = false
  const assignments = []

  for (const role of getRuntimeRoleEntries(runtime, setup)) {
    if (role.slotIndex < 0 || role.slotIndex >= 6) {
      continue
    }

    const currentMemberId = court[role.slotIndex] || ''
    if (
      currentMemberId &&
      currentMemberId !== role.liberoId &&
      !boundLiberoIds.has(currentMemberId) &&
      currentMemberId !== runtime[role.playerField]
    ) {
      runtime[role.playerField] = currentMemberId
      changed = true
    }

    const currentPlayerId = runtime[role.playerField] || ''
    if (!currentPlayerId) {
      continue
    }

    const shouldUseLibero = shouldRoleUseLibero(side, role.slotIndex, role.liberoId)
    assignments.push({
      ...role,
      currentPlayerId,
      shouldUseLibero,
      targetMemberId: shouldUseLibero ? role.liberoId || currentPlayerId : currentPlayerId,
    })
  }

  const liberoAssignmentMap = new Map()
  for (const assignment of assignments) {
    if (!assignment.shouldUseLibero || !assignment.liberoId) {
      continue
    }
    const current = liberoAssignmentMap.get(assignment.liberoId)
    if (!current || compareLiberoAssignmentPriority(assignment, current) > 0) {
      liberoAssignmentMap.set(assignment.liberoId, assignment)
    }
  }

  for (const assignment of assignments) {
    const preferredAssignment = assignment.liberoId ? liberoAssignmentMap.get(assignment.liberoId) : null
    const targetMemberId =
      preferredAssignment === assignment
        ? assignment.targetMemberId
        : assignment.currentPlayerId
    if (targetMemberId && court[assignment.slotIndex] !== targetMemberId) {
      court[assignment.slotIndex] = targetMemberId
      changed = true
    }
  }

  if (changed) {
    setLiberoRuntimeBySide(side, runtime)
    setCourtBySide(side, court)
  }
  return changed
}

function settleAllLiberoStates() {
  const leftChanged = settleTeamLibero('left')
  const rightChanged = settleTeamLibero('right')
  return leftChanged || rightChanged
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
    leftLiberoSetup: leftLiberoSetup.value,
    rightLiberoSetup: rightLiberoSetup.value,
    leftLiberoRuntime: leftLiberoRuntime.value,
    rightLiberoRuntime: rightLiberoRuntime.value,
    leftCaptainMemberId: leftCaptainMemberId.value,
    rightCaptainMemberId: rightCaptainMemberId.value,
    matchEvents: matchEvents.value,
    nextEventSeq: nextEventSeq.value,
    lastSyncedEventSeq: lastSyncedEventSeq.value,
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

function buildHistorySnapshot() {
  return buildHistoryEntry({
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
    leftLiberoSetup: leftLiberoSetup.value,
    rightLiberoSetup: rightLiberoSetup.value,
    leftLiberoRuntime: leftLiberoRuntime.value,
    rightLiberoRuntime: rightLiberoRuntime.value,
    leftCaptainMemberId: leftCaptainMemberId.value,
    rightCaptainMemberId: rightCaptainMemberId.value,
    matchEvents: matchEvents.value,
    nextEventSeq: nextEventSeq.value,
    lastSyncedEventSeq: lastSyncedEventSeq.value,
    draftLeftCourt: baseLeftCourt.value,
    draftRightCourt: baseRightCourt.value,
    draftServeSide: serveSide.value,
    lineupReady: lineupReady.value,
    retiredSide: retiredSide.value,
    matchEnded: matchEnded.value,
    winnerName: winnerName.value,
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
  leftLiberoSetup.value = cloneLiberoSetup(normalized.leftLiberoSetup)
  rightLiberoSetup.value = cloneLiberoSetup(normalized.rightLiberoSetup)
  leftLiberoRuntime.value = cloneLiberoRuntime(normalized.leftLiberoRuntime)
  rightLiberoRuntime.value = cloneLiberoRuntime(normalized.rightLiberoRuntime)
  leftCaptainMemberId.value = normalized.leftCaptainMemberId || ''
  rightCaptainMemberId.value = normalized.rightCaptainMemberId || ''
  matchEvents.value = Array.isArray(normalized.matchEvents) ? normalized.matchEvents.map((item) => ({ ...item, payload: clonePayload(item.payload) })) : []
  nextEventSeq.value = Number(normalized.nextEventSeq || 1)
  lastSyncedEventSeq.value = Number(normalized.lastSyncedEventSeq || 0)
  lineupReady.value = normalized.lineupReady
  retiredSide.value = normalized.retiredSide
  matchEnded.value = normalized.matchEnded
  winnerName.value = normalized.winnerName
  historyStack.value = normalized.historyStack
}

function persistState() {
  historyStack.value = historyStack.value.slice(-MAX_HISTORY_ENTRIES)
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

function buildRosterSnapshotPayload() {
  return {
    leftMembers: (leftTeam.value.members || []).map((member) => ({
      id: member.id,
      name: member.name,
      jerseyNumber: member.jerseyNumber,
      captain: !!member.captain,
      libero: !!member.libero,
    })),
    rightMembers: (rightTeam.value.members || []).map((member) => ({
      id: member.id,
      name: member.name,
      jerseyNumber: member.jerseyNumber,
      captain: !!member.captain,
      libero: !!member.libero,
    })),
  }
}

function buildLineupSnapshotPayload() {
  return {
    left: {
      court: cloneCourt(leftCourt.value),
      middlePairIndexes: [...(leftLiberoSetup.value?.pairIndexes || [])],
      libero1Id: leftLiberoSetup.value?.libero1Id || '',
      libero2Id: leftLiberoSetup.value?.libero2Id || '',
    },
    right: {
      court: cloneCourt(rightCourt.value),
      middlePairIndexes: [...(rightLiberoSetup.value?.pairIndexes || [])],
      libero1Id: rightLiberoSetup.value?.libero1Id || '',
      libero2Id: rightLiberoSetup.value?.libero2Id || '',
    },
    serveSide: serveSide.value,
  }
}

function ensureBootstrapEvents() {
  let changed = false
  if (!matchEvents.value.some((item) => item.type === 'roster_snapshot')) {
    appendMatchEvent('roster_snapshot', buildRosterSnapshotPayload(), { scheduleFlush: false })
    changed = true
  }
  if (!matchEvents.value.some((item) => item.type === 'lineup_snapshot' && item.gameNo === currentGameNo.value)) {
    appendMatchEvent('lineup_snapshot', buildLineupSnapshotPayload(), { scheduleFlush: false })
    changed = true
  }
  return changed
}

function syncCaptainState(options = {}) {
  const recordAutoEvent = !!options.recordAutoEvent
  let changed = false
  captainPromptQueue.value = []
  for (const side of ['left', 'right']) {
    const originalCaptainId = originalCaptainMemberId(side)
    const currentCaptainId = getCaptainBySide(side)
    const originalCaptainOnCourt = originalCaptainId && isOnCourt(side, originalCaptainId)
    if (originalCaptainOnCourt) {
      if (currentCaptainId !== originalCaptainId) {
        setCaptainBySide(side, originalCaptainId)
        changed = true
        if (recordAutoEvent) {
          appendMatchEvent('captain_change', {
            side,
            captainMemberId: originalCaptainId,
            originalCaptainMemberId: originalCaptainId,
            source: 'auto',
          })
        }
      }
      continue
    }

    if (currentCaptainId && isOnCourt(side, currentCaptainId)) {
      continue
    }

    if (currentCaptainId) {
      setCaptainBySide(side, '')
      changed = true
    }
    if (lineupReady.value && buildOnCourtMembers(side).length === 6) {
      captainPromptQueue.value.push(side)
    }
  }
  captainCandidateMemberId.value = captainPromptCandidates.value[0]?.id || ''
  return changed
}

function confirmCaptainSelection() {
  const side = captainPromptSide.value
  if (!side) return
  const member = captainPromptCandidates.value.find((item) => item.id === captainCandidateMemberId.value)
  if (!member) {
    uni.showToast({ title: '请先选择场上队长', icon: 'none' })
    return
  }
  setCaptainBySide(side, member.id)
  appendMatchEvent('captain_change', {
    side,
    captainMemberId: member.id,
    originalCaptainMemberId: originalCaptainMemberId(side),
    source: 'manual',
  })
  removeCaptainPrompt(side)
  persistState()
  scheduleEventFlush(200)
}

function pushHistory() {
  historyStack.value.push(buildHistorySnapshot())
  historyStack.value = historyStack.value.slice(-MAX_HISTORY_ENTRIES)
}

function selectBench(side, memberId) {
  if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value) return
  if (isOnCourt(side, memberId)) return
  const same = selectedBench.value.side === side && selectedBench.value.memberId === memberId
  selectedBench.value = same ? { side: '', memberId: '' } : { side, memberId }
}

function handleCourtSlot(side, index) {
  if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value) return
  if (selectedBench.value.side !== side || !selectedBench.value.memberId) return
  const previousCourt = side === 'left' ? leftCourt.value : rightCourt.value
  const outMemberId = previousCourt[index] || ''
  const inMemberId = selectedBench.value.memberId
  pushHistory()
  if (side === 'left') {
    leftCourt.value.splice(index, 1, inMemberId)
  } else {
    rightCourt.value.splice(index, 1, inMemberId)
  }
  settleTeamLibero(side)
  selectedBench.value = { side: '', memberId: '' }
  appendMatchEvent('substitution', {
    side,
    outMemberId,
    inMemberId,
  })
  syncCaptainState({ recordAutoEvent: true })
  persistState()
}

function rotateCourt(side) {
  const source = side === 'right' ? rightCourt.value.slice() : leftCourt.value.slice()
  const rotated = [source[3], source[0], source[1], source[4], source[5], source[2]]
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
  if (!lineupReady.value || isLocked.value || isCaptainPromptActive.value) return
  pushHistory()

  if (side === 'left') {
    leftScore.value += 1
  } else {
    rightScore.value += 1
  }

  if (serveSide.value !== side) {
    rotateCourt(side)
    rotateTeamLiberoRuntime(side)
    serveSide.value = side
  }

  settleAllLiberoStates()

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
  syncCaptainState({ recordAutoEvent: false })
  persistState()
  scheduleEventFlush(200)
}

function useTimeout(side) {
  if (isLocked.value || isCaptainPromptActive.value) return
  if (side === 'left') {
    if (leftTimeouts.value <= 0) return
    pushHistory()
    leftTimeouts.value -= 1
  } else {
    if (rightTimeouts.value <= 0) return
    pushHistory()
    rightTimeouts.value -= 1
  }
  appendMatchEvent('timeout', { side })
  persistState()
}

function openTimeoutSheet() {
  if (isLocked.value || isCaptainPromptActive.value) return
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
  if (isLocked.value || isCaptainPromptActive.value) return
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
  if (isLocked.value || isCaptainPromptActive.value) return
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

  const eventSynced = await flushPendingEvents()
  if (!eventSynced) {
    uni.showToast({ title: '比赛记录未完整同步，请稍后重试', icon: 'none' })
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
    const isNewGameEntry = !matchEvents.value.some((item) => item.type === 'lineup_snapshot' && item.gameNo === currentGameNo.value)
    if (isNewGameEntry) {
      leftCaptainMemberId.value = ''
      rightCaptainMemberId.value = ''
    }
    const initialized = ensureAllLiberoRuntimeReady()
    const settled = settleAllLiberoStates()
    const bootstrapped = ensureBootstrapEvents()
    const captainChanged = syncCaptainState({ recordAutoEvent: true })
    if (initialized || settled || bootstrapped || captainChanged) {
      persistState()
    }
    scheduleEventFlush(200)
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
  if (eventFlushTimer) {
    clearTimeout(eventFlushTimer)
    eventFlushTimer = null
  }
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
  gap: clamp(12px, 2vmin, 22px);
  overflow: hidden;
}

.state-text {
  color: rgba(255, 255, 255, 0.76);
  font-size: clamp(14px, 1.8vmin, 24px);
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: clamp(150px, 26vmin, 240px);
  height: clamp(42px, 6vmin, 68px);
  line-height: clamp(42px, 6vmin, 68px);
  border-radius: clamp(10px, 1.3vmin, 16px);
  border: none;
  background: #ff8c00;
  color: #13202d;
  font-size: clamp(14px, 1.6vmin, 22px);
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}

.scoreboard-page {
  --page-pad: clamp(10px, 1.4vmin, 20px);
  --panel-gap: clamp(8px, 1vmin, 14px);
  --panel-radius: clamp(14px, 1.8vmin, 24px);
  --soft-radius: clamp(10px, 1.4vmin, 18px);
  --roster-width: clamp(130px, 16vmin, 220px);
  --head-height: clamp(36px, 5.6vmin, 64px);
  --small-text: clamp(10px, 1.15vmin, 14px);
  --body-text: clamp(11px, 1.35vmin, 16px);
  --title-text: clamp(15px, 2.1vmin, 28px);
  --score-name-text: clamp(14px, 1.9vmin, 24px);
  --score-value-text: clamp(36px, 7.2vmin, 96px);
  --score-center-width: clamp(96px, 13vmin, 180px);
  --action-height: clamp(34px, 5.1vmin, 56px);
  --court-gap: clamp(6px, 0.85vmin, 12px);
  --court-label-text: clamp(10px, 1.05vmin, 14px);
  --court-number-text: clamp(22px, 3.1vmin, 42px);
  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, #122131 0%, #0d1823 100%);
  color: #ffffff;
  box-sizing: border-box;
  padding: var(--page-pad);
  gap: var(--panel-gap);
  align-items: stretch;
  overflow: hidden;
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
  width: var(--roster-width);
  flex: 0 0 var(--roster-width);
  min-width: 0;
  min-height: 0;
  padding: clamp(8px, 1vmin, 14px);
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.04);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--panel-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.roster-panel.right {
  border-right: 1px solid rgba(255, 255, 255, 0.08);
}

.column-head {
  min-height: var(--head-height);
  flex-shrink: 0;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.column-body {
  flex: 1;
  min-height: 0;
  overflow: hidden;
}

.roster-head {
  justify-content: space-between;
  gap: clamp(4px, 0.5vmin, 8px);
  margin-bottom: clamp(8px, 1vmin, 12px);
}

.roster-team {
  min-width: 0;
  font-size: var(--title-text);
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.roster-meta {
  color: #ffb347;
  font-size: var(--body-text);
  white-space: nowrap;
  flex-shrink: 0;
}

.roster-scroll {
  height: 100%;
  min-height: 0;
}

.roster-item {
  align-items: center;
  gap: clamp(4px, 0.45vmin, 8px);
  padding: clamp(6px, 0.75vmin, 10px) clamp(6px, 0.8vmin, 10px);
  margin-bottom: clamp(5px, 0.55vmin, 8px);
  border-radius: var(--soft-radius);
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.06);
}

.roster-item.oncourt {
  border-color: rgba(255, 140, 0, 0.3);
}

.roster-item.active {
  background: rgba(255, 140, 0, 0.16);
  border-color: rgba(255, 140, 0, 0.45);
}

.roster-item.captain-active {
  border-color: rgba(46, 196, 182, 0.72);
  box-shadow: 0 0 0 1px rgba(46, 196, 182, 0.2);
}

.roster-no {
  width: clamp(20px, 2.6vmin, 34px);
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.82);
  font-size: var(--body-text);
  font-weight: 700;
}

.roster-no.oncourt {
  color: #ffb347;
}

.roster-no.captain {
  color: #2ec4b6;
}

.roster-item.captain-active .roster-no {
  color: #2ec4b6;
}

.roster-main {
  flex: 1;
  min-width: 0;
}

.roster-name {
  display: inline-block;
  width: 100%;
  font-size: var(--body-text);
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.roster-name.oncourt {
  color: #ffb347;
}

.roster-name.captain {
  color: #2ec4b6;
}

.roster-tags {
  display: inline-block;
  flex-shrink: 0;
  margin-left: clamp(2px, 0.35vmin, 6px);
  color: rgba(255, 255, 255, 0.56);
  font-size: var(--small-text);
  font-weight: 700;
  white-space: nowrap;
}

.roster-tags.captain {
  color: #2ec4b6;
}

.center-panel {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: clamp(8px, 1vmin, 16px);
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: var(--panel-radius);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.center-head {
  justify-content: center;
  margin-bottom: clamp(8px, 1vmin, 12px);
}

.center-body {
  display: flex;
  flex: 1;
  min-height: 0;
  flex-direction: column;
  gap: var(--panel-gap);
  overflow: hidden;
}

.score-panel,
.court-card,
.settlement-card {
  border-radius: var(--panel-radius);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 140, 0, 0.16);
}

.score-panel {
  flex-shrink: 0;
  padding: clamp(10px, 1.1vmin, 16px) clamp(10px, 1.2vmin, 18px);
  overflow: hidden;
  position: relative;
}

.score-top {
  align-items: center;
  justify-content: space-between;
  gap: clamp(4px, 0.55vmin, 8px);
  flex-wrap: nowrap;
  width: 100%;
  min-width: 0;
  overflow: hidden;
}

.score-top-main {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: clamp(4px, 0.55vmin, 8px);
  flex: 1;
  min-width: 0;
  overflow: hidden;
}

.score-top-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: clamp(4px, 0.45vmin, 8px);
  flex-shrink: 0;
}

.game-pill,
.rule-pill,
.target-pill,
.set-pill {
  padding: clamp(4px, 0.55vmin, 8px) clamp(8px, 1vmin, 12px);
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: var(--small-text);
  white-space: nowrap;
}

.game-pill {
  color: #ffb347;
}

.score-main {
  align-items: stretch;
  margin-top: clamp(8px, 0.9vmin, 12px);
  gap: clamp(8px, 1vmin, 14px);
  min-height: 0;
}

.score-side {
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: clamp(96px, 19vmin, 180px);
  padding: clamp(6px, 0.75vmin, 10px);
  box-sizing: border-box;
  border-radius: clamp(14px, 1.8vmin, 24px);
  background: rgba(255, 255, 255, 0.06);
  border: 2px solid rgba(255, 140, 0, 0.26);
  overflow: hidden;
}

.score-side.right {
  border-color: rgba(82, 196, 26, 0.26);
}

.score-name {
  width: 100%;
  text-align: center;
  font-size: var(--score-name-text);
  font-weight: 700;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.score-value {
  font-size: var(--score-value-text);
  line-height: 1;
  font-weight: 800;
  margin-top: clamp(4px, 0.5vmin, 8px);
}

.serve-flag {
  margin-top: clamp(2px, 0.35vmin, 6px);
  color: #ffb347;
  font-size: clamp(12px, 1.45vmin, 18px);
  font-weight: 700;
  white-space: nowrap;
}

.score-center {
  width: var(--score-center-width);
  flex: 0 0 var(--score-center-width);
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: clamp(6px, 0.7vmin, 10px);
}

.set-score {
  text-align: center;
  font-size: clamp(24px, 3.8vmin, 46px);
  font-weight: 800;
  color: #ffffff;
  white-space: nowrap;
}

.action-list {
  flex-direction: column;
  gap: clamp(6px, 0.7vmin, 10px);
}

.action-btn,
.settlement-btn {
  border: none;
  border-radius: clamp(10px, 1.2vmin, 14px);
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: clamp(11px, 1.2vmin, 16px);
}

.action-btn::after,
.settlement-btn::after {
  border: none;
}

.action-btn {
  height: var(--action-height);
  line-height: var(--action-height);
  white-space: nowrap;
}

.top-action-btn {
  min-width: clamp(48px, 5.8vmin, 74px);
  padding: 0 clamp(8px, 0.8vmin, 12px);
}

.pause-action-btn {
  width: 100%;
}

.action-btn.danger {
  color: #ff7a45;
  border: 1px solid rgba(255, 122, 69, 0.35);
}

.set-strip {
  justify-content: center;
  gap: clamp(4px, 0.5vmin, 8px);
  margin-top: clamp(8px, 0.95vmin, 12px);
  flex-wrap: wrap;
  overflow: hidden;
}

.set-pill {
  display: inline-flex;
  gap: clamp(3px, 0.35vmin, 6px);
}

.captain-confirm-overlay {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  padding: clamp(16px, 2.4vmin, 28px);
  box-sizing: border-box;
  background: rgba(7, 18, 28, 0.82);
  z-index: 60;
}

.captain-confirm-card {
  display: flex;
  flex-direction: column;
  gap: clamp(8px, 0.9vmin, 12px);
  width: min(100%, 980px);
  max-height: calc(100vh - clamp(32px, 4.8vmin, 56px));
  padding: clamp(16px, 2vmin, 24px);
  box-sizing: border-box;
  background: rgba(12, 28, 44, 0.96);
  border-radius: var(--panel-radius);
  border: 1px solid rgba(46, 196, 182, 0.4);
  overflow: hidden;
}

.captain-confirm-title {
  font-size: clamp(14px, 1.7vmin, 22px);
  font-weight: 800;
  color: #2ec4b6;
}

.captain-confirm-tip {
  color: rgba(255, 255, 255, 0.72);
  font-size: var(--small-text);
}

.captain-confirm-list {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: clamp(6px, 0.8vmin, 10px);
  overflow: auto;
  padding-right: 2px;
}

.captain-option-btn,
.captain-confirm-btn {
  border: none;
  border-radius: clamp(10px, 1.2vmin, 14px);
}

.captain-option-btn::after,
.captain-confirm-btn::after {
  border: none;
}

.captain-option-btn {
  height: clamp(64px, 8vmin, 82px);
  width: 100%;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: clamp(11px, 1.2vmin, 15px);
  padding: clamp(8px, 1vmin, 12px) clamp(6px, 0.8vmin, 10px);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(4px, 0.45vmin, 6px);
}

.captain-option-btn.active {
  background: rgba(46, 196, 182, 0.18);
  color: #2ec4b6;
  box-shadow: inset 0 0 0 1px rgba(46, 196, 182, 0.42);
}

.captain-option-pos,
.captain-option-member {
  display: block;
  line-height: 1.2;
}

.captain-option-pos {
  font-size: clamp(12px, 1.3vmin, 16px);
  font-weight: 800;
}

.captain-option-member {
  font-size: clamp(11px, 1.1vmin, 14px);
  color: rgba(255, 255, 255, 0.82);
}

.captain-confirm-btn {
  align-self: stretch;
  min-width: 0;
  height: clamp(40px, 4.8vmin, 54px);
  line-height: clamp(40px, 4.8vmin, 54px);
  background: #2ec4b6;
  color: #0d1823;
  font-size: clamp(12px, 1.25vmin, 16px);
  font-weight: 800;
  flex-shrink: 0;
}

.court-card {
  flex: 1;
  min-height: 0;
  padding: clamp(10px, 1.1vmin, 16px);
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.court-header {
  align-items: center;
  justify-content: center;
  gap: clamp(4px, 0.5vmin, 8px);
  min-width: 0;
  flex-shrink: 0;
  overflow: hidden;
}

.court-title {
  font-size: clamp(13px, 1.55vmin, 20px);
  font-weight: 700;
  white-space: nowrap;
}

.court-tip {
  color: rgba(255, 255, 255, 0.58);
  font-size: var(--small-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.court-board {
  flex: 1;
  min-height: 0;
  margin-top: clamp(8px, 0.95vmin, 12px);
  align-items: stretch;
  gap: var(--court-gap);
  overflow: hidden;
}

.court-half {
  flex: 1;
  min-width: 0;
  min-height: 0;
  padding: clamp(8px, 0.9vmin, 12px);
  border-radius: clamp(12px, 1.5vmin, 18px);
  background: rgba(255, 255, 255, 0.05);
  overflow: hidden;
}

.court-net {
  width: clamp(3px, 0.35vmin, 5px);
  flex: 0 0 clamp(3px, 0.35vmin, 5px);
  align-self: stretch;
  margin-top: clamp(-6px, -0.5vmin, -4px);
  margin-bottom: clamp(-6px, -0.5vmin, -4px);
  background: rgba(255, 255, 255, 0.3);
}

.court-grid {
  width: 100%;
  height: 100%;
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: var(--court-gap);
  min-height: 0;
}

.court-slot {
  border-radius: var(--soft-radius);
  background: rgba(255, 255, 255, 0.08);
  border: 1px solid rgba(255, 140, 0, 0.18);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 0;
  padding: clamp(6px, 0.65vmin, 10px);
  box-sizing: border-box;
  overflow: hidden;
}

.court-slot.captain-active {
  border-color: rgba(46, 196, 182, 0.54);
  box-shadow: inset 0 0 0 1px rgba(46, 196, 182, 0.22);
}

.slot-pos {
  color: rgba(255, 255, 255, 0.45);
  font-size: var(--court-label-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.slot-no {
  margin-top: clamp(4px, 0.45vmin, 6px);
  font-size: var(--court-number-text);
  font-weight: 800;
  color: #ffffff;
  white-space: nowrap;
}

.slot-no.libero {
  color: #ffb347;
}

.slot-no.captain {
  color: #2ec4b6;
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
  width: clamp(320px, 52vmin, 560px);
  padding: clamp(16px, 2vmin, 28px);
  box-sizing: border-box;
  text-align: center;
}

.settlement-title {
  display: block;
  font-size: clamp(16px, 2.1vmin, 28px);
  font-weight: 800;
}

.settlement-winner {
  display: block;
  margin-top: clamp(8px, 0.9vmin, 12px);
  color: #ffb347;
  font-size: clamp(13px, 1.5vmin, 20px);
}

.settlement-score {
  display: block;
  margin-top: clamp(10px, 1.2vmin, 16px);
  font-size: clamp(34px, 5.8vmin, 76px);
  font-weight: 800;
  line-height: 1;
}

.settlement-games {
  display: block;
  margin-top: clamp(8px, 0.9vmin, 12px);
  color: rgba(255, 255, 255, 0.76);
  font-size: clamp(12px, 1.35vmin, 18px);
}

.settlement-actions {
  gap: clamp(8px, 0.9vmin, 12px);
  margin-top: clamp(14px, 1.6vmin, 22px);
}

.settlement-btn {
  flex: 1;
  height: clamp(40px, 5.5vmin, 64px);
  line-height: clamp(40px, 5.5vmin, 64px);
  background: #ff8c00;
  color: #13202d;
  font-size: clamp(13px, 1.45vmin, 18px);
  font-weight: 700;
}

.settlement-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

@media (max-width: 1400px) {
  .scoreboard-page {
    --roster-width: clamp(130px, 14vmin, 190px);
    --score-center-width: clamp(92px, 12vmin, 150px);
    --score-value-text: clamp(34px, 6.4vmin, 80px);
  }
}

@media (max-width: 1100px) {
  .scoreboard-page {
    --page-pad: clamp(8px, 1vmin, 14px);
    --panel-gap: clamp(6px, 0.8vmin, 10px);
    --roster-width: 130px;
    --title-text: clamp(14px, 1.8vmin, 20px);
    --score-center-width: 96px;
    --score-value-text: clamp(32px, 5.8vmin, 68px);
    --court-number-text: clamp(20px, 2.6vmin, 30px);
  }
}
</style>





