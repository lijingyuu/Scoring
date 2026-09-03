import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

vi.mock('./request', () => ({
  request: vi.fn(),
}))

import { request } from './request'
import {
  MATCH_LOCK_TOKEN_HEADER,
  acquireMatchLock,
  createMatchLockToken,
  heartbeatMatchLock,
  matchLockHeader,
  releaseMatchLock,
  startMatchLockHeartbeat,
} from './match-lock'

beforeEach(() => {
  vi.clearAllMocks()
})

describe('createMatchLockToken', () => {
  it('returns a non-empty token', () => {
    expect(typeof createMatchLockToken()).toBe('string')
    expect(createMatchLockToken().length).toBeGreaterThan(0)
  })

  it('returns unique tokens across calls', () => {
    expect(createMatchLockToken()).not.toBe(createMatchLockToken())
  })

  it('falls back to lock_ prefix when crypto.randomUUID is unavailable', () => {
    const originalCrypto = globalThis.crypto
    try {
      Object.defineProperty(globalThis, 'crypto', {
        value: undefined,
        writable: true,
        configurable: true,
      })
      const token = createMatchLockToken()
      expect(token).toMatch(/^lock_[0-9a-z]+_[0-9a-z]+$/)
    } finally {
      Object.defineProperty(globalThis, 'crypto', {
        value: originalCrypto,
        writable: true,
        configurable: true,
      })
    }
  })
})

describe('matchLockHeader', () => {
  it('returns empty object when lockToken is blank', () => {
    expect(matchLockHeader('')).toEqual({})
    expect(matchLockHeader(null)).toEqual({})
    expect(matchLockHeader(undefined)).toEqual({})
  })

  it('returns header keyed by X-Match-Lock-Token', () => {
    expect(matchLockHeader('token-abc')).toEqual({ [MATCH_LOCK_TOKEN_HEADER]: 'token-abc' })
  })
})

describe('acquireMatchLock', () => {
  it('calls POST /lock with lockToken and silent flag', async () => {
    request.mockResolvedValue({ success: true })
    const result = await acquireMatchLock('m-1', 'token-1')
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-1/lock', {
      method: 'POST',
      data: { lockToken: 'token-1' },
      silent: true,
    })
    expect(result).toEqual({ success: true })
  })
})

describe('heartbeatMatchLock', () => {
  it('calls POST /heartbeat with lockToken and silent flag', async () => {
    request.mockResolvedValue({ success: true })
    await heartbeatMatchLock('m-1', 'token-1')
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-1/heartbeat', {
      method: 'POST',
      data: { lockToken: 'token-1' },
      silent: true,
    })
  })
})

describe('releaseMatchLock', () => {
  it('is a no-op when matchId or lockToken is missing', async () => {
    await expect(releaseMatchLock('', 'token')).resolves.toBeUndefined()
    await expect(releaseMatchLock('m-1', '')).resolves.toBeUndefined()
    await expect(releaseMatchLock(null, 'token')).resolves.toBeUndefined()
    expect(request).not.toHaveBeenCalled()
  })

  it('calls POST /release with lockToken', async () => {
    request.mockResolvedValue({})
    await releaseMatchLock('m-1', 'token-1')
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-1/release', {
      method: 'POST',
      data: { lockToken: 'token-1' },
      silent: true,
    })
  })

  it('swallows network errors', async () => {
    request.mockRejectedValue(new Error('network down'))
    await expect(releaseMatchLock('m-1', 'token-1')).resolves.toBeUndefined()
  })
})

describe('startMatchLockHeartbeat', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    vi.clearAllMocks()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('sends heartbeat every 15s and does not call onLost on success', async () => {
    request.mockResolvedValue({ success: true })
    const onLost = vi.fn()
    const stop = startMatchLockHeartbeat('m-1', 'token-1', onLost)

    await vi.advanceTimersByTimeAsync(15000)
    expect(request).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-1/heartbeat', {
      method: 'POST',
      data: { lockToken: 'token-1' },
      silent: true,
    })
    expect(onLost).not.toHaveBeenCalled()

    await vi.advanceTimersByTimeAsync(15000)
    expect(request).toHaveBeenCalledTimes(2)

    stop()
  })

  it('does not call onLost when result.editable is true', async () => {
    request.mockResolvedValue({ editable: true })
    const onLost = vi.fn()
    const stop = startMatchLockHeartbeat('m-1', 'token-1', onLost)

    await vi.advanceTimersByTimeAsync(15000)
    expect(onLost).not.toHaveBeenCalled()
    stop()
  })

  it('calls onLost when heartbeat returns success=false', async () => {
    request.mockResolvedValue({ success: false })
    const onLost = vi.fn()
    const stop = startMatchLockHeartbeat('m-1', 'token-1', onLost)

    await vi.advanceTimersByTimeAsync(15000)
    expect(onLost).toHaveBeenCalledTimes(1)
    stop()
  })

  it('does not call onLost on network error and keeps retrying', async () => {
    request.mockRejectedValue(new Error('network'))
    const onLost = vi.fn()
    const stop = startMatchLockHeartbeat('m-1', 'token-1', onLost)

    await vi.advanceTimersByTimeAsync(15000)
    expect(onLost).not.toHaveBeenCalled()
    await vi.advanceTimersByTimeAsync(15000)
    expect(request).toHaveBeenCalledTimes(2)
    expect(onLost).not.toHaveBeenCalled()
    stop()
  })

  it('stop() clears the interval', async () => {
    request.mockResolvedValue({ success: true })
    const stop = startMatchLockHeartbeat('m-1', 'token-1', vi.fn())

    stop()
    await vi.advanceTimersByTimeAsync(45000)
    expect(request).not.toHaveBeenCalled()
  })
})
