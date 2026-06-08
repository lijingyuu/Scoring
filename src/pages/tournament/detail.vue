<template>
  <view class="page">
    <view class="header">
      <text class="back-btn" @click="goBack">返回</text>
      <text class="title">赛事详情</text>
    </view>

    <view class="detail-card" v-if="detail">
      <text class="name">{{ detail.name }}</text>
      <text class="line" v-if="detail.location">{{ detail.location }}</text>
      <text class="line">运动类型：{{ sportText }}</text>
      <text class="line">{{ typeText }}</text>
      <text class="line">{{ ruleText }}</text>
      <text class="line">收藏数：{{ detail.favoriteCount || 0 }}</text>
      <text class="line">创建时间：{{ detail.createTime || '-' }}</text>

      <view class="actions">
        <button class="secondary-btn" @click="toggleFavorite">{{ detail.favorite ? '取消收藏' : '收藏比赛' }}</button>
        <button class="primary-btn" @click="goToTournament">查看赛程</button>
      </view>

      <button class="judge-btn" v-if="detail.creator" @click="goJudge">进入计分板</button>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

const tournamentId = ref('')
const detail = ref(null)

const isVolleyball = computed(() => Number(detail.value?.sportType || 0) === 1)

const sportText = computed(() => isVolleyball.value ? '排球' : '羽毛球')

const typeText = computed(() => {
  if (isVolleyball.value) {
    return '淘汰赛'
  }
  if (Number(detail.value?.tournamentType || 0) === 1) {
    return `小组+淘汰 / ${detail.value?.knockoutSlots || 8}强 / 每组出线${detail.value?.qualifiersPerGroup || 2}人`
  }
  return '淘汰赛'
})

const ruleText = computed(() => {
  const bestOf = Number(detail.value?.bestOf || 3)
  if (isVolleyball.value) {
    return `${bestOf === 5 ? '五局三胜' : '三局两胜'} / 常规局25分 / 末局15分 / 领先2分`
  }
  const matchText = bestOf === 5 ? '五局三胜' : bestOf === 1 ? '一局定胜负' : '三局两胜'
  return `${matchText} / ${detail.value?.pointsToWin || 21}分 / ${detail.value?.enableDeuce ? `${detail.value?.capPoint || 30}分封顶` : '无追分'}`
})

async function fetchDetail() {
  if (!tournamentId.value) return
  detail.value = await request('/api/v1/tournaments/' + tournamentId.value, { method: 'GET' })
}

function goBack() {
  uni.navigateBack()
}

function goToTournament() {
  if (!detail.value?.id) return
  const url = !isVolleyball.value && Number(detail.value.tournamentType || 0) === 1
    ? '/pages/tournament/groups?id=' + detail.value.id
    : '/pages/tournament/bracket?id=' + detail.value.id
  uni.navigateTo({ url })
}

async function toggleFavorite() {
  try {
    await requireProfile()
    if (detail.value?.favorite) {
      await request('/api/v1/tournaments/' + tournamentId.value + '/favorite', { method: 'DELETE' })
    } else {
      await request('/api/v1/tournaments/' + tournamentId.value + '/favorite', { method: 'POST' })
    }
    await fetchDetail()
  } catch (_) {
    // noop
  }
}

async function goJudge() {
  try {
    await requireProfile()
    goToTournament()
  } catch (_) {
    // noop
  }
}

onLoad((options) => {
  tournamentId.value = options?.id || ''
})

onShow(() => {
  fetchDetail()
})
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

.detail-card {
  padding: 28rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.06);
}

.name {
  display: block;
  color: #ffffff;
  font-size: 38rpx;
  font-weight: 800;
}

.line {
  display: block;
  margin-top: 14rpx;
  color: rgba(255, 255, 255, 0.65);
  font-size: 26rpx;
  line-height: 1.6;
}

.actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.secondary-btn,
.primary-btn,
.judge-btn {
  border: none;
  border-radius: 18rpx;
}

.secondary-btn::after,
.primary-btn::after,
.judge-btn::after {
  border: none;
}

.secondary-btn,
.primary-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}

.secondary-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn,
.judge-btn {
  background: #ff8c00;
  color: #13202d;
  font-weight: 700;
}

.judge-btn {
  margin-top: 20rpx;
  height: 84rpx;
  line-height: 84rpx;
  font-size: 28rpx;
}
</style>
