import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
}))

import { request } from '@/utils/request'

function createUniMock() {
  const storage = new Map()
  return {
    getSystemInfoSync: vi.fn(() => ({ uniPlatform: 'mp-weixin' })),
    getStorageSync: vi.fn((key) => storage.get(key) || ''),
    setStorageSync: vi.fn((key, value) => storage.set(key, value)),
    removeStorageSync: vi.fn((key) => storage.delete(key)),
    showToast: vi.fn(),
    login: vi.fn(),
  }
}

async function loadAuthStore() {
  vi.resetModules()
  global.uni = createUniMock()
  return import('./auth.js')
}

describe('auth profile editor', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it('opens the manual profile editor without waiting for profile refresh', async () => {
    const auth = await loadAuthStore()
    auth.authState.token = 'token-1'
    global.uni.setStorageSync('scoring_token', 'token-1')
    request.mockReturnValueOnce(new Promise(() => {}))

    const promise = auth.openProfileEditor()
    await Promise.resolve()

    expect(auth.authState.popupVisible).toBe(true)
    expect(request).toHaveBeenCalledWith('/api/v1/users/me', { method: 'GET', silent: true })

    await expect(promise).resolves.toBeUndefined()
  })
})
