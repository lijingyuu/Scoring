<template>
  <view class="page" :style="pageStyle">
    <view class="profile-card">
      <view class="profile-main">
        <image v-if="authState.profile?.avatarUrl" class="avatar" :src="authState.profile.avatarUrl" mode="aspectFill" />
        <view v-else class="avatar placeholder">我</view>
        <view class="profile-meta">
          <text class="profile-name">{{ profileName }}</text>
          <text class="profile-hint">{{ profileHint }}</text>
        </view>
      </view>

      <button class="profile-action" :loading="actionLoading" @click="handlePrimaryAction">{{ primaryActionText }}</button>
    </view>

    <view class="block">
      <view class="block-title">我收藏的比赛</view>
      <view class="list" v-if="isLoggedIn && favoriteList.length">
        <TournamentListCard
          v-for="item in favoriteList"
          :key="'fav-' + item.id"
          :item="item"
          @open="openDetail"
          @toggle-favorite="toggleFavorite"
        />
      </view>
      <view class="empty" v-else>{{ favoriteEmptyText }}</view>
    </view>

    <view class="block">
      <view class="block-title">我创建的比赛</view>
      <view class="list" v-if="isLoggedIn && createdList.length">
        <TournamentListCard
          v-for="item in createdList"
          :key="'created-' + item.id"
          :item="item"
          @open="openDetail"
          @toggle-favorite="toggleFavorite"
        />
      </view>
      <view class="empty" v-else>{{ createdEmptyText }}</view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import TournamentListCard from '@/components/TournamentListCard.vue'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { authState, ensureAuth, fetchProfile, openProfileEditor, requireProfile } from '@/store/auth'
import { request } from '@/utils/request'

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 0) {
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

const pageStyle = buildBasePortraitPageStyle()

const favoriteList = ref([])
const createdList = ref([])
const actionLoading = ref(false)

const isLoggedIn = computed(() => !!authState.token)
const profileName = computed(() => {
  if (authState.profile?.nickname) return authState.profile.nickname
  return isLoggedIn.value ? '微信用户' : '游客'
})
const profileHint = computed(() => {
  if (!isLoggedIn.value) return '登录后可查看你的比赛与收藏'
  if (authState.profileCompleted) return '资料已完善，可继续使用全部功能'
  return '已登录，可主动完善个人资料'
})
const primaryActionText = computed(() => {
  if (!isLoggedIn.value) return '微信登录'
  return authState.profileCompleted ? '修改资料' : '完善资料'
})
const favoriteEmptyText = computed(() => (isLoggedIn.value ? '还没有收藏比赛' : '登录后可查看收藏比赛'))
const createdEmptyText = computed(() => (isLoggedIn.value ? '你还没有创建比赛' : '登录后可查看创建的比赛'))

function clearLists() {
  favoriteList.value = []
  createdList.value = []
}

async function fetchData() {
  if (!authState.token) {
    clearLists()
    return
  }

  try {
    await fetchProfile()
    const [favorites, created] = await Promise.all([
      request('/api/v1/tournaments/mine/favorites', { method: 'GET', silent: true }),
      request('/api/v1/tournaments/mine/created', { method: 'GET', silent: true }),
    ])
    favoriteList.value = favorites
    createdList.value = created
  } catch (error) {
    clearLists()
    uni.showToast({ title: error?.message || '加载比赛失败', icon: 'none' })
  }
}

function openDetail(item) {
  uni.navigateTo({ url: '/pages/tournament/detail?id=' + item.id })
}

async function handlePrimaryAction() {
  actionLoading.value = true
  try {
    if (!isLoggedIn.value) {
      await ensureAuth()
      await fetchProfile()
      await fetchData()
      return
    }
    await openProfileEditor()
  } catch (error) {
    uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
  } finally {
    actionLoading.value = false
  }
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
  padding: 0 24rpx 40rpx;
  box-sizing: border-box;
  background: linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.profile-card {
  padding: 28rpx;
  border-radius: 26rpx;
  background: rgba(255, 255, 255, 0.06);
}

.profile-main {
  display: flex;
  align-items: center;
  gap: 18rpx;
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

.profile-action {
  margin-top: 24rpx;
  height: 84rpx;
  line-height: 84rpx;
  border: none;
  border-radius: 18rpx;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 28rpx;
  font-weight: 800;
}

.profile-action::after {
  border: none;
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
