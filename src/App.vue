<template>
  <ProfileGatePopup />
</template>

<script>
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { bootstrapAuth } from '@/store/auth'

export default {
  components: {
    ProfileGatePopup,
  },
  onLaunch() {
    bootstrapAuth()
  },
  onShow() {
    bootstrapAuth()
  },
}
</script>

<style>
page {
  background: #13202d;
}
</style>
