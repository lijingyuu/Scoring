<template>
  <div class="draw-slot-editor">
    <div class="draw-slot-toolbar">
      <span class="muted">
        共 {{ slotCount }} 个签位 · {{ matches.length }} 场比赛 · 轮空 {{ byeCount }} 个
      </span>
      <span class="draw-slot-toolbar-actions">
        <button
          v-if="!disabled && placedCount > 0"
          class="ghost-action small"
          :class="{ 'is-armed': clearArmed }"
          type="button"
          @click="onClearClick"
        >
          {{ clearArmed ? '再点一次确认清空' : '一键清空' }}
        </button>
        <button v-if="!disabled" class="ghost-action small" type="button" @click="fillRandom">剩余随机填入</button>
      </span>
    </div>

    <div class="draw-match-grid">
      <template v-for="item in gridItems" :key="item.type === 'divider' ? item.key : item.match.index">
        <div v-if="item.type === 'divider'" class="draw-grid-divider" :class="item.kind"></div>
        <div
          v-else
          :ref="(el) => setMatchRef(el, item.match.index)"
          class="draw-match"
          :class="{ 'has-bye-conflict': bothByeIndexes.includes(item.match.index), 'is-incomplete': isIncomplete(item.match.index) }"
          :title="bothByeIndexes.includes(item.match.index) ? '第 ' + (item.match.index + 1) + ' 场两个签位都是轮空，请调整' : undefined"
        >
          <div class="draw-match-side">
            <span class="draw-match-char">第</span>
            <span class="draw-match-num">{{ item.match.number }}</span>
            <span class="draw-match-char">场</span>
          </div>
          <div class="draw-slot-cell">
            <span class="draw-slot-num">{{ item.match.index * 2 + 1 }}号位</span>
            <button
              type="button"
              class="draw-slot-btn"
              :class="slotClass(item.match.index * 2)"
              :disabled="disabled"
              @click="onSlotClick(item.match.index * 2)"
            >
              {{ slotText(item.match.index * 2) }}
            </button>
          </div>
          <span class="draw-slot-vs">vs</span>
          <div class="draw-slot-cell">
            <span class="draw-slot-num">{{ item.match.index * 2 + 2 }}号位</span>
            <button
              type="button"
              class="draw-slot-btn"
              :class="slotClass(item.match.index * 2 + 1)"
              :disabled="disabled"
              @click="onSlotClick(item.match.index * 2 + 1)"
            >
              {{ slotText(item.match.index * 2 + 1) }}
            </button>
          </div>
        </div>
      </template>
    </div>

    <p v-if="!disabled && !roster.length" class="muted">请先录入参赛名单，再安排签位。</p>

    <!-- 吸底人员面板：点名单填入高亮签位，自动跳到下一空位；可收起成一行 -->
    <div v-if="!disabled && roster.length" class="draw-palette" :class="{ 'is-collapsed': paletteCollapsed }">
      <div class="draw-palette-head">
        <span>
          点击名单填入高亮签位（当前 {{ activeSlotLabel }}） · 已安排 {{ placedCount }}/{{ roster.length }}
        </span>
        <span class="draw-palette-actions">
          <span v-if="!paletteCollapsed" class="muted">点签位切换高亮；再点一次已高亮的签位可清空</span>
          <button class="ghost-action small" type="button" @click="paletteCollapsed = !paletteCollapsed">
            {{ paletteCollapsed ? '展开面板 ▲' : '收起面板 ▼' }}
          </button>
        </span>
      </div>
      <div v-if="!paletteCollapsed" class="draw-palette-grid">
        <button type="button" class="palette-tile palette-tile-bye" @click="assignBye">轮空位</button>
        <button
          v-for="item in paletteItems"
          :key="item.key"
          type="button"
          class="palette-tile"
          :class="{ 'is-used': usedKeys.has(String(item.key)) }"
          :disabled="usedKeys.has(String(item.key))"
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
import { DRAW_SLOT_BYE, DRAW_SLOT_EMPTY, fillUnplacedSlotsRandomly, validateDrawSlots } from '../utils/drawSlots'

const props = defineProps({
  // 签位数组：'' = 未选择，null = 轮空，其它 = roster 里的 key
  modelValue: { type: Array, default: () => [] },
  // 可选项：[{ key, label }]
  roster: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false },
  // 外部定位：flashNonce 每次自增触发一次滚动+高亮，定位到 flashMatch 指定的场次（0-based）
  flashMatch: { type: Number, default: -1 },
  flashNonce: { type: Number, default: 0 },
})

const emit = defineEmits(['update:modelValue'])

const slotCount = computed(() => props.modelValue.length)
const matches = computed(() => Array.from({ length: Math.floor(slotCount.value / 2) }, (_, index) => ({
  index,
  number: index + 1,
})))
const validation = computed(() => validateDrawSlots(props.modelValue, props.roster.map((item) => item.key)))
const bothByeIndexes = computed(() => validation.value.bothByeMatches)
const byeCount = computed(() => props.modelValue.filter((slot) => slot === DRAW_SLOT_BYE).length)
const placedCount = computed(() => props.modelValue.filter((slot) => slot !== null && slot !== undefined && slot !== DRAW_SLOT_EMPTY).length)
const usedKeys = computed(() => new Set(
  props.modelValue
    .filter((slot) => slot !== null && slot !== undefined && slot !== DRAW_SLOT_EMPTY)
    .map((slot) => String(slot)),
))

/** 面板顺序：轮空位固定第一，未使用的选手按原顺序在前，已使用的沉到末尾（变灰禁用） */
const paletteItems = computed(() => {
  const unused = []
  const used = []
  for (const item of props.roster) {
    (usedKeys.value.has(String(item.key)) ? used : unused).push(item)
  }
  return [...unused, ...used]
})

// —— 面板折叠 & 一键清空（两段式确认，防误触） ——
const paletteCollapsed = ref(false)
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
  emit('update:modelValue', props.modelValue.map(() => DRAW_SLOT_EMPTY))
  activeSlot.value = 0
}

// —— 分区线：1/2 处粗线对半切分，1/4 与 3/4 处细线；只落在整行边界（每行两场） ——
const gridItems = computed(() => {
  const total = matches.value.length
  const items = matches.value.map((match) => ({ type: 'match', match }))
  if (total >= 8 && total % 8 === 0) {
    const marks = [
      { after: total / 4, kind: 'quarter' },
      { after: total / 2, kind: 'half' },
      { after: (3 * total) / 4, kind: 'quarter' },
    ]
    // 从后往前插，避免前面的插入位置失效；after 为奇数说明切在行中间，跳过
    for (const mark of marks.sort((left, right) => right.after - left.after)) {
      if (mark.after % 2 !== 0) continue
      items.splice(mark.after, 0, { type: 'divider', kind: mark.kind, key: `divider-${mark.kind}-${mark.after}` })
    }
  }
  return items
})

// —— 签位点选状态：点击签位=设为高亮入位目标；再点一次=清空该签位 ——
const activeSlot = ref(0)

watch(() => props.modelValue.length, (length) => {
  if (activeSlot.value >= length) activeSlot.value = 0
})

const activeSlotLabel = computed(() => `${activeSlot.value + 1}号位`)

function isBye(index) {
  return props.modelValue[index] === DRAW_SLOT_BYE
}

function isFilled(index) {
  const value = props.modelValue[index]
  return value !== null && value !== undefined && value !== DRAW_SLOT_EMPTY
}

function slotLabel(index) {
  const key = String(props.modelValue[index])
  const item = props.roster.find((candidate) => String(candidate.key) === key)
  return item ? item.label : key
}

function slotText(index) {
  if (isBye(index)) return '轮空位'
  if (isFilled(index)) return slotLabel(index)
  return '未选择'
}

function slotClass(index) {
  return {
    'is-active': !props.disabled && activeSlot.value === index,
    'is-bye': isBye(index),
    'is-filled': isFilled(index),
  }
}

function onSlotClick(index) {
  if (props.disabled) return
  if (activeSlot.value === index) {
    // 再点一次：清空该签位（保留高亮，方便直接重填）
    const next = props.modelValue.slice()
    next[index] = DRAW_SLOT_EMPTY
    emit('update:modelValue', next)
    return
  }
  activeSlot.value = index
}

/** 入位后自动跳到下一个「未选择」签位（循环扫描） */
function advanceFrom(next, from) {
  const total = next.length
  for (let step = 0; step < total; step += 1) {
    const index = (from + step) % total
    if (next[index] === DRAW_SLOT_EMPTY) {
      activeSlot.value = index
      // 填完一整排（左右两张卡）进入下一排第一个签位时，把新排滚到屏幕第二行，
      // 刚填完的那一排留在第一行方便回改；填排内右侧卡时不触发，避免抖动
      if (index % 4 === 0) scrollActiveRowIntoPlace()
      return
    }
  }
}

/** 把高亮签位所在卡片行滚动到视口第二行位置；位置已合适时不动，避免抖动 */
function scrollActiveRowIntoPlace() {
  const matchIndex = Math.floor(activeSlot.value / 2)
  const el = matchRefs.get(matchIndex)
  if (!el) return

  // 找到真正的滚动容器（可能是 window，也可能是某个 overflow:auto 的祖先）
  let scroller = null
  let node = el.parentElement
  while (node && node !== document.body) {
    const overflowY = getComputedStyle(node).overflowY
    if ((overflowY === 'auto' || overflowY === 'scroll') && node.scrollHeight > node.clientHeight) {
      scroller = node
      break
    }
    node = node.parentElement
  }

  const rect = el.getBoundingClientRect()
  const rowHeight = rect.height + 10 // 卡片高 + 网格行距
  // 顶栏 .app-header 是 sticky 的，会盖住滚动区顶部：目标位置要加上它的实际高度，
  // 让刚填完的一排完整露出来，新排是其下方第一个内容行（视觉第二行）
  const headerHeight = document.querySelector('.app-header')?.offsetHeight ?? 0
  const viewTop = scroller ? scroller.getBoundingClientRect().top : 0
  const targetTop = viewTop + headerHeight + rowHeight + 8
  const delta = rect.top - targetTop
  if (Math.abs(delta) < 24) return
  if (scroller) scroller.scrollBy({ top: delta, behavior: 'smooth' })
  else window.scrollBy({ top: delta, behavior: 'smooth' })
}
function assign(key) {
  if (props.disabled) return
  const next = props.modelValue.slice()
  next[activeSlot.value] = key
  emit('update:modelValue', next)
  advanceFrom(next, activeSlot.value + 1)
}

function assignBye() {
  if (props.disabled) return
  const next = props.modelValue.slice()
  next[activeSlot.value] = DRAW_SLOT_BYE
  emit('update:modelValue', next)
  advanceFrom(next, activeSlot.value + 1)
}

// —— 场次卡片定位（供父级“去处理”跳转） ——
const matchRefs = new Map()
let flashTimer = null

function setMatchRef(el, index) {
  if (el) matchRefs.set(index, el)
  else matchRefs.delete(index)
}

watch(() => props.flashNonce, () => {
  if (props.flashMatch < 0) return
  const el = matchRefs.get(props.flashMatch)
  if (!el) return
  el.scrollIntoView({ behavior: 'smooth', block: 'center' })
  el.classList.remove('draw-match-flash')
  // 强制重启动画
  void el.offsetWidth
  el.classList.add('draw-match-flash')
  clearTimeout(flashTimer)
  flashTimer = setTimeout(() => el.classList.remove('draw-match-flash'), 1600)
})

function isIncomplete(matchIndex) {
  if (bothByeIndexes.value.includes(matchIndex)) return false
  return props.modelValue[matchIndex * 2] === DRAW_SLOT_EMPTY
    || props.modelValue[matchIndex * 2 + 1] === DRAW_SLOT_EMPTY
}

function fillRandom() {
  if (props.disabled) return
  emit('update:modelValue', fillUnplacedSlotsRandomly(props.modelValue, props.roster.map((item) => item.key)))
}
</script>

<style scoped>
.draw-slot-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
}

.draw-slot-toolbar-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.draw-slot-toolbar-actions .is-armed {
  color: var(--danger);
  border-color: rgba(255, 116, 109, 0.62);
}

.draw-match-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px 12px;
}

/* 分区线：跨两列；half=对半粗线，quarter=1/4 细线 */
.draw-grid-divider {
  grid-column: 1 / -1;
  height: 0;
  margin: 4px 0;
  border-top: 3px solid rgba(15, 23, 42, 0.3);
}

.draw-grid-divider.half {
  border-top-width: 5px;
  border-top-color: rgba(15, 23, 42, 0.55);
}

/* 单行卡片：纵向“第N场” | 签位 | vs | 签位，vs 与两个签位按钮垂直居中对齐 */
.draw-match {
  display: grid;
  grid-template-columns: 26px minmax(0, 1fr) 26px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: rgba(0, 0, 0, 0.12);
}

.draw-match.has-bye-conflict {
  border-color: rgba(255, 116, 109, 0.62);
  background: rgba(255, 116, 109, 0.1);
}

.draw-match.is-incomplete {
  border-color: rgba(240, 195, 109, 0.5);
}

/* 三行式“第 / N / 场”：flex 均分布局，间距完全对称 */
.draw-match-side {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 3px;
  align-self: stretch;
  border-right: 1px dashed var(--line);
}

.draw-match-char,
.draw-match-num {
  font-weight: 800;
  line-height: 1;
  color: var(--muted);
  white-space: nowrap;
}

.draw-match-char {
  font-size: 12px;
}

.draw-match-num {
  font-size: 10px;
}

.draw-match.has-bye-conflict .draw-match-char,
.draw-match.has-bye-conflict .draw-match-num {
  color: var(--danger);
}

.draw-match-flash {
  animation: draw-match-flash 1.5s ease;
}

@keyframes draw-match-flash {
  0%, 60% {
    box-shadow: 0 0 0 3px rgba(255, 116, 109, 0.55);
    border-color: rgba(255, 116, 109, 0.9);
  }
  100% {
    box-shadow: none;
  }
}

.draw-slot-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}

.draw-slot-num {
  flex: none;
  min-width: 42px;
  color: var(--muted);
  font-size: 12px;
  text-align: right;
  white-space: nowrap;
}

.draw-slot-btn {
  min-width: 0;
  width: 100%;
  min-height: 34px;
  padding: 4px 8px;
  border: 1px dashed var(--line);
  border-radius: 7px;
  background: rgba(219, 222, 193, 0.04);
  color: var(--muted);
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.draw-slot-btn.is-filled {
  border-style: solid;
  border-color: rgba(219, 222, 193, 0.42);
  background: rgba(219, 222, 193, 0.09);
  color: inherit;
}

.draw-slot-btn.is-bye {
  border-style: dashed;
  background: rgba(0, 0, 0, 0.18);
  color: var(--muted);
}

.draw-slot-btn.is-active {
  border-color: rgba(59, 130, 246, 0.85);
  box-shadow: 0 0 0 2px rgba(59, 130, 246, 0.55);
}

.draw-slot-btn:disabled {
  cursor: default;
}

.draw-slot-vs {
  color: var(--muted);
  font-size: 12px;
  font-weight: 800;
  text-align: center;
}

/* 吸底人员面板：滚动签表时始终可见；可收起成一行 */
.draw-palette {
  position: sticky;
  bottom: 8px;
  z-index: 6;
  margin-top: 12px;
  padding: 10px 12px;
  border: 1px solid var(--line);
  border-radius: 10px;
  background: rgba(4, 37, 32, 0.97);
  box-shadow: 0 -8px 22px rgba(0, 0, 0, 0.35);
}

.draw-palette.is-collapsed {
  padding: 6px 12px;
}

.draw-palette.is-collapsed .draw-palette-head {
  margin-bottom: 0;
}

.draw-palette-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
  font-size: 13px;
}

.draw-palette-actions {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: none;
}

.draw-palette-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(92px, 1fr));
  gap: 8px;
  max-height: 176px;
  overflow-y: auto;
}

.palette-tile {
  padding: 7px 8px;
  border: 1px solid var(--line);
  border-radius: 7px;
  background: rgba(219, 222, 193, 0.06);
  color: inherit;
  font-size: 12px;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  cursor: pointer;
}

.palette-tile:hover:not(:disabled) {
  border-color: rgba(59, 130, 246, 0.75);
  background: rgba(59, 130, 246, 0.12);
}

.palette-tile.is-used {
  opacity: 0.38;
  text-decoration: line-through;
  cursor: default;
}

.palette-tile-bye {
  border-style: dashed;
  background: rgba(0, 0, 0, 0.2);
  color: var(--muted);
}
</style>
