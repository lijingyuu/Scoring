import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment, setRegisteredOnLoadHandler, getRegisteredOnLoadHandler } from './fuzzer-env'

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
import { saveMatchState, normalizeMatchState, cloneCourt, cloneLiberoSetup, createEmptyLiberoRuntime } from './match-state'

describe('Volleyball FIVB Substitution & Rotation Engine', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  async function createTestMatchScoreboard() {
    const tournamentId = 't_fivb_1'
    const matchId = 'm_fivb_1'

    const leftTeam = {
      name: 'Alpha Team',
      members: [
        { id: 'A_1', name: 'Player 1', jerseyNumber: 1, captain: true },
        { id: 'A_2', name: 'Player 2', jerseyNumber: 2 },
        { id: 'A_3', name: 'Player 3', jerseyNumber: 3 },
        { id: 'A_4', name: 'Player 4', jerseyNumber: 4 },
        { id: 'A_5', name: 'Middle 5', jerseyNumber: 5 }, // 副攻 1
        { id: 'A_6', name: 'Middle 6', jerseyNumber: 6 }, // 副攻 2
        { id: 'A_7', name: 'Libero 7', jerseyNumber: 7, libero: true }, // 自由人
        { id: 'A_10', name: 'Bench 10', jerseyNumber: 10 }, // 替补 1
        { id: 'A_11', name: 'Bench 11', jerseyNumber: 11 }, // 替补 2
      ],
    }

    const rightTeam = {
      name: 'Beta Team',
      members: [
        { id: 'B_1', name: 'P 1', jerseyNumber: 1, captain: true },
        { id: 'B_2', name: 'P 2', jerseyNumber: 2 },
        { id: 'B_3', name: 'P 3', jerseyNumber: 3 },
        { id: 'B_4', name: 'P 4', jerseyNumber: 4 },
        { id: 'B_5', name: 'P 5', jerseyNumber: 5 },
        { id: 'B_6', name: 'P 6', jerseyNumber: 6 },
        { id: 'B_7', name: 'L 7', jerseyNumber: 7, libero: true },
      ],
    }

    // 初始站位：A_5 在 slot 4 (6号位后排), A_6 在 slot 1 (3号位前排)
    // 自由人 A_7 绑定对角副攻 [1, 4]
    const initialLeftCourt = ['A_1', 'A_6', 'A_3', 'A_4', 'A_5', 'A_2']
    const initialRightCourt = ['B_1', 'B_2', 'B_3', 'B_4', 'B_5', 'B_6']

    const cachedState = normalizeMatchState({
      tournamentId,
      matchId,
      screenLeftParticipantSide: 'left',
      leftScore: 0,
      rightScore: 0,
      leftGameWins: 0,
      rightGameWins: 0,
      currentGameNo: 1,
      gameScores: [],
      serveSide: 'left',
      currentGameStartServeSide: 'left',
      leftTimeouts: 2,
      rightTimeouts: 2,
      leftCourt: cloneCourt(initialLeftCourt),
      rightCourt: cloneCourt(initialRightCourt),
      baseLeftCourt: cloneCourt(initialLeftCourt),
      baseRightCourt: cloneCourt(initialRightCourt),
      leftLiberoSetup: {
        pairIndexes: [1, 4],
        libero1Id: 'A_7',
        libero2Id: '',
      },
      rightLiberoSetup: {
        pairIndexes: [],
        libero1Id: '',
        libero2Id: '',
      },
      leftLiberoRuntime: createEmptyLiberoRuntime(),
      rightLiberoRuntime: createEmptyLiberoRuntime(),
      leftCaptainMemberId: 'A_1',
      rightCaptainMemberId: 'B_1',
      lineupReady: true,
      matchEnded: false,
      retiredSide: '',
      winnerName: '',
      historyStack: [],
      matchEvents: [],
      nextEventSeq: 1,
      lastSyncedEventSeq: 0,
    })

    saveMatchState(matchId, cachedState)
    const sb = useScoreboard()

    const { request } = await import('@/utils/request')
    request.mockImplementation((url) => {
      if (url.includes('/record')) {
        return Promise.resolve({
          tournamentId,
          matchId,
          bestOf: 3,
          gamesToWin: 2,
          pointsToWin: 25,
          decidingPointsToWin: 15,
          enableDeuce: true,
          capPoint: 99,
          left: leftTeam,
          right: rightTeam,
          gameScores: [],
          events: [],
        })
      }
      return Promise.resolve({})
    })

    sb.leftTeam.value = leftTeam
    sb.rightTeam.value = rightTeam
    sb.applyState(cachedState)
    sb.lineupReady.value = true
    sb.settleAllLiberoStates()
    vi.advanceTimersByTime(200)
    return { sb, leftTeam, rightTeam }
  }

  it('场景 1: 彻底根治双副攻克隆漏洞 (杜绝被自由人挂起的副攻被选为替补)', async () => {
    const { sb } = await createTestMatchScoreboard()

    // 此时自由人 A_7 替换了 slot 4 上的 A_5 (因为 slot 4 是后排 6 号位)
    expect(sb.leftCourt.value[4]).toBe('A_7')
    // 法理底座 baseLeftCourt[4] 依然是 A_5
    expect(sb.baseLeftCourt.value[4]).toBe('A_5')

    // 检查球员状态机
    expect(sb.getPlayerState('left', 'A_7')).toBe('LIBERO')
    expect(sb.getPlayerState('left', 'A_5')).toBe('SUSPENDED_BY_LIBERO')
    expect(sb.getPlayerState('left', 'A_10')).toBe('BENCH_FREE')

    // 门禁断言：A_5 与 A_7 均不可被选为替补
    expect(sb.canSelectBenchPlayer('left', 'A_5')).toBe(false)
    expect(sb.canSelectBenchPlayer('left', 'A_7')).toBe(false)
    expect(sb.canSelectBenchPlayer('left', 'A_10')).toBe(true)

    // 尝试点击 A_5 选为替补，必须被拦截
    sb.selectBench('left', 'A_5')
    expect(sb.selectedBench.value.memberId).toBe('')

    // 尝试点击自由人 A_7 选为替补，必须被拦截
    sb.selectBench('left', 'A_7')
    expect(sb.selectedBench.value.memberId).toBe('')

    // 推进轮转直到自由人转到前排，验证原进原出，场上无克隆人
    // 当前 slot 4 是自由人 A_7。轮转映射: [3, 0, 1, 4, 5, 2]
    // 轮转 1 次: 4 号位的 A_7 顺时针移动到 3 号位 (slot 3)
    // 轮转 2 次: 3 号位的 A_7 移动到 0 号位 (前排 4 号位) -> 此时必须原进原出，换回 A_5
    sb.rotateCourt('left')
    sb.rotateTeamLiberoRuntime('left')
    sb.settleTeamLibero('left')
    vi.advanceTimersByTime(200)

    sb.rotateCourt('left')
    sb.rotateTeamLiberoRuntime('left')
    sb.settleTeamLibero('left')
    vi.advanceTimersByTime(200)

    // 此时 slot 0 是前排，自由人必须离场，A_5 必须无损回归
    expect(sb.leftCourt.value[0]).toBe('A_5')
    expect(sb.baseLeftCourt.value[0]).toBe('A_5')

    // 场上 6 个位置绝无重复球员
    const seen = new Set(sb.leftCourt.value)
    expect(seen.size).toBe(6)
    expect(sb.leftCourt.value.filter((p) => p === 'A_5').length).toBe(1)
  })

  it('场景 2: FIVB 15.6 槽位通道终生对位死锁验证', async () => {
    const { sb } = await createTestMatchScoreboard()

    // 开局首发: slot 1 为 A_6 (出场通道 track 1), slot 0 为 A_1 (出场通道 track 0)
    expect(sb.baseLeftCourt.value[1]).toBe('A_6')
    expect(sb.baseLeftCourt.value[0]).toBe('A_1')

    // 1. 替补 A_10 首次上场换下 1 号槽位的 A_6
    sb.selectBench('left', 'A_10')
    expect(sb.selectedBench.value.memberId).toBe('A_10')
    sb.handleCourtSlot('left', 1)
    vi.advanceTimersByTime(200)

    expect(sb.baseLeftCourt.value[1]).toBe('A_10')
    expect(sb.leftCourt.value[1]).toBe('A_10')

    // 2. 此时 A_6 坐在替补席，但已被锁定在 track 1 (BENCH_LOCKED)
    expect(sb.getPlayerState('left', 'A_6')).toBe('BENCH_LOCKED')
    expect(sb.canSelectBenchPlayer('left', 'A_6')).toBe(true)

    // 3. 非法对位尝试：尝试把 A_6 换入 0 号槽位 (track 0)
    expect(sb.canPlayerSubstituteSlot('left', 'A_6', 0)).toBe(false)
    sb.selectBench('left', 'A_6')
    sb.handleCourtSlot('left', 0)
    vi.advanceTimersByTime(200)

    // 0 号槽位依然是 A_1，非法跨槽换人被阻断！
    expect(sb.baseLeftCourt.value[0]).toBe('A_1')

    // 4. 合法对位尝试：将 A_6 换回其锁定的 1 号槽位 (track 1)
    expect(sb.canPlayerSubstituteSlot('left', 'A_6', 1)).toBe(true)
    sb.selectBench('left', 'A_6')
    sb.handleCourtSlot('left', 1)
    vi.advanceTimersByTime(200)

    // 成功回场！
    expect(sb.baseLeftCourt.value[1]).toBe('A_6')
    expect(sb.leftCourt.value[1]).toBe('A_6')
  })

  it('场景 3: 自由人代理槽位的原子级复合换人 (FIVB 19.3.2 战术支持)', async () => {
    const { sb } = await createTestMatchScoreboard()

    // 此时 slot 4 站着自由人 A_7 (代理底座的副攻 A_5)
    expect(sb.leftCourt.value[4]).toBe('A_7')
    expect(sb.baseLeftCourt.value[4]).toBe('A_5')

    // 教练派遣替补发球手 A_11 换下后排副攻发球
    expect(sb.canSelectBenchPlayer('left', 'A_11')).toBe(true)
    expect(sb.canPlayerSubstituteSlot('left', 'A_11', 4)).toBe(true)

    sb.selectBench('left', 'A_11')
    sb.handleCourtSlot('left', 4)
    vi.advanceTimersByTime(200)

    // 原子级复合换人结果断言：
    // 1. 底座层持有人变更为 A_11
    expect(sb.baseLeftCourt.value[4]).toBe('A_11')
    // 2. 原副攻 A_5 离开场上，进入 BENCH_LOCKED
    expect(sb.getPlayerState('left', 'A_5')).toBe('BENCH_LOCKED')
    // 3. 换人事件记录了 out: A_5, in: A_11
    const subEvent = sb.matchEvents.value.find((e) => e.type === 'substitution')
    expect(subEvent).toBeDefined()
    expect(subEvent.payload.outMemberId).toBe('A_5')
    expect(subEvent.payload.inMemberId).toBe('A_11')
  })

  it('场景 4: 撤销 (Undo) 时间旅行纯函数级完美复原', async () => {
    const { sb } = await createTestMatchScoreboard()

    const initialCourt = [...sb.leftCourt.value]
    const initialBase = [...sb.baseLeftCourt.value]

    // 执行换人
    sb.selectBench('left', 'A_10')
    sb.handleCourtSlot('left', 0)
    vi.advanceTimersByTime(200)

    expect(sb.baseLeftCourt.value[0]).toBe('A_10')

    // 执行 Undo
    sb.undo()
    vi.advanceTimersByTime(200)

    // 状态完全逆向还原
    expect(sb.leftCourt.value).toEqual(initialCourt)
    expect(sb.baseLeftCourt.value).toEqual(initialBase)
    expect(sb.getPlayerState('left', 'A_10')).toBe('BENCH_FREE')
  })
})
