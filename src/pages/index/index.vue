<template>
  <view class="page">
    <view class="form-section">
      <view class="form-title">新建赛事</view>
      <input class="input" v-model="form.name" placeholder="赛事名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />

      <view class="rule-panel">
        <view class="rule-title">赛制</view>
        <view class="rule-row">
          <text class="rule-label">阶段</text>
          <view class="segment">
            <view class="segment-item" :class="{ active: form.tournamentType === 0 }" @click="setTournamentType(0)">淘汰赛</view>
            <view class="segment-item" :class="{ active: form.tournamentType === 1 }" @click="setTournamentType(1)">小组赛</view>
          </view>
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
            <text class="rule-label">预计组数</text>
            <text class="rule-value">{{ groupCount }}组</text>
          </view>
          <view class="rule-row">
            <text class="rule-label">每组人数</text>
            <view class="stepper">
              <view class="stepper-btn" @click="form.groupSize = Math.max(2, form.groupSize - 1)">-</view>
              <input class="stepper-input" type="number" :value="form.groupSize" @input="setGroupSize" />
              <view class="stepper-btn" @click="form.groupSize = Math.min(16, form.groupSize + 1)">+</view>
            </view>
          </view>
          <view class="rule-row">
            <text class="rule-label">每组晋级</text>
            <view class="stepper">
              <view class="stepper-btn" @click="form.qualifiersPerGroup = Math.max(1, form.qualifiersPerGroup - 1)">-</view>
              <input class="stepper-input" type="number" :value="form.qualifiersPerGroup" @input="setQualifiersPerGroup" />
              <view class="stepper-btn" @click="form.qualifiersPerGroup = Math.min(form.groupSize - 1, form.qualifiersPerGroup + 1)">+</view>
            </view>
          </view>
        </template>
      </view>

      <view class="rule-panel">
        <view class="rule-title">比赛规则</view>
        <view class="rule-row">
          <text class="rule-label">局制</text>
          <view class="segment">
            <view class="segment-item" :class="{ active: form.rule.bestOf === 1 }" @click="setBestOf(1)">一局定胜负</view>
            <view class="segment-item" :class="{ active: form.rule.bestOf === 3 }" @click="setBestOf(3)">三局两胜</view>
            <view class="segment-item" :class="{ active: form.rule.bestOf === 5 }" @click="setBestOf(5)">五局三胜</view>
          </view>
        </view>
        <view class="rule-row">
          <text class="rule-label">基础胜分</text>
          <view class="stepper">
            <view class="stepper-btn" @click="form.rule.pointsToWin = Math.max(1, form.rule.pointsToWin - 1)">-</view>
            <input class="stepper-input" type="number" :value="form.rule.pointsToWin" @input="setPointsToWin" />
            <view class="stepper-btn" @click="form.rule.pointsToWin = Math.min(99, form.rule.pointsToWin + 1)">+</view>
          </view>
        </view>
        <view class="rule-row">
          <text class="rule-label">追分机制</text>
          <view class="segment compact">
            <view class="segment-item" :class="{ active: form.rule.enableDeuce }" @click="form.rule.enableDeuce = true">开启</view>
            <view class="segment-item" :class="{ active: !form.rule.enableDeuce }" @click="form.rule.enableDeuce = false">关闭</view>
          </view>
        </view>
        <view class="rule-row" v-if="form.rule.enableDeuce">
          <text class="rule-label">封顶分</text>
          <view class="stepper">
            <view class="stepper-btn" @click="form.rule.capPoint = Math.max(form.rule.pointsToWin + 1, form.rule.capPoint - 1)">-</view>
            <input class="stepper-input" type="number" :value="form.rule.capPoint" @input="setCapPoint" />
            <view class="stepper-btn" @click="form.rule.capPoint = Math.min(99, form.rule.capPoint + 1)">+</view>
          </view>
        </view>
      </view>

      <textarea
        class="textarea"
        v-model="form.players"
        placeholder="每行一名选手。可在姓名前加数字指定种子，例如：1 张三。"
      />
      <button class="submit-btn" @click="createTournament">一键生成赛事</button>
    </view>

    <view class="list-section">
      <view class="list-title">赛事大厅 / 全部比赛</view>

      <view
        class="card"
        v-for="item in tournamentList"
        :key="item.id"
        @click="goToTournament(item)"
      >
        <view class="card-header">
          <text class="card-name">{{ item.name }}</text>
          <text class="card-status" :class="'status-' + item.status">{{ statusLabels[item.status] }}</text>
        </view>
        <view class="card-body">
          <text class="card-info" v-if="item.location">{{ item.location }}</text>
          <text class="card-info">{{ getTournamentTypeText(item) }} / {{ getRuleText(item) }}</text>
          <text class="card-time">{{ item.createTime }}</text>
        </view>
      </view>

      <view class="empty" v-if="!tournamentList.length">暂无赛事，先创建一场比赛</view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, reactive } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const statusLabels = { 0: '未开始', 1: '进行中', 2: '已结束' }

const form = reactive({
  name: '',
  location: '',
  players: '',
  tournamentType: 0,
  groupSize: 4,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
})
const tournamentList = ref([])
const groupCount = computed(() => Math.max(1, Math.floor(form.knockoutSlots / form.qualifiersPerGroup)))

function setTournamentType(type) {
  form.tournamentType = type
}

function setKnockoutSlots(slots) {
  form.knockoutSlots = slots
}

function setGroupSize(event) {
  form.groupSize = Math.max(2, Math.min(16, Number(event.detail.value) || 4))
  if (form.qualifiersPerGroup >= form.groupSize) {
    form.qualifiersPerGroup = form.groupSize - 1
  }
}

function setQualifiersPerGroup(event) {
  form.qualifiersPerGroup = Math.max(1, Math.min(form.groupSize - 1, Number(event.detail.value) || 1))
}

function setBestOf(bestOf) {
  form.rule.bestOf = bestOf
  form.rule.gamesToWin = Math.floor(bestOf / 2) + 1
}

function setPointsToWin(event) {
  const value = Math.max(1, Math.min(99, Number(event.detail.value) || 1))
  form.rule.pointsToWin = value
  if (form.rule.capPoint <= value) {
    form.rule.capPoint = Math.min(99, value + 1)
  }
}

function setCapPoint(event) {
  const min = form.rule.pointsToWin + 1
  form.rule.capPoint = Math.max(min, Math.min(99, Number(event.detail.value) || min))
}

function getTournamentTypeText(item) {
  if (Number(item.tournamentType || 0) === 1) {
    return `小组赛 每组${item.groupSize || 4}人 / 出线${item.qualifiersPerGroup || 2}人`
  }
  return '淘汰赛'
}

function getRuleText(item) {
  const bestOf = Number(item.bestOf || 3)
  const points = Number(item.pointsToWin || 21)
  const cap = Number(item.capPoint || 30)
  const deuce = item.enableDeuce === false ? '无追分' : `${cap}分封顶`
  const matchText = bestOf === 5 ? '五局三胜' : bestOf === 1 ? '一局定胜负' : '三局两胜'
  return `${matchText} / ${points}分 / ${deuce}`
}

async function fetchTournaments() {
  try {
    const res = await request('/api/v1/tournaments', { method: 'GET' })
    tournamentList.value = res || []
  } catch (_) {
    // request handles toast
  }
}

async function createTournament() {
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入赛事名称', icon: 'none' })
    return
  }
  if (!form.players.trim()) {
    uni.showToast({ title: '请输入参赛选手', icon: 'none' })
    return
  }

  const players = parsePlayers(form.players)
  if (players.length < 2) {
    uni.showToast({ title: '至少需要2名选手', icon: 'none' })
    return
  }
  if (form.tournamentType === 1 && form.qualifiersPerGroup >= Math.min(form.groupSize, players.length)) {
    uni.showToast({ title: '晋级人数必须少于每组人数', icon: 'none' })
    return
  }

  if (form.tournamentType === 1 && form.knockoutSlots > players.length) {
    uni.showToast({ title: '淘汰名额不能超过参赛人数', icon: 'none' })
    return
  }

  const tournamentType = form.tournamentType

  try {
    const res = await request('/api/v1/tournaments', {
      method: 'POST',
      data: {
        name: form.name.trim(),
        location: form.location.trim() || undefined,
        tournamentType,
        groupSize: tournamentType === 1 ? form.groupSize : undefined,
        knockoutSlots: tournamentType === 1 ? form.knockoutSlots : undefined,
        qualifiersPerGroup: tournamentType === 1 ? form.qualifiersPerGroup : undefined,
        players,
        rule: {
          bestOf: form.rule.bestOf,
          gamesToWin: form.rule.gamesToWin,
          pointsToWin: form.rule.pointsToWin,
          enableDeuce: form.rule.enableDeuce,
          capPoint: form.rule.capPoint,
        },
      },
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    resetForm()
    fetchTournaments()
    if (res?.tournamentId) {
      const url = tournamentType === 1
        ? '/pages/tournament/groups?id=' + res.tournamentId
        : '/pages/tournament/bracket?id=' + res.tournamentId
      uni.navigateTo({ url })
    }
  } catch (_) {
    // request handles toast
  }
}

function parsePlayers(text) {
  return text
    .split(/[\n\r]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)[.\s、]?\s*(.+)$/)
      if (!match) return { name: line, seed: null }
      return {
        name: match[2].trim(),
        seed: parseInt(match[1], 10),
      }
    })
}

function resetForm() {
  form.name = ''
  form.location = ''
  form.players = ''
  form.tournamentType = 0
  form.groupSize = 4
  form.knockoutSlots = 8
  form.qualifiersPerGroup = 2
  setBestOf(3)
  form.rule.pointsToWin = 21
  form.rule.enableDeuce = true
  form.rule.capPoint = 30
}

function goToTournament(item) {
  const id = item?.id
  if (!id) return
  const url = Number(item.tournamentType || 0) === 1
    ? '/pages/tournament/groups?id=' + id
    : '/pages/tournament/bracket?id=' + id
  uni.navigateTo({ url })
}

onShow(() => {
  fetchTournaments()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #1a2a3a;
  color: #ffffff;
  padding: 32rpx 28rpx;
  box-sizing: border-box;
}

.form-section {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 140, 0, 0.3);
  border-radius: 20rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 36rpx;
}

.form-title {
  font-size: 32rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
}

.input {
  height: 80rpx;
  line-height: 80rpx;
  padding: 0 20rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
  margin-bottom: 16rpx;
}

.input::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.rule-panel {
  border: 1rpx solid rgba(255, 140, 0, 0.28);
  border-radius: 14rpx;
  padding: 18rpx;
  margin-bottom: 18rpx;
  background: rgba(255, 255, 255, 0.04);
}

.rule-title {
  font-size: 26rpx;
  font-weight: 700;
  margin-bottom: 16rpx;
}

.rule-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
  margin-bottom: 14rpx;
}

.rule-row:last-child {
  margin-bottom: 0;
}

.rule-label {
  width: 130rpx;
  color: rgba(255, 255, 255, 0.78);
  font-size: 24rpx;
  flex-shrink: 0;
}

.segment {
  flex: 1;
  display: flex;
  border: 1rpx solid rgba(255, 140, 0, 0.4);
  border-radius: 10rpx;
  overflow: hidden;
}

.segment.compact {
  flex: none;
  width: 220rpx;
}

.segment-item {
  flex: 1;
  min-height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.62);
  background: rgba(255, 255, 255, 0.06);
}

.segment-item.active {
  background: #ff8c00;
  color: #1a2a3a;
  font-weight: 700;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.stepper-btn {
  width: 52rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: 8rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.45);
  color: #ff8c00;
  font-size: 28rpx;
}

.stepper-input {
  width: 84rpx;
  height: 48rpx;
  line-height: 48rpx;
  text-align: center;
  border-radius: 8rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 24rpx;
}

.textarea {
  width: 100%;
  height: 300rpx;
  padding: 20rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
  box-sizing: border-box;
  margin-bottom: 24rpx;
}

.textarea::placeholder {
  color: rgba(255, 255, 255, 0.4);
}

.submit-btn {
  width: 100%;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 14rpx;
  border: none;
  background: #ff8c00;
  color: #1a2a3a;
  font-size: 30rpx;
  font-weight: 700;
}

.submit-btn::after {
  border: none;
}

.list-section {
  padding-bottom: 40rpx;
}

.list-title {
  font-size: 30rpx;
  font-weight: 700;
  margin-bottom: 20rpx;
  color: rgba(255, 255, 255, 0.85);
}

.card {
  background: rgba(255, 255, 255, 0.06);
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 16rpx;
  padding: 24rpx 22rpx;
  margin-bottom: 16rpx;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12rpx;
}

.card-name {
  font-size: 30rpx;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-status {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.status-0 {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.7);
}

.status-1 {
  background: rgba(255, 140, 0, 0.2);
  color: #ff8c00;
}

.status-2 {
  background: rgba(76, 217, 100, 0.15);
  color: #4cd964;
}

.card-body {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card-info,
.card-time {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.55);
}

.empty {
  text-align: center;
  color: rgba(255, 255, 255, 0.35);
  font-size: 26rpx;
  padding: 60rpx 0;
}
</style>
