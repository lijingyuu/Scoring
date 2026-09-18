import { describe, it, expect } from 'vitest'
import os from 'node:os'
import path from 'node:path'
import {
  createPRNG,
  generateBadmintonScenario,
  createBadmintonScoreboard,
  simulateBadmintonMatch,
  runBadmintonFuzzerBatch,
} from './badminton-fuzzer'

describe('Badminton Scoreboard Headless Fuzzer', () => {
  it('runs single smoke match with zero critical anomalies', async () => {
    const prng = createPRNG(20260918)
    const scenario = generateBadmintonScenario(prng, 1)
    const result = await simulateBadmintonMatch(scenario, prng)

    expect(result.hasCritical).toBe(false)
    expect(result.actionHistory.length).toBeGreaterThan(5)
    expect(result.finalState.matchEnded).toBe(true)
  })

  it('guarantees 100% deterministic reproducibility with same seed', async () => {
    const seed = 20260999
    const prng1 = createPRNG(seed)
    const scenario1 = generateBadmintonScenario(prng1, 1)
    const result1 = await simulateBadmintonMatch(scenario1, prng1)

    const prng2 = createPRNG(seed)
    const scenario2 = generateBadmintonScenario(prng2, 1)
    const result2 = await simulateBadmintonMatch(scenario2, prng2)

    expect(result1.actionHistory).toEqual(result2.actionHistory)
    expect(result1.finalState.leftScore).toBe(result2.finalState.leftScore)
    expect(result1.finalState.rightScore).toBe(result2.finalState.rightScore)
    expect(result1.finalState.leftGameWins).toBe(result2.finalState.leftGameWins)
    expect(result1.finalState.rightGameWins).toBe(result2.finalState.rightGameWins)
    expect(result1.finalState.matchEnded).toBe(result2.finalState.matchEnded)
  })

  it('verifies deciding game side switch prompt logic', () => {
    const scenario = {
      matchId: 'test_deciding',
      rules: { bestOf: 3, gamesToWin: 2, pointsToWin: 21, enableDeuce: true, capPoint: 30 },
      leftTeam: 'A',
      rightTeam: 'B',
      initialServeSide: 'left',
    }
    const scoreboard = createBadmintonScoreboard(scenario)

    // 模拟前两局 1:1 进入第 3 局（决胜局）
    scoreboard.currentGameNo.value = 3
    scoreboard.leftGameWins.value = 1
    scoreboard.rightGameWins.value = 1

    // 加分到 10 分，尚未达到换边门槛 11
    for (let i = 0; i < 10; i++) {
      scoreboard.addScore('left')
    }
    expect(scoreboard.isFinalGameSideSwitchPromptActive.value).toBe(false)

    // 第 11 分触发决胜局换边弹窗
    scoreboard.addScore('left')
    expect(scoreboard.leftScore.value).toBe(11)
    expect(scoreboard.isFinalGameSideSwitchPromptActive.value).toBe(true)

    // 弹窗未处理前，尝试加分必须被拦截
    const blocked = scoreboard.addScore('left')
    expect(blocked).toBe(false)
    expect(scoreboard.leftScore.value).toBe(11)

    // 选择换边
    scoreboard.handleFinalGameSideSwitch(true)
    expect(scoreboard.isFinalGameSideSwitchPromptActive.value).toBe(false)
    // 换边后原左队(11分)变成右队(11分)
    expect(scoreboard.rightScore.value).toBe(11)
    expect(scoreboard.leftScore.value).toBe(0)
  })

  it('verifies deep undo integrity across score and side switch', () => {
    const scenario = {
      matchId: 'test_undo',
      rules: { bestOf: 1, gamesToWin: 1, pointsToWin: 21, enableDeuce: true, capPoint: 30 },
      leftTeam: 'TeamA',
      rightTeam: 'TeamB',
      initialServeSide: 'left',
    }
    const scoreboard = createBadmintonScoreboard(scenario)

    scoreboard.addScore('left') // 1:0
    scoreboard.addScore('right') // 1:1
    scoreboard.switchSides() // 换边 -> TeamB 1:1 TeamA

    expect(scoreboard.leftTeam.value).toBe('TeamB')
    expect(scoreboard.rightTeam.value).toBe('TeamA')
    expect(scoreboard.sidesSwapped.value).toBe(true)

    // 撤销换边
    scoreboard.undo()
    expect(scoreboard.leftTeam.value).toBe('TeamA')
    expect(scoreboard.rightTeam.value).toBe('TeamB')
    expect(scoreboard.sidesSwapped.value).toBe(false)

    // 继续撤销得分
    scoreboard.undo() // 1:0
    expect(scoreboard.leftScore.value).toBe(1)
    expect(scoreboard.rightScore.value).toBe(0)
    scoreboard.undo() // 0:0
    expect(scoreboard.leftScore.value).toBe(0)
    expect(scoreboard.rightScore.value).toBe(0)
    expect(scoreboard.historyStack.value.length).toBe(0)
  })

  it('rejects invalid side inputs with zero state drift (real module guard, 调研 §7.5)', () => {
    const scoreboard = createBadmintonScoreboard({
      matchId: 'test_side_guard',
      rules: { bestOf: 3, gamesToWin: 2, pointsToWin: 21, enableDeuce: true, capPoint: 30 },
      leftTeam: 'A',
      rightTeam: 'B',
      initialServeSide: 'left',
    })

    scoreboard.addScore('left')
    scoreboard.addScore('right')
    const before = JSON.stringify(scoreboard.buildSnapshot())

    // 开上帝模式使 adjustScore 的前置守卫放行，从而单独检验侧别守卫本身
    scoreboard.isGodMode.value = true
    expect(scoreboard.addScore('middle')).toBe(false)
    expect(scoreboard.addScore('LEFT')).toBe(false)
    expect(scoreboard.addScore(undefined)).toBe(false)
    expect(scoreboard.addScore(123)).toBe(false)
    expect(scoreboard.adjustScore('left ', 1)).toBe(false)
    expect(scoreboard.adjustScore(null, -1)).toBe(false)
    scoreboard.isGodMode.value = false

    // 拒绝必须无副作用：全量快照逐字段一致，serveSide 未被非法字符串污染
    expect(JSON.stringify(scoreboard.buildSnapshot())).toBe(before)
    expect(['left', 'right']).toContain(scoreboard.serveSide.value)

    // 守卫不影响合法路径
    expect(scoreboard.addScore('left')).toBe(true)
    expect(scoreboard.leftScore.value).toBe(2)
  })

  it('runs minimal 5-match smoke batch and verifies summary journal', async () => {
    const matchesCount = Number(process.env.FUZZ_MATCHES || 5)
    const baseSeed = Number(process.env.FUZZ_BASE_SEED || 200000000)
    const summary = await runBadmintonFuzzerBatch(matchesCount, {
      baseSeed,
      // 冒烟落盘必须与夜间批量的合并视图隔离（排球踩坑 #8）：本地直接跑本文件时
      // 产物进系统临时目录；编排器通过 FUZZ_SUMMARY_DIR 传临时目录，优先级更高不受影响
      outputDir: path.join(os.tmpdir(), 'fuzz-badminton-smoke'),
    })

    expect(summary.matchCount).toBe(matchesCount)
    expect(summary.stats.cleanMatches).toBe(matchesCount)
    expect(summary.stats.criticalMatches).toBe(0)
    expect(summary.stats.totalRallies).toBeGreaterThan(0)
    // P0-1 激活的探针覆盖度证据：弹窗锁定探针与终局探针必须有真实采样
    expect(summary.coverage.terminalProbes).toBeGreaterThan(0)
  })
})
