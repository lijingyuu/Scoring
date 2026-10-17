<template>
  <div v-if="visible" class="match-drawer-overlay" @click.self="closeDrawer">
    <div class="match-drawer-panel" role="dialog" aria-modal="true">
      <!-- 头部 -->
      <div class="drawer-header">
        <div class="drawer-title-group">
          <span class="drawer-eyebrow">{{ record?.tournamentName || tournamentName || '赛事战报' }}</span>
          <h2 class="drawer-title">{{ matchStageTitle }}</h2>
        </div>
        <div class="drawer-actions">
          <button class="ghost-action small icon-only" type="button" title="关闭 (Esc)" @click="closeDrawer">
            &times;
          </button>
        </div>
      </div>

      <!-- 加载状态 -->
      <div v-if="loading" class="drawer-state-layer">
        <p class="muted">战报数据加载中…</p>
      </div>

      <!-- 错误状态 -->
      <div v-else-if="error" class="drawer-state-layer">
        <p class="error-text">{{ error }}</p>
        <button class="secondary-action small" type="button" @click="fetchRecord">重新加载</button>
      </div>

      <!-- 内容主体 -->
      <div v-else-if="record" class="drawer-body">
        <!-- 战报封顶总分卡片 -->
        <div class="match-hero-card">
          <div
            class="hero-side left"
            :class="{ 'is-winner': record.winnerSide === 'left' }"
          >
            <div class="hero-name-wrap">
              <span class="hero-label">左侧单位</span>
              <strong class="hero-name">{{ leftParticipantName }}</strong>
            </div>
            <span v-if="record.winnerSide === 'left'" class="hero-winner-tag">获胜方 🏆</span>
          </div>

          <div class="hero-score-center">
            <span class="hero-score-label">总比分</span>
            <div class="hero-score-numbers">
              <span class="score-num left">{{ record.leftGameWins ?? 0 }}</span>
              <span class="score-colon">:</span>
              <span class="score-num right">{{ record.rightGameWins ?? 0 }}</span>
            </div>
            <span v-if="record.scoreDisplay" class="hero-score-sub">
              {{ record.scoreDisplay }}
            </span>
          </div>

          <div
            class="hero-side right"
            :class="{ 'is-winner': record.winnerSide === 'right' }"
          >
            <div class="hero-name-wrap">
              <span class="hero-label">右侧单位</span>
              <strong class="hero-name">{{ rightParticipantName }}</strong>
            </div>
            <span v-if="record.winnerSide === 'right'" class="hero-winner-tag">获胜方 🏆</span>
          </div>
        </div>

        <!-- 局分明细 -->
        <section class="drawer-section">
          <h3 class="section-title">局分明细</h3>
          <div v-if="gameScores.length" class="game-scores-list">
            <div
              v-for="game in gameScores"
              :key="game.gameNo"
              class="game-score-item"
              :class="{
                'left-win': game.winnerSide === 'left',
                'right-win': game.winnerSide === 'right',
              }"
            >
              <span class="game-index">第 {{ game.gameNo }} 局</span>
              <div class="game-score-row">
                <span class="side-score left" :class="{ win: game.winnerSide === 'left' }">
                  {{ game.leftScore ?? '-' }}
                </span>
                <span class="score-sep">-</span>
                <span class="side-score right" :class="{ win: game.winnerSide === 'right' }">
                  {{ game.rightScore ?? '-' }}
                </span>
              </div>
              <span class="game-winner-hint">
                {{ gameWinnerName(game) }}
              </span>
            </div>
          </div>
          <p v-else class="empty-state compact">暂无各局详细比分</p>
        </section>

        <!-- 阵容名单 / 队伍名单 -->
        <section v-if="hasMembers" class="drawer-section">
          <h3 class="section-title">参赛名单</h3>
          <div class="roster-grid">
            <div class="roster-column">
              <h4>{{ leftParticipantName }}</h4>
              <ul v-if="leftMembers.length" class="member-list">
                <li v-for="m in leftMembers" :key="m.id || m.name" class="member-item">
                  <span v-if="m.jerseyNumber != null" class="jersey-num">#{{ m.jerseyNumber }}</span>
                  <span class="member-name">{{ m.name }}</span>
                  <span v-if="m.captain" class="role-pill">队长</span>
                  <span v-if="m.libero" class="role-pill libero">自由人</span>
                </li>
              </ul>
              <p v-else class="muted small">未登记单独名单</p>
            </div>

            <div class="roster-column">
              <h4>{{ rightParticipantName }}</h4>
              <ul v-if="rightMembers.length" class="member-list">
                <li v-for="m in rightMembers" :key="m.id || m.name" class="member-item">
                  <span v-if="m.jerseyNumber != null" class="jersey-num">#{{ m.jerseyNumber }}</span>
                  <span class="member-name">{{ m.name }}</span>
                  <span v-if="m.captain" class="role-pill">队长</span>
                  <span v-if="m.libero" class="role-pill libero">自由人</span>
                </li>
              </ul>
              <p v-else class="muted small">未登记单独名单</p>
            </div>
          </div>
        </section>

        <!-- 比赛基本信息 -->
        <section class="drawer-section">
          <h3 class="section-title">赛制与基本信息</h3>
          <dl class="meta-dl">
            <div class="meta-item">
              <dt>赛制局数</dt>
              <dd>{{ record.bestOf ? `${record.bestOf} 局 ${record.gamesToWin} 胜` : '常规赛制' }}</dd>
            </div>
            <div class="meta-item">
              <dt>获胜局分</dt>
              <dd>{{ record.pointsToWin ? `${record.pointsToWin} 分` : '-' }}</dd>
            </div>
            <div v-if="record.capPoint" class="meta-item">
              <dt>封顶分</dt>
              <dd>{{ record.capPoint }} 分</dd>
            </div>
            <div v-if="record.location" class="meta-item">
              <dt>场地 / 地点</dt>
              <dd>{{ record.location }}</dd>
            </div>
            <div v-if="record.divisionName" class="meta-item">
              <dt>所属组别</dt>
              <dd>{{ record.divisionName }}</dd>
            </div>
            <div class="meta-item">
              <dt>比赛状态</dt>
              <dd>
                <span class="badge-finished">已完赛</span>
                <span v-if="record.retiredSide" class="danger-tag">中途退赛</span>
              </dd>
            </div>
          </dl>
        </section>

        <!-- 战报签名与封存状态 -->
        <section v-if="reportMeta" class="drawer-section">
          <h3 class="section-title">执裁记录</h3>
          <dl class="meta-dl">
            <div v-if="reportMeta.refereeName" class="meta-item">
              <dt>主裁</dt>
              <dd>{{ reportMeta.refereeName }}</dd>
            </div>
            <div v-if="reportMeta.matchDate" class="meta-item">
              <dt>执裁日期</dt>
              <dd>{{ reportMeta.matchDate }}</dd>
            </div>
            <div v-if="reportMeta.notes" class="meta-item full-width">
              <dt>战报备注</dt>
              <dd>{{ reportMeta.notes }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { fetchMatchRecord } from '../../services/api'

const props = defineProps({
  visible: { type: Boolean, default: false },
  matchId: { type: String, default: '' },
  tournamentName: { type: String, default: '' },
})

const emit = defineEmits(['update:visible'])

const loading = ref(false)
const error = ref('')
const record = ref(null)

const matchStageTitle = computed(() => {
  if (!record.value) return '比赛战报'
  if (Number(record.value.matchRole) === 1) return '季军赛战报'
  if (record.value.roundNum) return `第 ${record.value.roundNum} 轮战报`
  return '比赛战报'
})

const leftParticipantName = computed(() => {
  return record.value?.left?.name || '左侧选手/队伍'
})

const rightParticipantName = computed(() => {
  return record.value?.right?.name || '右侧选手/队伍'
})

const gameScores = computed(() => {
  return Array.isArray(record.value?.gameScores) ? record.value.gameScores : []
})

const leftMembers = computed(() => {
  return (
    record.value?.rosterSnapshot?.leftMembers ||
    record.value?.left?.members ||
    []
  )
})

const rightMembers = computed(() => {
  return (
    record.value?.rosterSnapshot?.rightMembers ||
    record.value?.right?.members ||
    []
  )
})

const hasMembers = computed(() => {
  return leftMembers.value.length > 0 || rightMembers.value.length > 0
})

const reportMeta = computed(() => {
  return record.value?.reportMeta || null
})

function gameWinnerName(game) {
  if (game.winnerSide === 'left') return `${leftParticipantName.value} 胜`
  if (game.winnerSide === 'right') return `${rightParticipantName.value} 胜`
  return ''
}

async function fetchRecord() {
  if (!props.matchId) return
  loading.value = true
  error.value = ''
  try {
    const data = await fetchMatchRecord(props.matchId)
    record.value = data
  } catch (err) {
    error.value = err?.message || '加载战报失败'
  } finally {
    loading.value = false
  }
}

function closeDrawer() {
  emit('update:visible', false)
}

function handleKeyDown(e) {
  if (e.key === 'Escape' && props.visible) {
    closeDrawer()
  }
}

watch(
  () => [props.visible, props.matchId],
  ([nextVisible, nextMatchId]) => {
    if (nextVisible) {
      document.body.style.overflow = 'hidden'
      if (nextMatchId) {
        fetchRecord()
      }
    } else {
      document.body.style.overflow = ''
      record.value = null
      error.value = ''
    }
  },
  { immediate: true },
)

onMounted(() => {
  window.addEventListener('keydown', handleKeyDown)
})

onUnmounted(() => {
  document.body.style.overflow = ''
  window.removeEventListener('keydown', handleKeyDown)
})
</script>
