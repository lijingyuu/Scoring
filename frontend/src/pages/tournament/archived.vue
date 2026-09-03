<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">已归档比赛</text>
    </view>

    <view class="list" v-if="archivedList.length">
      <TournamentListCard
        v-for="item in archivedList"
        :key="item.id"
        :item="item"
        :show-favorite="false"
        archive-action-text="取消归档"
        @open="openDetail"
        @archive-action="unarchiveTournament"
      />
    </view>

    <view class="empty" v-else>{{ emptyText }}</view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import { authState, ensureAuth } from '@/store/auth'
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
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}

const pageStyle = buildBasePortraitPageStyle()
const archivedList = ref([])
const emptyText = computed(() => (authState.token ? '暂无已归档比赛' : '登录后可查看已归档比赛'))

function goBack() {
  uni.navigateBack()
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

async function fetchArchived() {
  if (!authState.token) {
    archivedList.value = []
    return
  }
  await ensureAuth()
  archivedList.value = await request('/api/v1/tournaments/mine/archived', { method: 'GET', silent: true })
}

async function unarchiveTournament(item) {
  uni.showModal({
    title: '取消归档',
    content: '取消归档后，这场比赛会重新出现在我的比赛中。',
    confirmText: '取消归档',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await request('/api/v1/tournaments/' + item.id + '/unarchive', { method: 'PUT' })
        uni.showToast({ title: '已取消归档', icon: 'success' })
        await fetchArchived()
      } catch (_) {
        // request handles toast
      }
    },
  })
}

onShow(() => {
  fetchArchived()
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
