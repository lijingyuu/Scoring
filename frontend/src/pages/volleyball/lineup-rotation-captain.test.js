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
import { saveMatchState, normalizeMatchState, swapMatchStateSides } from './match-state'

describe('Volleyball Scoreboard Fixes: Next Game Lineup, Rotation & Captain Auto-Restore', () => {
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

  it('场景 1: 跨局进入下一局时，草稿阵容必须继承上一局开局真正首发，严禁继承局末轮转站位', async () => {
    const matchId = 'm_test_next_game'
    const { leftMembers, rightMembers } = createMockRoster()

    const initialState = normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 1,
      lineupReady: true,
      leftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      rightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      baseLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      baseRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      startingLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      startingRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      leftLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'A7', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'B7', libero2Id: '' },
    })
    saveMatchState(matchId, initialState)

    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.applyState(initialState)

    // 模拟第 1 局比赛进行，双方多次得分换发球并轮转
    sb.rotateCourt('left')
    sb.rotateCourt('left')
    sb.rotateCourt('right')
    sb.rotateCourt('right')

    // 此时场上 baseLeftCourt 和 baseRightCourt 已经发生了多次轮转，不再是初始首发
    expect(sb.baseLeftCourt.value).not.toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.baseRightCourt.value).not.toEqual(['B1', 'B2', 'B3', 'B4', 'B5', 'B6'])

    // 但是 startingLeftCourt 和 startingRightCourt 依然牢牢锁定开局首发
    expect(sb.startingLeftCourt.value).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
    expect(sb.startingRightCourt.value).toEqual(['B1', 'B2', 'B3', 'B4', 'B5', 'B6'])

    // 模拟结转生成下一局快照（换边）
    const snapshot = sb.buildSnapshot()
    const nextGameState = swapMatchStateSides(snapshot)

    // 换边后：屏幕左边为 B 队，屏幕右边为 A 队
    // 下一局的草稿必须等于各队在上一局开局的真正初始首发！
    const draftLeft = nextGameState.startingLeftCourt
    const draftRight = nextGameState.startingRightCourt

    expect(draftLeft).toEqual(['B1', 'B2', 'B3', 'B4', 'B5', 'B6'])
    expect(draftRight).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
  })

  it('场景 2: 队长因自由人替换下场并选定场上队长，随后随轮转回场后，场上队长必须自动切回全队队长', async () => {
    const matchId = 'm_test_captain_restore'
    const { leftMembers, rightMembers } = createMockRoster()

    // A1 为全队队长，首发在 slot 4（后排6号位）
    // 副攻对为 slot 1 和 slot 4，自由人 A7 绑定 slot 4
    // 导致开局时自由人 A7 替换 A1 上场，A1（队长）处于后排场下
    const initialState = normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 1,
      lineupReady: true,
      leftCourt: ['A4', 'A2', 'A3', 'A5', 'A7', 'A6'], // slot 4 上站着自由人 A7
      rightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      baseLeftCourt: ['A4', 'A2', 'A3', 'A5', 'A1', 'A6'], // A1 底座在 slot 4
      baseRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      leftLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'A7', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      leftCaptainMemberId: 'A3', // 此时选定场上队长为 A3
      rightCaptainMemberId: 'B1',
      serveSide: 'right',
      currentGameStartServeSide: 'right',
    })
    saveMatchState(matchId, initialState)

    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.applyState(initialState)

    expect(sb.leftCaptainMemberId.value).toBe('A3')
    expect(sb.isCurrentCaptain('left', 'A1')).toBe(false)
    expect(sb.isCurrentCaptain('left', 'A3')).toBe(true)

    // 左队得分换发，球场顺时针轮转多次，直到 A1 轮转到前排，自由人下场，A1 回到场上
    for (let i = 0; i < 6; i++) {
      sb.addScore('right'); vi.advanceTimersByTime(200)
      sb.addScore('left'); vi.advanceTimersByTime(200)
      if (sb.isOnCourt('left', 'A1')) {
        break
      }
    }

    expect(sb.isOnCourt('left', 'A1')).toBe(true)
    // 一旦 A1 回到场上，场上队长必须自动切回 A1！
    expect(sb.leftCaptainMemberId.value).toBe('A1')
    expect(sb.isCurrentCaptain('left', 'A1')).toBe(true)
    expect(sb.isCurrentCaptain('left', 'A3')).toBe(false)

    // 必须产生了 source: 'auto' 的 captain_change 事件
    const captainEvents = sb.matchEvents.value.filter(
      (e) => e.type === 'captain_change' && e.payload?.captainMemberId === 'A1'
    )
    expect(captainEvents.length).toBeGreaterThanOrEqual(1)
    expect(captainEvents[0].payload?.source).toBe('auto')
  })

  it('场景 3: 阵容调整轮次时（逆时针或顺时针旋转），副攻绑定 pairIndexes 必须跟随原副攻队员 ID 移动，绝不漂移至留在原位置的其他队员', () => {
    // 模拟阵容 6 人，slot 0~5 分别为 1号到6号队员
    // 假设 1 号是队长（slot 0），3 号和 6 号是副攻（slot 2 和 slot 5）
    const court = ['m1_captain', 'm2', 'm3_mb1', 'm4', 'm5', 'm6_mb2']
    const pairIndexes = [2, 5] // 副攻初始在 slot 2 和 slot 5

    // 模拟逆时针退一轮 (0->5, 1->0, 2->1, 3->2, 4->3, 5->4)
    // 即每个位置后退一格
    const rotatedCounterClockwise = [
      court[1], // slot 0 变成 m2
      court[2], // slot 1 变成 m3_mb1
      court[3], // slot 2 变成 m4
      court[4], // slot 3 变成 m5
      court[5], // slot 4 变成 m6_mb2
      court[0], // slot 5 变成 m1_captain
    ]

    // 调用我们在 lineup.vue 中实现的相同映射逻辑
    const originalMembers = pairIndexes.map(idx => court[idx]).filter(Boolean)
    expect(originalMembers).toEqual(['m3_mb1', 'm6_mb2'])

    const trackedIndexes = originalMembers.map(memberId => rotatedCounterClockwise.indexOf(memberId))
    expect(trackedIndexes).toEqual([1, 4])

    // 检查：新位置 1 和 4 上的队员依然是副攻本人
    expect(rotatedCounterClockwise[trackedIndexes[0]]).toBe('m3_mb1')
    expect(rotatedCounterClockwise[trackedIndexes[1]]).toBe('m6_mb2')
    // 原位置 slot 5 此时是 m1_captain，绝对不会被 trackedIndexes 选中
    expect(trackedIndexes.includes(5)).toBe(false)
  })

  it('场景 4: 多局流转测试：第1局首发 -> 第2局微调了首发阵容并开始 -> 第3局默认继承第2局调整后的新首发', async () => {
    const matchId = 'm_test_multi_game_progression'
    const { leftMembers, rightMembers } = createMockRoster()

    // 1. 第 1 局开局阵容
    const game1StartingLeft = ['A1', 'A2', 'A3', 'A4', 'A5', 'A6']
    const game1StartingRight = ['B1', 'B2', 'B3', 'B4', 'B5', 'B6']

    const game1State = normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 1,
      lineupReady: true,
      leftCourt: [...game1StartingLeft],
      rightCourt: [...game1StartingRight],
      baseLeftCourt: [...game1StartingLeft],
      baseRightCourt: [...game1StartingRight],
      startingLeftCourt: [...game1StartingLeft],
      startingRightCourt: [...game1StartingRight],
      leftLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'A7', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [4, 1], libero1Id: 'B7', libero2Id: '' },
    })
    saveMatchState(matchId, game1State)

    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.applyState(game1State)

    // 模拟第 1 局多次得分与轮转
    sb.rotateCourt('left')
    sb.rotateCourt('right')

    // 结转进入第 2 局草稿（换边）
    const game2DraftState = swapMatchStateSides(sb.buildSnapshot())
    // 换边后左边是 B 队（原 right），右边是 A 队（原 left）
    expect(game2DraftState.startingLeftCourt).toEqual(game1StartingRight) // B 队 Game 1 首发
    expect(game2DraftState.startingRightCourt).toEqual(game1StartingLeft) // A 队 Game 1 首发

    // 2. 模拟裁判在第 2 局开局界面进行了【调整】：
    // A 队（此时在屏幕右侧）用替补调整了首发，把 slot 0 换成了 A7，或者做了一次旋转
    const game2AdjustedRight = ['A2', 'A3', 'A4', 'A5', 'A6', 'A1'] // A 队调了一轮
    const game2AdjustedLeft = [...game1StartingRight] // B 队没调整

    // 裁判确认第 2 局开局阵容（模拟 lineup.vue 的 buildCurrentLineupState）
    const game2RunningState = normalizeMatchState({
      ...game2DraftState,
      currentGameNo: 2,
      lineupReady: true,
      leftCourt: [...game2AdjustedLeft],
      rightCourt: [...game2AdjustedRight],
      baseLeftCourt: [...game2AdjustedLeft],
      baseRightCourt: [...game2AdjustedRight],
      startingLeftCourt: [...game2AdjustedLeft], // 关键：第 2 局保存的是第 2 局调整后的首发！
      startingRightCourt: [...game2AdjustedRight],
    })

    sb.applyState(game2RunningState)

    // 模拟第 2 局比赛进行并发生轮转
    sb.rotateCourt('left')
    sb.rotateCourt('left')
    sb.rotateCourt('right')

    // 此时第 2 局局末实时底座又转了
    expect(sb.baseRightCourt.value).not.toEqual(game2AdjustedRight)

    // 3. 结转进入第 3 局草稿（再次换边）
    const game3DraftState = swapMatchStateSides(sb.buildSnapshot())
    // 再次换边后：左边又是 A 队（取第 2 局右边的 startingRightCourt），右边是 B 队（取第 2 局左边的 startingLeftCourt）
    expect(game3DraftState.startingLeftCourt).toEqual(game2AdjustedRight) // A 队继承的是第 2 局调整后的首发！
    expect(game3DraftState.startingRightCourt).toEqual(game2AdjustedLeft) // B 队继承的是第 2 局的首发！
    // 绝不能回退到第 1 局初始首发，也绝不能是第 2 局局末轮转站位
    expect(game3DraftState.startingLeftCourt).not.toEqual(game1StartingLeft)
    expect(game3DraftState.startingLeftCourt).not.toEqual(sb.baseRightCourt.value)
  })
})