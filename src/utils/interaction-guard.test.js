import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { useDelayedTapGate, useActionLock } from '@/utils/interaction-guard'
import { ref } from 'vue'

describe('useDelayedTapGate', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should initially be non-interactive when source is false', () => {
    const source = ref(false)
    const { interactive } = useDelayedTapGate(source)
    expect(interactive.value).toBe(false)
  })

  it('should become interactive after delay when source becomes true', async () => {
    const source = ref(false)
    const { interactive } = useDelayedTapGate(source, 200)
    expect(interactive.value).toBe(false)

    source.value = true
    await vi.advanceTimersByTimeAsync(100)
    expect(interactive.value).toBe(false) // still waiting

    await vi.advanceTimersByTimeAsync(150)
    expect(interactive.value).toBe(true) // delay elapsed
  })

  it('should reset to false when source becomes false before delay', async () => {
    const source = ref(false)
    const { interactive } = useDelayedTapGate(source, 200)

    source.value = true
    await vi.advanceTimersByTimeAsync(100)
    source.value = false

    await vi.advanceTimersByTimeAsync(200)
    expect(interactive.value).toBe(false) // never became interactive
  })

  it('should clear timer on rapid toggle', async () => {
    const source = ref(false)
    const { interactive } = useDelayedTapGate(source, 200)

    source.value = true
    await vi.advanceTimersByTimeAsync(100)
    source.value = false
    await vi.advanceTimersByTimeAsync(50)
    source.value = true
    await vi.advanceTimersByTimeAsync(50)
    expect(interactive.value).toBe(false) // timer reset, hasn't hit 200ms from last toggle
  })
})

describe('useActionLock', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('should begin and auto-release after default duration', async () => {
    const { locked, begin } = useActionLock(100)
    expect(locked.value).toBe(false)

    const result = begin()
    expect(result).toBe(true)
    expect(locked.value).toBe(true)

    await vi.advanceTimersByTimeAsync(150)
    expect(locked.value).toBe(false)
  })

  it('should reject second begin while locked', () => {
    const { locked, begin } = useActionLock(500)
    expect(begin()).toBe(true)
    expect(locked.value).toBe(true)
    expect(begin()).toBe(false)
  })

  it('should manually release before timeout', () => {
    const { locked, begin, release } = useActionLock(500)
    begin()
    expect(locked.value).toBe(true)
    release()
    expect(locked.value).toBe(false)
  })

  it('should keep locked indefinitely with Infinity duration', () => {
    const { locked, begin, release } = useActionLock(Infinity)
    begin()
    expect(locked.value).toBe(true)
    release()
    expect(locked.value).toBe(false)
  })

  it('should run async action with auto-lock and release', async () => {
    const { locked, run } = useActionLock()
    let executed = false

    const promise = run(async () => {
      executed = true
      return 'result'
    })

    expect(locked.value).toBe(true)
    const result = await promise
    expect(result).toBe('result')
    expect(executed).toBe(true)
    expect(locked.value).toBe(false)
  })

  it('should skip second run while locked', async () => {
    const { locked, run } = useActionLock(500)
    let firstDone = false

    const p1 = run(async () => {
      await new Promise((resolve) => setTimeout(resolve, 1000))
      firstDone = true
    })

    expect(locked.value).toBe(true)

    await run(async () => {
      // should be skipped
    })

    expect(firstDone).toBe(false) // second run was skipped, first still running
    expect(locked.value).toBe(true)
  })
})
