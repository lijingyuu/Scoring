<template>
  <view v-if="visible" class="popup-mask" @click="handleCancel">
    <view class="popup-card" @click.stop>
      <view class="popup-title">{{ title }}</view>
      <view class="popup-desc">{{ description }}</view>

      <input
        class="referee-input"
        v-model="localPassword"
        type="number"
        maxlength="8"
        password
        placeholder="请输入裁判密码"
      />

      <view class="popup-actions">
        <button class="ghost-btn" @click="handleCancel">{{ cancelText }}</button>
        <button class="primary-btn" :loading="loading" @click="handleSubmit">{{ confirmText }}</button>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  visible: {
    type: Boolean,
    default: false,
  },
  loading: {
    type: Boolean,
    default: false,
  },
  title: {
    type: String,
    default: '裁判验证',
  },
  description: {
    type: String,
    default: '请输入裁判密码，验证后可修改和封存战报。',
  },
  confirmText: {
    type: String,
    default: '验证',
  },
  cancelText: {
    type: String,
    default: '取消',
  },
})

const emit = defineEmits(['update:visible', 'submit', 'cancel'])

const localPassword = ref('')

const popupTitle = computed(() => props.title)
const popupDescription = computed(() => props.description)
const popupConfirmText = computed(() => props.confirmText)
const popupCancelText = computed(() => props.cancelText)

watch(
  () => props.visible,
  (visible) => {
    if (visible) {
      localPassword.value = ''
    }
  },
  { immediate: true }
)

function handleCancel() {
  emit('update:visible', false)
  emit('cancel')
}

function handleSubmit() {
  emit('submit', localPassword.value.trim())
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
}

.popup-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.5;
  color: rgba(29, 37, 46, 0.72);
}

.referee-input {
  height: 84rpx;
  margin-top: 24rpx;
  padding: 0 24rpx;
  box-sizing: border-box;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.84);
  color: #1d252e;
  font-size: 28rpx;
  border: 1rpx solid rgba(34, 44, 55, 0.14);
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
