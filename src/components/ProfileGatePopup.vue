<template>
  <view v-if="authState.popupVisible" class="popup-mask" @click="handleMask">
    <view class="popup-card" @click.stop>
      <view class="popup-title">完善比赛资料</view>
      <view class="popup-desc">首次创建比赛前，请先补全头像和昵称。</view>

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
        <button class="ghost-btn" @click="closeProfilePopup">稍后再说</button>
        <button class="primary-btn" :loading="authState.loading" @click="submitProfile">保存并继续</button>
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
  background: rgba(0, 0, 0, 0.58);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  z-index: 999;
}

.popup-card {
  width: 100%;
  background: linear-gradient(180deg, #24384f 0%, #162434 100%);
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  padding: 40rpx 32rpx 54rpx;
  box-sizing: border-box;
  border-top: 1rpx solid rgba(255, 140, 0, 0.35);
}

.popup-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
}

.popup-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.62);
}

.avatar-btn {
  margin-top: 28rpx;
  width: 140rpx;
  height: 140rpx;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  padding: 0;
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
  color: rgba(255, 255, 255, 0.78);
}

.primary-btn {
  background: #ff8c00;
  color: #142130;
  font-weight: 700;
}
</style>
