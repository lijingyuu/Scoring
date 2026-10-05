<template>
  <div class="app-shell">
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

    <main class="content">
      <div class="topbar">
        <div>
          <p class="eyebrow">手写签表</p>
          <h1>调整签位</h1>
        </div>
        <button class="ghost-action" type="button" @click="router.push('/lobby')">返回赛事大厅</button>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>
      <p v-if="success" class="success-text">{{ success }}</p>
      <p v-if="loading" class="muted">组别加载中…</p>

      <template v-else-if="knockoutDivisions.length">
        <section v-if="knockoutDivisions.length > 1" class="panel">
          <div class="panel-head">
            <h2>选择组别</h2>
            <span class="muted">共 {{ knockoutDivisions.length }} 个可调整签位的组别</span>
          </div>
          <div class="draw-division-tabs">
            <button
              v-for="division in knockoutDivisions"
              :key="division.divisionId"
              type="button"
              class="ghost-action small"
              :class="{ active: String(division.divisionId) === String(activeDivisionId) }"
              @click="selectDivision(division)"
            >
              {{ division.name || '未命名组别' }}
            </button>
          </div>
        </section>

        <section class="panel">
          <div class="panel-head">
            <h2>{{ activeDivisionName }}</h2>
            <span class="muted">
              当前抽签方式：{{ drawModeText(activeDivision?.drawMode) }}
              <template v-if="activeDivision?.playerCount"> · {{ activeDivision.playerCount }} 个参赛单位</template>
            </span>
          </div>

          <p v-if="bracketLoading" class="draw-bracket-loading">签表加载中…</p>
          <template v-else-if="slots.length >= 2">
            <template v-if="editable">
              <p class="draw-slot-hint">
                <template v-if="isManualGroupDivision">
                  这是小组+淘汰赛的出线签位（无轮空）：点击签位选中后，在下方名单面板点选手填入；出线选手必须全部安排且不能有空位，签位集合需与当前出线选手一致，保存后签表立即更新。
                </template>
                <template v-else>
                  点击签位选中后，在下方名单面板点选手填入或选「轮空位」；同一场比赛的两个签位不能都是轮空，保存后签表立即更新。
                </template>
              </p>
              <DrawSlotEditor v-model="slots" :roster="roster" :allow-bye="!isManualGroupDivision" />
              <p v-if="validationError" class="error-text draw-slot-validation">{{ validationError }}</p>
              <div class="draw-slot-submit">
                <button
                  class="secondary-action match-submit-action"
                  type="button"
                  :disabled="saving || !!validationError"
                  @click="submitSlots"
                >
                  {{ saving ? '保存中...' : '保存签位' }}
                </button>
              </div>
            </template>
            <template v-else>
              <p class="draw-slot-locked">已有比赛开始，签表不可编辑</p>
              <div class="bracket-readonly">
                <div v-for="round in bracketRounds" :key="round.roundNum" class="bracket-round">
                  <h3>第 {{ round.roundNum }} 轮</h3>
                  <div v-for="match in round.matches" :key="match.id" class="bracket-match">
                    <span class="bracket-side" :class="{ winner: isWinner(match, match.leftPlayerId) }">
                      {{ playerText(match.leftPlayerId) }}
                    </span>
                    <span class="bracket-vs">vs</span>
                    <span class="bracket-side" :class="{ winner: isWinner(match, match.rightPlayerId) }">
                      {{ playerText(match.rightPlayerId) }}
                    </span>
                    <span class="bracket-status muted">{{ matchStatusText(match) }}</span>
                  </div>
                </div>
              </div>
            </template>
          </template>
          <p v-else class="muted">未能从签表中推导出签位，请确认该组别已生成纯淘汰签表。</p>
        </section>
      </template>

      <p v-else class="panel">该赛事没有可调整签位的组别。</p>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { RouterLink, useRoute, useRouter } from 'vue-router'
import {
  clearToken,
  fetchDivisionBracket,
  fetchMe,
  fetchTournamentDivisions,
  updateDivisionDrawSlots,
} from '../services/api'
import DrawSlotEditor from '../components/DrawSlotEditor.vue'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'
import { DRAW_SLOT_EMPTY, validateDrawSlots } from '../utils/drawSlots'

const route = useRoute()
const router = useRouter()
const tournamentId = route.params.id

const profile = ref(null)
const divisions = ref([])
const activeDivisionId = ref('')
const bracket = ref(null)
const slots = ref([])
const loading = ref(true)
const bracketLoading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

/** 契约：drawMode 为 Integer —— 0=自动抽签、1=手写签表(type0)、2=手写分组(type1) */
function drawModeText(value) {
  if (Number(value) === 1) return '手写签表'
  if (Number(value) === 2) return '手写分组'
  return '自动抽签'
}

function isBlank(value) {
  return value === null || value === undefined || value === ''
}

/** 可调整签位的组别：type0 手写签表(drawMode=1)，或 type1 且淘汰赛已生成（可重排出线者签位） */
const knockoutDivisions = computed(() => divisions.value.filter((division) => {
  const type = Number(division.tournamentType)
  if (type === 0) return Number(division.drawMode) === 1
  if (type === 1) return !!division.knockoutGenerated
  return false
}))
const activeDivision = computed(() => knockoutDivisions.value
  .find((division) => String(division.divisionId) === String(activeDivisionId.value)) || null)
const activeDivisionName = computed(() => activeDivision.value?.name || '可调整签位的组别')
const isManualGroupDivision = computed(() => Number(activeDivision.value?.tournamentType) === 1)
/**
 * 编辑接口以 playerId 为单位。
 * type0：roster = 该组别全部参赛者（bracket.players）。
 * type1：bracket.players 是该组别全部选手（含小组赛未出线者），而出线签位集合必须保持不变，
 *        因此 roster 只取「当前首轮签位上的出线者」，避免未出线选手被误选入签表。
 */
const roster = computed(() => {
  const players = bracket.value?.players || []
  if (!isManualGroupDivision.value) {
    return players.map((player) => ({ key: String(player.id), label: player.name }))
  }
  const labelByKey = new Map(players.map((player) => [String(player.id), player.name]))
  return initialSlotKeys.value.map((key) => ({ key, label: labelByKey.get(key) || '未知选手' }))
})
const validation = computed(() => validateDrawSlots(slots.value, roster.value.map((item) => item.key)))
/**
 * 客户端可编辑性：所有比赛未开始、无胜者、无他人锁定（权威判定在后端痕迹法守卫）。
 * 轮空坍缩（单侧为空且胜者=在场侧，创建手写签表时即生成）不是开赛痕迹——与后端
 * updateDivisionDrawSlots 的零开赛判定对齐，否则带轮空的签表永远无法整表重排。
 */
function isByeCollapseWin(match) {
  const left = isBlank(match.leftPlayerId)
  const right = isBlank(match.rightPlayerId)
  if (left === right) return false
  const present = left ? match.rightPlayerId : match.leftPlayerId
  return String(match.winnerId) === String(present)
    && isBlank(match.scoreDisplay)
    && isBlank(match.gameScores)
    && isBlank(match.retiredSide)
}
const editable = computed(() => {
  const matches = bracket.value?.matches || []
  if (!matches.length) return false
  return matches.every((match) => (Number(match.status) === 0
    && isBlank(match.winnerId)
    && isBlank(match.lockedByUserId))
    || (isByeCollapseWin(match) && isBlank(match.lockedByUserId)))
})

const bracketRounds = computed(() => {
  const rounds = new Map()
  for (const match of bracket.value?.matches || []) {
    const roundNum = Number(match.roundNum)
    if (!rounds.has(roundNum)) rounds.set(roundNum, [])
    rounds.get(roundNum).push(match)
  }
  return [...rounds.entries()]
    .sort((left, right) => left[0] - right[0])
    .map(([roundNum, matches]) => ({
      roundNum,
      matches: matches.slice().sort((left, right) => Number(left.matchIndex) - Number(right.matchIndex)),
    }))
})

/** 当前签位顺序：roundNum=1 且 matchRole=0 的比赛按 matchIndex 升序，[left, right, left, right, ...] */
function deriveSlots(data) {
  const roundOne = (data?.matches || [])
    // 防御：只取淘汰赛(stage_type=1)行；字段缺失时按淘汰赛处理（兼容旧数据）
    .filter((match) => isBlank(match.stageType) || Number(match.stageType) === 1)
    .filter((match) => Number(match.roundNum) === 1 && Number(match.matchRole) === 0)
    .slice()
    .sort((left, right) => Number(left.matchIndex) - Number(right.matchIndex))
  return roundOne.flatMap((match) => [
    isBlank(match.leftPlayerId) ? null : String(match.leftPlayerId),
    isBlank(match.rightPlayerId) ? null : String(match.rightPlayerId),
  ])
}

/** 首轮签位选手集合（type1 契约：提交集合必须与之一致），每次载入签表时刷新 */
const initialSlotKeys = ref([])

function toSlotKeys(list) {
  return list.filter((slot) => !isBlank(slot)).map((slot) => String(slot)).sort()
}

/** type1 追加校验：签位数量 === 淘汰名额、无空位、集合与初始出线者完全一致 */
const manualGroupSlotError = computed(() => {
  if (!isManualGroupDivision.value) return ''
  const expected = Number(activeDivision.value?.knockoutSlots)
  if (Number.isFinite(expected) && expected > 0 && slots.value.length !== expected) {
    return `签位数量应为 ${expected} 个（当前 ${slots.value.length} 个）`
  }
  if (slots.value.some((slot) => isBlank(slot))) {
    return '小组+淘汰赛的出线签位不能有空位（无轮空），请为每个签位都安排出线选手'
  }
  const current = toSlotKeys(slots.value)
  if (current.length !== initialSlotKeys.value.length
    || current.some((key, index) => key !== initialSlotKeys.value[index])) {
    return '签位选手集合必须与当前出线选手完全一致（只能调整顺序，不能增删）'
  }
  return ''
})

const validationError = computed(() => (validation.value.ok ? manualGroupSlotError.value : validation.value.message))

function playerText(playerId) {
  if (isBlank(playerId)) return '轮空'
  return roster.value.find((item) => item.key === String(playerId))?.label || '未知选手'
}

function isWinner(match, playerId) {
  if (isBlank(playerId) || isBlank(match.winnerId)) return false
  return String(match.winnerId) === String(playerId)
}

function matchStatusText(match) {
  if (!isBlank(match.winnerId)) return `胜者：${playerText(match.winnerId)}`
  if (Number(match.status) === 1) return '进行中'
  if (Number(match.status) === 2) return '已结束'
  return '未开始'
}

async function loadDivisions() {
  try {
    const list = await fetchTournamentDivisions(tournamentId)
    divisions.value = Array.isArray(list) ? list : []
  } catch (err) {
    divisions.value = []
    error.value = err?.message || '组别加载失败'
  } finally {
    loading.value = false
  }
}

async function loadBracket(divisionId) {
  bracketLoading.value = true
  error.value = ''
  try {
    bracket.value = await fetchDivisionBracket(tournamentId, divisionId)
    slots.value = deriveSlots(bracket.value)
    initialSlotKeys.value = toSlotKeys(slots.value)
  } catch (err) {
    bracket.value = null
    slots.value = []
    error.value = err?.message || '签表加载失败'
  } finally {
    bracketLoading.value = false
  }
}

async function selectDivision(division) {
  if (String(division.divisionId) === String(activeDivisionId.value) && bracket.value) return
  activeDivisionId.value = division.divisionId
  success.value = ''
  await loadBracket(division.divisionId)
}

async function submitSlots() {
  if (validationError.value) {
    error.value = validationError.value
    return
  }
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await updateDivisionDrawSlots(
      tournamentId,
      activeDivisionId.value,
      slots.value.map((slot) => (slot === null || slot === DRAW_SLOT_EMPTY ? null : String(slot))),
    )
    success.value = '签位已保存'
    await loadDivisions()
    await loadBracket(activeDivisionId.value)
  } catch (err) {
    error.value = err?.message || '保存失败'
  } finally {
    saving.value = false
  }
}

function logout() {
  clearToken()
  router.replace('/login')
}

onMounted(async () => {
  try {
    profile.value = await fetchMe()
  } catch {
    profile.value = null
  }
  await loadDivisions()
  const first = knockoutDivisions.value[0]
  if (first) await selectDivision(first)
})
</script>

<style scoped>
.topbar h1 {
  font-size: 24px;
}

.draw-division-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.draw-division-tabs .active {
  color: var(--ink);
  border-color: var(--accent);
  background: var(--accent);
}

.draw-bracket-loading {
  margin-top: 12px;
  color: var(--muted);
}

.draw-slot-hint {
  margin: 12px 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.draw-slot-validation {
  margin: 12px 0 0;
}

.draw-slot-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}

.draw-slot-locked {
  margin: 12px 0;
  padding: 10px 12px;
  border: 1px solid rgba(var(--warn-rgb), 0.42);
  border-radius: 6px;
  color: var(--warn-ink);
  background: var(--warn-bg);
}

.bracket-readonly {
  display: grid;
  gap: 14px;
}

.bracket-round h3 {
  margin-bottom: 8px;
  font-size: 14px;
  color: var(--muted);
}

.bracket-match {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 34px minmax(0, 1fr) minmax(0, 120px);
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: rgba(var(--inset-rgb), 0.12);
}

.bracket-side.winner {
  color: var(--accent);
  font-weight: 800;
}

.bracket-vs {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

.bracket-status {
  font-size: 12px;
  text-align: right;
}
</style>
