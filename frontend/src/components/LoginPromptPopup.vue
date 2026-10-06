<template>
  <view v-if="visible" class="popup-mask" @click="handleCancel">
    <view class="popup-card" @click.stop>
      <view class="popup-title">{{ title }}</view>
      <view class="popup-desc">{{ description }}</view>

      <view class="popup-actions">
        <button class="ghost-btn" @click="handleCancel">{{ cancelText }}</button>
        <button class="primary-btn" @click="handleConfirm">{{ confirmText }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
// 「需要登录」引导弹窗：未登录/资料未完善时，操作被拦截前先给用户
// 一个明确的选择（知道了 / 登录后录入），点确认后由父页面拉起底部
// 登录弹窗（ProfileGatePopup）。视觉风格与 RefereeAuthPopup 保持一致。
const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '需要登录',
  },
  description: {
    type: String,
    default: '你需要先登录才能录入裁判',
  },
  confirmText: {
    type: String,
    default: '登录后录入',
  },
  cancelText: {
    type: String,
    default: '知道了',
  },
})

const emit = defineEmits(['update:visible', 'confirm', 'cancel'])

function handleCancel() {
  emit('update:visible', false)
  emit('cancel')
}

function handleConfirm() {
  // 不在此处关闭弹窗：父页面需先关闭本弹窗再拉起底部登录弹窗，关闭时机由父页面控制
  emit('confirm')
}
</script>

<style scoped>
.popup-mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.7);
}

.popup-card {
  width: 100%;
  max-width: 640rpx;
  padding: 32rpx;
  box-sizing: border-box;
  border-radius: 24rpx;
  background: #f7f1e6;
  color: #1d252e;
  box-shadow: 0 18rpx 42rpx rgba(0, 0, 0, 0.28);
}

.popup-title {
  font-size: 34rpx;
  font-weight: 800;
  line-height: 1.2;
  text-align: center;
}

.popup-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
  text-align: center;
  color: rgba(29, 37, 46, 0.72);
}

.popup-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 28rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  border: none;
  border-radius: 16rpx;
  font-size: 28rpx;
  font-weight: 700;
}

.ghost-btn {
  background: rgba(34, 44, 55, 0.06);
  color: #5f6b78;
}

.primary-btn {
  background: #ffb347;
  color: #13202d;
}

.ghost-btn::after,
.primary-btn::after {
  border: none;
}
</style>
