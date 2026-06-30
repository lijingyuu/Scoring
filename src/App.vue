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
</style>
