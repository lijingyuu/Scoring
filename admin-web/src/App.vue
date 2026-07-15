<template>
  <RouterView />
</template>

<script setup>
import { RouterView, useRouter } from 'vue-router'
import { setUnauthorizedHandler } from './services/api'

const router = useRouter()
setUnauthorizedHandler(() => {
  router.replace('/login')
})
</script>
