<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">创建排球比赛</text>
    </view>

    <view class="form-panel">
      <input class="input" v-model="form.name" placeholder="比赛名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />

      <view class="section">
        <view class="section-title">比赛赛制</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.tournamentType === 0 }" @click="setTournamentType(0)">淘汰赛</view>
          <view class="segment-item" :class="{ active: form.tournamentType === 1 }" @click="setTournamentType(1)">小组+淘汰</view>
        </view>

        <template v-if="form.tournamentType === 1">
          <view class="rule-row">
            <text class="rule-label">淘汰名额</text>
            <view class="segment compact">
              <view class="segment-item" :class="{ active: form.knockoutSlots === 4 }" @click="setKnockoutSlots(4)">4</view>
              <view class="segment-item" :class="{ active: form.knockoutSlots === 8 }" @click="setKnockoutSlots(8)">8</view>
              <view class="segment-item" :class="{ active: form.knockoutSlots === 16 }" @click="setKnockoutSlots(16)">16</view>
            </view>
          </view>

          <view class="rule-row">
            <text class="rule-label">每组出线</text>
            <view class="stepper">
              <view class="step-btn" @click="form.qualifiersPerGroup = Math.max(1, form.qualifiersPerGroup - 1)">-</view>
              <input class="step-input" type="number" :value="form.qualifiersPerGroup" @input="setQualifiersPerGroup" />
              <view class="step-btn" @click="form.qualifiersPerGroup = Math.min(2, form.qualifiersPerGroup + 1)">+</view>
            </view>
          </view>

          <text class="hint">预计 {{ groupCount }} 组，每组约 {{ estimatedGroupSize }} 队</text>
        </template>
      </view>

      <view class="section">
        <view class="section-title">比赛规则</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.bestOf === 3 }" @click="setBestOf(3)">三局两胜</view>
          <view class="segment-item" :class="{ active: form.bestOf === 5 }" @click="setBestOf(5)">五局三胜</view>
        </view>
        <text class="hint">标准排球规则：常规局 25 分，末局 15 分，均需领先 2 分。</text>
      </view>

      <view class="section team-section">
        <view class="section-head">
          <text class="section-title">参赛队伍</text>
          <text class="section-meta">已添加 {{ form.teams.length }} 支</text>
        </view>

        <view class="team-list" v-if="form.teams.length">
          <view class="team-card" v-for="(team, index) in form.teams" :key="team.id">
            <view class="team-main">
              <text class="team-name">{{ team.name }}</text>
              <text class="team-desc">{{ team.members.length }} 人 / 队长 {{ captainName(team) }}</text>
            </view>
            <view class="team-actions">
              <text class="team-action" @click="openEditor(index)">编辑</text>
              <text class="team-action danger" @click="removeTeam(index)">删除</text>
            </view>
          </view>
        </view>

        <view class="empty-card" v-else>
          <text class="empty-title">还没有队伍</text>
          <text class="empty-desc">先添加两支及以上队伍，再创建排球比赛。</text>
        </view>
      </view>
    </view>

    <view class="footer-bar">
      <button class="ghost-btn" @click="openEditor()">添加队伍</button>
      <button class="primary-btn" :loading="submitting" :disabled="submitting" @click="createTournament">创建比赛</button>
    </view>

    <view class="editor-mask" v-if="editorVisible" @click="closeEditor">
      <view class="editor-panel" @click.stop>
        <view class="editor-header">
          <text class="editor-title">{{ editingIndex === -1 ? '新增队伍' : '编辑队伍' }}</text>
          <text class="editor-close" @click="closeEditor">x</text>
        </view>

        <scroll-view class="editor-scroll" scroll-y>
          <input class="input" v-model="teamDraft.name" placeholder="球队名称" />

          <view class="draft-tools">
            <button class="sample-btn" @click="fillTestMembers">填入测试样例</button>
          </view>

          <view class="member-list">
            <view class="member-row" v-for="(member, index) in teamDraft.members" :key="index">
              <text class="member-no">{{ index + 1 }}</text>
              <input class="member-input name" v-model="member.name" placeholder="球员姓名" />
              <input class="member-input jersey" type="number" v-model="member.jerseyNumber" placeholder="号码" />
              <view class="member-toggle captain" :class="{ active: teamDraft.captainIndex === index }" @click="setCaptain(index)">队长</view>
            </view>
          </view>
        </scroll-view>

        <view class="editor-footer">
          <button class="ghost-btn" @click="closeEditor">取消</button>
          <button class="primary-btn" @click="saveTeam">确定</button>
        </view>
      </view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

const submitting = ref(false)
const editorVisible = ref(false)
const editingIndex = ref(-1)
const seed = ref(1)

const form = reactive({
  name: '',
  location: '',
  bestOf: 3,
  tournamentType: 0,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  teams: [],
})

const teamCount = computed(() => form.teams.length)
const groupCount = computed(() => Math.max(1, Math.floor(form.knockoutSlots / form.qualifiersPerGroup)))
const estimatedGroupSize = computed(() => {
  if (!teamCount.value) return '-'
  return Math.ceil(teamCount.value / groupCount.value)
})

const teamDraft = reactive(createEmptyDraft())

function createEmptyDraft() {
  return {
    name: '',
    captainIndex: -1,
    members: Array.from({ length: 12 }, () => ({
      name: '',
      jerseyNumber: '',
    })),
  }
}

function resetDraft() {
  const fresh = createEmptyDraft()
  teamDraft.name = fresh.name
  teamDraft.captainIndex = fresh.captainIndex
  teamDraft.members.splice(0, teamDraft.members.length, ...fresh.members)
}

function goBack() {
  uni.navigateBack()
}

function setBestOf(bestOf) {
  form.bestOf = bestOf
}

function setTournamentType(type) {
  form.tournamentType = type
}

function setKnockoutSlots(slots) {
  form.knockoutSlots = slots
}

function setQualifiersPerGroup(event) {
  form.qualifiersPerGroup = Math.max(1, Math.min(2, Number(event.detail.value) || 1))
}

function openEditor(index = -1) {
  editingIndex.value = index
  resetDraft()
  if (index >= 0) {
    const team = form.teams[index]
    teamDraft.name = team.name
    teamDraft.captainIndex = team.members.findIndex((item) => item.captain)
    team.members.forEach((member, memberIndex) => {
      if (!teamDraft.members[memberIndex]) return
      teamDraft.members[memberIndex] = {
        name: member.name,
        jerseyNumber: String(member.jerseyNumber),
      }
    })
  }
  editorVisible.value = true
}

function closeEditor() {
  editorVisible.value = false
  editingIndex.value = -1
}

function setCaptain(index) {
  if (!teamDraft.members[index].name.trim()) {
    uni.showToast({ title: '请先填写球员姓名', icon: 'none' })
    return
  }
  teamDraft.captainIndex = teamDraft.captainIndex === index ? -1 : index
}

function fillTestMembers() {
  const sampleNames = ['张一', '张二', '张三', '张四', '张五', '张六', '张七', '张八', '张九', '张十', '张十一', '张十二']
  teamDraft.members.forEach((member, index) => {
    member.name = sampleNames[index] || ''
    member.jerseyNumber = sampleNames[index] ? String(index + 1) : ''
  })
  teamDraft.captainIndex = 0
  uni.showToast({ title: '已填入测试样例', icon: 'none' })
}

function normalizeTeamDraft() {
  const members = teamDraft.members
    .map((member, index) => ({
      name: member.name.trim(),
      jerseyNumber: Number(member.jerseyNumber),
      captain: teamDraft.captainIndex === index,
    }))
    .filter((member) => member.name)

  return {
    id: editingIndex.value >= 0 ? form.teams[editingIndex.value].id : 'team_' + seed.value++,
    name: teamDraft.name.trim(),
    members,
  }
}

function validateTeam(team) {
  if (!team.name) {
    uni.showToast({ title: '请输入球队名称', icon: 'none' })
    return false
  }
  if (team.members.length < 6) {
    uni.showToast({ title: '每支队伍至少需要 6 名球员', icon: 'none' })
    return false
  }

  let captainCount = 0
  const jerseySet = new Set()
  for (const member of team.members) {
    if (!Number.isFinite(member.jerseyNumber) || member.jerseyNumber <= 0) {
      uni.showToast({ title: '请填写有效的球员号码', icon: 'none' })
      return false
    }
    if (jerseySet.has(member.jerseyNumber)) {
      uni.showToast({ title: '同一支队伍不能有重复号码', icon: 'none' })
      return false
    }
    jerseySet.add(member.jerseyNumber)
    if (member.captain) captainCount += 1
  }
  if (captainCount !== 1) {
    uni.showToast({ title: '请指定 1 名队长', icon: 'none' })
    return false
  }
  return true
}

function saveTeam() {
  const team = normalizeTeamDraft()
  if (!validateTeam(team)) return

  if (editingIndex.value >= 0) {
    form.teams.splice(editingIndex.value, 1, team)
  } else {
    form.teams.push(team)
  }
  closeEditor()
}

function removeTeam(index) {
  form.teams.splice(index, 1)
}

function captainName(team) {
  return team.members.find((item) => item.captain)?.name || '-'
}

function validateTournamentConfig() {
  if (form.tournamentType === 1 && form.knockoutSlots > form.teams.length) {
    uni.showToast({ title: '淘汰名额不能超过参赛队伍数', icon: 'none' })
    return false
  }
  if (form.tournamentType === 1 && teamCount.value) {
    const minGroupSize = Math.floor(teamCount.value / groupCount.value)
    if (minGroupSize <= form.qualifiersPerGroup) {
      uni.showToast({ title: '每组队伍数必须大于出线名额', icon: 'none' })
      return false
    }
  }
  return true
}

async function createTournament() {
  if (submitting.value) return
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入比赛名称', icon: 'none' })
    return
  }
  if (form.teams.length < 2) {
    uni.showToast({ title: '至少需要 2 支队伍', icon: 'none' })
    return
  }
  if (!validateTournamentConfig()) return

  submitting.value = true
  try {
    await requireProfile()
    const res = await request('/api/v1/tournaments', {
      method: 'POST',
      data: {
        sportType: 1,
        name: form.name.trim(),
        location: form.location.trim() || undefined,
        tournamentType: form.tournamentType,
        knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
        qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
        players: [],
        teams: form.teams.map((team) => ({
          name: team.name,
          members: team.members.map((member) => ({
            name: member.name,
            jerseyNumber: member.jerseyNumber,
            captain: member.captain,
          })),
        })),
        rule: {
          bestOf: form.bestOf,
          gamesToWin: Math.floor(form.bestOf / 2) + 1,
        },
      },
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    uni.redirectTo({ url: '/pages/tournament/detail?id=' + res.tournamentId })
  } catch (error) {
    if (error?.message && error.message !== '你取消了资料补全') {
      uni.showToast({ title: error.message, icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 160rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.18), transparent 34%),
    linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.header,
.section-head,
.team-card,
.editor-header,
.editor-footer,
.footer-bar,
.rule-row {
  display: flex;
  align-items: center;
}

.header {
  gap: 18rpx;
  margin-bottom: 24rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
}

.title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
}

.form-panel {
  padding: 28rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.06);
}

.input,
.step-input {
  width: 100%;
  height: 84rpx;
  padding: 0 22rpx;
  box-sizing: border-box;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
}

.input {
  margin-bottom: 16rpx;
}

.section {
  margin-top: 22rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.2);
}

.section-title {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.segment {
  display: flex;
  margin-top: 16rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  border-radius: 14rpx;
  overflow: hidden;
}

.segment.compact {
  margin-top: 0;
  min-width: 280rpx;
}

.segment-item {
  flex: 1;
  min-height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.68);
  font-size: 24rpx;
  background: rgba(255, 255, 255, 0.05);
}

.segment-item.active {
  background: #ff8c00;
  color: #152231;
  font-weight: 700;
}

.rule-row {
  justify-content: space-between;
  gap: 18rpx;
  margin-top: 18rpx;
}

.rule-label {
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.step-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 32rpx;
}

.step-input {
  width: 120rpx;
  text-align: center;
  padding: 0 12rpx;
}

.hint,
.section-meta,
.team-desc,
.empty-desc {
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  line-height: 1.6;
}

.hint {
  display: block;
  margin-top: 16rpx;
}

.section-head {
  justify-content: space-between;
}

.team-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.team-card,
.empty-card {
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.05);
}

.team-card {
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.team-main {
  flex: 1;
  min-width: 0;
}

.team-name,
.empty-title,
.editor-title {
  color: #ffffff;
  font-weight: 700;
}

.team-name {
  display: block;
  font-size: 30rpx;
}

.team-actions {
  display: flex;
  gap: 14rpx;
}

.team-action {
  color: #ffb347;
  font-size: 24rpx;
}

.team-action.danger {
  color: #ff7a45;
}

.empty-title {
  display: block;
  font-size: 30rpx;
}

.empty-desc {
  display: block;
  margin-top: 10rpx;
}

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  gap: 16rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 18rpx;
  border: none;
  font-size: 28rpx;
}

.ghost-btn::after,
.primary-btn::after,
.sample-btn::after {
  border: none;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn {
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-weight: 800;
}

.editor-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.58);
  z-index: 30;
}

.editor-panel {
  width: 100%;
  max-height: 90vh;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  background: linear-gradient(180deg, #23384d 0%, #172636 100%);
  border-top: 1rpx solid rgba(255, 140, 0, 0.35);
  padding: 24rpx 24rpx 32rpx;
  box-sizing: border-box;
}

.editor-header {
  justify-content: space-between;
}

.editor-title {
  font-size: 32rpx;
}

.editor-close {
  color: rgba(255, 255, 255, 0.58);
  font-size: 40rpx;
  padding: 0 10rpx;
}

.editor-scroll {
  max-height: 66vh;
  margin-top: 18rpx;
}

.draft-tools {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16rpx;
}

.sample-btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 24rpx;
  border: none;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 24rpx;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-bottom: 12rpx;
}

.member-row {
  display: grid;
  grid-template-columns: 48rpx 1fr 132rpx 110rpx;
  gap: 10rpx;
  align-items: center;
}

.member-no {
  color: rgba(255, 255, 255, 0.55);
  font-size: 22rpx;
  text-align: center;
}

.member-input {
  height: 72rpx;
  box-sizing: border-box;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 24rpx;
  padding: 0 18rpx;
}

.member-input.jersey {
  text-align: center;
}

.member-toggle {
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}

.member-toggle.active {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
  font-weight: 700;
}

.member-toggle.captain.active {
  background: #ff8c00;
  color: #142130;
}

.editor-footer {
  gap: 16rpx;
  margin-top: 18rpx;
}
</style>
