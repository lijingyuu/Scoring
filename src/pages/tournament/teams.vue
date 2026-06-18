<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">查看队伍</text>
    </view>

    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载队伍...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadTeams">重新加载</button>
    </view>

    <view v-else class="content">
      <view class="summary-card">
        <text class="summary-title">{{ tournamentName || '队伍列表' }}</text>
        <text class="summary-meta">共 {{ teams.length }} 支队伍</text>
      </view>

      <view v-if="teams.length" class="team-list">
        <view class="team-card" v-for="team in teams" :key="team.id" @click="openTeam(team)">
          <view class="team-main">
            <text class="team-name">{{ team.name }}</text>
            <text class="team-desc">{{ team.memberCount || 0 }} 人 / 队长 {{ team.captainName || '-' }}</text>
          </view>
          <text class="team-arrow">查看</text>
        </view>
      </view>

      <view v-else class="empty-card">
        <text class="empty-title">暂无队伍</text>
        <text class="empty-desc">当前赛事还没有可查看的队伍数据。</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const tournamentId = ref('')
const tournamentName = ref('')
const teams = ref([])
const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')

function goBack() {
  uni.navigateBack()
}

function openTeam(team) {
  if (!team?.id) return
  uni.navigateTo({
    url: '/pages/tournament/team-members?tournamentId='
      + encodeURIComponent(tournamentId.value)
      + '&participantId=' + encodeURIComponent(team.id)
      + '&teamName=' + encodeURIComponent(team.name || ''),
  })
}

async function loadTeams() {
  if (!tournamentId.value) {
    loading.value = false
    isError.value = true
    errorText.value = '缺少赛事ID'
    return
  }

  loading.value = true
  isError.value = false
  try {
    const [detail, data] = await Promise.all([
      request('/api/v1/tournaments/' + tournamentId.value, { method: 'GET' }),
      request('/api/v1/tournaments/' + tournamentId.value + '/teams', { method: 'GET' }),
    ])
    tournamentName.value = detail?.name || ''
    teams.value = Array.isArray(data?.teams) ? data.teams : []
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载队伍失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  tournamentId.value = options?.id || ''
  loadTeams()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 40rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.18), transparent 34%),
    linear-gradient(180deg, #13202d 0%, #0f1822 100%);
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

.state-layer {
  min-height: calc(100vh - 140rpx);
  display: flex;
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

.summary-card,
.team-card,
.empty-card {
  padding: 22rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.summary-title,
.team-name,
.empty-title {
  color: #ffffff;
  font-weight: 700;
}

.summary-title {
  display: block;
  font-size: 32rpx;
}

.summary-meta,
.team-desc,
.empty-desc {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  line-height: 1.6;
}

.team-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.team-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.team-main {
  flex: 1;
  min-width: 0;
}

.team-name {
  display: block;
  font-size: 30rpx;
}

.team-arrow {
  color: #ffb347;
  font-size: 24rpx;
  flex-shrink: 0;
}

.empty-title {
  display: block;
  font-size: 30rpx;
}
</style>
