<template>
  <div class="app-shell schedule-shell">
    <header class="app-header">
      <RouterLink class="brand-link" to="/lobby">
        <strong>Eunomia</strong>
      </RouterLink>
      <nav class="app-nav">
        <RouterLink to="/lobby">赛事大厅</RouterLink>
        <RouterLink to="/create">创建比赛</RouterLink>
      </nav>
      <div class="account-area">
        <ThemeSwitcher />
        <div class="user-pill">{{ profile?.nickname || '工作台用户' }}</div>
        <button class="ghost-action small" @click="logout">退出登录</button>
      </div>
    </header>

    <main class="content schedule-content">
      <!-- 顶部信息栏 -->
      <div class="schedule-topbar">
        <div class="topbar-left">
          <button class="ghost-action small back-btn" type="button" @click="goBack">
            &larr; 返回赛事大厅
          </button>
          <div class="tournament-title-row">
            <h1 class="tournament-title">{{ tournament?.name || '赛事赛程' }}</h1>
            <span class="sport-badge">{{ sportLabel }}</span>
            <span class="status-badge" :class="`status-${tournament?.status ?? 0}`">
              {{ statusLabel }}
            </span>
          </div>
          <div class="tournament-meta-row">
            <span v-if="tournament?.location" class="meta-item">📍 {{ tournament.location }}</span>
            <span class="meta-item">🎯 赛制：{{ activeFormatLabel }}</span>
            <span v-if="ruleDetailsLabel" class="meta-item">📏 规则：{{ ruleDetailsLabel }}</span>
          </div>
        </div>

        <div class="topbar-right">
          <button class="ghost-action small" type="button" :disabled="loading" @click="reloadAll">
            {{ loading ? '刷新中…' : '刷新赛程' }}
          </button>
        </div>
      </div>

      <!-- 提示消息 -->
      <div v-if="toastMessage" class="schedule-floating-toast">
        {{ toastMessage }}
      </div>

      <!-- 错误提示 -->
      <p v-if="error" class="error-text schedule-error">{{ error }}</p>

      <!-- 多组别切换栏 -->
      <section v-if="divisions.length > 1" class="panel division-panel">
        <div class="panel-head compact">
          <span class="panel-subtitle">选择赛程组别</span>
          <span class="muted">共 {{ divisions.length }} 个组别</span>
        </div>
        <div class="division-tabs-bar">
          <button
            v-for="div in divisions"
            :key="div.divisionId"
            type="button"
            class="division-pill"
            :class="{ active: String(div.divisionId) === String(activeDivisionId) }"
            @click="switchDivision(div.divisionId)"
          >
            <span class="pill-name">{{ div.name || '未命名组别' }}</span>
            <span v-if="div.playerCount" class="pill-count">{{ div.playerCount }}人</span>
          </button>
        </div>
      </section>

      <!-- 阶段选项卡（仅小组+淘汰赛有效） -->
      <div v-if="effectiveTournamentType === 1" class="stage-nav-bar">
        <button
          type="button"
          class="stage-tab-btn"
          :class="{ active: activeStage === 'group' }"
          @click="activeStage = 'group'"
        >
          小组赛阶段
        </button>
        <button
          type="button"
          class="stage-tab-btn"
          :class="{ active: activeStage === 'knockout' }"
          @click="activeStage = 'knockout'"
        >
          淘汰赛阶段
          <span v-if="isKnockoutGenerated" class="tab-dot" />
        </button>
      </div>

      <!-- 主视图区域 -->
      <div v-if="loading && !hasAnyData" class="panel schedule-loading-panel">
        <p class="muted">正在加载赛程与对阵数据…</p>
      </div>

      <div v-else class="schedule-main-view">
        <!-- 赛制 0：纯淘汰赛 -->
        <template v-if="effectiveTournamentType === 0">
          <section v-if="bracketMatches.length" class="panel bracket-panel">
            <div class="panel-head">
              <h2>淘汰赛晋级对阵图</h2>
              <span class="muted">支持鼠标拖拽平移与滚轮缩放，点击已完赛场次查看赛后战报</span>
            </div>
            <KnockoutBracket
              :matches="bracketMatches"
              :players="currentPlayers"
              :is-team-match="isTeamMatch"
              :is-volleyball="isVolleyball"
              @click-match="handleMatchClick"
            />
          </section>
          <div v-else class="panel empty-state">
            暂无淘汰赛对阵数据
          </div>
        </template>

        <!-- 赛制 1：小组 + 淘汰赛 -->
        <template v-else-if="effectiveTournamentType === 1">
          <!-- 小组赛标签 -->
          <div v-show="activeStage === 'group'">
            <GroupStageView
              :tournament-id="tournamentId"
              :division-id="activeDivisionId"
              :groups-data="groupsData"
              :standings-data="standingsData"
              :players="currentPlayers"
              :tournament-info="activeDivisionInfo"
              :is-team-match="isTeamMatch"
              :is-volleyball="isVolleyball"
              @click-match="handleMatchClick"
              @switch-to-knockout="activeStage = 'knockout'"
              @refresh-schedule="reloadAll"
            />
          </div>

          <!-- 淘汰赛标签 -->
          <div v-show="activeStage === 'knockout'">
            <section v-if="bracketMatches.length" class="panel bracket-panel">
              <div class="panel-head">
                <h2>淘汰赛晋级对阵图</h2>
                <span class="muted">支持鼠标拖拽平移与滚轮缩放，点击已完赛场次查看赛后战报</span>
              </div>
              <KnockoutBracket
                :matches="bracketMatches"
                :players="currentPlayers"
                :is-team-match="isTeamMatch"
                :is-volleyball="isVolleyball"
                @click-match="handleMatchClick"
              />
            </section>
            <div v-else class="panel empty-state">
              <div class="empty-bracket-prompt">
                <span class="prompt-icon">📋</span>
                <h3>淘汰赛尚未生成</h3>
                <p>待小组赛全部完赛并决出出线名单后，淘汰赛对阵将在此展示。</p>
                <button
                  class="ghost-action small"
                  type="button"
                  @click="activeStage = 'group'"
                >
                  &larr; 返回查看小组赛积分与赛程
                </button>
              </div>
            </div>
          </div>
        </template>

        <!-- 赛制 2：循环赛 -->
        <template v-else-if="effectiveTournamentType === 2">
          <RoundRobinView
            :groups-data="groupsData"
            :standings-data="standingsData"
            :players="currentPlayers"
            :tournament-info="activeDivisionInfo"
            :is-team-match="isTeamMatch"
            :is-volleyball="isVolleyball"
            @click-match="handleMatchClick"
          />
        </template>
      </div>
    </main>

    <!-- 赛后战报抽屉 -->
    <MatchRecordDrawer
      v-model:visible="drawerVisible"
      :match-id="selectedMatchId"
      :tournament-name="tournament?.name"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import KnockoutBracket from '../components/schedule/KnockoutBracket.vue'
import GroupStageView from '../components/schedule/GroupStageView.vue'
import RoundRobinView from '../components/schedule/RoundRobinView.vue'
import MatchRecordDrawer from '../components/schedule/MatchRecordDrawer.vue'
import {
  clearToken,
  fetchMe,
  fetchTournamentBracket,
  fetchTournamentDetail,
  fetchTournamentDivisions,
  fetchTournamentGroups,
  fetchTournamentGroupStandings,
} from '../services/api'
import { sportText, tournamentTypeText } from '../utils/matchFormatters'

const route = useRoute()
const router = useRouter()

const tournamentId = computed(() => String(route.params.id || ''))

const profile = ref(null)
const tournament = ref(null)
const divisions = ref([])
const activeDivisionId = ref('')
const activeStage = ref('group') // 'group' | 'knockout'

const bracketData = ref({ matches: [], players: [] })
const groupsData = ref({ groups: [] })
const standingsData = ref({ groups: [] })

const loading = ref(false)
const error = ref('')
const toastMessage = ref('')
let toastTimer = null

// 战报抽屉状态
const drawerVisible = ref(false)
const selectedMatchId = ref('')

const isTeamMatch = computed(() => {
  return Number(tournament.value?.participantType) === 1
})

const isVolleyball = computed(() => {
  return Number(tournament.value?.sportType) === 1
})

const sportLabel = computed(() => {
  return sportText(tournament.value)
})

const statusLabel = computed(() => {
  const s = Number(tournament.value?.status ?? 0)
  if (s === 2) return '已结束'
  if (s === 1) return '进行中'
  return '未开始'
})

const activeDivision = computed(() => {
  if (!activeDivisionId.value || !divisions.value.length) return null
  return divisions.value.find((d) => String(d.divisionId) === String(activeDivisionId.value)) || null
})

const effectiveTournamentType = computed(() => {
  if (activeDivision.value && activeDivision.value.tournamentType != null) {
    return Number(activeDivision.value.tournamentType)
  }
  return Number(tournament.value?.tournamentType ?? 0)
})

const activeFormatLabel = computed(() => {
  return tournamentTypeText(effectiveTournamentType.value)
})

const ruleDetailsLabel = computed(() => {
  const bestOf = tournament.value?.bestOf || activeDivision.value?.bestOf
  const points = tournament.value?.pointsToWin || activeDivision.value?.pointsToWin
  if (bestOf && points) {
    return `${bestOf}局${tournament.value?.gamesToWin || Math.ceil(bestOf / 2)}胜，每局${points}分`
  }
  return ''
})

const isKnockoutGenerated = computed(() => {
  if (activeDivision.value) return !!activeDivision.value.knockoutGenerated
  return !!tournament.value?.knockoutGenerated
})

const activeDivisionInfo = computed(() => {
  return {
    ...(tournament.value || {}),
    ...(activeDivision.value || {}),
  }
})

const currentPlayers = computed(() => {
  const list = []
  const seen = new Set()
  const addPlayer = (p) => {
    const id = String(p?.id ?? p?.playerId ?? '')
    const name = p?.name ?? p?.playerName ?? ''
    if (id && !seen.has(id)) {
      seen.add(id)
      list.push({ id, name, ...p })
    }
  }
  for (const p of bracketData.value?.players || []) addPlayer(p)
  for (const g of groupsData.value?.groups || []) {
    for (const p of g?.players || []) addPlayer(p)
  }
  for (const g of standingsData.value?.groups || []) {
    for (const s of g?.standings || []) addPlayer({ id: s.playerId, name: s.playerName, ...s })
  }
  return list
})

const bracketMatches = computed(() => {
  return Array.isArray(bracketData.value?.matches) ? bracketData.value.matches : []
})

const hasAnyData = computed(() => {
  return bracketMatches.value.length > 0 || (groupsData.value?.groups?.length ?? 0) > 0
})

function showToast(msg) {
  toastMessage.value = msg
  if (toastTimer) clearTimeout(toastTimer)
  toastTimer = setTimeout(() => {
    toastMessage.value = ''
  }, 3500)
}

function goBack() {
  router.push('/lobby')
}

function logout() {
  clearToken()
  router.replace('/login')
}

function handleMatchClick(match) {
  if (!match) return
  const s = Number(match.status ?? 0)
  if (s === 2) {
    // 已完赛：打开赛后战报抽屉
    selectedMatchId.value = String(match.id ?? match.matchId ?? '')
    drawerVisible.value = true
  } else {
    // 未开赛或进行中：提示网页端边界
    const leftName = match.leftName || '待定'
    const rightName = match.rightName || '待定'
    showToast(`场次 [${leftName} vs ${rightName}] 尚未完赛。网页端仅提供赛后战报查阅，执裁与计分请使用微信小程序。`)
  }
}

async function loadTournamentOverview() {
  if (!tournamentId.value) return
  try {
    const [me, tData, divList] = await Promise.all([
      fetchMe().catch(() => null),
      fetchTournamentDetail(tournamentId.value),
      fetchTournamentDivisions(tournamentId.value).catch(() => []),
    ])
    profile.value = me
    tournament.value = tData
    divisions.value = Array.isArray(divList) ? divList : []

    // 默认激活组别：优先从 route query 取，其次第一个组别，其次空
    const queryDiv = route.query.divisionId
    if (queryDiv && divisions.value.some((d) => String(d.divisionId) === String(queryDiv))) {
      activeDivisionId.value = String(queryDiv)
    } else if (divisions.value.length) {
      activeDivisionId.value = String(divisions.value[0].divisionId)
    }
  } catch (err) {
    error.value = err?.message || '加载赛事基本信息失败'
  }
}

let currentRequestId = 0

async function loadScheduleData() {
  if (!tournamentId.value) return
  const reqId = ++currentRequestId
  loading.value = true
  error.value = ''
  const tType = effectiveTournamentType.value
  const divId = activeDivisionId.value

  try {
    if (tType === 0) {
      // 纯淘汰赛：只拉 bracket
      const bRes = await fetchTournamentBracket(tournamentId.value, divId)
      if (reqId !== currentRequestId) return
      bracketData.value = bRes || { matches: [], players: [] }
      groupsData.value = { groups: [] }
      standingsData.value = { groups: [] }
    } else if (tType === 1) {
      // 小组 + 淘汰：并发拉取 groups, standings, bracket
      const [gRes, sRes, bRes] = await Promise.all([
        fetchTournamentGroups(tournamentId.value, divId).catch(() => ({ groups: [] })),
        fetchTournamentGroupStandings(tournamentId.value, divId).catch(() => ({ groups: [] })),
        fetchTournamentBracket(tournamentId.value, divId).catch(() => ({ matches: [], players: [] })),
      ])
      if (reqId !== currentRequestId) return
      groupsData.value = gRes || { groups: [] }
      standingsData.value = sRes || { groups: [] }
      bracketData.value = bRes || { matches: [], players: [] }

      // 若淘汰赛已有对阵且小组已完赛，默认展示淘汰赛标签；否则默认小组赛
      if (bRes?.matches?.length && activeStage.value !== 'knockout' && sRes?.allGroupMatchesFinished) {
        activeStage.value = 'knockout'
      }
    } else if (tType === 2) {
      // 循环赛：拉取 groups 与 standings
      const [gRes, sRes] = await Promise.all([
        fetchTournamentGroups(tournamentId.value, divId).catch(() => ({ groups: [] })),
        fetchTournamentGroupStandings(tournamentId.value, divId).catch(() => ({ groups: [] })),
      ])
      if (reqId !== currentRequestId) return
      groupsData.value = gRes || { groups: [] }
      standingsData.value = sRes || { groups: [] }
      bracketData.value = { matches: [], players: [] }
    }
  } catch (err) {
    if (reqId !== currentRequestId) return
    error.value = err?.message || '加载赛程数据失败'
  } finally {
    if (reqId === currentRequestId) {
      loading.value = false
    }
  }
}

async function reloadAll() {
  await loadTournamentOverview()
  await loadScheduleData()
}

function switchDivision(divId) {
  if (String(divId) === String(activeDivisionId.value)) return
  activeDivisionId.value = String(divId)
  router.replace({
    query: { ...route.query, divisionId: String(divId) },
  })
}

watch(
  () => activeDivisionId.value,
  () => {
    loadScheduleData()
  },
)

onMounted(async () => {
  await loadTournamentOverview()
  await loadScheduleData()
})

onUnmounted(() => {
  if (toastTimer) {
    clearTimeout(toastTimer)
    toastTimer = null
  }
})
</script>
