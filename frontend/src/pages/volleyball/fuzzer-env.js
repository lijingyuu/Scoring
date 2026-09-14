import { vi } from 'vitest'

// 存储当前注册的 onLoad 回调
let registeredOnLoadHandler = null

export function getRegisteredOnLoadHandler() {
  return registeredOnLoadHandler
}

export function setRegisteredOnLoadHandler(handler) {
  registeredOnLoadHandler = handler
}

// 内存版 UniStorage，用于替代小程序的本地缓存
export function createMemoryStorage() {
  const store = new Map()
  return {
    getStorageSync(key) {
      return store.has(key) ? store.get(key) : ''
    },
    setStorageSync(key, value) {
      store.set(key, value)
    },
    removeStorageSync(key) {
      store.delete(key)
    },
    clearStorageSync() {
      store.clear()
    },
    _raw: store,
  }
}

// 初始化 Node/Vitest 下的无头 uni 全局对象
export function setupFuzzerEnvironment() {
  const memoryStorage = createMemoryStorage()

  const mockUni = {
    getStorageSync: vi.fn((key) => memoryStorage.getStorageSync(key)),
    setStorageSync: vi.fn((key, val) => memoryStorage.setStorageSync(key, val)),
    removeStorageSync: vi.fn((key) => memoryStorage.removeStorageSync(key)),
    clearStorageSync: vi.fn(() => memoryStorage.clearStorageSync()),
    showToast: vi.fn(),
    hideToast: vi.fn(),
    showModal: vi.fn(({ success }) => {
      if (typeof success === 'function') {
        success({ confirm: true, cancel: false })
      }
    }),
    showActionSheet: vi.fn(({ success, itemList }) => {
      if (typeof success === 'function') {
        success({ tapIndex: 0 })
      }
    }),
    showLoading: vi.fn(),
    hideLoading: vi.fn(),
    redirectTo: vi.fn(),
    navigateTo: vi.fn(),
    navigateBack: vi.fn(),
    reLaunch: vi.fn(),
    switchTab: vi.fn(),
    getSystemInfoSync: vi.fn(() => ({
      windowWidth: 1280,
      windowHeight: 800,
      screenWidth: 1280,
      screenHeight: 800,
      statusBarHeight: 20,
      platform: 'devtools',
    })),
  }

  globalThis.uni = mockUni

  return {
    storage: memoryStorage,
    uni: mockUni,
  }
}
