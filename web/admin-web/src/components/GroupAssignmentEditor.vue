<template>
  <div class="group-assignment-editor">
    <div class="group-assignment-toolbar">
      <span class="muted">
        共 {{ groupCount }} 个小组 · 已分组 {{ placedCount }}/{{ roster.length }} · 每组至少 {{ minPerGroup }} 人
      </span>
      <span class="group-assignment-toolbar-actions">
        <button
          v-if="!disabled && placedCount > 0"
          class="ghost-action small"
          :class="{ 'is-armed': clearArmed }"
          type="button"
          @click="onClearClick"
        >
          {{ clearArmed ? '再点一次确认清空' : '清空分组' }}
        </button>
        <button v-if="!disabled && remainingCount > 0" class="ghost-action small" type="button" @click="fillRandom">
          剩余随机分配
        </button>
      </span>
    </div>

    <p v-if="suggestionText" class="group-suggestion">
      参考分配（{{ roster.length }} 人 ÷ {{ groupCount }} 组）：{{ suggestionText }}，「剩余随机分配」会按此分布补齐
    </p>

    <div class="group-assignment-grid">
      <div
        v-for="(group, groupIndex) in groups"
        :key="groupIndex"
        class="group-column"
        :class="{ 'is-active': !disabled && activeGroup === groupIndex, 'is-short': group.length < minPerGroup }"
        @click="selectGroup(groupIndex)"
      >
        <div class="group-column-head">
          <span class="group-column-title">第 {{ groupIndex + 1 }} 组</span>
          <span class="group-column-count" :class="{ 'is-short': group.length < minPerGroup }">
            {{ group.length }}/{{ minPerGroup }} 人
          </span>
        </div>
        <p v-if="group.length < minPerGroup" class="group-column-short-hint">
          还差 {{ minPerGroup - group.length }} 人
        </p>
        <div class="group-member-list">
          <p v-if="!group.length" class="group-member-empty muted">点本组标题选中后，从下方名单加入</p>
          <div v-for="(key, memberIndex) in group" :key="`${groupIndex}-${key}-${memberIndex}`" class="group-member">
            <span class="group-member-name" :title="labelOf(key)">{{ labelOf(key) }}</span>
            <button
              v-if="!disabled"
              class="group-member-remove"
              type="button"
              aria-label="移出本组"
              @click.stop="removeMember(groupIndex, memberIndex)"
            ></button>
          </div>
        </div>
      </div>
    </div>

    <p v-if="!disabled && !roster.length" class="muted">请先录入参赛名单，再安排小组。</p>

    <!-- 吸底名单面板：点名单加入高亮组末尾；只列未分组者 -->
    <div v-if="!disabled && roster.length" class="group-palette" :class="{ 'is-collapsed': paletteCollapsed }">
      <div class="group-palette-head">
        <span>
          点击名单加入「第 {{ activeGroup + 1 }} 组」末尾（组内顺序即组内座次） · 未分组 {{ remainingCount }} 人
        </span>
        <span class="group-palette-actions">
          <span v-if="!paletteCollapsed" class="muted">点组标题切换目标组；组内成员点 × 移出</span>
          <button class="ghost-action small" type="button" @click="paletteCollapsed = !paletteCollapsed">
            {{ paletteCollapsed ? '展开面板 ▲' : '收起面板 ▼' }}
          </button>
        </span>
      </div>
      <div v-if="!paletteCollapsed" class="group-palette-grid">
        <p v-if="!remainingItems.length" class="muted palette-empty">所有名单项都已分组。</p>
        <button
          v-for="item in remainingItems"
          :key="item.key"
          type="button"
          class="palette-tile"
          :title="item.label"
          @click="assign(item.key)"
        >
          {{ item.label }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  // 二维数组：每个元素是一个小组的成员 key 数组，组内顺序即组内座次
  modelValue: { type: Array, default: () => [] },
  // 可选项：[{ key, label }]
  roster: { type: Array, default: () => [] },
  // 小组数量（契约：淘汰名额 / 每组出线）
  groupCount: { type: Number, default: 0 },
  // 每组人数下限（契约：至少 max(2, 每组出线)）
  minPerGroup: { type: Number, default: 2 },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue'])

/** 规范化成恰好 groupCount 个小组；key 统一字符串化（不假设是 playerId 还是下标） */
const groups = computed(() => Array.from({ length: Math.max(0, Number(props.groupCount) || 0) }, (_, index) => {
  const group = props.modelValue[index]
  return Array.isArray(group) ? group.map((key) => String(key)) : []
}))

const groupMembers = computed(() => groups.value.flat())
const placedCount = computed(() => groupMembers.value.length)
const usedKeys = computed(() => new Set(groupMembers.value))
const remainingItems = computed(() => props.roster.filter((item) => !usedKeys.value.has(String(item.key))))
const remainingCount = computed(() => remainingItems.value.length)

/**
 * 均分参考提示：总人数 ÷ 组数的地板除结果（余数组多 1 人），
 * 与「剩余随机分配」的容量口径（ceil 上限、优先补最小组）一致；
 * 均分结果达不到每组下限时隐藏（此时下方/页面校验会另行提示）。
 */
const suggestionText = computed(() => {
  const total = props.roster.length
  const count = Number(props.groupCount) || 0
  if (!total || !count) return ''
  const base = Math.floor(total / count)
  if (base < props.minPerGroup) return ''
  const extra = total % count
  if (!extra) return `每组 ${base} 人`
  return `${count - extra} 组 ${base} 人 + ${extra} 组 ${base + 1} 人`
})

function labelOf(key) {
  const item = props.roster.find((candidate) => String(candidate.key) === String(key))
  return item ? item.label : String(key)
}

// —— 目标组选中：点组标题切换，点名单追加到该组末尾 ——
const activeGroup = ref(0)

watch(() => Number(props.groupCount) || 0, (count) => {
  if (activeGroup.value >= count) activeGroup.value = 0
})

function selectGroup(groupIndex) {
  if (props.disabled) return
  activeGroup.value = groupIndex
}

function emitGroups(next) {
  emit('update:modelValue', next)
}

function assign(key) {
  if (props.disabled) return
  if (activeGroup.value < 0 || activeGroup.value >= groups.value.length) return
  const next = groups.value.map((group) => group.slice())
  next[activeGroup.value].push(String(key))
  emitGroups(next)
}

function removeMember(groupIndex, memberIndex) {
  if (props.disabled) return
  const next = groups.value.map((group) => group.slice())
  next[groupIndex].splice(memberIndex, 1)
  emitGroups(next)
}

function shuffle(items) {
  const list = items.slice()
  for (let index = list.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(Math.random() * (index + 1))
    ;[list[index], list[swap]] = [list[swap], list[index]]
  }
  return list
}

/**
 * 剩余随机分配：把未分组者依次分到还有容量余地的小组。
 * 容量上限 = ceil(名单人数 / 组数)；优先补低于下限的组，其次补人数最少的组。
 */
function fillRandom() {
  if (props.disabled) return
  const next = groups.value.map((group) => group.slice())
  const capacity = Math.max(1, Math.ceil(props.roster.length / Math.max(1, groups.value.length)))
  for (const key of shuffle(remainingItems.value.map((item) => String(item.key)))) {
    const candidates = next
      .map((group, index) => ({ index, size: group.length }))
      .filter((candidate) => candidate.size < capacity)
    if (!candidates.length) break
    const belowMin = candidates.filter((candidate) => candidate.size < props.minPerGroup)
    const pool = belowMin.length ? belowMin : candidates
    pool.sort((left, right) => left.size - right.size || Math.random() - 0.5)
    next[pool[0].index].push(key)
  }
  emitGroups(next)
}

// —— 两段式清空（先点一次进入待确认，再点一次真正清空，防误触） ——
const clearArmed = ref(false)
let clearDisarmTimer = null

function onClearClick() {
  if (props.disabled) return
  if (!clearArmed.value) {
    clearArmed.value = true
    clearTimeout(clearDisarmTimer)
    clearDisarmTimer = setTimeout(() => {
      clearArmed.value = false
    }, 3000)
    return
  }
  clearArmed.value = false
  clearTimeout(clearDisarmTimer)
  emitGroups(groups.value.map(() => []))
  activeGroup.value = 0
}

const paletteCollapsed = ref(false)
</script>

<style scoped>
.group-assignment-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.group-assignment-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.group-assignment-toolbar-actions .is-armed {
  color: var(--danger);
  border-color: rgba(var(--danger-rgb), 0.62);
}

.group-suggestion {
  margin: 0 0 10px;
  color: var(--muted);
  font-size: 12px;
  line-height: 1.5;
}

.group-assignment-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(190px, 1fr));
  gap: 10px 12px;
}

.group-column {
  display: grid;
  grid-template-rows: auto auto minmax(0, 1fr);
  gap: 6px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(var(--inset-rgb), 0.12);
  cursor: pointer;
}

.group-column.is-active {
  border-color: rgba(var(--focus-rgb), 0.85);
  box-shadow: 0 0 0 2px rgba(var(--focus-rgb), 0.4);
}

.group-column.is-short {
  border-color: rgba(var(--danger-rgb), 0.52);
}

.group-column-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.group-column-title {
  font-weight: 800;
  font-size: 13px;
}

.group-column.is-short .group-column-title {
  color: var(--danger);
}

.group-column-count {
  color: var(--muted);
  font-size: 12px;
  white-space: nowrap;
}

.group-column-count.is-short {
  color: var(--danger);
}

.group-column-short-hint {
  margin: 0;
  color: var(--danger);
  font-size: 12px;
}

.group-member-list {
  display: grid;
  align-content: start;
  gap: 4px;
  max-height: 220px;
  overflow-y: auto;
}

.group-member {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 5px 8px;
  border: 1px solid rgba(var(--tint-rgb), 0.28);
  border-radius: 6px;
  background: rgba(var(--tint-rgb), 0.08);
  font-size: 13px;
}

.group-member-name {
  min-width: 0;
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}

.group-member-remove {
  flex: none;
  width: 18px;
  height: 18px;
  padding: 0;
  border: 1px solid var(--line);
  border-radius: 50%;
  background: transparent;
  color: var(--muted);
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
}

.group-member-remove::before {
  content: '×';
}

.group-member-remove:hover {
  border-color: rgba(var(--danger-rgb), 0.62);
  color: var(--danger);
}

.group-member-empty {
  margin: 0;
  font-size: 12px;
  line-height: 1.5;
}

/* 吸底名单面板：滚动分组时始终可见；可收起成一行 */
.group-palette {
  position: sticky;
  bottom: 8px;
  z-index: 6;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(var(--bg-deep-rgb), 0.97);
  box-shadow: 0 -8px 22px rgba(0, 0, 0, 0.35);
}

.group-palette.is-collapsed {
  padding: 6px 12px;
}

.group-palette.is-collapsed .group-palette-head {
  margin-bottom: 0;
}

.group-palette-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}

.group-palette-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.group-palette-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(92px, 1fr));
  gap: 8px;
  max-height: 176px;
  overflow-y: auto;
}

.palette-empty {
  grid-column: 1 / -1;
  margin: 0;
  font-size: 13px;
}

.palette-tile {
  padding: 7px 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: rgba(var(--tint-rgb), 0.06);
  color: inherit;
  font-size: 12px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.palette-tile:hover {
  border-color: rgba(var(--focus-rgb), 0.75);
  background: rgba(var(--focus-rgb), 0.12);
}
</style>
