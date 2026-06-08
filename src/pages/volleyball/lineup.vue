<template>
  <view class="page">
    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载排球轮次填写...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadMatch">重新加载</button>
    </view>

    <view class="lineup-page" v-else>
      <view v-if="setupPage === 'main'" class="setup-page">
        <view class="lineup-header">
          <text class="lineup-subtitle">第 {{ currentGameNo }} 局</text>
          <text class="lineup-title">轮次填写</text>
        </view>

        <view class="setup-section">
          <text class="setup-label">选择首发球方</text>
          <view class="serve-options">
            <view class="serve-option" :class="{ active: draftServeSide === 'left' }" @click="draftServeSide = 'left'">
              {{ leftDisplayTeamName }}
            </view>
            <view class="serve-option" :class="{ active: draftServeSide === 'right' }" @click="draftServeSide = 'right'">
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
        <view class="editor-topbar">
          <view class="editor-back" @click="backToSetupHome">返回</view>
          <text class="lineup-title">{{ currentEditorDisplayTeamName }}轮次填写</text>
          <view class="editor-back editor-done" @click="backToSetupHome">完成</view>
        </view>

        <view class="editor-body">
          <view class="draft-slots editor-slots">
            <view
              class="draft-slot"
              :class="{ active: draftActive.side === setupPage && draftActive.index === index }"
              v-for="(memberId, index) in currentEditorCourt"
              :key="setupPage + '_draft_' + index"
              @click="activateDraftSlot(setupPage, index)"
            >
              <text class="draft-pos">{{ slotLabel(index) }}</text>
              <text class="draft-no">{{ memberNameText(setupPage, memberId) }}</text>
            </view>
          </view>

          <scroll-view class="draft-roster editor-roster" scroll-y>
            <view
              class="draft-member"
              :class="{ chosen: draftContains(setupPage, member.id) }"
              v-for="member in currentEditorTeam.members"
              :key="setupPage + '_draft_member_' + member.id"
              @click="assignDraftMember(setupPage, member.id)"
            >
              <text>{{ member.jerseyNumber }}</text>
              <text>{{ member.name }}</text>
            </view>
          </scroll-view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onBackPress, onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import {
  buildScoreboardUrl,
  cloneCourt,
  createEmptyMatchState,
  formatTeamName,
  loadMatchState,
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
const currentGameNo = ref(1)
const draftLeftCourt = ref(Array(6).fill(''))
const draftRightCourt = ref(Array(6).fill(''))
const draftServeSide = ref('left')
const draftActive = ref({ side: 'left', index: 0 })
const setupPage = ref('main')
const pageQuery = ref({})

const leftDisplayTeamName = computed(() => formatTeamName(leftTeam.value.name))
const rightDisplayTeamName = computed(() => formatTeamName(rightTeam.value.name))
const currentEditorTeam = computed(() => (setupPage.value === 'right' ? rightTeam.value : leftTeam.value))
const currentEditorCourt = computed(() => (setupPage.value === 'right' ? draftRightCourt.value : draftLeftCourt.value))
const currentEditorDisplayTeamName = computed(() => formatTeamName(currentEditorTeam.value.name))

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

function memberNameText(side, memberId) {
  const member = memberById(side, memberId)
  return member ? member.name : '--'
}

function teamDraftCount(side) {
  const draft = side === 'right' ? draftRightCourt.value : draftLeftCourt.value
  return draft.filter(Boolean).length
}

function draftContains(side, memberId) {
  const draft = side === 'right' ? draftRightCourt.value : draftLeftCourt.value
  return draft.includes(memberId)
}

function activateDraftSlot(side, index) {
  draftActive.value = { side, index }
}

function openLineupEditor(side) {
  const draft = side === 'right' ? draftRightCourt.value : draftLeftCourt.value
  const firstEmptyIndex = draft.findIndex((item) => !item)
  draftActive.value = { side, index: firstEmptyIndex >= 0 ? firstEmptyIndex : 0 }
  setupPage.value = side
}

function backToSetupHome() {
  setupPage.value = 'main'
}

function slotLabel(index) {
  const positions = [4, 3, 2, 5, 6, 1]
  return `${positions[index] || index + 1}号位`
}

function assignDraftMember(side, memberId) {
  const draft = side === 'right' ? draftRightCourt.value : draftLeftCourt.value
  const active = draftActive.value.side === side ? draftActive.value.index : draft.findIndex((item) => !item)
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

function applyDraftFromState(state) {
  currentGameNo.value = Number(state.currentGameNo || 1)
  const leftDraft = state.draftLeftCourt?.some(Boolean) ? state.draftLeftCourt : state.baseLeftCourt
  const rightDraft = state.draftRightCourt?.some(Boolean) ? state.draftRightCourt : state.baseRightCourt
  draftLeftCourt.value = cloneCourt(leftDraft)
  draftRightCourt.value = cloneCourt(rightDraft)
  draftServeSide.value = state.draftServeSide === 'right' ? 'right' : 'left'
  draftActive.value = { side: 'left', index: 0 }
  setupPage.value = 'main'
}

function buildBaseState() {
  const cached = loadMatchState(matchId.value)
  return cached || createEmptyMatchState()
}

function goToScoreboard() {
  uni.redirectTo({
    url: buildScoreboardUrl(pageQuery.value),
  })
}

function confirmLineup() {
  if (draftLeftCourt.value.some((item) => !item) || draftRightCourt.value.some((item) => !item)) {
    uni.showToast({ title: '请先补齐双方首发站位', icon: 'none' })
    return
  }

  const state = buildBaseState()
  state.draftLeftCourt = cloneCourt(draftLeftCourt.value)
  state.draftRightCourt = cloneCourt(draftRightCourt.value)
  state.leftCourt = cloneCourt(draftLeftCourt.value)
  state.rightCourt = cloneCourt(draftRightCourt.value)
  state.baseLeftCourt = cloneCourt(draftLeftCourt.value)
  state.baseRightCourt = cloneCourt(draftRightCourt.value)
  state.draftServeSide = draftServeSide.value
  state.currentGameStartServeSide = draftServeSide.value
  state.serveSide = draftServeSide.value
  state.lineupReady = true
  saveMatchState(matchId.value, state)
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

    leftTeam.value = participantMap.get(match.leftPlayerId) || { name: '主队', members: [] }
    rightTeam.value = participantMap.get(match.rightPlayerId) || { name: '客队', members: [] }

    if (leftTeam.value.members.length < 6 || rightTeam.value.members.length < 6) {
      throw new Error('双方队伍都至少需要 6 名队员')
    }

    const cached = loadMatchState(matchId.value)
    if (cached?.lineupReady && !cached.matchEnded) {
      goToScoreboard()
      return
    }

    if (cached) {
      applyDraftFromState(cached)
    } else {
      const state = createEmptyMatchState()
      state.draftServeSide = 'left'
      state.currentGameStartServeSide = 'left'
      applyDraftFromState(state)
    }
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
  const cached = loadMatchState(matchId.value)
  if (cached && !cached.matchEnded && !cached.lineupReady && Number(cached.currentGameNo || 1) > 1 && !options?.serveSide) {
    draftServeSide.value = toggleSide(cached.currentGameStartServeSide)
  }
  loadMatch()
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
  min-height: 100vh;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
  color: #ffffff;
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

.setup-page {
  min-height: calc(100vh - 68rpx);
  display: flex;
  flex-direction: column;
}

.lineup-header {
  text-align: center;
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

.setup-section {
  margin-top: 32rpx;
}

.setup-label {
  display: block;
  text-align: center;
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
  font-weight: 700;
}

.serve-options {
  display: flex;
  justify-content: center;
  gap: 10rpx;
  margin-top: 16rpx;
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

.team-entry-name {
  font-size: 30rpx;
  font-weight: 800;
  white-space: nowrap;
}

.team-entry-meta {
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

.lineup-footer {
  padding-top: 24rpx;
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
}

.editor-back,
.editor-done {
  min-width: 88rpx;
  font-size: 24rpx;
  color: #ffb347;
}

.editor-done {
  text-align: right;
}

.editor-body {
  flex: 1;
  min-height: 0;
  margin-top: 24rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.draft-slots {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12rpx;
  width: 100%;
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
}

.draft-slot.active {
  border-color: rgba(255, 140, 0, 0.5);
  background: rgba(255, 140, 0, 0.14);
}

.draft-pos {
  color: rgba(255, 255, 255, 0.5);
  font-size: 20rpx;
  white-space: nowrap;
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

.editor-roster {
  flex: 1;
  min-height: 0;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.04);
  padding: 8rpx;
  box-sizing: border-box;
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

.draft-member.chosen {
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
}
</style>
