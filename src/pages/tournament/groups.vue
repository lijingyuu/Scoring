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
      <view class="header-safe" :style="headerSafeStyle">
        <view class="header">
          <view class="header-top">
            <view class="header-left">
              <text class="back-btn safe-back-btn" @click="goBack">返回</text>
              <text class="header-title">{{ info.name || (isRoundRobin ? '循环赛' : '小组赛') }}</text>
            </view>
            <text class="header-status" :class="'status-' + info.status">{{ statusLabels[info.status] ?? '' }}</text>
          </view>
          <text class="header-line header-meta-line">{{ modeText }} / {{ ruleText }}</text>

          <view class="tabs" v-if="!isRoundRobin">
            <view class="tab" :class="{ active: activeTab === 'group' }" @click="activeTab = 'group'">小组赛</view>
            <view class="tab" :class="{ active: activeTab === 'knockout' }" @click="activeTab = 'knockout'">淘汰赛</view>
          </view>
        </view>
      </view>

      <scroll-view class="group-scroll" scroll-y v-if="activeTab === 'group' && hasGroupContent">
        <view class="group-section" v-for="group in groups" :key="getGroupNo(group)">
          <view class="group-title">{{ isRoundRobin ? '积分榜' : groupName(getGroupNo(group)) }}</view>

          <view class="standing-table" v-if="getStandings(getGroupNo(group)).length">
            <view class="standing-row standing-head">
              <text class="standing-cell standing-rank">排名</text>
              <text class="standing-cell standing-name">{{ isTeamTournament ? '队伍' : '选手' }}</text>
              <text class="standing-cell standing-stat">胜负</text>
              <text class="standing-cell standing-stat">净胜局</text>
              <text class="standing-cell standing-stat">净胜分</text>
            </view>
            <view class="standing-row" :class="{ 'standing-row-round-robin': isRoundRobin }" v-for="standing in getStandings(getGroupNo(group))" :key="standing.playerId">
              <text class="standing-cell standing-rank">{{ getStandingRankText(standing) }}</text>
              <text class="standing-cell standing-name">{{ standing.playerName }}{{ !isRoundRobin && standing.qualified ? ' 出线' : '' }}{{ !isRoundRobin && standing.tieUnresolved ? ' 待定' : '' }}</text>
              <text class="standing-cell standing-stat">{{ standing.matchWins }}-{{ standing.matchLosses }}</text>
              <text class="standing-cell standing-stat">{{ standing.netGames }}</text>
              <text class="standing-cell standing-stat">{{ standing.netPoints }}</text>
            </view>
          </view>

          <view class="empty-panel compact" v-if="!getStandings(getGroupNo(group)).length">
            <text class="empty-text">暂无积分数据</text>
            <text class="empty-subtext">完成任意一场比赛后，这里会刷新排名。</text>
          </view>

          <view class="player-row" v-if="!isRoundRobin">
            <text class="player-pill" v-for="player in groupPlayers(group)" :key="player.id">
              {{ player.name }}{{ player.seedRank ? ' #' + player.seedRank : '' }}
            </text>
          </view>

          <view class="round-block" v-for="round in groupRounds(groupMatches(group))" :key="getGroupNo(group) + '-' + round.roundNum">
            <view class="round-title">第{{ round.roundNum }}轮</view>
            <view class="match-list">
              <MatchCard
                v-for="match in round.matches"
                :key="getMatchId(match)"
                :match-id="getMatchId(match)"
                :left-name="getPlayerName(getLeftPlayerId(match))"
                :right-name="getPlayerName(getRightPlayerId(match))"
                :status="getMatchStatus(match)"
                :score-text="getScoreText(match)"
                :winner-side="getWinnerSide(match)"
                :retired-side="getRetiredSide(match)"
                @click-card="() => handleGroupMatchClick(match)"
              />
            </view>
          </view>
          <view class="empty-panel compact" v-if="!groupRounds(groupMatches(group)).length">
            <text class="empty-text">暂无对阵数据</text>
            <text class="empty-subtext">如果这是刚创建的循环赛，请重新进入页面或检查后端赛程生成结果。</text>
          </view>
        </view>
      </scroll-view>

      <view class="empty-panel page-empty" v-else-if="activeTab === 'group'">
        <text class="empty-text">暂未加载到赛程数据</text>
        <text class="empty-subtext">当前接口没有返回积分榜或对阵。请重新加载；如果仍为空，需要检查赛事创建时是否成功生成比赛。</text>
        <button class="retry-btn" @click="fetchData(tournamentId)">重新加载</button>
      </view>

      <view class="knockout-panel" v-else-if="!isRoundRobin">
        <view class="knockout-actions" v-if="!info.knockoutGenerated && !isArchived">
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
                  <view class="match-node" v-for="match in round.matches" :key="getMatchId(match)">
                    <MatchCard
                      :match-id="getMatchId(match)"
                      :left-name="getPlayerName(getLeftPlayerId(match))"
                      :right-name="getPlayerName(getRightPlayerId(match))"
                      :status="getMatchStatus(match)"
                      :score-text="getScoreText(match)"
                      :winner-side="getWinnerSide(match)"
                      :retired-side="getRetiredSide(match)"
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
import { useActionLock } from '@/utils/interaction-guard'
import MatchCard from '@/components/MatchCard.vue'
import { buildLineupUrl, buildMatchQuery } from '@/pages/volleyball/match-state'
import { findStandings, getStandingRankText as resolveStandingRankText, groupMatchesByRound, hasVisibleGroupContent } from './groups-data'

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

const headerSafeStyle = buildBasePortraitPageStyle()

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
const { begin: beginPageAction, run: runPageAction } = useActionLock(500)

const isVolleyball = computed(() => Number(info.value?.sportType || 0) === 1)
const isTeamTournament = computed(() => Number(info.value?.participantType || 0) === 1)
const isRelayTournament = computed(() => Number(info.value?.teamMatchTemplate || 0) === 2)
const isRoundRobin = computed(() => Number(info.value?.tournamentType || 0) === 2)
const canOperateMatches = computed(() => info.value?.canOperateMatches === true)
const isArchived = computed(() => info.value?.archived === true)

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
  decidingPointsToWin: info.value?.decidingPointsToWin == null ? null : Number(info.value.decidingPointsToWin),
  enableDeuce: info.value.enableDeuce !== false,
  capPoint: Number(info.value.capPoint || 30),
}))

const ruleText = computed(() => {
  const matchText = rule.value.bestOf === 5 ? '五局三胜' : rule.value.bestOf === 1 ? '一局定胜负' : '三局两胜'
  const roundRuleText = info.value?.roundRuleEnabled === true ? ' / 分轮规则已启用' : ''
  if (isVolleyball.value) {
    const decidingPoints = rule.value.decidingPointsToWin || 15
    return `${matchText} / 常规局${rule.value.pointsToWin}分 / 末局${decidingPoints}分 / 领先2分${roundRuleText}`
  }
  const deuce = rule.value.enableDeuce ? `${rule.value.capPoint}分封顶` : '无追分'
  return `${matchText} / ${rule.value.pointsToWin}分 / ${deuce}${roundRuleText}`
})

const modeText = computed(() => {
  if (isRoundRobin.value) {
    return `循环赛 / ${Number(info.value?.roundRobinRounds || 1) === 2 ? '双循环' : '单循环'}`
  }
  return `${info.value.knockoutSlots || 0}强淘汰赛 / 每组出线${info.value.qualifiersPerGroup || 2}${isTeamTournament.value ? '队' : '人'}`
})

const canGenerateKnockout = computed(() => (
  standings.value.allGroupMatchesFinished === true
  && standings.value.hasUnresolvedTie !== true
  && info.value.knockoutGenerated !== true
  && !isArchived.value
))

const groupedKnockoutMatches = computed(() => groupMatchesByRound(knockoutMatches.value))
const hasGroupContent = computed(() => hasVisibleGroupContent(groups.value, standings.value))

const knockoutColumnHeight = computed(() => {
  if (!groupedKnockoutMatches.value.length) return '2000rpx'
  const maxCount = Math.max(...groupedKnockoutMatches.value.map((group) => group.matches.length))
  return maxCount * 150 + 80 + 'rpx'
})

function getGroupNo(group) {
  return group?.groupNo ?? group?.group_no ?? 1
}

function groupPlayers(group) {
  return Array.isArray(group?.players) ? group.players : []
}

function groupMatches(group) {
  return Array.isArray(group?.matches) ? group.matches : []
}

function getMatchId(match) {
  return match?.id ?? match?.matchId ?? match?.match_id ?? ''
}

function getLeftPlayerId(match) {
  return match?.leftPlayerId ?? match?.left_player_id ?? ''
}

function getRightPlayerId(match) {
  return match?.rightPlayerId ?? match?.right_player_id ?? ''
}

function getWinnerId(match) {
  return match?.winnerId ?? match?.winner_id ?? ''
}

function getMatchStatus(match) {
  return Number(match?.status ?? 0)
}

function hasCompleteParticipants(match) {
  return !!getLeftPlayerId(match) && !!getRightPlayerId(match)
}

function isSettledMatch(match) {
  const status = getMatchStatus(match)
  return status === 2 || status === 3
}

function getScoreDisplay(match) {
  return match?.scoreDisplay ?? match?.score_display ?? ''
}

function getRetiredSide(match) {
  return match?.retiredSide ?? match?.retired_side ?? ''
}

function groupName(groupNo) {
  return String.fromCharCode(64 + Number(groupNo || 1)) + '组'
}

function getStandings(groupNo) {
  return findStandings(standings.value, groupNo)
}

function getPlayerName(id) {
  if (!id) return '待定'
  return playerMap.value.get(id) || '待定'
}

function getStandingRankText(standing) {
  return resolveStandingRankText(standing, isRoundRobin.value)
}

function groupRounds(matches) {
  return groupMatchesByRound(matches)
}


function getScoreText(match) {
  if (!match) return '待开始'
  if (getMatchStatus(match) === 2) return getScoreDisplay(match) || '已完赛'
  if (getMatchStatus(match) === 1) return getScoreDisplay(match) || '进行中'
  if (getLeftPlayerId(match) && getRightPlayerId(match)) return '待开始'
  return '等待选手'
}

function getWinnerSide(match) {
  const winnerId = getWinnerId(match)
  if (!match || !winnerId) return ''
  if (winnerId === getLeftPlayerId(match)) return 'left'
  if (winnerId === getRightPlayerId(match)) return 'right'
  return ''
}

function goBack() {
  if (!beginPageAction()) return
  uni.navigateBack()
}

function buildMatchParams(match) {
  const matchRule = ruleForMatch(match)
  return {
    tournamentId: tournamentId.value,
    matchId: getMatchId(match),
    leftName: getPlayerName(getLeftPlayerId(match)),
    rightName: getPlayerName(getRightPlayerId(match)),
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
    const stageType = Number(match?.stageType ?? match?.stage_type ?? 0)
    const roundNum = stageType === 0 ? 0 : Number(match?.roundNum ?? match?.round_num ?? 1)
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

function openBadmintonScoreboard(match) {
  const query = buildMatchQuery(buildMatchParams(match))
  if (!beginPageAction()) return
  uni.navigateTo({ url: '/pages/scoreboard/index?' + query })
}

function openVolleyballLineup(match) {
  if (!beginPageAction()) return
  uni.navigateTo({ url: buildLineupUrl(buildMatchParams(match)) })
}

function openBadmintonTeamMatch(match) {
  if (!beginPageAction()) return
  uni.navigateTo({
    url: (isRelayTournament.value ? '/pages/tournament/team-lineup' : '/pages/tournament/team-match')
      + '?tournamentId='
      + encodeURIComponent(tournamentId.value)
      + '&matchId='
      + encodeURIComponent(getMatchId(match)),
  })
}

function openVolleyballRecord(match) {
  if (!beginPageAction()) return
  uni.navigateTo({
    url: '/pages/volleyball/record?tournamentId=' + encodeURIComponent(tournamentId.value) + '&matchId=' + encodeURIComponent(getMatchId(match)),
  })
}

function guardOperateMatch() {
  if (canOperateMatches.value) return true
  uni.showToast({ title: '请先录入裁判身份后再开始执裁', icon: 'none' })
  return false
}

function handleVolleyballMatchClick(match) {
  const status = getMatchStatus(match)
  if (status === 2) {
    openVolleyballRecord(match)
    return
  }
  if (!guardOperateMatch()) return
  openVolleyballLineup(match)
}

function guardArchivedMatch(match) {
  if (!isArchived.value) return false
  if (isVolleyball.value && getMatchStatus(match) === 2) {
    openVolleyballRecord(match)
    return true
  }
  uni.showToast({ title: '已归档，只读查看', icon: 'none' })
  return true
}

function guardMatchEntry(match) {
  if (!hasCompleteParticipants(match)) {
    uni.showToast({ title: '对阵未完整，不能执裁', icon: 'none' })
    return false
  }
  if (isSettledMatch(match) && !isVolleyball.value) {
    uni.showToast({ title: '比赛已结束，不能执裁', icon: 'none' })
    return false
  }
  return true
}

function handleGroupMatchClick(match) {
  if (!getMatchId(match)) return
  if (!guardMatchEntry(match)) return
  if (guardArchivedMatch(match)) return
  if (isVolleyball.value) {
    handleVolleyballMatchClick(match)
    return
  }
  if (isTeamTournament.value) {
    openBadmintonTeamMatch(match)
    return
  }
  openBadmintonScoreboard(match)
}

function handleKnockoutMatchClick(match) {
  if (!getMatchId(match)) return
  if (!guardMatchEntry(match)) return
  if (guardArchivedMatch(match)) return
  if (isVolleyball.value) {
    handleVolleyballMatchClick(match)
    return
  }
  if (isTeamTournament.value) {
    openBadmintonTeamMatch(match)
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
    archived: data.archived,
    sportType: data.sportType,
    participantType: data.participantType,
    teamMatchTemplate: data.teamMatchTemplate,
    tournamentType: data.tournamentType,
    groupSize: data.groupSize,
    knockoutSlots: data.knockoutSlots,
    knockoutRounds: data.knockoutRounds,
    qualifiersPerGroup: data.qualifiersPerGroup,
    currentStage: data.currentStage,
    knockoutGenerated: data.knockoutGenerated,
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
    roundRobinRounds: data.roundRobinRounds,
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
  if (data?.refereeGranted != null) {
    info.value.refereeGranted = data.refereeGranted
  }
  if (data?.canOperateMatches != null) {
    info.value.canOperateMatches = data.canOperateMatches
  }
  if (data?.decidingPointsToWin !== undefined) {
    info.value.decidingPointsToWin = data.decidingPointsToWin
  }
  if (data?.knockoutRounds !== undefined) {
    info.value.knockoutRounds = data.knockoutRounds
  }
  if (data?.roundRuleEnabled !== undefined) {
    info.value.roundRuleEnabled = data.roundRuleEnabled
  }
  if (Array.isArray(data?.roundRules)) {
    info.value.roundRules = data.roundRules
  }
  if (data?.archived != null) {
    info.value.archived = data.archived
  }
  if (data?.participantType != null) {
    info.value.participantType = data.participantType
  }
  if (data?.teamMatchTemplate != null) {
    info.value.teamMatchTemplate = data.teamMatchTemplate
  }
}

async function fetchData(tid) {
  if (!tid) return
  loading.value = true
  isError.value = false
  try {
    await fetchGroups(tid)
    await fetchStandings(tid)
    if (!isRoundRobin.value) {
      await fetchBracket(tid)
    } else {
      knockoutPlayers.value = []
      knockoutMatches.value = []
      activeTab.value = 'group'
    }
  } catch (_) {
    isError.value = true
  } finally {
    loading.value = false
  }
}

async function generateKnockout() {
  if (isArchived.value) {
    uni.showToast({ title: '已归档，只读查看', icon: 'none' })
    return
  }
  if (!canGenerateKnockout.value) return
  await runPageAction(async () => {
    try {
      await request('/api/v1/tournaments/' + tournamentId.value + '/generate-knockout', { method: 'POST' })
      uni.showToast({ title: '已生成淘汰赛', icon: 'success' })
      activeTab.value = 'knockout'
      await fetchData(tournamentId.value)
    } catch (_) {
      // request handles toast
    }
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
  if (tournamentId.value) fetchData(tournamentId.value)
})
</script>

<style scoped>
.page {
  height: 100vh;
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

.header-safe {
  flex-shrink: 0;
  background: rgba(19, 32, 45, 0.96);
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
  padding: 0 24rpx 20rpx;
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

.header-meta-line {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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
  min-height: 0;
  height: 0;
  width: 100%;
  box-sizing: border-box;
}

.empty-panel {
  margin: 24rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.empty-panel.compact {
  margin: 18rpx 0 0;
  padding: 20rpx;
}

.page-empty {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  gap: 18rpx;
}

.empty-text,
.empty-subtext {
  display: block;
  text-align: center;
}

.empty-text {
  color: rgba(255, 255, 255, 0.78);
  font-size: 26rpx;
  font-weight: 700;
}

.empty-subtext {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.5);
  font-size: 22rpx;
  line-height: 1.5;
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
  grid-template-columns: 72rpx minmax(0, 1fr) 88rpx 96rpx 96rpx;
  gap: 8rpx;
  font-size: 22rpx;
  color: rgba(255, 255, 255, 0.78);
}

.standing-head {
  background: rgba(255, 140, 0, 0.14);
  color: #ffcf8a;
  font-weight: 700;
}

.standing-cell {
  min-width: 0;
  line-height: 1.4;
}

.standing-rank,
.standing-stat {
  text-align: center;
}

.standing-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  flex-wrap: wrap;
  gap: 14rpx;
  margin-top: 14rpx;
  max-width: 1000rpx;
}

.match-list :deep(.match-card) {
  width: calc((100% - 28rpx) / 3);
  min-width: 300rpx;
  flex: 1 1 300rpx;
  max-width: 360rpx;
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

