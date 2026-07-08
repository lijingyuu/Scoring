<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">{{ pageConfig.title }}</text>
    </view>

    <view class="list" v-if="tournamentList.length">
      <TournamentListCard
        v-for="item in tournamentList"
        :key="item.id"
        :item="item"
        :show-favorite="pageConfig.showFavorite"
        @open="openDetail"
        @toggle-favorite="toggleFavorite"
      />
    </view>

    <view class="empty" v-else>{{ emptyText }}</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import { authState, ensureAuth, requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === 'function'
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop
    } else {
      const statusBarHeight = Number(info?.statusBarHeight)
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
        safeTopPx = statusBarHeight
      }
    }
  } catch (_) {
    // noop
  }

  let extraTopPx = 0
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2)
    try {
      if (typeof uni?.upx2px === 'function') {
        const px = Number(uni.upx2px(extraTopRpx))
        if (Number.isFinite(px) && px > 0) {
          extraTopPx = px
        }
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: 'border-box',
    paddingTop: String(safeTopPx + extraTopPx) + 'px',
  }
}

const CONFIGS = {
  favorites: {
    title: '我收藏的比赛',
    endpoint: '/api/v1/tournaments/mine/favorites',
    empty: '还没有收藏比赛',
    guestEmpty: '登录后可查看收藏比赛',
    showFavorite: true,
  },
  created: {
    title: '我创建的比赛',
    endpoint: '/api/v1/tournaments/mine/created',
    empty: '你还没有创建比赛',
    guestEmpty: '登录后可查看创建的比赛',
    showFavorite: false,
  },
}

const pageStyle = buildBasePortraitPageStyle()
const listType = ref('favorites')
const tournamentList = ref([])

const pageConfig = computed(() => CONFIGS[listType.value] || CONFIGS.favorites)
const emptyText = computed(() => (authState.token ? pageConfig.value.empty : pageConfig.value.guestEmpty))

function goBack() {
  uni.navigateBack()
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

async function fetchList() {
  if (!authState.token) {
    tournamentList.value = []
    return
  }
  await ensureAuth()
  tournamentList.value = await request(pageConfig.value.endpoint, { method: 'GET', silent: true })
}

async function toggleFavorite(item) {
  if (!pageConfig.value.showFavorite) return
  try {
    await requireProfile()
    if (item.favorite) {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'DELETE' })
    } else {
      await request('/api/v1/tournaments/' + item.id + '/favorite', { method: 'POST' })
    }
    await fetchList()
  } catch (_) {
    // noop
  }
}

onLoad((options) => {
  if (CONFIGS[options?.type]) {
    listType.value = options.type
  }
})

onShow(() => {
  fetchList()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 40rpx;
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

.list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.empty {
  padding: 90rpx 0;
  color: rgba(255, 255, 255, 0.42);
  text-align: center;
  font-size: 26rpx;
}
</style>
