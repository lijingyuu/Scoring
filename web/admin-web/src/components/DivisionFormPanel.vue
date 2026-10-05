<template>
  <div class="division-form-panel">
    <div class="division-card-head">
      <span class="division-index">组别 {{ index }}</span>
      <input v-model.trim="draft.name" class="division-name-input" maxlength="64" placeholder="例如 男单组" />
      <button v-if="canRemove" class="text-action danger" type="button" @click="emit('remove')">删除组别</button>
    </div>

    <div class="field-grid three division-format-grid">
      <label>
        <span>赛制</span>
        <select v-model.number="draft.tournamentType">
          <option :value="0">淘汰赛</option>
          <option :value="1">小组赛 + 淘汰赛</option>
          <option :value="2">循环赛</option>
        </select>
      </label>
      <label v-if="draft.tournamentType === 0">
        <span>淘汰轮数</span>
        <input v-model.number="draft.knockoutRounds" type="number" min="1" max="10" />
        <small class="muted">该轮数需要 {{ roundsHint }} 名选手</small>
      </label>
      <template v-if="draft.tournamentType === 1">
        <label>
          <span>淘汰名额</span>
          <select v-model.number="draft.knockoutSlots">
            <option :value="2">2</option>
            <option :value="4">4</option>
            <option :value="8">8</option>
            <option :value="16">16</option>
            <option :value="32">32</option>
          </select>
        </label>
        <label>
          <span>每组出线</span>
          <select v-model.number="draft.qualifiersPerGroup">
            <option :value="1">1</option>
            <option :value="2">2</option>
          </select>
        </label>
      </template>
      <label v-if="draft.tournamentType === 2">
        <span>轮次</span>
        <select v-model.number="draft.roundRobinRounds">
          <option :value="1">单循环</option>
          <option :value="2">双循环</option>
        </select>
      </label>
      <label v-if="draft.tournamentType !== 2" class="inline-toggle division-third-place">
        <input v-model="draft.thirdPlaceEnabled" type="checkbox" />
        <span>季军赛</span>
      </label>
      <label v-if="draft.tournamentType === 0" class="manual-draw-toggle-field">
        <span>手写签表</span>
        <span class="inline-toggle">
          <input
            type="checkbox"
            :checked="draft.manualDrawEnabled"
            :disabled="!canEnableManualDraw"
            @change="toggleManualDraw"
          />
          <span>手动安排签位（默认自动抽签）</span>
        </span>
        <small v-if="manualDrawUnavailableReason" class="muted">{{ manualDrawUnavailableReason }}</small>
      </label>
      <label v-if="draft.tournamentType === 1" class="manual-groups-toggle-field">
        <span>手写分组</span>
        <span class="inline-toggle">
          <input
            type="checkbox"
            :checked="draft.manualGroupsEnabled"
            :disabled="!canEnableManualGroups"
            @change="toggleManualGroups"
          />
          <span>手动安排小组名单（默认自动分组）</span>
        </span>
        <small v-if="manualGroupsUnavailableReason" class="muted">{{ manualGroupsUnavailableReason }}</small>
      </label>
    </div>

    <div v-if="draft.tournamentType !== 0" class="ranking-template-panel division-ranking-panel">
      <label>
        <span>小组赛排名规则</span>
        <select v-model="draft.rankingTemplate">
          <option v-for="option in rankingOptions" :key="option.value" :value="option.value">
            {{ option.name }}
          </option>
        </select>
      </label>
      <p>对小组赛阶段的排位生效，纯淘汰组别忽略</p>
    </div>

    <div class="field-grid four division-rule-grid">
      <label>
        <span>总局数</span>
        <select v-model.number="draft.rule.bestOf" @change="setBestOf(draft.rule, draft.rule.bestOf)">
          <option :value="1">一局</option>
          <option :value="3">三局两胜</option>
          <option :value="5">五局三胜</option>
        </select>
      </label>
      <label>
        <span>胜局</span>
        <input :value="Math.floor(Number(draft.rule.bestOf) / 2) + 1" type="number" readonly />
      </label>
      <label>
        <span>每局分</span>
        <input v-model.number="draft.rule.pointsToWin" type="number" min="1" />
      </label>
      <label>
        <span>追分</span>
        <select v-model="draft.rule.enableDeuce">
          <option :value="true">开启</option>
          <option :value="false">关闭</option>
        </select>
      </label>
      <label>
        <span>封顶</span>
        <input v-model.number="draft.rule.capPoint" type="number" min="1" @change="clampCapPoint" />
      </label>
    </div>

    <div v-if="draft.tournamentType === 1" class="division-knockout-rule">
      <p class="division-knockout-rule-title">淘汰赛规则</p>
      <div class="field-grid four division-rule-grid">
        <label>
          <span>总局数</span>
          <select v-model.number="draft.knockoutRule.bestOf">
            <option :value="1">一局</option>
            <option :value="3">三局两胜</option>
            <option :value="5">五局三胜</option>
          </select>
        </label>
        <label>
          <span>胜局</span>
          <input :value="Math.floor(Number(draft.knockoutRule.bestOf) / 2) + 1" type="number" readonly />
        </label>
        <label>
          <span>每局分</span>
          <input v-model.number="draft.knockoutRule.pointsToWin" type="number" min="1" />
        </label>
        <label>
          <span>追分</span>
          <select v-model="draft.knockoutRule.enableDeuce">
            <option :value="true">开启</option>
            <option :value="false">关闭</option>
          </select>
        </label>
        <label>
          <span>封顶</span>
          <input v-model.number="draft.knockoutRule.capPoint" type="number" min="1" />
        </label>
      </div>
    </div>

    <label class="division-players-label">
      <span>选手名单</span>
      <textarea
        v-model="draft.playersText"
        placeholder="每行一名选手，可在前面加种子序号，例如：1 张三"
      ></textarea>
      <small class="muted">已识别 {{ playerCount }} 名选手</small>
    </label>

    <section
      v-if="draft.manualDrawEnabled"
      ref="manualDrawPanel"
      class="division-manual-section"
      :class="{ 'manual-panel-flash': manualDrawPanelFlash }"
    >
      <div class="division-manual-head">
        <h3>手写签表</h3>
        <span class="muted">容量 {{ manualCapacity }} 个签位 · {{ playerCount }} 个参赛单位</span>
      </div>
      <p class="muted">
        容量由淘汰轮数决定（2 的轮数幂）。点击签位选中后，在下方名单面板点名单项填入（自动跳到下一空位），「轮空位」填空签；也可点「剩余随机填入」随机补齐。同一场比赛的两个签位不能都是轮空。
      </p>
      <p v-if="!manualDrawValidation.ok" class="error-text">{{ manualDrawValidation.message }}</p>
      <DrawSlotEditor v-model="draft.manualSlots" :roster="roster" />
    </section>

    <section
      v-if="draft.manualGroupsEnabled"
      ref="manualGroupsPanel"
      class="division-manual-section"
      :class="{ 'manual-panel-flash': manualGroupsPanelFlash }"
    >
      <div class="division-manual-head">
        <h3>手写分组</h3>
        <span class="muted">
          {{ manualGroupCount }} 个小组 · {{ playerCount }} 个参赛单位 · 每组至少 {{ manualMinPerGroup }} 人
        </span>
      </div>
      <p class="muted">
        小组数量固定为「淘汰名额 ÷ 每组出线」（{{ draft.knockoutSlots }} ÷ {{ draft.qualifiersPerGroup }}），每组人数不少于 {{ manualMinPerGroup }} 人，组间允许不均。点组标题选中目标组后，在下方名单面板点名单项加入该组末尾（组内顺序即组内座次）；组内成员点 × 移出，也可点「剩余随机分配」随机补人。
      </p>
      <p v-if="!manualGroupCount" class="error-text">淘汰名额需能被每组出线整除，请先调整上方「淘汰名额 / 每组出线」。</p>
      <p v-else-if="!manualGroupsValidation.ok" class="error-text">{{ manualGroupsValidation.message }}</p>
      <GroupAssignmentEditor
        v-if="manualGroupCount"
        v-model="draft.manualGroups"
        :roster="roster"
        :group-count="manualGroupCount"
        :min-per-group="manualMinPerGroup"
      />
    </section>

    <div v-if="pendingDisableManualDraw" class="modal-overlay" @click.self="pendingDisableManualDraw = false">
      <section class="message-modal">
        <h2>关闭手写签表</h2>
        <p>已安排的签位会保留，重新开启后可继续编辑；确定关闭吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDisableManualDraw = false">取消</button>
          <button class="secondary-action" type="button" @click="confirmDisableManualDraw">确定关闭</button>
        </div>
      </section>
    </div>

    <div v-if="pendingDisableManualGroups" class="modal-overlay" @click.self="pendingDisableManualGroups = false">
      <section class="message-modal">
        <h2>关闭手写分组</h2>
        <p>已安排的小组名单会保留，重新开启后可继续编辑；确定关闭吗？</p>
        <div class="message-modal-actions">
          <button class="ghost-action" type="button" @click="pendingDisableManualGroups = false">取消</button>
          <button class="secondary-action" type="button" @click="confirmDisableManualGroups">确定关闭</button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, ref, watch } from 'vue'
import DrawSlotEditor from './DrawSlotEditor.vue'
import GroupAssignmentEditor from './GroupAssignmentEditor.vue'
import { MAX_DRAW_SLOTS, validateDrawSlots } from '../utils/drawSlots'
import {
  divisionDrawCapacity,
  divisionDrawUnavailableReason,
  divisionGroupCount,
  divisionGroupsUnavailableReason,
  divisionMinPerGroup,
  divisionRosterItems,
  divisionRosterKeys,
  syncDivisionDrawSlots,
  syncDivisionGroups,
  syncRosterIdentity,
  validateDivisionGroups,
} from '../utils/divisionForm'

const props = defineProps({
  // 组别草稿对象（父级 divisionDrafts 的元素），组件内直接改其属性
  draft: { type: Object, required: true },
  // 展示用序号（1-based）
  index: { type: Number, default: 1 },
  canRemove: { type: Boolean, default: false },
})

const emit = defineEmits(['remove', 'notify'])

// 组别级排名配置（仅小组赛+淘汰/循环赛组别生效）。多组别仅羽毛球个人赛，
// 只提供个人赛排名模板（团体模板 BADMINTON_TEAM_COMMON_1 不适用，审查 P2-6）
const rankingOptions = [
  { value: 'BWF_BADMINTON', name: 'BWF标准规则' },
  { value: 'BADMINTON_COMMON_1', name: '胜场数-净胜局-得失分比' },
]

const playerCount = computed(() => divisionRosterItems(props.draft).length)
/** 名单项：key = 稳定身份 key（内容=身份，见 assignStableKeys）；提交时由 payload 映射回名单下标 */
const roster = computed(() => divisionRosterItems(props.draft))
const rosterKeysJoin = computed(() => roster.value.map((item) => item.key).join(','))

const roundsHint = computed(() => {
  const n = Number(props.draft.knockoutRounds)
  if (!Number.isInteger(n) || n < 1 || n > 10) return '-'
  const minExclusive = n === 1 ? 1 : 2 ** (n - 1)
  return `${minExclusive + 1}~${2 ** n}`
})

// ——— 手写签表（type0 组别）———
const manualCapacity = computed(() => divisionDrawCapacity(props.draft))
const manualDrawUnavailableReason = computed(() => divisionDrawUnavailableReason(props.draft, playerCount.value))
const canEnableManualDraw = computed(() => !manualDrawUnavailableReason.value)
const manualDrawValidation = computed(() => validateDrawSlots(props.draft.manualSlots || [], roster.value.map((item) => item.key)))
const manualPlacedCount = computed(() => (props.draft.manualSlots || [])
  .filter((slot) => slot !== null && slot !== undefined && slot !== '').length)

const manualDrawPanel = ref(null)
const manualDrawPanelFlash = ref(false)
const pendingDisableManualDraw = ref(false)

function toggleManualDraw() {
  if (props.draft.manualDrawEnabled) {
    // 已有安排时先确认，防止误触清空签位
    if (manualPlacedCount.value > 0) {
      pendingDisableManualDraw.value = true
      return
    }
    props.draft.manualDrawEnabled = false
    return
  }
  if (!canEnableManualDraw.value) {
    emit('notify', manualDrawUnavailableReason.value)
    return
  }
  props.draft.manualDrawEnabled = true
  syncDivisionDrawSlots(props.draft, manualCapacity.value, divisionRosterKeys(props.draft))
  scrollToManualDrawPanel()
}

function confirmDisableManualDraw() {
  pendingDisableManualDraw.value = false
  props.draft.manualDrawEnabled = false
}

async function scrollToManualDrawPanel() {
  await nextTick()
  const el = manualDrawPanel.value
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  manualDrawPanelFlash.value = false
  requestAnimationFrame(() => {
    manualDrawPanelFlash.value = true
    setTimeout(() => {
      manualDrawPanelFlash.value = false
    }, 1800)
  })
}

// ——— 手写分组（type1 组别）———
const manualGroupCount = computed(() => divisionGroupCount(props.draft))
const manualMinPerGroup = computed(() => divisionMinPerGroup(props.draft))
const manualGroupsUnavailableReason = computed(() => divisionGroupsUnavailableReason(props.draft, playerCount.value))
const canEnableManualGroups = computed(() => !manualGroupsUnavailableReason.value)
const manualGroupsValidation = computed(() => validateDivisionGroups(
  props.draft.manualGroups,
  roster.value.map((item) => item.key),
  manualGroupCount.value,
  manualMinPerGroup.value,
))
const manualGroupsPlacedCount = computed(() => (props.draft.manualGroups || [])
  .reduce((total, group) => total + (Array.isArray(group) ? group.length : 0), 0))

const manualGroupsPanel = ref(null)
const manualGroupsPanelFlash = ref(false)
const pendingDisableManualGroups = ref(false)

function toggleManualGroups() {
  if (props.draft.manualGroupsEnabled) {
    // 已有安排时先确认，防止误触清空分组
    if (manualGroupsPlacedCount.value > 0) {
      pendingDisableManualGroups.value = true
      return
    }
    props.draft.manualGroupsEnabled = false
    return
  }
  if (!canEnableManualGroups.value) {
    emit('notify', manualGroupsUnavailableReason.value)
    return
  }
  props.draft.manualGroupsEnabled = true
  syncDivisionGroups(props.draft, manualGroupCount.value, divisionRosterKeys(props.draft))
  scrollToManualGroupsPanel()
}

function confirmDisableManualGroups() {
  pendingDisableManualGroups.value = false
  props.draft.manualGroupsEnabled = false
}

async function scrollToManualGroupsPanel() {
  await nextTick()
  const el = manualGroupsPanel.value
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'start' })
  manualGroupsPanelFlash.value = false
  requestAnimationFrame(() => {
    manualGroupsPanelFlash.value = true
    setTimeout(() => {
      manualGroupsPanelFlash.value = false
    }, 1800)
  })
}

function setBestOf(rule, bestOf) {
  rule.bestOf = Number(bestOf)
  rule.gamesToWin = Math.floor(rule.bestOf / 2) + 1
}

function clampCapPoint() {
  const rule = props.draft.rule
  const pointsToWin = Number(rule.pointsToWin) || 1
  const cap = Number(rule.capPoint)
  if (!Number.isFinite(cap)) {
    rule.capPoint = Math.min(99, pointsToWin + 1)
    return
  }
  rule.capPoint = Math.max(pointsToWin + 1, Math.min(99, Math.round(cap)))
}

// 名单身份同步：playersText 每次变化先重算稳定 key（内容=身份，见 assignStableKeys），
// 签位/分组同步依赖它的产物——声明顺序必须先于下面两个同步 watcher
watch(
  () => props.draft.playersText,
  () => syncRosterIdentity(props.draft),
)

// 赛制切换后清理不再适用的手写状态；循环赛没有淘汰阶段，季军赛自动关闭
watch(() => props.draft.tournamentType, (type) => {
  const normalized = Number(type)
  if (normalized === 2) props.draft.thirdPlaceEnabled = false
  if (normalized !== 0) {
    props.draft.manualDrawEnabled = false
    props.draft.manualSlots = []
  }
  if (normalized !== 1) {
    props.draft.manualGroupsEnabled = false
    props.draft.manualGroups = []
  }
})

// 手写签表：名单/轮数变化时把签位数组对齐到框架容量；轮数超出 64 签位上限时自动关闭
watch(
  () => [props.draft.manualDrawEnabled, rosterKeysJoin.value, manualCapacity.value],
  () => {
    if (!props.draft.manualDrawEnabled) return
    if (!manualCapacity.value || manualCapacity.value > MAX_DRAW_SLOTS) {
      props.draft.manualDrawEnabled = false
      props.draft.manualSlots = []
      return
    }
    syncDivisionDrawSlots(props.draft, manualCapacity.value, divisionRosterKeys(props.draft))
  },
)

// 手写分组：名单/组数变化时对齐分组数组（组数失效时清空，恢复后重新安排）
watch(
  () => [props.draft.manualGroupsEnabled, rosterKeysJoin.value, manualGroupCount.value],
  () => {
    if (!props.draft.manualGroupsEnabled) return
    syncDivisionGroups(props.draft, manualGroupCount.value, divisionRosterKeys(props.draft))
  },
)
</script>

<style scoped>
.division-card-head {
  display: flex;
  align-items: center;
  gap: 10px;
}
.division-card-head .division-index {
  font-weight: 600;
  white-space: nowrap;
}
.division-card-head .division-name-input {
  flex: 1;
}
.division-rule-grid,
.division-players-label {
  margin-top: 2px;
}
.division-players-label {
  display: grid;
  gap: 6px;
}
.division-players-label textarea {
  min-height: 72px;
}
.division-third-place,
.manual-draw-toggle-field,
.manual-groups-toggle-field {
  align-self: end;
}
.manual-draw-toggle-field,
.manual-groups-toggle-field {
  display: grid;
  gap: 4px;
}
.division-ranking-panel {
  margin-top: 2px;
}
.division-ranking-panel p {
  font-size: 12px;
}
.division-knockout-rule {
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px dashed rgba(var(--slate-rgb), 0.12);
}
.division-knockout-rule-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 600;
  color: rgba(var(--slate-rgb), 0.72);
}
.division-manual-section {
  margin-top: 12px;
  padding-top: 12px;
  border-top: 1px dashed rgba(var(--slate-rgb), 0.12);
  display: grid;
  gap: 8px;
}
.division-manual-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
}
.division-manual-head h3 {
  margin: 0;
  font-size: 14px;
}
.division-manual-section > p.muted {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
}
.manual-panel-flash {
  animation: manual-panel-flash 1.8s ease;
}
@keyframes manual-panel-flash {
  0%, 55% {
    box-shadow: 0 0 0 3px rgba(var(--focus-rgb), 0.45);
  }
  100% {
    box-shadow: none;
  }
}
</style>
