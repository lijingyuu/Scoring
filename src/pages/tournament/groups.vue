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
        <view class="ranking-config-panel">
          <view class="ranking-config-head">
            <text class="ranking-config-title">排名规则：{{ rankingTemplateLabel }}</text>
          </view>
          <text class="ranking-config-subtitle">{{ rankingTemplateDescription }}</text>
        </view>

        <view class="group-section" v-for="group in groups" :key="getGroupNo(group)">
          <view class="group-title">{{ isRoundRobin ? '积分榜' : groupName(getGroupNo(group)) }}</view>

          <view class="standing-table" v-if="getStandings(getGroupNo(group)).length">
            <view class="standing-row standing-head" :style="standingGridStyle">
              <text class="standing-cell standing-rank">排名</text>
              <text class="standing-cell standing-name">{{ isTeamTournament ? '队伍' : '选手' }}</text>
              <text class="standing-cell standing-stat" v-for="column in standingColumns" :key="column.key">{{ column.label }}</text>
            </view>
            <view class="standing-row" :style="standingGridStyle" :class="{ 'standing-row-round-robin': isRoundRobin }" v-for="standing in getStandings(getGroupNo(group))" :key="standing.playerId">
              <text class="standing-cell standing-rank">{{ getStandingRankText(standing) }}</text>
              <text
                class="standing-cell standing-name"
                :class="{ 'standing-name-qualified': standing.qualified === true }"
              >
                {{ standing.playerName }}
              </text>
              <text class="standing-cell standing-stat" v-for="column in standingColumns" :key="column.key">{{ getStandingCellText(standing, column.key) }}</text>
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
                :is-team-match="isTeamTournament && !isVolleyball"
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
        <view class="knockout-actions" v-if="!info.knockoutGenerated && !isArchived && canOperateMatches">
          <text class="knockout-hint" v-if="!standings.allGroupMatchesFinished">小组赛全部完成后才能生成淘汰赛</text>
          <text class="knockout-hint" v-else-if="standings.hasUnresolvedTie">存在无法自动判定的同分，需要人工处理后再生成</text>
          <button
            class="qualification-btn"
            v-if="standings.hasUnresolvedTie && standings.allGroupMatchesFinished"
            @click="openQualificationConfirm"
          >
            确认出线名单
          </button>
          <button class="generate-btn" :disabled="!canOpenKnockout" @click="generateKnockout">生成淘汰赛</button>
        </view>

        <view class="bracket-viewport-shell" v-if="knockoutMatches.length">
          <movable-area
            class="bracket-viewport"
            scale-area
          >
            <movable-view
              class="bracket-movable"
              :direction="bracketMoveDirection"
              scale
              :animation="false"
              :scale-min="minScale"
              :scale-max="maxScale"
              :scale-value="bracketScale"
              :x="bracketX"
              :y="bracketY"
              :style="toRpxStyle({ width: bracketLayout.width, height: bracketLayout.height })"
              @change="handleBracketMove"
              @scale="handleBracketScale"
            >
              <view
                class="bracket-board"
                :style="toRpxStyle({ width: bracketLayout.width, height: bracketLayout.height })"
              >
                <view
                  v-for="round in bracketLayout.rounds"
                  :key="round.roundNum"
                  class="bracket-round-title"
                  :style="toRpxStyle({ left: round.left })"
                >
                  第{{ round.roundNum }}轮
                </view>
                <view
                  v-for="segment in bracketLayout.segments"
                  :key="segment.id"
                  class="bracket-connector"
                  :class="'connector-' + segment.axis"
                  :style="toRpxStyle({ left: segment.left, top: segment.top, width: segment.width, height: segment.height })"
                />
                <view
                  v-for="node in bracketLayout.nodes"
                  :key="node.id"
                  class="match-node"
                  :style="toRpxStyle({ left: node.left, top: node.top })"
                >
                  <view class="match-role-label" v-if="isThirdPlaceMatch(node.match)">季军赛</view>
                  <MatchCard
                    class="bracket-match-card"
                    :match-id="getMatchId(node.match)"
                    :left-name="getPlayerName(getLeftPlayerId(node.match))"
                    :right-name="getPlayerName(getRightPlayerId(node.match))"
                    :status="getMatchStatus(node.match)"
                    :score-text="getScoreText(node.match)"
                    :winner-side="getWinnerSide(node.match)"
                    :retired-side="getRetiredSide(node.match)"
                    :is-team-match="isTeamTournament && !isVolleyball"
                    @click-card="() => handleKnockoutMatchClick(node.match)"
                  />
                </view>
              </view>
            </movable-view>
          </movable-area>
          <view class="bracket-controls">
            <button class="bracket-control-btn wide" @tap.stop="fitBracketToOverview">总览</button>
            <button class="bracket-control-btn wide" @tap.stop="resetBracketView">100%</button>
            <view class="bracket-zoom-group">
              <view class="bracket-zoom-indicator">{{ bracketScalePercent }}</view>
              <view class="bracket-zoom-buttons">
                <button class="bracket-control-btn" @tap.stop="zoomOutBracket">-</button>
                <button class="bracket-control-btn" @tap.stop="zoomInBracket">+</button>
              </view>
            </view>
          </view>
        </view>

        <text class="knockout-hint" v-else-if="info.knockoutGenerated">淘汰赛数据加载中</text>
      </view>

      <view class="qualification-mask" v-if="qualificationConfirmVisible" @tap="closeQualificationConfirm">
        <view class="qualification-dialog" @tap.stop>
          <view class="qualification-header">
            <text class="qualification-title">确认出线名单</text>
            <text class="qualification-subtitle">自动排名无法区分时，由主办方或裁判确认出线人选。</text>
          </view>
          <scroll-view class="qualification-list" scroll-y>
            <view class="qualification-group" v-for="group in unresolvedQualificationGroups" :key="group.groupNo">
              <view class="qualification-group-head">
                <text class="qualification-group-title">{{ groupName(group.groupNo) }}</text>
                <text class="qualification-group-hint">请选择 {{ group.remainingSlots }} 人</text>
              </view>
              <view
                class="qualification-candidate"
                v-for="standing in group.candidates"
                :key="standing.playerId"
                :class="{ selected: isQualificationSelected(group.groupNo, standing.playerId) }"
                @tap="toggleQualificationCandidate(group.groupNo, standing.playerId)"
              >
                <text class="qualification-candidate-name">{{ standing.playerName }}</text>
                <text class="qualification-candidate-stat">{{ standing.matchWins }}胜 / {{ qualificationRatioLabel }} {{ standing.pointWinRate || '0.0000' }}</text>
              </view>
            </view>
          </scroll-view>
          <view class="qualification-footer">
            <button class="preview-btn ghost" @tap.stop="closeQualificationConfirm" :disabled="qualificationSaving">取消</button>
            <button class="preview-btn primary" @tap.stop="confirmQualificationSelection" :loading="qualificationSaving" :disabled="qualificationSaving || !qualificationSelectionComplete">确认出线</button>
          </view>
        </view>
      </view>

      <view class="preview-mask" v-if="knockoutPreviewVisible" @tap="closeKnockoutPreview">
        <view class="preview-dialog" :class="{ 'swap-active': previewSwapMode }" @tap.stop>
          <view class="preview-header">
            <text class="preview-title">淘汰赛首轮预览</text>
            <text class="preview-subtitle">
              {{ previewMetaText }} / 当前{{ previewModeLabel }}
            </text>
          </view>

          <view class="preview-mode-bar">
            <button class="preview-mode-btn" :class="{ active: knockoutPreviewMode === 'STANDARD_CROSS' }" @tap.stop="setPreviewMode('STANDARD_CROSS')">标准交叉</button>
            <button class="preview-mode-btn" :class="{ active: knockoutPreviewMode === 'RANDOM_DRAW' }" @tap.stop="setPreviewMode('RANDOM_DRAW')">随机排列</button>
            <button class="preview-mode-btn" :class="{ active: knockoutPreviewMode === 'MANUAL' }" @tap.stop="setPreviewMode('MANUAL')">手动调整</button>
          </view>

          <view class="preview-swap-bar" v-if="knockoutPreviewMode === 'MANUAL'">
            <text class="preview-swap-hint" :class="{ active: previewSwapMode }">{{ previewSwapMode ? '先后点击两个槽位即可完成互换' : '当前排布已固定，可继续调整槽位' }}</text>
          </view>

          <scroll-view class="preview-list" scroll-y>
            <view class="preview-loading" v-if="knockoutPreviewLoading">
              <text class="preview-loading-text">正在生成预览...</text>
            </view>
            <template v-else>
              <view class="preview-empty" v-if="!knockoutPreviewMatches.length">
                <text class="preview-empty-text">暂无可展示的首轮对阵</text>
              </view>
              <view class="preview-match" v-for="(match, index) in knockoutPreviewMatches" :key="index">
                <view class="preview-match-head">
                  <text class="preview-match-index">第{{ currentPreviewMatchNo(index) }}组</text>
                </view>
                <view
                  class="preview-side"
                  :class="previewSideClass(index * 2)"
                  @tap.stop="handlePreviewSlotTap(index * 2)"
                >
                  <text class="preview-side-name">{{ getPreviewParticipantText(match.leftPlayer) }}</text>
                  <text class="preview-side-slot">槽位{{ index * 2 + 1 }}</text>
                </view>
                <view
                  class="preview-side"
                  :class="previewSideClass(index * 2 + 1)"
                  @tap.stop="handlePreviewSlotTap(index * 2 + 1)"
                >
                  <text class="preview-side-name">{{ getPreviewParticipantText(match.rightPlayer) }}</text>
                  <text class="preview-side-slot">槽位{{ index * 2 + 2 }}</text>
                </view>
              </view>
            </template>
          </scroll-view>

          <view class="preview-adjust-footer" v-if="knockoutPreviewMode === 'MANUAL'">
            <button class="preview-adjust-btn" :class="{ active: previewSwapMode }" @tap.stop="togglePreviewSwapMode">
              {{ previewSwapMode ? '完成' : '槽位调整' }}
            </button>
          </view>

          <view class="preview-footer">
            <button class="preview-btn ghost" @tap.stop="closeKnockoutPreview" :disabled="knockoutGenerating">取消</button>
            <button class="preview-btn secondary" @tap.stop="resetPreviewArrangement" :disabled="knockoutGenerating || knockoutPreviewLoading">恢复标准</button>
            <button class="preview-btn primary" @tap.stop="confirmKnockoutGeneration" :loading="knockoutGenerating" :disabled="knockoutGenerating || knockoutPreviewLoading || !knockoutPreviewMatches.length">确认生成</button>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import { request } from '@/utils/request'
import { useActionLock } from '@/utils/interaction-guard'
import MatchCard from '@/components/MatchCard.vue'
import { buildLineupUrl, buildMatchQuery } from '@/pages/volleyball/match-state'
import {
  findStandings,
  formatStandingCell,
  getStandingColumns,
  getStandingRankText as resolveStandingRankText,
  groupMatchesByRound,
  hasVisibleGroupContent,
} from './groups-data'
import {
  RANKING_CUSTOM_INPUT_PREFIX,
  RANKING_CUSTOM_RESULT_PREFIX,
  RELAY_RANKING_MODE,
  STANDARD_RANKING_MODE,
  TEAM_RANKING_MODE,
  defaultBaseTemplateForRankingMode,
  getCriterionLabel,
  rankingStorageKey,
} from '@/pages/ranking/ranking-options'
import { buildKnockoutBracketLayout, toRpxStyle } from './knockout-bracket-layout'
import { buildIndividualRecordUrl, buildTeamRecordUrl } from './tournament-navigation'
import { useKnockoutBracketViewport } from './use-knockout-bracket-viewport'

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
const rankingConfig = ref({ template: 'CUSTOM', locked: false })
const rankingConfigSaving = ref(false)
const customRankingKey = computed(() => 'groups_ranking_' + (tournamentId.value || 'default'))
const knockoutPlayers = ref([])
const knockoutMatches = ref([])
const knockoutPreviewVisible = ref(false)
const knockoutPreviewLoading = ref(false)
const knockoutPreviewMode = ref('STANDARD_CROSS')
const knockoutPreviewSourceMatches = ref([])
const knockoutPreviewWorkingMatches = ref([])
const previewSwapMode = ref(false)
const previewSelectedSlotIndex = ref(null)
const knockoutPreview = ref({
  knockoutSlots: 0,
  qualifiersPerGroup: 0,
  allGroupMatchesFinished: false,
  hasUnresolvedTie: false,
  matches: [],
})
const knockoutGenerating = ref(false)
const qualificationConfirmVisible = ref(false)
const qualificationSaving = ref(false)
const qualificationSelections = ref({})
const activeTab = ref('group')
const { begin: beginPageAction, run: runPageAction } = useActionLock(500)

const isVolleyball = computed(() => Number(info.value?.sportType || 0) === 1)
const isTeamTournament = computed(() => Number(info.value?.participantType || 0) === 1)
const qualificationRatioLabel = computed(() => (
  isTeamTournament.value && !isVolleyball.value ? '小分得失比' : '得失分比'
))
const isRelayTournament = computed(() => Number(info.value?.teamMatchTemplate || 0) === 2)
const isRoundRobin = computed(() => Number(info.value?.tournamentType || 0) === 2)
const canOperateMatches = computed(() => info.value?.canOperateMatches === true)
const isArchived = computed(() => info.value?.archived === true)
const canUpdateRankingConfig = computed(() => (
  rankingConfig.value?.creator === true
  && !isArchived.value
))
const standingColumns = computed(() => getStandingColumns(rankingConfig.value))
const standingGridStyle = computed(() => ({
  gridTemplateColumns: `72rpx 150rpx repeat(${standingColumns.value.length}, 112rpx)`,
}))

const rankingTemplateLabel = computed(() => {
  const template = rankingConfig.value?.template
  if (template === 'BWF_BADMINTON') return 'BWF标准规则'
  if (template === 'FIVB_VOLLEYBALL') return 'FIVB标准规则'
  if (
    template === 'BADMINTON_COMMON_1'
    || template === 'BADMINTON_TEAM_COMMON_1'
    || template === 'BADMINTON_RELAY_COMMON_1'
    || template === 'VOLLEYBALL_COMMON_1'
  ) {
    return '常用模板一'
  }
  return '自定义'
})

const rankingTemplateDescription = computed(() => {
  const priorities = Array.isArray(rankingConfig.value?.priorities)
    ? rankingConfig.value.priorities
    : []
  const systemFallback = rankingConfig.value?.systemFallbackCriterion
  const visiblePriorities = priorities.filter((criterion) => criterion !== systemFallback)
  const description = visiblePriorities.map(getCriterionLabel).filter(Boolean).join('/')
  if (!systemFallback) return description
  return `${description}(系统兜底:${getCriterionLabel(systemFallback)})`
})

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

const thirdPlaceRule = computed(() => ({
  bestOf: Number(info.value?.thirdPlaceBestOf || rule.value.bestOf),
  gamesToWin: Number(info.value?.thirdPlaceGamesToWin || rule.value.gamesToWin),
  pointsToWin: Number(info.value?.thirdPlacePointsToWin || rule.value.pointsToWin),
  decidingPointsToWin: info.value?.thirdPlaceDecidingPointsToWin == null ? rule.value.decidingPointsToWin : Number(info.value.thirdPlaceDecidingPointsToWin),
  enableDeuce: info.value?.thirdPlaceEnableDeuce == null ? rule.value.enableDeuce : info.value.thirdPlaceEnableDeuce !== false,
  capPoint: Number(info.value?.thirdPlaceCapPoint || rule.value.capPoint),
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
  canOperateMatches.value
  && standings.value.allGroupMatchesFinished === true
  && standings.value.hasUnresolvedTie !== true
  && info.value.knockoutGenerated !== true
  && !isArchived.value
))

const canOpenKnockout = computed(() => (
  canOperateMatches.value
  && standings.value.allGroupMatchesFinished === true
  && info.value.knockoutGenerated !== true
  && !isArchived.value
))

const unresolvedQualificationGroups = computed(() => {
  const qualifiers = Number(standings.value?.qualifiersPerGroup || info.value?.qualifiersPerGroup || 0)
  const source = Array.isArray(standings.value?.groups) ? standings.value.groups : []
  return source
    .map((group) => {
      const groupNo = Number(group?.groupNo ?? group?.group_no)
      const rows = Array.isArray(group?.standings) ? group.standings : []
      const candidates = rows.filter((standing) => standing?.tieUnresolved === true)
      if (!candidates.length) return null
      const autoQualified = rows.filter((standing) => standing?.qualified === true && standing?.tieUnresolved !== true).length
      return {
        groupNo,
        candidates,
        remainingSlots: Math.max(1, qualifiers - autoQualified),
      }
    })
    .filter(Boolean)
})

const qualificationSelectionComplete = computed(() => (
  unresolvedQualificationGroups.value.length > 0
  && unresolvedQualificationGroups.value.every((group) => (
    (qualificationSelections.value[group.groupNo] || []).length === group.remainingSlots
  ))
))

const knockoutPreviewMatches = computed(() => (
  knockoutPreviewWorkingMatches.value
))

const previewModeLabel = computed(() => {
  if (knockoutPreviewMode.value === 'RANDOM_DRAW') return '随机排列'
  if (knockoutPreviewMode.value === 'MANUAL') return '手动调整'
  return '标准交叉'
})

const previewMetaText = computed(() => {
  const slots = Number(knockoutPreview.value?.knockoutSlots || info.value.knockoutSlots || 0)
  const qualifiers = Number(knockoutPreview.value?.qualifiersPerGroup || info.value.qualifiersPerGroup || 0)
  const finished = knockoutPreview.value?.allGroupMatchesFinished === true ? '已完赛' : '未完赛'
  const tieText = knockoutPreview.value?.hasUnresolvedTie === true ? '存在未决平局' : '无未决平局'
  return `${slots}强 / 每组出线${qualifiers}${isTeamTournament.value ? '队' : '人'} / ${finished} / ${tieText}`
})

const bracketLayout = computed(() => buildKnockoutBracketLayout(knockoutMatches.value))
const hasGroupContent = computed(() => hasVisibleGroupContent(groups.value, standings.value))
const {
  x: bracketX,
  y: bracketY,
  scale: bracketScale,
  scalePercent: bracketScalePercent,
  minScale,
  maxScale,
  moveDirection: bracketMoveDirection,
  fitToOverview: fitBracketToOverview,
  setDefaultView: setDefaultBracketView,
  resetView: resetBracketView,
  zoomIn: zoomInBracket,
  zoomOut: zoomOutBracket,
  handleMove: handleBracketMove,
  handleScale: handleBracketScale,
} = useKnockoutBracketViewport(bracketLayout)

watch(activeTab, (tab) => {
  if (tab === 'knockout') setDefaultBracketView()
}, { flush: 'post' })

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

function isThirdPlaceMatch(match) {
  return Number(match?.matchRole ?? match?.match_role ?? 0) === 1
}

function hasCompleteParticipants(match) {
  return !!getLeftPlayerId(match) && !!getRightPlayerId(match)
}

function isSettledMatch(match) {
  const status = getMatchStatus(match)
  return status === 2 || status === 3 || !!getWinnerId(match)
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

function getPreviewParticipantText(participant) {
  if (!participant) return '待定'
  const parts = []
  if (participant.groupNo != null && participant.groupRank != null) {
    parts.push(String.fromCharCode(64 + Number(participant.groupNo || 1)) + participant.groupRank)
  }
  if (participant.playerName) parts.push(participant.playerName)
  return parts.join(' · ') || '待定'
}

function clonePreviewMatches(matches) {
  return (Array.isArray(matches) ? matches : []).map((match, index) => ({
    slotIndex: Number(match?.slotIndex ?? index),
    leftPlayer: match?.leftPlayer ? { ...match.leftPlayer } : null,
    rightPlayer: match?.rightPlayer ? { ...match.rightPlayer } : null,
  }))
}

function previewMatchesToSlots(matches) {
  return clonePreviewMatches(matches).flatMap((match) => [match.leftPlayer, match.rightPlayer])
}

function previewSlotsToMatches(slots) {
  const matches = []
  for (let i = 0; i + 1 < slots.length; i += 2) {
    matches.push({
      slotIndex: i / 2,
      leftPlayer: slots[i] ? { ...slots[i] } : null,
      rightPlayer: slots[i + 1] ? { ...slots[i + 1] } : null,
    })
  }
  return matches
}

function syncPreviewWorkingMatches(matches) {
  knockoutPreviewSourceMatches.value = clonePreviewMatches(matches)
  knockoutPreviewWorkingMatches.value = clonePreviewMatches(matches)
}

function resetPreviewArrangement() {
  knockoutPreviewMode.value = 'STANDARD_CROSS'
  resetPreviewSwapState()
  knockoutPreviewWorkingMatches.value = clonePreviewMatches(knockoutPreviewSourceMatches.value)
}

function shufflePreviewArrangement() {
  const next = previewMatchesToSlots(knockoutPreviewSourceMatches.value)
  for (let i = next.length - 1; i > 0; i -= 1) {
    const j = Math.floor(Math.random() * (i + 1))
    const temp = next[i]
    next[i] = next[j]
    next[j] = temp
  }
  knockoutPreviewWorkingMatches.value = previewSlotsToMatches(next)
}

function setPreviewMode(mode) {
  knockoutPreviewMode.value = mode
  resetPreviewSwapState()
  if (mode === 'STANDARD_CROSS') {
    resetPreviewArrangement()
    return
  }
  if (mode === 'RANDOM_DRAW') {
    shufflePreviewArrangement()
    return
  }
  if (mode === 'MANUAL') {
    if (knockoutPreviewWorkingMatches.value.length === 0) {
      knockoutPreviewWorkingMatches.value = clonePreviewMatches(knockoutPreviewSourceMatches.value)
    }
    previewSwapMode.value = true
  }
}

function resetPreviewSwapState() {
  previewSwapMode.value = false
  previewSelectedSlotIndex.value = null
}

function togglePreviewSwapMode() {
  previewSwapMode.value = !previewSwapMode.value
  previewSelectedSlotIndex.value = null
  if (previewSwapMode.value && knockoutPreviewMode.value !== 'MANUAL') {
    knockoutPreviewMode.value = 'MANUAL'
  }
}

function previewSideClass(slotIndex) {
  return {
    'swap-selectable': previewSwapMode.value,
    selected: previewSelectedSlotIndex.value === slotIndex,
  }
}

function handlePreviewSlotTap(slotIndex) {
  if (!previewSwapMode.value || knockoutPreviewMode.value !== 'MANUAL') return
  if (previewSelectedSlotIndex.value == null) {
    previewSelectedSlotIndex.value = slotIndex
    return
  }
  if (previewSelectedSlotIndex.value === slotIndex) {
    previewSelectedSlotIndex.value = null
    return
  }
  swapPreviewSlots(previewSelectedSlotIndex.value, slotIndex)
  previewSelectedSlotIndex.value = null
}

function getPreviewSlot(slotIndex) {
  const matchIndex = Math.floor(slotIndex / 2)
  const match = knockoutPreviewWorkingMatches.value[matchIndex]
  if (!match) return null
  return slotIndex % 2 === 0 ? match.leftPlayer : match.rightPlayer
}

function setPreviewSlot(slotIndex, participant) {
  const matchIndex = Math.floor(slotIndex / 2)
  const match = knockoutPreviewWorkingMatches.value[matchIndex]
  if (!match) return
  if (slotIndex % 2 === 0) {
    match.leftPlayer = participant
  } else {
    match.rightPlayer = participant
  }
}

function swapPreviewSlots(firstSlotIndex, secondSlotIndex) {
  const first = getPreviewSlot(firstSlotIndex)
  const second = getPreviewSlot(secondSlotIndex)
  setPreviewSlot(firstSlotIndex, second)
  setPreviewSlot(secondSlotIndex, first)
  knockoutPreviewWorkingMatches.value = knockoutPreviewWorkingMatches.value.slice()
}

function buildPreviewSlots() {
  return knockoutPreviewWorkingMatches.value.flatMap((match) => {
    const leftId = match?.leftPlayer?.playerId || ''
    const rightId = match?.rightPlayer?.playerId || ''
    return [leftId, rightId]
  })
}

function currentPreviewMatchNo(index) {
  return index + 1
}

function getStandingRankText(standing) {
  return resolveStandingRankText(standing, isRoundRobin.value)
}

function getStandingCellText(standing, columnKey) {
  return formatStandingCell(standing, columnKey)
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
  if (isThirdPlaceMatch(match)) {
    return thirdPlaceRule.value
  }
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

function openBadmintonTeamRecord(match) {
  if (!beginPageAction()) return
  uni.navigateTo({
    url: buildTeamRecordUrl({
      tournamentId: tournamentId.value,
      matchId: getMatchId(match),
      isRelayTemplate: isRelayTournament.value,
    }),
  })
}

function openBadmintonIndividualRecord(match) {
  if (!beginPageAction()) return
  uni.navigateTo({
    url: buildIndividualRecordUrl({
      tournamentId: tournamentId.value,
      matchId: getMatchId(match),
    }),
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

function guardArchivedMatch(match) {
  if (!isArchived.value) return false
  if (isVolleyball.value && getMatchStatus(match) === 2) {
    openVolleyballRecord(match)
    return true
  }
  if (isTeamTournament.value && isSettledMatch(match)) {
    openBadmintonTeamRecord(match)
    return true
  }
  if (!isVolleyball.value && isSettledMatch(match)) {
    openBadmintonIndividualRecord(match)
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
    if (isTeamTournament.value) {
      openBadmintonTeamRecord(match)
      return false
    }
    openBadmintonIndividualRecord(match)
    return false
  }
  return true
}

function handleMatchAction(match) {
  if (isVolleyball.value && getMatchStatus(match) === 2) {
    openVolleyballRecord(match)
    return
  }

  if (!guardOperateMatch()) return

  if (isVolleyball.value) {
    openVolleyballLineup(match)
    return
  }
  if (isTeamTournament.value) {
    openBadmintonTeamMatch(match)
    return
  }
  openBadmintonScoreboard(match)
}

function handleGroupMatchClick(match) {
  if (!getMatchId(match)) return
  if (!guardMatchEntry(match)) return
  if (guardArchivedMatch(match)) return
  handleMatchAction(match)
}

function handleKnockoutMatchClick(match) {
  if (!getMatchId(match)) return
  if (!guardMatchEntry(match)) return
  if (guardArchivedMatch(match)) return
  handleMatchAction(match)
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
    thirdPlaceEnabled: data.thirdPlaceEnabled,
    thirdPlaceBestOf: data.thirdPlaceBestOf,
    thirdPlaceGamesToWin: data.thirdPlaceGamesToWin,
    thirdPlacePointsToWin: data.thirdPlacePointsToWin,
    thirdPlaceDecidingPointsToWin: data.thirdPlaceDecidingPointsToWin,
    thirdPlaceEnableDeuce: data.thirdPlaceEnableDeuce,
    thirdPlaceCapPoint: data.thirdPlaceCapPoint,
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

async function fetchRankingConfig(tid) {
  rankingConfig.value = await request('/api/v1/tournaments/' + tid + '/ranking-config', { method: 'GET' }) || { template: 'CUSTOM', locked: false }
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
  // 取 groups 和 bracket 两个接口的 OR，
  // 避免 bracket 接口因缓存/旧代码返回 false 时覆盖 groups 的正确结果
  if (data?.canOperateMatches === true) {
    info.value.canOperateMatches = true
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
  if (data?.thirdPlaceEnabled !== undefined) {
    info.value.thirdPlaceEnabled = data.thirdPlaceEnabled
    info.value.thirdPlaceBestOf = data.thirdPlaceBestOf
    info.value.thirdPlaceGamesToWin = data.thirdPlaceGamesToWin
    info.value.thirdPlacePointsToWin = data.thirdPlacePointsToWin
    info.value.thirdPlaceDecidingPointsToWin = data.thirdPlaceDecidingPointsToWin
    info.value.thirdPlaceEnableDeuce = data.thirdPlaceEnableDeuce
    info.value.thirdPlaceCapPoint = data.thirdPlaceCapPoint
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
  knockoutPreviewVisible.value = false
  knockoutPreviewLoading.value = false
  knockoutGenerating.value = false
  knockoutPreviewMode.value = 'STANDARD_CROSS'
  knockoutPreviewSourceMatches.value = []
  knockoutPreviewWorkingMatches.value = []
  resetPreviewSwapState()
  try {
    await fetchGroups(tid)
    await fetchRankingConfig(tid)
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

async function selectRankingTemplate(template) {
  if (template === 'CUSTOM') {
    openCustomRanking()
    return
  }
  if (!template || rankingConfig.value?.template === template) return
  if (!canUpdateRankingConfig.value || rankingConfigSaving.value) return
  await runPageAction(async () => {
    rankingConfigSaving.value = true
    try {
      rankingConfig.value = await request('/api/v1/tournaments/' + tournamentId.value + '/ranking-config', {
        method: 'PUT',
        data: { template },
      }) || rankingConfig.value
      await fetchStandings(tournamentId.value)
      uni.showToast({ title: '排名规则已更新', icon: 'success' })
    } catch (_) {
      // request handles toast
    } finally {
      rankingConfigSaving.value = false
    }
  })
}

function rankingModeForCurrentTournament() {
  if (isRelayTournament.value) return RELAY_RANKING_MODE
  return isTeamTournament.value && !isVolleyball.value ? TEAM_RANKING_MODE : STANDARD_RANKING_MODE
}

function defaultCustomBaseTemplateForTournament() {
  return defaultBaseTemplateForRankingMode(rankingModeForCurrentTournament(), isVolleyball.value ? 1 : 0)
}

function openCustomRanking() {
  if (!canUpdateRankingConfig.value || rankingConfigSaving.value) return
  const mode = rankingModeForCurrentTournament()
  uni.setStorageSync(rankingStorageKey(RANKING_CUSTOM_INPUT_PREFIX, customRankingKey.value), {
    mode,
    baseTemplate: defaultCustomBaseTemplateForTournament(),
    priorities: Array.isArray(rankingConfig.value?.priorities) ? rankingConfig.value.priorities : [],
    systemFallbackCriterion: rankingConfig.value?.systemFallbackCriterion || null,
  })
  uni.navigateTo({
    url: '/pages/ranking/custom?key='
      + encodeURIComponent(customRankingKey.value)
      + '&mode='
      + encodeURIComponent(mode),
  })
}

async function consumeCustomRankingResult() {
  const key = rankingStorageKey(RANKING_CUSTOM_RESULT_PREFIX, customRankingKey.value)
  const result = uni.getStorageSync(key)
  if (!result || !Array.isArray(result.priorities) || !result.priorities.length) return false
  uni.removeStorageSync(key)
  if (!canUpdateRankingConfig.value || rankingConfigSaving.value) return true
  await runPageAction(async () => {
    rankingConfigSaving.value = true
    try {
      rankingConfig.value = await request('/api/v1/tournaments/' + tournamentId.value + '/ranking-config', {
        method: 'PUT',
        data: {
          template: result.baseTemplate || defaultCustomBaseTemplateForTournament(),
          priorities: result.priorities,
        },
      }) || rankingConfig.value
      await fetchStandings(tournamentId.value)
      uni.showToast({ title: '自定义规则已更新', icon: 'success' })
    } catch (_) {
      // request handles toast
    } finally {
      rankingConfigSaving.value = false
    }
  })
  return true
}

function closeKnockoutPreview() {
  if (knockoutGenerating.value) return
  resetPreviewSwapState()
  knockoutPreviewVisible.value = false
}

function openQualificationConfirm() {
  if (!canOpenKnockout.value || !standings.value.hasUnresolvedTie) return
  const selections = {}
  for (const group of unresolvedQualificationGroups.value) {
    selections[group.groupNo] = []
  }
  qualificationSelections.value = selections
  qualificationConfirmVisible.value = true
}

function closeQualificationConfirm() {
  if (qualificationSaving.value) return
  qualificationConfirmVisible.value = false
}

function promptQualificationConfirmation() {
  uni.showModal({
    title: '需要确认出线名单',
    content: '当前存在成绩相同的选手，请先手动确认出线名单。',
    confirmText: '去确认',
    cancelText: '暂不处理',
    success: (result) => {
      if (result?.confirm) openQualificationConfirm()
    },
    fail: () => openQualificationConfirm(),
  })
}

function isQualificationSelected(groupNo, playerId) {
  return (qualificationSelections.value[groupNo] || []).includes(playerId)
}

function toggleQualificationCandidate(groupNo, playerId) {
  const group = unresolvedQualificationGroups.value.find((item) => item.groupNo === Number(groupNo))
  if (!group || qualificationSaving.value) return
  const current = qualificationSelections.value[groupNo] || []
  if (current.includes(playerId)) {
    qualificationSelections.value = {
      ...qualificationSelections.value,
      [groupNo]: current.filter((id) => id !== playerId),
    }
    return
  }
  if (current.length >= group.remainingSlots) {
    uni.showToast({ title: `本组只能选择${group.remainingSlots}人`, icon: 'none' })
    return
  }
  qualificationSelections.value = {
    ...qualificationSelections.value,
    [groupNo]: [...current, playerId],
  }
}

function buildQualificationOverrides() {
  const overrides = []
  for (const group of unresolvedQualificationGroups.value) {
    const rows = getStandings(group.groupNo)
    const usedSlots = new Set(
      rows
        .filter((standing) => standing?.qualified === true && standing?.tieUnresolved !== true)
        .map((standing) => Number(standing.rank))
        .filter((rank) => Number.isFinite(rank) && rank > 0),
    )
    const availableSlots = []
    const qualifierCount = Number(standings.value?.qualifiersPerGroup || info.value?.qualifiersPerGroup || 0)
    for (let slot = 1; slot <= qualifierCount; slot += 1) {
      if (!usedSlots.has(slot)) availableSlots.push(slot)
    }
    const selected = qualificationSelections.value[group.groupNo] || []
    selected.forEach((playerId, index) => {
      overrides.push({
        groupNo: group.groupNo,
        rankSlot: availableSlots[index],
        playerId,
      })
    })
  }
  return overrides
}

async function confirmQualificationSelection() {
  if (!qualificationSelectionComplete.value || qualificationSaving.value) return
  await runPageAction(async () => {
    qualificationSaving.value = true
    try {
      await request('/api/v1/tournaments/' + tournamentId.value + '/qualification-overrides', {
        method: 'PUT',
        data: { overrides: buildQualificationOverrides() },
      })
      qualificationConfirmVisible.value = false
      await fetchStandings(tournamentId.value)
      uni.showToast({ title: '出线名单已确认', icon: 'success' })
    } catch (_) {
      // request handles toast
    } finally {
      qualificationSaving.value = false
    }
  })
}

async function generateKnockout() {
  if (isArchived.value) {
    uni.showToast({ title: '已归档，只读查看', icon: 'none' })
    return
  }
  if (!canOperateMatches.value) {
    uni.showToast({ title: '仅创建者或已认证裁判可操作', icon: 'none' })
    return
  }
  if (standings.value.allGroupMatchesFinished !== true) {
    uni.showToast({ title: '小组赛尚未全部完成', icon: 'none' })
    return
  }
  if (!canOpenKnockout.value) return
  if (standings.value.hasUnresolvedTie === true) {
    promptQualificationConfirmation()
    return
  }
  await runPageAction(async () => {
    knockoutPreviewLoading.value = true
    try {
      const data = await request('/api/v1/tournaments/' + tournamentId.value + '/knockout-preview', { method: 'POST' })
      knockoutPreview.value = {
        knockoutSlots: Number(data?.knockoutSlots || 0),
        qualifiersPerGroup: Number(data?.qualifiersPerGroup || 0),
        allGroupMatchesFinished: data?.allGroupMatchesFinished === true,
        hasUnresolvedTie: data?.hasUnresolvedTie === true,
        matches: Array.isArray(data?.matches) ? data.matches : [],
      }
      syncPreviewWorkingMatches(knockoutPreview.value.matches)
      knockoutPreviewMode.value = 'STANDARD_CROSS'
      knockoutPreviewVisible.value = true
    } catch (_) {
      // request handles toast
    } finally {
      knockoutPreviewLoading.value = false
    }
  })
}

async function confirmKnockoutGeneration() {
  if (isArchived.value) {
    uni.showToast({ title: '已归档，只读查看', icon: 'none' })
    return
  }
  if (!canOperateMatches.value) {
    uni.showToast({ title: '仅创建者或已认证裁判可操作', icon: 'none' })
    return
  }
  if (!canGenerateKnockout.value || knockoutGenerating.value) return
  await runPageAction(async () => {
    knockoutGenerating.value = true
    try {
      await request('/api/v1/tournaments/' + tournamentId.value + '/generate-knockout', {
        method: 'POST',
        data: {
          generationMode: knockoutPreviewMode.value,
          slots: buildPreviewSlots(),
        },
      })
      knockoutPreviewVisible.value = false
      resetPreviewSwapState()
      uni.showToast({ title: '已生成淘汰赛', icon: 'success' })
      activeTab.value = 'knockout'
      await fetchData(tournamentId.value)
    } catch (_) {
      // request handles toast
    } finally {
      knockoutGenerating.value = false
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

onShow(async () => {
  if (!tournamentId.value) return
  const consumed = await consumeCustomRankingResult()
  if (!consumed) fetchData(tournamentId.value)
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
.generate-btn,
.qualification-btn {
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
.generate-btn::after,
.qualification-btn::after {
  border: none;
}

.qualification-btn {
  background: rgba(255, 255, 255, 0.1);
  color: #ffcf8a;
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
.bracket-viewport-shell {
  flex: 1;
  min-height: 0;
  height: 0;
  width: 100%;
  box-sizing: border-box;
}

.bracket-viewport-shell {
  position: relative;
  height: 100%;
  overflow: hidden;
}

.bracket-viewport {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  width: 100%;
  height: 100%;
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

.ranking-config-panel {
  margin: 24rpx 24rpx 0;
  padding: 0 24rpx;
}

.ranking-config-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
}

.ranking-config-title-block {
  min-width: 0;
}

.ranking-config-title,
.ranking-config-subtitle,
.ranking-config-hint {
  display: block;
}

.ranking-config-title {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.ranking-config-subtitle {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.82);
  font-size: 22rpx;
  line-height: 1.4;
}

.ranking-config-lock {
  flex-shrink: 0;
  padding: 7rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.66);
  font-size: 20rpx;
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
  overflow-x: auto;
  overflow-y: hidden;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
}

.standing-row {
  padding: 16rpx 18rpx;
  display: grid;
  min-width: max-content;
  gap: 8rpx;
  font-size: 22rpx;
  color: #ffffff;
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

.standing-stat {
  white-space: nowrap;
}

.standing-rank,
.standing-name {
  position: sticky;
  z-index: 2;
}

.standing-rank {
  left: 18rpx;
}

.standing-name {
  position: sticky;
  left: 98rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.standing-name-qualified {
  color: #ffb347;
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
  gap: 8rpx;
  margin-top: 14rpx;
  max-width: 1000rpx;
}

.match-list :deep(.match-card) {
  width: 318rpx;
  min-width: 318rpx;
  flex: 0 0 318rpx;
  max-width: 318rpx;
}

.knockout-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
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

.bracket-movable {
  width: 320rpx;
  height: 320rpx;
}

.bracket-board {
  position: relative;
}

.bracket-round-title {
  position: absolute;
  top: 0;
  width: 320rpx;
  font-size: 26rpx;
  font-weight: 700;
  color: #ff8c00;
  padding-bottom: 8rpx;
  border-bottom: 2rpx solid rgba(255, 140, 0, 0.3);
  box-sizing: border-box;
}

.bracket-connector {
  position: absolute;
  background: rgba(255, 255, 255, 0.3);
  pointer-events: none;
}

.connector-horizontal {
  height: 2rpx;
  transform: translateY(-1rpx);
}

.connector-vertical {
  width: 2rpx;
}

.match-node {
  position: absolute;
  width: 320rpx;
}

.match-role-label {
  position: absolute;
  top: -42rpx;
  left: 0;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 22rpx;
  font-weight: 700;
}

.bracket-match-card {
  height: 128rpx;
  min-height: 128rpx;
}

.bracket-controls {
  position: absolute;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 10;
  display: flex;
  align-items: flex-end;
  gap: 8rpx;
  padding: 8rpx;
  border-radius: 16rpx;
  background: rgba(19, 32, 45, 0.82);
  box-shadow: 0 8rpx 24rpx rgba(0, 0, 0, 0.28);
}

.bracket-control-btn {
  width: 56rpx;
  height: 56rpx;
  line-height: 56rpx;
  padding: 0;
  border: none;
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.12);
  color: #ffffff;
  font-size: 24rpx;
}

.bracket-control-btn.wide {
  width: 82rpx;
  font-size: 22rpx;
}

.bracket-zoom-group {
  position: relative;
  display: flex;
  align-items: center;
  height: 56rpx;
}

.bracket-zoom-indicator {
  position: absolute;
  left: 50%;
  top: -36rpx;
  width: 120rpx;
  height: 32rpx;
  line-height: 32rpx;
  padding: 0 8rpx;
  box-sizing: border-box;
  border-radius: 8rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 20rpx;
  font-weight: 700;
  text-align: center;
  transform: translateX(-50%);
}

.bracket-zoom-buttons {
  display: flex;
  gap: 8rpx;
}

.bracket-control-btn::after {
  border: none;
}

.preview-mask {
  position: fixed;
  inset: 0;
  z-index: 80;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 24rpx;
  background: rgba(9, 15, 22, 0.72);
}

.qualification-mask {
  position: fixed;
  inset: 0;
  z-index: 90;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40rpx 24rpx;
  background: rgba(9, 15, 22, 0.72);
}

.qualification-dialog {
  width: 100%;
  max-width: 680rpx;
  max-height: 78vh;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  border-radius: 20rpx;
  background: #13202d;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.35);
  overflow: hidden;
}

.qualification-header {
  padding: 22rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.qualification-title,
.qualification-subtitle,
.qualification-group-title,
.qualification-group-hint,
.qualification-candidate-name,
.qualification-candidate-stat {
  display: block;
}

.qualification-title {
  color: #ffffff;
  font-size: 30rpx;
  font-weight: 700;
}

.qualification-subtitle {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
  line-height: 1.4;
}

.qualification-list {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  min-height: 180rpx;
  padding: 18rpx 22rpx;
}

.qualification-group {
  margin-bottom: 22rpx;
}

.qualification-group-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 12rpx;
}

.qualification-group-title {
  color: #ffb347;
  font-size: 28rpx;
  font-weight: 700;
}

.qualification-group-hint {
  color: rgba(255, 255, 255, 0.55);
  font-size: 22rpx;
}

.qualification-candidate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  width: 100%;
  box-sizing: border-box;
  min-height: 70rpx;
  margin-bottom: 10rpx;
  padding: 0 18rpx;
  border: 1rpx solid rgba(255, 255, 255, 0.1);
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.05);
}

.qualification-candidate.selected {
  border-color: rgba(255, 140, 0, 0.68);
  background: rgba(255, 140, 0, 0.16);
}

.qualification-candidate-name {
  flex: 1 1 auto;
  min-width: 0;
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qualification-candidate-stat {
  flex: 0 1 auto;
  min-width: 0;
  max-width: 55%;
  color: rgba(255, 255, 255, 0.58);
  font-size: 22rpx;
  overflow: hidden;
  text-align: right;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.qualification-footer {
  display: flex;
  gap: 12rpx;
  padding: 16rpx 22rpx 22rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
}

.preview-dialog {
  width: 100%;
  max-width: 680rpx;
  height: 75vh;
  display: flex;
  flex-direction: column;
  border-radius: 20rpx;
  background: #13202d;
  border: 1rpx solid rgba(255, 255, 255, 0.08);
  box-shadow: 0 20rpx 60rpx rgba(0, 0, 0, 0.35);
  overflow: hidden;
}

.preview-header {
  padding: 20rpx 22rpx 14rpx;
  border-bottom: 1rpx solid rgba(255, 255, 255, 0.08);
}

.preview-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #ffffff;
}

.preview-subtitle {
  display: block;
  margin-top: 6rpx;
  font-size: 22rpx;
  line-height: 1.35;
  color: rgba(255, 255, 255, 0.55);
}

.preview-mode-bar {
  display: flex;
  gap: 10rpx;
  padding: 14rpx 22rpx 0;
  flex-wrap: wrap;
}

.preview-mode-btn {
  height: 58rpx;
  line-height: 58rpx;
  padding: 0 16rpx;
  border-radius: 10rpx;
  border: none;
  background: rgba(255, 255, 255, 0.08);
  color: rgba(255, 255, 255, 0.7);
  font-size: 22rpx;
}

.preview-mode-btn.active {
  background: rgba(255, 140, 0, 0.18);
  color: #ffcf8a;
}

.preview-mode-btn::after,
.preview-adjust-btn::after {
  border: none;
}

.preview-swap-bar {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 12rpx 22rpx 0;
  min-height: 44rpx;
  flex-shrink: 0;
}

.preview-swap-hint {
  flex: 0 1 auto;
  min-width: 0;
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
  line-height: 1.4;
  text-align: center;
}

.preview-swap-hint.active {
  color: #ffcf8a;
}

.preview-list {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 12rpx 22rpx 6rpx;
  box-sizing: border-box;
}

.preview-loading,
.preview-empty {
  min-height: 260rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.preview-loading-text,
.preview-empty-text {
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
}

.preview-match {
  padding: 14rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.05);
  border: 1rpx solid rgba(255, 255, 255, 0.06);
  margin-bottom: 10rpx;
}

.preview-match-head {
  display: flex;
  justify-content: space-between;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.preview-match-index {
  color: #ffb347;
  font-size: 24rpx;
  font-weight: 700;
}

.preview-side {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14rpx;
  padding: 10rpx 12rpx;
  border-radius: 10rpx;
  background: rgba(19, 32, 45, 0.86);
  border: 1rpx solid transparent;
}

.preview-side + .preview-side {
  margin-top: 8rpx;
}

.preview-side-name {
  display: block;
  min-width: 0;
  flex: 1;
  color: #ffffff;
  font-size: 24rpx;
  line-height: 1.45;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.preview-side-slot {
  flex-shrink: 0;
  color: rgba(255, 255, 255, 0.52);
  font-size: 22rpx;
  line-height: 1.45;
}

.preview-adjust-footer {
  padding: 10rpx 22rpx 0;
  flex-shrink: 0;
}

.preview-adjust-btn {
  width: 100%;
  height: 62rpx;
  line-height: 62rpx;
  padding: 0;
  border-radius: 12rpx;
  border: none;
  background: rgba(255, 255, 255, 0.1);
  color: rgba(255, 255, 255, 0.86);
  font-size: 25rpx;
  font-weight: 700;
}

.preview-adjust-btn.active {
  background: #ff8c00;
  color: #13202d;
}

.preview-footer {
  display: flex;
  gap: 10rpx;
  padding: 14rpx 22rpx 18rpx;
  border-top: 1rpx solid rgba(255, 255, 255, 0.08);
  flex-shrink: 0;
}

.preview-btn {
  flex: 1;
  height: 64rpx;
  line-height: 64rpx;
  padding: 0;
  border-radius: 12rpx;
  font-size: 25rpx;
  font-weight: 700;
}

.preview-btn.ghost {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.preview-btn.secondary {
  background: rgba(255, 255, 255, 0.12);
  color: rgba(255, 255, 255, 0.88);
}

.preview-btn.primary {
  background: #ff8c00;
  color: #1a2a3a;
}

.preview-btn::after {
  border: none;
}

.swap-active .preview-match {
  background: rgba(0, 0, 0, 0.26);
}

.swap-active .preview-header,
.swap-active .preview-mode-bar,
.swap-active .preview-footer {
  opacity: 0.34;
}

.preview-side.swap-selectable {
  background: rgba(255, 255, 255, 0.12);
  border-color: rgba(255, 207, 138, 0.5);
  box-shadow: 0 0 0 1rpx rgba(255, 207, 138, 0.2);
}

.preview-side.selected {
  background: rgba(255, 140, 0, 0.22);
  border-color: #ff8c00;
  box-shadow: 0 0 0 2rpx rgba(255, 140, 0, 0.45);
}
</style>

