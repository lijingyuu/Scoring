<template>
  <div
    class="schedule-match-card"
    :class="{
      'status-finished': isFinished,
      'status-live': isLive,
      'status-pending': isPending,
      'is-bye': isBye,
      'is-clickable': isClickable,
    }"
    :title="cardTooltip"
    @click="handleClick"
  >
    <div class="match-card-header">
      <span class="match-stage-label">{{ stageLabel }}</span>
      <span class="match-status-badge" :class="statusBadgeClass">
        <span v-if="isLive" class="live-dot" />
        {{ statusBadgeText }}
      </span>
    </div>

    <div class="match-card-body">
      <!-- 队伍/选手 A (左侧) -->
      <div
        class="participant-row"
        :class="{
          'is-winner': isFinished && winnerSide === 'left',
          'is-loser': isFinished && winnerSide === 'right',
        }"
      >
        <div class="participant-info">
          <template v-if="isLeftDoubles">
            <span class="doubles-name">{{ leftDoublesNames[0] }}</span>
            <span class="doubles-name sub">{{ leftDoublesNames[1] }}</span>
          </template>
          <span v-else class="single-name" :class="{ 'is-placeholder': !leftName || leftName === '待定' }">
            {{ leftName || '待定' }}
          </span>
        </div>
        <div class="participant-score">
          <span v-if="isFinished && leftScore != null" class="score-badge">{{ leftScore }}</span>
          <span v-else-if="winnerSide === 'left'" class="win-tag">胜</span>
        </div>
      </div>

      <!-- 分割线 -->
      <div class="match-divider" />

      <!-- 队伍/选手 B (右侧) -->
      <div
        class="participant-row"
        :class="{
          'is-winner': isFinished && winnerSide === 'right',
          'is-loser': isFinished && winnerSide === 'left',
        }"
      >
        <div class="participant-info">
          <template v-if="isRightDoubles">
            <span class="doubles-name">{{ rightDoublesNames[0] }}</span>
            <span class="doubles-name sub">{{ rightDoublesNames[1] }}</span>
          </template>
          <span v-else class="single-name" :class="{ 'is-placeholder': !rightName || rightName === '待定' }">
            {{ rightName || '待定' }}
          </span>
        </div>
        <div class="participant-score">
          <span v-if="isFinished && rightScore != null" class="score-badge">{{ rightScore }}</span>
          <span v-else-if="winnerSide === 'right'" class="win-tag">胜</span>
        </div>
      </div>
    </div>

    <!-- 底部比分或提示 -->
    <div v-if="scoreDetailsText" class="match-card-footer">
      <span class="score-display-text">{{ scoreDetailsText }}</span>
      <span v-if="isFinished" class="view-record-hint">查看战报 &rarr;</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { splitDoublesName } from '../../utils/matchFormatters'

const props = defineProps({
  match: { type: Object, required: true },
  leftName: { type: String, default: '待定' },
  rightName: { type: String, default: '待定' },
  isTeamMatch: { type: Boolean, default: false },
  isVolleyball: { type: Boolean, default: false },
})

const emit = defineEmits(['click-match'])

const status = computed(() => Number(props.match?.status ?? 0))
const isLive = computed(() => status.value === 1)
const isFinished = computed(() => status.value === 2)
const isPending = computed(() => status.value === 0)

const isBye = computed(() => {
  const score = props.match?.scoreDisplay || ''
  return score.includes('轮空') || (isFinished.value && (!props.match?.leftPlayerId || !props.match?.rightPlayerId))
})

const isClickable = computed(() => isFinished.value)

const winnerSide = computed(() => {
  if (props.match?.winnerSide) return props.match.winnerSide
  const leftId = String(props.match?.leftPlayerId ?? props.match?.left_player_id ?? '')
  const rightId = String(props.match?.rightPlayerId ?? props.match?.right_player_id ?? '')
  const winnerId = String(props.match?.winnerId ?? props.match?.winner_id ?? '')
  if (winnerId) {
    if (leftId && winnerId === leftId) return 'left'
    if (rightId && winnerId === rightId) return 'right'
  }
  const retired = props.match?.retiredSide ?? props.match?.retired_side
  if (retired === 'left') return 'right'
  if (retired === 'right') return 'left'
  if (props.match?.leftGameWins != null && props.match?.rightGameWins != null) {
    if (props.match.leftGameWins > props.match.rightGameWins) return 'left'
    if (props.match.rightGameWins > props.match.leftGameWins) return 'right'
  }
  return ''
})

const leftDoublesNames = computed(() => {
  if (props.isTeamMatch || props.isVolleyball) return []
  return splitDoublesName(props.leftName)
})
const isLeftDoubles = computed(() => leftDoublesNames.value.length > 1)

const rightDoublesNames = computed(() => {
  if (props.isTeamMatch || props.isVolleyball) return []
  return splitDoublesName(props.rightName)
})
const isRightDoubles = computed(() => rightDoublesNames.value.length > 1)

const stageLabel = computed(() => {
  if (Number(props.match?.matchRole) === 1) return '季军赛'
  if (props.match?.roundNum) return `第${props.match.roundNum}轮`
  return '比赛'
})

const statusBadgeText = computed(() => {
  if (isBye.value) return '轮空晋级'
  if (isLive.value) return '进行中'
  if (isFinished.value) return '已完赛'
  return '未开赛'
})

const statusBadgeClass = computed(() => {
  if (isBye.value) return 'badge-bye'
  if (isLive.value) return 'badge-live'
  if (isFinished.value) return 'badge-finished'
  return 'badge-pending'
})

const parsedGameWins = computed(() => {
  if (props.match?.leftGameWins != null && props.match?.rightGameWins != null) {
    return { left: props.match.leftGameWins, right: props.match.rightGameWins }
  }
  const score = props.match?.scoreDisplay || ''
  const parts = score.match(/\d+\s*:\s*\d+/g) || []
  if (parts.length > 1) {
    let leftWins = 0
    let rightWins = 0
    for (const p of parts) {
      const [l, r] = p.split(':').map((x) => Number(x.trim()))
      if (l > r) leftWins += 1
      if (r > l) rightWins += 1
    }
    return { left: leftWins, right: rightWins }
  }
  if (parts.length === 1) {
    const [l, r] = parts[0].split(':').map((x) => Number(x.trim()))
    if (l <= 5 && r <= 5) return { left: l, right: r }
  }
  return null
})

const leftScore = computed(() => {
  if (parsedGameWins.value) return parsedGameWins.value.left
  return null
})

const rightScore = computed(() => {
  if (parsedGameWins.value) return parsedGameWins.value.right
  return null
})

const scoreDetailsText = computed(() => {
  if (isBye.value) return '轮空晋级'
  if (!isFinished.value && !props.match?.scoreDisplay) return ''
  return String(props.match?.scoreDisplay || '').replace(/,/g, '  ')
})

const cardTooltip = computed(() => {
  if (isFinished.value) return '点击查看赛后战报明细'
  if (isLive.value) return '比赛进行中（现场执裁在微信小程序端进行）'
  return '比赛尚未开始'
})

function handleClick() {
  emit('click-match', props.match)
}
</script>
