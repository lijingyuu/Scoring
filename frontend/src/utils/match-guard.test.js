import { describe, expect, it, vi, beforeEach } from 'vitest'

// Mock dependencies before importing the module under test
vi.mock('@/store/auth', () => ({
  ensureAuth: vi.fn(() => Promise.resolve()),
}))

vi.mock('./request', () => ({
  request: vi.fn(),
}))

// Mock uni global
const mockShowToast = vi.fn()
const mockNavigateBack = vi.fn()
// 默认实现：立即按「取消/返回」回调，保证 catch 分支不会悬挂 Promise
const mockShowModal = vi.fn((options = {}) => {
  const result = { confirm: false, cancel: true }
  if (typeof options.success === 'function') options.success(result)
  if (typeof options.complete === 'function') options.complete(result)
})

global.uni = {
  showToast: mockShowToast,
  navigateBack: mockNavigateBack,
  showModal: mockShowModal,
}

import { requireMatchOperator } from './match-guard'
import { ensureAuth } from '@/store/auth'
import { request } from './request'

/** 让下一次（且仅下一次）modal 返回「确认/重试」 */
function mockModalConfirmOnce() {
  mockShowModal.mockImplementationOnce((options = {}) => {
    const result = { confirm: true, cancel: false }
    if (typeof options.success === 'function') options.success(result)
    if (typeof options.complete === 'function') options.complete(result)
  })
}

/** 让下一次（且仅下一次）modal 返回「取消/返回」 */
function mockModalCancelOnce() {
  mockShowModal.mockImplementationOnce((options = {}) => {
    const result = { confirm: false, cancel: true }
    if (typeof options.success === 'function') options.success(result)
    if (typeof options.complete === 'function') options.complete(result)
  })
}

describe('requireMatchOperator', () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  // ============================================================
  // missing / blank matchId
  // ============================================================

  it('should return false and show toast when matchId is empty string', async () => {
    const result = await requireMatchOperator('')
    expect(result).toBe(false)
    expect(mockShowToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '缺少比赛ID', icon: 'none' })
    )
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  it('should return false and show toast when matchId is undefined', async () => {
    const result = await requireMatchOperator(undefined)
    expect(result).toBe(false)
    expect(mockShowToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '缺少比赛ID' })
    )
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  // ============================================================
  // valid matchId — API returns true
  // ============================================================

  it('should return true when API returns true (creator or referee)', async () => {
    request.mockResolvedValueOnce(true)

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(true)
    expect(ensureAuth).toHaveBeenCalledOnce()
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-test/can-operate', { method: 'GET', silent: true })
    expect(mockShowToast).not.toHaveBeenCalled()
    expect(mockShowModal).not.toHaveBeenCalled()
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  // ============================================================
  // valid matchId — API returns false (non-authorized user)
  // ============================================================

  it('should return false and show toast when API returns false', async () => {
    request.mockResolvedValueOnce(false)

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
    expect(ensureAuth).toHaveBeenCalledOnce()
    expect(mockShowToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '请先录入裁判身份后再开始执裁', icon: 'none' })
    )
    // 无权限不可重试：不弹 modal
    expect(mockShowModal).not.toHaveBeenCalled()
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  // ============================================================
  // API throws (network error / server error)
  // ============================================================

  it('should return false when API call throws and user cancels the retry modal', async () => {
    request.mockRejectedValueOnce(new Error('Network error'))

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
    expect(ensureAuth).toHaveBeenCalledOnce()
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  it('should keep the request silent and show a retryable modal when API call throws', async () => {
    request.mockRejectedValueOnce(new Error('Network error'))

    const result = await requireMatchOperator('m-test')

    // 网络错误由 modal 提示，request 走 silent，避免 toast + modal 双弹
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-test/can-operate', { method: 'GET', silent: true })
    expect(mockShowToast).not.toHaveBeenCalled()
    expect(mockShowModal).toHaveBeenCalledTimes(1)
    expect(mockShowModal).toHaveBeenCalledWith(
      expect.objectContaining({
        title: '网络异常',
        content: '无法确认执裁权限，请检查网络后重试',
        confirmText: '重试',
        cancelText: '返回',
      })
    )
    // 默认 modal mock 走「返回」→ 不可执裁，调用方的 navigateBack 逻辑接管
    expect(result).toBe(false)
  })

  it('should retry and return true when the user confirms and the second request succeeds', async () => {
    request.mockRejectedValueOnce(new Error('Network error'))
    request.mockResolvedValueOnce(true)
    mockModalConfirmOnce()

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(true)
    expect(mockShowModal).toHaveBeenCalledTimes(1)
    expect(request).toHaveBeenCalledTimes(2)
    expect(ensureAuth).toHaveBeenCalledTimes(2)
    expect(mockShowToast).not.toHaveBeenCalled()
  })

  it('should return false when retry fails again and the user finally cancels', async () => {
    request.mockRejectedValueOnce(new Error('Network error'))
    request.mockRejectedValueOnce(new Error('Network error'))
    mockModalConfirmOnce() // 第一次弹窗点「重试」
    mockModalCancelOnce() // 第二次弹窗点「返回」

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
    expect(request).toHaveBeenCalledTimes(2)
    expect(mockShowModal).toHaveBeenCalledTimes(2)
  })

  // ============================================================
  // API returns falsy (null / undefined / 0) — not exactly true
  // ============================================================

  it('should return false when API returns null (not true)', async () => {
    request.mockResolvedValueOnce(null)

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
    expect(mockShowToast).toHaveBeenCalledWith(
      expect.objectContaining({ title: '请先录入裁判身份后再开始执裁' })
    )
  })

  it('should return false when API returns 0 (falsy, not true)', async () => {
    request.mockResolvedValueOnce(0)

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
  })
})
