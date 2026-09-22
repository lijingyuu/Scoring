import { describe, expect, it, vi, beforeEach } from 'vitest'

vi.mock('@/utils/request', () => ({
  request: vi.fn(),
  uploadAvatar: vi.fn(),
  setUnauthorizedHandler: vi.fn(),
}))

import { request, uploadAvatar, setUnauthorizedHandler } from '@/utils/request'

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

  it('uploads a temporary avatar before saving the profile', async () => {
    const auth = await loadAuthStore()
    uploadAvatar.mockResolvedValueOnce('https://api.example.com/uploads/avatars/avatar.png')
    request.mockResolvedValueOnce({
      id: 'user-1',
      nickname: '测试用户',
      avatarUrl: 'https://api.example.com/uploads/avatars/avatar.png',
      profileCompleted: true,
    })

    await auth.submitProfile('测试用户', 'wxfile://tmp/avatar.png')

    expect(uploadAvatar).toHaveBeenCalledWith('wxfile://tmp/avatar.png')
    expect(request).toHaveBeenCalledWith('/api/v1/auth/profile', {
      method: 'POST',
      data: {
        nickname: '测试用户',
        avatarUrl: 'https://api.example.com/uploads/avatars/avatar.png',
      },
    })
  })

  it('registers a 401 handler that clears the token and re-logs in', async () => {
    const auth = await loadAuthStore()
    // 模块加载即注册（注册式解法，避免 request.js 与 store 循环依赖）
    expect(setUnauthorizedHandler).toHaveBeenCalledTimes(1)
    const handler = setUnauthorizedHandler.mock.calls[0][0]
    expect(typeof handler).toBe('function')

    auth.authState.token = 'expired-token'
    global.uni.setStorageSync('scoring_token', 'expired-token')
    global.uni.login = vi.fn(({ success }) => success({ code: 'wx-code' }))
    request.mockResolvedValueOnce({ token: 'fresh-token', profileCompleted: true })
    // jsdom 下 window/document 存在会被当成 web dev 模式；隐藏它们模拟小程序运行环境
    vi.stubGlobal('window', undefined)
    vi.stubGlobal('document', undefined)

    try {
      await handler()
    } finally {
      vi.unstubAllGlobals()
    }

    // 先清 token，ensureAuth 才会真正走 uni.login 重登
    expect(global.uni.removeStorageSync).toHaveBeenCalledWith('scoring_token')
    expect(request).toHaveBeenCalledWith('/api/v1/auth/wechat-login', {
      method: 'POST',
      data: { code: 'wx-code' },
      silent: true,
    })
    expect(auth.authState.token).toBe('fresh-token')
    expect(global.uni.getStorageSync('scoring_token')).toBe('fresh-token')
  })
})
