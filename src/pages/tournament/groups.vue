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
        <text class="header-line">{{ info.knockoutSlots || 0 }}强淘汰赛 / 每组出线{{ info.qualifiersPerGroup || 2 }}人</text>

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
              <text>选手</text>
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
            <view class="round-title">第 {{ round.roundNum }} 轮</view>
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
                @click-card="goToScoreboard"
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
                <view class="round-title">第 {{ round.roundNum }} 轮</view>
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
                      @click-card="goToScoreboard"
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
import MatchCard from '../../components/MatchCard.vue'

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

const players = computed(() => {
  const groupPlayers = groups.value.flatMap(group => Array.isArray(group.players) ? group.players : [])
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
  const matchText = rule.value.bestOf === 5
    ? '五局三胜'
    : rule.value.bestOf === 1
      ? '一局定胜负'
      : '三局两胜'
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
  const maxCount = Math.max(...groupedKnockoutMatches.value.map(group => group.matches.length))
  return (maxCount * 150 + 80) + 'rpx'
})

function groupName(groupNo) {
  return String.fromCharCode(64 + Number(groupNo || 1)) + '组'
}

function getStandings(groupNo) {
  const groups = Array.isArray(standings.value.groups) ? standings.value.groups : []
  return groups.find(group => Number(group.groupNo) === Number(groupNo))?.standings || []
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
    .map(roundNum => ({
      roundNum: Number(roundNum),
      matches: map[roundNum],
    }))
}

function getScoreText(match) {
  if (!match) return '待开赛'
  if (match.status === 2) return match.scoreDisplay || '已完赛'
  if (match.status === 1) return match.scoreDisplay || '进行中'
  if (match.leftPlayerId && match.rightPlayerId) return '待开赛'
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
    const match = (group.matches || []).find(item => item.id === matchId)
    if (match) return match
  }
  return knockoutMatches.value.find(item => item.id === matchId)
}

function goToScoreboard(matchId) {
  if (!matchId) return
  const match = findMatch(matchId)
  const leftName = getPlayerName(match?.leftPlayerId)
  const rightName = getPlayerName(match?.rightPlayerId)

  const query = [
    'matchId=' + encodeURIComponent(matchId),
    'leftName=' + encodeURIComponent(leftName),
    'rightName=' + encodeURIComponent(rightName),
    'bestOf=' + rule.value.bestOf,
    'gamesToWin=' + rule.value.gamesToWin,
    'pointsToWin=' + rule.value.pointsToWin,
    'enableDeuce=' + (rule.value.enableDeuce ? '1' : '0'),
    'capPoint=' + rule.value.capPoint,
  ].join('&')
  uni.navigateTo({ url: '/pages/scoreboard/index?' + query })
}

async function fetchGroups(tid) {
  const data = await request('/api/v1/tournaments/' + tid + '/groups', { method: 'GET' })
  info.value = {
    id: data.id,
    name: data.name,
    location: data.location,
    status: data.status,
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

.generate-btn[disabled] {
  opacity: 0.45;
}

.header {
  padding: 28rpx 28rpx 16rpx;
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

.header-line {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 6rpx;
}

.tabs {
  display: flex;
  margin-top: 18rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.4);
  border-radius: 12rpx;
  overflow: hidden;
}

.tab {
  flex: 1;
  height: 60rpx;
  line-height: 60rpx;
  text-align: center;
  font-size: 26rpx;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.65);
}

.tab.active {
  background: #ff8c00;
  color: #1a2a3a;
  font-weight: 700;
}

.group-scroll,
.bracket-scroll-view {
  flex: 1;
  padding: 0 28rpx 32rpx;
  box-sizing: border-box;
}

.group-section {
  margin-bottom: 32rpx;
}

.group-title {
  font-size: 32rpx;
  color: #ff8c00;
  font-weight: 700;
  margin-bottom: 14rpx;
}

.standing-table {
  border: 1rpx solid rgba(255, 255, 255, 0.12);
  border-radius: 12rpx;
  overflow: hidden;
  margin-bottom: 18rpx;
}

.standing-row {
  display: grid;
  grid-template-columns: 70rpx 1fr 110rpx 90rpx 90rpx;
  gap: 8rpx;
  padding: 12rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.75);
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.standing-head {
  border-top: none;
  color: #ff8c00;
  background: rgba(255, 140, 0, 0.08);
  font-weight: 700;
}

.player-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-bottom: 18rpx;
}

.player-pill {
  padding: 8rpx 14rpx;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.82);
  font-size: 24rpx;
}

.round-block {
  margin-bottom: 18rpx;
}

.round-title {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.55);
  margin-bottom: 10rpx;
}

.match-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
}

.knockout-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.knockout-actions {
  padding: 20rpx 28rpx;
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.knockout-hint {
  color: rgba(255, 255, 255, 0.58);
  font-size: 26rpx;
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
