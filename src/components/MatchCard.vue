<template>
  <view class="match-card" @click="handleClick">
    <!-- 第一行：对阵排布 -->
    <view class="match-row">
      <text class="player-name" :class="leftClass">{{ leftName }}</text>
      <text class="vs">vs</text>
      <text class="player-name" :class="rightClass">{{ rightName }}</text>
    </view>

    <!-- 第二行：状态 / 比分 -->
    <view class="score-row">
      <view v-if="status === 1" class="live-dot" />
      <text class="score-text">{{ displayText }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  matchId: { type: String, required: true },
  leftName: { type: String, default: '待定' },
  rightName: { type: String, default: '待定' },
  status: { type: Number, default: 0 },
  scoreText: { type: String, default: '待开赛' },
  winnerSide: { type: String, default: '' },
  retiredSide: { type: String, default: '' },
})

const emit = defineEmits(['click-card'])

const isEnded = computed(() => props.status === 2)

const leftClass = computed(() => {
  if (!isEnded.value || !props.winnerSide) return ''
  return props.winnerSide === 'left' ? 'winner' : 'loser'
})

const rightClass = computed(() => {
  if (!isEnded.value || !props.winnerSide) return ''
  return props.winnerSide === 'right' ? 'winner' : 'loser'
})

const displayText = computed(() => {
  if (props.retiredSide === 'left') {
    const score = props.scoreText === '待开赛' ? '0:0' : props.scoreText
    return `(退赛) ${score}`
  }
  if (props.retiredSide === 'right') {
    const score = props.scoreText === '待开赛' ? '0:0' : props.scoreText
    return `${score} (退赛)`
  }
  return props.scoreText
})

function isMissing(name) {
  return name === '待定' || name === 'TBD'
}

function handleClick() {
  // 已完赛：禁止
  if (props.status === 2) return

  // status=0 但选手未就位或无效状态：禁止
  if (props.status === 0) {
    if (isMissing(props.leftName) || isMissing(props.rightName)) return
    if (props.scoreText === '等待选手' || props.scoreText === '轮空晋级') return
  }

  // status=0 双方就位 或 status=1 进行中：允许
  emit('click-card', props.matchId)
}
</script>

<style scoped>
.match-card {
  width: 320rpx;
  background: #2a3a4a;
  border-radius: 14rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.2);
  padding: 20rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  box-sizing: border-box;
  flex-shrink: 0;
}

.match-card:active {
  opacity: 0.7;
}

/* ─── 第一行：对阵 ─── */
.match-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
}

.player-name {
  flex: 1;
  font-size: 26rpx;
  font-weight: 500;
  color: #ffffff;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-name:first-child {
  text-align: right;
}

.player-name:last-child {
  text-align: left;
}

.player-name.winner {
  color: #ff8c00;
  font-weight: 700;
}

.player-name.loser {
  color: #888888;
}

.vs {
  font-size: 20rpx;
  color: #666666;
  flex-shrink: 0;
}

/* ─── 第二行：状态 / 比分 ─── */
.score-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.score-text {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
}

/* ─── 进行中绿色呼吸圆点 ─── */
.live-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #4cd964;
  animation: breathe 1.6s ease-in-out infinite;
}

@keyframes breathe {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.25; }
}
</style>
