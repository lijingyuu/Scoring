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
  const x = ref(0)
  const y = ref(0)
  const scale = ref(KNOCKOUT_BRACKET_VIEWPORT.baseScale)
  const overviewScale = ref(KNOCKOUT_BRACKET_VIEWPORT.minScale)
  const maxScale = ref(KNOCKOUT_BRACKET_VIEWPORT.maxScale)
  const viewport = ref({ width: 0, height: 0 })
  const isGestureBlocking = ref(false)
  const tapGuard = createBracketTapGuard(() => Date.now(), (blocking) => {
    isGestureBlocking.value = blocking
  })
  let positionSyncPending = false

  const layoutSize = () => ({
    width: rpxToPx(layout.value?.width),
    height: rpxToPx(layout.value?.height),
  })
  const firstRoundHeight = () => (
    rpxToPx(layout.value?.firstRoundHeight || layout.value?.height)
  )

  function isOverviewScale(scaleValue = scale.value) {
    return scaleValue <= overviewScale.value + 0.001
  }

  function isVerticalLocked(scaleValue = scale.value, viewportSize = viewport.value) {
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

  async function fitToOverview(retryCount = 3) {
    const overview = await refreshOverviewScale()
    if (!overview && retryCount > 0) {
      setTimeout(() => fitToOverview(retryCount - 1), 80)
      return
    }
    if (!overview) return
    const { nextScale } = overview
    scale.value = nextScale
    x.value = 0
    y.value = 0
    forcePositionSync(true, true)
  }

  async function setDefaultView(retryCount = 3) {
    const overview = await refreshOverviewScale()
    if (!overview && retryCount > 0) {
      setTimeout(() => setDefaultView(retryCount - 1), 80)
      return
    }
    const nextScale = clampBracketScaleWithin(KNOCKOUT_BRACKET_VIEWPORT.baseScale, overviewScale.value, maxScale.value)
    scale.value = nextScale
    x.value = 0
    y.value = 0
    forcePositionSync(true, true)
  }

  async function resetView() {
    const viewportSize = await measureViewport()
    const size = layoutSize()
    const nextScale = clampBracketScaleWithin(KNOCKOUT_BRACKET_VIEWPORT.baseScale, overviewScale.value, maxScale.value)
    const nextPosition = keepViewportCenterPosition(size, viewportSize, {
      x: x.value,
      y: y.value,
      scale: scale.value,
    }, nextScale)
    const lockedOverview = nextScale <= overviewScale.value + 0.001
    const lockedVertical = isVerticalLocked(nextScale, viewportSize)
    const constrainedPosition = clampBracketPosition(size, viewportSize, {
      x: 0,
      y: nextPosition.y,
    }, nextScale)
    scale.value = nextScale
    x.value = lockedOverview ? 0 : nextPosition.x
    y.value = lockedVertical ? 0 : (lockedOverview ? constrainedPosition.y : nextPosition.y)
    if (lockedOverview || lockedVertical) {
      forcePositionSync(lockedOverview, lockedVertical)
    }
  }

  async function zoomBy(delta) {
    const viewportSize = await measureViewport()
    const size = layoutSize()
    const nextScale = clampBracketScaleWithin(scale.value + delta, overviewScale.value, maxScale.value)
    const lockedOverview = nextScale <= overviewScale.value + 0.001
    const lockedVertical = isVerticalLocked(nextScale, viewportSize)
    const nextPosition = keepViewportCenterPosition(size, viewportSize, {
      x: x.value,
      y: y.value,
      scale: scale.value,
    }, nextScale)
    const constrainedPosition = clampBracketPosition(size, viewportSize, {
      x: 0,
      y: nextPosition.y,
    }, nextScale)
    scale.value = nextScale
    x.value = lockedOverview ? 0 : nextPosition.x
    y.value = lockedVertical ? 0 : (lockedOverview ? constrainedPosition.y : nextPosition.y)
    if (lockedOverview || lockedVertical) {
      forcePositionSync(lockedOverview, lockedVertical)
    }
  }

  function settleLockedBounds() {
    const lockedOverview = isOverviewScale()
    const lockedVertical = isVerticalLocked()
    if (lockedOverview) {
      scale.value = overviewScale.value
      x.value = 0
    }
    if (lockedVertical) {
      y.value = 0
    }
    if (lockedOverview || lockedVertical) {
      forcePositionSync(lockedOverview, lockedVertical)
    }
  }

  function handleTouchEnd(event) {
    tapGuard.handleTouchEnd(event)
    settleLockedBounds()
  }

  function handleTouchCancel(event) {
    tapGuard.handleTouchCancel(event)
    settleLockedBounds()
  }

  function handleMove(event, options = {}) {
    tapGuard.handleMoveChange(event)
    const syncLocked = options.syncLocked !== false
    const detail = event?.detail || {}
    const lockedOverview = isOverviewScale()
    const lockedVertical = isVerticalLocked()
    if (lockedOverview) {
      x.value = 0
    } else if (Number.isFinite(Number(detail.x))) {
      x.value = Number(detail.x)
    }
    if (lockedVertical) {
      y.value = 0
      if (syncLocked && Number.isFinite(Number(detail.y)) && Math.abs(Number(detail.y)) > 0.5) {
        forcePositionSync(false, true)
      }
    } else if (Number.isFinite(Number(detail.y))) {
      y.value = Number(detail.y)
    }
  }

  function handleScale(event) {
    tapGuard.handleScale(event)
    const detail = event?.detail || {}
    const requestedScale = Number(detail.scale)
    const reachedOverview = Number.isFinite(requestedScale) && requestedScale <= overviewScale.value + 0.001
    if (Number.isFinite(requestedScale)) {
      scale.value = reachedOverview
        ? overviewScale.value
        : clampBracketScaleWithin(requestedScale, overviewScale.value, maxScale.value)
    }
    const lockedOverview = isOverviewScale()
    const lockedVertical = isVerticalLocked()
    handleMove(event, { syncLocked: false })
    if (!reachedOverview && (lockedOverview || lockedVertical)) {
      forcePositionSync(lockedOverview, lockedVertical)
    }
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
      const currentScale = Number(scale.value) || baseScale
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
