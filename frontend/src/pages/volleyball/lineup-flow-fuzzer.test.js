import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment, getRegisteredOnLoadHandler } from './fuzzer-env'

// Setup global mocks before importing useScoreboard
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

import { setRegisteredOnLoadHandler } from './fuzzer-env'

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

import { effectScope } from 'vue'
import { useScoreboard } from './composables/useScoreboard'
import { auditStateInvariants } from './scoreboard-fuzzer'
import {
  normalizeMatchState,
  swapMatchStateSides,
  cloneCourt,
  cloneLiberoSetup,
  cloneLiberoRuntime,
  loadMatchState,
  saveMatchState,
  createEmptyLiberoRuntime,
} from './match-state'
import {
  shouldUseLocalRecoveryCache,
  computeRecoveredGameNo,
  buildRecoveredCacheFromRecord,
  shouldSeedEntryDraftFromRemoteConfig,
} from './score-recovery'
import { mkdirSync, writeFileSync } from 'node:fs'
import path from 'node:path'

/**
 * 跨局填写流混沌测试：走【真实】的 finishGame → goToNextLineup → 存储 →
 * 首发页决策链（score-recovery 纯函数）→ 草稿变更（换边/改发球/自由人继承）→
 * 确认上场 → 记分牌恢复 的完整生命周期，并随机插入"回退重填"恢复路径。
 * 此前记分 fuzzer 的局间为手动补丁，该层零覆盖（2026-09-17 深审的结构性
 * 发现，见问题报告 §5.8.3）。
 *
 * 运行：FUZZ_FLOW_ITERS=8 FUZZ_FLOW_SEED=<long> FUZZ_FLOW_OUT=<dir> \
 *   npx vitest run src/pages/volleyball/lineup-flow-fuzzer.test.js
 */

function makePrng(seed) {
  let state = seed >>> 0 || 1
  return {
    seed,
    next() {
      state = (state * 1664525 + 1013904223) >>> 0
      return state / 0x100000000
    },
    int(min, max) {
      return min + Math.floor(this.next() * (max - min + 1))
    },
    bool(p = 0.5) {
      return this.next() < p
    },
    choice(arr) {
      return arr[this.int(0, arr.length - 1)]
    },
  }
}

function generateTeam(prefix) {
  const members = []
  for (let i = 1; i <= 9; i++) {
    members.push({
      id: `${prefix}_${i}`,
      name: `${prefix}员${i}`,
      jerseyNumber: i,
      captain: i === 1,
      libero: i === 7 || i === 8,
    })
  }
  return { id: `team_${prefix}`, name: `战队${prefix}`, members }
}

const LEFT_START = ['A_4', 'A_3', 'A_2', 'A_5', 'A_6', 'A_1']
const RIGHT_START = ['B_4', 'B_3', 'B_2', 'B_5', 'B_6', 'B_1']

function buildInitialCachedState(matchId, initialLineup) {
  return normalizeMatchState({
    matchId,
    displaySideSwapped: false,
    screenLeftParticipantSide: 'left',
    currentGameNo: 1,
    leftScore: 0,
    rightScore: 0,
    leftGameWins: 0,
    rightGameWins: 0,
    gameScores: [],
    serveSide: initialLineup.serveSide,
    currentGameStartServeSide: initialLineup.serveSide,
    leftTimeouts: 2,
    rightTimeouts: 2,
    leftCourt: cloneCourt(initialLineup.leftCourt),
    rightCourt: cloneCourt(initialLineup.rightCourt),
    baseLeftCourt: cloneCourt(initialLineup.leftCourt),
    baseRightCourt: cloneCourt(initialLineup.rightCourt),
    startingLeftCourt: cloneCourt(initialLineup.leftCourt),
    startingRightCourt: cloneCourt(initialLineup.rightCourt),
    draftLeftCourt: cloneCourt(initialLineup.leftCourt),
    draftRightCourt: cloneCourt(initialLineup.rightCourt),
    leftLiberoSetup: cloneLiberoSetup(initialLineup.leftLiberoSetup),
    rightLiberoSetup: cloneLiberoSetup(initialLineup.rightLiberoSetup),
    leftLiberoRuntime: createEmptyLiberoRuntime(),
    rightLiberoRuntime: createEmptyLiberoRuntime(),
    leftCaptainMemberId: 'A_1',
    rightCaptainMemberId: 'B_1',
    draftServeSide: initialLineup.serveSide,
    lineupReady: true,
    matchEnded: false,
  })
}

function extractSnapshot(sb) {
  return {
    screenLeftParticipantSide: sb.screenLeftParticipantSide.value,
    leftCourt: cloneCourt(sb.leftCourt.value),
    rightCourt: cloneCourt(sb.rightCourt.value),
    leftLiberoSetup: cloneLiberoSetup(sb.leftLiberoSetup.value),
    rightLiberoSetup: cloneLiberoSetup(sb.rightLiberoSetup.value),
  }
}

async function runFlowIteration(iterSeed) {
  const rnd = makePrng(iterSeed)
  const matchId = `match_flow_${iterSeed}`
  const bestOf = 3
  const leftTeam = generateTeam('A')
  const rightTeam = generateTeam('B')
  const rules = { bestOf, gamesToWin: 2, pointsToWin: 25, decidingPointsToWin: 15, enableDeuce: true, capPoint: 99 }
  const initialLineup = {
    leftCourt: [...LEFT_START],
    rightCourt: [...RIGHT_START],
    leftLiberoSetup: { pairIndexes: [2, 3], libero1Id: 'A_7', libero2Id: rnd.bool() ? 'A_8' : '' },
    rightLiberoSetup: { pairIndexes: [2, 3], libero1Id: 'B_7', libero2Id: rnd.bool() ? 'B_8' : '' },
    serveSide: rnd.choice(['left', 'right']),
  }

  const record = { iterSeed, matchId, games: [], failures: [] }
  const fail = (message) => record.failures.push(message)

  saveMatchState(matchId, buildInitialCachedState(matchId, initialLineup))

  const { request } = await import('@/utils/request')
  request.mockImplementation((url) => {
    if (url.includes('/record')) {
      return Promise.resolve({
        tournamentId: 't_flow',
        matchId,
        ...rules,
        left: leftTeam,
        right: rightTeam,
        gameScores: [],
        events: [],
      })
    }
    return Promise.resolve({})
  })

  const onLoadOptions = {
    tournamentId: 't_flow',
    matchId,
    leftName: leftTeam.name,
    rightName: rightTeam.name,
    bestOf: '3',
    gamesToWin: '2',
    pointsToWin: '25',
    decidingPointsToWin: '15',
    enableDeuce: '1',
    capPoint: '99',
    lockToken: 'mock-token',
  }

  let scope = effectScope()
  let sb = scope.run(() => useScoreboard())

  async function enterScoreboard(extraOptions = {}) {
    // 重走真实页面进入：新实例构造时注册新的 onLoad 处理器，取最新的。
    // 先清掉旧实例遗留的定时器（对应产品代码 onUnload 的冲刷定时器清理）
    scope.stop()
    vi.clearAllTimers()
    scope = effectScope()
    sb = scope.run(() => useScoreboard())
    const handler = getRegisteredOnLoadHandler()
    if (handler) {
      await handler({ ...onLoadOptions, ...extraOptions })
    }
    vi.advanceTimersByTime(300)
  }

  // 首次进入（此时处理器由上一次 useScoreboard() 构造注册）
  {
    const handler = getRegisteredOnLoadHandler()
    if (handler) {
      await handler(onLoadOptions)
    }
    vi.advanceTimersByTime(300)
  }

  const gameServes = [ // 记录每局【确认后】的开局发球方（参赛方视角）
    initialLineup.serveSide === 'left' ? 'left' : 'right',
  ]
  let guard = 0
  while (!sb.matchEnded.value && guard++ < 12) {
    const gameNo = sb.currentGameNo.value
    const gameScreenLeft = sb.screenLeftParticipantSide.value // 本局进入时的屏侧
    const target = gameNo === bestOf ? rules.decidingPointsToWin : rules.pointsToWin
    const gameRecord = { gameNo, rallies: 0, undoBursts: 0, reentries: 0 }

    let inner = 0
    while (!sb.matchEnded.value && !sb.isTransitioningToNextGame.value && inner++ < 400) {
      const roll = rnd.next()
      if (roll < 0.85) {
        sb.addScore(rnd.choice(['left', 'right']))
        vi.advanceTimersByTime(200)
        gameRecord.rallies++
      } else if (roll < 0.93) {
        if (sb.historyStack.value.length > 0) {
          sb.undo()
          vi.advanceTimersByTime(250)
          gameRecord.undoBursts++
        }
      } else {
        // 回退重填：模拟真实页面的状态持久化后，以 resumeFromScoreboard=1
        // 重新进入首发页（禁止自动回记分牌），比分与站位必须完整保留
        const before = {
          leftScore: sb.leftScore.value,
          rightScore: sb.rightScore.value,
          leftCourt: [...sb.leftCourt.value],
        }
        await enterScoreboard({ resumeFromScoreboard: '1' })
        const after = {
          leftScore: sb.leftScore.value,
          rightScore: sb.rightScore.value,
          leftCourt: [...sb.leftCourt.value],
        }
        if (
          before.leftScore !== after.leftScore ||
          before.rightScore !== after.rightScore ||
          JSON.stringify(before.leftCourt) !== JSON.stringify(after.leftCourt)
        ) {
          fail(`回退重填后比分/站位未保留: ${JSON.stringify({ before, after })}`)
        }
        gameRecord.reentries++
      }
      if (sb.matchEnded.value || sb.isTransitioningToNextGame.value) break
      if (sb.finalGameSideSwitchPending.value) {
        if (rnd.bool(0.2)) {
          sb.keepCurrentDisplaySide()
        } else {
          sb.confirmDisplaySideSwitch()
        }
        vi.advanceTimersByTime(200)
      }
    }

    gameServes.push(sb.currentGameStartServeSide.value)

    if (!sb.matchEnded.value) {
      const preTransition = {
        screenLeft: sb.screenLeftParticipantSide.value,
        startServe: sb.currentGameStartServeSide.value,
        serveSide: sb.serveSide.value,
        gameNo: sb.currentGameNo.value,
      }
      expect(sb.isTransitioningToNextGame.value).toBe(true)
      // 真实跨局过渡：2 秒倒计时触发 goToNextLineup（此前被 fuzzer 绕过的路径）
      await vi.advanceTimersByTimeAsync(2200)

      const cached = normalizeMatchState(loadMatchState(matchId))
      expect(cached.lineupReady).toBe(false)
      expect(cached.currentGameNo).toBe(gameNo + 1)
      // 开局发球权逐局交替（FIVB 规则）：必须在【参赛方视角】断言——
      // 局间换边后屏幕坐标翻转，屏幕侧数值表现为重复而非交替
      // （真实对局 639755b1 印证：R 先发→L 先发，两局屏幕值均为 right）
      const participantServe = cached.screenLeftParticipantSide === 'right'
        ? (cached.draftServeSide === 'left' ? 'right' : 'left')
        : cached.draftServeSide
      // 引擎内部交替一致性：新局开局发球方（参赛方）应为上一局开局发球方的对侧。
      // 基线取引擎自身的过渡前/过渡缓存值，与测试注入的草稿变更无关。
      const prevParticipant = preTransition.screenLeft === 'right'
        ? (preTransition.startServe === 'left' ? 'right' : 'left')
        : preTransition.startServe
      if (participantServe === prevParticipant) {
        const msg = `开局发球方未交替: 前局(参赛方)=${prevParticipant} 新局(参赛方)=${participantServe} ` +
          `过渡前[screenLeft=${preTransition.screenLeft} startServe=${preTransition.startServe}] ` +
          `cached[screenLeft=${cached.screenLeftParticipantSide} draftServeSide=${cached.draftServeSide}]`
        console.error('[FLOW-BUG]', msg)
        fail(msg)
      }
      // 自由人绑定随【队伍】继承：局间换边后左列应是原右队（B）的绑定，
      // 屏侧标记翻转（swapMatchStateSides 语义，§5.1.1 修复保护的归属关系）
      const expectedScreenLeft = gameScreenLeft === 'left' ? 'right' : 'left'
      const expectedLeftSetup = gameScreenLeft === 'left'
        ? initialLineup.rightLiberoSetup : initialLineup.leftLiberoSetup
      const expectedRightSetup = gameScreenLeft === 'left'
        ? initialLineup.leftLiberoSetup : initialLineup.rightLiberoSetup
      if (
        cached.leftLiberoSetup.libero1Id !== expectedLeftSetup.libero1Id ||
        cached.rightLiberoSetup.libero1Id !== expectedRightSetup.libero1Id ||
        cached.screenLeftParticipantSide !== expectedScreenLeft
      ) {
        fail(`过渡后归属异常: 左列1=${cached.leftLiberoSetup.libero1Id} 右列1=${cached.rightLiberoSetup.libero1Id} ` +
          `screenLeft=${cached.screenLeftParticipantSide}（期望 左列1=${expectedLeftSetup.libero1Id} ` +
          `右列1=${expectedRightSetup.libero1Id} screenLeft=${expectedScreenLeft}）`)
      }

      // 首发页决策链（与 lineup.vue loadMatch 相同的组合顺序，纯函数复现）
      const recordMock = {
        bestOf: 3, gamesToWin: 2, pointsToWin: 25, decidingPointsToWin: 15,
        enableDeuce: true, capPoint: 99, status: 1,
        gameScores: [], events: [], lineupSnapshots: [],
        left: leftTeam, right: rightTeam,
      }
      const requestedGameNo = computeRecoveredGameNo(recordMock, cached)
      expect(requestedGameNo).toBe(gameNo + 1)
      // 正常局间链路：本地过渡缓存应被采用（这是绑定继承的载体）
      expect(shouldUseLocalRecoveryCache(recordMock, cached, true)).toBe(true)
      // 若缓存被拒而走服务器重建：0 异常批次重建缓存应触发远端配置种入
      const rebuilt = buildRecoveredCacheFromRecord(recordMock, requestedGameNo)
      expect(shouldSeedEntryDraftFromRemoteConfig(rebuilt, requestedGameNo)).toBe(true)

      // 草稿变更（首发页里的各种填法）
      let draftState = cached
      if (rnd.bool(0.3)) {
        draftState = { ...draftState, ...swapMatchStateSides(draftState) }
      }
      if (rnd.bool(0.4)) {
        draftState = {
          ...draftState,
          draftServeSide: draftState.draftServeSide === 'left' ? 'right' : 'left',
        }
      }
      // 确认上场（buildCurrentLineupState 非恢复态语义）
      const confirmed = normalizeMatchState({
        ...draftState,
        leftCourt: cloneCourt(draftState.draftLeftCourt),
        rightCourt: cloneCourt(draftState.draftRightCourt),
        baseLeftCourt: cloneCourt(draftState.draftLeftCourt),
        baseRightCourt: cloneCourt(draftState.draftRightCourt),
        startingLeftCourt: cloneCourt(draftState.draftLeftCourt),
        startingRightCourt: cloneCourt(draftState.draftRightCourt),
        serveSide: draftState.draftServeSide,
        currentGameStartServeSide: draftState.draftServeSide,
        leftLiberoRuntime: createEmptyLiberoRuntime(),
        rightLiberoRuntime: createEmptyLiberoRuntime(),
        leftScore: 0,
        rightScore: 0,
        leftTimeouts: 2,
        rightTimeouts: 2,
        lineupReady: true,
        finalGameSideSwitchPending: false,
        finalGameSideSwitchHandled: false,
      })
      saveMatchState(matchId, confirmed)

      await enterScoreboard()
      expect(sb.lineupReady.value).toBe(true)
      expect(sb.currentGameNo.value).toBe(gameNo + 1)
      const confirmedParticipantServe = sb.screenLeftParticipantSide.value === 'right'
        ? (sb.currentGameStartServeSide.value === 'left' ? 'right' : 'left')
        : sb.currentGameStartServeSide.value
      gameServes.push(confirmedParticipantServe)
      const snap = extractSnapshot(sb)
      const anomalies = auditStateInvariants(snap, { leftTeam, rightTeam, rules }, {
        step: 0,
        phase: `after-game${gameNo}-transition`,
      })
      if (anomalies.length) {
        fail(`game${gameNo} 过渡后记分牌不变式违规: ${JSON.stringify(anomalies.map((x) => x.message))} ` +
          `[诊断] screenLeft=${snap.screenLeftParticipantSide} 左0=${snap.leftCourt[0]} 右0=${snap.rightCourt[0]} ` +
          `cached.screenLeft=${cached.screenLeftParticipantSide}`)
      }
      gameRecord.nextGameServe = sb.currentGameStartServeSide.value
    }
    record.games.push(gameRecord)
  }

  record.totalGames = sb.currentGameNo.value
  record.matchEnded = sb.matchEnded.value
  record.winner = sb.winnerName.value
  scope.stop()
  return record
}

describe('跨局填写流混沌（真实 goToNextLineup 生命周期）', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.clearAllTimers()
    vi.clearAllMocks()
    vi.useRealTimers()
    if (typeof globalThis.gc === 'function') globalThis.gc()
  })

  it('runs lineup flow chaos across games with recovery paths', async () => {
    const iterCount = process.env.FUZZ_FLOW_ITERS ? parseInt(process.env.FUZZ_FLOW_ITERS, 10) : 6
    const baseSeed = process.env.FUZZ_FLOW_SEED ? parseInt(process.env.FUZZ_FLOW_SEED, 10) : 20260917
    const journalDir = process.env.FUZZ_FLOW_OUT || null

    const allRecords = []
    const allFailures = []
    for (let i = 0; i < iterCount; i++) {
      const iterSeed = baseSeed + i * 7919
      try {
        const record = await runFlowIteration(iterSeed)
        allRecords.push(record)
      } catch (err) {
        const stack = String(err?.stack || '').split('\n').slice(0, 4).join(' | ')
        allFailures.push(`iter#${i}(seed=${iterSeed}) 异常: ${err?.message} @ ${stack}`)
      }
    }
    for (const r of allRecords) {
      for (const f of r.failures) {
        allFailures.push(`iter#${r.iterSeed}: ${f}`)
      }
    }

    if (journalDir) {
      try {
        mkdirSync(journalDir, { recursive: true })
        writeFileSync(
          path.join(journalDir, `lineup-flow-${Date.now()}.json`),
          JSON.stringify({ suite: 'lineup-flow-fuzzer', baseSeed, iters: iterCount, records: allRecords, failures: allFailures }, null, 2),
          'utf8',
        )
      } catch (_) { /* 忽略落盘失败 */ }
    }

    console.log(`跨局填写流混沌完成: iters=${iterCount}`)
    for (const r of allRecords) {
      console.log(`  [flow] seed=${r.iterSeed} games=${JSON.stringify(r.games.map((g) => g.gameNo))} rallies=${JSON.stringify(r.games.map((g) => g.rallies))} ended=${r.matchEnded}`)
    }
    if (allFailures.length) {
      console.error('失败清单:')
      for (const f of allFailures) {
        console.error('  -', f)
      }
    }
    expect(allFailures).toEqual([])
  }, Math.max(180000, 90000 * (process.env.FUZZ_FLOW_ITERS ? parseInt(process.env.FUZZ_FLOW_ITERS, 10) : 6)))
})
