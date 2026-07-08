<template>
  <view>
    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { bootstrapAuth } from '@/store/auth'

onLaunch(() => {
  bootstrapAuth()
})

onShow(() => {
  bootstrapAuth()
})
</script>

<style>
page {
  background: #13202d;
}

.safe-back-btn {
  display: inline-flex;
  align-items: center;
  min-height: 32px;
  padding: 0 16rpx 0 0;
  box-sizing: border-box;
}
</style>
