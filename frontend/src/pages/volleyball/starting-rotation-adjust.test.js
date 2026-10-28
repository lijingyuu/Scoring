import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment } from './fuzzer-env'

// 1. 初始化全局垫片
setupFuzzerEnvironment()

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: vi.fn(),
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

// 顺时针置换（new[i] = old[perm[i]]，与 rotateCourt 现有置换一致）
const CLOCKWISE_PERMUTE = [3, 0, 1, 4, 5, 2]
const permute = (court, perm) => perm.map((index) => court[index] || '')

describe('Volleyball Scoreboard: 0:0 Starting Rotation Adjust', () => {
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
    ]
    const rightMembers = [
      { id: 'B1', name: 'B队1', jerseyNumber: 1, captain: true, libero: false },
      { id: 'B2', name: 'B队2', jerseyNumber: 2, captain: false, libero: false },
      { id: 'B3', name: 'B队3', jerseyNumber: 3, captain: false, libero: false },
      { id: 'B4', name: 'B队4', jerseyNumber: 4, captain: false, libero: false },
      { id: 'B5', name: 'B队5', jerseyNumber: 5, captain: false, libero: false },
      { id: 'B6', name: 'B队6', jerseyNumber: 6, captain: false, libero: false },
    ]
    return { leftMembers, rightMembers }
  }

  function buildScoreboard({ leftScore = 0, rightScore = 0, liberoPair = [] } = {}) {
    const { leftMembers, rightMembers } = createMockRoster()
    const initialState = normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 1,
      lineupReady: true,
      leftScore,
      rightScore,
      leftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      rightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      baseLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      baseRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      startingLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      startingRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      leftSlotTracks: [0, 1, 2, 3, 4, 5],
      rightSlotTracks: [0, 1, 2, 3, 4, 5],
      leftLiberoSetup: liberoPair.length === 2
        ? { pairIndexes: [...liberoPair], libero1Id: '', libero2Id: '' }
        : { pairIndexes: [], libero1Id: '', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      leftLiberoRuntime: liberoPair.length === 2
        ? { role1SlotIndex: liberoPair[0], role2SlotIndex: liberoPair[1], role1PlayerId: '', role2PlayerId: '' }
        : { role1SlotIndex: -1, role2SlotIndex: -1, role1PlayerId: '', role2PlayerId: '' },
    })
    saveMatchState('m_test_rotation_adjust', initialState)

    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.applyState(initialState)
    return sb
  }

  it('0:0 时顺时针旋转左队：三层数组与首发快照按顺时针置换，并追加 lineup_snapshot 修正帧，右队不动', () => {
    const sb = buildScoreboard()
    expect(sb.canAdjustStartingRotation.value).toBe(true)

    const snapshotBefore = {
      rightCourt: [...sb.rightCourt.value],
      baseRightCourt: [...sb.baseRightCourt.value],
      startingRightCourt: [...sb.startingRightCourt.value],
      rightSlotTracks: [...sb.rightSlotTracks.value],
    }

    sb.rotateStartingLineup('left', 'clockwise')

    // 顺时针置换：new[i] = old[[3,0,1,4,5,2][i]]
    const expected = permute(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'], CLOCKWISE_PERMUTE)
    expect(expected).toEqual(['A4', 'A1', 'A2', 'A5', 'A6', 'A3'])
    expect(sb.leftCourt.value).toEqual(expected)
    expect(sb.baseLeftCourt.value).toEqual(expected)
    expect(sb.startingLeftCourt.value).toEqual(expected)
    expect(sb.leftSlotTracks.value).toEqual(permute([0, 1, 2, 3, 4, 5], CLOCKWISE_PERMUTE))

    // 追加了一条 lineup_snapshot 修正帧
    const adjustEvents = sb.matchEvents.value.filter((item) => item.type === 'lineup_snapshot')
    expect(adjustEvents).toHaveLength(1)
    expect(adjustEvents[0].payload.reason).toBe('starting_rotation_adjust')
    expect(adjustEvents[0].payload.direction).toBe('clockwise')
    expect(adjustEvents[0].payload.side).toBe('left')
    // 修正帧携带 runtime 快照供服务端恢复
    expect(adjustEvents[0].payload.runtime).toBeTruthy()

    // 右队数据不动
    expect(sb.rightCourt.value).toEqual(snapshotBefore.rightCourt)
    expect(sb.baseRightCourt.value).toEqual(snapshotBefore.baseRightCourt)
    expect(sb.startingRightCourt.value).toEqual(snapshotBefore.startingRightCourt)
    expect(sb.rightSlotTracks.value).toEqual(snapshotBefore.rightSlotTracks)

    // 弹窗自动收起逻辑未触发（仍可继续调整）
    expect(sb.canAdjustStartingRotation.value).toBe(true)
  })

  it('自由人绑定随轮次同步旋转：liberoRuntime 槽位与 liberoSetup pairIndexes 均按方向映射', () => {
    // 副攻对初始在 slot 2 / slot 5
    const sb = buildScoreboard({ liberoPair: [2, 5] })

    sb.rotateStartingLineup('left', 'clockwise')

    // 顺时针槽位映射：2->5, 5->4（rotateSlotIndex: {0:1,1:2,2:5,3:0,4:3,5:4}）
    expect(sb.leftLiberoRuntime.value.role1SlotIndex).toBe(5)
    expect(sb.leftLiberoRuntime.value.role2SlotIndex).toBe(4)
    // pairIndexes 跟随移动到 [5, 4]
    expect(sb.leftLiberoSetup.value.pairIndexes).toEqual([5, 4])
  })

  it('逆时针一次等于顺时针五次：两种路径回到同一站位', () => {
    const sbCounter = buildScoreboard()
    const sbClockwise = buildScoreboard()

    sbCounter.rotateStartingLineup('left', 'counterclockwise')
    for (let i = 0; i < 5; i++) {
      sbClockwise.rotateStartingLineup('left', 'clockwise')
    }

    // 逆时针置换：new[i] = old[[1,2,5,0,3,4][i]] = ['A2','A3','A6','A1','A4','A5']
    const expected = ['A2', 'A3', 'A6', 'A1', 'A4', 'A5']
    expect(sbCounter.leftCourt.value).toEqual(expected)
    expect(sbCounter.baseLeftCourt.value).toEqual(expected)
    expect(sbCounter.startingLeftCourt.value).toEqual(expected)
    expect(sbClockwise.leftCourt.value).toEqual(sbCounter.leftCourt.value)
    expect(sbClockwise.baseLeftCourt.value).toEqual(sbCounter.baseLeftCourt.value)
    expect(sbClockwise.startingLeftCourt.value).toEqual(sbCounter.startingLeftCourt.value)
    expect(sbClockwise.leftSlotTracks.value).toEqual(sbCounter.leftSlotTracks.value)
  })

  it('比分 > 0 时入口关闭：canAdjustStartingRotation 为 false，轮次修正不产生任何变化与事件', () => {
    const sb = buildScoreboard({ leftScore: 1 })
    expect(sb.canAdjustStartingRotation.value).toBe(false)

    const eventsBefore = sb.matchEvents.value.length
    sb.rotateStartingLineup('left', 'clockwise')

    expect(sb.leftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.baseLeftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.startingLeftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.leftSlotTracks.value).toEqual([0, 1, 2, 3, 4, 5])
    expect(sb.matchEvents.value).toHaveLength(eventsBefore)
  })

  it('undo 能把轮次修正回滚：courts 与事件列表均回到修正前', () => {
    const sb = buildScoreboard()

    sb.rotateStartingLineup('left', 'clockwise')
    const eventsAfterAdjust = sb.matchEvents.value.length
    expect(eventsAfterAdjust).toBeGreaterThan(0)
    expect(sb.leftCourt.value).toEqual(['A4', 'A1', 'A2', 'A5', 'A6', 'A3'])

    sb.undo()
    vi.advanceTimersByTime(200)

    // courts 回滚到修正前
    expect(sb.leftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.baseLeftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.startingLeftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.leftSlotTracks.value).toEqual([0, 1, 2, 3, 4, 5])
    // 事件列表回滚：修正帧消失（undo 会补一条 score_snapshot 补偿事件）
    const lineupEvents = sb.matchEvents.value.filter((item) => item.type === 'lineup_snapshot')
    expect(lineupEvents).toHaveLength(0)
    expect(sb.matchEvents.value.some((item) => item.payload?.reason === 'undo')).toBe(true)
  })

  it('出现比分后 canAdjustStartingRotation 变 false，弹窗自动收起', () => {
    const sb = buildScoreboard()
    sb.openRotationAdjustSheet()
    expect(sb.isRotationAdjustSheetVisible.value).toBe(true)

    sb.addScore('left')
    vi.advanceTimersByTime(200)

    expect(sb.canAdjustStartingRotation.value).toBe(false)
    expect(sb.isRotationAdjustSheetVisible.value).toBe(false)

    sb.closeRotationAdjustSheet()
  })
})
