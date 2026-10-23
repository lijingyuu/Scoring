<template>
  <!-- 执裁锁全屏遮罩：锁不可得（denied）/中途丢失（lost）统一由此呈现，取代旧只读横幅 -->
  <LockMask
    v-if="lockGuard.maskMode.value"
    :title="lockGuard.maskTitle.value"
    :message="lockGuard.maskMessage.value"
    :score="lockGuard.maskScore.value"
    :retry-label="lockGuard.maskMode.value === 'denied' ? '重试获取' : '重新获取执裁权'"
    :retry-disabled="lockGuard.retryDisabled.value"
    :retrying="lockGuard.retrying.value"
    @retry="lockGuard.retry"
    @back="lockGuard.back" />

  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-if="ctx.loading">
    <text class="state-text">正在加载排球记分牌...</text>
  </view>

  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-else-if="ctx.isError">
    <text class="state-text state-error">{{ ctx.errorText }}</text>
    <button class="retry-btn" @click="ctx.loadMatch">重新加载</button>
  </view>

  <!-- 拒绝进入时的兜底底图：交互全部由上方 LockMask 承担（重试获取 / 返回） -->
  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-else-if="ctx.lockDenied">
    <text class="state-text state-error">{{ ctx.lockDeniedMessage }}</text>
  </view>

  <view class="state-page" :class="[ctx.pageClassNames, { 'landscape-preview': ctx.useLandscapePreview }]" :style="ctx.rootPageStyle" v-else-if="ctx.transitionSyncError">
    <text class="state-text state-error">{{ ctx.transitionSyncError }}</text>
    <button class="retry-btn" @click="ctx.retryNextLineup">重试进入下一局</button>
  </view>

  <ScoreboardPhone v-else-if="!ctx.isTablet" :ctx="ctx" />
  <ScoreboardPad v-else :ctx="ctx" />
</template>

<script setup>
import { reactive } from 'vue'
import { useScoreboard } from './composables/useScoreboard'
import LockMask from '@/components/LockMask.vue'
import ScoreboardPhone from './components/ScoreboardPhone.vue'
import ScoreboardPad from './components/ScoreboardPad.vue'

const scoreboard = useScoreboard()
const ctx = reactive(scoreboard)
// reactive() auto-unwraps all refs so child templates can do
// ctx.captainCandidateMemberId = value (no .value needed)
// 执裁锁遮罩走 lockGuard 的原始 ref（reactive 会解包嵌套 ref），模板里需显式取 .value
const lockGuard = scoreboard.lockGuard
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
