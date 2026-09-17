import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest'
import { setupFuzzerEnvironment } from './fuzzer-env'

// Setup global mocks before importing useScoreboard
setupFuzzerEnvironment()

import { setRegisteredOnLoadHandler } from './fuzzer-env'

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

import {
  createPRNG,
  generateMatchScenario,
  simulateVolleyballMatch,
  runFuzzerBatch,
} from './scoreboard-fuzzer'

describe('Volleyball Headless Fuzzer Engine', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('runs a single headless match to completion with full action trace', async () => {
    const prng = createPRNG(42)
    const scenario = generateMatchScenario(prng, 1)

    const result = await simulateVolleyballMatch(scenario, prng, {
      maxTotalActions: 50,
    })

    expect(result).toBeDefined()
    expect(result.totalActions).toBeGreaterThan(10)
    expect(result.actionHistory.length).toBeGreaterThan(10)

    if (result.hasCritical) {
      console.warn('⚠️ Critical anomalies discovered in seed:', result.seed)
      for (const a of result.anomalies.slice(0, 3)) {
        console.warn(`  - Step ${a.step} [${a.action}]:`, a.anomalies.map((m) => m.message).join('; '))
      }
    }
  })

  it('runs batch random matches and persists audit reports', async () => {
    const matchCount = process.env.FUZZ_MATCHES ? parseInt(process.env.FUZZ_MATCHES, 10) : 5
    const baseSeed = process.env.FUZZ_SEED ? parseInt(process.env.FUZZ_SEED, 10) : 1001

    console.log(`Running batch fuzzer for ${matchCount} matches...`)
    // 落盘重定向到临时目录：单测冒烟小样不得覆写共享 outputs 目录下的
    // 批量批次合并视图（§5.3.3 污染事故同类预防）
    const os = await import('node:os')
    const path = await import('node:path')
    // FUZZ_SUMMARY_DIR: 夜间编排器等批量调用方指定共享产物目录；
    // 未指定（普通单测）时落临时目录，避免覆写共享合并视图（§5.3.3 同类预防）
    const outputDir = process.env.FUZZ_SUMMARY_DIR || path.join(os.tmpdir(), `fuzz-volleyball-test-${process.pid}`)
    const report = await runFuzzerBatch(matchCount, { baseSeed, outputDir })

    console.log(`Fuzzer batch completed:`)
    console.log(`- Total matches: ${report.matchCount}`)
    console.log(`- Total rallies: ${report.stats.totalRallies}`)
    console.log(`- Total substitutions: ${report.stats.totalSubstitutions}`)
    console.log(`- Total undos: ${report.stats.totalUndos}`)
    console.log(`- Clean matches: ${report.stats.cleanMatches}`)
    console.log(`- Critical matches: ${report.stats.criticalMatches}`)
    console.log(`- Suspicious matches: ${report.stats.suspiciousMatches}`)
    if (report.coverage) {
      console.log(`- Coverage: reloads=${report.coverage.totalReloads} sideSwitchKept=${report.coverage.totalSideSwitchKept}/${report.coverage.totalSideSwitchConfirmed} deepUndos=${report.coverage.totalDeepUndos} hostileRejected=${report.coverage.totalHostileRejected}/${report.coverage.totalHostileSubs} decidingGame=${report.coverage.matchesDecidingGameReached} capHits=${report.coverage.totalCapHits}`)
      console.log(`- Games played histogram: ${JSON.stringify(report.coverage.gamesPlayedHistogram)}`)
    }

    expect(report.matchCount).toBe(matchCount)
    // 超时随场次缩放：实测单场约 2~35 秒（局数、机器负载波动大），
    // 写死 120s 会在 8 场以上必然误报超时失败；60s/场留足争用余量（存取回环模式每次重入约 +2~3s/场）
  }, Math.max(120000, 30000 + (process.env.FUZZ_MATCHES ? parseInt(process.env.FUZZ_MATCHES, 10) : 5) * 40000))
})
