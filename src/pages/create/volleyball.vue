<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">创建排球比赛</text>
    </view>

    <view class="form-panel">
      <input class="input" v-model="form.name" placeholder="比赛名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />

      <view class="section">
        <view class="section-title">比赛赛制</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.tournamentType === 0 }" @click="setTournamentType(0)">淘汰赛</view>
          <view class="segment-item" :class="{ active: form.tournamentType === 1 }" @click="setTournamentType(1)">小组+淘汰</view>
          <view class="segment-item" :class="{ active: form.tournamentType === 2 }" @click="setTournamentType(2)">循环赛</view>
        </view>
        <view class="rule-row" v-if="form.tournamentType !== 2">
          <text class="rule-label">季军赛</text>
          <view class="segment compact">
            <view class="segment-item" :class="{ active: !form.thirdPlaceEnabled }" @click="setThirdPlaceEnabled(false)">不需要</view>
            <view class="segment-item" :class="{ active: form.thirdPlaceEnabled }" @click="setThirdPlaceEnabled(true)">需要</view>
          </view>
        </view>

        <template v-if="form.tournamentType === 2">
          <view class="rule-row">
            <text class="rule-label">循环模式</text>
            <view class="segment compact">
              <view class="segment-item" :class="{ active: form.roundRobinRounds === 1 }" @click="form.roundRobinRounds = 1">单循环</view>
              <view class="segment-item" :class="{ active: form.roundRobinRounds === 2 }" @click="form.roundRobinRounds = 2">双循环</view>
            </view>
          </view>
          <text class="hint">{{ teamCount }} 支队伍，共 {{ estimatedLeagueMatches }} 场比赛</text>
        </template>

        <template v-if="form.tournamentType === 1">
          <view class="rule-row">
            <text class="rule-label">淘汰名额</text>
            <view class="segment compact">
              <view class="segment-item" :class="{ active: form.knockoutSlots === 4 }" @click="setKnockoutSlots(4)">4</view>
              <view class="segment-item" :class="{ active: form.knockoutSlots === 8 }" @click="setKnockoutSlots(8)">8</view>
              <view class="segment-item" :class="{ active: form.knockoutSlots === 16 }" @click="setKnockoutSlots(16)">16</view>
            </view>
          </view>

          <view class="rule-row">
            <text class="rule-label">每组出线</text>
            <view class="stepper">
              <view class="step-btn" @click="form.qualifiersPerGroup = Math.max(1, form.qualifiersPerGroup - 1)">-</view>
              <input class="step-input" type="number" :value="form.qualifiersPerGroup" @input="setQualifiersPerGroup" />
              <view class="step-btn" @click="form.qualifiersPerGroup = Math.min(2, form.qualifiersPerGroup + 1)">+</view>
            </view>
          </view>

          <text class="hint">预计 {{ groupCount }} 组，每组约 {{ estimatedGroupSize }} 队</text>
        </template>

        <view class="ranking-section" v-if="showRankingConfig">
          <view class="section-title compact-title">小组赛排名规则</view>
          <view class="template-list">
            <view
              class="template-card"
              v-for="option in volleyballRankingOptions"
              :key="option.value"
              :class="{ active: form.rankingTemplate === option.value, disabled: option.disabled }"
              @click="selectRankingTemplate(option)"
            >
              <text class="template-name">{{ option.name }}</text>
              <text class="template-desc">{{ option.desc }}</text>
            </view>
          </view>
        </view>

      </view>

      <view class="section" v-if="form.tournamentType === 1">
        <view class="section-title">比赛规则</view>
        <view class="rule-subsection">
          <view class="rule-subtitle">小组赛规则</view>
          <view class="segment">
            <view class="segment-item" :class="{ active: form.groupRule.bestOf === 3 }" @click="setBestOf('groupRule', 3)">三局两胜</view>
            <view class="segment-item" :class="{ active: form.groupRule.bestOf === 5 }" @click="setBestOf('groupRule', 5)">五局三胜</view>
          </view>
          <text class="hint">标准排球规则：常规局 25 分，末局 15 分，均需领先 2 分。</text>
        </view>
        <view class="rule-subsection">
          <view class="rule-subtitle">淘汰赛规则</view>
          <view class="segment">
            <view class="segment-item" :class="{ active: form.knockoutRule.bestOf === 3 }" @click="setBestOf('knockoutRule', 3)">三局两胜</view>
            <view class="segment-item" :class="{ active: form.knockoutRule.bestOf === 5 }" @click="setBestOf('knockoutRule', 5)">五局三胜</view>
          </view>
          <text class="hint">标准排球规则：常规局 25 分，末局 15 分，均需领先 2 分。</text>
        </view>
      </view>

      <view class="section" v-else>
        <view class="section-title">比赛规则</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.bestOf === 3 }" @click="setBestOf('bestOf', 3)">三局两胜</view>
          <view class="segment-item" :class="{ active: form.bestOf === 5 }" @click="setBestOf('bestOf', 5)">五局三胜</view>
        </view>
        <text class="hint">标准排球规则：常规局 25 分，末局 15 分，均需领先 2 分。</text>
      </view>

      <view class="section">
        <view class="section-title">裁判设置</view>
        <input class="input" v-model="form.refereePassword" type="number" maxlength="8" placeholder="裁判密码（8位数字，选填）" />
        <text class="hint">设置密码后，裁判可通过密码验证操作比赛。留空则不启用裁判功能。</text>
      </view>

      <view class="section team-section">
        <view class="section-head">
          <text class="section-title">参赛队伍</text>
          <text class="section-meta">已添加 {{ form.teams.length }} 支</text>
        </view>

        <view class="team-list" v-if="form.teams.length">
          <view class="team-card" v-for="(team, index) in form.teams" :key="team.id">
            <view class="team-main">
              <text class="team-name">{{ team.name }}</text>
              <text class="team-desc">{{ team.members.length }} 人 / 队长 {{ captainName(team) }}</text>
            </view>
            <view class="team-actions">
              <text class="team-action" @click="openEditor(index)">编辑</text>
              <text class="team-action danger" @click="removeTeam(index)">删除</text>
            </view>
          </view>
        </view>

        <view class="empty-card" v-else>
          <text class="empty-title">还没有队伍</text>
          <text class="empty-desc">先添加两支及以上队伍，再创建排球比赛。</text>
        </view>
      </view>
    </view>

    <view class="footer-bar">
      <button class="ghost-btn" @click="openEditor()">添加队伍</button>
      <button class="primary-btn" :loading="submitting" :disabled="submitting" @click="createTournament">创建比赛</button>
    </view>

    <view class="editor-mask" v-if="editorVisible" @click="closeEditor">
      <view class="editor-panel" @click.stop>
        <view class="editor-header">
          <text class="editor-title">{{ editingIndex === -1 ? '新增队伍' : '编辑队伍' }}</text>
          <text class="editor-close" @click="closeEditor">x</text>
        </view>

        <scroll-view class="editor-scroll" scroll-y>
          <input class="input" v-model="teamDraft.name" placeholder="球队名称" />

          <view class="draft-tools">
            <button class="sample-btn" @click="fillTestMembers">填入测试样例</button>
            <button class="sample-btn" @click="addDraftMember">添加球员</button>
          </view>

          <view class="member-list">
            <view class="member-row" v-for="(member, index) in teamDraft.members" :key="index">
              <text class="member-no">{{ index + 1 }}</text>
              <input class="member-input name" v-model="member.name" placeholder="球员姓名" />
              <input class="member-input jersey" type="number" v-model="member.jerseyNumber" placeholder="号码" />
              <view class="member-toggle captain" :class="{ active: teamDraft.captainIndex === index }" @click="setCaptain(index)">队长</view>
            </view>
          </view>
        </scroll-view>

        <view class="editor-footer">
          <button class="ghost-btn" @click="closeEditor">取消</button>
          <button class="primary-btn" @click="saveTeam">确定</button>
        </view>
      </view>
    </view>

    <ProfileGatePopup />
  </view>
</template>

<script setup>
import { computed, reactive, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import ProfileGatePopup from '@/components/ProfileGatePopup.vue'
import { guardProfileBeforeAction, requireProfile } from '@/store/auth'
import { useActionLock } from '@/utils/interaction-guard'
import { request } from '@/utils/request'
import {
  RANKING_CUSTOM_INPUT_PREFIX,
  RANKING_CUSTOM_RESULT_PREFIX,
  STANDARD_RANKING_MODE,
  defaultBaseTemplateForRankingMode,
  rankingStorageKey,
  summarizePriorities,
} from '@/pages/ranking/ranking-options'

// ???????????????????????? util?
// ????????????mp-weixin ????????/???????
// "utils/base-page-layout.js is not defined" ? ENOENT??????????
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

const pageStyle = buildBasePortraitPageStyle()

const submitting = ref(false)
const editorVisible = ref(false)
const editingIndex = ref(-1)
const seed = ref(1)
const { begin: beginNav } = useActionLock(500)
const customRankingKey = 'create_volleyball_ranking'

const form = reactive({
  name: '',
  location: '',
  bestOf: 3,
  groupRule: {
    bestOf: 3,
  },
  knockoutRule: {
    bestOf: 3,
  },
  tournamentType: 0,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  roundRobinRounds: 1,
  rankingTemplate: 'FIVB_VOLLEYBALL',
  rankingBaseTemplate: 'FIVB_VOLLEYBALL',
  rankingPriorities: [],
  thirdPlaceEnabled: false,
  refereePassword: '',
  teams: [],
})

const teamCount = computed(() => form.teams.length)
const showRankingConfig = computed(() => form.tournamentType === 1 || form.tournamentType === 2)
const rankingCustomSummary = computed(() => summarizePriorities(form.rankingPriorities))
const groupCount = computed(() => Math.max(1, Math.floor(form.knockoutSlots / form.qualifiersPerGroup)))
const estimatedGroupSize = computed(() => {
  if (!teamCount.value) return '-'
  return Math.ceil(teamCount.value / groupCount.value)
})
const estimatedLeagueMatches = computed(() => {
  const n = teamCount.value
  if (n < 2) return '-'
  const singleRound = n * (n - 1) / 2
  return singleRound * form.roundRobinRounds
})

const teamDraft = reactive(createEmptyDraft())
const volleyballRankingOptions = computed(() => [
  {
    value: 'FIVB_VOLLEYBALL',
    name: 'FIVB标准规则',
    desc: '胜场、比赛积分、胜负局比、得失分比，再看直接交手。',
  },
  {
    value: 'VOLLEYBALL_COMMON_1',
    name: '胜场数/胜局数/得失分比',
    desc: '先按胜场数排名，若胜场数相同，依次对比胜局数、得失分比',
  },
  {
    value: 'CUSTOM',
    name: '自定义',
    desc: rankingCustomSummary.value,
  },
])

function createEmptyDraft() {
  return {
    name: '',
    captainIndex: -1,
    // 原先这里固定 12 个槽位作为报名上限；现在保留默认槽位，但允许继续添加。
    members: Array.from({ length: 12 }, () => ({
      name: '',
      jerseyNumber: '',
    })),
  }
}

function resetDraft() {
  const fresh = createEmptyDraft()
  teamDraft.name = fresh.name
  teamDraft.captainIndex = fresh.captainIndex
  teamDraft.members.splice(0, teamDraft.members.length, ...fresh.members)
}

function goBack() {
  if (!beginNav()) return
  uni.navigateBack()
}

function setBestOf(ruleKey, bestOf) {
  if (bestOf == null) {
    bestOf = ruleKey
    ruleKey = 'bestOf'
  }
  if (ruleKey === 'bestOf') {
    form.bestOf = bestOf
    return
  }
  const rule = form[ruleKey]
  if (rule) rule.bestOf = bestOf
}

function setTournamentType(type) {
  form.tournamentType = type
  if (type === 2) {
    form.roundRobinRounds = 1
    form.thirdPlaceEnabled = false
  }
}

function selectRankingTemplate(option) {
  if (!option) return
  if (option.disabled) {
    uni.showToast({ title: '自定义规则暂未开放', icon: 'none' })
    return
  }
  if (option.value === 'CUSTOM') {
    openCustomRanking()
    return
  }
  form.rankingTemplate = option.value
  form.rankingBaseTemplate = option.value
  form.rankingPriorities = []
}

function openCustomRanking() {
  const baseTemplate = form.rankingTemplate === 'CUSTOM'
    ? form.rankingBaseTemplate || defaultBaseTemplateForRankingMode(STANDARD_RANKING_MODE, 1)
    : defaultBaseTemplateForRankingMode(STANDARD_RANKING_MODE, 1)
  uni.setStorageSync(rankingStorageKey(RANKING_CUSTOM_INPUT_PREFIX, customRankingKey), {
    mode: STANDARD_RANKING_MODE,
    baseTemplate,
    priorities: form.rankingTemplate === 'CUSTOM' ? form.rankingPriorities : [],
  })
  uni.navigateTo({
    url: '/pages/ranking/custom?key='
      + encodeURIComponent(customRankingKey)
      + '&mode='
      + encodeURIComponent(STANDARD_RANKING_MODE),
  })
}

function consumeCustomRankingResult() {
  const key = rankingStorageKey(RANKING_CUSTOM_RESULT_PREFIX, customRankingKey)
  const result = uni.getStorageSync(key)
  if (!result || !Array.isArray(result.priorities) || !result.priorities.length) return
  uni.removeStorageSync(key)
  form.rankingTemplate = 'CUSTOM'
  form.rankingBaseTemplate = result.baseTemplate || defaultBaseTemplateForRankingMode(STANDARD_RANKING_MODE, 1)
  form.rankingPriorities = result.priorities
}

function setThirdPlaceEnabled(enabled) {
  if (enabled && form.tournamentType === 2) {
    uni.showToast({ title: '循环赛不支持季军赛', icon: 'none' })
    form.thirdPlaceEnabled = false
    return
  }
  form.thirdPlaceEnabled = enabled
}

function setKnockoutSlots(slots) {
  form.knockoutSlots = slots
}

function setQualifiersPerGroup(event) {
  form.qualifiersPerGroup = Math.max(1, Math.min(2, Number(event.detail.value) || 1))
}

function openEditor(index = -1) {
  editingIndex.value = index
  resetDraft()
  if (index >= 0) {
    const team = form.teams[index]
    teamDraft.name = team.name
    teamDraft.captainIndex = team.members.findIndex((item) => item.captain)
    while (teamDraft.members.length < team.members.length) {
      teamDraft.members.push({ name: '', jerseyNumber: '' })
    }
    team.members.forEach((member, memberIndex) => {
      if (!teamDraft.members[memberIndex]) return
      teamDraft.members[memberIndex] = {
        name: member.name,
        jerseyNumber: String(member.jerseyNumber),
      }
    })
  }
  editorVisible.value = true
}

function closeEditor() {
  editorVisible.value = false
  editingIndex.value = -1
}

function nextDraftJerseyNumber() {
  const jerseys = teamDraft.members
    .map((member) => Number(member.jerseyNumber))
    .filter((number) => Number.isFinite(number) && number > 0)
  return jerseys.length ? Math.max(...jerseys) + 1 : 1
}

function addDraftMember() {
  teamDraft.members.push({ name: '', jerseyNumber: String(nextDraftJerseyNumber()) })
}

function setCaptain(index) {
  if (!teamDraft.members[index].name.trim()) {
    uni.showToast({ title: '请先填写球员姓名', icon: 'none' })
    return
  }
  teamDraft.captainIndex = teamDraft.captainIndex === index ? -1 : index
}

function fillTestMembers() {
  const sampleNames = ['张一', '张二', '张三', '张四', '张五', '张六', '张七', '张八', '张九', '张十', '张十一', '张十二']
  while (teamDraft.members.length < sampleNames.length) {
    addDraftMember()
  }
  teamDraft.members.forEach((member, index) => {
    member.name = sampleNames[index] || ''
    member.jerseyNumber = sampleNames[index] ? String(index + 1) : ''
  })
  teamDraft.captainIndex = 0
  uni.showToast({ title: '已填入测试样例', icon: 'none' })
}

function normalizeTeamDraft() {
  const members = teamDraft.members
    .map((member, index) => ({
      name: member.name.trim(),
      jerseyNumber: Number(member.jerseyNumber),
      captain: teamDraft.captainIndex === index,
    }))
    .filter((member) => member.name)

  return {
    id: editingIndex.value >= 0 ? form.teams[editingIndex.value].id : 'team_' + seed.value++,
    name: teamDraft.name.trim(),
    members,
  }
}

function validateTeam(team) {
  if (!team.name) {
    uni.showToast({ title: '请输入球队名称', icon: 'none' })
    return false
  }
  if (team.members.length < 6) {
    uni.showToast({ title: '每支队伍至少需要 6 名球员', icon: 'none' })
    return false
  }

  let captainCount = 0
  const jerseySet = new Set()
  for (const member of team.members) {
    if (!Number.isFinite(member.jerseyNumber) || member.jerseyNumber <= 0) {
      uni.showToast({ title: '请填写有效的球员号码', icon: 'none' })
      return false
    }
    if (jerseySet.has(member.jerseyNumber)) {
      uni.showToast({ title: '同一支队伍不能有重复号码', icon: 'none' })
      return false
    }
    jerseySet.add(member.jerseyNumber)
    if (member.captain) captainCount += 1
  }
  if (captainCount !== 1) {
    uni.showToast({ title: '请指定 1 名队长', icon: 'none' })
    return false
  }
  return true
}

function saveTeam() {
  const team = normalizeTeamDraft()
  if (!validateTeam(team)) return

  if (editingIndex.value >= 0) {
    form.teams.splice(editingIndex.value, 1, team)
  } else {
    form.teams.push(team)
  }
  closeEditor()
}

function removeTeam(index) {
  form.teams.splice(index, 1)
}

function captainName(team) {
  return team.members.find((item) => item.captain)?.name || '-'
}

function volleyballRulePayload(rule) {
  const bestOf = Number(rule?.bestOf || form.bestOf || 3)
  return {
    bestOf,
    gamesToWin: Math.floor(bestOf / 2) + 1,
    pointsToWin: 25,
    decidingPointsToWin: 15,
    enableDeuce: true,
    capPoint: 99,
  }
}

function buildVolleyballRoundRules() {
  const knockoutRounds = Math.max(1, Math.round(Math.log2(Number(form.knockoutSlots || 2))))
  return [
    { stageType: 0, roundNum: 0, rule: volleyballRulePayload(form.groupRule) },
    ...Array.from({ length: knockoutRounds }, (_, index) => ({
      stageType: 1,
      roundNum: index + 1,
      rule: volleyballRulePayload(form.knockoutRule),
    })),
  ]
}

function validateTournamentConfig() {
  if (form.tournamentType === 1 && form.knockoutSlots > form.teams.length) {
    uni.showToast({ title: '淘汰名额不能超过参赛队伍数', icon: 'none' })
    return false
  }
  if (form.tournamentType === 1 && teamCount.value) {
    const minGroupSize = Math.floor(teamCount.value / groupCount.value)
    if (minGroupSize <= form.qualifiersPerGroup) {
      uni.showToast({ title: '每组队伍数必须大于出线名额', icon: 'none' })
      return false
    }
  }
  if (form.thirdPlaceEnabled) {
    const thirdPlaceCount = form.tournamentType === 1 ? form.knockoutSlots : form.teams.length
    if (thirdPlaceCount < 4) {
      uni.showModal({
        title: '无法开启季军赛',
        content: `季军赛需要至少 4 个淘汰阶段参赛队伍，当前只有 ${thirdPlaceCount} 队。`,
        showCancel: false,
      })
      return false
    }
  }
  return true
}

async function createTournament() {
  if (submitting.value) return
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入比赛名称', icon: 'none' })
    return
  }
  if (form.teams.length < 2) {
    uni.showToast({ title: '至少需要 2 支队伍', icon: 'none' })
    return
  }
  if (!validateTournamentConfig()) return

  submitting.value = true
  try {
    await requireProfile()
    const baseRule = form.tournamentType === 1
      ? volleyballRulePayload(form.groupRule)
      : volleyballRulePayload({ bestOf: form.bestOf })
    const knockoutRule = form.tournamentType === 1
      ? volleyballRulePayload(form.knockoutRule)
      : volleyballRulePayload({ bestOf: form.bestOf })
    const res = await request('/api/v1/tournaments', {
      method: 'POST',
      data: {
        sportType: 1,
        name: form.name.trim(),
        location: form.location.trim() || undefined,
        tournamentType: form.tournamentType,
        knockoutSlots: form.tournamentType === 1 ? form.knockoutSlots : undefined,
        qualifiersPerGroup: form.tournamentType === 1 ? form.qualifiersPerGroup : undefined,
        roundRobinRounds: form.tournamentType === 2 ? form.roundRobinRounds : undefined,
        rankingTemplate: showRankingConfig.value
          ? (form.rankingTemplate === 'CUSTOM' ? form.rankingBaseTemplate : form.rankingTemplate)
          : undefined,
        rankingPriorities: showRankingConfig.value && form.rankingTemplate === 'CUSTOM'
          ? form.rankingPriorities
          : undefined,
        thirdPlaceEnabled: form.tournamentType !== 2 && form.thirdPlaceEnabled,
        roundRuleEnabled: form.tournamentType === 1,
        roundRules: form.tournamentType === 1 ? buildVolleyballRoundRules() : undefined,
        players: [],
        teams: form.teams.map((team) => ({
          name: team.name,
          members: team.members.map((member) => ({
            name: member.name,
            jerseyNumber: member.jerseyNumber,
            captain: member.captain,
          })),
        })),
        rule: baseRule,
        thirdPlaceRule: form.thirdPlaceEnabled ? knockoutRule : undefined,
        refereePassword: form.refereePassword.trim() || undefined,
      },
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    uni.redirectTo({ url: '/pages/tournament/detail?id=' + res.tournamentId })
  } catch (error) {
    if (error?.message && error.message !== '你取消了资料补全') {
      uni.showToast({ title: error.message, icon: 'none' })
    }
  } finally {
    submitting.value = false
  }
}

onShow(async () => {
  if (!(await guardProfileBeforeAction('请先完善个人资料，再创建比赛'))) {
    uni.navigateBack()
    return
  }
  consumeCustomRankingResult()
})
</script>

<style scoped>
.page {
  min-height: 100vh;
  padding: 0 24rpx 160rpx;
  box-sizing: border-box;
  background:
    radial-gradient(circle at top left, rgba(255, 140, 0, 0.18), transparent 34%),
    linear-gradient(180deg, #13202d 0%, #0f1822 100%);
}

.header,
.section-head,
.team-card,
.editor-header,
.editor-footer,
.footer-bar,
.rule-row {
  display: flex;
  align-items: center;
}

.header {
  gap: 18rpx;
  margin-bottom: 24rpx;
}

.back-btn {
  color: #ffb347;
  font-size: 26rpx;
}

.title {
  color: #ffffff;
  font-size: 34rpx;
  font-weight: 700;
}

.form-panel {
  padding: 28rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.06);
}

.input,
.step-input {
  width: 100%;
  height: 84rpx;
  padding: 0 22rpx;
  box-sizing: border-box;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 26rpx;
}

.input {
  margin-bottom: 16rpx;
}

.section {
  margin-top: 22rpx;
  padding: 22rpx;
  border-radius: 18rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.2);
}

.section-title {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.ranking-section {
  margin-top: 18rpx;
}

.compact-title {
  font-size: 26rpx;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 14rpx;
}

.template-card {
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.22);
  background: rgba(255, 255, 255, 0.05);
}

.template-card.active {
  border-color: rgba(255, 140, 0, 0.68);
  background: rgba(255, 140, 0, 0.12);
}

.template-card.disabled {
  opacity: 0.48;
}

.template-name,
.template-desc {
  display: block;
}

.template-name {
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.template-desc {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 23rpx;
}

.segment {
  display: flex;
  margin-top: 16rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  border-radius: 14rpx;
  overflow: hidden;
}

.segment.compact {
  margin-top: 0;
  min-width: 280rpx;
}

.segment-item {
  flex: 1;
  min-height: 56rpx;
  line-height: 56rpx;
  text-align: center;
  color: rgba(255, 255, 255, 0.68);
  font-size: 24rpx;
  background: rgba(255, 255, 255, 0.05);
}

.segment-item.active {
  background: #ff8c00;
  color: #152231;
  font-weight: 700;
}

.rule-row {
  justify-content: space-between;
  gap: 18rpx;
  margin-top: 18rpx;
}

.rule-label {
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 600;
}

.rule-subsection {
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid rgba(255, 140, 0, 0.18);
}

.rule-subtitle {
  display: block;
  color: #ffffff;
  font-size: 24rpx;
  font-weight: 700;
}

.stepper {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.step-btn {
  width: 52rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  border-radius: 10rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  color: #ffb347;
}

.step-input {
  width: 84rpx;
  height: 52rpx;
  line-height: 52rpx;
  text-align: center;
  padding: 0;
  border-radius: 10rpx;
}

.hint,
.section-meta,
.team-desc,
.empty-desc {
  color: rgba(255, 255, 255, 0.62);
  font-size: 24rpx;
  line-height: 1.6;
}

.hint {
  display: block;
  margin-top: 16rpx;
}

.section-head {
  justify-content: space-between;
}

.team-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.team-card,
.empty-card {
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.05);
}

.team-card {
  justify-content: space-between;
  align-items: flex-start;
  gap: 16rpx;
}

.team-main {
  flex: 1;
  min-width: 0;
}

.team-name,
.empty-title,
.editor-title {
  color: #ffffff;
  font-weight: 700;
}

.team-name {
  display: block;
  font-size: 30rpx;
}

.team-actions {
  display: flex;
  gap: 14rpx;
}

.team-action {
  color: #ffb347;
  font-size: 24rpx;
}

.team-action.danger {
  color: #ff7a45;
}

.empty-title {
  display: block;
  font-size: 30rpx;
}

.empty-desc {
  display: block;
  margin-top: 10rpx;
}

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  gap: 16rpx;
}

.ghost-btn,
.primary-btn {
  flex: 1;
  height: 88rpx;
  line-height: 88rpx;
  border-radius: 18rpx;
  border: none;
  font-size: 28rpx;
}

.ghost-btn::after,
.primary-btn::after,
.sample-btn::after {
  border: none;
}

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn {
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-weight: 800;
}

.editor-mask {
  position: fixed;
  inset: 0;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.58);
  z-index: 30;
}

.editor-panel {
  width: 100%;
  max-height: 90vh;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  background: linear-gradient(180deg, #23384d 0%, #172636 100%);
  border-top: 1rpx solid rgba(255, 140, 0, 0.35);
  padding: 24rpx 24rpx 32rpx;
  box-sizing: border-box;
}

.editor-header {
  justify-content: space-between;
}

.editor-title {
  font-size: 32rpx;
}

.editor-close {
  color: rgba(255, 255, 255, 0.58);
  font-size: 40rpx;
  padding: 0 10rpx;
}

.editor-scroll {
  max-height: 66vh;
  margin-top: 18rpx;
}

.draft-tools {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 16rpx;
}

.sample-btn {
  height: 64rpx;
  line-height: 64rpx;
  padding: 0 24rpx;
  border: none;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.16);
  color: #ffb347;
  font-size: 24rpx;
}

.member-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-bottom: 12rpx;
}

.member-row {
  display: grid;
  grid-template-columns: 48rpx 1fr 132rpx 110rpx;
  gap: 10rpx;
  align-items: center;
}

.member-no {
  color: rgba(255, 255, 255, 0.55);
  font-size: 22rpx;
  text-align: center;
}

.member-input {
  height: 72rpx;
  box-sizing: border-box;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  font-size: 24rpx;
  padding: 0 18rpx;
}

.member-input.jersey {
  text-align: center;
}

.member-toggle {
  height: 72rpx;
  line-height: 72rpx;
  text-align: center;
  border-radius: 14rpx;
  background: rgba(255, 255, 255, 0.06);
  color: rgba(255, 255, 255, 0.72);
  font-size: 22rpx;
}

.member-toggle.active {
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
  font-weight: 700;
}

.member-toggle.captain.active {
  background: #ff8c00;
  color: #142130;
}

.editor-footer {
  gap: 16rpx;
  margin-top: 18rpx;
}
</style>
