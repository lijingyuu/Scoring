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

    <view class="entry-list" v-if="isLoggedIn">
      <view class="mine-entry" @click="openFavorites">我的收藏</view>
      <view class="mine-entry" @click="openCreated">我的创建</view>
      <view class="mine-entry" @click="openArchived">已归档比赛</view>
    </view>

    <view class="contact-card" @click="copyContactEmail">
      <text class="contact-title">反馈联系</text>
      <text class="contact-desc">使用中遇到问题或有建议，可通过邮箱联系我</text>
      <text class="contact-email">{{ contactEmail }}</text>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { authState, ensureAuth, fetchProfile, openProfileEditor } from '@/store/auth'

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

const actionLoading = ref(false)
const contactEmail = 'lijingyu05@163.com'

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

function openFavorites() {
  uni.navigateTo({ url: '/pages/tournament/mine-list?type=favorites' })
}

function openCreated() {
  uni.navigateTo({ url: '/pages/tournament/mine-list?type=created' })
}

function openArchived() {
  uni.navigateTo({ url: '/pages/tournament/archived' })
}

function copyContactEmail() {
  uni.setClipboardData({
    data: contactEmail,
    success: () => {
      setTimeout(() => {
        uni.showToast({ title: '邮箱已复制', icon: 'success' })
      }, 80)
    },
    fail: () => {
      uni.showToast({ title: '复制失败，请手动复制', icon: 'none' })
    },
  })
}

async function handlePrimaryAction() {
  actionLoading.value = true
  try {
    if (!isLoggedIn.value) {
      await ensureAuth()
      await fetchProfile()
      return
    }
    await openProfileEditor()
  } catch (error) {
    uni.showToast({ title: error?.message || '操作失败', icon: 'none' })
  } finally {
    actionLoading.value = false
  }
}

onShow(() => {
  if (isLoggedIn.value) fetchProfile()
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

.entry-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  margin-top: 22rpx;
}

.mine-entry {
  padding: 22rpx;
  border-radius: 16rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.05);
  color: rgba(255, 255, 255, 0.82);
  font-size: 26rpx;
  text-align: center;
}

.contact-card {
  margin-top: 22rpx;
  padding: 24rpx 26rpx;
  border-radius: 16rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  background: rgba(255, 255, 255, 0.04);
}

.contact-title,
.contact-desc,
.contact-email {
  display: block;
  text-align: center;
}

.contact-title {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.contact-desc {
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 23rpx;
  line-height: 1.45;
}

.contact-email {
  margin-top: 14rpx;
  color: #ffb347;
  font-size: 25rpx;
}

@media screen and (min-width: 700px) and (min-height: 700px) {
  .page {
    padding-left: 32px;
    padding-right: 32px;
    padding-bottom: 32px;
  }

  .profile-card,
  .entry-list,
  .contact-card {
    width: 100%;
    max-width: 640px;
    margin-left: auto;
    margin-right: auto;
    box-sizing: border-box;
  }

  .mine-entry {
    box-sizing: border-box;
  }

  .profile-card {
    padding: 22px 24px;
    border-radius: 18px;
  }

  .profile-main {
    gap: 14px;
  }

  .avatar,
  .placeholder {
    width: 64px;
    height: 64px;
  }

  .placeholder {
    font-size: 22px;
  }

  .profile-name {
    font-size: 21px;
  }

  .profile-hint {
    margin-top: 6px;
    font-size: 15px;
  }

  .profile-action {
    margin-top: 18px;
    height: 50px;
    line-height: 50px;
    border-radius: 12px;
    font-size: 17px;
  }

  .entry-list {
    gap: 12px;
    margin-top: 18px;
  }

  .mine-entry {
    padding: 16px;
    border-radius: 12px;
    font-size: 16px;
  }

  .contact-card {
    margin-top: 18px;
    padding: 18px 20px;
    border-radius: 12px;
  }

  .contact-title {
    font-size: 16px;
  }

  .contact-desc {
    margin-top: 8px;
    font-size: 14px;
  }

  .contact-email {
    margin-top: 10px;
    font-size: 15px;
  }
}
</style>
