<template>
  <view class="page" :class="pageClassNames">
    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载排球轮次填写...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadMatch">重新加载</button>
    </view>

    <view class="lineup-page" v-else>
      <view v-if="setupPage === 'main'" class="setup-page">
        <view class="page-topbar">
          <view class="page-back" @click="handlePageBack">返回</view>
          <view class="page-topbar-spacer"></view>
        </view>

        <view class="lineup-header">
          <text class="lineup-subtitle">第 {{ currentGameNo }} 局</text>
          <text class="lineup-title">轮次填写</text>
        </view>

        <view class="setup-section">
          <text class="setup-label">选择首发发球方</text>
          <view class="serve-options">
            <view class="serve-option" :class="{ active: displayDraftServeSide === 'left' }" @click="draftServeSide = toActualSide('left')">
              {{ leftDisplayTeamName }}
            </view>
            <view class="serve-option" :class="{ active: displayDraftServeSide === 'right' }" @click="draftServeSide = toActualSide('right')">
              {{ rightDisplayTeamName }}
            </view>
          </view>
        </view>

        <view class="setup-section team-entry-list">
          <view class="team-entry-btn" @click="openLineupEditor('left')">
            <text class="team-entry-name">{{ leftDisplayTeamName }}轮次</text>
            <text class="team-entry-meta">{{ teamDraftCount('left') }}/6</text>
          </view>
          <view class="team-entry-btn" @click="openLineupEditor('right')">
            <text class="team-entry-name">{{ rightDisplayTeamName }}轮次</text>
            <text class="team-entry-meta">{{ teamDraftCount('right') }}/6</text>
          </view>
        </view>

        <view class="lineup-footer">
          <button class="confirm-btn" @click="confirmLineup">开始比赛</button>
        </view>
      </view>

      <view v-else class="setup-page setup-editor-page">
        <view class="editor-topbar" :class="{ dimmed: isSelectingMiddlePair }">
          <view class="editor-back" @click="backToSetupHome">返回</view>
          <text class="lineup-title">{{ currentEditorDisplayTeamName }}轮次填写</text>
          <view class="editor-topbar-spacer"></view>
        </view>

        <view class="editor-body">
          <view class="editor-main-panel">
          <view class="draft-area" :class="{ focus: isSelectingMiddlePair }">
            <view class="draft-slots editor-slots" :class="{ pulsing: isSelectingMiddlePair }">
              <view
                class="draft-slot"
                :class="{
                  active: canEditCurrentLineup && draftActive.side === setupPage && draftActive.index === index,
                  selected: isMiddlePairSlot(index),
                }"
                v-for="(memberId, index) in currentEditorCourt"
                :key="setupPage + '_draft_' + index"
                @click="handleDraftSlotClick(index)"
              >
                <text class="draft-pos">{{ slotLabel(index) }}</text>
                <text class="draft-no">{{ memberNameText(setupPage, memberId) }}</text>
              </view>
            </view>

            <view class="libero-entry-row">
              <button class="libero-entry-btn" @click="startLiberoSetup">
                {{ hasCurrentEditorLiberoSetup ? '重新设置自由人' : '开始添加自由人' }}
              </button>
            </view>

            <view class="libero-focus-panel" v-if="isSelectingMiddlePair">
              <text class="libero-focus-text">请确认副攻位置</text>
              <button class="focus-confirm-btn" :disabled="!pendingMiddlePairIndexes.length" @click="confirmMiddlePair">确定</button>
            </view>

            <view class="libero-binding-card" v-else-if="showLiberoBindingPanel">
              <view class="libero-binding-head">
                <text class="libero-binding-title">自由人绑定</text>
                <text class="libero-binding-link" @click="resetCurrentEditorLiberoSetup">修改首发</text>
              </view>
              <view class="libero-binding-row" v-for="item in currentEditorMiddleBlockers" :key="item.liberoKey">
                <view class="libero-binding-main">
                  <view>
                    <text class="libero-binding-name">副攻{{ item.orderNo }}</text>
                    <text class="libero-binding-player">{{ item.jerseyNumber }}号 {{ item.name }}</text>
                  </view>
                  <view
                    class="libero-slot"
                    :class="{ active: activeLiberoKey === item.liberoKey }"
                    @click="activateLiberoSlot(item.liberoKey)"
                  >
                    <text class="draft-pos">自由人{{ item.orderNo }}</text>
                    <text class="draft-no">{{ liberoMemberText(item.liberoKey) }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>

          <scroll-view class="draft-roster editor-roster" :class="{ dimmed: isSelectingMiddlePair }" scroll-y>
            <view
              v-if="showLiberoBindingPanel"
              class="draft-member libero-empty-option"
              :class="{ chosen: currentEditorLiberoValue(activeLiberoKey) === '' }"
              @click="handleRosterMemberClick('')"
            >
              <text>不绑定</text>
              <text>清空当前自由人</text>
            </view>
            <view
              class="draft-member"
              :class="{
                chosen: rosterMemberChosen(member.id),
                active: rosterMemberActive(member.id),
                disabled: !canPickRosterMember,
              }"
              v-for="member in currentEditorRosterMembers"
              :key="setupPage + '_draft_member_' + member.id"
              @click="handleRosterMemberClick(member.id)"
            >
              <text>{{ member.jerseyNumber }}</text>
              <text>{{ member.name }}</text>
            </view>
            <view class="draft-empty" v-if="!currentEditorRosterMembers.length">当前没有可选替补</view>
          </scroll-view>
          </view>
        </view>

        <view class="lineup-footer">
          <button class="confirm-btn" @click="backToSetupHome">完成</button>
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
  buildScoreboardUrl,
  cloneCourt,
  cloneLiberoSetup,
  createEmptyLiberoRuntime,
  createEmptyLiberoSetup,
  createEmptyMatchState,
  formatTeamName,
  loadMatchState,
  normalizeParticipantSide,
  normalizeMatchState,
  normalizeTeam,
  saveMatchState,
  swapMatchStateSides,
} from './match-state'

const SLOT_POSITIONS = [4, 3, 2, 5, 6, 1]
const SLOT_OPPOSITE_MAP = {
  0: 5,
  1: 4,
  2: 3,
  3: 2,
  4: 1,
  5: 0,
}

const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const tournamentId = ref('')
const matchId = ref('')
const info = ref({})
const leftTeam = ref({ name: '主队', members: [] })
const rightTeam = ref({ name: '客队', members: [] })
const currentGameNo = ref(1)
const draftLeftCourt = ref(Array(6).fill(''))
const draftRightCourt = ref(Array(6).fill(''))
const draftLeftLiberoSetup = ref(createEmptyLiberoSetup())
const draftRightLiberoSetup = ref(createEmptyLiberoSetup())
const draftServeSide = ref('left')
const draftActive = ref({ side: 'left', index: 0 })
const setupPage = ref('main')
const editorMode = ref('idle')
const pendingMiddlePairIndexes = ref([])
const activeLiberoKey = ref('libero1Id')
const pageQuery = ref({})
const displaySideSwapped = ref(false)
const screenLeftParticipantSide = ref('left')
const windowWidth = ref(0)
const windowHeight = ref(0)

const leftDisplayTeam = computed(() => leftTeam.value)
const rightDisplayTeam = computed(() => rightTeam.value)
const leftDisplayTeamName = computed(() => formatTeamName(leftDisplayTeam.value.name))
const rightDisplayTeamName = computed(() => formatTeamName(rightDisplayTeam.value.name))
const currentEditorTeam = computed(() => {
  const actualSide = toActualSide(setupPage.value)
  return actualSide === 'right' ? rightTeam.value : leftTeam.value
})
const currentEditorCourt = computed(() => {
  const actualSide = toActualSide(setupPage.value)
  return actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
})
const currentEditorDisplayTeamName = computed(() => formatTeamName(currentEditorTeam.value.name))
const currentEditorLiberoSetup = computed(() => {
  const actualSide = toActualSide(setupPage.value)
  return actualSide === 'right' ? draftRightLiberoSetup.value : draftLeftLiberoSetup.value
})
const displayDraftServeSide = computed(() => draftServeSide.value)
const currentEditorBenchMembers = computed(() => {
  const onCourt = new Set(currentEditorCourt.value.filter(Boolean))
  return (currentEditorTeam.value.members || []).filter((member) => !onCourt.has(member.id))
})
const isSelectingMiddlePair = computed(() => editorMode.value === 'selectPair')
const currentResolvedMiddlePairIndexes = computed(() => cloneLiberoSetup(currentEditorLiberoSetup.value).pairIndexes)
const hasCurrentEditorLiberoSetup = computed(() => currentResolvedMiddlePairIndexes.value.length === 2)
const showLiberoBindingPanel = computed(() => {
  return setupPage.value !== 'main' && !isSelectingMiddlePair.value && hasCurrentEditorLiberoSetup.value
})
const canEditCurrentLineup = computed(() => setupPage.value !== 'main' && editorMode.value === 'idle')
const canPickRosterMember = computed(() => setupPage.value !== 'main' && !isSelectingMiddlePair.value)
const currentEditorRosterMembers = computed(() => {
  return showLiberoBindingPanel.value ? currentEditorBenchMembers.value : currentEditorTeam.value.members || []
})
const currentEditorMiddleBlockers = computed(() => {
  if (currentResolvedMiddlePairIndexes.value.length !== 2) return []
  return currentResolvedMiddlePairIndexes.value
    .map((slotIndex) => {
      const memberId = currentEditorCourt.value[slotIndex] || ''
      const member = memberById(setupPage.value, memberId)
      return {
        slotIndex,
        memberId,
        name: member?.name || '--',
        jerseyNumber: Number(member?.jerseyNumber || 0),
      }
    })
    .sort((a, b) => a.jerseyNumber - b.jerseyNumber)
    .map((item, index) => ({
      ...item,
      orderNo: index + 1,
      liberoKey: index === 0 ? 'libero1Id' : 'libero2Id',
    }))
})
const orientation = computed(() => (windowWidth.value >= windowHeight.value ? 'landscape' : 'portrait'))
const isTablet = computed(() => Math.min(windowWidth.value || 0, windowHeight.value || 0) >= 720)
const sizeBand = computed(() => {
  if (!isTablet.value) return 'phone'
  if (orientation.value === 'portrait') {
    return windowWidth.value <= 820 ? 'pad-portrait-sm' : 'pad-portrait-lg'
  }
  if (windowWidth.value <= 1228) return 'pad-landscape-sm'
  if (windowWidth.value <= 1400) return 'pad-landscape-md'
  return 'pad-landscape-lg'
})
const pageClassNames = computed(() => [
  isTablet.value ? 'is-tablet' : 'is-phone',
  `is-${orientation.value}`,
  sizeBand.value,
])

function toActualSide(side) {
  return side === 'right' ? 'right' : 'left'
}

function toDisplaySide(side) {
  return side === 'right' ? 'right' : 'left'
}

function getParticipantSideByScreenSide(side) {
  const normalizedSide = side === 'right' ? 'right' : 'left'
  if (screenLeftParticipantSide.value === 'right') {
    return normalizedSide === 'right' ? 'left' : 'right'
  }
  return normalizedSide
}

function getScreenSideByParticipantSide(side) {
  const normalizedSide = normalizeParticipantSide(side)
  if (screenLeftParticipantSide.value === 'right') {
    return normalizedSide === 'right' ? 'left' : 'right'
  }
  return normalizedSide
}

function toParticipantLineupState(state) {
  const normalized = normalizeMatchState(state)
  if (normalizeParticipantSide(normalized.screenLeftParticipantSide) === 'left') {
    return normalized
  }
  return swapMatchStateSides({
    ...normalized,
    screenLeftParticipantSide: 'right',
  })
}

function applyWindowMetrics(size = {}) {
  const nextWidth = Number(size.windowWidth || size.width || 0)
  const nextHeight = Number(size.windowHeight || size.height || 0)
  if (nextWidth > 0) {
    windowWidth.value = nextWidth
  }
  if (nextHeight > 0) {
    windowHeight.value = nextHeight
  }
}

function syncWindowMetrics() {
  try {
    if (typeof uni.getWindowInfo === 'function') {
      applyWindowMetrics(uni.getWindowInfo())
      return
    }
    if (typeof uni.getSystemInfoSync === 'function') {
      const info = uni.getSystemInfoSync()
      applyWindowMetrics({
        windowWidth: info.windowWidth,
        windowHeight: info.windowHeight,
      })
    }
  } catch (_) {
    // ignore metric errors
  }
}

function handleWindowResize(res) {
  applyWindowMetrics(res?.size || res || {})
}

function memberMap(side) {
  const actualSide = toActualSide(side)
  const team = actualSide === 'right' ? rightTeam.value : leftTeam.value
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

function memberNameText(side, memberId) {
  const member = memberById(side, memberId)
  return member ? member.name : '--'
}

function liberoMemberText(key) {
  const memberId = currentEditorLiberoValue(key)
  if (!memberId) return '--'
  const member = memberById(setupPage.value, memberId)
  return member ? member.name : '--'
}

function teamDraftCount(side) {
  const actualSide = toActualSide(side)
  const draft = actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
  return draft.filter(Boolean).length
}

function teamDraftComplete(side) {
  return teamDraftCount(side) === 6
}

function draftContains(side, memberId) {
  const actualSide = toActualSide(side)
  const draft = actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
  return draft.includes(memberId)
}

function getLiberoSetup(side) {
  const actualSide = toActualSide(side)
  return actualSide === 'right' ? draftRightLiberoSetup.value : draftLeftLiberoSetup.value
}

function setLiberoSetup(side, setup) {
  const normalized = cloneLiberoSetup(setup)
  const actualSide = toActualSide(side)
  if (actualSide === 'right') {
    draftRightLiberoSetup.value = normalized
  } else {
    draftLeftLiberoSetup.value = normalized
  }
}

function clearLiberoSetup(side) {
  setLiberoSetup(side, createEmptyLiberoSetup())
}

function normalizePairIndexes(pairIndexes) {
  if (!Array.isArray(pairIndexes) || !pairIndexes.length) return []
  const anchor = Number(pairIndexes[0])
  if (!Number.isInteger(anchor) || SLOT_OPPOSITE_MAP[anchor] === undefined) {
    return []
  }
  return [anchor, SLOT_OPPOSITE_MAP[anchor]]
}

function samePair(left, right) {
  return JSON.stringify(normalizePairIndexes(left)) === JSON.stringify(normalizePairIndexes(right))
}

function sanitizeLiberoSetup(side) {
  if (!teamDraftComplete(side)) {
    clearLiberoSetup(side)
    return
  }
  const actualSide = toActualSide(side)
  const draft = actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
  const onCourt = new Set(draft.filter(Boolean))
  const members = actualSide === 'right' ? rightTeam.value.members || [] : leftTeam.value.members || []
  const memberIds = new Set(members.map((member) => member.id))
  const normalized = cloneLiberoSetup(getLiberoSetup(side))
  normalized.pairIndexes = normalizePairIndexes(normalized.pairIndexes)
  if (normalized.pairIndexes.length !== 2) {
    normalized.libero1Id = ''
    normalized.libero2Id = ''
    setLiberoSetup(side, normalized)
    return
  }
  if (normalized.libero1Id && (!memberIds.has(normalized.libero1Id) || onCourt.has(normalized.libero1Id))) {
    normalized.libero1Id = ''
  }
  if (normalized.libero2Id && (!memberIds.has(normalized.libero2Id) || onCourt.has(normalized.libero2Id))) {
    normalized.libero2Id = ''
  }
  setLiberoSetup(side, normalized)
}

function currentEditorLiberoValue(key) {
  return currentEditorLiberoSetup.value?.[key] || ''
}

function activeLiberoKeyExists() {
  return currentEditorMiddleBlockers.value.some((item) => item.liberoKey === activeLiberoKey.value)
}

function ensureActiveLiberoKey() {
  if (activeLiberoKeyExists()) return
  activeLiberoKey.value = currentEditorMiddleBlockers.value[0]?.liberoKey || 'libero1Id'
}

function activateDraftSlot(side, index) {
  draftActive.value = { side, index }
}

function openLineupEditor(side) {
  const actualSide = toActualSide(side)
  const draft = actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
  const firstEmptyIndex = draft.findIndex((item) => !item)
  draftActive.value = { side, index: firstEmptyIndex >= 0 ? firstEmptyIndex : 0 }
  setupPage.value = side
  sanitizeLiberoSetup(side)
  pendingMiddlePairIndexes.value = []
  editorMode.value = getLiberoSetup(side).pairIndexes.length === 2 ? 'bind' : 'idle'
  activeLiberoKey.value = 'libero1Id'
  ensureActiveLiberoKey()
}

function backToSetupHome() {
  setupPage.value = 'main'
  editorMode.value = 'idle'
  pendingMiddlePairIndexes.value = []
  activeLiberoKey.value = 'libero1Id'
}

function handlePageBack() {
  uni.navigateBack({
    delta: 1,
    fail: () => {
      uni.switchTab({
        url: '/pages/index/index',
      })
    },
  })
}

function slotLabel(index) {
  return `${SLOT_POSITIONS[index] || index + 1}号位`
}

function assignDraftMember(side, memberId) {
  const actualSide = toActualSide(side)
  const draft = actualSide === 'right' ? draftRightCourt.value : draftLeftCourt.value
  const activeIndex = draftActive.value.side === side ? draftActive.value.index : draft.findIndex((item) => !item)
  const targetIndex = activeIndex >= 0 ? activeIndex : 0
  const existingIndex = draft.indexOf(memberId)
  if (existingIndex >= 0) {
    draft.splice(existingIndex, 1, '')
  }
  draft.splice(targetIndex, 1, memberId)
  draftActive.value = {
    side,
    index: Math.min(5, targetIndex + 1),
  }
  clearLiberoSetup(side)
  if (setupPage.value === side) {
    editorMode.value = 'idle'
    pendingMiddlePairIndexes.value = []
  }
}

function handleDraftSlotClick(index) {
  if (isSelectingMiddlePair.value) {
    pendingMiddlePairIndexes.value = normalizePairIndexes([index])
    return
  }
  if (!canEditCurrentLineup.value) return
  activateDraftSlot(setupPage.value, index)
}

function handleRosterMemberClick(memberId) {
  if (!canPickRosterMember.value) return
  if (showLiberoBindingPanel.value) {
    ensureActiveLiberoKey()
    assignLibero(activeLiberoKey.value, memberId)
    return
  }
  assignDraftMember(setupPage.value, memberId)
}

function isMiddlePairSlot(index) {
  const source = isSelectingMiddlePair.value ? pendingMiddlePairIndexes.value : currentResolvedMiddlePairIndexes.value
  return source.includes(index)
}

function activateLiberoSlot(key) {
  activeLiberoKey.value = key
}

function rosterMemberChosen(memberId) {
  if (showLiberoBindingPanel.value) {
    return currentEditorMiddleBlockers.value.some((item) => currentEditorLiberoValue(item.liberoKey) === memberId)
  }
  return canEditCurrentLineup.value && draftContains(setupPage.value, memberId)
}

function rosterMemberActive(memberId) {
  if (!showLiberoBindingPanel.value) return false
  return currentEditorLiberoValue(activeLiberoKey.value) === memberId
}

function startLiberoSetup() {
  if (!teamDraftComplete(setupPage.value)) {
    uni.showToast({ title: '请先将以上六个位置填写完整', icon: 'none' })
    return
  }
  sanitizeLiberoSetup(setupPage.value)
  pendingMiddlePairIndexes.value = currentResolvedMiddlePairIndexes.value.slice(0, 2)
  editorMode.value = 'selectPair'
}

function confirmMiddlePair() {
  const pairIndexes = normalizePairIndexes(pendingMiddlePairIndexes.value)
  if (pairIndexes.length !== 2) return
  const current = cloneLiberoSetup(getLiberoSetup(setupPage.value))
  setLiberoSetup(setupPage.value, {
    pairIndexes,
    libero1Id: samePair(current.pairIndexes, pairIndexes) ? current.libero1Id : '',
    libero2Id: samePair(current.pairIndexes, pairIndexes) ? current.libero2Id : '',
  })
  sanitizeLiberoSetup(setupPage.value)
  editorMode.value = 'bind'
  pendingMiddlePairIndexes.value = []
  activeLiberoKey.value = currentEditorMiddleBlockers.value[0]?.liberoKey || 'libero1Id'
}

function assignLibero(key, memberId) {
  const next = cloneLiberoSetup(getLiberoSetup(setupPage.value))
  next[key] = memberId || ''
  setLiberoSetup(setupPage.value, next)
  sanitizeLiberoSetup(setupPage.value)
  ensureActiveLiberoKey()
}

function resetCurrentEditorLiberoSetup() {
  clearLiberoSetup(setupPage.value)
  editorMode.value = 'idle'
  pendingMiddlePairIndexes.value = []
  activeLiberoKey.value = 'libero1Id'
}

function applyDraftFromState(state) {
  const normalized = normalizeMatchState(state)
  displaySideSwapped.value = false
  screenLeftParticipantSide.value = normalizeParticipantSide(normalized.screenLeftParticipantSide)
  currentGameNo.value = Number(normalized.currentGameNo || 1)
  const leftDraft = normalized.draftLeftCourt?.some(Boolean) ? normalized.draftLeftCourt : normalized.baseLeftCourt
  const rightDraft = normalized.draftRightCourt?.some(Boolean) ? normalized.draftRightCourt : normalized.baseRightCourt
  draftLeftCourt.value = cloneCourt(leftDraft)
  draftRightCourt.value = cloneCourt(rightDraft)
  draftLeftLiberoSetup.value = cloneLiberoSetup(normalized.leftLiberoSetup)
  draftRightLiberoSetup.value = cloneLiberoSetup(normalized.rightLiberoSetup)
  draftServeSide.value = normalized.draftServeSide === 'right' ? 'right' : 'left'
  draftActive.value = { side: 'left', index: 0 }
  setupPage.value = 'main'
  editorMode.value = 'idle'
  pendingMiddlePairIndexes.value = []
  activeLiberoKey.value = 'libero1Id'
  sanitizeLiberoSetup('left')
  sanitizeLiberoSetup('right')
}

function buildBaseState() {
  const cached = loadMatchState(matchId.value)
  return cached ? normalizeMatchState(cached) : createEmptyMatchState()
}

function goToScoreboard() {
  uni.redirectTo({
    url: buildScoreboardUrl(pageQuery.value),
  })
}

function normalizeLineupServeSide(side) {
  return side === 'right' ? 'right' : 'left'
}

function buildLiberoSetupFromConfig(teamConfig) {
  return {
    pairIndexes: Array.isArray(teamConfig?.middlePairIndexes)
      ? teamConfig.middlePairIndexes
          .slice(0, 2)
          .map((item) => Number(item))
          .filter((item) => Number.isInteger(item) && item >= 0 && item < 6)
      : [],
    libero1Id: teamConfig?.libero1Id || '',
    libero2Id: teamConfig?.libero2Id || '',
  }
}

function hasLocalLineupDraft(state, requestedGameNo) {
  if (!state || state.lineupReady) return false
  if (Number(state.currentGameNo || 1) !== Number(requestedGameNo || 1)) return false
  return (
    state.draftLeftCourt?.some(Boolean) ||
    state.draftRightCourt?.some(Boolean) ||
    state.leftLiberoSetup?.pairIndexes?.length ||
    state.rightLiberoSetup?.pairIndexes?.length ||
    state.leftLiberoSetup?.libero1Id ||
    state.leftLiberoSetup?.libero2Id ||
    state.rightLiberoSetup?.libero1Id ||
    state.rightLiberoSetup?.libero2Id
  )
}

function buildStateFromLineupConfig(cached, lineupResponse, requestedGameNo) {
  const state = createEmptyMatchState()
  if (cached) {
    Object.assign(state, normalizeMatchState(cached))
  }

  const config = lineupResponse?.config || {}
  const remoteServeSide = normalizeLineupServeSide(config.serveSide)
  const remoteLeftCourt = cloneCourt(config.left?.court)
  const remoteRightCourt = cloneCourt(config.right?.court)
  const remoteLeftLiberoSetup = cloneLiberoSetup(buildLiberoSetupFromConfig(config.left))
  const remoteRightLiberoSetup = cloneLiberoSetup(buildLiberoSetupFromConfig(config.right))
  const keepLocalDraft = hasLocalLineupDraft(cached, requestedGameNo)
  const remoteScreenLeftCourt = getScreenSideByParticipantSide('left') === 'left' ? remoteLeftCourt : remoteRightCourt
  const remoteScreenRightCourt = getScreenSideByParticipantSide('right') === 'right' ? remoteRightCourt : remoteLeftCourt
  const remoteScreenLeftLiberoSetup = getScreenSideByParticipantSide('left') === 'left' ? remoteLeftLiberoSetup : remoteRightLiberoSetup
  const remoteScreenRightLiberoSetup = getScreenSideByParticipantSide('right') === 'right' ? remoteRightLiberoSetup : remoteLeftLiberoSetup
  const remoteScreenServeSide = getScreenSideByParticipantSide(remoteServeSide)

  state.currentGameNo = Number(requestedGameNo || 1)
  state.baseLeftCourt = cloneCourt(remoteScreenLeftCourt)
  state.baseRightCourt = cloneCourt(remoteScreenRightCourt)
  state.leftLiberoSetup = keepLocalDraft ? cloneLiberoSetup(cached.leftLiberoSetup) : cloneLiberoSetup(remoteScreenLeftLiberoSetup)
  state.rightLiberoSetup = keepLocalDraft ? cloneLiberoSetup(cached.rightLiberoSetup) : cloneLiberoSetup(remoteScreenRightLiberoSetup)
  state.draftLeftCourt = keepLocalDraft ? cloneCourt(cached.draftLeftCourt) : cloneCourt(remoteScreenLeftCourt)
  state.draftRightCourt = keepLocalDraft ? cloneCourt(cached.draftRightCourt) : cloneCourt(remoteScreenRightCourt)
  state.draftServeSide = keepLocalDraft ? normalizeLineupServeSide(cached.draftServeSide) : remoteScreenServeSide
  state.currentGameStartServeSide = remoteScreenServeSide
  state.serveSide = remoteScreenServeSide
  state.lineupReady = false
  state.finalGameSideSwitchPending = false
  state.finalGameSideSwitchHandled = false
  return state
}

function buildLineupPayload() {
  const participantState = toParticipantLineupState({
    screenLeftParticipantSide: screenLeftParticipantSide.value,
    draftLeftCourt: cloneCourt(draftLeftCourt.value),
    draftRightCourt: cloneCourt(draftRightCourt.value),
    leftLiberoSetup: cloneLiberoSetup(draftLeftLiberoSetup.value),
    rightLiberoSetup: cloneLiberoSetup(draftRightLiberoSetup.value),
    draftServeSide: draftServeSide.value,
  })
  return {
    gameNo: Number(currentGameNo.value || 1),
    serveSide: normalizeLineupServeSide(participantState.draftServeSide),
    left: {
      court: cloneCourt(participantState.draftLeftCourt),
      middlePairIndexes: [...(participantState.leftLiberoSetup?.pairIndexes || [])],
      libero1Id: participantState.leftLiberoSetup?.libero1Id || '',
      libero2Id: participantState.leftLiberoSetup?.libero2Id || '',
    },
    right: {
      court: cloneCourt(participantState.draftRightCourt),
      middlePairIndexes: [...(participantState.rightLiberoSetup?.pairIndexes || [])],
      libero1Id: participantState.rightLiberoSetup?.libero1Id || '',
      libero2Id: participantState.rightLiberoSetup?.libero2Id || '',
    },
  }
}

async function confirmLineup() {
  if (draftLeftCourt.value.some((item) => !item) || draftRightCourt.value.some((item) => !item)) {
    uni.showToast({ title: '请先补齐双方首发站位', icon: 'none' })
    return
  }

  sanitizeLiberoSetup('left')
  sanitizeLiberoSetup('right')
  uni.showLoading({ title: '淇濆瓨涓?..', mask: true })
  try {
    await request('/api/v1/matches/' + matchId.value + '/lineup-config', {
      method: 'PUT',
      data: buildLineupPayload(),
    })
  } catch (_) {
    uni.hideLoading()
    return
  }
  const state = buildBaseState()
  state.displaySideSwapped = false
  state.screenLeftParticipantSide = screenLeftParticipantSide.value
  state.currentGameNo = Number(currentGameNo.value || 1)
  state.draftLeftCourt = cloneCourt(draftLeftCourt.value)
  state.draftRightCourt = cloneCourt(draftRightCourt.value)
  state.leftCourt = cloneCourt(draftLeftCourt.value)
  state.rightCourt = cloneCourt(draftRightCourt.value)
  state.baseLeftCourt = cloneCourt(draftLeftCourt.value)
  state.baseRightCourt = cloneCourt(draftRightCourt.value)
  state.leftLiberoSetup = cloneLiberoSetup(draftLeftLiberoSetup.value)
  state.rightLiberoSetup = cloneLiberoSetup(draftRightLiberoSetup.value)
  state.leftLiberoRuntime = createEmptyLiberoRuntime()
  state.rightLiberoRuntime = createEmptyLiberoRuntime()
  state.draftServeSide = draftServeSide.value
  state.currentGameStartServeSide = draftServeSide.value
  state.serveSide = draftServeSide.value
  state.lineupReady = true
  state.finalGameSideSwitchPending = false
  state.finalGameSideSwitchHandled = false
  saveMatchState(matchId.value, state)
  uni.hideLoading()
  goToScoreboard()
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

    const participantLeftTeam = participantMap.get(match.leftPlayerId) || { name: '主队', members: [] }
    const participantRightTeam = participantMap.get(match.rightPlayerId) || { name: '客队', members: [] }

    if (participantLeftTeam.members.length < 6 || participantRightTeam.members.length < 6) {
      throw new Error('双方队伍都至少需要 6 名队员')
    }

    const cached = loadMatchState(matchId.value)
    displaySideSwapped.value = false
    screenLeftParticipantSide.value = normalizeParticipantSide(cached?.screenLeftParticipantSide)
    leftTeam.value = screenLeftParticipantSide.value === 'right' ? participantRightTeam : participantLeftTeam
    rightTeam.value = screenLeftParticipantSide.value === 'right' ? participantLeftTeam : participantRightTeam
    if (cached?.lineupReady && !cached.matchEnded) {
      goToScoreboard()
      return
    }

    const requestedGameNo = Number(cached?.currentGameNo || 1)
    const lineupResponse = await request(
      '/api/v1/matches/' + matchId.value + '/lineup-config?gameNo=' + requestedGameNo,
      { method: 'GET' }
    )
    applyDraftFromState(buildStateFromLineupConfig(cached, lineupResponse, requestedGameNo))
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载排球轮次填写失败'
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
  syncWindowMetrics()
  if (typeof uni.onWindowResize === 'function') {
    uni.onWindowResize(handleWindowResize)
  }
  loadMatch()
})

onUnmounted(() => {
  if (typeof uni.offWindowResize === 'function') {
    uni.offWindowResize(handleWindowResize)
  }
})

onBackPress(() => {
  if (setupPage.value !== 'main') {
    backToSetupHome()
    return true
  }
  return false
})
</script>

<style scoped>
.page {
  --pad-page-max-width: 100%;
  --pad-gap: 24rpx;
  min-height: 100vh;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
  color: #ffffff;
}

.page.is-tablet {
  --pad-gap: clamp(14px, 1.6vmin, 24px);
}

.state-layer {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  padding: 40rpx;
  box-sizing: border-box;
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

.lineup-page {
  min-height: 100vh;
  padding: 32rpx 24rpx 36rpx;
  box-sizing: border-box;
}

.page.is-tablet .lineup-page {
  min-height: 100vh;
  padding: clamp(18px, 2.2vmin, 32px);
}

.setup-page {
  min-height: calc(100vh - 68rpx);
  display: flex;
  flex-direction: column;
}

.page-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 44rpx;
}

.page-back {
  min-width: 88rpx;
  font-size: 24rpx;
  color: #ffb347;
}

.page-topbar-spacer {
  min-width: 88rpx;
}

.page.is-tablet .page-topbar,
.page.is-tablet .editor-topbar {
  min-height: clamp(32px, 4vmin, 44px);
}

.page.is-tablet .page-back,
.page.is-tablet .editor-back {
  min-width: clamp(64px, 7vmin, 88px);
  font-size: clamp(17px, 1.8vmin, 24px);
}

.lineup-header {
  text-align: center;
  margin-top: 8rpx;
}

.page.is-tablet .lineup-header {
  margin-bottom: clamp(8px, 1.2vmin, 16px);
}

.lineup-subtitle {
  display: block;
  color: rgba(255, 255, 255, 0.65);
  font-size: 24rpx;
}

.lineup-title {
  display: block;
  margin-top: 12rpx;
  font-size: 42rpx;
  font-weight: 800;
}

.page.is-tablet .lineup-title {
  font-size: clamp(30px, 3vmin, 40px);
}

.page.is-tablet .lineup-subtitle {
  font-size: clamp(15px, 1.5vmin, 20px);
}

.setup-section {
  margin-top: 32rpx;
}

.page.is-tablet .setup-section {
  margin-top: clamp(16px, 1.8vmin, 26px);
}

.setup-label {
  display: block;
  text-align: center;
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 700;
}

.page.is-tablet .setup-label {
  font-size: clamp(15px, 1.5vmin, 19px);
}

.serve-options {
  display: flex;
  justify-content: center;
  gap: 10rpx;
  margin-top: 16rpx;
}

.page.is-tablet .serve-options {
  gap: clamp(12px, 1.4vmin, 18px);
}

.serve-option {
  min-width: 180rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  white-space: nowrap;
}

.page.is-tablet .serve-option {
  min-width: clamp(180px, 24vw, 280px);
  height: clamp(56px, 6vmin, 74px);
  line-height: clamp(56px, 6vmin, 74px);
  border-radius: clamp(14px, 1.6vmin, 20px);
  font-size: clamp(16px, 1.6vmin, 20px);
}

.serve-option.active {
  background: #ff8c00;
  color: #13202d;
  font-weight: 700;
}

.team-entry-list {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 20rpx;
}

.page.is-tablet .team-entry-list {
  flex-direction: row;
  align-items: stretch;
  gap: clamp(14px, 1.6vmin, 24px);
}

.team-entry-btn {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 28rpx;
  height: 96rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.07);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
}

.page.is-tablet .team-entry-btn {
  flex: 1;
  min-height: clamp(120px, 18vmin, 180px);
  padding: clamp(18px, 2.2vmin, 28px);
  border-radius: clamp(18px, 1.9vmin, 26px);
  flex-direction: column;
  justify-content: center;
  align-items: flex-start;
  gap: clamp(10px, 1.2vmin, 16px);
}

.team-entry-name {
  font-size: 30rpx;
  font-weight: 800;
  white-space: nowrap;
}

.page.is-tablet .team-entry-name {
  font-size: clamp(20px, 2.1vmin, 28px);
}

.team-entry-meta {
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

.page.is-tablet .team-entry-meta {
  font-size: clamp(16px, 1.6vmin, 20px);
}

.lineup-footer {
  padding-top: 24rpx;
}

.page.is-tablet .lineup-footer {
  padding-top: clamp(16px, 1.8vmin, 24px);
}

.confirm-btn {
  width: 100%;
  height: 72rpx;
  line-height: 72rpx;
  border: none;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 26rpx;
  font-weight: 800;
}

.page.is-tablet .confirm-btn {
  height: clamp(56px, 6vmin, 74px);
  line-height: clamp(56px, 6vmin, 74px);
  border-radius: clamp(16px, 1.7vmin, 22px);
  font-size: clamp(18px, 1.7vmin, 22px);
}

.confirm-btn::after {
  border: none;
}

.setup-editor-page {
  min-height: calc(100vh - 68rpx);
}

.editor-topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  transition: opacity 0.2s ease;
}

.editor-topbar.dimmed {
  opacity: 0.18;
}

.editor-back {
  min-width: 88rpx;
  font-size: 24rpx;
  color: #ffb347;
}

.editor-topbar-spacer {
  min-width: 88rpx;
}

.editor-body {
  flex: 1;
  min-height: 0;
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.editor-main-panel {
  display: contents;
}

.page.is-tablet.is-portrait .editor-body {
  flex-direction: row;
  align-items: stretch;
  gap: clamp(14px, 1.6vmin, 24px);
}

.page.is-tablet.is-portrait .editor-main-panel {
  display: flex;
  flex: 1;
  min-width: 0;
  min-height: 0;
  gap: clamp(14px, 1.6vmin, 24px);
}

.draft-area {
  position: relative;
}

.page.is-tablet.is-portrait .draft-area {
  flex: 1.15;
  min-width: 0;
  min-height: 0;
  padding: clamp(16px, 1.9vmin, 24px);
  border-radius: clamp(18px, 1.8vmin, 24px);
  background: rgba(255, 255, 255, 0.05);
  border: 1px solid rgba(255, 255, 255, 0.1);
}

.draft-area.focus {
  z-index: 2;
}

.draft-slots {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
  width: 100%;
}

.page.is-tablet .draft-slots {
  gap: clamp(10px, 1.1vmin, 16px);
}

.draft-slots.pulsing .draft-slot {
  animation: slotPulse 1.8s ease-in-out infinite;
}

.draft-slot {
  width: auto;
  min-height: 88rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  transition: background 0.2s ease, border-color 0.2s ease, transform 0.2s ease;
}

.page.is-tablet .draft-slot {
  min-height: clamp(92px, 9.5vmin, 126px);
  border-radius: clamp(14px, 1.5vmin, 18px);
}

.draft-slot.active {
  border-color: rgba(255, 140, 0, 0.5);
  background: rgba(255, 140, 0, 0.14);
}

.draft-slot.selected {
  border-color: rgba(255, 140, 0, 0.76);
  background: rgba(255, 140, 0, 0.22);
}

.draft-pos {
  color: rgba(255, 255, 255, 0.5);
  font-size: 20rpx;
  white-space: nowrap;
}

.page.is-tablet .draft-pos {
  font-size: clamp(13px, 1.3vmin, 16px);
}

.draft-no {
  margin-top: 6rpx;
  width: 100%;
  padding: 0 8rpx;
  box-sizing: border-box;
  text-align: center;
  font-size: 28rpx;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.page.is-tablet .draft-no {
  font-size: clamp(18px, 1.9vmin, 24px);
}

.libero-entry-row {
  margin-top: 18rpx;
}

.page.is-tablet .libero-entry-row {
  margin-top: clamp(14px, 1.5vmin, 20px);
}

.libero-entry-btn,
.focus-confirm-btn {
  width: 100%;
  height: 68rpx;
  line-height: 68rpx;
  border: none;
  border-radius: 16rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.page.is-tablet .libero-entry-btn,
.page.is-tablet .focus-confirm-btn {
  height: clamp(52px, 5.6vmin, 68px);
  line-height: clamp(52px, 5.6vmin, 68px);
  border-radius: clamp(14px, 1.5vmin, 18px);
  font-size: clamp(16px, 1.5vmin, 20px);
}

.libero-entry-btn::after,
.focus-confirm-btn::after {
  border: none;
}

.focus-confirm-btn[disabled] {
  opacity: 0.45;
}

.libero-focus-panel {
  margin-top: 18rpx;
  padding: 22rpx 20rpx 0;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 140, 0, 0.24);
}

.page.is-tablet .libero-focus-panel,
.page.is-tablet .libero-binding-card {
  margin-top: clamp(14px, 1.5vmin, 20px);
  padding: clamp(16px, 1.8vmin, 22px);
  border-radius: clamp(16px, 1.6vmin, 20px);
}

.libero-focus-text {
  display: block;
  text-align: center;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.libero-binding-card {
  margin-top: 18rpx;
  padding: 20rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 140, 0, 0.22);
}

.libero-binding-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.libero-binding-title {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.libero-binding-link {
  color: #ffb347;
  font-size: 22rpx;
}

.libero-binding-row {
  margin-top: 18rpx;
}

.libero-binding-main {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
}

.libero-binding-name {
  display: block;
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
  font-weight: 700;
}

.libero-binding-player {
  display: block;
  margin-top: 6rpx;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.libero-slot {
  width: 168rpx;
  min-height: 88rpx;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.page.is-tablet .libero-slot {
  width: clamp(128px, 12vw, 180px);
  min-height: clamp(86px, 8vmin, 112px);
}

.libero-slot.active {
  border-color: rgba(255, 140, 0, 0.76);
  background: rgba(255, 140, 0, 0.2);
}

.editor-roster {
  flex: 1;
  min-height: 0;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.04);
  padding: 8rpx;
  box-sizing: border-box;
  transition: opacity 0.2s ease;
}

.page.is-tablet.is-portrait .editor-roster {
  flex: 0.85;
  min-width: 0;
  min-height: 0;
  padding: clamp(10px, 1.2vmin, 16px);
  border-radius: clamp(16px, 1.6vmin, 20px);
  background: rgba(255, 255, 255, 0.05);
}

.editor-roster.dimmed {
  opacity: 0.18;
  pointer-events: none;
}

.draft-member {
  display: flex;
  justify-content: space-between;
  gap: 8rpx;
  padding: 12rpx 14rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.05);
  margin-bottom: 8rpx;
  font-size: 22rpx;
}

.page.is-tablet .draft-member {
  padding: clamp(12px, 1.25vmin, 16px) clamp(14px, 1.35vmin, 18px);
  margin-bottom: clamp(8px, 0.9vmin, 12px);
  border-radius: clamp(12px, 1.25vmin, 16px);
  font-size: clamp(15px, 1.4vmin, 18px);
}

.draft-member.chosen {
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
}

.draft-member.active {
  border: 1rpx solid rgba(255, 140, 0, 0.5);
}

.draft-member.disabled {
  opacity: 0.72;
}

.libero-empty-option {
  border: 1rpx dashed rgba(255, 255, 255, 0.18);
}

.draft-empty {
  padding: 24rpx 12rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.48);
  font-size: 22rpx;
}

@keyframes slotPulse {
  0%,
  100% {
    transform: scale(1);
    opacity: 0.88;
  }
  50% {
    transform: scale(0.985);
    opacity: 1;
  }
}
</style>
