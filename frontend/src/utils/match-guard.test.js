import { describe, expect, it, vi, beforeEach, afterEach } from 'vitest'

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

global.uni = {
  showToast: mockShowToast,
  navigateBack: mockNavigateBack,
}

import { requireMatchOperator } from './match-guard'
import { ensureAuth } from '@/store/auth'
import { request } from './request'

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
    expect(request).toHaveBeenCalledWith('/api/v1/matches/m-test/can-operate', { method: 'GET' })
    expect(mockShowToast).not.toHaveBeenCalled()
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
    expect(mockNavigateBack).not.toHaveBeenCalled()
  })

  // ============================================================
  // API throws (network error / server error)
  // ============================================================

  it('should return false when API call throws', async () => {
    request.mockRejectedValueOnce(new Error('Network error'))

    const result = await requireMatchOperator('m-test')

    expect(result).toBe(false)
    expect(ensureAuth).toHaveBeenCalledOnce()
    expect(mockNavigateBack).not.toHaveBeenCalled()
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
