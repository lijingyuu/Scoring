 import { afterEach, describe, expect, it } from 'vitest'
import { createBracketTapGuard } from './use-knockout-bracket-viewport'

function touch(x, y) {
  return { clientX: x, clientY: y }
}

function touchEvent(points) {
  return { touches: points.map(([x, y]) => touch(x, y)) }
}

describe('bracket tap guard', () => {
  it('allows a normal tap without movement', () => {
    let now = 1000
    const guard = createBracketTapGuard(() => now)

    guard.handleTouchStart(touchEvent([[10, 10]]))
    guard.handleTouchEnd()

    expect(guard.shouldSuppressTap()).toBe(false)
  })

  it('suppresses the synthetic tap after a pinch gesture and consumes the lock once', () => {
    let now = 1000
    const guard = createBracketTapGuard(() => now)

    guard.handleTouchStart(touchEvent([[10, 10]]))
    guard.handleTouchMove(touchEvent([[10, 10], [30, 30]]))
    guard.handleScale({ detail: { scale: 0.8 } })
    guard.handleTouchEnd()

    now += 80
    expect(guard.shouldSuppressTap()).toBe(true)
    expect(guard.shouldSuppressTap()).toBe(false)
  })

  it('blocks pointer events while dragging and clears blocking after touch end', () => {
    let blocking = false
    const guard = createBracketTapGuard(() => 1000, (next) => {
      blocking = next
    })

    guard.handleTouchStart(touchEvent([[10, 10]]))
    guard.handleTouchMove(touchEvent([[26, 10]]))
    expect(blocking).toBe(true)

    guard.handleTouchEnd()
    expect(blocking).toBe(false)
    expect(guard.shouldSuppressTap()).toBe(true)
  })

  it('does not suppress a later intentional tap after the safety window', () => {
    let now = 1000
    const guard = createBracketTapGuard(() => now)

    guard.handleTouchStart(touchEvent([[10, 10], [30, 30]]))
    guard.handleTouchCancel()

    now += 1000
    expect(guard.shouldSuppressTap()).toBe(false)
  })
})
// ---------------------------------------------------------------------------
// 原生 movable-view 模拟器：复现“缩放抖动”
//
// 模型（对应微信 movable-view 的真实行为）：
// 1. JS 侧 ref（:x/:y/:scale-value）每次变化 = 一次 setData，原生异步收到后
//    应用该值，并回发 @change/@scale 回声事件（携带原生最终状态）；
// 2. 原生自身有自己的内容尺寸测量（与 JS 侧 upx2px 估算可能差 1px），
//    越界写入会被原生回弹修正；
// 3. 双指捏合期间，缩放由原生手势驱动，通过 @scale 事件持续上报。
// ---------------------------------------------------------------------------
import { nextTick, ref, watch } from 'vue'
import { buildKnockoutBracketLayout } from './knockout-bracket-layout'
import { useKnockoutBracketViewport } from './use-knockout-bracket-viewport'

function createMovableViewSim({ refs, handlers, areaWidth, areaHeight, layoutWidthPx, layoutHeightPx, extraContentPx = 1 }) {
  const native = {
    x: Number(refs.x.value) || 0,
    y: Number(refs.y.value) || 0,
    scale: Number(refs.scale.value) || 1,
  }
  const queue = []
  const stats = { changeEvents: 0, scaleEvents: 0 }

  const clampNum = (value, min, max) => Math.min(max, Math.max(min, value))
  const scaleBounds = () => [vp_min(), vp_max()]
  let vp_min = () => 0.1
  let vp_max = () => 0.98

  function nativeRange(axis, scaleValue) {
    const content = (axis === 'x' ? layoutWidthPx : layoutHeightPx) * scaleValue + extraContentPx
    const area = axis === 'x' ? areaWidth : areaHeight
    return [Math.min(0, area - content), Math.max(0, area - content)]
  }

  // JS 侧 ref 变化 → 排队一次 setData（异步被原生消费）
  for (const axis of ['x', 'y', 'scale']) {
    watch(refs[axis], (value) => {
      queue.push({ axis, value: Number(value) })
    }, { flush: 'sync' })
  }

  function applyOne() {
    const item = queue.shift()
    if (!item) return null
    if (item.axis === 'scale') {
      const [lo, hi] = scaleBounds()
      native.scale = clampNum(item.value, lo, hi)
      stats.scaleEvents += 1
      handlers.onScale?.({ detail: { scale: native.scale, x: native.x, y: native.y } })
      return item
    }
    const [lo, hi] = nativeRange(item.axis, native.scale)
    native[item.axis] = clampNum(item.value, lo, hi)
    stats.changeEvents += 1
    handlers.onMove?.({
      detail: {
        x: item.axis === 'x' ? native.x : native.x,
        y: item.axis === 'y' ? native.y : native.y,
        source: Math.abs(native[item.axis] - item.value) > 1e-9 ? 'out-of-bounds' : '',
      },
    })
    return item
  }

  return {
    native,
    stats,
    queueLength: () => queue.length,
     setScaleBounds: (min, max) => { vp_min = min; vp_max = max },
    applyOne,
  }
}

function mockUniViewport(viewport) {
  globalThis.uni = {
     upx2px: (value) => (Number(value) || 0) * 0.5,
    createSelectorQuery: () => {
      const query = {
        in() { return query },
        select() { return query },
        boundingClientRect(callback) { query.callback = callback; return query },
        exec() { query.callback?.({ width: viewport.width, height: viewport.height }) },
      }
      return query
    },
  }
}

function sevenMatchLayout() {
  const match = (id, roundNum, matchIndex, nextMatchId = '') => ({ id, roundNum, matchIndex, nextMatchId })
  return buildKnockoutBracketLayout([
    match('r1-0', 1, 0, 'r2-0'),
    match('r1-1', 1, 1, 'r2-0'),
    match('r1-2', 1, 2, 'r2-1'),
    match('r1-3', 1, 3, 'r2-1'),
    match('r2-0', 2, 0, 'final'),
    match('r2-1', 2, 1, 'final'),
    match('final', 3, 0),
  ])
}

async function setupScene({ viewport }) {
  mockUniViewport(viewport)
  const layout = ref(sevenMatchLayout())
  const vp = useKnockoutBracketViewport(layout)
  const sim = createMovableViewSim({
    refs: { x: vp.x, y: vp.y, scale: vp.scale },
    handlers: { onMove: vp.handleMove, onScale: vp.handleScale },
    areaWidth: viewport.width,
    areaHeight: viewport.height,
    layoutWidthPx: layout.value.width * 0.5,
    layoutHeightPx: layout.value.height * 0.5,
  })
  sim.setScaleBounds(() => vp.minScale.value, () => vp.maxScale.value)
  await vp.setDefaultView()
  await nextTick()
  return { vp, sim }
}

function countRefWrites(refValue) {
  let count = 0
  watch(refValue, () => { count += 1 }, { flush: 'sync' })
  return () => count
}

function touchStart(vp) {
  vp.handleTouchStart({ touches: [{ clientX: 100, clientY: 200 }, { clientX: 150, clientY: 260 }] })
}

function pinchStep(vp, scale, x, y) {
  vp.handleScale({ detail: { scale, x, y } })
  vp.handleMove({ detail: { x, y, source: 'touch' } })
}

function release(vp) {
  vp.handleTouchEnd({ touches: [] })
}

async function pump(sim, { maxIterations = 200, quietNeeded = 25 } = {}) {
  let quiet = 0
  let iterations = 0
  let changeEvents = 0
  while (iterations < maxIterations) {
    iterations += 1
    const before = sim.stats.changeEvents
    sim.applyOne()
    changeEvents = sim.stats.changeEvents
    await nextTick()
    await new Promise((resolve) => setTimeout(resolve, 0))
    if (sim.queueLength() === 0 && sim.stats.changeEvents === before) quiet += 1
    else quiet = 0
    if (quiet >= quietNeeded) break
  }
  return { iterations, quiet, changeEvents }
}

// 视口 375x280：总览比例 = 375/573 ≈ 0.6545，且 408*0.6545=267 ≤ 280，
// 即总览态同时触发「横向总览锁定 + 纵向锁定」——抖动高发配置。
const VIEWPORT = { width: 375, height: 280 }

describe('bracket viewport scale stability (movable-view 交互模拟)', () => {
  afterEach(() => {
    delete globalThis.uni
  })

  it('双指缩放期间不回写 scale-value / x / y（不让 JS 与原生手势打架）', async () => {
    const { vp } = await setupScene({ viewport: VIEWPORT })
    const countScale = countRefWrites(vp.scale)
    const countX = countRefWrites(vp.x)
    const countY = countRefWrites(vp.y)

    touchStart(vp)
    pinchStep(vp, 0.68, -6, -3)
    await nextTick()
    pinchStep(vp, 0.66, -8, -4)
    await nextTick()
    pinchStep(vp, 0.64, -10, -5)
    await nextTick()

    expect(countScale()).toBe(0)
    expect(countX()).toBe(0)
    expect(countY()).toBe(0)
  })

  it('松手后状态收敛：-1→0 翻转的回声不再触发新的翻转（无手指也会停）', async () => {
    const { vp, sim } = await setupScene({ viewport: VIEWPORT })
    touchStart(vp)
     pinchStep(vp, 0.68, -6, -3)
     pinchStep(vp, 0.66, -8, -4)
     pinchStep(vp, 0.655, -10, -5)
     release(vp)
    const result = await pump(sim)
    expect(result.iterations).toBeLessThan(60)
    expect(vp.scale.value).toBeCloseTo(vp.minScale.value, 3)
    expect(vp.x.value).toBe(0)
    expect(vp.y.value).toBe(0)
    expect(sim.native.x).toBe(0)
    expect(sim.native.y).toBe(0)
  })

  it('松手后 scale 意图值与原生实际值同步，按钮缩放从真实档位起算', async () => {
    const { vp, sim } = await setupScene({ viewport: VIEWPORT })
    touchStart(vp)
    pinchStep(vp, 0.75, -20, -6)
    pinchStep(vp, 0.8, -26, -8)
    pinchStep(vp, 0.88, -30, -9)
    release(vp)
    await pump(sim)

    expect(vp.scale.value).toBeCloseTo(0.88, 3)
    expect(sim.native.scale).toBeCloseTo(0.88, 3)

    await vp.zoomOut()
    await pump(sim)
    expect(vp.scale.value).toBeCloseTo(0.775, 3)
    expect(sim.native.scale).toBeCloseTo(0.775, 3)
  })
})
