<template>
  <div class="round-robin-view">
    <!-- 积分榜面板 -->
    <section class="panel standing-panel">
      <div class="panel-head">
        <div>
          <h2>循环赛积分榜</h2>
          <span class="muted">根据各场比赛胜负与局分自动计算排名</span>
        </div>
        <span class="muted">共 {{ standingsList.length }} 个参赛单位</span>
      </div>

      <div class="standing-section">
        <div v-if="standingsList.length" class="table-wrap standings-table-wrap">
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
                v-for="standing in standingsList"
                :key="standing.playerId"
                :class="{ 'is-top': Number(standing.rank) === 1 }"
              >
                <td class="col-rank">
                  <span class="rank-badge" :class="`rank-${standing.rank}`">
                    {{ getStandingRank(standing) }}
                  </span>
                </td>
                <td class="col-name">
                  <span class="standing-player-name">{{ standing.playerName || '未知' }}</span>
                  <span v-if="Number(standing.rank) === 1" class="top-tag">榜首</span>
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
    </section>

    <!-- 轮次对阵列表 -->
    <section class="panel matches-panel">
      <div class="panel-head">
        <h2>循环赛轮次对阵</h2>
        <span class="muted">共 {{ rounds.length }} 轮比赛</span>
      </div>

      <div v-if="rounds.length" class="rounds-container">
        <div
          v-for="round in rounds"
          :key="round.roundNum"
          class="round-robin-round-block"
        >
          <div class="round-header-row">
            <h3 class="round-title">第 {{ round.roundNum }} 轮</h3>
            <span class="muted small">{{ round.matches.length }} 场比赛</span>
          </div>

          <div class="round-matches-grid">
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
      </div>

      <div v-else class="empty-state">
        暂无对阵数据
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import MatchCard from './MatchCard.vue'
import {
  findStandings,
  getStandingCellText,
  getStandingRankText,
  groupMatchesByRound,
  resolveStandingColumns,
} from '../../utils/groupStandings'

const props = defineProps({
  groupsData: { type: Object, default: () => ({ groups: [] }) },
  standingsData: { type: Object, default: () => ({ groups: [] }) },
  players: { type: Array, default: () => [] },
  tournamentInfo: { type: Object, default: () => ({}) },
  isTeamMatch: { type: Boolean, default: false },
  isVolleyball: { type: Boolean, default: false },
})

defineEmits(['click-match'])

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

const standingsList = computed(() => {
  // 循环赛通常在第一个组别（groupNo 1）内返回全部 standings
  const firstGroupStandings = findStandings(props.standingsData, 1)
  if (firstGroupStandings.length) return firstGroupStandings

  // 若 groupNo 区分不为 1，提取第 0 个分组
  const groups = props.standingsData?.groups
  if (Array.isArray(groups) && groups.length > 0) {
    return groups[0]?.standings || []
  }
  return []
})

const rounds = computed(() => {
  const firstGroup = Array.isArray(props.groupsData?.groups) ? props.groupsData.groups[0] : null
  const matches = firstGroup?.matches || []
  return groupMatchesByRound(matches)
})

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
</script>
