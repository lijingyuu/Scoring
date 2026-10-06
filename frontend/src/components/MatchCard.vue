<template>
  <view
    class="match-card"
    :class="{
      'doubles-card': isDoublesMatch,
      'result-card': showResultRow,
      'team-result-card': showTeamResult,
      'pending-card': isPendingMatch,
    }"
    @click="handleClick"
  >
    <!-- 双打比赛模式 -->
    <view v-if="isDoublesMatch" class="doubles-row">
      <view class="doubles-side left-side">
        <template v-if="isLeftDoubles">
          <text class="doubles-player-name" :class="leftClass">{{ leftPlayerNames[0] }}</text>
          <text class="doubles-player-name" :class="leftClass">{{ leftPlayerNames[1] }}</text>
        </template>
        <template v-else>
          <text class="doubles-player-name doubles-player-single" :class="leftClass">{{ leftName }}</text>
        </template>
      </view>

      <view class="doubles-center">
        <text v-if="showResultRow" class="doubles-score">{{ resultScore }}</text>
        <text v-else class="doubles-vs">VS</text>
      </view>

      <view class="doubles-side right-side">
        <template v-if="isRightDoubles">
          <text class="doubles-player-name" :class="rightClass">{{ rightPlayerNames[0] }}</text>
          <text class="doubles-player-name" :class="rightClass">{{ rightPlayerNames[1] }}</text>
        </template>
        <template v-else>
          <text class="doubles-player-name doubles-player-single" :class="rightClass">{{ rightName }}</text>
        </template>
      </view>
    </view>

    <!-- 单打/其他模式：完赛状态 -->
    <view v-else-if="showResultRow" class="result-row">
      <text class="result-name left-name" :class="[leftClass, { 'team-name': showTeamResult }]">{{ leftName }}</text>
      <text class="result-score">{{ resultScore }}</text>
      <text class="result-name right-name" :class="[rightClass, { 'team-name': showTeamResult }]">{{ rightName }}</text>
    </view>

    <!-- 单打/其他模式：未完赛状态 -->
    <view v-else class="match-row">
      <text class="player-name" :class="leftClass">{{ leftName }}</text>
      <text class="vs">vs</text>
      <text class="player-name" :class="rightClass">{{ rightName }}</text>
    </view>

    <view v-if="showScoreRow" class="score-row" :class="{ 'pending-score-row': isPendingMatch }">
      <view v-if="status === 1" class="live-dot" />
      <view v-if="showFiveGameScoreLayout" class="score-text five-game-score-text">
        <text class="score-line">{{ firstFourScoreText }}</text>
        <text class="score-line deciding-score-line">{{ decidingGameScoreText }}</text>
      </view>
      <text v-else class="score-text" :class="{ 'pending-score-text': isPendingMatch }">{{ displayText }}</text>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { isDoublesMatch as checkIsDoublesMatch, splitDoublesName } from './match-card-doubles'

const props = defineProps({
  matchId: { type: String, required: true },
  leftName: { type: String, default: '待定' },
  rightName: { type: String, default: '待定' },
  status: { type: Number, default: 0 },
  scoreText: { type: String, default: '待开赛' },
  winnerSide: { type: String, default: '' },
  retiredSide: { type: String, default: '' },
  isTeamMatch: { type: Boolean, default: false },
  isVolleyball: { type: Boolean, default: false },
})

const emit = defineEmits(['click-card'])

const leftPlayerNames = computed(() => splitDoublesName(props.leftName))
const rightPlayerNames = computed(() => splitDoublesName(props.rightName))
const isLeftDoubles = computed(() => leftPlayerNames.value.length > 1)
const isRightDoubles = computed(() => rightPlayerNames.value.length > 1)
const isDoublesMatch = computed(() => checkIsDoublesMatch({
  leftName: props.leftName,
  rightName: props.rightName,
  isTeamMatch: props.isTeamMatch,
  isVolleyball: props.isVolleyball,
}))

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
))
const showScoreRow = computed(() => !showTeamResult.value && (!showIndividualResult.value || hasIndividualDetailScore.value))
const showFiveGameScoreLayout = computed(() => (
  props.isVolleyball
  && showIndividualResult.value
  && !props.retiredSide
  && scoreParts.value.length === 5
))
const firstFourScoreText = computed(() => scoreParts.value.slice(0, 4).join(','))
const decidingGameScoreText = computed(() => scoreParts.value[4] || '')

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
  return name === '待定' || name === 'TBD' || name === '轮空位'
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

/* 双打比赛卡片样式 */
.doubles-card {
  padding-top: 14rpx;
  padding-bottom: 14rpx;
  padding-left: 14rpx;
  padding-right: 14rpx;
  gap: 10rpx;
  justify-content: center;
}

.doubles-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.doubles-side {
  flex: 1 1 0;
  min-width: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 4rpx;
}

.doubles-player-name {
  width: 100%;
  font-size: 24rpx;
  line-height: 32rpx;
  font-weight: 500;
  color: #ffffff;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  box-sizing: border-box;
  padding: 0 2rpx;
}

.doubles-player-single {
  line-height: 36rpx;
}

.doubles-player-name.winner {
  color: #ffb347;
  font-weight: 700;
}

.doubles-player-name.loser {
  color: #888888;
}

.doubles-center {
  flex: 0 0 52rpx;
  width: 52rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.doubles-vs {
  font-size: 20rpx;
  font-weight: 700;
  color: #ffffff;
  letter-spacing: 1rpx;
}

.doubles-score {
  font-size: 22rpx;
  font-weight: 700;
  color: #ffffff;
  line-height: 32rpx;
  text-align: center;
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

.five-game-score-text {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 2rpx;
  line-height: 28rpx;
  text-align: center;
}

.score-line {
  width: 100%;
  white-space: nowrap;
  text-align: center;
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
