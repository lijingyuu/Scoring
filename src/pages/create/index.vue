<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">创建比赛</text>
    </view>

    <view class="form-panel">
      <input class="input" v-model="form.name" placeholder="比赛名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />

      <view class="section">
        <view class="section-title">赛制</view>
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
          <view class="hint">预计 {{ groupCount }} 组，每组约 {{ estimatedGroupSize }} 人</view>
        </template>
      </view>

      <view class="section">
        <view class="section-title">规则</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.rule.bestOf === 1 }" @click="setBestOf(1)">一局</view>
          <view class="segment-item" :class="{ active: form.rule.bestOf === 3 }" @click="setBestOf(3)">三局</view>
          <view class="segment-item" :class="{ active: form.rule.bestOf === 5 }" @click="setBestOf(5)">五局</view>
        </view>
        <view class="rule-row">
          <text class="rule-label">基础胜分</text>
          <view class="stepper">
            <view class="step-btn" @click="form.rule.pointsToWin = Math.max(1, form.rule.pointsToWin - 1)">-</view>
            <input class="step-input" type="number" :value="form.rule.pointsToWin" @input="setPointsToWin" />
            <view class="step-btn" @click="form.rule.pointsToWin = Math.min(99, form.rule.pointsToWin + 1)">+</view>
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
            <view class="step-btn" @click="form.rule.capPoint = Math.max(form.rule.pointsToWin + 1, form.rule.capPoint - 1)">-</view>
            <input class="step-input" type="number" :value="form.rule.capPoint" @input="setCapPoint" />
            <view class="step-btn" @click="form.rule.capPoint = Math.min(99, form.rule.capPoint + 1)">+</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">裁判设置</view>
        <input class="input" v-model="form.refereePassword" type="number" maxlength="6" placeholder="裁判密码（6位数字，选填）" />
        <text class="hint">设置密码后，裁判可通过密码验证操作比赛。留空则不启用裁判功能。</text>
      </view>

      <textarea class="textarea" v-model="form.players" placeholder="每行一名选手，可在前面加种子序号，例如：1 张三" />
      <button class="submit-btn" :loading="submitting" :disabled="submitting" @click="createTournament">生成比赛</button>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { requireProfile } from '@/store/auth'
import { useActionLock } from '@/utils/interaction-guard'
import { request } from '@/utils/request'

const submitting = ref(false)
const { begin: beginNav } = useActionLock(500)

const form = reactive({
  name: '',
  location: '',
  players: '',
  tournamentType: 0,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  refereePassword: '',
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
})

const groupCount = computed(() => Math.max(1, Math.floor(form.knockoutSlots / form.qualifiersPerGroup)))
const playerCount = computed(() => parsePlayers(form.players).length)
const estimatedGroupSize = computed(() => {
  if (!playerCount.value) return '-'
  return Math.ceil(playerCount.value / groupCount.value)
})

function goBack() {
  if (!beginNav()) return
  uni.navigateBack()
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
  form.knockoutSlots = 8
  form.qualifiersPerGroup = 2
  setBestOf(3)
  form.rule.pointsToWin = 21
  form.rule.enableDeuce = true
  form.rule.capPoint = 30
  form.refereePassword = ''
}

async function createTournament() {
  if (submitting.value) return

  if (!form.name.trim()) {
    uni.showToast({ title: '请输入比赛名称', icon: 'none' })
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
  if (form.tournamentType === 1 && form.knockoutSlots > players.length) {
    uni.showToast({ title: '淘汰名额不能超过参赛人数', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await requireProfile()
    const res = await request('/api/v1/tournaments', {
      method: 'POST',
      data: {
        name: form.name.trim(),
        location: form.location.trim() || undefined,
        tournamentType: form.tournamentType,
        knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
        qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
        players,
        rule: {
          bestOf: form.rule.bestOf,
          gamesToWin: form.rule.gamesToWin,
          pointsToWin: form.rule.pointsToWin,
          enableDeuce: form.rule.enableDeuce,
          capPoint: form.rule.capPoint,
        },
        refereePassword: form.refereePassword.trim() || undefined,
      },
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    resetForm()
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
  padding: 28rpx 24rpx 40rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.header {
  display: flex;
  align-items: center;
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
.textarea {
  width: 100%;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  border-radius: 16rpx;
  font-size: 26rpx;
}

.input {
  height: 84rpx;
  padding: 0 22rpx;
  margin-bottom: 16rpx;
}

.textarea {
  min-height: 280rpx;
  padding: 22rpx;
  margin-top: 22rpx;
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
  margin-bottom: 16rpx;
}

.segment {
  display: flex;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  border-radius: 14rpx;
  overflow: hidden;
}

.segment.compact {
  width: 240rpx;
}

.segment-item {
  flex: 1;
  min-height: 52rpx;
  line-height: 52rpx;
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
  margin-top: 16rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
}

.rule-label,
.hint {
  color: rgba(255, 255, 255, 0.64);
  font-size: 24rpx;
}

.hint {
  display: block;
  margin-top: 16rpx;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.step-btn {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 10rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  color: #ffb347;
}

.step-input {
  width: 84rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.submit-btn {
  margin-top: 24rpx;
  height: 90rpx;
  line-height: 90rpx;
  border-radius: 18rpx;
  border: none;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 30rpx;
  font-weight: 800;
}

.submit-btn::after {
  border: none;
}
</style>
