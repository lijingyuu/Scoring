<template>
  <view class="page" :style="pageStyle">
    <view class="hero">
      <text class="hero-title">赛事大厅</text>
      <text class="hero-desc">搜索比赛并快速发起新比赛。为保护隐私，首页不再默认展示赛事列表。</text>
      <input
        class="search-input"
        v-model="keyword"
        placeholder="输入比赛名称或地点关键词"
        confirm-type="search"
        @confirm="fetchTournaments"
      />
      <button class="create-btn" @click="goCreate">创建比赛</button>
    </view>

    <view class="section-header">
      <text class="section-title">{{ hasKeyword ? '搜索结果' : '赛事大厅' }}</text>
      <text class="section-action" @click="handleRefresh">搜索</text>
    </view>

    <view class="list" v-if="hasKeyword">
      <TournamentListCard
        v-for="item in tournaments"
        :key="item.id"
        :item="item"
        @open="openDetail"
        @toggle-favorite="toggleFavorite"
      />
      <view class="empty" v-if="!tournaments.length">没有找到相关比赛</view>
    </view>

    <view class="empty empty-hint" v-else>输入关键词以查看相关比赛</view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 28) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === "function"
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
      if (typeof uni?.upx2px === "function") {
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
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}

const pageStyle = buildBasePortraitPageStyle(28)

const keyword = ref('')
const tournaments = ref([])
const hasKeyword = computed(() => !!keyword.value.trim())

async function fetchTournaments() {
  const query = keyword.value.trim()
  if (!query) {
    tournaments.value = []
    return
  }
  tournaments.value = await request('/api/v1/tournaments?keyword=' + encodeURIComponent(query), { method: 'GET' })
}

function handleRefresh() {
  if (!hasKeyword.value) {
    uni.showToast({ title: '请输入关键词', icon: 'none' })
    return
  }
  fetchTournaments()
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

function goCreate() {
  uni.navigateTo({ url: '/pages/create/sport' })
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
  if (hasKeyword.value) {
    fetchTournaments()
  } else {
    tournaments.value = []
  }
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

.empty-hint {
  min-height: 36vh;
  display: flex;
  align-items: center;
  justify-content: center;
  color: rgba(255, 255, 255, 0.5);
  font-size: 28rpx;
  letter-spacing: 1rpx;
}
</style>
