<template>
  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-if="ctx.loading">
    <text class="state-text">正在加载排球记分牌...</text>
  </view>

  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-else-if="ctx.isError">
    <text class="state-text state-error">{{ ctx.errorText }}</text>
    <button class="retry-btn" @click="ctx.loadMatch">重新加载</button>
  </view>

  <ScoreboardPhone v-else-if="!ctx.isTablet" :ctx="ctx" />
  <ScoreboardPad v-else :ctx="ctx" />
</template>

<script setup>
import { reactive } from 'vue'
import { useScoreboard } from './composables/useScoreboard'
import ScoreboardPhone from './components/ScoreboardPhone.vue'
import ScoreboardPad from './components/ScoreboardPad.vue'

const ctx = reactive(useScoreboard())
// reactive() auto-unwraps all refs so child templates can do
// ctx.captainCandidateMemberId = value (no .value needed)
</script>

<style scoped>
.state-page {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(180deg, var(--theme-base) 0%, var(--theme-base-deep) 100%);
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: clamp(12px, 2vmin, 22px);
  overflow: hidden;
  display: flex;
}

.state-page.landscape-preview {
  position: fixed;
  top: 0;
  left: 0;
  width: 1280px;
  height: 720px;
  overflow: hidden;
}

.state-text {
  color: rgba(var(--text-strong-rgb), 0.76);
  font-size: clamp(14px, 1.8vmin, 24px);
}

.state-error {
  color: var(--theme-accent);
}

.retry-btn {
  width: clamp(150px, 26vmin, 240px);
  height: clamp(42px, 6vmin, 68px);
  line-height: clamp(42px, 6vmin, 68px);
  border-radius: clamp(10px, 1.3vmin, 16px);
  border: none;
  background: var(--theme-accent);
  color: var(--theme-accent-ink);
  font-size: clamp(14px, 1.6vmin, 22px);
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}
</style>
