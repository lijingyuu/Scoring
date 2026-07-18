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
        <div class="user-pill">{{ profile?.nickname || '后台用户' }}</div>
        <button class="ghost-action small" @click="logout">退出登录</button>
      </div>
    </header>

    <main class="content">
      <p v-if="success" class="success-text">{{ success }}</p>

      <div class="create-layout" :class="{ 'has-side-panel': showPlayerSidePanel, 'has-team-side-panel': showTeamSidePanel }">
        <div class="create-main">
          <section class="form-grid">
            <div class="panel">
              <h2>基础信息</h2>
              <div class="field-grid tournament-basic-grid">
                <label>
                  <span>赛事名称</span>
                  <input v-model.trim="form.name" placeholder="例如 2026 春季赛" />
                </label>
                <label>
                  <span>地点</span>
                  <input v-model.trim="form.location" placeholder="可选" />
                </label>
                <label>
                  <span>裁判密码</span>
                  <input v-model.trim="form.refereePassword" maxlength="8" placeholder="8位数字，可选" />
                </label>
              </div>

              <div class="field-grid sport-config-grid">
                <label>
                  <span>运动</span>
                  <select v-model.number="form.sportType" @change="syncSportDefaults">
                    <option :value="0">羽毛球</option>
                    <option :value="1">排球</option>
                  </select>
                </label>
                <label v-if="form.sportType === 0">
                  <span>参赛形式</span>
                  <select v-model.number="form.participantType" @change="syncParticipantDefaults">
                    <option :value="0">个人赛</option>
                    <option :value="1">团体赛</option>
                  </select>
                </label>
                <label v-if="isBadmintonTeam">
                  <span>团体模板</span>
                  <select v-model.number="form.teamMatchTemplate" @change="syncTemplateDefaults">
                    <option :value="1">苏迪曼杯 5 项</option>
                    <option :value="2">接力追分赛</option>
                  </select>
                </label>
              </div>
            </div>

            <div class="panel">
              <h2>赛制与规则</h2>
              <div class="field-grid three">
                <label>
                  <span>赛制</span>
                  <select v-model.number="form.tournamentType">
                    <option :value="0">淘汰赛</option>
                    <option :value="1">小组 + 淘汰</option>
                    <option :value="2">循环赛</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 0">
                  <span>淘汰轮数</span>
                  <input v-model.number="form.knockoutRounds" type="number" min="1" max="10" />
                </label>
                <label v-if="form.tournamentType === 1">
                  <span>淘汰名额</span>
                  <select v-model.number="form.knockoutSlots">
                    <option :value="4">4</option>
                    <option :value="8">8</option>
                    <option :value="16">16</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 1">
                  <span>每组出线</span>
                  <select v-model.number="form.qualifiersPerGroup">
                    <option :value="1">1</option>
                    <option :value="2">2</option>
                  </select>
                </label>
                <label v-if="form.tournamentType === 2">
                  <span>循环轮次</span>
                  <select v-model.number="form.roundRobinRounds">
                    <option :value="1">单循环</option>
                    <option :value="2">双循环</option>
                  </select>
                </label>
              </div>

              <div class="field-grid four">
                <label>
                  <span>局数</span>
                  <select v-model.number="form.rule.bestOf" :disabled="isRelay" @change="setBestOf(form.rule, form.rule.bestOf)">
                    <option :value="1">一局</option>
                    <option :value="3">三局两胜</option>
                    <option :value="5">五局三胜</option>
                  </select>
                </label>
                <label>
                  <span>{{ isRelay ? '分段基准分' : '基础胜分' }}</span>
                  <input v-model.number="form.rule.pointsToWin" type="number" min="1" />
                </label>
                <label>
                  <span>追分</span>
                  <select v-model="form.rule.enableDeuce" :disabled="isRelay">
                    <option :value="true">开启</option>
                    <option :value="false">关闭</option>
                  </select>
                </label>
                <label>
                  <span>{{ isRelay ? '轮转人数' : '封顶分' }}</span>
                  <input v-model.number="form.rule.capPoint" type="number" min="1" />
                </label>
                <label v-if="isVolleyball">
                  <span>决胜局胜分</span>
                  <input v-model.number="form.rule.decidingPointsToWin" type="number" min="1" />
                </label>
              </div>

              <div v-if="supportsRoundRules" class="round-rule-panel">
                <label class="inline-toggle">
                  <input v-model="form.roundRuleEnabled" type="checkbox" @change="syncRoundRules" />
                  <span>启用分段规则设计</span>
                </label>
                <div v-if="form.roundRuleEnabled" class="round-rule-entry">
                  <button class="secondary-action small" type="button" @click="openRoundRuleDrawer">
                    设计分段规则
                  </button>
                  <div class="round-rule-summary">
                    <span v-for="segment in activeRoundRuleSegments" :key="segment.id">
                      {{ segment.name || '未命名赛段' }}：{{ formatSegmentScopes(segment) }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </section>

          <section v-if="isIndividual" class="panel player-paste-panel">
            <div class="panel-head">
              <h2>选手名单粘贴板</h2>
              <span class="muted">{{ players.length }} 人</span>
            </div>
            <textarea
              v-model="playerPaste"
              class="player-paste-input"
              placeholder="每行一名选手。行首数字表示种子，可写 1张三、1 张三、1.张三、1、张三；不写数字则种子为空。"
            ></textarea>
            <div class="player-paste-actions">
              <button class="secondary-action" @click="applyPlayersPaste">生成选手列表</button>
              <button class="secondary-action match-submit-action" :disabled="submitting" @click="submit">
                {{ submitting ? '创建中...' : '生成比赛' }}
              </button>
            </div>
          </section>

          <section v-else class="panel team-paste-panel">
            <div class="panel-head">
              <h2>队伍队员名单粘贴板</h2>
              <span class="muted">{{ teams.length }} 队</span>
            </div>
            <div class="quick-team-form">
              <label>
                <span>队名</span>
                <input v-model.trim="quickTeamName" placeholder="例如 一队" />
              </label>
              <label>
                <span>队员名单</span>
                <textarea
                  v-model="teamPaste"
                  :placeholder="isVolleyball ? '每行一名队员，格式：姓名 号码；第一名默认队长' : '每行一名队员，第一名默认队长'"
                ></textarea>
              </label>
              <div class="team-paste-actions">
                <button class="ghost-action" @click="quickAddTeam">快捷添加队伍</button>
                <button class="secondary-action match-submit-action" :disabled="submitting" @click="submit">
                  {{ submitting ? '创建中...' : '生成比赛' }}
                </button>
              </div>
            </div>

            <div v-if="teams.length" class="team-list-block">
              <div class="panel-head">
                <h3>队伍列表</h3>
                <span class="muted">{{ teams.length }} 队</span>
              </div>
              <div class="team-list">
                <div
                  v-for="team in teams"
                  :key="team.id"
                  class="team-list-item"
                  :class="{ active: selectedTeamId === team.id }"
                  @click="selectTeam(team.id)"
                >
                  <span>{{ team.name }}</span>
                  <button class="text-action danger" type="button" @click.stop="requestDeleteTeam(team.id)">移除队伍</button>
                </div>
              </div>
            </div>
          </section>

        </div>

        <aside v-if="showTeamSidePanel" class="team-side-panel">
          <section class="panel team-detail-panel">
            <div class="team-detail-head">
              <div v-if="editingTeamName" class="team-name-editor">
                <input v-model.trim="teamNameDraft" placeholder="队伍名称" @keyup.enter="confirmTeamNameEdit" />
                <button class="ghost-action small" type="button" @click="confirmTeamNameEdit">确定</button>
              </div>
              <div v-else class="team-title-line">
                <h2>{{ selectedTeam.name }}</h2>
              </div>
              <div class="team-title-actions">
                <button class="tiny-text-action" type="button" @click="startTeamNameEdit">编辑队名</button>
                <button v-if="!changingCaptain" class="tiny-text-action" type="button" @click="startCaptainChange">更改队长</button>
              </div>
            </div>
            <div class="editable-list compact-list team-member-table" :class="{ 'has-jersey': isVolleyball }">
              <div class="row header team-row">
                <span>姓名</span>
                <span v-if="isVolleyball">号码</span>
              </div>
              <div v-for="(member, memberIndex) in selectedTeam.members" :key="memberIndex" class="row team-row">
                <div class="name-cell">
                  <input v-model.trim="member.name" placeholder="成员姓名" />
                  <span v-if="isCaptainMember(selectedTeam, memberIndex)" class="captain-badge">队长</span>
                  <button
                    v-if="changingCaptain"
                    class="captain-select"
                    :class="{ selected: captainCandidateIndex === memberIndex }"
                    type="button"
                    aria-label="选择队长"
                    :disabled="!member.name"
                    @click="captainCandidateIndex = memberIndex"
                  ></button>
                  <button v-else class="icon-remove" type="button" aria-label="删除成员" @click="deleteMember(selectedTeam, memberIndex)"></button>
                </div>
                <input v-if="isVolleyball" v-model.number="member.jerseyNumber" class="jersey-input" type="number" min="1" placeholder="-" />
              </div>
            </div>
            <div class="team-detail-actions">
              <button class="ghost-action small" @click="addTeamMember(selectedTeam)">添加成员</button>
              <button
                v-if="changingCaptain"
                class="secondary-action small"
                type="button"
                :disabled="captainCandidateIndex < 0"
                @click="confirmCaptainChange"
              >
                确定
              </button>
            </div>
          </section>
        </aside>

        <aside v-if="showPlayerSidePanel" class="player-side-panel">
          <section class="panel player-list-panel">
            <div class="panel-head">
              <h2>选手名单</h2>
              <span class="muted">{{ players.length }} 人</span>
            </div>
            <div class="editable-list player-table">
              <div class="row header"><span>种子</span><span>姓名</span></div>
              <div v-for="(player, index) in players" :key="index" class="row">
                <input v-model.number="player.seed" type="number" placeholder="-" />
                <div class="name-cell">
                  <input v-model.trim="player.name" placeholder="选手姓名" />
                  <button class="icon-remove" type="button" aria-label="删除选手" @click="players.splice(index, 1)"></button>
                </div>
              </div>
            </div>
            <button class="ghost-action small" @click="players.push({ name: '', seed: null })">添加选手</button>
          </section>
        </aside>
      </div>
    </main>

    <div v-if="modalError" class="modal-overlay" @click.self="modalError = ''">
      <section class="message-modal">
        <h2>信息不完整</h2>
        <p>{{ modalError }}</p>
        <button class="secondary-action" @click="modalError = ''">知道了</button>
      </section>
    </div>

    <div v-if="pendingDeleteTeam" class="modal-overlay" @click.self="pendingDeleteTeamId = ''">
      <section class="message-modal">
        <h2>移除队伍</h2>
        <p>确定移除「{{ pendingDeleteTeam.name }}」队吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDeleteTeamId = ''">取消</button>
          <button class="secondary-action" type="button" @click="confirmDeleteTeam">确定</button>
        </div>
      </section>
    </div>

    <div v-if="roundRuleDrawerOpen" class="drawer-overlay" @click.self="closeRoundRuleDrawer">
      <aside class="round-rule-drawer">
        <div class="drawer-head">
          <div>
            <h2>分段规则设计</h2>
            <p>{{ roundRuleDrawerHint }}</p>
          </div>
          <button class="ghost-action small" type="button" @click="closeRoundRuleDrawer">关闭</button>
        </div>

        <div class="scope-bank">
          <h3>比赛阶段</h3>
          <div class="scope-chip-list">
            <span
              v-for="scope in roundRuleScopes"
              :key="scope.key"
              class="scope-chip"
              :class="{ assigned: !!assignedSegmentName(scope.key) }"
            >
              {{ scope.label }}
              <em>{{ assignedSegmentName(scope.key) || '未分配' }}</em>
            </span>
          </div>
        </div>

        <div class="drawer-toolbar">
          <button class="ghost-action small" type="button" @click="addRoundRuleSegment">新增赛段</button>
        </div>

        <p v-if="roundRuleDrawerError" class="drawer-error">{{ roundRuleDrawerError }}</p>

        <div class="segment-list">
          <section v-for="segment in form.roundRuleSegments" :key="segment.id" class="segment-card">
            <div class="segment-card-head">
              <input v-model.trim="segment.name" placeholder="赛段名称" />
              <button
                class="tiny-text-action danger"
                type="button"
                :disabled="form.roundRuleSegments.length <= 1"
                @click="removeRoundRuleSegment(segment)"
              >
                删除赛段
              </button>
            </div>

            <div class="scope-toggle-grid">
              <button
                v-for="scope in roundRuleScopes"
                :key="scope.key"
                type="button"
                class="scope-toggle"
                :class="{ active: segment.scopeKeys.includes(scope.key) }"
                @click="toggleSegmentScope(segment, scope)"
              >
                {{ scope.label }}
              </button>
            </div>

            <div class="field-grid four round-rule-grid">
              <label>
                <span>局数</span>
                <select v-model.number="segment.rule.bestOf" :disabled="isRelay" @change="setBestOf(segment.rule, segment.rule.bestOf)">
                  <option :value="1" v-if="!isVolleyball">一局</option>
                  <option :value="3">三局两胜</option>
                  <option :value="5">五局三胜</option>
                </select>
              </label>
              <label>
                <span>基础胜分</span>
                <input v-model.number="segment.rule.pointsToWin" type="number" min="1" />
              </label>
              <label>
                <span>追分</span>
                <select v-model="segment.rule.enableDeuce">
                  <option :value="true">开启</option>
                  <option :value="false">关闭</option>
                </select>
              </label>
              <label>
                <span>封顶分</span>
                <input v-model.number="segment.rule.capPoint" type="number" min="1" />
              </label>
              <label v-if="isVolleyball">
                <span>决胜局胜分</span>
                <input v-model.number="segment.rule.decidingPointsToWin" type="number" min="1" />
              </label>
            </div>
          </section>
        </div>

        <div class="drawer-actions">
          <button class="ghost-action" type="button" @click="closeRoundRuleDrawer">取消</button>
          <button class="secondary-action" type="button" @click="confirmRoundRuleSegments">确定</button>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { clearToken, createTournament, fetchMe } from '../services/api'

const router = useRouter()
const submitting = ref(false)
const success = ref('')
const modalError = ref('')
const profile = ref(null)
const playerPaste = ref('')
const playerListVisible = ref(false)
const quickTeamName = ref('')
const teamPaste = ref('')
const selectedTeamId = ref('')
const editingTeamName = ref(false)
const teamNameDraft = ref('')
const changingCaptain = ref(false)
const captainCandidateIndex = ref(-1)
const pendingDeleteTeamId = ref('')
const roundRuleDrawerOpen = ref(false)
const roundRuleDrawerError = ref('')
const players = reactive([])
const teams = reactive([])
let nextTeamId = 1
let nextRoundRuleSegmentId = 1

const form = reactive({
  name: '',
  location: '',
  sportType: 0,
  participantType: 0,
  teamMatchTemplate: 1,
  tournamentType: 0,
  knockoutRounds: 3,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  roundRobinRounds: 1,
  roundRuleEnabled: false,
  roundRules: [],
  roundRuleSegments: [],
  refereePassword: '',
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    decidingPointsToWin: null,
    enableDeuce: true,
    capPoint: 30,
  },
})

const isVolleyball = computed(() => form.sportType === 1)
const isIndividual = computed(() => form.sportType === 0 && form.participantType === 0)
const isBadmintonTeam = computed(() => form.sportType === 0 && form.participantType === 1)
const isRelay = computed(() => isBadmintonTeam.value && form.teamMatchTemplate === 2)
const supportsRoundRules = computed(() => form.tournamentType !== 2 && !isRelay.value)
const showPlayerSidePanel = computed(() => isIndividual.value && playerListVisible.value)
const selectedTeam = computed(() => teams.find((team) => team.id === selectedTeamId.value) || null)
const showTeamSidePanel = computed(() => !isIndividual.value && !!selectedTeam.value)
const pendingDeleteTeam = computed(() => teams.find((team) => team.id === pendingDeleteTeamId.value) || null)
const participantCount = computed(() => (isIndividual.value ? players.filter((player) => player.name).length : teams.length))
const roundRuleScopes = computed(() => expectedRoundRuleScopes())
const activeRoundRuleSegments = computed(() => form.roundRuleSegments.filter((segment) => segment.scopeKeys.length))
const roundRuleDrawerHint = computed(() => {
  if (form.tournamentType === 1) return `小组赛 + ${Math.log2(form.knockoutSlots)} 轮淘汰赛`
  return `${form.knockoutRounds} 轮淘汰赛`
})

function createRule(source = form.rule) {
  return {
    bestOf: source.bestOf,
    gamesToWin: source.gamesToWin,
    pointsToWin: source.pointsToWin,
    decidingPointsToWin: isVolleyball.value ? (source.decidingPointsToWin || 15) : null,
    enableDeuce: source.enableDeuce,
    capPoint: source.capPoint,
  }
}

function createDefaultRuleForCurrentSport() {
  if (isVolleyball.value) {
    return {
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 25,
      decidingPointsToWin: 15,
      enableDeuce: true,
      capPoint: 99,
    }
  }
  return {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    decidingPointsToWin: null,
    enableDeuce: true,
    capPoint: 30,
  }
}

function knockoutCapacity() {
  if (form.tournamentType === 1) return form.knockoutSlots
  const rounds = Number(form.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return 0
  return 2 ** rounds
}

function validateKnockoutRounds(count) {
  if (form.tournamentType !== 0) return ''
  const rounds = Number(form.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return '淘汰轮数必须是1到10之间的整数'
  const minExclusive = rounds === 1 ? 1 : 2 ** (rounds - 1)
  const maxInclusive = 2 ** rounds
  if (count <= minExclusive || count > maxInclusive) {
    return `当前淘汰轮数需要${minExclusive + 1}到${maxInclusive}名参赛方`
  }
  return ''
}

function expectedRoundRuleScopes() {
  if (!supportsRoundRules.value) return []
  const scopes = []
  if (form.tournamentType === 1) {
    scopes.push({ stageType: 0, roundNum: 0, key: roundRuleScopeKey(0, 0), label: '小组赛' })
  }
  const capacity = knockoutCapacity()
  if (capacity < 2) return scopes
  const roundCount = Math.log2(capacity)
  for (let round = 1; round <= roundCount; round++) {
    const from = capacity / (2 ** (round - 1))
    const to = from / 2
    scopes.push({
      stageType: 1,
      roundNum: round,
      key: roundRuleScopeKey(1, round),
      label: to === 1 ? '决赛' : `${from}进${to}`,
    })
  }
  return scopes
}

function roundRuleScopeKey(stageType, roundNum) {
  return `${stageType}-${roundNum}`
}

function syncRoundRules(options = {}) {
  if (!supportsRoundRules.value) {
    form.roundRuleEnabled = false
    form.roundRules = []
    form.roundRuleSegments = []
    return
  }
  if (!form.roundRuleEnabled) {
    form.roundRules = []
    form.roundRuleSegments = []
    return
  }
  normalizeRoundRuleSegments(options)
  updateFlattenedRoundRules()
}

function normalizeRoundRuleSegments(options = {}) {
  const scopes = expectedRoundRuleScopes()
  const scopeOrder = new Map(scopes.map((scope, index) => [scope.key, index]))
  const scopeKeys = new Set(scopes.map((scope) => scope.key))
  const previousRules = new Map(form.roundRules.map((item) => [roundRuleScopeKey(item.stageType, item.roundNum), item.rule]))

  if (!form.roundRuleSegments.length && !previousRules.size && scopes.length) {
    form.roundRuleSegments = [{
      id: nextRoundRuleSegmentId++,
      name: '默认赛段',
      scopeKeys: scopes.map((scope) => scope.key),
      rule: createRule(),
    }]
    return
  }

  const segments = form.roundRuleSegments
    .map((segment) => ({
      ...segment,
      scopeKeys: segment.scopeKeys.filter((key) => scopeKeys.has(key)),
      rule: options.resetRules ? createRule() : segment.rule,
    }))
    .filter((segment) => segment.scopeKeys.length)

  const assigned = new Set(segments.flatMap((segment) => segment.scopeKeys))
  for (const scope of scopes) {
    if (assigned.has(scope.key)) continue
    segments.push({
      id: nextRoundRuleSegmentId++,
      name: scope.label,
      scopeKeys: [scope.key],
      rule: options.resetRules ? createRule() : (previousRules.get(scope.key) || createRule()),
    })
  }

  for (const segment of segments) {
    segment.scopeKeys.sort((left, right) => scopeOrder.get(left) - scopeOrder.get(right))
  }
  form.roundRuleSegments = segments
}

function updateFlattenedRoundRules() {
  const scopeMap = new Map(expectedRoundRuleScopes().map((scope) => [scope.key, scope]))
  form.roundRules = form.roundRuleSegments.flatMap((segment) => segment.scopeKeys
    .filter((key) => scopeMap.has(key))
    .map((key) => {
      const scope = scopeMap.get(key)
      return {
        stageType: scope.stageType,
        roundNum: scope.roundNum,
        label: scope.label,
        rule: segment.rule,
      }
    }))
}

function openRoundRuleDrawer() {
  syncRoundRules()
  roundRuleDrawerError.value = ''
  roundRuleDrawerOpen.value = true
}

function closeRoundRuleDrawer() {
  roundRuleDrawerOpen.value = false
}

function addRoundRuleSegment() {
  roundRuleDrawerError.value = ''
  form.roundRuleSegments.push({
    id: nextRoundRuleSegmentId++,
    name: `赛段${form.roundRuleSegments.length + 1}`,
    scopeKeys: [],
    rule: createRule(),
  })
}

function removeRoundRuleSegment(segment) {
  if (form.roundRuleSegments.length <= 1) return
  roundRuleDrawerError.value = ''
  form.roundRuleSegments = form.roundRuleSegments.filter((item) => item.id !== segment.id)
  updateFlattenedRoundRules()
}

function toggleSegmentScope(segment, scope) {
  roundRuleDrawerError.value = ''
  if (segment.scopeKeys.includes(scope.key)) {
    segment.scopeKeys = segment.scopeKeys.filter((key) => key !== scope.key)
  } else {
    for (const item of form.roundRuleSegments) {
      item.scopeKeys = item.scopeKeys.filter((key) => key !== scope.key)
    }
    segment.scopeKeys.push(scope.key)
    const scopeOrder = new Map(roundRuleScopes.value.map((item, index) => [item.key, index]))
    segment.scopeKeys.sort((left, right) => scopeOrder.get(left) - scopeOrder.get(right))
    form.roundRuleSegments = form.roundRuleSegments.filter((item) => item === segment || item.scopeKeys.length)
  }
  updateFlattenedRoundRules()
}

function assignedSegmentName(scopeKey) {
  const segment = form.roundRuleSegments.find((item) => item.scopeKeys.includes(scopeKey))
  return segment?.name || ''
}

function formatSegmentScopes(segment) {
  const scopeMap = new Map(roundRuleScopes.value.map((scope) => [scope.key, scope.label]))
  return segment.scopeKeys.map((key) => scopeMap.get(key)).filter(Boolean).join('、') || '未选择'
}

function validateRoundRuleSegments() {
  const scopes = roundRuleScopes.value
  if (!scopes.length) return '请先设置有效的淘汰轮数'
  const assigned = new Set()
  for (const segment of form.roundRuleSegments) {
    if (!segment.scopeKeys.length) return `请为「${segment.name || '未命名赛段'}」选择比赛阶段，或删除该赛段`
    for (const key of segment.scopeKeys) {
      if (assigned.has(key)) return '同一个比赛阶段不能重复分配'
      assigned.add(key)
    }
  }
  const missing = scopes.filter((scope) => !assigned.has(scope.key))
  if (missing.length) return `还有比赛阶段未分配：${missing.map((scope) => scope.label).join('、')}`
  return ''
}

function confirmRoundRuleSegments() {
  const error = validateRoundRuleSegments()
  if (error) {
    roundRuleDrawerError.value = error
    return
  }
  updateFlattenedRoundRules()
  roundRuleDrawerOpen.value = false
}

function serializeRule(rule) {
  return {
    bestOf: rule.bestOf,
    gamesToWin: rule.gamesToWin,
    pointsToWin: rule.pointsToWin,
    decidingPointsToWin: isVolleyball.value ? rule.decidingPointsToWin : undefined,
    enableDeuce: rule.enableDeuce,
    capPoint: rule.capPoint,
  }
}

function setBestOf(rule, bestOf) {
  rule.bestOf = Number(bestOf)
  rule.gamesToWin = Math.floor(rule.bestOf / 2) + 1
}

function syncSportDefaults() {
  if (form.sportType === 1) {
    form.participantType = 1
    form.teamMatchTemplate = 0
    Object.assign(form.rule, createDefaultRuleForCurrentSport())
    fillMissingJerseyNumbers()
  } else {
    form.participantType = 0
    form.teamMatchTemplate = 1
    Object.assign(form.rule, createDefaultRuleForCurrentSport())
  }
  syncRoundRules({ resetRules: true })
}

function syncParticipantDefaults() {
  if (form.participantType === 0) {
    form.teamMatchTemplate = 0
  } else {
    form.teamMatchTemplate = 1
  }
  syncTemplateDefaults()
  syncRoundRules()
}

function syncTemplateDefaults() {
  if (isRelay.value) {
    form.rule.bestOf = 1
    form.rule.gamesToWin = 1
    form.rule.pointsToWin = 10
    form.rule.enableDeuce = false
    form.rule.capPoint = 6
  } else {
    setBestOf(form.rule, 3)
    form.rule.enableDeuce = true
    form.rule.capPoint = form.sportType === 1 ? 99 : 30
  }
  syncRoundRules()
}

function applyPlayersPaste() {
  const parsed = playerPaste.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)[.\s、-]*(.+)$/)
      return match
        ? { seed: Number(match[1]), name: match[2].trim() }
        : { seed: null, name: line }
    })
  players.splice(0, players.length, ...parsed)
  playerListVisible.value = parsed.length > 0
  syncRoundRules()
}

function createMember(name = '', jerseyNumber = '', captain = false) {
  return { name, jerseyNumber, captain }
}

function createTeam(name = '') {
  return { id: `team-${nextTeamId++}`, name, members: [createMember('', '', true)] }
}

function quickAddTeam() {
  const members = teamPaste.value
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line, index) => parseTeamMemberLine(line, index))
  if (!quickTeamName.value || !members.length) {
    modalError.value = !quickTeamName.value ? '请先填写队名' : '请粘贴队员名单'
    return
  }
  const team = { id: `team-${nextTeamId++}`, name: quickTeamName.value, members }
  teams.push(team)
  selectedTeamId.value = team.id
  quickTeamName.value = ''
  teamPaste.value = ''
  syncRoundRules()
}

function parseTeamMemberLine(line, index) {
  if (isVolleyball.value) {
    const match = line.match(/^(.+?)\s+(\d+)$/)
    return createMember(
      match ? match[1].trim() : line,
      match ? Number(match[2]) : index + 1,
      index === 0,
    )
  }
  const name = line.replace(/^\d+[.\s、]+/, '').trim()
  return createMember(name, '', index === 0)
}

function selectTeam(teamId) {
  selectedTeamId.value = teamId
  editingTeamName.value = false
  changingCaptain.value = false
  captainCandidateIndex.value = -1
}

function startTeamNameEdit() {
  teamNameDraft.value = selectedTeam.value?.name || ''
  editingTeamName.value = true
}

function confirmTeamNameEdit() {
  if (selectedTeam.value && teamNameDraft.value) {
    selectedTeam.value.name = teamNameDraft.value
  }
  editingTeamName.value = false
}

function captainIndexOf(team) {
  const savedCaptainIndex = team.members.findIndex((member) => member.name && member.captain)
  return savedCaptainIndex >= 0
    ? savedCaptainIndex
    : team.members.findIndex((member) => member.name)
}

function isCaptainMember(team, index) {
  return captainIndexOf(team) === index
}

function startCaptainChange() {
  captainCandidateIndex.value = captainIndexOf(selectedTeam.value)
  changingCaptain.value = true
}

function confirmCaptainChange() {
  if (!selectedTeam.value || captainCandidateIndex.value < 0) return
  const candidate = selectedTeam.value.members[captainCandidateIndex.value]
  if (!candidate?.name) return
  selectedTeam.value.members.forEach((member, index) => {
    member.captain = index === captainCandidateIndex.value
  })
  changingCaptain.value = false
}

function ensureTeamCaptain(team) {
  const captainIndex = captainIndexOf(team)
  team.members.forEach((member, index) => {
    member.captain = index === captainIndex
  })
}

function deleteMember(team, memberIndex) {
  team.members.splice(memberIndex, 1)
  ensureTeamCaptain(team)
  if (changingCaptain.value) {
    captainCandidateIndex.value = captainIndexOf(team)
  }
}

function nextJerseyNumber(team) {
  const jerseys = team.members
    .map((member) => Number(member.jerseyNumber))
    .filter((number) => Number.isFinite(number) && number > 0)
  return jerseys.length ? Math.max(...jerseys) + 1 : 1
}

function fillMissingJerseyNumbers() {
  teams.forEach((team) => {
    team.members.forEach((member) => {
      const jerseyNumber = Number(member.jerseyNumber)
      if (!Number.isFinite(jerseyNumber) || jerseyNumber <= 0) {
        member.jerseyNumber = nextJerseyNumber(team)
      }
    })
  })
}

function addTeamMember(team) {
  team.members.push(createMember('', isVolleyball.value ? nextJerseyNumber(team) : ''))
}

function requestDeleteTeam(teamId) {
  pendingDeleteTeamId.value = teamId
}

function confirmDeleteTeam() {
  const teamId = pendingDeleteTeamId.value
  pendingDeleteTeamId.value = ''
  deleteTeam(teamId)
}

function deleteTeam(teamId) {
  const teamIndex = teams.findIndex((team) => team.id === teamId)
  if (teamIndex < 0) return
  teams.splice(teamIndex, 1)
  if (selectedTeamId.value !== teamId) return
  selectedTeamId.value = teams[Math.min(teamIndex, teams.length - 1)]?.id || ''
  editingTeamName.value = false
  changingCaptain.value = false
  captainCandidateIndex.value = -1
  syncRoundRules()
}

function logout() {
  clearToken()
  router.replace('/login')
}

async function loadProfile() {
  try {
    profile.value = await fetchMe()
  } catch {
    profile.value = null
  }
}

function validate() {
  if (!form.name) return '请填写赛事名称'
  if (form.refereePassword && !/^\d{8}$/.test(form.refereePassword)) return '裁判密码必须是8位数字'
  if (isIndividual.value) {
    const validPlayers = players.filter((player) => player.name)
    if (validPlayers.length < 2) return '个人赛至少需要2名选手'
    const knockoutRoundsError = validateKnockoutRounds(validPlayers.length)
    if (knockoutRoundsError) return knockoutRoundsError
    if (form.roundRuleEnabled && !supportsRoundRules.value) return '当前赛制不支持分轮规则'
    if (form.roundRuleEnabled) {
      const roundRuleSegmentError = validateRoundRuleSegments()
      if (roundRuleSegmentError) return roundRuleSegmentError
    }
    if (form.roundRuleEnabled && !form.roundRules.length) return '请先生成选手名单后再启用分轮规则'
    return ''
  }

  if (teams.length < 2) return '至少需要2支队伍'
  const knockoutRoundsError = validateKnockoutRounds(teams.length)
  if (knockoutRoundsError) return knockoutRoundsError
  if (form.roundRuleEnabled && !supportsRoundRules.value) return '当前赛制不支持分轮规则'
  if (form.roundRuleEnabled) {
    const roundRuleSegmentError = validateRoundRuleSegments()
    if (roundRuleSegmentError) return roundRuleSegmentError
  }
  if (form.roundRuleEnabled && !form.roundRules.length) return '请先生成参赛名单后再启用分轮规则'
  for (const team of teams) {
    if (!team.name) return '请填写所有队伍名称'
    const validMembers = team.members.filter((member) => member.name)
    if (isVolleyball.value && validMembers.length < 6) return `${team.name} 至少需要6名球员`
    if (!isVolleyball.value && validMembers.length < 2) return `${team.name} 至少需要2名成员`
    if (isVolleyball.value) {
      const jerseys = new Set()
      for (const member of validMembers) {
        if (!Number.isFinite(Number(member.jerseyNumber)) || Number(member.jerseyNumber) <= 0) return `${team.name} 存在无效号码`
        if (jerseys.has(Number(member.jerseyNumber))) return `${team.name} 号码不能重复`
        jerseys.add(Number(member.jerseyNumber))
      }
    }
    if (isRelay.value && validMembers.length < form.rule.capPoint) return `${team.name} 人数不能少于轮转人数`
  }
  return ''
}

function buildPayload() {
  const base = {
    name: form.name,
    location: form.location || undefined,
    sportType: form.sportType,
    tournamentType: form.tournamentType,
    knockoutRounds: form.tournamentType === 0 ? form.knockoutRounds : undefined,
    knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
    qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
    roundRobinRounds: form.tournamentType === 2 ? form.roundRobinRounds : undefined,
    refereePassword: form.refereePassword || undefined,
    rule: serializeRule({
      ...form.rule,
      bestOf: isRelay.value ? 1 : form.rule.bestOf,
      gamesToWin: isRelay.value ? 1 : form.rule.gamesToWin,
      enableDeuce: isRelay.value ? false : form.rule.enableDeuce,
    }),
    roundRuleEnabled: form.roundRuleEnabled && supportsRoundRules.value,
    roundRules: form.roundRuleEnabled && supportsRoundRules.value
      ? form.roundRules.map((item) => ({
        stageType: item.stageType,
        roundNum: item.roundNum,
        rule: serializeRule(item.rule),
      }))
      : undefined,
  }

  if (isIndividual.value) {
    return {
      ...base,
      participantType: 0,
      teamMatchTemplate: 0,
      players: players.filter((player) => player.name).map((player) => ({
        name: player.name,
        seed: player.seed || undefined,
      })),
    }
  }

  return {
    ...base,
    participantType: 1,
    teamMatchTemplate: isBadmintonTeam.value ? form.teamMatchTemplate : 0,
    teams: teams.map((team) => {
      const validMembers = team.members.filter((member) => member.name)
      const savedCaptainIndex = validMembers.findIndex((member) => member.captain)
      const captainIndex = savedCaptainIndex >= 0 ? savedCaptainIndex : 0
      return {
        name: team.name,
        members: validMembers.map((member, memberIndex) => ({
          name: member.name,
          jerseyNumber: isVolleyball.value ? Number(member.jerseyNumber) : undefined,
          captain: memberIndex === captainIndex,
        })),
      }
    }),
  }
}

async function submit() {
  modalError.value = ''
  success.value = ''
  const validationError = validate()
  if (validationError) {
    modalError.value = validationError
    return
  }
  if (form.roundRuleEnabled && supportsRoundRules.value) {
    updateFlattenedRoundRules()
  }
  submitting.value = true
  try {
    const res = await createTournament(buildPayload())
    success.value = `创建成功：${res.tournamentId}`
    setTimeout(() => router.push('/lobby'), 700)
  } catch (err) {
    modalError.value = err?.message || '创建失败'
  } finally {
    submitting.value = false
  }
}

applyPlayersPaste()
watch(
  () => [form.tournamentType, form.knockoutRounds, form.knockoutSlots, form.teamMatchTemplate, form.roundRuleEnabled, participantCount.value],
  syncRoundRules,
)
onMounted(loadProfile)
</script>
