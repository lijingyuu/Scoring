<template>
  <view class="page">
    <view v-if="loading" class="state-layer">
      <text class="state-text">正在加载比赛记录...</text>
    </view>

    <view v-else-if="isError" class="state-layer">
      <text class="state-text state-error">{{ errorText }}</text>
      <button class="retry-btn" @click="loadRecord">重新加载</button>
    </view>

    <template v-else-if="record">
      <view class="toolbar">
        <text class="back-btn" @click="goBack">返回</text>
        <view class="toolbar-actions">
          <button class="toolbar-btn ghost" disabled>图片待补</button>
          <button class="toolbar-btn" @click="exportAsPdf">导出 PDF</button>
        </view>
      </view>

      <scroll-view class="page-scroll" scroll-y>
        <view id="record-export-root" class="record-shell">
          <view class="hero-card">
            <text class="eyebrow">Match Record</text>
            <text class="hero-title">{{ record.tournamentName || '比赛记录' }}</text>
            <text class="hero-matchup">{{ leftTeamName }} vs {{ rightTeamName }}</text>
            <view class="hero-meta">
              <text class="meta-pill">第 {{ record.roundNum || '-' }} 轮</text>
              <text class="meta-pill">比赛 {{ record.matchIndex || '-' }}</text>
              <text class="meta-pill">{{ statusText }}</text>
              <text class="meta-pill" v-if="record.location">{{ record.location }}</text>
            </view>
            <view class="hero-score">
              <view class="hero-side">
                <text class="hero-team">{{ leftTeamName }}</text>
                <text class="hero-games">{{ record.leftGameWins ?? 0 }}</text>
              </view>
              <view class="hero-center">
                <text class="hero-score-line">{{ record.scoreDisplay || '-' }}</text>
                <text class="hero-winner">{{ winnerText }}</text>
              </view>
              <view class="hero-side">
                <text class="hero-team">{{ rightTeamName }}</text>
                <text class="hero-games">{{ record.rightGameWins ?? 0 }}</text>
              </view>
            </view>
          </view>

          <view class="summary-grid">
            <view class="summary-card">
              <text class="summary-label">赛制</text>
              <text class="summary-value">{{ formatRule(record) }}</text>
            </view>
            <view class="summary-card">
              <text class="summary-label">局分</text>
              <text class="summary-value">{{ gameScoreSummary }}</text>
            </view>
            <view class="summary-card">
              <text class="summary-label">退赛</text>
              <text class="summary-value">{{ retiredText }}</text>
            </view>
            <view class="summary-card">
              <text class="summary-label">事件数</text>
              <text class="summary-value">{{ eventCountText }}</text>
            </view>
          </view>

          <view class="section-card">
            <view class="section-head">
              <text class="section-title">双方队员名单快照</text>
              <text class="section-tip">赛前落盘名单</text>
            </view>
            <view class="roster-grid">
              <view class="team-card">
                <text class="team-title">{{ leftTeamName }}</text>
                <view class="member-list">
                  <view v-for="member in leftRoster" :key="member.id || member.name" class="member-item">
                    <text class="member-no">{{ member.jerseyNumber || '-' }}</text>
                    <view class="member-main">
                      <text class="member-name">{{ member.name || '-' }}</text>
                      <text class="member-tags">
                        {{ buildMemberTags(member) }}
                      </text>
                    </view>
                  </view>
                </view>
              </view>

              <view class="team-card">
                <text class="team-title">{{ rightTeamName }}</text>
                <view class="member-list">
                  <view v-for="member in rightRoster" :key="member.id || member.name" class="member-item">
                    <text class="member-no">{{ member.jerseyNumber || '-' }}</text>
                    <view class="member-main">
                      <text class="member-name">{{ member.name || '-' }}</text>
                      <text class="member-tags">
                        {{ buildMemberTags(member) }}
                      </text>
                    </view>
                  </view>
                </view>
              </view>
            </view>
          </view>

          <view class="section-card">
            <view class="section-head">
              <text class="section-title">每局开局轮次与自由人绑定</text>
              <text class="section-tip">按比赛实际开局快照展示</text>
            </view>

            <view
              v-for="lineup in record.lineupSnapshots || []"
              :key="lineup.gameNo"
              class="lineup-card"
            >
              <view class="lineup-head">
                <text class="lineup-title">第 {{ lineup.gameNo }} 局</text>
                <text class="lineup-serve">{{ lineup.serveSide === 'right' ? rightTeamName : leftTeamName }} 先发球</text>
              </view>

              <view class="lineup-grid">
                <view class="lineup-team">
                  <text class="lineup-team-name">{{ leftTeamName }}</text>
                  <view class="court-grid">
                    <view v-for="slot in lineup.left?.court || []" :key="'l_' + lineup.gameNo + '_' + slot.slotIndex" class="court-slot">
                      <text class="court-pos">{{ slot.positionLabel }}</text>
                      <text class="court-player">{{ formatCourtPlayer(slot) }}</text>
                    </view>
                  </view>
                  <text class="lineup-libero">{{ formatLibero(lineup.left) }}</text>
                </view>

                <view class="lineup-team">
                  <text class="lineup-team-name">{{ rightTeamName }}</text>
                  <view class="court-grid">
                    <view v-for="slot in lineup.right?.court || []" :key="'r_' + lineup.gameNo + '_' + slot.slotIndex" class="court-slot">
                      <text class="court-pos">{{ slot.positionLabel }}</text>
                      <text class="court-player">{{ formatCourtPlayer(slot) }}</text>
                    </view>
                  </view>
                  <text class="lineup-libero">{{ formatLibero(lineup.right) }}</text>
                </view>
              </view>
            </view>

            <text v-if="!(record.lineupSnapshots || []).length" class="empty-text">暂无开局轮次快照</text>
          </view>

          <view class="section-card">
            <view class="section-head">
              <text class="section-title">比赛事件时间轴</text>
              <text class="section-tip">暂停 / 手动换人 / 场上队长等过程记录</text>
            </view>

            <view v-for="event in record.events || []" :key="event.eventSeq" class="event-item">
              <view class="event-left">
                <text class="event-seq">#{{ event.eventSeq }}</text>
                <text class="event-badge">{{ event.eventTypeLabel }}</text>
              </view>
              <view class="event-main">
                <view class="event-top">
                  <text class="event-title">{{ event.summary }}</text>
                  <text class="event-score">第{{ event.gameNo }}局 · {{ event.leftScore }}:{{ event.rightScore }}</text>
                </view>
                <text v-if="event.createTime" class="event-time">{{ event.createTime }}</text>
                <text
                  v-for="(line, index) in event.detailLines || []"
                  :key="event.eventSeq + '_' + index"
                  class="event-detail"
                >
                  {{ line }}
                </text>
              </view>
            </view>

            <text v-if="!(record.events || []).length" class="empty-text">暂无比赛事件</text>
          </view>
        </view>
      </scroll-view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import { request } from '@/utils/request'

const loading = ref(true)
const isError = ref(false)
const errorText = ref('加载失败')
const tournamentId = ref('')
const matchId = ref('')
const record = ref(null)

const statusText = computed(() => {
  const status = Number(record.value?.status || 0)
  if (status === 2) return '已结束'
  if (status === 1) return '进行中'
  return '未开始'
})

const leftTeamName = computed(() => record.value?.left?.name || '左队')
const rightTeamName = computed(() => record.value?.right?.name || '右队')

const winnerText = computed(() => {
  if (!record.value?.winnerSide) return '未记录胜方'
  return record.value.winnerSide === 'left'
    ? `${leftTeamName.value} 获胜`
    : `${rightTeamName.value} 获胜`
})

const retiredText = computed(() => {
  if (!record.value?.retiredSide) return '无'
  return record.value.retiredSide === 'left' ? `${leftTeamName.value} 退赛` : `${rightTeamName.value} 退赛`
})

const leftRoster = computed(() => record.value?.rosterSnapshot?.leftMembers || [])
const rightRoster = computed(() => record.value?.rosterSnapshot?.rightMembers || [])

const gameScoreSummary = computed(() => {
  const scores = Array.isArray(record.value?.gameScores) ? record.value.gameScores : []
  if (!scores.length) return '暂无'
  return scores.map((item) => `${item.leftScore}:${item.rightScore}`).join(' / ')
})

const eventCountText = computed(() => `${Array.isArray(record.value?.events) ? record.value.events.length : 0} 条`)

function goBack() {
  uni.navigateBack()
}

function formatRule(data) {
  const bestOf = Number(data?.bestOf || 3)
  const pointsToWin = Number(data?.pointsToWin || 25)
  const capPoint = Number(data?.capPoint || 99)
  return `${bestOf === 5 ? '五局三胜' : '三局两胜'} / ${pointsToWin}分 / ${capPoint}分封顶`
}

function buildMemberTags(member) {
  const tags = []
  if (member?.captain) tags.push('队长')
  if (member?.libero) tags.push('自由人')
  return tags.length ? tags.join(' / ') : '普通队员'
}

function formatCourtPlayer(slot) {
  if (!slot?.memberName && !slot?.jerseyNumber) return '未记录'
  const jersey = slot?.jerseyNumber ? `${slot.jerseyNumber}号` : ''
  return `${jersey} ${slot.memberName || ''}`.trim()
}

function formatLibero(teamLineup) {
  const names = [teamLineup?.libero1Name, teamLineup?.libero2Name].filter(Boolean)
  if (!names.length) return '自由人绑定：未设置'
  return `自由人绑定：${names.join(' / ')}`
}

async function loadRecord() {
  if (!matchId.value) {
    isError.value = true
    errorText.value = '缺少比赛ID'
    loading.value = false
    return
  }

  loading.value = true
  isError.value = false
  try {
    record.value = await request('/api/v1/matches/' + matchId.value + '/record', { method: 'GET' })
  } catch (error) {
    isError.value = true
    errorText.value = error?.message || '加载比赛记录失败'
  } finally {
    loading.value = false
  }
}

function exportAsPdf() {
  // #ifdef H5
  window.print()
  return
  // #endif
  uni.showToast({ title: '当前端暂只支持 H5 导出 PDF', icon: 'none' })
}

onLoad((options) => {
  tournamentId.value = options?.tournamentId || ''
  matchId.value = options?.matchId || ''
  loadRecord()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  background:
    radial-gradient(circle at top, rgba(240, 164, 92, 0.14), transparent 28%),
    linear-gradient(180deg, #122131 0%, #0d1823 100%);
}

.state-layer {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 24rpx;
  padding: 40rpx;
  box-sizing: border-box;
}

.state-text {
  color: rgba(255, 255, 255, 0.76);
  font-size: 30rpx;
}

.state-error {
  color: #ffb347;
}

.retry-btn,
.toolbar-btn {
  border: none;
  border-radius: 18rpx;
}

.retry-btn::after,
.toolbar-btn::after {
  border: none;
}

.retry-btn {
  width: 280rpx;
  height: 76rpx;
  line-height: 76rpx;
  background: #ffb347;
  color: #13202d;
  font-size: 28rpx;
  font-weight: 700;
}

.toolbar {
  position: sticky;
  top: 0;
  z-index: 20;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 22rpx 24rpx 16rpx;
  backdrop-filter: blur(18rpx);
  background: rgba(10, 18, 27, 0.72);
}

.back-btn {
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 700;
}

.toolbar-actions {
  display: flex;
  gap: 12rpx;
}

.toolbar-btn {
  height: 68rpx;
  line-height: 68rpx;
  padding: 0 22rpx;
  background: #ffb347;
  color: #13202d;
  font-size: 26rpx;
  font-weight: 700;
}

.toolbar-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.toolbar-btn[disabled] {
  opacity: 0.56;
}

.page-scroll {
  height: calc(100vh - 106rpx);
}

.record-shell {
  width: 100%;
  max-width: 980rpx;
  margin: 0 auto;
  padding: 10rpx 24rpx 40rpx;
  box-sizing: border-box;
}

.hero-card,
.section-card,
.summary-card,
.team-card,
.lineup-card {
  background: #f3eee2;
  color: #1d252e;
  border-radius: 28rpx;
  box-shadow: 0 18rpx 48rpx rgba(0, 0, 0, 0.16);
}

.hero-card {
  padding: 34rpx 30rpx;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.92), rgba(243, 238, 226, 0.98)),
    #f3eee2;
}

.eyebrow {
  display: block;
  color: #8a6745;
  font-size: 22rpx;
  letter-spacing: 4rpx;
  text-transform: uppercase;
}

.hero-title {
  display: block;
  margin-top: 12rpx;
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 42rpx;
  font-weight: 700;
}

.hero-matchup {
  display: block;
  margin-top: 10rpx;
  color: #5c6670;
  font-size: 28rpx;
  font-weight: 600;
}

.hero-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 22rpx;
}

.meta-pill {
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(24, 39, 56, 0.08);
  color: #42505f;
  font-size: 22rpx;
}

.hero-score {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16rpx;
  align-items: center;
  margin-top: 28rpx;
}

.hero-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.hero-team {
  font-size: 24rpx;
  color: #66717c;
}

.hero-games {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 80rpx;
  font-weight: 700;
  line-height: 1;
  color: #1d252e;
}

.hero-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.hero-score-line {
  color: #7f5f43;
  font-size: 26rpx;
  font-weight: 700;
}

.hero-winner {
  color: #1b5f57;
  font-size: 24rpx;
  font-weight: 700;
}

.summary-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14rpx;
  margin-top: 18rpx;
}

.summary-card {
  padding: 22rpx 24rpx;
}

.summary-label {
  display: block;
  color: #8c7054;
  font-size: 22rpx;
}

.summary-value {
  display: block;
  margin-top: 10rpx;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 1.5;
}

.section-card {
  margin-top: 18rpx;
  padding: 28rpx 24rpx;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.section-title {
  font-family: "Noto Serif SC", "Songti SC", "STSong", serif;
  font-size: 34rpx;
  font-weight: 700;
}

.section-tip {
  color: #85715d;
  font-size: 22rpx;
  text-align: right;
}

.roster-grid,
.lineup-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 16rpx;
}

.team-card,
.lineup-team {
  padding: 22rpx;
  border-radius: 22rpx;
  background: rgba(24, 39, 56, 0.05);
}

.team-title,
.lineup-team-name {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  margin-bottom: 14rpx;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.member-item {
  display: flex;
  gap: 14rpx;
  padding: 12rpx 14rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.58);
}

.member-no {
  width: 54rpx;
  color: #8a6745;
  font-size: 24rpx;
  font-weight: 800;
}

.member-main {
  flex: 1;
  min-width: 0;
}

.member-name {
  display: block;
  font-size: 26rpx;
  font-weight: 700;
}

.member-tags {
  display: block;
  margin-top: 6rpx;
  color: #6b7681;
  font-size: 22rpx;
}

.lineup-card {
  padding: 20rpx;
  margin-bottom: 16rpx;
  background: rgba(24, 39, 56, 0.04);
  box-shadow: none;
}

.lineup-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 16rpx;
}

.lineup-title {
  font-size: 28rpx;
  font-weight: 800;
}

.lineup-serve {
  color: #7f5f43;
  font-size: 22rpx;
  font-weight: 700;
}

.court-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10rpx;
}

.court-slot {
  min-height: 92rpx;
  padding: 12rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.62);
}

.court-pos {
  display: block;
  color: #7b858f;
  font-size: 20rpx;
}

.court-player {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 1.45;
}

.lineup-libero {
  display: block;
  margin-top: 14rpx;
  color: #7f5f43;
  font-size: 22rpx;
}

.event-item {
  display: flex;
  gap: 18rpx;
  padding: 18rpx 0;
  border-top: 1rpx solid rgba(28, 39, 51, 0.08);
}

.event-item:first-of-type {
  border-top: none;
}

.event-left {
  width: 132rpx;
  flex-shrink: 0;
}

.event-seq {
  display: block;
  color: #7b858f;
  font-size: 22rpx;
}

.event-badge {
  display: inline-block;
  margin-top: 8rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(24, 39, 56, 0.08);
  color: #42505f;
  font-size: 20rpx;
  font-weight: 700;
}

.event-main {
  flex: 1;
  min-width: 0;
}

.event-top {
  display: flex;
  justify-content: space-between;
  align-items: baseline;
  gap: 12rpx;
}

.event-title {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  font-weight: 700;
}

.event-score {
  color: #7b858f;
  font-size: 20rpx;
  white-space: nowrap;
}

.event-time,
.event-detail,
.empty-text {
  display: block;
  color: #68747f;
  font-size: 22rpx;
  line-height: 1.7;
}

.event-time {
  margin-top: 6rpx;
}

.event-detail {
  margin-top: 4rpx;
}

.empty-text {
  padding-top: 6rpx;
}

@media (min-width: 880px) {
  .roster-grid,
  .lineup-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .summary-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media print {
  .page {
    background: #ffffff;
  }

  .toolbar {
    display: none;
  }

  .page-scroll {
    height: auto;
  }

  .record-shell {
    max-width: none;
    padding: 0;
  }

  .hero-card,
  .section-card,
  .summary-card,
  .team-card,
  .lineup-card {
    box-shadow: none;
  }
}
</style>
