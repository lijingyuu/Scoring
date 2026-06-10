<template>
  <view class="page">
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
            <text class="back-btn" @click="goBack">返回</text>
            <text class="header-title">{{ info.name || '小组赛' }}</text>
          </view>
          <text class="header-status" :class="'status-' + info.status">{{ statusLabels[info.status] ?? '' }}</text>
        </view>
        <text class="header-line" v-if="info.location">{{ info.location }}</text>
        <text class="header-line">{{ ruleText }}</text>
        <text class="header-line">{{ info.knockoutSlots || 0 }}强淘汰赛 / 每组出线{{ info.qualifiersPerGroup || 2 }}{{ isVolleyball ? '队' : '人' }}</text>

        <view class="tabs">
          <view class="tab" :class="{ active: activeTab === 'group' }" @click="activeTab = 'group'">小组赛</view>
          <view class="tab" :class="{ active: activeTab === 'knockout' }" @click="activeTab = 'knockout'">淘汰赛</view>
        </view>
      </view>

      <scroll-view class="group-scroll" scroll-y v-if="activeTab === 'group'">
        <view class="group-section" v-for="group in groups" :key="group.groupNo">
          <view class="group-title">{{ groupName(group.groupNo) }}</view>

          <view class="standing-table" v-if="getStandings(group.groupNo).length">
            <view class="standing-row standing-head">
              <text>排名</text>
              <text>{{ isVolleyball ? '队伍' : '选手' }}</text>
              <text>胜负</text>
              <text>净局</text>
              <text>净分</text>
            </view>
            <view class="standing-row" v-for="standing in getStandings(group.groupNo)" :key="standing.playerId">
              <text>{{ standing.rank }}</text>
              <text>{{ standing.playerName }}{{ standing.qualified ? ' 出线' : '' }}{{ standing.tieUnresolved ? ' 待定' : '' }}</text>
              <text>{{ standing.matchWins }}-{{ standing.matchLosses }}</text>
              <text>{{ standing.netGames }}</text>
              <text>{{ standing.netPoints }}</text>
            </view>
          </view>

          <view class="player-row">
            <text class="player-pill" v-for="player in group.players" :key="player.id">
              {{ player.name }}{{ player.seedRank ? ' #' + player.seedRank : '' }}
            </text>
          </view>

          <view class="round-block" v-for="round in groupRounds(group.matches)" :key="group.groupNo + '-' + round.roundNum">
            <view class="round-title">第{{ round.roundNum }}轮</view>
            <view class="match-list">
              <MatchCard
                v-for="match in round.matches"
                :key="match.id"
                :match-id="match.id"
                :left-name="getPlayerName(match.leftPlayerId)"
                :right-name="getPlayerName(match.rightPlayerId)"
                :status="match.status ?? 0"
                :score-text="getScoreText(match)"
                :winner-side="getWinnerSide(match)"
                :retired-side="match.retiredSide ?? ''"
                @click-card="() => handleGroupMatchClick(match)"
              />
            </view>
          </view>
        </view>
      </scroll-view>

      <view class="knockout-panel" v-else>
        <view class="knockout-actions" v-if="!info.knockoutGenerated">
          <text class="knockout-hint" v-if="!standings.allGroupMatchesFinished">小组赛全部完成后才能生成淘汰赛</text>
          <text class="knockout-hint" v-else-if="standings.hasUnresolvedTie">存在无法自动判定的同分，需要人工处理后再生成</text>
          <button class="generate-btn" :disabled="!canGenerateKnockout" @click="generateKnockout">生成淘汰赛</button>
        </view>

        <scroll-view class="bracket-scroll-view" scroll-x scroll-y v-if="knockoutMatches.length">
          <view class="canvas-container">
            <view class="rounds-wrapper">
              <view
                class="round-column"
                v-for="round in groupedKnockoutMatches"
                :key="round.roundNum"
                :style="{ height: knockoutColumnHeight }"
              >
                <view class="round-title">第{{ round.roundNum }}轮</view>
                <view class="cards-stack">
                  <view class="match-node" v-for="match in round.matches" :key="match.id">
                    <MatchCard
                      :match-id="match.id"
                      :left-name="getPlayerName(match.leftPlayerId)"
                      :right-name="getPlayerName(match.rightPlayerId)"
                      :status="match.status ?? 0"
                      :score-text="getScoreText(match)"
                      :winner-side="getWinnerSide(match)"
                      :retired-side="match.retiredSide ?? ''"
                      @click-card="() => handleKnockoutMatchClick(match)"
                    />
                  </view>
                </view>
              </view>
            </view>
          </view>
        </scroll-view>

        <text class="knockout-hint" v-else-if="info.knockoutGenerated">淘汰赛数据加载中</text>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import MatchCard from '@/components/MatchCard.vue'
import { buildLineupUrl, buildMatchQuery } from '@/pages/volleyball/match-state'

const statusLabels = { 0: '未开始', 1: '进行中', 2: '已结束' }

const loading = ref(true)
const isError = ref(false)
const tournamentId = ref('')
const info = ref({})
const groups = ref([])
const standings = ref({})
const knockoutPlayers = ref([])
const knockoutMatches = ref([])
const activeTab = ref('group')

const isVolleyball = computed(() => Number(info.value?.sportType || 0) === 1)

const players = computed(() => {
  const groupPlayers = groups.value.flatMap((group) => (Array.isArray(group.players) ? group.players : []))
  return knockoutPlayers.value.length ? knockoutPlayers.value : groupPlayers
})

const playerMap = computed(() => {
  const map = new Map()
  for (const player of players.value) {
    if (player?.id) map.set(player.id, player.name)
  }
  return map
})

const rule = computed(() => ({
  bestOf: Number(info.value.bestOf || 3),
  gamesToWin: Number(info.value.gamesToWin || 2),
  pointsToWin: Number(info.value.pointsToWin || 21),
  enableDeuce: info.value.enableDeuce !== false,
  capPoint: Number(info.value.capPoint || 30),
}))

const ruleText = computed(() => {
  const matchText = rule.value.bestOf === 5 ? '五局三胜' : rule.value.bestOf === 1 ? '一局定胜负' : '三局两胜'
  if (isVolleyball.value) {
    return `${matchText} / 常规局25分 / 末局15分 / 领先2分`
  }
  const deuce = rule.value.enableDeuce ? `${rule.value.capPoint}分封顶` : '无追分'
  return `${matchText} / ${rule.value.pointsToWin}分 / ${deuce}`
})

const canGenerateKnockout = computed(() => (
  standings.value.allGroupMatchesFinished === true
  && standings.value.hasUnresolvedTie !== true
  && info.value.knockoutGenerated !== true
))

const groupedKnockoutMatches = computed(() => groupMatchesByRound(knockoutMatches.value))

const knockoutColumnHeight = computed(() => {
  if (!groupedKnockoutMatches.value.length) return '2000rpx'
  const maxCount = Math.max(...groupedKnockoutMatches.value.map((group) => group.matches.length))
  return maxCount * 150 + 80 + 'rpx'
})

function groupName(groupNo) {
  return String.fromCharCode(64 + Number(groupNo || 1)) + '组'
}

function getStandings(groupNo) {
  const standingGroups = Array.isArray(standings.value.groups) ? standings.value.groups : []
  return standingGroups.find((group) => Number(group.groupNo) === Number(groupNo))?.standings || []
}

function getPlayerName(id) {
  if (!id) return '待定'
  return playerMap.value.get(id) || '待定'
}

function groupRounds(matches) {
  return groupMatchesByRound(matches)
}

function groupMatchesByRound(matches) {
  if (!Array.isArray(matches)) return []
  const map = {}
  for (const match of matches) {
    if (!map[match.roundNum]) map[match.roundNum] = []
    map[match.roundNum].push(match)
  }
  return Object.keys(map)
    .sort((a, b) => Number(a) - Number(b))
    .map((roundNum) => ({
      roundNum: Number(roundNum),
      matches: map[roundNum],
    }))
}

function getScoreText(match) {
  if (!match) return '待开始'
  if (match.status === 2) return match.scoreDisplay || '已完赛'
  if (match.status === 1) return match.scoreDisplay || '进行中'
  if (match.leftPlayerId && match.rightPlayerId) return '待开始'
  return '等待选手'
}

function getWinnerSide(match) {
  if (!match || !match.winnerId) return ''
  if (match.winnerId === match.leftPlayerId) return 'left'
  if (match.winnerId === match.rightPlayerId) return 'right'
  return ''
}

function goBack() {
  uni.navigateBack()
}

function findMatch(matchId) {
  for (const group of groups.value) {
    const match = (group.matches || []).find((item) => item.id === matchId)
    if (match) return match
  }
  return knockoutMatches.value.find((item) => item.id === matchId)
}

function buildMatchParams(match) {
  return {
    tournamentId: tournamentId.value,
    matchId: match.id,
    leftName: getPlayerName(match?.leftPlayerId),
    rightName: getPlayerName(match?.rightPlayerId),
    bestOf: rule.value.bestOf,
    gamesToWin: rule.value.gamesToWin,
    pointsToWin: rule.value.pointsToWin,
    enableDeuce: rule.value.enableDeuce ? '1' : '0',
    capPoint: rule.value.capPoint,
  }
}

function openBadmintonScoreboard(match) {
  const query = buildMatchQuery(buildMatchParams(match))
  uni.navigateTo({ url: '/pages/scoreboard/index?' + query })
}

function openVolleyballLineup(match) {
  uni.navigateTo({ url: buildLineupUrl(buildMatchParams(match)) })
}

function openVolleyballRecord(match) {
  uni.navigateTo({
    url: '/pages/volleyball/record?tournamentId=' + encodeURIComponent(tournamentId.value) + '&matchId=' + encodeURIComponent(match.id),
  })
}

function handleGroupMatchClick(match) {
  if (!match?.id) return
  if (isVolleyball.value) {
    openVolleyballLineup(match)
    return
  }
  openBadmintonScoreboard(match)
}

function handleKnockoutMatchClick(match) {
  if (!match?.id) return
  if (isVolleyball.value && Number(match.status || 0) === 2) {
    uni.showActionSheet({
      itemList: ['查看比赛记录'],
      success(res) {
        if (res.tapIndex === 0) {
          openVolleyballRecord(match)
        }
      },
    })
    return
  }
  if (isVolleyball.value) {
    openVolleyballLineup(match)
    return
  }
  openBadmintonScoreboard(match)
}

async function fetchGroups(tid) {
  const data = await request('/api/v1/tournaments/' + tid + '/groups', { method: 'GET' })
  info.value = {
    id: data.id,
    name: data.name,
    location: data.location,
    status: data.status,
    sportType: data.sportType,
    tournamentType: data.tournamentType,
    groupSize: data.groupSize,
    knockoutSlots: data.knockoutSlots,
    qualifiersPerGroup: data.qualifiersPerGroup,
    currentStage: data.currentStage,
    knockoutGenerated: data.knockoutGenerated,
    bestOf: data.bestOf,
    gamesToWin: data.gamesToWin,
    pointsToWin: data.pointsToWin,
    enableDeuce: data.enableDeuce,
    capPoint: data.capPoint,
  }
  groups.value = Array.isArray(data.groups) ? data.groups : []
}

async function fetchStandings(tid) {
  standings.value = await request('/api/v1/tournaments/' + tid + '/group-standings', { method: 'GET' }) || {}
}

async function fetchBracket(tid) {
  const data = await request('/api/v1/tournaments/' + tid + '/bracket', { method: 'GET' })
  knockoutPlayers.value = Array.isArray(data?.players) ? data.players : []
  knockoutMatches.value = Array.isArray(data?.matches) ? data.matches : []
  if (data?.knockoutGenerated != null) {
    info.value.knockoutGenerated = data.knockoutGenerated
  }
}

async function fetchData(tid) {
  if (!tid) return
  loading.value = true
  isError.value = false
  try {
    await fetchGroups(tid)
    await fetchStandings(tid)
    await fetchBracket(tid)
  } catch (_) {
    isError.value = true
  } finally {
    loading.value = false
  }
}

async function generateKnockout() {
  if (!canGenerateKnockout.value) return
  try {
    await request('/api/v1/tournaments/' + tournamentId.value + '/generate-knockout', { method: 'POST' })
    uni.showToast({ title: '已生成淘汰赛', icon: 'success' })
    activeTab.value = 'knockout'
    await fetchData(tournamentId.value)
  } catch (_) {
    // request handles toast
  }
}

onLoad((options) => {
  const tid = options?.id
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
  if (tournamentId.value) fetchData(tournamentId.value)
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

.retry-btn,
.generate-btn {
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

.retry-btn::after,
.generate-btn::after {
  border: none;
}

.header {
  padding: 28rpx 24rpx 20rpx;
  background: rgba(19, 32, 45, 0.96);
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.header-top,
.header-left,
.tabs,
.standing-row,
.knockout-actions {
  display: flex;
  align-items: center;
}

.header-top {
  justify-content: space-between;
  gap: 16rpx;
}

.header-left {
  gap: 16rpx;
  min-width: 0;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
}

.header-title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
}

.header-status {
  flex-shrink: 0;
  font-size: 22rpx;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
}

.status-0 {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.72);
}

.status-1 {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
}

.status-2 {
  background: rgba(76, 217, 100, 0.14);
  color: #7ee787;
}

.header-line {
  display: block;
  margin-top: 12rpx;
  color: rgba(255, 255, 255, 0.66);
  font-size: 24rpx;
  line-height: 1.5;
}

.tabs {
  gap: 14rpx;
  margin-top: 22rpx;
}

.tab {
  min-width: 160rpx;
  height: 64rpx;
  line-height: 64rpx;
  text-align: center;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.72);
  font-size: 24rpx;
}

.tab.active {
  background: #ff8c00;
  color: #13202d;
  font-weight: 700;
}

.group-scroll,
.bracket-scroll-view {
  flex: 1;
}

.group-section {
  margin: 24rpx;
  padding: 24rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.05);
}

.group-title,
.round-title {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.standing-table {
  margin-top: 18rpx;
  border-radius: 18rpx;
  overflow: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.standing-row {
  padding: 16rpx 18rpx;
  display: grid;
  grid-template-columns: 80rpx 1.8fr 1fr 0.8fr 0.8fr;
  gap: 10rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.78);
}

.standing-head {
  background: rgba(255, 140, 0, 0.14);
  color: #ffcf8a;
  font-weight: 700;
}

.player-row {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.player-pill {
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.78);
  font-size: 22rpx;
}

.round-block {
  margin-top: 20rpx;
}

.match-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 14rpx;
}

.knockout-panel {
  flex: 1;
  min-height: 0;
}

.knockout-actions {
  flex-direction: column;
  gap: 16rpx;
  padding: 24rpx;
}

.knockout-hint {
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  text-align: center;
}

.canvas-container {
  padding: 24rpx;
}

.rounds-wrapper {
  display: flex;
  align-items: flex-start;
  gap: 30rpx;
  min-width: fit-content;
}

.round-column {
  width: 360rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.cards-stack {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.match-node {
  display: flex;
}
</style>
