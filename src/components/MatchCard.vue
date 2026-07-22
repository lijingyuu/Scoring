<template>
  <view class="match-card" :class="{ 'result-card': showResultRow, 'team-result-card': showTeamResult, 'pending-card': isPendingMatch }" @click="handleClick">
    <view v-if="showResultRow" class="result-row">
      <text class="result-name left-name" :class="[leftClass, { 'team-name': showTeamResult }]">{{ leftName }}</text>
      <text class="result-score">{{ resultScore }}</text>
      <text class="result-name right-name" :class="[rightClass, { 'team-name': showTeamResult }]">{{ rightName }}</text>
    </view>

    <view v-else class="match-row">
      <text class="player-name" :class="leftClass">{{ leftName }}</text>
      <text class="vs">vs</text>
      <text class="player-name" :class="rightClass">{{ rightName }}</text>
    </view>

    <view v-if="showScoreRow" class="score-row" :class="{ 'pending-score-row': isPendingMatch }">
      <view v-if="status === 1" class="live-dot" />
      <text class="score-text" :class="{ 'pending-score-text': isPendingMatch }">{{ displayText }}</text>
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
  isTeamMatch: { type: Boolean, default: false },
})

const emit = defineEmits(['click-card'])

const isEnded = computed(() => props.status === 2)
const isByeMatch = computed(() => props.scoreText === '轮空晋级')
const isPendingMatch = computed(() => props.status === 0 && props.scoreText === '待开始')
const showTeamResult = computed(() => props.isTeamMatch && isEnded.value && !isByeMatch.value)
const showIndividualResult = computed(() => !props.isTeamMatch && isEnded.value && !isByeMatch.value)
const showResultRow = computed(() => showTeamResult.value || showIndividualResult.value)
const scoreParts = computed(() => displayText.value.match(/\d+\s*:\s*\d+/g) || [])
const resultScore = computed(() => {
  if (!showIndividualResult.value) return scoreParts.value[0] || displayText.value
  if (!scoreParts.value.length) return displayText.value

  if (scoreParts.value.length === 1) {
    const [left, right] = scoreParts.value[0].split(':').map((item) => Number(item.trim()))
    if (Math.max(left, right) <= 5) return scoreParts.value[0]
    if (props.winnerSide === 'left') return '1:0'
    if (props.winnerSide === 'right') return '0:1'
    return left > right ? '1:0' : '0:1'
  }

  let leftWins = 0
  let rightWins = 0
  for (const part of scoreParts.value) {
    const [left, right] = part.split(':').map((item) => Number(item.trim()))
    if (left > right) leftWins += 1
    if (right > left) rightWins += 1
  }
  return `${leftWins}:${rightWins}`
})
const hasIndividualDetailScore = computed(() => (
  showIndividualResult.value
  && scoreParts.value.length > 0
  && displayText.value !== resultScore.value
))
const showScoreRow = computed(() => !showTeamResult.value && (!showIndividualResult.value || hasIndividualDetailScore.value))

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
  if (isMissing(props.leftName) || isMissing(props.rightName)) return
  if (props.scoreText === '等待选手' || props.scoreText === '轮空晋级') return
  emit('click-card', props.matchId)
}
</script>

<style scoped>
.match-card {
  width: 320rpx;
  min-height: 96rpx;
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
  color: #ffb347;
  font-weight: 700;
}

.player-name.loser {
  color: #888888;
}

.vs {
  font-size: 20rpx;
  color: #ffffff;
  flex-shrink: 0;
}

.result-row {
  min-height: 32rpx;
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.result-name {
  flex: 1 1 0;
  min-width: 0;
  max-height: 64rpx;
  line-height: 32rpx;
  font-size: 26rpx;
  font-weight: 500;
  color: #ffffff;
  overflow: hidden;
  white-space: normal;
  word-break: break-all;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.result-name.left-name {
  text-align: right;
}

.result-name.right-name {
  text-align: left;
}

.result-name.team-name {
  text-align: center;
  font-size: 24rpx;
}

.result-name.winner {
  color: #ffb347;
  font-weight: 700;
}

.result-score {
  flex: 0 0 48rpx;
  width: 48rpx;
  line-height: 32rpx;
  text-align: center;
  align-self: center;
  font-size: 21rpx;
  font-weight: 700;
  color: #ffffff;
}

.result-card {
  padding-top: 16rpx;
  padding-bottom: 16rpx;
  gap: 8rpx;
}

.team-result-card {
  justify-content: center;
}

.pending-card {
  padding-top: 16rpx;
  padding-bottom: 14rpx;
  gap: 8rpx;
}

.score-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.pending-score-row {
  margin-top: 2rpx;
}

.score-text {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.5);
}

.pending-score-text {
  font-size: 20rpx;
}

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
