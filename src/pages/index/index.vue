<template>
  <view class="page">
    <view class="hero">
      <text class="hero-title">赛事大厅</text>
      <text class="hero-desc">搜索比赛、查看热门赛事，也可以快速发起一场新比赛。</text>
      <input
        class="search-input"
        v-model="keyword"
        placeholder="搜索比赛名称或地点"
        confirm-type="search"
        @confirm="fetchTournaments"
      />
      <button class="create-btn" @click="goCreate">创建比赛</button>
    </view>

    <view class="section-header">
      <text class="section-title">{{ keyword.trim() ? '搜索结果' : '热门比赛' }}</text>
      <text class="section-action" @click="fetchTournaments">刷新</text>
    </view>

    <view class="list">
      <TournamentListCard
        v-for="item in tournaments"
        :key="item.id"
        :item="item"
        @open="openDetail"
        @toggle-favorite="toggleFavorite"
      />
      <view class="empty" v-if="!tournaments.length">暂时没有找到比赛</view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import { requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

const keyword = ref('')
const tournaments = ref([])

async function fetchTournaments() {
  const query = keyword.value.trim()
  const suffix = query ? '?keyword=' + encodeURIComponent(query) : ''
  tournaments.value = await request('/api/v1/tournaments' + suffix, { method: 'GET' })
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/create/index' })
}

async function toggleFavorite(item) {
  try {
    await requireProfile()
    if (item.favorite) {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'DELETE' })
    } else {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'POST' })
    }
    await fetchTournaments()
  } catch (_) {
    // noop
  }
}

onShow(() => {
  fetchTournaments()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 40rpx;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.2), transparent 34%),
    linear-gradient(180deg, #142130 0%, #101a25 100%);
  box-sizing: border-box;
}

.hero {
  padding: 30rpx 28rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.06);
  border: 1rpx solid rgba(255, 140, 0, 0.18);
}

.hero-title {
  display: block;
  font-size: 42rpx;
  font-weight: 800;
  color: #ffffff;
}

.hero-desc {
  display: block;
  margin-top: 14rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.65);
  line-height: 1.6;
}

.search-input {
  margin-top: 24rpx;
  height: 84rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 28rpx;
}

.create-btn {
  margin-top: 24rpx;
  height: 92rpx;
  line-height: 92rpx;
  border-radius: 18rpx;
  border: none;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 30rpx;
  font-weight: 800;
}

.create-btn::after {
  border: none;
}

.section-header {
  margin-top: 28rpx;
  margin-bottom: 18rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.section-title {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
}

.section-action {
  color: #ffb347;
  font-size: 24rpx;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.empty {
  padding: 80rpx 0;
  text-align: center;
  color: rgba(255, 255, 255, 0.4);
  font-size: 26rpx;
}
</style>
