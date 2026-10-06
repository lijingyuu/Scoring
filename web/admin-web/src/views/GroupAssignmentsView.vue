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
          <p class="eyebrow">手写分组</p>
          <h1>调整分组</h1>
        </div>
        <button class="ghost-action" type="button" @click="router.push('/lobby')">返回赛事大厅</button>
      </div>

      <p v-if="error" class="error-text">{{ error }}</p>
      <p v-if="success" class="success-text">{{ success }}</p>
      <p v-if="loading" class="muted">组别加载中…</p>

      <template v-else-if="manualGroupDivisions.length">
        <section v-if="manualGroupDivisions.length > 1" class="panel">
          <div class="panel-head">
            <h2>选择组别</h2>
            <span class="muted">共 {{ manualGroupDivisions.length }} 个手写分组组别</span>
          </div>
          <div class="group-division-tabs">
            <button
              v-for="division in manualGroupDivisions"
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
              当前抽签方式：手写分组
              <template v-if="groupCount"> · {{ groupCount }} 个小组 · 每组至少 {{ minPerGroup }} 人</template>
              <template v-if="roster.length"> · {{ roster.length }} 个参赛单位</template>
            </span>
          </div>

          <p v-if="bracketLoading" class="group-bracket-loading">名单加载中…</p>
          <template v-else-if="roster.length || groupCount">
            <p class="group-assignment-hint">
              组数为「淘汰名额 ÷ 每组出线」（{{ activeDivision?.knockoutSlots ?? '-' }} ÷ {{ activeDivision?.qualifiersPerGroup ?? '-' }}），
              每组人数不少于 {{ minPerGroup }} 人；组内顺序即组内座次，保存后立即生效。小组赛开赛后不可再调整（由服务端校验）。
            </p>
            <p v-if="!groupCount" class="error-text group-assignment-validation">
              无法确定小组数量：请先确认该组别的淘汰名额与每组出线配置（淘汰名额需能被每组出线整除）。
            </p>
            <p v-else-if="groupStageLocked" class="error-text group-assignment-validation">
              小组赛已有开赛记录，分组不可再调整（保存由服务端校验拦截）。
            </p>
            <p v-else-if="unassignedPlayers.length" class="group-unassigned-hint">
              有 {{ unassignedPlayers.length }} 名选手尚未分组（下方名单面板可加入小组）：{{ unassignedNames }}
            </p>
            <GroupAssignmentEditor
              v-if="groupCount"
              v-model="groups"
              :roster="roster"
              :group-count="groupCount"
              :min-per-group="minPerGroup"
              :disabled="groupStageLocked"
            />
            <p v-if="groupCount && !validation.ok" class="error-text group-assignment-validation">{{ validation.message }}</p>
            <div class="group-assignment-submit">
              <button
                class="secondary-action match-submit-action"
                type="button"
                :disabled="saving || groupStageLocked || !validation.ok"
                @click="submitGroups"
              >
                {{ saving ? '保存中...' : '保存分组' }}
              </button>
            </div>
          </template>
          <p v-else class="muted">未能从签表中读取该组别的参赛名单，请确认该组别已生成小组分组。</p>
        </section>
      </template>

      <p v-else class="panel">该赛事没有可调整分组的手写分组组别（仅「小组赛 + 淘汰赛」且启用「手写分组」、淘汰赛尚未生成的组别可调整）。</p>
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
  updateDivisionGroupAssignments,
} from '../services/api'
import GroupAssignmentEditor from '../components/GroupAssignmentEditor.vue'
import ThemeSwitcher from '../components/ThemeSwitcher.vue'

const route = useRoute()
const router = useRouter()
const tournamentId = route.params.id

const profile = ref(null)
const divisions = ref([])
const activeDivisionId = ref('')
const bracket = ref(null)
const groups = ref([])
const loading = ref(true)
const bracketLoading = ref(false)
const saving = ref(false)
const error = ref('')
const success = ref('')

function isBlank(value) {
  return value === null || value === undefined || value === ''
}

/** 契约：手写分组 = type1 + drawMode=2 + 淘汰赛未生成 */
const manualGroupDivisions = computed(() => divisions.value.filter((division) => (
  Number(division.tournamentType) === 1
  && Number(division.drawMode) === 2
  && !division.knockoutGenerated
)))

const activeDivision = computed(() => manualGroupDivisions.value
  .find((division) => String(division.divisionId) === String(activeDivisionId.value)) || null)
const activeDivisionName = computed(() => activeDivision.value?.name || '小组+淘汰赛组别')

/** 服务端摘要信号：小组赛已开赛则提前禁用保存（权威判定在后端痕迹守卫，这里只做提示） */
const groupStageLocked = computed(() => activeDivision.value?.groupStageStarted === true)

/** 编辑接口以 playerId 为单位，所以 roster 的 key 直接用 playerId 字符串 */
const roster = computed(() => (bracket.value?.players || []).map((player) => ({ key: String(player.id), label: player.name || '未命名选手' })))

/** 契约：组数 = 淘汰名额 ÷ 每组出线；组别摘要缺字段时退回 bracket 里的最大组号 */
const groupCount = computed(() => {
  const slots = Number(activeDivision.value?.knockoutSlots)
  const perGroup = Number(activeDivision.value?.qualifiersPerGroup)
  if (Number.isInteger(slots) && Number.isInteger(perGroup) && perGroup > 0 && slots >= 2 && slots % perGroup === 0) {
    return slots / perGroup
  }
  const numbers = (bracket.value?.players || [])
    .map((player) => Number(player.groupNo))
    .filter((number) => Number.isInteger(number) && number > 0)
  return numbers.length ? Math.max(...numbers) : 0
})

/** 契约：每组人数下限 = max(2, 每组出线) */
const minPerGroup = computed(() => Math.max(2, Number(activeDivision.value?.qualifiersPerGroup) || 2))

const rosterKeys = computed(() => roster.value.map((item) => item.key))

/** 当前无小组的选手（groupNo 为空或越界）：在下方面板可分组，不阻塞页面 */
const unassignedPlayers = computed(() => (bracket.value?.players || []).filter((player) => {
  if (isBlank(player.groupNo)) return true
  const groupNo = Number(player.groupNo)
  return !Number.isInteger(groupNo) || groupNo < 1 || (groupCount.value > 0 && groupNo > groupCount.value)
}))
const unassignedNames = computed(() => unassignedPlayers.value.map((player) => player.name || '未命名选手').join('、'))

// 提交前客户端校验：组数 / 每组下限 / 全覆盖 / 无重复（服务端守卫仍会兜底）
const validation = computed(() => {
  const problems = []
  const count = groupCount.value
  const list = groups.value.map((group) => (Array.isArray(group) ? group.map((key) => String(key)) : []))
  const known = new Set(rosterKeys.value)
  const seen = new Set()
  const duplicated = new Set()
  for (const group of list) {
    for (const key of group) {
      if (seen.has(key)) duplicated.add(key)
      seen.add(key)
    }
  }
  const unknownCount = [...seen].filter((key) => !known.has(key)).length
  const unplacedCount = rosterKeys.value.filter((key) => !seen.has(key)).length
  const shortGroups = list
    .map((group, index) => ({ index, size: group.length }))
    .filter((item) => item.size < minPerGroup.value)

  if (!count) problems.push('无法确定小组数量：淘汰名额需能被每组出线整除（至少 2 个小组）')
  else if (list.length !== count) problems.push(`小组数量应为 ${count} 个`)
  if (unknownCount) problems.push(`有 ${unknownCount} 个参赛单位已不在该组别名单中，请先移出`)
  if (duplicated.size) problems.push(`有 ${duplicated.size} 个参赛单位出现在多个小组`)
  if (unplacedCount) problems.push(`还有 ${unplacedCount} 个参赛单位未分组`)
  if (shortGroups.length) {
    problems.push(`第 ${shortGroups.map((item) => item.index + 1).join('、')} 组不足 ${minPerGroup.value} 人`)
  }
  return { ok: !problems.length, message: problems.join('；') }
})

/** groupNo 有值按组号归组、组内按 groupPosition 升序；groupNo 为空/越界的选手回到「未分组」 */
function deriveGroups(data) {
  const count = groupCount.value
  const derived = Array.from({ length: Math.max(0, count) }, () => [])
  const byGroupNo = new Map()
  for (const player of data?.players || []) {
    if (isBlank(player.groupNo)) continue
    const groupNo = Number(player.groupNo)
    if (!Number.isInteger(groupNo) || groupNo < 1) continue
    if (!byGroupNo.has(groupNo)) byGroupNo.set(groupNo, [])
    byGroupNo.get(groupNo).push(player)
  }
  for (const [groupNo, list] of byGroupNo.entries()) {
    const index = groupNo - 1
    if (index < 0 || index >= derived.length) continue
    list.sort((left, right) => positionOf(left) - positionOf(right))
    derived[index] = list.map((player) => String(player.id))
  }
  return derived
}

function positionOf(player) {
  if (isBlank(player.groupPosition)) return Number.MAX_SAFE_INTEGER
  const value = Number(player.groupPosition)
  return Number.isFinite(value) ? value : Number.MAX_SAFE_INTEGER
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
    groups.value = deriveGroups(bracket.value)
  } catch (err) {
    bracket.value = null
    groups.value = []
    error.value = err?.message || '参赛名单加载失败'
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

async function submitGroups() {
  if (groupStageLocked.value) {
    error.value = '小组赛已开赛，分组不可再调整'
    return
  }
  if (!validation.value.ok) {
    error.value = validation.value.message
    return
  }
  saving.value = true
  error.value = ''
  success.value = ''
  try {
    await updateDivisionGroupAssignments(
      tournamentId,
      activeDivisionId.value,
      groups.value.map((group) => group.map((key) => String(key))),
    )
    success.value = '分组已保存'
    await loadDivisions()
    await loadBracket(activeDivisionId.value)
  } catch (err) {
    const reason = err?.message || '保存失败'
    error.value = reason
    // 失败自愈：以服务端为准重拉分组与组别摘要（groupStageLocked/knockoutGenerated 都来自
    // divisions，不重拉会滞留可编辑态反复失败），重拉过程不覆盖原始保存错误
    try {
      await loadDivisions()
      await loadBracket(activeDivisionId.value)
    } catch {
      // 保留原保存失败信息
    }
    error.value = reason
    if (!groupStageLocked.value) {
      // 子表痕迹（赛前保存过的阵容/布阵配置）前端不可见：仍显示可编辑时给出终态提示
      error.value = `${reason}（提示：服务端存在页面看不到的开赛痕迹，例如赛前保存过阵容/布阵配置，请联系创建者核查）`
    }
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
  const first = manualGroupDivisions.value[0]
  if (first) await selectDivision(first)
})
</script>

<style scoped>
.topbar h1 {
  font-size: 24px;
}

.group-division-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.group-division-tabs .active {
  color: var(--ink);
  border-color: var(--accent);
  background: var(--accent);
}

.group-bracket-loading {
  margin-top: 12px;
  color: var(--muted);
}

.group-assignment-hint {
  margin: 12px 0;
  color: var(--muted);
  font-size: 13px;
  line-height: 1.5;
}

.group-unassigned-hint {
  margin: 12px 0;
  padding: 8px 10px;
  border: 1px solid rgba(var(--warn-rgb), 0.5);
  border-radius: 6px;
  background: var(--warn-bg);
  color: var(--warn-ink);
  font-size: 13px;
  line-height: 1.5;
}

.group-assignment-validation {
  margin: 12px 0 0;
}

.group-assignment-submit {
  display: flex;
  justify-content: flex-end;
  margin-top: 14px;
}
</style>
