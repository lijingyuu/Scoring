<template>
  <view class="page" :style="pageStyle">
    <view class="header">
      <text class="back-btn safe-back-btn" @click="goBack">返回</text>
      <text class="title">创建比赛</text>
    </view>

    <view class="form-panel">
      <input class="input" v-model="form.name" placeholder="比赛名称（必填）" />
      <input class="input" v-model="form.location" placeholder="比赛地点（选填）" />

      <view class="section">
        <view class="section-title">参赛形式</view>
        <view class="segment">
          <view class="segment-item" :class="{ active: form.participantType === 0 }" @click="setParticipantType(0)">个人赛</view>
          <view class="segment-item" :class="{ active: form.participantType === 1 }" @click="setParticipantType(1)">团体赛</view>
        </view>
      </view>

      <view class="section" v-if="!isIndividual">
        <view class="section-title">团体模板</view>
        <view class="template-list">
          <view class="template-card" :class="{ active: form.teamMatchTemplate === 1 }" @click="setTeamMatchTemplate(1)">
            <text class="template-name">苏迪曼杯五项</text>
            <text class="template-desc">男单、女单、男双、女双、混双</text>
            <view class="template-items">
              <text class="template-item" v-for="item in teamMatchItems" :key="item.code">{{ item.name }}</text>
            </view>
          </view>
          <view class="template-card" :class="{ active: form.teamMatchTemplate === 2 }" @click="setTeamMatchTemplate(2)">
            <text class="template-name">人员流转追分赛</text>
            <text class="template-desc">全程双打，1+2、2+3，最后循环回第 1 人</text>
          </view>
        </view>
        <view class="rule-row" v-if="isRelayTemplate">
          <text class="rule-label">轮转人数</text>
          <view class="stepper">
            <view class="step-btn" @click="changeRelayMemberCount(-1)">-</view>
            <input class="step-input" type="number" :value="form.relayMemberCount" @input="setRelayMemberCount" />
            <view class="step-btn" @click="changeRelayMemberCount(1)">+</view>
          </view>
        </view>
        <text class="hint" v-if="isRelayTemplate">{{ relayHintText }}</text>
      </view>

      <view class="section">
        <view class="section-title">赛制</view>
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
          <view class="hint">{{ participantCount }} {{ participantUnit }}，预计 {{ estimatedLeagueMatches }} 场比赛</view>
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
          <view class="hint">预计 {{ groupCount }} 组，每组约 {{ estimatedGroupSize }} {{ participantUnit }}</view>
        </template>

        <view class="ranking-section" v-if="showRankingConfig">
          <view class="section-title compact-title">小组赛排名规则</view>
          <view class="template-list">
            <view
              class="template-card"
              v-for="option in badmintonRankingOptions"
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
        <view class="section-title">规则</view>
        <view class="rule-subsection">
          <view class="rule-subtitle">小组赛规则</view>
          <view class="segment" v-if="!isRelayTemplate">
            <view class="segment-item" :class="{ active: form.groupRule.bestOf === 1 }" @click="setBestOf('groupRule', 1)">一局</view>
            <view class="segment-item" :class="{ active: form.groupRule.bestOf === 3 }" @click="setBestOf('groupRule', 3)">三局</view>
            <view class="segment-item" :class="{ active: form.groupRule.bestOf === 5 }" @click="setBestOf('groupRule', 5)">五局</view>
          </view>
          <view class="rule-row">
            <text class="rule-label">{{ rulePointsLabel }}</text>
            <view class="stepper">
              <view class="step-btn" @click="changePointsToWin('groupRule', -1)">-</view>
              <input class="step-input" type="number" :value="form.groupRule.pointsToWin" @input="setPointsToWin('groupRule', $event)" />
              <view class="step-btn" @click="changePointsToWin('groupRule', 1)">+</view>
            </view>
          </view>
          <view class="rule-row" v-if="!isRelayTemplate">
            <text class="rule-label">追分机制</text>
            <view class="segment compact">
              <view class="segment-item" :class="{ active: form.groupRule.enableDeuce }" @click="form.groupRule.enableDeuce = true">开启</view>
              <view class="segment-item" :class="{ active: !form.groupRule.enableDeuce }" @click="form.groupRule.enableDeuce = false">关闭</view>
            </view>
          </view>
          <view class="rule-row" v-if="!isRelayTemplate && form.groupRule.enableDeuce">
            <text class="rule-label">封顶分</text>
            <view class="stepper">
              <view class="step-btn" @click="changeCapPoint('groupRule', -1)">-</view>
              <input class="step-input" type="number" :value="form.groupRule.capPoint" @input="setCapPoint('groupRule', $event)" />
              <view class="step-btn" @click="changeCapPoint('groupRule', 1)">+</view>
            </view>
          </view>
        </view>

        <view class="rule-subsection">
          <view class="rule-subtitle">淘汰赛规则</view>
          <view class="segment" v-if="!isRelayTemplate">
            <view class="segment-item" :class="{ active: form.knockoutRule.bestOf === 1 }" @click="setBestOf('knockoutRule', 1)">一局</view>
            <view class="segment-item" :class="{ active: form.knockoutRule.bestOf === 3 }" @click="setBestOf('knockoutRule', 3)">三局</view>
            <view class="segment-item" :class="{ active: form.knockoutRule.bestOf === 5 }" @click="setBestOf('knockoutRule', 5)">五局</view>
          </view>
          <view class="rule-row">
            <text class="rule-label">{{ rulePointsLabel }}</text>
            <view class="stepper">
              <view class="step-btn" @click="changePointsToWin('knockoutRule', -1)">-</view>
              <input class="step-input" type="number" :value="form.knockoutRule.pointsToWin" @input="setPointsToWin('knockoutRule', $event)" />
              <view class="step-btn" @click="changePointsToWin('knockoutRule', 1)">+</view>
            </view>
          </view>
          <view class="rule-row" v-if="!isRelayTemplate">
            <text class="rule-label">追分机制</text>
            <view class="segment compact">
              <view class="segment-item" :class="{ active: form.knockoutRule.enableDeuce }" @click="form.knockoutRule.enableDeuce = true">开启</view>
              <view class="segment-item" :class="{ active: !form.knockoutRule.enableDeuce }" @click="form.knockoutRule.enableDeuce = false">关闭</view>
            </view>
          </view>
          <view class="rule-row" v-if="!isRelayTemplate && form.knockoutRule.enableDeuce">
            <text class="rule-label">封顶分</text>
            <view class="stepper">
              <view class="step-btn" @click="changeCapPoint('knockoutRule', -1)">-</view>
              <input class="step-input" type="number" :value="form.knockoutRule.capPoint" @input="setCapPoint('knockoutRule', $event)" />
              <view class="step-btn" @click="changeCapPoint('knockoutRule', 1)">+</view>
            </view>
          </view>
        </view>
      </view>

      <view class="section" v-else>
        <view class="section-title">规则</view>
        <view class="segment" v-if="!isRelayTemplate">
          <view class="segment-item" :class="{ active: form.rule.bestOf === 1 }" @click="setBestOf('rule', 1)">一局</view>
          <view class="segment-item" :class="{ active: form.rule.bestOf === 3 }" @click="setBestOf('rule', 3)">三局</view>
          <view class="segment-item" :class="{ active: form.rule.bestOf === 5 }" @click="setBestOf('rule', 5)">五局</view>
        </view>
        <view class="rule-row">
          <text class="rule-label">{{ rulePointsLabel }}</text>
          <view class="stepper">
            <view class="step-btn" @click="changePointsToWin('rule', -1)">-</view>
            <input class="step-input" type="number" :value="form.rule.pointsToWin" @input="setPointsToWin('rule', $event)" />
            <view class="step-btn" @click="changePointsToWin('rule', 1)">+</view>
          </view>
        </view>
        <view class="rule-row" v-if="!isRelayTemplate">
          <text class="rule-label">追分机制</text>
          <view class="segment compact">
            <view class="segment-item" :class="{ active: form.rule.enableDeuce }" @click="form.rule.enableDeuce = true">开启</view>
            <view class="segment-item" :class="{ active: !form.rule.enableDeuce }" @click="form.rule.enableDeuce = false">关闭</view>
          </view>
        </view>
        <view class="rule-row" v-if="!isRelayTemplate && form.rule.enableDeuce">
          <text class="rule-label">封顶分</text>
          <view class="stepper">
            <view class="step-btn" @click="changeCapPoint('rule', -1)">-</view>
            <input class="step-input" type="number" :value="form.rule.capPoint" @input="setCapPoint('rule', $event)" />
            <view class="step-btn" @click="changeCapPoint('rule', 1)">+</view>
          </view>
        </view>
      </view>

      <view class="section">
        <view class="section-title">裁判设置</view>
        <input class="input" v-model="form.refereePassword" type="number" maxlength="8" placeholder="裁判密码（8位数字，选填）" />
        <text class="hint">设置密码后，裁判可通过密码验证操作比赛。留空则不启用裁判功能。</text>
      </view>

      <textarea v-if="isIndividual" class="textarea" v-model="form.players" placeholder="每行一名选手，可在前面加种子序号，例如：1 张三" />

      <view class="section team-section" v-else>
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
          <text class="empty-desc">{{ emptyTeamDesc }}</text>
        </view>
      </view>

      <button v-if="isIndividual" class="submit-btn" :loading="submitting" :disabled="submitting" @click="createTournament">生成比赛</button>
    </view>

    <view class="footer-bar" v-if="!isIndividual">
      <button class="ghost-btn" @click="openEditor()">添加队伍</button>
      <button class="primary-btn" :loading="submitting" :disabled="submitting" @click="createTournament">生成比赛</button>
    </view>

    <view class="editor-mask" v-if="editorVisible" @click="closeEditor">
      <view class="editor-panel" @click.stop>
        <view class="editor-header">
          <text class="editor-title">{{ editorTitle }}</text>
          <text class="editor-close" @click="closeEditor">x</text>
        </view>
        <scroll-view class="editor-scroll" scroll-y>
          <input class="input" v-model="teamDraft.name" placeholder="队伍名称" />

          <view class="draft-tools">
            <button class="sample-btn" @click="fillTestMembers">填入测试样例</button>
            <button class="sample-btn" @click="addDraftMember">添加成员</button>
          </view>

          <view class="member-list">
            <view class="member-row" v-for="(member, index) in visibleDraftMembers" :key="index">
              <text class="member-no">{{ index + 1 }}</text>
              <input class="member-input name" v-model="member.name" :placeholder="memberPlaceholder(index)" />
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
  RELAY_RANKING_MODE,
  STANDARD_RANKING_MODE,
  TEAM_RANKING_MODE,
  defaultBaseTemplateForRankingMode,
  rankingStorageKey,
  summarizePriorities,
} from '@/pages/ranking/ranking-options'

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
const customRankingKey = 'create_badminton_ranking'

const form = reactive({
  name: '',
  location: '',
  players: '',
  participantType: 0,
  teamMatchTemplate: 1,
  relayMemberCount: 6,
  tournamentType: 0,
  knockoutSlots: 8,
  qualifiersPerGroup: 2,
  roundRobinRounds: 1,
  rankingTemplate: 'BWF_BADMINTON',
  rankingBaseTemplate: 'BADMINTON_COMMON_1',
  rankingPriorities: [],
  thirdPlaceEnabled: false,
  refereePassword: '',
  teams: [],
  rule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
  groupRule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
  knockoutRule: {
    bestOf: 3,
    gamesToWin: 2,
    pointsToWin: 21,
    enableDeuce: true,
    capPoint: 30,
  },
})

const teamDraft = reactive(createEmptyDraft())
// Creation page preview only; the lineup page renders items returned by the backend API.
const teamMatchItems = [
  { code: 'MS', name: '男单', playerCount: 1 },
  { code: 'WS', name: '女单', playerCount: 1 },
  { code: 'MD', name: '男双', playerCount: 2 },
  { code: 'WD', name: '女双', playerCount: 2 },
  { code: 'XD', name: '混双', playerCount: 2 },
]
const isIndividual = computed(() => form.participantType === 0)
const isRelayTemplate = computed(() => !isIndividual.value && form.teamMatchTemplate === 2)
const showRankingConfig = computed(() => form.tournamentType === 1 || form.tournamentType === 2)
const rankingCustomSummary = computed(() => summarizePriorities(form.rankingPriorities))
const badmintonRankingOptions = computed(() => {
  const commonOption = isIndividual.value
    ? {
        value: 'BADMINTON_COMMON_1',
        name: '胜场数/净胜局/得失分比',
        desc: '先按胜场数排名，若胜场数相同，依次对比净胜局、得失分比。',
      }
    : isRelayTemplate.value
      ? {
          value: 'BADMINTON_RELAY_COMMON_1',
          name: '胜场数/两队直胜/小分得失比',
          desc: '先按胜场数排名，若两队胜场相同先看直接交手，再看小分得失比。',
        }
      : {
          value: 'BADMINTON_TEAM_COMMON_1',
          name: '常用模板一',
          desc: '胜场；两队直胜，多队看场内大分、场内局、局内小分。',
        }
  if (isRelayTemplate.value) {
    return [
      commonOption,
      {
        value: 'CUSTOM',
        name: '自定义',
        desc: rankingCustomSummary.value,
      },
    ]
  }
  return [
    {
      value: 'BWF_BADMINTON',
      name: 'BWF标准规则',
      desc: '胜场优先；两人同分先看直胜，再看净胜局、净胜分。',
    },
    commonOption,
    {
      value: 'CUSTOM',
      name: '自定义',
      desc: rankingCustomSummary.value,
    },
  ]
})
const visibleDraftMembers = computed(() => teamDraft.members)
const groupCount = computed(() => Math.max(1, Math.floor(form.knockoutSlots / form.qualifiersPerGroup)))
const playerCount = computed(() => parsePlayers(form.players).length)
const teamCount = computed(() => form.teams.length)
const participantCount = computed(() => (isIndividual.value ? playerCount.value : teamCount.value))
const participantUnit = computed(() => (isIndividual.value ? '人' : '队'))
const relayHintText = computed(() => `本赛事固定 ${form.relayMemberCount} 人轮转；队伍报名人数由主办方控制，可在不同场次选择不同队员。`)
const rulePointsLabel = computed(() => (isRelayTemplate.value ? '分段基准分' : '基础胜分'))
const emptyTeamDesc = computed(() => (isRelayTemplate.value ? '每队报名人数由主办方控制，并指定 1 名队长。' : '每队至少 2 名成员，并指定 1 名队长。'))
const editorTitle = computed(() => (editingIndex.value === -1 ? '新增队伍' : '编辑队伍'))
const estimatedGroupSize = computed(() => {
  if (!participantCount.value) return '-'
  return Math.ceil(participantCount.value / groupCount.value)
})
const estimatedLeagueMatches = computed(() => {
  const n = participantCount.value
  if (n < 2) return '-'
  return n * (n - 1) / 2 * form.roundRobinRounds
})

function goBack() {
  if (!beginNav()) return
  uni.navigateBack()
}

function setParticipantType(type) {
  form.participantType = type
  if (type === 1) form.teamMatchTemplate = 1
  syncRankingTemplateForMode(true)
}

function setTeamMatchTemplate(template) {
  if (template !== 1 && template !== 2) {
    uni.showToast({ title: '该模板尚未开放', icon: 'none' })
    return
  }
  form.teamMatchTemplate = template
  if (template === 2) {
    allMatchRules().forEach((rule) => {
      rule.bestOf = 1
      rule.gamesToWin = 1
      rule.enableDeuce = false
      rule.capPoint = Math.min(99, rule.pointsToWin + 1)
    })
  } else {
    allMatchRules().forEach((rule) => {
      rule.bestOf = 3
      rule.gamesToWin = 2
      rule.enableDeuce = true
      rule.capPoint = Math.max(rule.pointsToWin + 1, 30)
    })
  }
  syncRankingTemplateForMode(true)
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

function defaultRankingTemplateForMode() {
  if (isIndividual.value) return 'BWF_BADMINTON'
  if (isRelayTemplate.value) return 'BADMINTON_RELAY_COMMON_1'
  return 'BADMINTON_TEAM_COMMON_1'
}

function syncRankingTemplateForMode(forceDefault = false) {
  const options = badmintonRankingOptions.value || []
  if (forceDefault || !options.some((option) => option.value === form.rankingTemplate && !option.disabled)) {
    form.rankingTemplate = defaultRankingTemplateForMode()
    form.rankingBaseTemplate = form.rankingTemplate
    form.rankingPriorities = []
  }
}

function rankingModeForCurrentForm() {
  if (isIndividual.value) return STANDARD_RANKING_MODE
  return isRelayTemplate.value ? RELAY_RANKING_MODE : TEAM_RANKING_MODE
}

function defaultCustomBaseTemplateForMode() {
  return defaultBaseTemplateForRankingMode(rankingModeForCurrentForm(), 0)
}

function openCustomRanking() {
  const mode = rankingModeForCurrentForm()
  const baseTemplate = form.rankingTemplate === 'CUSTOM'
    ? form.rankingBaseTemplate || defaultCustomBaseTemplateForMode()
    : defaultCustomBaseTemplateForMode()
  uni.setStorageSync(rankingStorageKey(RANKING_CUSTOM_INPUT_PREFIX, customRankingKey), {
    mode,
    baseTemplate,
    priorities: form.rankingTemplate === 'CUSTOM' ? form.rankingPriorities : [],
  })
  uni.navigateTo({
    url: '/pages/ranking/custom?key='
      + encodeURIComponent(customRankingKey)
      + '&mode='
      + encodeURIComponent(mode),
  })
}

function consumeCustomRankingResult() {
  const key = rankingStorageKey(RANKING_CUSTOM_RESULT_PREFIX, customRankingKey)
  const result = uni.getStorageSync(key)
  if (!result || !Array.isArray(result.priorities) || !result.priorities.length) return
  uni.removeStorageSync(key)
  form.rankingTemplate = 'CUSTOM'
  form.rankingBaseTemplate = result.baseTemplate || defaultCustomBaseTemplateForMode()
  form.rankingPriorities = result.priorities
}

function setKnockoutSlots(slots) {
  form.knockoutSlots = slots
}

function setQualifiersPerGroup(event) {
  form.qualifiersPerGroup = Math.max(1, Math.min(2, Number(event.detail.value) || 1))
}

function changeRelayMemberCount(delta) {
  form.relayMemberCount = Math.max(3, Math.min(12, form.relayMemberCount + delta))
}

function setRelayMemberCount(event) {
  form.relayMemberCount = Math.max(3, Math.min(12, Number(event.detail.value) || 6))
}

function ruleByKey(ruleKey = 'rule') {
  return form[ruleKey] || form.rule
}

function allMatchRules() {
  return [form.rule, form.groupRule, form.knockoutRule]
}

function setBestOf(ruleKey, bestOf) {
  if (bestOf == null) {
    bestOf = ruleKey
    ruleKey = 'rule'
  }
  const rule = ruleByKey(ruleKey)
  rule.bestOf = bestOf
  rule.gamesToWin = Math.floor(bestOf / 2) + 1
}

function setPointsToWin(ruleKey, event) {
  if (event == null) {
    event = ruleKey
    ruleKey = 'rule'
  }
  const rule = ruleByKey(ruleKey)
  const value = Math.max(1, Math.min(99, Number(event.detail.value) || 1))
  rule.pointsToWin = value
  if (isRelayTemplate.value) {
    rule.capPoint = Math.min(99, value + 1)
    return
  }
  if (rule.capPoint <= value) rule.capPoint = Math.min(99, value + 1)
}

function changePointsToWin(ruleKey, delta) {
  if (delta == null) {
    delta = ruleKey
    ruleKey = 'rule'
  }
  const rule = ruleByKey(ruleKey)
  setPointsToWin(ruleKey, { detail: { value: rule.pointsToWin + delta } })
}

function setCapPoint(ruleKey, event) {
  if (event == null) {
    event = ruleKey
    ruleKey = 'rule'
  }
  const rule = ruleByKey(ruleKey)
  const min = rule.pointsToWin + 1
  rule.capPoint = Math.max(min, Math.min(99, Number(event.detail.value) || min))
}

function changeCapPoint(ruleKey, delta) {
  if (delta == null) {
    delta = ruleKey
    ruleKey = 'rule'
  }
  const rule = ruleByKey(ruleKey)
  setCapPoint(ruleKey, { detail: { value: rule.capPoint + delta } })
}

function setThirdPlaceEnabled(enabled) {
  if (enabled && form.tournamentType === 2) {
    uni.showToast({ title: '循环赛不支持季军赛', icon: 'none' })
    form.thirdPlaceEnabled = false
    return
  }
  form.thirdPlaceEnabled = enabled
}

function memberPlaceholder(index) {
  return isRelayTemplate.value ? `第 ${index + 1} 位` : '成员姓名'
}

function parsePlayers(text) {
  return text
    .split(/[\n\r]+/)
    .map((s) => s.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)[.\s、]?\s*(.+)$/)
      if (!match) return { name: line, seed: null }
      return { name: match[2].trim(), seed: parseInt(match[1], 10) }
    })
}

function createEmptyDraft() {
  return {
    name: '',
    captainIndex: -1,
    // 原先这里固定 12 个槽位作为报名上限；现在保留默认槽位，但允许继续添加。
    members: Array.from({ length: 12 }, () => ({ name: '' })),
  }
}

function resetDraft() {
  const fresh = createEmptyDraft()
  teamDraft.name = fresh.name
  teamDraft.captainIndex = fresh.captainIndex
  teamDraft.members.splice(0, teamDraft.members.length, ...fresh.members)
}

function openEditor(index = -1) {
  editingIndex.value = index
  resetDraft()
  if (index >= 0) {
    const team = form.teams[index]
    teamDraft.name = team.name
    teamDraft.captainIndex = team.members.findIndex((item) => item.captain)
    while (teamDraft.members.length < team.members.length) {
      teamDraft.members.push({ name: '' })
    }
    team.members.forEach((member, memberIndex) => {
      if (!teamDraft.members[memberIndex]) return
      teamDraft.members[memberIndex] = { name: member.name }
    })
  }
  editorVisible.value = true
}

function closeEditor() {
  editorVisible.value = false
  editingIndex.value = -1
}

function addDraftMember() {
  teamDraft.members.push({ name: '' })
}


function fillTestMembers() {
  const sampleNames = ["张一","张二","张三","张四","张五","张六","张七","张八","张九","张十","张十一","张十二","张十三","张十四","张十五"]
  while (teamDraft.members.length < sampleNames.length) {
    addDraftMember()
  }
  teamDraft.members.forEach((member, index) => {
    member.name = sampleNames[index] || ''
  })
  teamDraft.captainIndex = 0
  uni.showToast({ title: '已填入测试样例', icon: 'none' })
}

function setCaptain(index) {
  if (!teamDraft.members[index].name.trim()) {
    uni.showToast({ title: '请先填写成员姓名', icon: 'none' })
    return
  }
  teamDraft.captainIndex = teamDraft.captainIndex === index ? -1 : index
}

function normalizeTeamDraft() {
  const members = teamDraft.members
    .map((member, index) => ({ name: member.name.trim(), captain: teamDraft.captainIndex === index }))
    .filter((member) => member.name)
  return {
    id: editingIndex.value >= 0 ? form.teams[editingIndex.value].id : 'badminton_team_' + seed.value++,
    name: teamDraft.name.trim(),
    members,
  }
}

function validateTeam(team) {
  if (!team.name) {
    uni.showToast({ title: '请输入队伍名称', icon: 'none' })
    return false
  }
  if (team.members.length < 2) {
    uni.showToast({ title: '每支队伍至少需要 2 名成员', icon: 'none' })
    return false
  }
  if (isRelayTemplate.value && team.members.length < form.relayMemberCount) {
    uni.showToast({ title: '接力赛每队报名不能少于 ' + form.relayMemberCount + ' 人', icon: 'none' })
    return false
  }
  if (team.members.filter((member) => member.captain).length !== 1) {
    uni.showToast({ title: '请指定 1 名队长', icon: 'none' })
    return false
  }
  return true
}

function saveTeam() {
  const team = normalizeTeamDraft()
  if (!validateTeam(team)) return
  if (editingIndex.value >= 0) form.teams.splice(editingIndex.value, 1, team)
  else form.teams.push(team)
  closeEditor()
}

function removeTeam(index) {
  form.teams.splice(index, 1)
}

function captainName(team) {
  return team.members.find((item) => item.captain)?.name || '-'
}

function resetRule(rule) {
  rule.bestOf = 3
  rule.gamesToWin = 2
  rule.pointsToWin = 21
  rule.enableDeuce = true
  rule.capPoint = 30
}

function resetForm() {
  form.name = ''
  form.location = ''
  form.players = ''
  form.participantType = 0
  form.teamMatchTemplate = 1
  form.relayMemberCount = 6
  form.teams = []
  form.tournamentType = 0
  form.knockoutSlots = 8
  form.qualifiersPerGroup = 2
  form.roundRobinRounds = 1
  form.rankingTemplate = 'BWF_BADMINTON'
  form.rankingBaseTemplate = 'BADMINTON_COMMON_1'
  form.rankingPriorities = []
  form.thirdPlaceEnabled = false
  allMatchRules().forEach(resetRule)
  form.refereePassword = ''
}

function showInvalidThirdPlaceModal(count, unit) {
  uni.showModal({
    title: '无法开启季军赛',
    content: `季军赛需要至少 4 个淘汰阶段参赛单位，当前只有 ${count} ${unit}。`,
    showCancel: false,
  })
}

function badmintonRulePayload(rule) {
  return {
    bestOf: isRelayTemplate.value ? 1 : rule.bestOf,
    gamesToWin: isRelayTemplate.value ? 1 : rule.gamesToWin,
    pointsToWin: rule.pointsToWin,
    enableDeuce: isRelayTemplate.value ? false : rule.enableDeuce,
    capPoint: isRelayTemplate.value ? form.relayMemberCount : rule.capPoint,
  }
}

function buildBadmintonRoundRules() {
  const knockoutRounds = Math.max(1, Math.round(Math.log2(Number(form.knockoutSlots || 2))))
  return [
    { stageType: 0, roundNum: 0, rule: badmintonRulePayload(form.groupRule) },
    ...Array.from({ length: knockoutRounds }, (_, index) => ({
      stageType: 1,
      roundNum: index + 1,
      rule: badmintonRulePayload(form.knockoutRule),
    })),
  ]
}

async function createTournament() {
  if (submitting.value) return
  if (!form.name.trim()) {
    uni.showToast({ title: '请输入比赛名称', icon: 'none' })
    return
  }

  let players = []
  let teams = []
  if (isIndividual.value) {
    if (!form.players.trim()) {
      uni.showToast({ title: '请输入参赛选手', icon: 'none' })
      return
    }
    players = parsePlayers(form.players)
    if (players.length < 2) {
      uni.showToast({ title: '至少需要2名选手', icon: 'none' })
      return
    }
  } else {
    teams = form.teams
    if (teams.length < 2) {
      uni.showToast({ title: '至少需要 2 支队伍', icon: 'none' })
      return
    }
    if (isRelayTemplate.value && teams.some((team) => team.members.length < form.relayMemberCount)) {
      uni.showToast({ title: '接力赛每队报名不能少于 ' + form.relayMemberCount + ' 人', icon: 'none' })
      return
    }
  }

  const count = isIndividual.value ? players.length : teams.length
  if (form.tournamentType === 1 && form.knockoutSlots > count) {
    uni.showToast({ title: '淘汰名额不能超过参赛数量', icon: 'none' })
    return
  }
  if (form.tournamentType === 2 && count < 2) {
    uni.showToast({ title: '循环赛至少需要 2 个参赛单位', icon: 'none' })
    return
  }
  if (form.thirdPlaceEnabled) {
    const thirdPlaceCount = form.tournamentType === 1 ? form.knockoutSlots : count
    if (thirdPlaceCount < 4) {
      showInvalidThirdPlaceModal(thirdPlaceCount, participantUnit.value)
      return
    }
  }

  submitting.value = true
  try {
    await requireProfile()
    const baseRule = form.tournamentType === 1
      ? badmintonRulePayload(form.groupRule)
      : badmintonRulePayload(form.rule)
    const knockoutRule = form.tournamentType === 1
      ? badmintonRulePayload(form.knockoutRule)
      : badmintonRulePayload(form.rule)
    const payload = {
      sportType: 0,
      participantType: form.participantType,
      teamMatchTemplate: isIndividual.value ? 0 : form.teamMatchTemplate,
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
      roundRules: form.tournamentType === 1 ? buildBadmintonRoundRules() : undefined,
      ...(isIndividual.value
        ? { players }
        : {
            teams: teams.map((team) => ({
              name: team.name,
              members: team.members.map((member) => ({ name: member.name, captain: member.captain })),
            })),
          }),
      rule: baseRule,
      thirdPlaceRule: form.thirdPlaceEnabled ? knockoutRule : undefined,
      refereePassword: form.refereePassword.trim() || undefined,
    }
    const res = await request('/api/v1/tournaments', {
      method: 'POST',
      data: payload,
    })
    uni.showToast({ title: '创建成功', icon: 'success' })
    resetForm()
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

.header {
  display: flex;
  align-items: center;
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
.textarea {
  width: 100%;
  box-sizing: border-box;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
  border-radius: 16rpx;
  font-size: 26rpx;
}

.input {
  height: 84rpx;
  padding: 0 22rpx;
  margin-bottom: 16rpx;
}

.textarea {
  min-height: 280rpx;
  padding: 22rpx;
  margin-top: 22rpx;
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
  margin-bottom: 16rpx;
}

.ranking-section {
  margin-top: 18rpx;
}

.compact-title {
  font-size: 26rpx;
}

.section-head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.section-head .section-title {
  margin-bottom: 0;
}

.segment {
  display: flex;
  border: 1rpx solid rgba(255, 140, 0, 0.36);
  border-radius: 14rpx;
  overflow: hidden;
}

.segment.compact {
  width: 240rpx;
}

.segment-item {
  flex: 1;
  min-height: 52rpx;
  line-height: 52rpx;
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
  margin-top: 16rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 18rpx;
}

.rule-label,
.rule-subtitle,
.hint,
.section-meta {
  color: rgba(255, 255, 255, 0.64);
  font-size: 24rpx;
}

.rule-subsection {
  margin-top: 18rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid rgba(255, 140, 0, 0.18);
}

.rule-subtitle {
  display: block;
  color: #ffffff;
  font-weight: 700;
}

.hint {
  display: block;
  margin-top: 16rpx;
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
  border-radius: 10rpx;
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.mini-btn {
  height: 56rpx;
  line-height: 56rpx;
  padding: 0 20rpx;
  border-radius: 12rpx;
  border: 1rpx solid rgba(255, 140, 0, 0.42);
  background: transparent;
  color: #ffb347;
  font-size: 24rpx;
}

.template-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
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

.template-items {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
  margin-top: 14rpx;
}

.template-item {
  padding: 8rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 140, 0, 0.18);
  color: #ffb347;
  font-size: 22rpx;
}

.team-list {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 18rpx;
}

.team-card {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16rpx;
  padding: 20rpx 22rpx;
  border-radius: 18rpx;
  background: rgba(255, 255, 255, 0.05);
}

.team-main {
  flex: 1;
  min-width: 0;
}

.team-name {
  display: block;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 700;
}

.team-desc {
  display: block;
  margin-top: 6rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 23rpx;
}

.team-actions {
  display: flex;
  gap: 16rpx;
  flex-shrink: 0;
}

.team-action {
  color: #ffb347;
  font-size: 24rpx;
}

.team-action.danger {
  color: #ff7a7a;
}

.empty-card {
  margin-top: 18rpx;
  padding: 24rpx;
  border-radius: 16rpx;
  background: rgba(255, 255, 255, 0.05);
}

.empty-title,
.empty-desc {
  display: block;
}

.empty-title {
  color: #ffffff;
  font-size: 26rpx;
  font-weight: 700;
}

.empty-desc {
  margin-top: 8rpx;
  color: rgba(255, 255, 255, 0.62);
  font-size: 23rpx;
}

.submit-btn {
  margin-top: 24rpx;
  height: 90rpx;
  line-height: 90rpx;
  border-radius: 18rpx;
  border: none;
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-size: 30rpx;
  font-weight: 800;
}

.submit-btn::after,
.mini-btn::after,
.ghost-btn::after,
.primary-btn::after,
.sample-btn::after {
  border: none;
}

.editor-mask {
  position: fixed;
  inset: 0;
  z-index: 30;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  background: rgba(0, 0, 0, 0.58);
}

.editor-panel {
  width: 100%;
  max-height: 90vh;
  padding: 24rpx 24rpx 32rpx;
  box-sizing: border-box;
  border-top-left-radius: 30rpx;
  border-top-right-radius: 30rpx;
  border-top: 1rpx solid rgba(255, 140, 0, 0.35);
  background: linear-gradient(180deg, #23384d 0%, #172636 100%);
}

.editor-header,
.editor-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.editor-footer {
  margin-top: 18rpx;
}

.editor-title {
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 800;
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
  grid-template-columns: 48rpx 1fr 110rpx;
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

.footer-bar {
  position: fixed;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  display: flex;
  align-items: center;
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

.ghost-btn {
  background: rgba(255, 255, 255, 0.08);
  color: #ffffff;
}

.primary-btn {
  background: linear-gradient(135deg, #ff9b1a, #ff6d00);
  color: #13202d;
  font-weight: 800;
}
</style>
