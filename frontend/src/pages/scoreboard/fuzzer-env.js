/**
 * 羽毛球记分板无头混沌测试环境垫片
 *
 * 参照排球模块的 pages/volleyball/fuzzer-env.js 裁剪而来（不修改排球目录任何文件）。
 * use-scoreboard-state.js 在运行时真实调用 uni.setStorageSync / getStorageSync /
 * removeStorageSync，本垫片在 Node/Vitest 无头环境下用内存 Map 提供同语义实现，
 * 并补齐少量页面级 no-op 接口，保证真实模块可以在测试进程中直接驱动。
 *
 * 注意：刻意不使用 vi.fn 包装（排球垫片用 vi.fn 会永久累积每次调用的全量状态
 * 实参，长批跑必须手动清理；这里用纯函数 + 每场 clearStorageSync 即可避免）。
 */

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

// 初始化/重置 Node/Vitest 下的无头 uni 全局对象。
// 每次调用都会换上全新的内存 storage，天然实现"单场隔离"。
export function setupFuzzerEnvironment() {
  const memoryStorage = createMemoryStorage()

  const mockUni = {
    getStorageSync: (key) => memoryStorage.getStorageSync(key),
    setStorageSync: (key, val) => memoryStorage.setStorageSync(key, val),
    removeStorageSync: (key) => memoryStorage.removeStorageSync(key),
    clearStorageSync: () => memoryStorage.clearStorageSync(),
    showToast: () => {},
    hideToast: () => {},
    showModal: ({ success } = {}) => {
      if (typeof success === 'function') success({ confirm: true, cancel: false })
    },
    showActionSheet: ({ success } = {}) => {
      if (typeof success === 'function') success({ tapIndex: 0 })
    },
    showLoading: () => {},
    hideLoading: () => {},
    redirectTo: () => {},
    navigateTo: () => {},
    navigateBack: () => {},
    reLaunch: () => {},
    switchTab: () => {},
    getSystemInfoSync: () => ({
      windowWidth: 1280,
      windowHeight: 800,
      screenWidth: 1280,
      screenHeight: 800,
      statusBarHeight: 20,
      platform: 'devtools',
    }),
  }

  globalThis.uni = mockUni

  return {
    storage: memoryStorage,
    uni: mockUni,
  }
}
