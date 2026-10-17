<template>
  <div
    ref="viewportRef"
    class="bracket-viewport"
    :class="{ 'is-dragging': isDragging }"
    @mousedown="handleMouseDown"
    @wheel="handleWheel"
    @touchstart="handleTouchStart"
    @touchmove="handleTouchMove"
    @touchend="handleTouchEnd"
  >
    <!-- 画布主体 -->
    <div
      class="bracket-canvas"
      :style="canvasTransformStyle"
    >
      <div
        class="bracket-board"
        :style="{ width: `${layout.width}px`, height: `${layout.height}px` }"
      >
        <!-- 轮次表头 -->
        <div
          v-for="round in layout.rounds"
          :key="round.roundNum"
          class="bracket-round-header"
          :style="{ left: `${round.left}px`, width: `${layout.cardWidth}px` }"
        >
          <span class="round-badge">{{ getRoundName(round.roundNum, layout.rounds.length) }}</span>
        </div>

        <!-- 连线段 -->
        <div
          v-for="seg in layout.segments"
          :key="seg.id"
          class="bracket-connector"
          :class="`connector-${seg.axis}`"
          :style="{
            left: `${seg.left}px`,
            top: `${seg.top}px`,
            width: `${seg.width}px`,
            height: `${seg.height}px`,
          }"
        />

        <!-- 比赛节点 -->
        <div
          v-for="node in layout.nodes"
          :key="node.id"
          class="bracket-node"
          :style="{
            left: `${node.left}px`,
            top: `${node.top}px`,
            width: `${layout.cardWidth}px`,
            height: `${layout.cardHeight}px`,
          }"
        >
          <span v-if="isThirdPlace(node.match)" class="third-place-badge">🥉 季军赛</span>
          <MatchCard
            :match="node.match"
            :left-name="getParticipantName(node.match, 'left')"
            :right-name="getParticipantName(node.match, 'right')"
            :is-team-match="isTeamMatch"
            :is-volleyball="isVolleyball"
            @click-match="onMatchClick"
          />
        </div>
      </div>
    </div>

    <!-- 缩放控制浮条 -->
    <div class="bracket-controls">
      <button class="control-btn" type="button" title="自适应窗口居中显示全部签位" @click.stop="fitOverview">
        总览
      </button>
      <button class="control-btn" type="button" title="重置为 100% 原始大小" @click.stop="resetScale">
        100%
      </button>
      <div class="zoom-group">
        <button class="control-btn icon-btn" type="button" title="缩小" @click.stop="zoomOut">
          &minus;
        </button>
        <span class="zoom-value">{{ Math.round(scale * 100) }}%</span>
        <button class="control-btn icon-btn" type="button" title="放大" @click.stop="zoomIn">
          &#43;
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import MatchCard from './MatchCard.vue'
import {
  buildKnockoutBracketLayout,
  calculateFitScale,
  centerBracketPosition,
  clampBracketScale,
  getKnockoutParticipantName,
  isThirdPlaceMatch,
  KNOCKOUT_BRACKET_VIEWPORT,
} from '../../utils/bracketLayout'

const props = defineProps({
  matches: { type: Array, default: () => [] },
  players: { type: Array, default: () => [] },
  isTeamMatch: { type: Boolean, default: false },
  isVolleyball: { type: Boolean, default: false },
})

const emit = defineEmits(['click-match'])

const viewportRef = ref(null)
const scale = ref(1.0)
const position = ref({ x: 0, y: 0 })
const isDragging = ref(false)
const dragStart = ref({ x: 0, y: 0, posX: 0, posY: 0 })

// 双指触控缩放记忆
let touchDistance = 0

const playerMap = computed(() => {
  const map = new Map()
  for (const p of props.players || []) {
    const id = String(p.id ?? p.playerId ?? '')
    const name = p.name ?? p.playerName ?? ''
    if (id) map.set(id, name)
  }
  return map
})

const layout = computed(() => {
  return buildKnockoutBracketLayout(props.matches, {
    cardWidth: 260,
    cardHeight: 104,
  })
})

const canvasTransformStyle = computed(() => {
  return {
    transform: `translate3d(${position.value.x}px, ${position.value.y}px, 0) scale(${scale.value})`,
    transformOrigin: '0 0',
  }
})

function getParticipantName(match, side) {
  return getKnockoutParticipantName({
    match,
    side,
    playerMap: playerMap.value,
  })
}

function isThirdPlace(match) {
  return isThirdPlaceMatch(match)
}

function getRoundName(roundNum, totalRounds) {
  const r = Number(roundNum || 1)
  const remaining = totalRounds - r + 1
  if (remaining === 1) return '决赛'
  if (remaining === 2) return '半决赛'
  if (remaining === 3) return '1/4 决赛'
  return `第 ${r} 轮`
}

function fitOverview() {
  if (!viewportRef.value || !layout.value.width) return
  const vw = viewportRef.value.clientWidth
  const vh = viewportRef.value.clientHeight
  const targetScale = calculateFitScale(layout.value, { width: vw, height: vh })
  scale.value = targetScale
  position.value = centerBracketPosition(layout.value, { width: vw, height: vh }, targetScale)
}

function resetScale() {
  if (!viewportRef.value) return
  const vw = viewportRef.value.clientWidth
  const vh = viewportRef.value.clientHeight
  scale.value = 1.0
  position.value = centerBracketPosition(layout.value, { width: vw, height: vh }, 1.0)
}

function zoomIn() {
  adjustScale(scale.value + KNOCKOUT_BRACKET_VIEWPORT.scaleStep)
}

function zoomOut() {
  adjustScale(scale.value - KNOCKOUT_BRACKET_VIEWPORT.scaleStep)
}

function adjustScale(nextScale, anchorX, anchorY) {
  const clamped = clampBracketScale(nextScale)
  if (clamped === scale.value) return

  if (!viewportRef.value) {
    scale.value = clamped
    return
  }

  const vw = viewportRef.value.clientWidth
  const vh = viewportRef.value.clientHeight
  const ax = anchorX != null ? anchorX : vw / 2
  const ay = anchorY != null ? anchorY : vh / 2

  const oldScale = scale.value
  const contentX = (ax - position.value.x) / oldScale
  const contentY = (ay - position.value.y) / oldScale

  scale.value = clamped
  position.value = {
    x: Math.round(ax - contentX * clamped),
    y: Math.round(ay - contentY * clamped),
  }
}

// 鼠标滚轮缩放
function handleWheel(e) {
  e.preventDefault()
  if (!viewportRef.value) return
  const rect = viewportRef.value.getBoundingClientRect()
  const mouseX = e.clientX - rect.left
  const mouseY = e.clientY - rect.top

  const delta = e.deltaY < 0 ? 0.08 : -0.08
  adjustScale(scale.value + delta, mouseX, mouseY)
}

// 鼠标拖拽平移
let hasDragged = false

// 鼠标拖拽平移
function handleMouseDown(e) {
  // 仅左键且非点击按钮时触发平移
  if (e.button !== 0) return
  if (e.target.closest('.bracket-controls') || e.target.closest('.control-btn')) return

  hasDragged = false
  isDragging.value = true
  dragStart.value = {
    x: e.clientX,
    y: e.clientY,
    posX: position.value.x,
    posY: position.value.y,
  }

  window.addEventListener('mousemove', handleMouseMove)
  window.addEventListener('mouseup', handleMouseUp)
}

function handleMouseMove(e) {
  if (!isDragging.value) return
  const dx = e.clientX - dragStart.value.x
  const dy = e.clientY - dragStart.value.y
  if (Math.hypot(dx, dy) > 5) {
    hasDragged = true
  }
  position.value = {
    x: dragStart.value.posX + dx,
    y: dragStart.value.posY + dy,
  }
}

function handleMouseUp() {
  isDragging.value = false
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', handleMouseUp)
}

// 触控支持（移动/平板端）
// 触控支持（移动/平板端）
function handleTouchStart(e) {
  hasDragged = false
  if (e.touches.length === 1) {
    isDragging.value = true
    dragStart.value = {
      x: e.touches[0].clientX,
      y: e.touches[0].clientY,
      posX: position.value.x,
      posY: position.value.y,
    }
  } else if (e.touches.length === 2) {
    isDragging.value = false
    const dx = e.touches[0].clientX - e.touches[1].clientX
    const dy = e.touches[0].clientY - e.touches[1].clientY
    touchDistance = Math.hypot(dx, dy)
  }
}

function handleTouchMove(e) {
  if (e.touches.length === 1 && isDragging.value) {
    const dx = e.touches[0].clientX - dragStart.value.x
    const dy = e.touches[0].clientY - dragStart.value.y
    if (Math.hypot(dx, dy) > 5) {
      hasDragged = true
    }
    position.value = {
      x: dragStart.value.posX + dx,
      y: dragStart.value.posY + dy,
    }
  } else if (e.touches.length === 2 && touchDistance > 0) {
    hasDragged = true
    const dx = e.touches[0].clientX - e.touches[1].clientX
    const dy = e.touches[0].clientY - e.touches[1].clientY
    const newDist = Math.hypot(dx, dy)
    const factor = newDist / touchDistance
    adjustScale(scale.value * factor)
    touchDistance = newDist
  }
}

function handleTouchEnd() {
  isDragging.value = false
  touchDistance = 0
}

function onMatchClick(match) {
  if (hasDragged) {
    hasDragged = false
    return
  }
  emit('click-match', match)
}

defineExpose({
  fitOverview,
  resetScale,
})

let resizeObserver = null
let prevWidth = 0

onMounted(() => {
  nextTick(() => {
    if (viewportRef.value && viewportRef.value.clientWidth > 0) {
      fitOverview()
      prevWidth = viewportRef.value.clientWidth
    }
  })

  if (viewportRef.value && typeof ResizeObserver !== 'undefined') {
    resizeObserver = new ResizeObserver((entries) => {
      const entry = entries[0]
      if (entry && entry.contentRect.width > 0 && entry.contentRect.height > 0) {
        if (prevWidth === 0) {
          fitOverview()
        }
        prevWidth = entry.contentRect.width
      } else {
        prevWidth = 0
      }
    })
    resizeObserver.observe(viewportRef.value)
  }
})

onUnmounted(() => {
  window.removeEventListener('mousemove', handleMouseMove)
  window.removeEventListener('mouseup', handleMouseUp)
  if (resizeObserver) {
    resizeObserver.disconnect()
    resizeObserver = null
  }
})
</script>
