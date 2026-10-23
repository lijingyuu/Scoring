<template>
  <view class="lock-guard-overlay">
    <view class="lock-guard-card">
      <text class="lock-guard-title">{{ title }}</text>
      <text class="lock-guard-message">{{ message }}</text>
      <view v-if="score" class="lock-guard-score">
        <text class="lock-guard-score-name">{{ score.leftName }}</text>
        <text class="lock-guard-score-value">{{ score.leftScore }} : {{ score.rightScore }}</text>
        <text class="lock-guard-score-name">{{ score.rightName }}</text>
      </view>
      <view class="lock-guard-actions">
        <button class="lock-guard-btn secondary" @click="$emit('back')">{{ backLabel }}</button>
        <button
          class="lock-guard-btn"
          :disabled="retryDisabled || retrying"
          :loading="retrying"
          @click="$emit('retry')">
          {{ retryLabel }}
        </button>
      </view>
    </view>
  </view>
</template>

<script setup>
/**
 * 执裁锁全屏遮罩（配合 composables/use-lock-guard.js 使用）。
 * 取代旧的「只读模式」横幅：锁不可得（denied）或执裁中失去（lost）时覆盖页面，
 * z-index 高于各记分板页面弹窗（局末确认/换边/结算等）。
 * 纯展示组件：文案/比分/按钮状态由 props 提供，动作全部抛回父级。
 */
defineProps({
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  // lost 模式下显示的当前比分：{ leftName, rightName, leftScore, rightScore }
  score: { type: Object, default: null },
  retryLabel: { type: String, default: '重试' },
  backLabel: { type: String, default: '返回' },
  retryDisabled: { type: Boolean, default: false },
  retrying: { type: Boolean, default: false },
})

defineEmits(['retry', 'back'])
</script>

<style scoped>
.lock-guard-overlay {
  position: fixed;
  inset: 0;
  z-index: 999;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 32rpx;
  box-sizing: border-box;
  background: rgba(0, 0, 0, 0.82);
}

.lock-guard-card {
  width: 78vw;
  max-width: 640rpx;
  padding: 40rpx 32rpx;
  box-sizing: border-box;
  border-radius: 24rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.18);
  background: #22364c;
  box-shadow: 0 12rpx 40rpx rgba(0, 0, 0, 0.4);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.lock-guard-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 1.3;
}

.lock-guard-message {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.86);
  line-height: 1.5;
  text-align: center;
}

.lock-guard-score {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 18rpx;
  padding: 14rpx 0 4rpx;
}

.lock-guard-score-name {
  max-width: 220rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 26rpx;
  color: rgba(255, 255, 255, 0.9);
}

.lock-guard-score-value {
  font-size: 40rpx;
  font-weight: 700;
  color: #ffb347;
  white-space: nowrap;
}

.lock-guard-actions {
  width: 100%;
  display: flex;
  justify-content: center;
  gap: 18rpx;
  margin-top: 6rpx;
}

.lock-guard-btn {
  flex: 1;
  max-width: 260rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 14rpx;
  border: none;
  font-size: 26rpx;
  font-weight: 700;
  color: #ffffff;
  background: rgba(255, 140, 0, 0.2);
  box-shadow: inset 0 0 0 1rpx rgba(255, 140, 0, 0.55);
}

.lock-guard-btn::after {
  border: none;
}

.lock-guard-btn.secondary {
  background: rgba(255, 255, 255, 0.1);
  box-shadow: inset 0 0 0 1rpx rgba(255, 255, 255, 0.32);
}

.lock-guard-btn[disabled] {
  opacity: 0.45;
}
</style>
