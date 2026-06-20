<template>
  <view v-if="authState.popupVisible" class="popup-mask" @click="handleMask">
    <view class="popup-card" @click.stop>
      <view class="popup-title">完善个人资料</view>
      <view class="popup-desc">用于创建比赛、收藏比赛和裁判操作，可稍后再补全。</view>

      <button class="avatar-btn" open-type="chooseAvatar" @chooseavatar="onChooseAvatar">
        <image v-if="authState.avatarUrl" class="avatar-image" :src="authState.avatarUrl" mode="aspectFill" />
        <text v-else>选择头像</text>
      </button>

      <input
        class="nickname-input"
        type="nickname"
        v-model="authState.nickname"
        placeholder="请输入昵称"
      />

      <view class="popup-actions">
        <button class="ghost-btn" @click="closeProfilePopup">暂不完善</button>
        <button class="primary-btn" :loading="authState.loading" @click="submitProfile">保存资料</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { authState, closeProfilePopup, submitProfile } from '@/store/auth'

function onChooseAvatar(event) {
  authState.avatarUrl = event?.detail?.avatarUrl || ''
}

function handleMask() {
  closeProfilePopup()
}
</script>

<style scoped>
.popup-mask {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.72);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}

.popup-card {
  width: 100%;
  background: #194955;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  padding: 40rpx 32rpx 54rpx;
  box-sizing: border-box;
  border-top: 1rpx solid rgba(255, 255, 255, 0.16);
  box-shadow: 0 -18rpx 40rpx rgba(0, 0, 0, 0.24), inset 0 0 0 9999px rgba(0, 0, 0, 0.1);
}

.popup-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
}

.popup-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.72);
}

.avatar-btn {
  margin-top: 28rpx;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.88);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  padding: 0;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.avatar-btn::after,
.ghost-btn::after,
.primary-btn::after {
  border: none;
}

.avatar-image {
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
}

.nickname-input {
  margin-top: 28rpx;
  height: 84rpx;
  padding: 0 24rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 28rpx;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.popup-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 30rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 16rpx;
  font-size: 28rpx;
  border: none;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.86);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.14);
}

.primary-btn {
  background: rgba(255, 255, 255, 0.18);
  color: #ffffff;
  font-weight: 700;
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.28);
}
</style>
