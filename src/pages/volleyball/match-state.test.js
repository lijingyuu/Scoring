import { describe, it, expect } from 'vitest'
import {
  emptyCourt,
  createEmptyLiberoSetup,
  createEmptyLiberoRuntime,
  createEmptyMatchState,
  cloneCourt,
  cloneLiberoSetup,
  cloneLiberoRuntime,
  toggleSide,
  normalizeParticipantSide,
  normalizeMatchState,
  formatTeamName,
  swapMatchStateSides,
  buildHistoryEntry,
  buildMatchStorageKey,
  buildMatchQuery,
  buildLineupUrl,
  buildScoreboardUrl,
  VOLLEYBALL_LINEUP_ROUTE,
  VOLLEYBALL_SCOREBOARD_ROUTE,
} from '@/pages/volleyball/match-state'

describe('emptyCourt', () => {
  it('should return array of 6 empty strings', () => {
    const court = emptyCourt()
    expect(court).toHaveLength(6)
    expect(court.every((slot) => slot === '')).toBe(true)
  })
})

describe('createEmptyLiberoSetup', () => {
  it('should return default libero setup', () => {
    const setup = createEmptyLiberoSetup()
    expect(setup.pairIndexes).toEqual([])
    expect(setup.libero1Id).toBe('')
    expect(setup.libero2Id).toBe('')
  })
})

describe('createEmptyLiberoRuntime', () => {
  it('should return default libero runtime', () => {
    const runtime = createEmptyLiberoRuntime()
    expect(runtime.role1SlotIndex).toBe(-1)
    expect(runtime.role2SlotIndex).toBe(-1)
    expect(runtime.role1PlayerId).toBe('')
    expect(runtime.role2PlayerId).toBe('')
  })
})

describe('createEmptyMatchState', () => {
  it('should return a complete empty state with expected defaults', () => {
    const state = createEmptyMatchState()
    expect(state.screenLeftParticipantSide).toBe('left')
    expect(state.leftScore).toBe(0)
    expect(state.rightScore).toBe(0)
    expect(state.currentGameNo).toBe(1)
    expect(state.serveSide).toBe('left')
    expect(state.leftTimeouts).toBe(2)
    expect(state.rightTimeouts).toBe(2)
    expect(state.leftCourt).toHaveLength(6)
    expect(state.rightCourt).toHaveLength(6)
    expect(state.matchEnded).toBe(false)
    expect(state.retiredSide).toBe('')
    expect(state.historyStack).toEqual([])
    expect(state.matchEvents).toEqual([])
    expect(state.nextEventSeq).toBe(1)
  })
})

describe('cloneCourt', () => {
  it('should clone a valid court array', () => {
    const original = ['p1', 'p2', 'p3', 'p4', 'p5', 'p6']
    const cloned = cloneCourt(original)
    expect(cloned).toEqual(original)
    expect(cloned).not.toBe(original) // different reference
  })

  it('should pad short arrays to 6 with empty strings', () => {
    const result = cloneCourt(['p1', 'p2'])
    expect(result).toHaveLength(6)
    expect(result[0]).toBe('p1')
    expect(result[1]).toBe('p2')
    expect(result[2]).toBe('')
    expect(result[5]).toBe('')
  })

  it('should handle null/undefined', () => {
    expect(cloneCourt(null)).toHaveLength(6)
    expect(cloneCourt(undefined)).toHaveLength(6)
  })

  it('should truncate to 6 slots', () => {
    const result = cloneCourt(['a', 'b', 'c', 'd', 'e', 'f', 'g', 'h'])
    expect(result).toHaveLength(6)
    expect(result[5]).toBe('f')
  })
})

describe('toggleSide', () => {
  it('should flip left to right', () => {
    expect(toggleSide('left')).toBe('right')
  })

  it('should flip right to left', () => {
    expect(toggleSide('right')).toBe('left')
  })

  it('should treat non-right as left and return right', () => {
    expect(toggleSide('invalid')).toBe('right')
    expect(toggleSide('')).toBe('right')
  })
})

describe('normalizeParticipantSide', () => {
  it('should accept left and right', () => {
    expect(normalizeParticipantSide('left')).toBe('left')
    expect(normalizeParticipantSide('right')).toBe('right')
  })

  it('should default to left', () => {
    expect(normalizeParticipantSide('invalid')).toBe('left')
    expect(normalizeParticipantSide(undefined)).toBe('left')
  })
})

describe('formatTeamName', () => {
  it('should append 队 if missing', () => {
    expect(formatTeamName('火箭')).toBe('火箭队')
  })

  it('should not double-append 队', () => {
    expect(formatTeamName('火箭队')).toBe('火箭队')
  })

  it('should return empty for falsy input', () => {
    expect(formatTeamName('')).toBe('')
    expect(formatTeamName(null)).toBe('')
    expect(formatTeamName(undefined)).toBe('')
  })
})

describe('normalizeMatchState', () => {
  it('should fill defaults for empty input', () => {
    const state = normalizeMatchState({})
    expect(state.screenLeftParticipantSide).toBe('left')
    expect(state.leftScore).toBe(0)
    expect(state.historyStack).toEqual([])
  })

  it('should normalize numeric fields from strings', () => {
    const state = normalizeMatchState({
      leftScore: '21',
      rightScore: '15',
      leftGameWins: '2',
      rightGameWins: '1',
    })
    expect(state.leftScore).toBe(21)
    expect(state.rightScore).toBe(15)
    expect(state.leftGameWins).toBe(2)
    expect(state.rightGameWins).toBe(1)
  })

  it('should handle null/undefined input gracefully', () => {
    const state = normalizeMatchState(null)
    expect(state.screenLeftParticipantSide).toBe('left')
    expect(state.leftScore).toBe(0)
  })

  it('should migrate old displaySideSwapped state to new format', () => {
    // Old states have displaySideSwapped=true but do NOT have screenLeftParticipantSide
    const oldState = {
      displaySideSwapped: true,
      leftScore: 5,
      rightScore: 10,
      leftCourt: ['a', 'b', 'c', 'd', 'e', 'f'],
      rightCourt: ['g', 'h', 'i', 'j', 'k', 'l'],
    }
    const state = normalizeMatchState(oldState)
    // After migration, left and right should be swapped
    expect(state.leftScore).toBe(10)
    expect(state.rightScore).toBe(5)
    expect(state.displaySideSwapped).toBe(false)
  })

  it('should not swap when screenLeftParticipantSide is explicitly set', () => {
    const state = normalizeMatchState({
      screenLeftParticipantSide: 'right',
      leftScore: 5,
      rightScore: 10,
    })
    expect(state.screenLeftParticipantSide).toBe('right')
    expect(state.leftScore).toBe(5)
    expect(state.rightScore).toBe(10)
  })

  it('should cap history stack at 40 entries', () => {
    const bigStack = Array.from({ length: 50 }, (_, i) => ({
      leftScore: i,
      rightScore: 0,
    }))
    const state = normalizeMatchState({ historyStack: bigStack })
    expect(state.historyStack).toHaveLength(40)
    expect(state.historyStack[0].leftScore).toBe(10) // slice(-40) keeps last 40
  })

  it('should strip history from history entries (anti-recursion)', () => {
    const state = normalizeMatchState({
      historyStack: [{ leftScore: 21, historyStack: [{ leftScore: 10 }] }],
    })
    expect(state.historyStack[0].historyStack).toEqual([])
  })

  it('should sort matchEvents by seq', () => {
    const state = normalizeMatchState({
      matchEvents: [
        { seq: 5, type: 'timeout', leftScore: 10 },
        { seq: 1, type: 'substitution', leftScore: 3 },
        { seq: 3, type: 'timeout', leftScore: 5 },
      ],
    })
    expect(state.matchEvents[0].seq).toBe(1)
    expect(state.matchEvents[1].seq).toBe(3)
    expect(state.matchEvents[2].seq).toBe(5)
  })

  it('should calculate nextEventSeq from max event seq', () => {
    const state = normalizeMatchState({
      matchEvents: [
        { seq: 1, type: 'timeout', leftScore: 0 },
        { seq: 5, type: 'timeout', leftScore: 0 },
        { seq: 3, type: 'timeout', leftScore: 0 },
      ],
      nextEventSeq: 0,
    })
    // max seq is 5, so next should be 6
    expect(state.nextEventSeq).toBe(6)
  })

  it('should preserve explicit nextEventSeq if greater than max', () => {
    const state = normalizeMatchState({
      matchEvents: [
        { seq: 1, type: 'timeout', leftScore: 0 },
      ],
      nextEventSeq: 100,
    })
    expect(state.nextEventSeq).toBe(100)
  })
})

describe('swapMatchStateSides', () => {
  it('should swap scores and courts', () => {
    const state = createEmptyMatchState()
    state.leftScore = 25
    state.rightScore = 20
    state.leftCourt = ['p1', 'p2', 'p3', 'p4', 'p5', 'p6']
    state.rightCourt = ['q1', 'q2', 'q3', 'q4', 'q5', 'q6']

    const swapped = swapMatchStateSides(state)

    expect(swapped.leftScore).toBe(20)
    expect(swapped.rightScore).toBe(25)
    expect(swapped.leftCourt).toEqual(['q1', 'q2', 'q3', 'q4', 'q5', 'q6'])
    expect(swapped.rightCourt).toEqual(['p1', 'p2', 'p3', 'p4', 'p5', 'p6'])
  })

  it('should toggle screenLeftParticipantSide', () => {
    const state = createEmptyMatchState()
    state.screenLeftParticipantSide = 'left'
    const swapped = swapMatchStateSides(state)
    expect(swapped.screenLeftParticipantSide).toBe('right')
  })

  it('should swap game scores with winner side correction', () => {
    const state = createEmptyMatchState()
    state.gameScores = [
      { gameNo: 1, leftScore: 25, rightScore: 20, winnerSide: 'left' },
      { gameNo: 2, leftScore: 22, rightScore: 25, winnerSide: 'right' },
    ]

    const swapped = swapMatchStateSides(state)

    expect(swapped.gameScores[0].leftScore).toBe(20)
    expect(swapped.gameScores[0].rightScore).toBe(25)
    expect(swapped.gameScores[0].winnerSide).toBe('right')
  })

  it('should swap captain IDs and libero setups', () => {
    const state = createEmptyMatchState()
    state.leftCaptainMemberId = 'captain-left'
    state.rightCaptainMemberId = 'captain-right'

    const swapped = swapMatchStateSides(state)

    expect(swapped.leftCaptainMemberId).toBe('captain-right')
    expect(swapped.rightCaptainMemberId).toBe('captain-left')
  })
})

describe('buildMatchStorageKey', () => {
  it('should include matchId', () => {
    const key = buildMatchStorageKey('match-123')
    expect(key).toContain('match-123')
    expect(key).toContain('volleyball_scoreboard_state')
  })

  it('should return base key for empty matchId', () => {
    const key = buildMatchStorageKey('')
    expect(key).toBe('volleyball_scoreboard_state')
  })
})

describe('buildMatchQuery', () => {
  it('should build query string from params', () => {
    const query = buildMatchQuery({ matchId: 'm-1', round: 2 })
    expect(query).toContain('matchId=m-1')
    expect(query).toContain('round=2')
  })

  it('should skip null and undefined values', () => {
    const query = buildMatchQuery({ matchId: 'm-1', extra: null, missing: undefined })
    expect(query).toBe('matchId=m-1')
  })

  it('should return empty string for empty params', () => {
    expect(buildMatchQuery({})).toBe('')
    expect(buildMatchQuery()).toBe('')
  })
})

describe('buildLineupUrl', () => {
  it('should build lineup URL with params', () => {
    const url = buildLineupUrl({ matchId: 'm-1', gameNo: 2 })
    expect(url).toContain(VOLLEYBALL_LINEUP_ROUTE)
    expect(url).toContain('matchId=m-1')
    expect(url).toContain('gameNo=2')
  })
})

describe('buildScoreboardUrl', () => {
  it('should build scoreboard URL with params', () => {
    const url = buildScoreboardUrl({ matchId: 'm-1' })
    expect(url).toContain(VOLLEYBALL_SCOREBOARD_ROUTE)
    expect(url).toContain('matchId=m-1')
  })
})

describe('buildHistoryEntry', () => {
  it('should strip history stack from snapshot', () => {
    const state = createEmptyMatchState()
    state.leftScore = 21
    state.historyStack = [{ leftScore: 10 }]

    const entry = buildHistoryEntry(state)
    expect(entry.leftScore).toBe(21)
    expect(entry.historyStack).toEqual([])
  })
})

describe('cloneLiberoSetup', () => {
  it('should normalize valid setup', () => {
    const setup = cloneLiberoSetup({
      pairIndexes: [2, 3],
      libero1Id: 'lib-1',
      libero2Id: '',
    })
    expect(setup.pairIndexes).toEqual([2, 3])
    expect(setup.libero1Id).toBe('lib-1')
    expect(setup.libero2Id).toBe('')
  })

  it('should filter invalid pair indexes', () => {
    const setup = cloneLiberoSetup({
      pairIndexes: [2, 10, -1],
      libero1Id: '',
      libero2Id: '',
    })
    expect(setup.pairIndexes).toHaveLength(1)
    expect(setup.pairIndexes[0]).toBe(2)
  })
})
