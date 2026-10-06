<template>
  <div v-if="open" class="drawer-overlay" @click.self="emit('close')">
    <aside class="round-rule-drawer">
      <div class="drawer-head">
        <div>
          <div class="drawer-title-row">
            <h2>分段规则设计</h2>
            <p>{{ hint }}</p>
          </div>
        </div>
        <button class="ghost-action small" type="button" @click="emit('close')">关闭</button>
      </div>

      <div class="scope-bank">
        <h3>比赛阶段</h3>
        <div class="scope-chip-list">
          <span
            v-for="scope in scopes"
            :key="scope.key"
            class="scope-chip"
            :class="{ assigned: !!assignedSegmentName(scope.key) }"
          >
            {{ scope.label }}
            <em>{{ assignedSegmentName(scope.key) || '未分配' }}</em>
          </span>
        </div>
      </div>

      <p v-if="drawerError" class="drawer-error">{{ drawerError }}</p>

      <div class="segment-list">
        <section v-for="segment in segments" :key="segment.id" class="segment-card">
          <div class="segment-card-head">
            <input v-model.trim="segment.name" placeholder="赛段名称" />
            <button
              class="tiny-text-action danger"
              type="button"
              :disabled="segments.length <= 1"
              @click="requestRemoveSegment(segment)"
            >
              删除赛段
            </button>
          </div>

          <div class="scope-toggle-grid">
            <button
              v-for="scope in scopes"
              :key="scope.key"
              type="button"
              class="scope-toggle"
              :class="{
                active: segment.scopeKeys.includes(scope.key),
                unavailable: isScopeAssignedToOtherSegment(segment, scope.key),
              }"
              :disabled="isScopeAssignedToOtherSegment(segment, scope.key)"
              @click="toggleSegmentScope(segment, scope)"
            >
              {{ scope.label }}
            </button>
          </div>

          <div class="field-grid four round-rule-grid">
            <label>
              <span>局数</span>
              <select v-model.number="segment.rule.bestOf" @change="setBestOf(segment.rule, segment.rule.bestOf)">
                <option :value="1" v-if="allowBestOfOne">一局</option>
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
            <label v-if="showDecidingPoints">
              <span>决胜局胜分</span>
              <input v-model.number="segment.rule.decidingPointsToWin" type="number" min="1" />
            </label>
          </div>
        </section>
      </div>

      <div class="drawer-actions">
        <button
          class="ghost-action small round-rule-add-action"
          :class="{ 'at-limit': limitReached }"
          type="button"
          @click="addSegment"
        >
          新增赛段
        </button>
        <div class="drawer-actions-right">
          <button class="ghost-action" type="button" @click="emit('close')">取消</button>
          <button class="secondary-action" type="button" @click="confirmSegments">确定</button>
        </div>
      </div>
    </aside>
  </div>

  <div v-if="pendingDeleteSegment" class="modal-overlay segment-delete-modal" @click.self="pendingDeleteSegmentId = null">
    <section class="message-modal">
      <h2>删除赛段</h2>
      <p>确定删除「{{ pendingDeleteSegment.name || '未命名赛段' }}」吗？</p>
      <div class="message-modal-actions">
        <button class="ghost-action" type="button" @click="pendingDeleteSegmentId = null">取消</button>
        <button class="secondary-action" type="button" @click="confirmRemoveSegment">确定</button>
      </div>
    </section>
  </div>

  <div v-if="limitMessage" class="modal-overlay segment-delete-modal" @click.self="limitMessage = ''">
    <section class="message-modal">
      <h2>赛段已到上限</h2>
      <p>{{ limitMessage }}</p>
      <button class="secondary-action" type="button" @click="limitMessage = ''">知道了</button>
    </section>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'
import { nextRoundRuleSegmentId, validateSegmentCoverage } from '../utils/roundRules'

const props = defineProps({
  // 是否渲染抽屉（父级 v-if/v-show 亦可，这里收敛为 prop 便于复用）
  open: { type: Boolean, default: false },
  // 作用域：[{ stageType, roundNum, key, label }]，由父级按赛制推导
  scopes: { type: Array, default: () => [] },
  // 赛段：[{ id, name, scopeKeys[], rule{bestOf,gamesToWin,pointsToWin,decidingPointsToWin,enableDeuce,capPoint} }]
  // 赛段对象由父级持有，本组件就地编辑；结构性增删通过 update:segments 通知父级替换数组
  segments: { type: Array, default: () => [] },
  // 抽屉头部副标题（当前赛制描述）
  hint: { type: String, default: '' },
  // 羽毛球允许"一局"；排球隐藏
  allowBestOfOne: { type: Boolean, default: true },
  // 排球显示"决胜局胜分"
  showDecidingPoints: { type: Boolean, default: false },
  // 新增赛段的默认规则工厂（父级按当前运动/组别基础规则提供）
  createDefaultRule: { type: Function, required: true },
})

const emit = defineEmits(['update:segments', 'confirm', 'close'])

const drawerError = ref('')
const limitMessage = ref('')
const pendingDeleteSegmentId = ref(null)

const limitReached = computed(() => props.scopes.length > 0 && props.segments.length >= props.scopes.length)
const pendingDeleteSegment = computed(() => props.segments.find((segment) => segment.id === pendingDeleteSegmentId.value) || null)

// 打开时重置内部状态：旧实现 openRoundRuleDrawer 每次打开前清错误、close 清待删/上限，
// 抽为组件后状态跨"关闭→重开"持久，必须显式复位（复审 P2-1）
watch(() => props.open, (open) => {
  if (!open) return
  drawerError.value = ''
  limitMessage.value = ''
  pendingDeleteSegmentId.value = null
})

function setBestOf(rule, bestOf) {
  rule.bestOf = Number(bestOf)
  rule.gamesToWin = Math.floor(rule.bestOf / 2) + 1
}

function assignedSegmentName(scopeKey) {
  const segment = props.segments.find((item) => item.scopeKeys.includes(scopeKey))
  return segment?.name || ''
}

function isScopeAssignedToOtherSegment(segment, scopeKey) {
  return props.segments.some((item) => item !== segment && item.scopeKeys.includes(scopeKey))
}

function addSegment() {
  drawerError.value = ''
  if (!props.scopes.length) {
    limitMessage.value = '当前赛制没有可分配的比赛阶段。'
    return
  }
  if (limitReached.value) {
    limitMessage.value = `当前赛制最多划分 ${props.scopes.length} 个赛段。`
    return
  }
  const finalScope = props.scopes[props.scopes.length - 1]
  const finalScopeKeys = props.segments.length > 0 && finalScope ? [finalScope.key] : []
  if (finalScopeKeys.length) {
    for (const segment of props.segments) {
      segment.scopeKeys = segment.scopeKeys.filter((key) => !finalScopeKeys.includes(key))
    }
  }
  emit('update:segments', [
    ...props.segments,
    {
      id: nextRoundRuleSegmentId(),
      name: `赛段${props.segments.length + 1}`,
      scopeKeys: finalScopeKeys,
      rule: props.createDefaultRule(),
    },
  ])
}

function requestRemoveSegment(segment) {
  if (props.segments.length <= 1) return
  pendingDeleteSegmentId.value = segment.id
}

function confirmRemoveSegment() {
  const segment = pendingDeleteSegment.value
  pendingDeleteSegmentId.value = null
  if (!segment || props.segments.length <= 1) return
  drawerError.value = ''
  emit('update:segments', props.segments.filter((item) => item.id !== segment.id))
}

function toggleSegmentScope(segment, scope) {
  drawerError.value = ''
  if (segment.scopeKeys.includes(scope.key)) {
    segment.scopeKeys = segment.scopeKeys.filter((key) => key !== scope.key)
    return
  }
  for (const item of props.segments) {
    item.scopeKeys = item.scopeKeys.filter((key) => key !== scope.key)
  }
  segment.scopeKeys.push(scope.key)
  const scopeOrder = new Map(props.scopes.map((item, index) => [item.key, index]))
  segment.scopeKeys.sort((left, right) => scopeOrder.get(left) - scopeOrder.get(right))
}

function confirmSegments() {
  const error = validateSegmentCoverage(props.segments, props.scopes)
  if (error) {
    drawerError.value = error
    return
  }
  emit('confirm')
}
</script>

