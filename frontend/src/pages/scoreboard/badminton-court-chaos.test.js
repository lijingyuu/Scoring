import { describe, it, expect } from 'vitest'
import os from 'node:os'
import path from 'node:path'
import {
  createPRNG,
  generateDoublesCourtScenario,
  runCourtChaosBatch,
  simulateDoublesCourtMatch,
} from './badminton-court-fuzzer'

/**
 * 双打「四半区站位」混沌测试（badminton-court-fuzzer 引擎）：
 * 差分影子模型逐步全等 + BWF 站位不变量（偶右奇左 / 斜对角 / 同发球员换区 /
 * 锚点-发球员一致）+ 恶意探针零漂移 + 快照与事件 runtime 往返。
 *
 * 批量规模走 FUZZ_MATCHES 环境变量（对齐 badminton-fuzzer 惯例）：
 * 例行测试默认小批量；1000 场夜间/专项批量用
 * `FUZZ_MATCHES=1000 npx vitest run src/pages/scoreboard/badminton-court-chaos.test.js`。
 */
const MATCHES = Number(process.env.FUZZ_MATCHES || 40)

describe('Badminton doubles court chaos (headless, real state machine)', () => {
  it('single deterministic match replays identically with the same seed', async () => {
    const run = async () => {
      const prng = createPRNG(20261025)
      const scenario = generateDoublesCourtScenario(prng, 1)
      return simulateDoublesCourtMatch(scenario, createPRNG(20261025))
    }
    const first = await run()
    const second = await run()
    expect(second.actionHistory).toEqual(first.actionHistory)
    expect(first.hasCritical).toBe(false)
  })

  it(`runs ${MATCHES}-match batch with zero critical anomalies`, async () => {
    const summary = await runCourtChaosBatch(MATCHES, {
      baseSeed: Number(process.env.FUZZ_BASE_SEED || 20261025),
      // 冒烟落盘进系统临时目录，与夜间批量产物隔离（排球踩坑 #8 同款约定）
      outputDir: process.env.FUZZ_SUMMARY_DIR || path.join(os.tmpdir(), 'fuzz-badminton-court-smoke'),
    })

    expect(summary.matchCount).toBe(MATCHES)
    expect(summary.stats.criticalMatches).toBe(0)
    expect(summary.stats.cleanMatches + summary.stats.suspiciousMatches).toBe(MATCHES)
    expect(summary.stats.totalRallies).toBeGreaterThan(0)

    // 覆盖度证据：站位专属动作必须有真实采样
    expect(summary.stats.totalSwaps).toBeGreaterThan(0)
    expect(summary.stats.totalUndos).toBeGreaterThan(0)
    expect(summary.stats.totalHostileProbes).toBe(summary.stats.totalHostileRejected)
    expect(summary.stats.totalRoundtripProbes).toBeGreaterThan(0)
    // 大批量时附加结构覆盖断言（小批量采样不足则跳过）
    if (MATCHES >= 200) {
      expect(summary.stats.totalManualSwitches).toBeGreaterThan(0)
      expect(summary.stats.totalDecidingSwitches).toBeGreaterThan(0)
      expect(summary.stats.totalGameEnds).toBeGreaterThan(0)
      expect(summary.stats.skipPlacementMatches).toBeGreaterThan(0)
      expect(summary.stats.finishedMatches).toBeGreaterThan(0)
    }
  }, 600000)
})
