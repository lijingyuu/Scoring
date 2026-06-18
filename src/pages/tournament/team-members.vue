<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">队伍成员</text>
    </view>

    <view class="state-layer" v-if="loading">
      <text class="state-text">正在加载队员...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadTeamMembers">重新加载</button>
    </view>

    <view v-else class="content">
      <view class="summary-card">
        <text class="summary-title">{{ team?.name || teamName || '队伍成员' }}</text>
        <text class="summary-meta">{{ members.length }} 人</text>
      </view>

      <view v-if="members.length" class="member-list">
        <view class="member-card" v-for="member in members" :key="member.id">
          <view class="member-main">
            <text class="member-no">{{ member.jerseyNumber }}号</text>
            <text class="member-name">{{ member.name }}</text>
          </view>
          <view class="member-tags">
            <text v-if="member.captain" class="member-tag captain">队长</text>
            <text v-if="member.libero" class="member-tag libero">自由人</text>
          </view>
        </view>
      </view>

      <view v-else class="empty-card">
        <text class="empty-title">暂无队员</text>
        <text class="empty-desc">当前队伍没有可展示的成员数据。</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { sortVolleyballMembers } from '@/utils/volleyball-team'

const tournamentId = ref('')
const participantId = ref('')
const teamName = ref('')
const team = ref(null)
const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')

const members = computed(() => sortVolleyballMembers(team.value?.members || []))

function goBack() {
  uni.navigateBack()
}

async function loadTeamMembers() {
  if (!tournamentId.value || !participantId.value) {
    loading.value = false
    isError.value = true
    errorText.value = '缺少队伍参数'
    return
  }

  loading.value = true
  isError.value = false
  try {
    const data = await request('/api/v1/tournaments/' + tournamentId.value + '/teams', { method: 'GET' })
    const list = Array.isArray(data?.teams) ? data.teams : []
    team.value = list.find((item) => item.id === participantId.value) || null
    if (!team.value) {
      throw new Error('未找到对应队伍')
    }
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载队员失败'
  } finally {
    loading.value = false
  }
}

onLoad((options) => {
  tournamentId.value = options?.tournamentId || ''
  participantId.value = options?.participantId || ''
  teamName.value = decodeURIComponent(options?.teamName || '')
  loadTeamMembers()
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
.member-card,
.empty-card {
  padding: 22rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.summary-title,
.member-name,
.empty-title {
  color: #ffffff;
  font-weight: 700;
}

.summary-title {
  display: block;
  font-size: 32rpx;
}

.summary-meta,
.empty-desc {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  line-height: 1.6;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.member-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
}

.member-main {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.member-no {
  min-width: 88rpx;
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.member-name {
  font-size: 28rpx;
}

.member-tags {
  display: flex;
  gap: 10rpx;
  flex-shrink: 0;
}

.member-tag {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 700;
}

.member-tag.captain {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
}

.member-tag.libero {
  background: rgba(82, 196, 26, 0.16);
  color: #95de64;
}

.empty-title {
  display: block;
  font-size: 30rpx;
}
</style>
