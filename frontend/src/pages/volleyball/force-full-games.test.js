import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment } from './fuzzer-env'

// 1. 初始化全局垫片
setupFuzzerEnvironment()

const announcerMocks = vi.hoisted(() => ({
  announceScore: vi.fn(),
}))

vi.mock('@dcloudio/uni-app', () => ({
  onLoad: vi.fn(() => {}),
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
    announceScore: announcerMocks.announceScore,
    isMuted: { value: true },
    toggleMuted: vi.fn(),
    isScoreMuted: { value: true },
    toggleScoreMuted: vi.fn(),
    destroyScoreAnnouncer: vi.fn(),
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
import { saveMatchState, normalizeMatchState, loadMatchState } from './match-state'

/**
 * V32 打满三局（force_full_games）记分板行为：
 * 1) 终场时机：2:0 后不终场，照常进第三局（15 分制决胜局流程）；第三局打完才 matchEnded；
 * 2) 开关关闭：2:0 即终场（既有行为零变化）；
 * 3) 赛点播报抑制：未到最后一局时 2:0 领先方的局点不得播报"赛点"；最后一局恢复正常。
 */
describe('Volleyball Scoreboard: force full games (BO3 打满三局)', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    announcerMocks.announceScore.mockReset()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function createMockRoster() {
    const leftMembers = [
      { id: 'A1', name: 'A队1', jerseyNumber: 1, captain: true },
      { id: 'A2', name: 'A队2', jerseyNumber: 2 },
      { id: 'A3', name: 'A队3', jerseyNumber: 3 },
      { id: 'A4', name: 'A队4', jerseyNumber: 4 },
      { id: 'A5', name: 'A队5', jerseyNumber: 5 },
      { id: 'A6', name: 'A队6', jerseyNumber: 6 },
      { id: 'A7', name: 'A队7', jerseyNumber: 7, libero: true },
    ]
    const rightMembers = [
      { id: 'B1', name: 'B队1', jerseyNumber: 1, captain: true },
      { id: 'B2', name: 'B队2', jerseyNumber: 2 },
      { id: 'B3', name: 'B队3', jerseyNumber: 3 },
      { id: 'B4', name: 'B队4', jerseyNumber: 4 },
      { id: 'B5', name: 'B队5', jerseyNumber: 5 },
      { id: 'B6', name: 'B队6', jerseyNumber: 6 },
      { id: 'B7', name: 'B队7', jerseyNumber: 7, libero: true },
    ]
    return { leftMembers, rightMembers }
  }

  function createScoreboard({ forceFullGames, matchId: customMatchId } = {}) {
    const { leftMembers, rightMembers } = createMockRoster()
    const matchId = customMatchId || ('m_force_full_' + (forceFullGames ? 'on' : 'off'))
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
      leftLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      leftCaptainMemberId: 'A1',
      rightCaptainMemberId: 'B1',
    })
    saveMatchState(matchId, initialState)
    const sb = useScoreboard()
    sb.leftTeam.value = { name: 'A队', members: leftMembers }
    sb.rightTeam.value = { name: 'B队', members: rightMembers }
    sb.info.value = {
      id: 't_force_full',
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 25,
      decidingPointsToWin: 15,
      enableDeuce: true,
      capPoint: 99,
      forceFullGames,
    }
    sb.applyState(initialState)
    sb.lineupReady.value = true
    return sb
  }

  it('打满三局开启：第二局打完后 2:0 不终场，进入第三局决胜局', () => {
    const sb = createScoreboard({ forceFullGames: true })
    sb.currentGameNo.value = 2
    sb.leftGameWins.value = 1
    sb.rightGameWins.value = 0
    sb.leftScore.value = 25
    sb.rightScore.value = 20

    sb.finishGame('left')

    // 2:0 领先但不得终场
    expect(sb.matchEnded.value).toBe(false)
    expect(sb.leftGameWins.value).toBe(2)
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.leftScore.value).toBe(0)
    expect(sb.rightScore.value).toBe(0)
    // 第三局按现有流程等待下一局阵容
    expect(sb.isTransitioningToNextGame.value).toBe(true)
  })

  it('打满三局开启：第三局打完才终场，2:1 定胜负', () => {
    const sb = createScoreboard({ forceFullGames: true })
    sb.currentGameNo.value = 3
    sb.leftGameWins.value = 2
    sb.rightGameWins.value = 0
    sb.leftScore.value = 15
    sb.rightScore.value = 10

    sb.finishGame('left')

    expect(sb.matchEnded.value).toBe(true)
    expect(sb.leftGameWins.value).toBe(3)
    expect(sb.rightGameWins.value).toBe(0)
    expect(sb.winnerName.value).toBe('A队')
  })

  it('打满三局开启：2:1（负方扳回一局）终局按胜局定胜负', () => {
    const sb = createScoreboard({ forceFullGames: true })
    sb.currentGameNo.value = 3
    sb.leftGameWins.value = 2
    sb.rightGameWins.value = 1
    sb.leftScore.value = 12
    sb.rightScore.value = 15

    sb.finishGame('right')

    expect(sb.matchEnded.value).toBe(true)
    expect(sb.leftGameWins.value).toBe(2)
    expect(sb.rightGameWins.value).toBe(2)
    expect(sb.winnerName.value).toBe('B队')
  })

  it('打满三局关闭：2:0 即终场（既有行为零变化）', () => {
    const sb = createScoreboard({ forceFullGames: false })
    sb.currentGameNo.value = 2
    sb.leftGameWins.value = 1
    sb.rightGameWins.value = 0
    sb.leftScore.value = 25
    sb.rightScore.value = 20

    sb.finishGame('left')

    expect(sb.matchEnded.value).toBe(true)
    expect(sb.leftGameWins.value).toBe(2)
    expect(sb.winnerName.value).toBe('A队')
  })

  it('打满三局开启：未到最后一局时 2:0 领先方的局点不播报赛点', () => {
    const sb = createScoreboard({ forceFullGames: true })
    sb.currentGameNo.value = 2
    sb.leftGameWins.value = 2
    sb.rightGameWins.value = 0
    sb.leftScore.value = 23
    sb.rightScore.value = 23
    sb.serveSide.value = 'left'

    sb.addScore('left')

    expect(sb.leftScore.value).toBe(24)
    expect(announcerMocks.announceScore).toHaveBeenCalled()
    const options = announcerMocks.announceScore.mock.calls.at(-1)[3]
    expect(options.isGamePoint).toBe(true)
    expect(options.isMatchPoint).toBe(false)
  })

  it('打满三局关闭：同样局面正常播报赛点（既有行为零变化）', () => {
    const sb = createScoreboard({ forceFullGames: false })
    sb.currentGameNo.value = 2
    sb.leftGameWins.value = 1
    sb.rightGameWins.value = 0
    sb.leftScore.value = 23
    sb.rightScore.value = 23
    sb.serveSide.value = 'left'

    sb.addScore('left')

    const options = announcerMocks.announceScore.mock.calls.at(-1)[3]
    // 赛点与局点互斥：isMatchPoint=true 时 isGamePoint=false（与既有 addScore 契约一致）
    expect(options.isGamePoint).toBe(false)
    expect(options.isMatchPoint).toBe(true)
  })

  it('打满三局开启：最后一局的局点恢复赛点播报', () => {
    const sb = createScoreboard({ forceFullGames: true })
    sb.currentGameNo.value = 3
    sb.leftGameWins.value = 1
    sb.rightGameWins.value = 1
    sb.leftScore.value = 13
    sb.rightScore.value = 13
    sb.serveSide.value = 'left'

    sb.addScore('left')

    const options = announcerMocks.announceScore.mock.calls.at(-1)[3]
    expect(options.isGamePoint).toBe(false)
    expect(options.isMatchPoint).toBe(true)
  })

  /** 场景 1a：2:0 过渡窗口内撤销——状态回退自洽、终止挂起的下一局跳转、可正常重打 */
  it('打满三局开启：2:0 过渡窗口内撤销，回退到第二局赛点前且可继续重打', async () => {
    const sb = createScoreboard({ forceFullGames: true, matchId: 'm_ff_undo_window' })
    sb.currentGameNo.value = 2
    sb.leftGameWins.value = 1
    sb.rightGameWins.value = 0
    sb.leftScore.value = 24
    sb.rightScore.value = 20
    sb.serveSide.value = 'left'
    // 种入「最后一分之前」的历史快照（等价 addScore 前的 pushHistory）
    sb.historyStack.value.push(sb.buildSnapshot())

    sb.addScore('left') // 25:20 → 第二局结束，2:0 进入过渡窗口
    expect(sb.matchEnded.value).toBe(false)
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.isTransitioningToNextGame.value).toBe(true)
    expect(sb.gameScores.value).toHaveLength(1)

    sb.undo() // 过渡窗口内撤销
    expect(sb.currentGameNo.value).toBe(2)
    expect(sb.leftScore.value).toBe(24)
    expect(sb.rightScore.value).toBe(20)
    expect(sb.leftGameWins.value).toBe(1)
    expect(sb.gameScores.value).toHaveLength(0)
    expect(sb.matchEnded.value).toBe(false)
    // 撤销契约：终止过渡锁与挂起的下一局阵容跳转
    expect(sb.isTransitioningToNextGame.value).toBe(false)
    await vi.advanceTimersByTimeAsync(2500)
    expect(uni.redirectTo).not.toHaveBeenCalled()

    // 可正常重打：重新得分直到再次 2:0 过渡（finishGame 完局后比分重置为第三局 0:0）
    sb.addScore('left')
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.leftGameWins.value).toBe(2)
    expect(sb.leftScore.value).toBe(0)
    expect(sb.isTransitioningToNextGame.value).toBe(true)
  })

  /** 场景 1b：第三局进行中撤销跨局回退——历史栈跨过局边界后状态自洽、可重打 */
  it('打满三局开启：第三局进行中撤销跨局回退到第二局赛点前，状态自洽可重打', async () => {
    const sb = createScoreboard({ forceFullGames: true, matchId: 'm_ff_undo_cross_game' })
    // 构造「第三局进行中」恢复态（等价跨局接力后的记分页）：历史栈保留第二局赛点前快照
    sb.currentGameNo.value = 3
    sb.leftGameWins.value = 2
    sb.rightGameWins.value = 0
    sb.leftScore.value = 0
    sb.rightScore.value = 0
    sb.lineupReady.value = true
    sb.serveSide.value = 'left'
    sb.historyStack.value.push(sb.buildSnapshot()) // 第三局 0:0 锚点
    const game2Snapshot = sb.buildSnapshot()
    game2Snapshot.currentGameNo = 2
    game2Snapshot.leftGameWins = 1
    game2Snapshot.rightGameWins = 0
    game2Snapshot.leftScore = 24
    game2Snapshot.rightScore = 20
    game2Snapshot.gameScores = []
    sb.historyStack.value.push(game2Snapshot) // 第二局 24:20（跨局边界外旧快照）

    sb.addScore('left') // 第三局 1:0
    expect(sb.leftScore.value).toBe(1)
    expect(sb.currentGameNo.value).toBe(3)

    await vi.advanceTimersByTimeAsync(310)
    sb.undo() // 回退第三局最后一分 → 0:0
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.leftScore.value).toBe(0)

    await vi.advanceTimersByTimeAsync(310)
    sb.undo() // 跨局回退 → 第二局 24:20（1:0）
    expect(sb.currentGameNo.value).toBe(2)
    expect(sb.leftScore.value).toBe(24)
    expect(sb.rightScore.value).toBe(20)
    expect(sb.leftGameWins.value).toBe(1)
    expect(sb.rightGameWins.value).toBe(0)
    expect(sb.gameScores.value).toHaveLength(0)
    expect(sb.matchEnded.value).toBe(false)
    expect(sb.isTransitioningToNextGame.value).toBe(false)

    // 跨局撤销后无残留跳转，可正常重打（25:20 再次完局 → 第三局 0:0 过渡）
    await vi.advanceTimersByTimeAsync(2200)
    expect(uni.redirectTo).not.toHaveBeenCalled()
    sb.addScore('left')
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.leftGameWins.value).toBe(2)
    expect(sb.isTransitioningToNextGame.value).toBe(true)
  })

  /** 场景 4：换设备恢复——本地无缓存，forceFullGames 经 /record 重取，恢复后仍按强制打满执行 */
  it('打满三局开启：换设备无本地缓存恢复，forceFullGames 经 /record 重取并照常强制打满', async () => {
    const matchId = 'm_ff_restore_new_device'
    const { leftMembers, rightMembers } = createMockRoster()
    // 换设备：本地无缓存
    expect(loadMatchState(matchId)).toBeNull()

    request.mockResolvedValueOnce({
      tournamentId: 't_force_full',
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 25,
      decidingPointsToWin: 15,
      enableDeuce: true,
      capPoint: 99,
      forceFullGames: true,
      left: { id: 'left', name: 'A队', members: leftMembers },
      right: { id: 'right', name: 'B队', members: rightMembers },
    })

    const sb = useScoreboard()
    sb.matchId.value = matchId
    sb.tournamentId.value = 't_force_full'
    sb.pageQuery.value = { tournamentId: 't_force_full', matchId }
    await sb.loadMatch()

    // 开关来自服务端 /record，不依赖本地缓存
    expect(sb.info.value.forceFullGames).toBe(true)
    expect(sb.leftTeam.value.members).toHaveLength(7)
    // 无本地缓存 → 转阵容页重建
    expect(uni.redirectTo).toHaveBeenCalled()

    // 阵容确认回到记分页：恢复第二局进行中状态（1:0、24:20）
    sb.applyState(normalizeMatchState({
      screenLeftParticipantSide: 'left',
      currentGameNo: 2,
      leftGameWins: 1,
      rightGameWins: 0,
      lineupReady: true,
      leftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      rightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      baseLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      baseRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      startingLeftCourt: ['A1', 'A2', 'A3', 'A4', 'A5', 'A6'],
      startingRightCourt: ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'],
      leftLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      rightLiberoSetup: { pairIndexes: [], libero1Id: '', libero2Id: '' },
      leftCaptainMemberId: 'A1',
      rightCaptainMemberId: 'B1',
    }))
    sb.leftScore.value = 24
    sb.rightScore.value = 20
    sb.serveSide.value = 'left'

    // 恢复后仍按强制打满执行：2:0 不终场，进入第三局
    sb.addScore('left')
    expect(sb.matchEnded.value).toBe(false)
    expect(sb.currentGameNo.value).toBe(3)
    expect(sb.isTransitioningToNextGame.value).toBe(true)
  })
})
