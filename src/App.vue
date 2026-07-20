<template>
  <view>
    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { onLaunch, onShow } from '@dcloudio/uni-app'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { bootstrapAuth } from '@/store/auth'

// 微信小程序初始启动时 onShow 紧跟在 onLaunch 之后触发，
// 若两次都调用 bootstrapAuth 会在页面栈就绪前产生竞态，
// 导致 "appLaunch with non-empty page stack" 框架级错误。
let initialLaunch = true

onLaunch(() => {
  bootstrapAuth()
})

onShow(() => {
  // 初始启动时 onLaunch 已处理，跳过；后续切前台时正常刷新。
  if (initialLaunch) {
    initialLaunch = false
    return
  }
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
