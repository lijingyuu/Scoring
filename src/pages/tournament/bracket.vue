<template>
  <view class="page">
    <!-- 加载中 -->
    <view class="state-layer" v-if="loading">
      <text class="state-text">正在获取赛程...</text>
    </view>

    <!-- 加载失败 -->
    <view class="state-layer" v-else-if="isError">
      <text class="state-text state-error">网络请求失败</text>
      <button class="retry-btn" @click="fetchData(tournamentId)">重新加载</button>
    </view>

    <!-- 正常内容 -->
    <template v-else>
      <view class="header">
        <view class="header-top">
          <view class="header-left">
            <text class="back-btn" @click="goBack">← 返回</text>
            <text class="header-title">{{ info?.name || '赛程' }}</text>
          </view>
          <text class="header-status" :class="'status-' + info?.status">{{ statusLabels[info?.status] ?? '' }}</text>
        </view>
        <text class="header-location" v-if="info?.location">{{ info?.location }}</text>
        <text class="header-hint" v-if="!matches?.length">暂无比赛数据</text>
      </view>

      <scroll-view class="bracket-scroll" scroll-x="true" v-if="matches?.length">
        <view class="rounds-wrapper">
          <view
            class="round-column"
            v-for="round in groupedMatches"
            :key="round?.roundNum"
          >
            <view class="round-title">第 {{ round?.roundNum }} 轮</view>

            <view
              class="match-card"
              v-for="match in round?.matches ?? []"
              :key="match?.id"
              @click="goToScoreboard(match?.id)"
            >
              <view class="player-row">
                <text
                  class="player-name"
                  :class="{ winner: match?.winnerId && match?.winnerId === match?.leftPlayerId }"
                >{{ getPlayerName(match?.leftPlayerId) || '???' }}</text>
                <text class="vs">vs</text>
                <text
                  class="player-name"
                  :class="{ winner: match?.winnerId && match?.winnerId === match?.rightPlayerId }"
                >{{ getPlayerName(match?.rightPlayerId) || '???' }}</text>
              </view>

              <view class="match-footer">
                <text class="match-score" v-if="match?.status === 2">{{ match?.scoreDisplay || '已完赛' }}</text>
                <text class="match-pending" v-else-if="match?.leftPlayerId && match?.rightPlayerId">等待中</text>
                <text class="match-tbd" v-else>待定</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const statusLabels = { 0: '未开始', 1: '进行中', 2: '已结束' }

const loading = ref(true)
const isError = ref(false)
const tournamentId = ref('')
const info = ref({})
const players = ref([])
const matches = ref([])

const playerMap = computed(() => {
  const map = new Map()
  if (Array.isArray(players.value)) {
    for (const p of players.value) {
      if (p?.id) map.set(p.id, p.name)
    }
  }
  return map
})

const groupedMatches = computed(() => {
  if (!Array.isArray(matches.value)) return []
  const groups = {}
  for (const m of matches.value) {
    if (m == null) continue
    const r = m.roundNum
    if (r == null) continue
    if (!groups[r]) groups[r] = []
    groups[r].push(m)
  }
  return Object.keys(groups)
    .sort((a, b) => Number(a) - Number(b))
    .map((roundNum) => ({
      roundNum: Number(roundNum),
      matches: groups[roundNum],
    }))
})

function getPlayerName(id) {
  if (!id) return '待定'
  return playerMap.value.get(id) || '??'
}

function goBack() {
  uni.navigateBack()
}

function goToScoreboard(matchId) {
  if (!matchId) return
  // 在 match 数据里找到对应的比赛，获取选手名字
  let leftName = '', rightName = ''
  for (const m of matches.value) {
    if (m.id === matchId) {
      leftName = getPlayerName(m.leftPlayerId)
      rightName = getPlayerName(m.rightPlayerId)
      break
    }
  }
  uni.navigateTo({
    url: '/pages/scoreboard/index?matchId=' + matchId
      + '&leftName=' + encodeURIComponent(leftName)
      + '&rightName=' + encodeURIComponent(rightName),
  })
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
  // 从记分牌返回时自动刷新，展示晋级后的最新数据
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

/* ─── 通用状态层 ─── */
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

/* ─── 头部 ─── */
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
}

.back-btn {
  font-size: 26rpx;
  color: #ff8c00;
  padding: 6rpx 12rpx;
}

.header-title {
  font-size: 34rpx;
  font-weight: 700;
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

.header-location {
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

/* ─── 横向滚动 ─── */
.bracket-scroll {
  flex: 1;
  padding: 0 28rpx 28rpx;
  box-sizing: border-box;
}

.rounds-wrapper {
  display: flex;
  gap: 28rpx;
  min-height: 100%;
}

/* ─── 轮次列 ─── */
.round-column {
  min-width: 320rpx;
  display: flex;
  flex-direction: column;
  gap: 16rpx;
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

/* ─── 比赛卡片 ─── */
.match-card {
  background: #2a3a4a;
  border: 1px solid rgba(255, 255, 255, 0.1);
  border-radius: 14rpx;
  padding: 20rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.match-card:active {
  opacity: 0.7;
}

.player-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8rpx;
}

.player-name {
  flex: 1;
  font-size: 26rpx;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.player-name.winner {
  color: #ff8c00;
  font-weight: 700;
}

.vs {
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.3);
  flex-shrink: 0;
}

.match-footer {
  text-align: center;
}

.match-score {
  font-size: 24rpx;
  color: #4cd964;
}

.match-pending {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.4);
}

.match-tbd {
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.25);
}
</style>
