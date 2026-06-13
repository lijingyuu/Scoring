<template>
  <view class="tabbar-shell">
    <view class="tabbar" :class="{ 'is-pad': isPad }">
      <view
        v-for="item in tabs"
        :key="item.pagePath"
        class="tab-item"
        :class="{ active: selected === item.pagePath }"
        @click="switchTo(item.pagePath)"
      >
        <text class="tab-label">{{ item.text }}</text>
      </view>
    </view>
  </view>
</template>

<script>
const TABBAR_HEIGHT_PX = 64
const PAD_MIN_EDGE = 720

export default {
  data() {
    return {
      selected: 'pages/index/index',
      isPad: false,
      tabs: [
        { pagePath: 'pages/index/index', text: '赛事大厅' },
        { pagePath: 'pages/mine/index', text: '我的' },
      ],
    }
  },
  mounted() {
    this.syncSelected()
    this.syncDeviceType()
  },
  methods: {
    syncSelected() {
      const page = getCurrentPages().slice(-1)[0]
      const route = page?.route || 'pages/index/index'
      this.selected = route
    },
    syncDeviceType() {
      const { windowWidth = 0, windowHeight = 0 } = uni.getSystemInfoSync()
      this.isPad = Math.min(windowWidth, windowHeight) >= PAD_MIN_EDGE
    },
    switchTo(pagePath) {
      if (this.selected === pagePath) return
      this.selected = pagePath
      uni.switchTab({
        url: '/' + pagePath,
      })
    },
  },
}
</script>

<style scoped>
.tabbar-shell {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  padding: 0 0 env(safe-area-inset-bottom);
  background: #162434;
  box-shadow: 0 -8px 24px rgba(0, 0, 0, 0.18);
}

.tabbar {
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: space-around;
  border-top: 1px solid rgba(255, 255, 255, 0.08);
}

.tabbar.is-pad {
  height: 82px;
}

.tab-item {
  flex: 1;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #7c8a99;
}

.tab-label {
  font-size: 16px;
  font-weight: 600;
  line-height: 1;
}

.tabbar.is-pad .tab-label {
  font-size: 24px;
  font-weight: 800;
}

.tab-item.active {
  color: #ff8c00;
}
</style>
