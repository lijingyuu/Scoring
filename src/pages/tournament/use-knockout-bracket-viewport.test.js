import { describe, expect, it } from 'vitest'
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
