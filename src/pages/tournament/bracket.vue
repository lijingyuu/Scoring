<template>
  <view class="page" :style="pageStyle">
    <view class="state-layer" v-if="loading">
      <text class="state-text">正在获取赛程...</text>
    </view>

    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">网络请求失败</text>
      <button class="retry-btn" @click="fetchData(tournamentId)">重新加载</button>
    </view>

    <template v-else>
      <view class="header">
        <view class="header-top">
          <view class="header-left">
            <text class="back-btn safe-back-btn" @click="goBack">返回</text>
            <text class="header-title">{{ info?.name || '赛程' }}</text>
          </view>
          <text class="header-status" :class="'status-' + info?.status">{{ statusLabels[info?.status] ?? '' }}</text>
        </view>
        <text class="header-location" v-if="info?.location">{{ info.location }}</text>
        <text class="header-rule">{{ ruleText }}</text>
        <text class="header-hint" v-if="!matches?.length">暂无比赛数据</text>
      </view>

      <scroll-view
        v-if="matches?.length"
        class="bracket-scroll-view"
        scroll-x="true"
        scroll-y="true"
      >
        <view class="canvas-container">
          <view class="rounds-wrapper">
            <view
              v-for="round in groupedMatches"
              :key="round.roundNum"
              class="round-column"
              :style="{ height: columnHeight }"
            >
              <view class="round-title">第 {{ round.roundNum }} 轮</view>

              <view class="cards-stack">
                <view
                  v-for="match in round.matches"
                  :key="match.id"
                  class="match-node"
                >
                  <MatchCard
                    :match-id="match.id"
                    :left-name="getPlayerName(match.leftPlayerId)"
                    :right-name="getPlayerName(match.rightPlayerId)"
                    :status="match.status ?? 0"
                    :score-text="getScoreText(match)"
                    :winner-side="getWinnerSide(match)"
                    :retired-side="match.retiredSide ?? ''"
                    :is-team-match="isTeamTournament && !isVolleyball"
                    @click-card="() => handleMatchClick(match)"
                  />
                </view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import MatchCard from '@/components/MatchCard.vue'
import { buildLineupUrl, buildMatchQuery } from '@/pages/volleyball/match-state'

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
function buildBasePortraitPageStyle(extraTopRpx = 0) {
  let safeTopPx = 0
  try {
    const info = typeof uni.getWindowInfo === "function"
      ? uni.getWindowInfo()
      : uni.getSystemInfoSync()
    const safeInsetTop = Number(info?.safeAreaInsets?.top)
    if (Number.isFinite(safeInsetTop) && safeInsetTop > 0) {
      safeTopPx = safeInsetTop
    } else {
      const statusBarHeight = Number(info?.statusBarHeight)
      if (Number.isFinite(statusBarHeight) && statusBarHeight > 0) {
        safeTopPx = statusBarHeight
      }
    }
  } catch (_) {
    // noop
  }

  let extraTopPx = 0
  if (extraTopRpx > 0) {
    extraTopPx = Math.round(extraTopRpx / 2)
    try {
      if (typeof uni?.upx2px === "function") {
        const px = Number(uni.upx2px(extraTopRpx))
        if (Number.isFinite(px) && px > 0) {
          extraTopPx = px
        }
      }
    } catch (_) {
      // noop
    }
  }

  return {
    boxSizing: "border-box",
    paddingTop: `${safeTopPx + extraTopPx}px`,
  }
}

const pageStyle = buildBasePortraitPageStyle()

const statusLabels = {
  0: '未开始',
  1: '进行中',
  2: '已结束',
}

const loading = ref(true)
const isError = ref(false)
const tournamentId = ref('')
const info = ref({})
const players = ref([])
const matches = ref([])

const playerMap = computed(() => {
  const map = new Map()
  for (const player of Array.isArray(players.value) ? players.value : []) {
    if (player?.id) {
      map.set(player.id, player.name)
    }
  }
  return map
})

const isVolleyball = computed(() => Number(info.value?.sportType || 0) === 1)
const isTeamTournament = computed(() => Number(info.value?.participantType || 0) === 1)
const isRelayTournament = computed(() => Number(info.value?.teamMatchTemplate || 0) === 2)
const canOperateMatches = computed(() => info.value?.canOperateMatches === true)
const isArchived = computed(() => info.value?.archived === true)

const rule = computed(() => ({
  bestOf: Number(info.value?.bestOf || 3),
  gamesToWin: Number(info.value?.gamesToWin || 2),
  pointsToWin: Number(info.value?.pointsToWin || 21),
  decidingPointsToWin: info.value?.decidingPointsToWin == null ? null : Number(info.value.decidingPointsToWin),
  enableDeuce: info.value?.enableDeuce !== false,
  capPoint: Number(info.value?.capPoint || 30),
}))

const ruleText = computed(() => {
  const roundRuleText = info.value?.roundRuleEnabled === true ? ' / 分轮规则已启用' : ''
  if (isVolleyball.value) {
    const matchText = rule.value.bestOf === 5 ? '五局三胜' : '三局两胜'
    const decidingPoints = rule.value.decidingPointsToWin || 15
    return `排球 / ${matchText} / 常规局${rule.value.pointsToWin}分 / 决胜局${decidingPoints}分 / 领先2分${roundRuleText}`
  }
  const matchText = rule.value.bestOf === 5
    ? '五局三胜'
    : rule.value.bestOf === 1
      ? '一局定胜负'
      : '三局两胜'
  const deuceText = rule.value.enableDeuce ? `${rule.value.capPoint}分封顶` : '无追分'
  return `${matchText} / ${rule.value.pointsToWin}分 / ${deuceText}${roundRuleText}`
})

const groupedMatches = computed(() => {
  const groups = {}
  for (const match of Array.isArray(matches.value) ? matches.value : []) {
    if (!match || match.roundNum == null) continue
    if (!groups[match.roundNum]) {
      groups[match.roundNum] = []
    }
    groups[match.roundNum].push(match)
  }
  return Object.keys(groups)
    .sort((a, b) => Number(a) - Number(b))
    .map((roundNum) => ({
      roundNum: Number(roundNum),
      matches: groups[roundNum],
    }))
})

const columnHeight = computed(() => {
  if (!groupedMatches.value.length) return '2000rpx'
  const maxCount = Math.max(...groupedMatches.value.map((group) => group.matches.length))
  return `${maxCount * 150 + 80}rpx`
})

function getPlayerName(id) {
  if (!id) return '待定'
  return playerMap.value.get(id) || '待定'
}

function getScoreText(match) {
  if (!match) return '待开赛'

  if (match.status === 2) {
    const hasLeft = !!match.leftPlayerId
    const hasRight = !!match.rightPlayerId
    if (hasLeft !== hasRight) return '轮空晋级'
    return match.scoreDisplay || '已完赛'
  }

  if (match.status === 1) {
    return match.scoreDisplay || '进行中'
  }

  if (match.leftPlayerId && match.rightPlayerId) {
    return '待开赛'
  }
  return '等待选手'
}

function getWinnerSide(match) {
  if (!match?.winnerId) return ''
  if (match.winnerId === match.leftPlayerId) return 'left'
  if (match.winnerId === match.rightPlayerId) return 'right'
  return ''
}

function hasCompleteParticipants(match) {
  return !!match?.leftPlayerId && !!match?.rightPlayerId
}

function isSettledMatch(match) {
  const status = Number(match?.status || 0)
  return status === 2 || status === 3
}

function buildMatchParams(match) {
  const matchRule = ruleForMatch(match)
  return {
    tournamentId: tournamentId.value,
    matchId: match.id,
    leftName: getPlayerName(match.leftPlayerId),
    rightName: getPlayerName(match.rightPlayerId),
    bestOf: matchRule.bestOf,
    gamesToWin: matchRule.gamesToWin,
    pointsToWin: matchRule.pointsToWin,
    decidingPointsToWin: matchRule.decidingPointsToWin || '',
    enableDeuce: matchRule.enableDeuce ? '1' : '0',
    capPoint: matchRule.capPoint,
  }
}

function ruleForMatch(match) {
  if (info.value?.roundRuleEnabled === true && Array.isArray(info.value?.roundRules)) {
    const stageType = Number(match?.stageType ?? 1)
    const roundNum = stageType === 0 ? 0 : Number(match?.roundNum || 1)
    const found = info.value.roundRules.find((item) => Number(item.stageType) === stageType && Number(item.roundNum) === roundNum)
    if (found) {
      return {
        bestOf: Number(found.bestOf || rule.value.bestOf),
        gamesToWin: Number(found.gamesToWin || rule.value.gamesToWin),
        pointsToWin: Number(found.pointsToWin || rule.value.pointsToWin),
        decidingPointsToWin: found.decidingPointsToWin == null ? null : Number(found.decidingPointsToWin),
        enableDeuce: found.enableDeuce !== false,
        capPoint: Number(found.capPoint || rule.value.capPoint),
      }
    }
  }
  return rule.value
}

function goBack() {
  uni.navigateBack()
}

function buildTeamMatchUrl(match) {
  return (isRelayTournament.value ? '/pages/tournament/team-lineup' : '/pages/tournament/team-match')
    + '?tournamentId='
    + encodeURIComponent(tournamentId.value)
    + '&matchId='
    + encodeURIComponent(match.id)
}

function openScoreboard(match) {
  const params = buildMatchParams(match)
  const query = buildMatchQuery(params)
  const page = isVolleyball.value
    ? buildLineupUrl(params)
    : isTeamTournament.value
      ? buildTeamMatchUrl(match)
      : '/pages/scoreboard/index?' + query
  uni.navigateTo({ url: page })
}

function openMatchRecord(match) {
  uni.navigateTo({
    url: '/pages/volleyball/record?tournamentId=' + encodeURIComponent(tournamentId.value) + '&matchId=' + encodeURIComponent(match.id),
  })
}

function guardOperateMatch() {
  if (canOperateMatches.value) return true
  uni.showToast({ title: '请先录入裁判身份后再开始执裁', icon: 'none' })
  return false
}

function handleMatchClick(match) {
  if (!match?.id) return

  if (!hasCompleteParticipants(match)) {
    uni.showToast({ title: '对阵未完整，不能执裁', icon: 'none' })
    return
  }

  if (isArchived.value) {
    if (isVolleyball.value && Number(match.status || 0) === 2) {
      openMatchRecord(match)
      return
    }
    uni.showToast({ title: '已归档，只读查看', icon: 'none' })
    return
  }

  if (isSettledMatch(match) && !isVolleyball.value) {
    uni.showToast({ title: '比赛已结束，不能执裁', icon: 'none' })
    return
  }

  if (isVolleyball.value && Number(match.status || 0) === 2) {
    openMatchRecord(match)
    return
  }

  if (!guardOperateMatch()) return

  openScoreboard(match)
}

function fetchData(tid) {
  if (!tid) return
  loading.value = true
  isError.value = false

  request('/api/v1/tournaments/' + tid + '/bracket', { method: 'GET' })
    .then((data) => {
      if (!data) {
        isError.value = true
        return
      }
      info.value = {
        id: data.id,
        name: data.name,
        location: data.location,
        status: data.status,
        archived: data.archived,
        sportType: data.sportType,
        participantType: data.participantType,
        teamMatchTemplate: data.teamMatchTemplate,
        tournamentType: data.tournamentType,
        knockoutRounds: data.knockoutRounds,
        bestOf: data.bestOf,
        gamesToWin: data.gamesToWin,
        pointsToWin: data.pointsToWin,
        decidingPointsToWin: data.decidingPointsToWin,
        enableDeuce: data.enableDeuce,
        capPoint: data.capPoint,
        roundRuleEnabled: data.roundRuleEnabled,
        roundRules: Array.isArray(data.roundRules) ? data.roundRules : [],
        refereeGranted: data.refereeGranted,
        canOperateMatches: data.canOperateMatches,
      }
      players.value = Array.isArray(data.players) ? data.players : []
      matches.value = Array.isArray(data.matches) ? data.matches : []
    })
    .catch(() => {
      isError.value = true
    })
    .finally(() => {
      loading.value = false
    })
}

onLoad((options) => {
  const tid = options?.id || ''
  if (!tid) {
    uni.showToast({ title: '缺少赛事ID', icon: 'none' })
    loading.value = false
    isError.value = true
    return
  }
  tournamentId.value = tid
  fetchData(tid)
})

onShow(() => {
  if (tournamentId.value) {
    fetchData(tournamentId.value)
  }
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background: #1a2a3a;
  color: #ffffff;
  display: flex;
  flex-direction: column;
}

.state-layer {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 40rpx;
}

.state-text {
  font-size: 30rpx;
  color: rgba(255, 255, 255, 0.6);
}

.state-error {
  color: #ff8c00;
}

.retry-btn {
  width: 280rpx;
  height: 72rpx;
  line-height: 72rpx;
  border-radius: 12rpx;
  border: none;
  background: #ff8c00;
  color: #1a2a3a;
  font-size: 28rpx;
  font-weight: 700;
}

.retry-btn::after {
  border: none;
}

.header {
  padding: 0 28rpx 16rpx;
  flex-shrink: 0;
}

.header-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
  min-width: 0;
}

.back-btn {
  font-size: 26rpx;
  color: #ff8c00;
  padding: 6rpx 12rpx;
  flex-shrink: 0;
}

.header-title {
  font-size: 34rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.header-status {
  font-size: 22rpx;
  padding: 4rpx 14rpx;
  border-radius: 8rpx;
  flex-shrink: 0;
}

.status-0 {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.7);
}

.status-1 {
  background: rgba(255, 140, 0, 0.2);
  color: #ff8c00;
}

.status-2 {
  background: rgba(76, 217, 100, 0.15);
  color: #4cd964;
}

.header-location,
.header-rule {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 6rpx;
}

.header-hint {
  display: block;
  margin-top: 10rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.35);
}

.bracket-scroll-view {
  flex: 1;
  width: 100%;
  padding: 0 28rpx 28rpx;
  box-sizing: border-box;
}

.canvas-container {
  display: inline-block;
  min-width: max-content;
}

.rounds-wrapper {
  display: flex;
  flex-direction: row;
  gap: 80rpx;
  align-items: stretch;
}

.round-column {
  min-width: 320rpx;
  display: flex;
  flex-direction: column;
  overflow: visible;
}

.round-title {
  font-size: 26rpx;
  font-weight: 700;
  color: #ff8c00;
  padding-bottom: 8rpx;
  border-bottom: 2rpx solid rgba(255, 140, 0, 0.3);
  margin-bottom: 8rpx;
  flex-shrink: 0;
}

.cards-stack {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  overflow: visible;
}

.match-node {
  position: relative;
  overflow: visible;
  flex-shrink: 0;
}

.match-node::after {
  content: '';
  position: absolute;
  right: -80rpx;
  top: 50%;
  width: 80rpx;
  height: 0;
  border-top: 2rpx solid rgba(255, 255, 255, 0.18);
  transform: translateY(-50%);
  pointer-events: none;
}

.round-column:last-child .match-node::after {
  display: none;
}
</style>

