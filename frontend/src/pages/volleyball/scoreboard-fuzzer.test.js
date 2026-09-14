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
    const report = await runFuzzerBatch(matchCount, { baseSeed })

    console.log(`Fuzzer batch completed:`)
    console.log(`- Total matches: ${report.matchCount}`)
    console.log(`- Total rallies: ${report.stats.totalRallies}`)
    console.log(`- Total substitutions: ${report.stats.totalSubstitutions}`)
    console.log(`- Total undos: ${report.stats.totalUndos}`)
    console.log(`- Clean matches: ${report.stats.cleanMatches}`)
    console.log(`- Critical matches: ${report.stats.criticalMatches}`)
    console.log(`- Suspicious matches: ${report.stats.suspiciousMatches}`)

    expect(report.matchCount).toBe(matchCount)
  }, 120000)
})
