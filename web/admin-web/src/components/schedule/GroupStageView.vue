<template>
  <div class="group-stage-view">
    <!-- 淘汰赛状态提示条（仅小组+淘汰赛有） -->
    <div v-if="knockoutBanner" class="group-knockout-banner" :class="knockoutBanner.type">
      <div class="banner-content">
        <span class="banner-icon">{{ knockoutBanner.icon }}</span>
        <div class="banner-text">
          <strong>{{ knockoutBanner.title }}</strong>
          <p>{{ knockoutBanner.desc }}</p>
        </div>
      </div>
      <div v-if="canGenerateKnockout" class="banner-action">
        <button
          class="secondary-action small"
          type="button"
          :disabled="generating"
          @click="handleGenerateKnockout"
        >
          {{ generating ? '生成中...' : '生成淘汰赛' }}
        </button>
      </div>
      <div v-else-if="knockoutGenerated" class="banner-action">
        <button class="ghost-action small" type="button" @click="$emit('switch-to-knockout')">
          查看淘汰赛对阵 &rarr;
        </button>
      </div>
    </div>

    <!-- 分组展示 -->
    <div v-if="groups.length" class="groups-grid">
      <section
        v-for="group in groups"
        :key="getGroupKey(group)"
        class="panel group-panel"
      >
        <div class="panel-head">
          <div class="group-head-title">
            <h2>{{ groupTitle(group) }}</h2>
            <span v-if="qualifiersPerGroup > 0" class="group-qualify-pill">
              前 {{ qualifiersPerGroup }} 名晋级
            </span>
          </div>
          <span class="muted">{{ groupPlayersCount(group) }} 个参赛单位</span>
        </div>

        <!-- 积分榜 -->
        <div class="standing-section">
          <div v-if="getGroupStandingsList(group).length" class="table-wrap standings-table-wrap">
            <table class="standings-table">
              <thead>
                <tr>
                  <th class="col-rank">排名</th>
                  <th class="col-name">{{ isTeamMatch ? '队伍' : '选手' }}</th>
                  <th v-for="col in standingColumns" :key="col.key" class="col-stat">
                    {{ col.label }}
                  </th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="standing in getGroupStandingsList(group)"
                  :key="standing.playerId"
                  :class="{ 'is-qualified': standing.qualified }"
                >
                  <td class="col-rank">
                    <span class="rank-badge" :class="`rank-${standing.rank}`">
                      {{ getStandingRank(standing) }}
                    </span>
                  </td>
                  <td class="col-name">
                    <span class="standing-player-name">{{ standing.playerName || '未知' }}</span>
                    <span v-if="standing.qualified" class="qualify-tag">出线</span>
                  </td>
                  <td v-for="col in standingColumns" :key="col.key" class="col-stat">
                    {{ getStandingCell(standing, col.key) }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <div v-else class="empty-state compact">
            暂无积分数据（首场比赛结束后自动更新）
          </div>
        </div>

        <!-- 按轮次比赛列表 -->
        <div class="group-matches-section">
          <div
            v-for="round in getRounds(group)"
            :key="round.roundNum"
            class="group-round-block"
          >
            <h3 class="group-round-title">第 {{ round.roundNum }} 轮</h3>
            <div class="group-match-cards">
              <MatchCard
                v-for="m in round.matches"
                :key="m.id"
                :match="m"
                :left-name="getPlayerName(m.leftPlayerId)"
                :right-name="getPlayerName(m.rightPlayerId)"
                :is-team-match="isTeamMatch"
                :is-volleyball="isVolleyball"
                @click-match="$emit('click-match', m)"
              />
            </div>
          </div>

          <div v-if="!getRounds(group).length" class="empty-state compact">
            该组暂无对阵
          </div>
        </div>
      </section>
    </div>

    <div v-else class="panel empty-state">
      暂未查到小组赛程数据
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import MatchCard from './MatchCard.vue'
import {
  findStandings,
  getStandingCellText,
  getStandingRankText,
  groupMatchesByRound,
  groupName,
  resolveStandingColumns,
} from '../../utils/groupStandings'
import { generateTournamentKnockout } from '../../services/api'

const props = defineProps({
  tournamentId: { type: String, required: true },
  divisionId: { type: String, default: '' },
  groupsData: { type: Object, default: () => ({ groups: [] }) },
  standingsData: { type: Object, default: () => ({ groups: [] }) },
  players: { type: Array, default: () => [] },
  tournamentInfo: { type: Object, default: () => ({}) },
  isTeamMatch: { type: Boolean, default: false },
  isVolleyball: { type: Boolean, default: false },
})

const emit = defineEmits(['click-match', 'switch-to-knockout', 'refresh-schedule'])

const generating = ref(false)

const groups = computed(() => {
  return Array.isArray(props.groupsData?.groups) ? props.groupsData.groups : []
})

const qualifiersPerGroup = computed(() => {
  return Number(props.groupsData?.qualifiersPerGroup || props.tournamentInfo?.qualifiersPerGroup || 0)
})

const knockoutGenerated = computed(() => {
  return !!props.tournamentInfo?.knockoutGenerated
})

const allGroupMatchesFinished = computed(() => {
  return !!props.standingsData?.allGroupMatchesFinished
})

const hasUnresolvedTie = computed(() => {
  return !!props.standingsData?.hasUnresolvedTie
})

const canManageKnockout = computed(() => {
  return !!props.tournamentInfo?.canManageKnockout
})

const canGenerateKnockout = computed(() => {
  return canManageKnockout.value && !knockoutGenerated.value && allGroupMatchesFinished.value && !hasUnresolvedTie.value
})

const playerMap = computed(() => {
  const map = new Map()
  const setPlayer = (id, name) => {
    const sid = String(id ?? '')
    if (sid && name && !map.has(sid)) {
      map.set(sid, String(name))
    }
  }
  for (const p of props.players || []) {
    setPlayer(p.id ?? p.playerId, p.name ?? p.playerName)
  }
  for (const g of props.groupsData?.groups || []) {
    for (const p of g.players || []) {
      setPlayer(p.id ?? p.playerId, p.name ?? p.playerName)
    }
  }
  for (const g of props.standingsData?.groups || []) {
    for (const s of g.standings || []) {
      setPlayer(s.playerId ?? s.id, s.playerName ?? s.name)
    }
  }
  return map
})

const standingColumns = computed(() => {
  return resolveStandingColumns({
    rankingTemplate: props.tournamentInfo?.rankingTemplate,
    isTeamTournament: props.isTeamMatch,
    isVolleyball: props.isVolleyball,
    teamMatchTemplate: props.tournamentInfo?.teamMatchTemplate,
  })
})

const knockoutBanner = computed(() => {
  if (knockoutGenerated.value) {
    return {
      type: 'info',
      icon: '🏆',
      title: '淘汰赛已生成',
      desc: '小组赛晋级资格已确认，淘汰赛对阵图已就绪。',
    }
  }
  if (!allGroupMatchesFinished.value) {
    return {
      type: 'pending',
      icon: '⏳',
      title: '小组赛进行中',
      desc: '所有小组赛场次全部结束后，将根据积分榜名次生成后续淘汰赛对阵。',
    }
  }
  if (hasUnresolvedTie.value) {
    return {
      type: 'warn',
      icon: '⚠️',
      title: '存在同分待裁定',
      desc: '积分榜存在无法自动判定的并列名次，需裁判/主办方确认出线名单后方可生成淘汰赛。',
    }
  }
  return {
    type: 'ready',
    icon: '✨',
    title: '小组赛已全部打完',
    desc: '出线名单已决出，赛事创建者可点击生成淘汰赛晋级对阵。',
  }
})

function getGroupKey(group) {
  return group?.groupNo ?? group?.group_no ?? Math.random()
}

function groupTitle(group) {
  const gNo = group?.groupNo ?? group?.group_no
  return groupName(gNo)
}

function groupPlayersCount(group) {
  if (Array.isArray(group?.players)) return group.players.length
  return 0
}

function getGroupStandingsList(group) {
  const gNo = group?.groupNo ?? group?.group_no
  return findStandings(props.standingsData, gNo)
}

function getStandingRank(standing) {
  return getStandingRankText(standing)
}

function getStandingCell(standing, colKey) {
  return getStandingCellText(standing, colKey)
}

function getPlayerName(id) {
  if (!id) return '待定'
  return playerMap.value.get(String(id)) || '待定'
}

function getRounds(group) {
  return groupMatchesByRound(group?.matches)
}

async function handleGenerateKnockout() {
  if (!confirm('确认生成淘汰赛对阵？生成后小组出线者将排入淘汰赛签表。')) return
  generating.value = true
  try {
    await generateTournamentKnockout(props.tournamentId, props.divisionId)
    emit('refresh-schedule')
    emit('switch-to-knockout')
  } catch (err) {
    alert(err?.message || '生成淘汰赛失败')
  } finally {
    generating.value = false
  }
}
</script>
