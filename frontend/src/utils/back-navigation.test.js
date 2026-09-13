import { describe, it, expect, vi, beforeEach } from 'vitest'

const mockNavigateBack = vi.fn()
const mockSwitchTab = vi.fn()
const mockReLaunch = vi.fn()
const mockRedirectTo = vi.fn()

global.uni = {
  navigateBack: mockNavigateBack,
  switchTab: mockSwitchTab,
  reLaunch: mockReLaunch,
  redirectTo: mockRedirectTo,
}

let mockStack = []

global.getCurrentPages = () => mockStack

import {
  HOME_TAB_URL,
  canNavigateBack,
  getPageStack,
  navigateBackOrHome,
  navigateHome,
  resetNavigationLock,
  resolveLogicalParent,
} from './back-navigation'

function stackOf(...routes) {
  return routes.map((route) => ({ route, options: {} }))
}

describe('back-navigation', () => {
  beforeEach(() => {
    vi.clearAllMocks()
    resetNavigationLock()
    mockStack = []
  })

  describe('getPageStack / canNavigateBack', () => {
    it('returns empty stack and cannot go back when stack has only the entry page', () => {
      mockStack = stackOf('pages/tournament/detail')
      expect(getPageStack()).toHaveLength(1)
      expect(canNavigateBack()).toBe(false)
    })

    it('can go back when there is a page beneath the current one', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      expect(canNavigateBack()).toBe(true)
    })
  })

  describe('navigateHome', () => {
    it('uses switchTab to the home tab by default', () => {
      navigateHome()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })

    it('falls back to reLaunch when switchTab fails (non-tab url)', () => {
      mockSwitchTab.mockImplementationOnce(({ fail }) => fail({}))
      navigateHome('/pages/tournament/detail')
      expect(mockReLaunch).toHaveBeenCalledWith({ url: '/pages/tournament/detail' })
    })
  })

  describe('navigateBackOrHome — stack above bottom', () => {
    it('navigates back when the stack has a previous page', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      navigateBackOrHome()
      expect(mockNavigateBack).toHaveBeenCalledWith(
        expect.objectContaining({ delta: 1 })
      )
      expect(mockSwitchTab).not.toHaveBeenCalled()
      expect(mockRedirectTo).not.toHaveBeenCalled()
    })

    it('passes custom delta through to navigateBack', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail', 'pages/tournament/teams')
      navigateBackOrHome({ delta: 2 })
      expect(mockNavigateBack).toHaveBeenCalledWith(
        expect.objectContaining({ delta: 2 })
      )
    })

    it('still lands on home when navigateBack fails unexpectedly (stack timing race)', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      mockNavigateBack.mockImplementationOnce(({ fail }) => fail({}))
      navigateBackOrHome()
      expect(mockNavigateBack).toHaveBeenCalled()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })
  })

  describe('navigateBackOrHome — stack bottom (share entry)', () => {
    it('bracket: redirects to tournament detail with id', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: { id: '42' } }]
      navigateBackOrHome()
      expect(mockNavigateBack).not.toHaveBeenCalled()
      expect(mockRedirectTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/tournament/detail?id=42' })
      )
    })

    it('groups: redirects to tournament detail with id', () => {
      mockStack = [{ route: 'pages/tournament/groups', options: { id: '7' } }]
      navigateBackOrHome()
      expect(mockRedirectTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/tournament/detail?id=7' })
      )
    })

    it('bracket without id: falls back to home', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: {} }]
      navigateBackOrHome()
      expect(mockRedirectTo).not.toHaveBeenCalled()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })

    it.each([
      ['pages/tournament/individual-record'],
      ['pages/tournament/team-record'],
      ['pages/tournament/relay-record'],
      ['pages/volleyball/record'],
    ])('%s with loaded type: redirects to the schedule page', (route) => {
      mockStack = [{ route, options: { tournamentId: '9', matchId: '3' } }]
      navigateBackOrHome({ context: { tournamentId: '9', tournamentType: 1 } })
      expect(mockRedirectTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/tournament/groups?id=9' })
      )
    })

    it('record page with knockout type redirects to bracket', () => {
      mockStack = [{ route: 'pages/tournament/team-record', options: { tournamentId: '9' } }]
      navigateBackOrHome({ context: { tournamentId: '9', tournamentType: 0 } })
      expect(mockRedirectTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/tournament/bracket?id=9' })
      )
    })

    it('record page with url id but type not loaded yet: degrades to detail page', () => {
      mockStack = [{ route: 'pages/volleyball/record', options: { tournamentId: '9', matchId: '3' } }]
      navigateBackOrHome()
      expect(mockRedirectTo).toHaveBeenCalledWith(
        expect.objectContaining({ url: '/pages/tournament/detail?id=9' })
      )
    })

    it('record page without any id: falls back to home', () => {
      mockStack = [{ route: 'pages/tournament/relay-record', options: { matchId: '3' } }]
      navigateBackOrHome({ context: { tournamentType: 0 } })
      expect(mockRedirectTo).not.toHaveBeenCalled()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })

    it('unmapped route (e.g. detail): falls back to home', () => {
      mockStack = [{ route: 'pages/tournament/detail', options: { id: '1' } }]
      navigateBackOrHome()
      expect(mockRedirectTo).not.toHaveBeenCalled()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })

    it('redirectTo failure falls back to home', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: { id: '42' } }]
      mockRedirectTo.mockImplementationOnce(({ fail }) => fail({}))
      navigateBackOrHome()
      expect(mockSwitchTab).toHaveBeenCalledWith(
        expect.objectContaining({ url: HOME_TAB_URL })
      )
    })

    it('resolver throwing is swallowed and falls back to home', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: undefined }]
      navigateBackOrHome()
      expect(mockSwitchTab).toHaveBeenCalled()
    })
  })

  describe('chain termination — every mapped route reaches home within 6 hops', () => {
    it('walks the PARENT_MAP until the home tab', () => {
      const startPages = [
        { route: 'pages/tournament/bracket', options: { id: '1' } },
        { route: 'pages/tournament/groups', options: { id: '2' } },
        { route: 'pages/tournament/individual-record', options: { tournamentId: '3', matchId: '1' }, context: { tournamentId: '3', tournamentType: 0 } },
        { route: 'pages/tournament/team-record', options: { tournamentId: '4', matchId: '1' }, context: { tournamentId: '4', tournamentType: 1 } },
        { route: 'pages/tournament/relay-record', options: { tournamentId: '5', matchId: '1' }, context: { tournamentId: '5', tournamentType: 2 } },
        { route: 'pages/volleyball/record', options: { tournamentId: '6', matchId: '1' }, context: { tournamentId: '6', tournamentType: 1 } },
      ]

      for (const start of startPages) {
        vi.clearAllMocks()
        let current = { ...start }
        let hops = 0
        while (hops < 6) {
          resetNavigationLock() // 逐跳重置连点锁，模拟用户每次转场后再点击
          mockStack = [current]
          navigateBackOrHome({ context: current.context || null })
          hops += 1
          const redirectUrl = mockRedirectTo.mock.calls.at(-1)?.[0]?.url
          if (!redirectUrl) break // landed on home via switchTab
          const id = new URL(redirectUrl, 'https://x').searchParams.get('id')
          if (redirectUrl.startsWith('/pages/tournament/detail')) {
            current = { route: 'pages/tournament/detail', options: { id } }
          } else if (redirectUrl.startsWith('/pages/tournament/bracket')) {
            current = { route: 'pages/tournament/bracket', options: { id } }
          } else if (redirectUrl.startsWith('/pages/tournament/groups')) {
            current = { route: 'pages/tournament/groups', options: { id } }
          } else {
            throw new Error('unexpected redirect url: ' + redirectUrl)
          }
        }
        // 链条必须在 6 跳内以 switchTab 回首页收尾
        expect(mockSwitchTab).toHaveBeenCalledWith(
          expect.objectContaining({ url: HOME_TAB_URL })
        )
      }
    })
  })

  describe('double-tap guard (transition lock)', () => {
    beforeEach(() => {
      vi.useFakeTimers()
    })

    afterEach(() => {
      vi.useRealTimers()
    })

    it('ignores repeated calls during the transition window instead of failing over to home', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      navigateBackOrHome()
      vi.advanceTimersByTime(200) // 仍在转场动画期内
      navigateBackOrHome() // 连点第二次：平台导航锁会 fail，应被窗口拦截
      expect(mockNavigateBack).toHaveBeenCalledTimes(1)
      expect(mockSwitchTab).not.toHaveBeenCalled()
    })

    it('stack-bottom redirectTo also protected from double-tap', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: { id: '42' } }]
      navigateBackOrHome()
      vi.advanceTimersByTime(200)
      navigateBackOrHome()
      expect(mockRedirectTo).toHaveBeenCalledTimes(1)
      expect(mockSwitchTab).not.toHaveBeenCalled()
    })

    it('works again once the window has elapsed', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      navigateBackOrHome()
      vi.advanceTimersByTime(700) // 超过窗口期
      mockStack = stackOf('pages/index/index', 'pages/tournament/teams')
      navigateBackOrHome()
      expect(mockNavigateBack).toHaveBeenCalledTimes(2)
    })

    it('ignored calls do not extend the window', () => {
      mockStack = stackOf('pages/index/index', 'pages/tournament/detail')
      navigateBackOrHome()
      vi.advanceTimersByTime(150)
      navigateBackOrHome() // 被忽略，且不重新计时
      vi.advanceTimersByTime(500) // 距首次发起已超过窗口
      mockStack = stackOf('pages/index/index', 'pages/tournament/teams')
      navigateBackOrHome()
      expect(mockNavigateBack).toHaveBeenCalledTimes(2)
    })
  })

  describe('resolveLogicalParent', () => {
    it('returns null on empty stack', () => {
      expect(resolveLogicalParent()).toBeNull()
    })

    it('把组别透传到赛程页返回 URL', () => {
      mockStack = [{ route: 'pages/tournament/individual-record', options: { tournamentId: 't-1', divisionId: 'd-2' } }]
      expect(resolveLogicalParent({ tournamentId: 't-1', tournamentType: 1 })).toEqual({
        type: 'page',
        url: '/pages/tournament/groups?id=t-1&divisionId=d-2',
      })
    })

    it('优先取页面 options 的组别，其次取 context 的组别', () => {
      mockStack = [{ route: 'pages/tournament/individual-record', options: { tournamentId: 't-1' } }]
      expect(resolveLogicalParent({ tournamentId: 't-1', tournamentType: 0, divisionId: 'd-9' })).toEqual({
        type: 'page',
        url: '/pages/tournament/bracket?id=t-1&divisionId=d-9',
      })
    })

    it('无组别时返回 URL 与改动前一致', () => {
      mockStack = [{ route: 'pages/tournament/team-record', options: { tournamentId: 't-5' } }]
      expect(resolveLogicalParent({ tournamentId: 't-5', tournamentType: 1 })).toEqual({
        type: 'page',
        url: '/pages/tournament/groups?id=t-5',
      })
    })

    it('exposes the resolved page target for mapped routes', () => {
      mockStack = [{ route: 'pages/tournament/bracket', options: { id: '8' } }]
      expect(resolveLogicalParent()).toEqual({
        type: 'page',
        url: '/pages/tournament/detail?id=8',
      })
    })
  })
})
