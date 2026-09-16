import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment, setRegisteredOnLoadHandler } from './fuzzer-env'

// 1. 初始化全局垫片
setupFuzzerEnvironment()

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: vi.fn((cb) => {
    setRegisteredOnLoadHandler(cb)
  }),
  onUnload: vi.fn(),
  onBackPress: vi.fn(),
  onReady: vi.fn(),
  onShow: vi.fn(),
  onHide: vi.fn(),
  redirectTo: vi.fn(),
  showToast: vi.fn(),
}))

vi.mock('@/utils/request', () => ({
  request: vi.fn(() => Promise.resolve({})),
}))

vi.mock('@/composables/useScoreAnnouncer', () => ({
  useScoreAnnouncer: () => ({
    announceScore: vi.fn(),
    isScoreMuted: { value: true },
    toggleScoreMuted: vi.fn(),
  }),
}))

vi.mock('@/store/auth', () => ({
  authState: { user: { id: 'referee_1', name: '裁判员' } },
  guardProfileBeforeAction: vi.fn(() => true),
}))

vi.mock('@/utils/match-guard', () => ({
  requireMatchOperator: vi.fn(() => true),
}))

vi.mock('@/utils/match-lock', () => ({
  acquireMatchLockWithRetry: vi.fn(() => Promise.resolve({ success: true, editable: true, sameSession: true })),
  releaseMatchLock: vi.fn(() => Promise.resolve()),
  startMatchLockHeartbeat: vi.fn(() => () => {}),
  loadMatchLockToken: vi.fn(() => 'mock-token'),
  createMatchLockToken: vi.fn(() => 'mock-token'),
  saveMatchLockToken: vi.fn(),
  clearMatchLockToken: vi.fn(),
  matchLockHeader: vi.fn(() => ({})),
}))

import { useScoreboard } from './composables/useScoreboard'
import { saveMatchState, normalizeMatchState } from './match-state'

/**
 * 决胜局换边 → 撤销 场景回归：
 * 混沌测试 20260916 批次 chunk-000 中 6/100 场 CRITICAL 均源于此路径。
 * 根因：confirmDisplaySideSwitch → swapSides 会原地交换 leftTeam/rightTeam 花名册引用，
 * 但历史快照不含花名册；undo 跨越换边边界后名册与球场错位，
 * 自由人结算/换人校验张冠李戴，后续轮转即克隆出重复球员。
 */
describe('Volleyball Scoreboard Fixes: side switch undo keeps roster-court consistency', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function createMockRoster() {
    const leftMembers = [
      { id: 'A1', name: 'A队1', jerseyNumber: 1, captain: true, libero: false },
      { id: 'A2', name: 'A队2', jerseyNumber: 2, captain: false, libero: false },
      { id: 'A3', name: 'A队3', jerseyNumber: 3, captain: false, libero: false },
      { id: 'A4', name: 'A队4', jerseyNumber: 4, captain: false, libero: false },
      { id: 'A5', name: 'A队5', jerseyNumber: 5, captain: false, libero: false },
      { id: 'A6', name: 'A队6', jerseyNumber: 6, captain: false, libero: false },
      { id: 'A7', name: 'A队7', jerseyNumber: 7, captain: false, libero: true },
    ]
    const rightMembers = [
      { id: 'B1', name: 'B队1', jerseyNumber: 1, captain: true, libero: false },
      { id: 'B2', name: 'B队2', jerseyNumber: 2, captain: false, libero: false },
      { id: 'B3', name: 'B队3', jerseyNumber: 3, captain: false, libero: false },
      { id: 'B4', name: 'B队4', jerseyNumber: 4, captain: false, libero: false },
      { id: 'B5', name: 'B队5', jerseyNumber: 5, captain: false, libero: false },
      { id: 'B6', name: 'B队6', jerseyNumber: 6, captain: false, libero: false },
      { id: 'B7', name: 'B队7', jerseyNumber: 7, captain: false, libero: true },
    ]
    return { leftMembers, rightMembers }
  }

  function memberIds(members) {
    return new Set(members.map((m) => m.id))
  }

  function buildInitialState() {
    return normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 3,
      lineupReady: true,
      leftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      rightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      baseLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      baseRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      startingLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      startingRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      leftLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'A7', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'B7', libero2Id: '' },
      leftCaptainMemberId: 'A1',
      rightCaptainMemberId: 'B1',
    })
  }

  function createScoreboard(initialState) {
    const { leftMembers, rightMembers } = createMockRoster()
    const matchId = 'm_test_side_switch_undo'
    saveMatchState(matchId, initialState)
    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.applyState(initialState)
    return sb
  }

  it('确认换边后场上球员必须全部属于换边后的屏侧名册', () => {
    const sb = createScoreboard(buildInitialState())

    sb.finalGameSideSwitchPending.value = true
    sb.confirmDisplaySideSwitch()

    expect(sb.screenLeftParticipantSide.value).toBe('right')
    const leftIds = memberIds(sb.leftTeam.value.members)
    const rightIds = memberIds(sb.rightTeam.value.members)
    for (const pid of sb.leftCourt.value) {
      expect(leftIds.has(pid)).toBe(true)
    }
    for (const pid of sb.rightCourt.value) {
      expect(rightIds.has(pid)).toBe(true)
    }
  })

  it('换边后名册外球员不可被选为替补（异队/自由人防串线）', () => {
    const sb = createScoreboard(buildInitialState())

    sb.finalGameSideSwitchPending.value = true
    sb.confirmDisplaySideSwitch()

    // 换边后屏幕左侧是 B 队，A 队球员不在左侧名册，任何途径都不得将其作为替补选入
    expect(sb.canSelectBenchPlayer('left', 'A3')).toBe(false)
    expect(sb.canPlayerSubstituteSlot('left', 'A3', 0)).toBe(false)
    // 左侧名册内（B 队）的自由人也不得进入前排槽位
    expect(sb.canSelectBenchPlayer('left', 'B7')).toBe(false)
  })

  it('撤销跨越换边边界后，花名册必须随球场坐标一并还原', () => {
    const sb = createScoreboard(buildInitialState())

    sb.finalGameSideSwitchPending.value = true
    sb.confirmDisplaySideSwitch()
    expect(sb.screenLeftParticipantSide.value).toBe('right')

    sb.undo()

    // 撤销后回到换边前的球场坐标（屏幕左侧是 A 队），屏侧标记与花名册必须同步还原
    expect(sb.screenLeftParticipantSide.value).toBe('left')
    const leftIds = memberIds(sb.leftTeam.value.members)
    const rightIds = memberIds(sb.rightTeam.value.members)
    for (const pid of sb.leftCourt.value) {
      expect(leftIds.has(pid), `左队场上 ${pid} 不在左侧名册中（名册与球场错位）`).toBe(true)
    }
    for (const pid of sb.rightCourt.value) {
      expect(rightIds.has(pid), `右队场上 ${pid} 不在右侧名册中（名册与球场错位）`).toBe(true)
    }
  })

  it('换边→撤销→再换边后，轮转与自由人结算不得产生重复球员', () => {
    const sb = createScoreboard(buildInitialState())

    sb.finalGameSideSwitchPending.value = true
    sb.confirmDisplaySideSwitch()
    sb.undo()
    sb.finalGameSideSwitchPending.value = true
    sb.confirmDisplaySideSwitch()

    // 混沌测试 match_fuzz_5 的直接复现路径：错位状态下轮转会克隆自由人绑定的副攻
    sb.rotateCourt('left')
    sb.rotateCourt('right')

    const courts = [
      ['left', sb.leftCourt.value, memberIds(sb.leftTeam.value.members)],
      ['right', sb.rightCourt.value, memberIds(sb.rightTeam.value.members)],
    ]
    for (const [side, court, ids] of courts) {
      expect(new Set(court).size, `${side} 场上出现重复球员: ${JSON.stringify(court)}`).toBe(6)
      for (const pid of court) {
        expect(ids.has(pid), `${side} 场上 ${pid} 不在该侧名册中`).toBe(true)
      }
    }
  })
})
