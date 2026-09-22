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
import { request } from '@/utils/request'
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
    // 换边/撤销节流（150/300ms）：真实用户两次点击间隔大于节流窗口后，第二次换边才会执行
    vi.advanceTimersByTime(300)
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

  // 与 addScore 同款的连点节流：双击不得重复消费动作（撤销代价最大，300ms）
  describe('连点节流防护', () => {
    it('撤销在 300ms 节流窗口内连点只退一步', () => {
      const sb = createScoreboard(buildInitialState())
      sb.addScore('left')
      vi.advanceTimersByTime(200)
      sb.addScore('left')
      vi.advanceTimersByTime(200)
      expect(sb.leftScore.value).toBe(2)

      sb.undo()
      expect(sb.leftScore.value).toBe(1)
      sb.undo()
      expect(sb.leftScore.value).toBe(1)

      vi.advanceTimersByTime(300)
      sb.undo()
      expect(sb.leftScore.value).toBe(0)
    })

    it('暂停在 150ms 节流窗口内连点只扣一次', () => {
      const sb = createScoreboard(buildInitialState())
      const before = sb.leftTimeouts.value

      sb.openTimeoutSheet()
      sb.openTimeoutSheet()
      expect(sb.leftTimeouts.value).toBe(before - 1)

      vi.advanceTimersByTime(150)
      sb.openTimeoutSheet()
      expect(sb.leftTimeouts.value).toBe(before - 2)
    })

    it('换边在 150ms 节流窗口内连点只执行一次', () => {
      const sb = createScoreboard(buildInitialState())

      sb.finalGameSideSwitchPending.value = true
      sb.confirmDisplaySideSwitch()
      expect(sb.screenLeftParticipantSide.value).toBe('right')

      // 处于节流窗口内时，即便再次进入换边分支也不得把两边换回去
      sb.finalGameSideSwitchPending.value = true
      sb.confirmDisplaySideSwitch()
      expect(sb.screenLeftParticipantSide.value).toBe('right')
    })
  })

  // 断网/锁过期导致 flush 失败后，必须退避自动重试，而不是等下一次加分才补救
  describe('事件冲刷失败退避重试', () => {
    it('首次 flush 失败后按 1s 退避重试，成功即收敛且不再重排', async () => {
      const sb = createScoreboard(buildInitialState())
      sb.matchId.value = 'm_flush_retry_test'
      request.mockRejectedValueOnce(new Error('network down'))

      sb.addScore('left')
      await vi.advanceTimersByTimeAsync(800) // 防抖到期 → 第一次 flush（失败）
      expect(sb.matchEvents.value.some((e) => e.syncStatus !== 'synced')).toBe(true)

      await vi.advanceTimersByTimeAsync(1000) // 1s 退避后自动重试（成功）
      expect(sb.matchEvents.value.every((e) => e.syncStatus === 'synced')).toBe(true)
    })
  })

  // 任务1：新设备/清缓存后 eventSeq 从 1 重来与库中已有 seq 撞号，后端 409 整批拒绝
  describe('事件序号冲突(409)自愈', () => {
    it('收到 409 后按服务端最大序号重排未同步事件并立即重试成功（只重试一次）', async () => {
      const sb = createScoreboard(buildInitialState())
      sb.matchId.value = 'm_event_seq_conflict'
      // 同一份 mock 跨用例累积调用记录，先清空再断言本次冲刷次数
      request.mockClear()
      request.mockRejectedValueOnce(new Error('事件序号与已有记录冲突，请刷新后重试（服务端最大序号 5）'))

      sb.addScore('left')
      await vi.advanceTimersByTimeAsync(800)
      await vi.advanceTimersByTimeAsync(0)

      const eventCalls = request.mock.calls.filter(([url]) => String(url).includes('/events'))
      expect(eventCalls.length).toBe(2)
      expect(eventCalls[0][1].data.events[0].eventSeq).toBe(1)
      expect(eventCalls[1][1].data.events.every((item) => item.eventSeq > 5)).toBe(true)
      expect(sb.matchEvents.value.every((item) => item.syncStatus === 'synced')).toBe(true)

      // 只重试一次：成功收敛后不再有退避重试
      await vi.advanceTimersByTimeAsync(30000)
      expect(request.mock.calls.filter(([url]) => String(url).includes('/events')).length).toBe(2)
    })
  })

  // 任务2：undo 补的比分快照必须携带补偿水位，后端据此不再渲染被撤销的换人/暂停
  describe('撤销补偿水位(revertToSeq)', () => {
    it('undo 快照携带 revertToSeq = 撤销后应保留的最后一条事件序号', () => {
      const sb = createScoreboard(normalizeMatchState({
        ...buildInitialState(),
        matchEvents: [
          { seq: 1, type: 'lineup_snapshot', gameNo: 3, leftScore: 0, rightScore: 0, serveSide: 'left', payload: { serveSide: 'left' }, syncStatus: 'synced' },
          { seq: 2, type: 'score_snapshot', gameNo: 3, leftScore: 1, rightScore: 0, serveSide: 'left', payload: { reason: 'score' }, syncStatus: 'synced' },
        ],
        nextEventSeq: 3,
        lastSyncedEventSeq: 2,
      }))

      sb.addScore('left')
      sb.undo()

      const undoEvent = sb.matchEvents.value.find(
        (item) => item.type === 'score_snapshot' && item.payload?.reason === 'undo',
      )
      expect(undoEvent).toBeTruthy()
      expect(undoEvent.payload.revertToSeq).toBe(2)
      // 撤销快照自身序号继续单调递增，不会回落到被撤销的水位
      expect(undoEvent.seq).toBeGreaterThan(2)
    })
  })

  // 任务3：完局/退赛落定后必须立即冲刷事件，不能等 800ms 防抖——
  // 迟到的事件会晚于 finish 落地，被服务端拒收且不重试，记录完整性受损
  describe('比赛结束后立即冲刷事件', () => {
    it('完局锁定待结算后立即发出 /events 请求，不等 800ms 防抖', async () => {
      const sb = createScoreboard(buildInitialState())
      sb.matchId.value = 'm_match_end_flush'
      sb.info.value = { ...sb.info.value, gamesToWin: 1 }
      request.mockClear()

      sb.addScore('left') // score_snapshot 进入 800ms 防抖窗口
      sb.finishGame('left') // 末局到手 → 完局锁定，进入待结算

      await vi.advanceTimersByTimeAsync(0)
      const eventCalls = request.mock.calls.filter(([url]) => String(url).includes('/events'))
      expect(sb.matchEnded.value).toBe(true)
      expect(eventCalls.length).toBe(1)
      expect(sb.matchEvents.value.every((item) => item.syncStatus === 'synced')).toBe(true)

      // 防抖窗口到期后不得再补一次重复冲刷
      await vi.advanceTimersByTimeAsync(800)
      expect(request.mock.calls.filter(([url]) => String(url).includes('/events')).length).toBe(1)
    })
  })
})
