import { computed, getCurrentInstance, nextTick, ref, watch } from 'vue'
import {
  calculateOverviewScale,
  clampBracketScaleWithin,
  clampBracketPosition,
  keepViewportCenterPosition,
  KNOCKOUT_BRACKET_VIEWPORT,
} from './knockout-bracket-layout'

function rpxToPx(value) {
  const numberValue = Number(value || 0)
  if (!Number.isFinite(numberValue)) return 0
  try {
    if (typeof uni?.upx2px === 'function') return Number(uni.upx2px(numberValue)) || 0
  } catch (_) {
    // noop
  }
  return numberValue / 2
}

function readViewport(selector, instance) {
  return new Promise((resolve) => {
    try {
      const query = uni.createSelectorQuery()
      const scopedQuery = instance?.proxy && typeof query.in === 'function'
        ? query.in(instance.proxy)
        : query
      scopedQuery
        .select(selector)
        .boundingClientRect((rect) => {
          resolve({
            width: Number(rect?.width || 0),
            height: Number(rect?.height || 0),
          })
        })
        .exec()
    } catch (_) {
      resolve({ width: 0, height: 0 })
    }
  })
}

const TAP_MOVE_THRESHOLD_PX = 10
const SYNTHETIC_TAP_SUPPRESS_MS = 700

function readTouches(event) {
  const touches = event?.touches || event?.detail?.touches || []
  return Array.isArray(touches) ? touches : []
}

function firstTouchPoint(touches) {
  const touch = touches[0] || {}
  return {
    x: Number(touch.clientX ?? touch.pageX ?? 0),
    y: Number(touch.clientY ?? touch.pageY ?? 0),
  }
}

export function createBracketTapGuard(now = () => Date.now(), onBlockingChange = () => {}) {
  let touchStart = null
  let gestureDetected = false
  let suppressNextTap = false
  let suppressUntil = 0
  let blocking = false

  function setBlocking(next) {
    if (blocking === next) return
    blocking = next
    onBlockingChange(blocking)
  }

  function markGesture() {
    gestureDetected = true
    setBlocking(true)
  }

  function armTapSuppress() {
    suppressNextTap = true
    suppressUntil = now() + SYNTHETIC_TAP_SUPPRESS_MS
  }

  function handleTouchStart(event) {
    const touches = readTouches(event)
    if (!touches.length) return
    const point = firstTouchPoint(touches)
    touchStart = {
      x: point.x,
      y: point.y,
      startedAt: now(),
    }
    gestureDetected = false
    setBlocking(false)
    if (touches.length >= 2) {
      markGesture()
    }
  }

  function handleTouchMove(event) {
    const touches = readTouches(event)
    if (!touches.length) return
    if (!touchStart) {
      handleTouchStart(event)
    }
    if (touches.length >= 2) {
      markGesture()
      return
    }
    const point = firstTouchPoint(touches)
    const dx = point.x - Number(touchStart?.x || 0)
    const dy = point.y - Number(touchStart?.y || 0)
    if (Math.hypot(dx, dy) > TAP_MOVE_THRESHOLD_PX) {
      markGesture()
    }
  }

  function handleTouchEnd() {
    if (gestureDetected) {
      armTapSuppress()
    }
    touchStart = null
    gestureDetected = false
    setBlocking(false)
  }

  function handleTouchCancel() {
    if (gestureDetected) {
      armTapSuppress()
    }
    touchStart = null
    gestureDetected = false
    setBlocking(false)
  }

  function handleScale() {
    markGesture()
    armTapSuppress()
  }

  function handleMoveChange(event) {
    const source = event?.detail?.source
    if (touchStart && (source === 'touch' || source == null)) {
      markGesture()
    }
  }

  function shouldSuppressTap() {
    const current = now()
    if (gestureDetected || blocking) {
      armTapSuppress()
      touchStart = null
      gestureDetected = false
      setBlocking(false)
      return true
    }
    if (!suppressNextTap) return false
    suppressNextTap = false
    if (current <= suppressUntil) {
      suppressUntil = 0
      return true
    }
    suppressUntil = 0
    return false
  }

  return {
    handleTouchStart,
    handleTouchMove,
    handleTouchEnd,
    handleTouchCancel,
    handleScale,
    handleMoveChange,
    shouldSuppressTap,
  }
}

 export function useKnockoutBracketViewport(layout, selector = '.bracket-viewport') {
   const instance = getCurrentInstance()
   // x / y / scale 是「编程意图」：只在按钮缩放、总览、100%、松手收敛时写入。
   // 手势（拖动/双指缩放）期间绝不回写——原生组件在手势中自己维护变换，
   // 回写会与手势互相重置基线，产生“两种缩放档位来回抖动”。
   const x = ref(0)
   const y = ref(0)
   const scale = ref(KNOCKOUT_BRACKET_VIEWPORT.baseScale)
   // 原生组件的实际状态（从 @change / @scale 事件跟踪），缩放数学一律以它为准
   const live = {
     x: 0,
     y: 0,
     scale: KNOCKOUT_BRACKET_VIEWPORT.baseScale,
   }
   // 缩放百分比指示：手势期间跟随原生实际值刷新，只驱动文字节点，不回传组件
   const displayScale = ref(KNOCKOUT_BRACKET_VIEWPORT.baseScale)
   const overviewScale = ref(KNOCKOUT_BRACKET_VIEWPORT.minScale)
   const maxScale = ref(KNOCKOUT_BRACKET_VIEWPORT.maxScale)
   const viewport = ref({ width: 0, height: 0 })
   const isGestureBlocking = ref(false)
   const tapGuard = createBracketTapGuard(() => Date.now(), (blocking) => {
     isGestureBlocking.value = blocking
   })
   let positionSyncPending = false
 
   const SCALE_SYNC_EPSILON = 0.0005
 
   const layoutSize = () => ({
     width: rpxToPx(layout.value?.width),
     height: rpxToPx(layout.value?.height),
   })
   const firstRoundHeight = () => (
     rpxToPx(layout.value?.firstRoundHeight || layout.value?.height)
   )
 
   function isOverviewScale(scaleValue = live.scale) {
     return scaleValue <= overviewScale.value + 0.001
   }
 
   function isVerticalLocked(scaleValue = live.scale, viewportSize = viewport.value) {
     return firstRoundHeight() * scaleValue <= viewportSize.height + 0.5
   }
 
   const moveDirection = 'all'
 
   function forcePositionSync(lockX, lockY) {
     if (positionSyncPending) return
     positionSyncPending = true
     if (lockX) x.value = -1
     if (lockY) y.value = -1
     nextTick(() => {
       if (lockX) x.value = 0
       if (lockY) y.value = 0
       positionSyncPending = false
     })
   }
 
   async function measureViewport() {
     await nextTick()
     const size = await readViewport(selector, instance)
     if (size.width > 0 && size.height > 0) viewport.value = size
     return viewport.value
   }
 
   async function refreshOverviewScale() {
     const viewportSize = await measureViewport()
     if (viewportSize.width <= 0 || viewportSize.height <= 0) {
       return null
     }
     const size = layoutSize()
     if (size.width <= 0 || size.height <= 0) return null
     const nextScale = Math.min(
       calculateOverviewScale(size, viewportSize),
       KNOCKOUT_BRACKET_VIEWPORT.maxScale,
     )
     overviewScale.value = nextScale
     maxScale.value = KNOCKOUT_BRACKET_VIEWPORT.maxScale
     return { nextScale, viewportSize, size }
   }
 
   /** 把目标缩放写入意图值并同步指示器；与当前意图值差异极小时跳过（避免回声循环） */
   function syncIntentScale(nextScale) {
     live.scale = nextScale
     displayScale.value = nextScale
     if (Math.abs(nextScale - scale.value) > SCALE_SYNC_EPSILON) {
       scale.value = nextScale
     }
   }
 
   /**
    * 锁定轴归位：
    * - 意图值与目标(0)不同 → 直接写入，原生空闲时必然生效；
    * - 意图值已是 0 但原生实际位置偏离 → 才用 -1→0 翻转强制原生重新定位。
    * 翻转产生的 @change 回声只会更新 live 状态，不会再触发新的翻转（收敛）。
    */
   function enforceLockedPosition(scaleValue) {
     const lockedOverview = isOverviewScale(scaleValue)
     const lockedVertical = isVerticalLocked(scaleValue)
     if (lockedOverview) {
       if (x.value !== 0) x.value = 0
       if (Math.abs(live.x) > 0.5) forcePositionSync(true, false)
     }
     if (lockedVertical) {
       if (y.value !== 0) y.value = 0
       if (Math.abs(live.y) > 0.5) forcePositionSync(false, true)
     }
     return { lockedOverview, lockedVertical }
   }
 
   async function fitToOverview(retryCount = 3) {
     const overview = await refreshOverviewScale()
     if (!overview && retryCount > 0) {
       setTimeout(() => fitToOverview(retryCount - 1), 80)
       return
     }
     if (!overview) return
     const { nextScale } = overview
     x.value = 0
     y.value = 0
     live.x = 0
     live.y = 0
     syncIntentScale(nextScale)
     forcePositionSync(true, true)
   }
 
   async function setDefaultView(retryCount = 3) {
     const overview = await refreshOverviewScale()
     if (!overview && retryCount > 0) {
       setTimeout(() => setDefaultView(retryCount - 1), 80)
       return
     }
     const nextScale = clampBracketScaleWithin(KNOCKOUT_BRACKET_VIEWPORT.baseScale, overviewScale.value, maxScale.value)
     x.value = 0
     y.value = 0
     live.x = 0
     live.y = 0
     syncIntentScale(nextScale)
     forcePositionSync(true, true)
   }
 
   async function resetView() {
     const viewportSize = await measureViewport()
     const size = layoutSize()
     const currentScale = Number(live.scale) || scale.value
     const nextScale = clampBracketScaleWithin(KNOCKOUT_BRACKET_VIEWPORT.baseScale, overviewScale.value, maxScale.value)
     const nextPosition = keepViewportCenterPosition(size, viewportSize, {
       x: live.x,
       y: live.y,
       scale: currentScale,
     }, nextScale)
     x.value = nextPosition.x
     y.value = nextPosition.y
     live.x = nextPosition.x
     live.y = nextPosition.y
     syncIntentScale(nextScale)
     enforceLockedPosition(nextScale)
   }
 
   async function zoomBy(delta) {
     const viewportSize = await measureViewport()
     const size = layoutSize()
     const currentScale = Number(live.scale) || scale.value
     const nextScale = clampBracketScaleWithin(currentScale + delta, overviewScale.value, maxScale.value)
     const nextPosition = keepViewportCenterPosition(size, viewportSize, {
       x: live.x,
       y: live.y,
       scale: currentScale,
     }, nextScale)
     x.value = nextPosition.x
     y.value = nextPosition.y
     live.x = nextPosition.x
     live.y = nextPosition.y
     syncIntentScale(nextScale)
     enforceLockedPosition(nextScale)
   }
 
   /** 松手/手势取消时调用：一次性的状态收敛点 */
   function settleLockedBounds() {
     const currentScale = Number(live.scale) || scale.value
     // 落在总览档容差内时精确吸附到总览比例（只在此处做一次，不在手势中反复做）
     const settledScale = isOverviewScale(currentScale)
       ? overviewScale.value
       : clampBracketScaleWithin(currentScale, overviewScale.value, maxScale.value)
     syncIntentScale(settledScale)
     enforceLockedPosition(settledScale)
   }
 
   function trackNativePosition(detail) {
     const detailX = Number(detail?.x)
     const detailY = Number(detail?.y)
     if (Number.isFinite(detailX)) live.x = detailX
     if (Number.isFinite(detailY)) live.y = detailY
   }
 
   function handleTouchEnd(event) {
     tapGuard.handleTouchEnd(event)
     settleLockedBounds()
   }
 
   function handleTouchCancel(event) {
     tapGuard.handleTouchCancel(event)
     settleLockedBounds()
   }
 
   function handleMove(event) {
     tapGuard.handleMoveChange(event)
     // 只观察原生位置（含越界回弹、-1→0 翻转的回声），不回写 x/y
     trackNativePosition(event?.detail)
   }
 
   function handleScale(event) {
     tapGuard.handleScale(event)
     const detail = event?.detail || {}
     const requestedScale = Number(detail.scale)
     if (Number.isFinite(requestedScale)) {
       // 手势期间只记录原生实际缩放、刷新百分比指示；
       // 越界由原生按 scale-min/scale-max 自行约束，松手时 settle 统一收敛
       live.scale = requestedScale
       displayScale.value = requestedScale
     }
     trackNativePosition(detail)
   }
 
   watch(
     () => [layout.value?.width, layout.value?.height],
     ([width, height]) => {
       if (Number(width || 0) > 0 && Number(height || 0) > 0) {
         setDefaultView()
       }
     },
     { flush: 'post' },
   )
 
   return {
     x,
     y,
     scale,
     scalePercent: computed(() => {
       const baseScale = Number(KNOCKOUT_BRACKET_VIEWPORT.baseScale) || 1
       const currentScale = Number(displayScale.value) || baseScale
       return `${Math.round((currentScale / baseScale) * 100)}%`
     }),
     minScale: overviewScale,
     maxScale,
     moveDirection,
     isGestureBlocking,
     fitToOverview,
     setDefaultView,
     resetView,
     zoomIn: () => zoomBy(KNOCKOUT_BRACKET_VIEWPORT.scaleStep),
     zoomOut: () => zoomBy(-KNOCKOUT_BRACKET_VIEWPORT.scaleStep),
     handleTouchStart: tapGuard.handleTouchStart,
     handleTouchMove: tapGuard.handleTouchMove,
     handleTouchEnd,
     handleTouchCancel,
     handleMove,
     handleScale,
     shouldSuppressTap: tapGuard.shouldSuppressTap,
   }
 }
