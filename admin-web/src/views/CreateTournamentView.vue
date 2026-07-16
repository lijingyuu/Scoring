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

      <div class="create-layout" :class="{ 'has-side-panel': showPlayerSidePanel }">
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
                  <select v-model.number="form.rule.bestOf" :disabled="isRelay" @change="setBestOf(form.rule.bestOf)">
                    <option :value="1">一局</option>
                    <option :value="3">三局两胜</option>
                    <option :value="5">五局三胜</option>
                    <option :value="7">七局四胜</option>
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

          <section v-else class="panel">
            <div class="panel-head">
              <h2>队伍名单</h2>
              <span class="muted">{{ teams.length }} 队</span>
            </div>
            <div class="quick-team-form">
              <label>
                <span>队名</span>
                <input v-model.trim="quickTeamName" placeholder="例如 一队" />
              </label>
              <label>
                <span>队员名单</span>
                <textarea v-model="teamPaste" placeholder="每行一名队员，第一名默认队长"></textarea>
              </label>
              <button class="secondary-action" @click="quickAddTeam">快捷添加队伍</button>
            </div>

            <div class="team-grid">
              <article v-for="(team, teamIndex) in teams" :key="team.id" class="team-card">
                <div class="panel-head">
                  <input v-model.trim="team.name" class="team-title-input" placeholder="队伍名称" />
                  <button class="text-action danger" @click="teams.splice(teamIndex, 1)">删除队伍</button>
                </div>
                <div class="editable-list compact-list">
                  <div class="row header team-row">
                    <span>姓名</span>
                    <span v-if="isVolleyball">号码</span>
                    <span>队长</span>
                    <span></span>
                  </div>
                  <div v-for="(member, memberIndex) in team.members" :key="memberIndex" class="row team-row">
                    <input v-model.trim="member.name" placeholder="成员姓名" />
                    <input v-if="isVolleyball" v-model.number="member.jerseyNumber" type="number" min="1" placeholder="号码" />
                    <input class="captain-radio" type="radio" :name="`captain-${team.id}`" :checked="member.captain" @change="setCaptain(team, memberIndex)" />
                    <button class="text-action danger" @click="team.members.splice(memberIndex, 1)">删除</button>
                  </div>
                </div>
                <button class="ghost-action small" @click="team.members.push(createMember())">添加成员</button>
              </article>
            </div>
            <button class="ghost-action" @click="teams.push(createTeam())">添加队伍</button>
          </section>

          <div v-if="!isIndividual" class="submit-footer">
            <button class="primary-action compact" :disabled="submitting" @click="submit">
              {{ submitting ? '创建中...' : '生成比赛' }}
            </button>
          </div>
        </div>

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
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
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
const players = reactive([])
const teams = reactive([])
let nextTeamId = 1

const form = reactive({
  name: '',
  location: '',
  sportType: 0,
  participantType: 0,
  teamMatchTemplate: 1,
  tournamentType: 0,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  roundRobinRounds: 1,
  refereePassword: '',
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
})

const isVolleyball = computed(() => form.sportType === 1)
const isIndividual = computed(() => form.sportType === 0 && form.participantType === 0)
const isBadmintonTeam = computed(() => form.sportType === 0 && form.participantType === 1)
const isRelay = computed(() => isBadmintonTeam.value && form.teamMatchTemplate === 2)
const showPlayerSidePanel = computed(() => isIndividual.value && playerListVisible.value)

function setBestOf(bestOf) {
  form.rule.bestOf = Number(bestOf)
  form.rule.gamesToWin = Math.floor(form.rule.bestOf / 2) + 1
}

function syncSportDefaults() {
  if (form.sportType === 1) {
    form.participantType = 1
    form.teamMatchTemplate = 0
    form.rule.bestOf = 3
    form.rule.gamesToWin = 2
    form.rule.pointsToWin = 25
    form.rule.enableDeuce = true
    form.rule.capPoint = 30
  } else {
    form.participantType = 0
    form.teamMatchTemplate = 1
    form.rule.pointsToWin = 21
    setBestOf(3)
  }
}

function syncParticipantDefaults() {
  if (form.participantType === 0) {
    form.teamMatchTemplate = 0
  } else {
    form.teamMatchTemplate = 1
  }
  syncTemplateDefaults()
}

function syncTemplateDefaults() {
  if (isRelay.value) {
    form.rule.bestOf = 1
    form.rule.gamesToWin = 1
    form.rule.pointsToWin = 10
    form.rule.enableDeuce = false
    form.rule.capPoint = 6
  } else {
    setBestOf(3)
    form.rule.enableDeuce = true
    form.rule.capPoint = form.sportType === 1 ? 30 : 30
  }
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
    .map((line, index) => {
      const name = line.replace(/^\d+[.\s、]+/, '').trim()
      return createMember(name, isVolleyball.value ? index + 1 : '', index === 0)
    })
  if (!quickTeamName.value || !members.length) {
    modalError.value = !quickTeamName.value ? '请先填写队名' : '请粘贴队员名单'
    return
  }
  teams.push({ id: `team-${nextTeamId++}`, name: quickTeamName.value, members })
  quickTeamName.value = ''
  teamPaste.value = ''
}

function setCaptain(team, index) {
  team.members.forEach((member, memberIndex) => {
    member.captain = memberIndex === index
  })
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
    return ''
  }

  if (teams.length < 2) return '至少需要2支队伍'
  for (const team of teams) {
    if (!team.name) return '请填写所有队伍名称'
    const validMembers = team.members.filter((member) => member.name)
    if (isVolleyball.value && validMembers.length < 6) return `${team.name} 至少需要6名球员`
    if (!isVolleyball.value && validMembers.length < 2) return `${team.name} 至少需要2名成员`
    if (validMembers.filter((member) => member.captain).length !== 1) return `${team.name} 必须有且仅有1名队长`
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
    knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
    qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
    roundRobinRounds: form.tournamentType === 2 ? form.roundRobinRounds : undefined,
    refereePassword: form.refereePassword || undefined,
    rule: {
      bestOf: isRelay.value ? 1 : form.rule.bestOf,
      gamesToWin: isRelay.value ? 1 : form.rule.gamesToWin,
      pointsToWin: form.rule.pointsToWin,
      enableDeuce: isRelay.value ? false : form.rule.enableDeuce,
      capPoint: isRelay.value ? form.rule.capPoint : form.rule.capPoint,
    },
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
    teams: teams.map((team) => ({
      name: team.name,
      members: team.members.filter((member) => member.name).map((member) => ({
        name: member.name,
        jerseyNumber: isVolleyball.value ? Number(member.jerseyNumber) : undefined,
        captain: !!member.captain,
      })),
    })),
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
teams.push(createTeam('一队'), createTeam('二队'))
onMounted(loadProfile)
</script>
