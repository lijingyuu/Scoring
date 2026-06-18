<template>
  <view class="page">
    <view class="profile-card">
      <image v-if="authState.profile?.avatarUrl" class="avatar" :src="authState.profile.avatarUrl" mode="aspectFill" />
      <view v-else class="avatar placeholder">我</view>
      <view class="profile-meta">
        <text class="profile-name">{{ authState.profile?.nickname || '游客' }}</text>
        <text class="profile-hint">{{ authState.profileCompleted ? '资料已同步' : '可先浏览，操作时再补全资料' }}</text>
      </view>
    </view>

    <view class="block">
      <view class="block-title">我收藏的比赛</view>
      <view class="list">
        <TournamentListCard
          v-for="item in favoriteList"
          :key="'fav-' + item.id"
          :item="item"
          @open="openDetail"
          @toggle-favorite="toggleFavorite"
        />
        <view class="empty" v-if="!favoriteList.length">还没有收藏比赛</view>
      </view>
    </view>

    <view class="block">
      <view class="block-title">我创建的比赛</view>
      <view class="list">
        <TournamentListCard
          v-for="item in createdList"
          :key="'created-' + item.id"
          :item="item"
          @open="openDetail"
          @toggle-favorite="toggleFavorite"
        />
        <view class="empty" v-if="!createdList.length">你还没有创建比赛</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import { authState, ensureAuth, fetchProfile, requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

const favoriteList = ref([])
const createdList = ref([])

async function fetchData() {
  try {
    await ensureAuth()
    await fetchProfile()
    const [favorites, created] = await Promise.all([
      request('/api/v1/tournaments/mine/favorites', { method: 'GET', silent: true }),
      request('/api/v1/tournaments/mine/created', { method: 'GET', silent: true }),
    ])
    favoriteList.value = favorites
    createdList.value = created
  } catch (error) {
    favoriteList.value = []
    createdList.value = []
    uni.showToast({ title: error?.message || '加载比赛失败', icon: 'none' })
  }
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

async function toggleFavorite(item) {
  try {
    await requireProfile()
    if (item.favorite) {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'DELETE' })
    } else {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'POST' })
    }
    await fetchData()
  } catch (_) {
    // noop
  }
}

onShow(() => {
  fetchData()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 28rpx 24rpx 40rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.profile-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 28rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.06);
}

.avatar,
.placeholder {
  width: 108rpx;
  height: 108rpx;
  border-radius: 50%;
}

.placeholder {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
  font-weight: 700;
}

.profile-meta {
  flex: 1;
  min-width: 0;
}

.profile-name {
  display: block;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
}

.profile-hint {
  display: block;
  margin-top: 10rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 24rpx;
}

.block {
  margin-top: 26rpx;
}

.block-title {
  margin-bottom: 16rpx;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.empty {
  padding: 50rpx 0;
  color: rgba(255, 255, 255, 0.38);
  text-align: center;
  font-size: 24rpx;
}

</style>
