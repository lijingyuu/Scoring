import { describe, expect, it } from 'vitest'
import {
  parseScoreDisplay,
  isRecoveredGameWon,
  computeBackendRecovery,
  findRecoveredGameScore,
  collectRecoveredGameScores,
  hasSavedProgress,
  hasScoreProgress,
  computeRecoveredGameNo,
  buildRecoveredCacheFromRecord,
} from './score-recovery'

// 排球记分恢复纯函数测试。record 采用后端 match detail 返回结构的子集。
// 默认赛制参数：bestOf=3, pointsToWin=25, decidingPointsToWin=15, enableDeuce=true, capPoint=30

const BASE_RECORD = {
  bestOf: 3,
  pointsToWin: 25,
  decidingPointsToWin: 15,
  enableDeuce: true,
  capPoint: 30,
}

describe('parseScoreDisplay', () => {
  it('parses "21:15" style scoreDisplay', () => {
    expect(parseScoreDisplay('21:15')).toEqual({ leftScore: 21, rightScore: 15 })
    expect(parseScoreDisplay('2:0')).toEqual({ leftScore: 2, rightScore: 0 })
  })

  it('returns null for blank / non-matching input', () => {
    expect(parseScoreDisplay('')).toBeNull()
    expect(parseScoreDisplay(null)).toBeNull()
    expect(parseScoreDisplay(undefined)).toBeNull()
    expect(parseScoreDisplay('21-15')).toBeNull()
    expect(parseScoreDisplay('abc')).toBeNull()
  })
})

describe('isRecoveredGameWon', () => {
  it('returns false when score is falsy', () => {
    expect(isRecoveredGameWon(BASE_RECORD, 1, null)).toBe(false)
    expect(isRecoveredGameWon(BASE_RECORD, 1, undefined)).toBe(false)
  })

  it('treats capPoint hit as won regardless of deuce rule', () => {
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 30, rightScore: 28 })).toBe(true)
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 28, rightScore: 30 })).toBe(true)
  })

  it('regular game needs pointsToWin and two-point lead when deuce enabled', () => {
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 24, rightScore: 10 })).toBe(false)
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 25, rightScore: 10 })).toBe(true)
    // 已达 25 分但分差仅 1，金球制（enableDeuce）下不能判胜
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 25, rightScore: 24 })).toBe(false)
    // 超 25 且分差 >= 2 → 胜
    expect(isRecoveredGameWon(BASE_RECORD, 1, { leftScore: 26, rightScore: 24 })).toBe(true)
  })

  it('non-deuce (enableDeuce=false) wins by reaching target even by one point', () => {
    const record = { ...BASE_RECORD, enableDeuce: false }
    expect(isRecoveredGameWon(record, 1, { leftScore: 25, rightScore: 24 })).toBe(true)
    expect(isRecoveredGameWon(record, 1, { leftScore: 24, rightScore: 25 })).toBe(true)
    expect(isRecoveredGameWon(record, 1, { leftScore: 24, rightScore: 23 })).toBe(false)
  })

  it('deciding game uses decidingPointsToWin when provided', () => {
    const record = { ...BASE_RECORD }
    // gameNo === bestOf(3)，用 15 分决胜
    expect(isRecoveredGameWon(record, 3, { leftScore: 15, rightScore: 13 })).toBe(true)
    expect(isRecoveredGameWon(record, 3, { leftScore: 14, rightScore: 13 })).toBe(false)
    // 非决胜局仍按 25 分
    expect(isRecoveredGameWon(record, 1, { leftScore: 15, rightScore: 13 })).toBe(false)
  })
})

describe('computeBackendRecovery', () => {
  it('no progress starts at game 1, not ended', () => {
    expect(computeBackendRecovery({ ...BASE_RECORD, status: 1 })).toEqual({
      currentGameNo: 1,
      matchEnded: false,
    })
  })

  it('one finished game (via gameScores) resumes at game 2', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
    }
    expect(computeBackendRecovery(record)).toEqual({ currentGameNo: 2, matchEnded: false })
  })

  it('all games finished ends the match', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      gameScores: [
        { gameNo: 1, leftScore: 25, rightScore: 15 },
        { gameNo: 2, leftScore: 25, rightScore: 18 },
        { gameNo: 3, leftScore: 15, rightScore: 12 },
      ],
    }
    expect(computeBackendRecovery(record)).toEqual({ currentGameNo: 3, matchEnded: true })
  })

  it('status 2/3 always marks match ended', () => {
    expect(computeBackendRecovery({ ...BASE_RECORD, status: 2 })).toEqual({
      currentGameNo: 1,
      matchEnded: true,
    })
    const record = {
      ...BASE_RECORD,
      status: 3,
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
    }
    // 已结束分支返回「最后一局局号」= max(1, completedCount)，不加 1（与进行中分支不同）
    expect(computeBackendRecovery(record)).toEqual({ currentGameNo: 1, matchEnded: true })
  })

  it('counts finished games from events when gameScores absent', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      events: [
        { gameNo: 1, leftScore: 20, rightScore: 18, eventType: 'score' },
        { gameNo: 1, leftScore: 25, rightScore: 18, eventType: 'score' },
      ],
    }
    expect(computeBackendRecovery(record)).toEqual({ currentGameNo: 2, matchEnded: false })
  })

  it('events that never reach target do not count as finished', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      events: [{ gameNo: 1, leftScore: 15, rightScore: 10, eventType: 'score' }],
    }
    expect(computeBackendRecovery(record)).toEqual({ currentGameNo: 1, matchEnded: false })
  })
})

describe('findRecoveredGameScore', () => {
  it('returns null for non-positive gameNo', () => {
    expect(findRecoveredGameScore({}, 0)).toBeNull()
    expect(findRecoveredGameScore({}, -1)).toBeNull()
    expect(findRecoveredGameScore({}, null)).toBeNull()
  })

  it('prefers exact gameScores entry', () => {
    const record = {
      gameScores: [
        { gameNo: 1, leftScore: 25, rightScore: 15 },
        { gameNo: 2, leftScore: 18, rightScore: 25 },
      ],
    }
    expect(findRecoveredGameScore(record, 2)).toEqual({ leftScore: 18, rightScore: 25 })
    expect(findRecoveredGameScore(record, 1)).toEqual({ leftScore: 25, rightScore: 15 })
  })

  it('falls back to latest event for the game', () => {
    const record = {
      events: [
        { gameNo: 3, leftScore: 10, rightScore: 5, eventType: 'score' },
        { gameNo: 3, leftScore: 13, rightScore: 6, eventType: 'score' },
      ],
    }
    expect(findRecoveredGameScore(record, 3)).toEqual({ leftScore: 13, rightScore: 6 })
  })

  it('returns null when nothing found', () => {
    expect(findRecoveredGameScore({}, 1)).toBeNull()
  })
})

describe('collectRecoveredGameScores', () => {
  it('collects gameScores with explicit or derived winnerSide, sorted by gameNo', () => {
    const record = {
      gameScores: [
        { gameNo: 2, leftScore: 15, rightScore: 25 },
        { gameNo: 1, leftScore: 25, rightScore: 15, winnerSide: 'left' },
      ],
    }
    expect(collectRecoveredGameScores(record)).toEqual([
      { gameNo: 1, leftScore: 25, rightScore: 15, winnerSide: 'left' },
      { gameNo: 2, leftScore: 15, rightScore: 25, winnerSide: 'right' },
    ])
  })

  it('fills gaps from events only when the game is won', () => {
    const record = {
      ...BASE_RECORD,
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
      events: [
        { gameNo: 2, leftScore: 25, rightScore: 22, eventType: 'score' },
        // 第 3 局未达到目标分，不应被收集
        { gameNo: 3, leftScore: 10, rightScore: 8, eventType: 'score' },
      ],
    }
    expect(collectRecoveredGameScores(record)).toEqual([
      { gameNo: 1, leftScore: 25, rightScore: 15, winnerSide: 'left' },
      { gameNo: 2, leftScore: 25, rightScore: 22, winnerSide: 'left' },
    ])
  })
})

describe('hasSavedProgress vs hasScoreProgress', () => {
  it('both return false for finished match (status 2/3)', () => {
    expect(hasSavedProgress({ ...BASE_RECORD, status: 2 })).toBe(false)
    expect(hasScoreProgress({ ...BASE_RECORD, status: 2 })).toBe(false)
    expect(hasSavedProgress({ ...BASE_RECORD, status: 3, scoreDisplay: '1:0' })).toBe(false)
    expect(hasScoreProgress({ ...BASE_RECORD, status: 3, scoreDisplay: '1:0' })).toBe(false)
  })

  it('gameScores counts as progress for both', () => {
    const record = { status: 1, gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }] }
    expect(hasSavedProgress(record)).toBe(true)
    expect(hasScoreProgress(record)).toBe(true)
  })

  it('lineupSnapshots only count as saved progress, not score progress', () => {
    const record = { status: 1, lineupSnapshots: [{ gameNo: 1, court: [] }] }
    expect(hasSavedProgress(record)).toBe(true)
    expect(hasScoreProgress(record)).toBe(false)
  })

  it('scoreDisplay counts as progress for both', () => {
    const record = { status: 1, scoreDisplay: '1:0' }
    expect(hasSavedProgress(record)).toBe(true)
    expect(hasScoreProgress(record)).toBe(true)
  })

  it('roster_snapshot event is not progress for either', () => {
    const record = {
      status: 1,
      events: [{ gameNo: 1, eventType: 'roster_snapshot' }],
    }
    expect(hasSavedProgress(record)).toBe(false)
    expect(hasScoreProgress(record)).toBe(false)
  })

  it('lineup_snapshot event is saved progress but not score progress', () => {
    const record = {
      status: 1,
      events: [{ gameNo: 1, eventType: 'lineup_snapshot' }],
    }
    expect(hasSavedProgress(record)).toBe(true)
    expect(hasScoreProgress(record)).toBe(false)
  })

  it('score event counts as progress for both', () => {
    const record = {
      status: 1,
      events: [{ gameNo: 1, eventType: 'score', leftScore: 1, rightScore: 0 }],
    }
    expect(hasSavedProgress(record)).toBe(true)
    expect(hasScoreProgress(record)).toBe(true)
  })
})

describe('computeRecoveredGameNo', () => {
  it('prefers backend recovery when match ended', () => {
    const record = { ...BASE_RECORD, status: 2 }
    expect(computeRecoveredGameNo(record, { currentGameNo: 3 })).toBe(1)
  })

  it('takes the max of backend recovery and cached gameNo when not ended', () => {
    const record = { ...BASE_RECORD, status: 1 }
    expect(computeRecoveredGameNo(record, { currentGameNo: 2 })).toBe(2)
    const recordWithProgress = {
      ...BASE_RECORD,
      status: 1,
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
    }
    expect(computeRecoveredGameNo(recordWithProgress, { currentGameNo: 1 })).toBe(2)
  })

  it('handles missing cached state', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
    }
    expect(computeRecoveredGameNo(record, null)).toBe(2)
    expect(computeRecoveredGameNo({ ...BASE_RECORD, status: 1 }, undefined)).toBe(1)
  })
})

describe('buildRecoveredCacheFromRecord', () => {
  it('resolves score from requested game, wins from recovered scores, winnerName from record.winnerSide', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      winnerSide: 'left',
      left: { name: '甲方' },
      right: { name: '乙方' },
      retiredSide: 'right',
      gameScores: [
        { gameNo: 1, leftScore: 25, rightScore: 15 },
        { gameNo: 2, leftScore: 25, rightScore: 20 },
      ],
    }
    expect(buildRecoveredCacheFromRecord(record, 3)).toEqual({
      currentGameNo: 3,
      leftScore: 0,
      rightScore: 0,
      leftGameWins: 2,
      rightGameWins: 0,
      gameScores: [
        { gameNo: 1, leftScore: 25, rightScore: 15, winnerSide: 'left' },
        { gameNo: 2, leftScore: 25, rightScore: 20, winnerSide: 'left' },
      ],
      retiredSide: 'right',
      matchEnded: false,
      winnerName: '甲方',
    })
  })

  it('falls back to scoreDisplay when requested game has no score', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      scoreDisplay: '1:0',
      gameScores: [{ gameNo: 1, leftScore: 25, rightScore: 15 }],
    }
    const cache = buildRecoveredCacheFromRecord(record, 2)
    expect(cache.leftScore).toBe(1)
    expect(cache.rightScore).toBe(0)
    expect(cache.currentGameNo).toBe(2)
  })

  it('fallback winnerName derived from game wins when winnerSide absent', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      left: { name: '甲' },
      right: { name: '乙' },
      gameScores: [
        { gameNo: 1, leftScore: 25, rightScore: 15 },
        { gameNo: 2, leftScore: 25, rightScore: 18 },
      ],
    }
    expect(buildRecoveredCacheFromRecord(record, 1).winnerName).toBe('甲')
  })

  it('prefers record.leftGameWins/rightGameWins when no recovered scores', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      leftGameWins: 2,
      rightGameWins: 0,
      left: { name: '甲' },
      right: { name: '乙' },
    }
    const cache = buildRecoveredCacheFromRecord(record, 1)
    expect(cache.leftGameWins).toBe(2)
    expect(cache.rightGameWins).toBe(0)
    expect(cache.gameScores).toEqual([])
    expect(cache.matchEnded).toBe(false)
    // winnerName 只由可重建的 gameWins/winnerSide 推导：无 gameScores/events 时推不出来
    expect(cache.winnerName).toBe('')
  })

  it('empty winnerName when nobody has won', () => {
    const record = {
      ...BASE_RECORD,
      status: 1,
      left: { name: '甲' },
      right: { name: '乙' },
    }
    expect(buildRecoveredCacheFromRecord(record, 1).winnerName).toBe('')
  })
})
