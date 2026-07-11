import { describe, expect, it } from 'vitest'
import {
  computeCurrentSegmentIndex,
  computeTargetScore,
  computeSegmentTarget,
  isRelayMatchEnded,
  isSegmentTargetReached,
  shouldAdvanceSegment,
  toggleSidesSwapped,
  visualToLogicalSide,
  buildScoreState,
  parseScoreState,
  appendSegmentScore,
  buildRelayItemsFromOrders,
  validateRelayChain,
  buildRelayOrderFromItems,
  relayStorageKey,
  isLineupComplete,
  validateRelayLineup,
  buildFullSnapshot,
} from './relay-scoring'

// ============================================================================
// computeCurrentSegmentIndex
// ============================================================================
describe('computeCurrentSegmentIndex', () => {
  it('returns 0 when no items exist', () => {
    expect(computeCurrentSegmentIndex(0, 0)).toBe(0)
  })

  it('returns 0 when 0 segments completed and 6 items', () => {
    expect(computeCurrentSegmentIndex(0, 6)).toBe(0)
  })

  it('returns 1 after the first segment score is recorded (segmentScores.length=1)', () => {
    expect(computeCurrentSegmentIndex(1, 6)).toBe(1)
  })

  it('returns 2 after two segments completed', () => {
    expect(computeCurrentSegmentIndex(2, 6)).toBe(2)
  })

  it('caps at items.length - 1 even when segmentScores is larger', () => {
    expect(computeCurrentSegmentIndex(10, 6)).toBe(5)
  })

  it('works for 3-person relay', () => {
    expect(computeCurrentSegmentIndex(0, 3)).toBe(0)
    expect(computeCurrentSegmentIndex(1, 3)).toBe(1)
    expect(computeCurrentSegmentIndex(2, 3)).toBe(2)
    expect(computeCurrentSegmentIndex(3, 3)).toBe(2) // capped
  })
})

// ============================================================================
// computeTargetScore
// ============================================================================
describe('computeTargetScore', () => {
  it('uses itemsLength when non-zero', () => {
    expect(computeTargetScore(10, 6, 6)).toBe(60)
  })

  it('falls back to relayMemberCount when itemsLength is 0', () => {
    expect(computeTargetScore(10, 0, 6)).toBe(60)
  })

  it('falls back to default 6 when both are 0', () => {
    expect(computeTargetScore(10, 0, 0)).toBe(60)
  })

  it('base 11 with 5 members -> 55', () => {
    expect(computeTargetScore(11, 5, 5)).toBe(55)
  })

  it('base 15 with 3 members -> 45', () => {
    expect(computeTargetScore(15, 3, 3)).toBe(45)
  })
})

// ============================================================================
// computeSegmentTarget
// ============================================================================
describe('computeSegmentTarget', () => {
  it('segment 1 target = base * 1 capped at target', () => {
    expect(computeSegmentTarget(10, 1, 60)).toBe(10)
  })

  it('segment 2 target = base * 2', () => {
    expect(computeSegmentTarget(10, 2, 60)).toBe(20)
  })

  it('segment 6 target = base * 6 = target', () => {
    expect(computeSegmentTarget(10, 6, 60)).toBe(60)
  })

  it('caps at targetScore', () => {
    expect(computeSegmentTarget(10, 7, 60)).toBe(60)
  })

  it('base 11, segment 3 -> 33', () => {
    expect(computeSegmentTarget(11, 3, 55)).toBe(33)
  })
})

// ============================================================================
// isRelayMatchEnded
// ============================================================================
describe('isRelayMatchEnded', () => {
  it('true when matchStatus is 2', () => {
    expect(isRelayMatchEnded(2, 60, 10, 5)).toBe(true)
  })

  it('true when left score >= targetScore', () => {
    expect(isRelayMatchEnded(0, 60, 60, 30)).toBe(true)
    expect(isRelayMatchEnded(0, 60, 61, 30)).toBe(true)
  })

  it('true when right score >= targetScore', () => {
    expect(isRelayMatchEnded(0, 60, 30, 60)).toBe(true)
    expect(isRelayMatchEnded(0, 60, 30, 61)).toBe(true)
  })

  it('false when targetScore is 0 and matchStatus is not 2', () => {
    expect(isRelayMatchEnded(0, 0, 100, 100)).toBe(false)
  })

  it('false during normal play', () => {
    expect(isRelayMatchEnded(0, 60, 15, 12)).toBe(false)
  })

  it('false at matchStatus 0 with scores below target', () => {
    expect(isRelayMatchEnded(0, 60, 59, 40)).toBe(false)
  })
})

// ============================================================================
// isSegmentTargetReached
// ============================================================================
describe('isSegmentTargetReached', () => {
  it('true when left reaches target', () => {
    expect(isSegmentTargetReached(10, 5, 10)).toBe(true)
  })

  it('true when right reaches target', () => {
    expect(isSegmentTargetReached(5, 10, 10)).toBe(true)
  })

  it('true when both exceed', () => {
    expect(isSegmentTargetReached(15, 12, 10)).toBe(true)
  })

  it('false when neither reaches', () => {
    expect(isSegmentTargetReached(9, 8, 10)).toBe(false)
  })

  it('segment 2 target: 20 is reached', () => {
    expect(isSegmentTargetReached(19, 20, 20)).toBe(true)
    expect(isSegmentTargetReached(19, 19, 20)).toBe(false)
  })
})

// ============================================================================
// shouldAdvanceSegment
// ============================================================================
describe('shouldAdvanceSegment', () => {
  it('true when pending and match not ended', () => {
    expect(shouldAdvanceSegment(true, false)).toBe(true)
  })

  it('false when not pending', () => {
    expect(shouldAdvanceSegment(false, false)).toBe(false)
  })

  it('false when match ended (even if pending)', () => {
    expect(shouldAdvanceSegment(true, true)).toBe(false)
  })

  it('false when neither', () => {
    expect(shouldAdvanceSegment(false, true)).toBe(false)
  })
})

// ============================================================================
// toggleSidesSwapped
// ============================================================================
describe('toggleSidesSwapped', () => {
  it('false -> true', () => expect(toggleSidesSwapped(false)).toBe(true))
  it('true -> false', () => expect(toggleSidesSwapped(true)).toBe(false))

  it('3 toggles = from false -> true -> false -> true', () => {
    let s = false
    s = toggleSidesSwapped(s) // game1->2
    expect(s).toBe(true)
    s = toggleSidesSwapped(s) // game2->3
    expect(s).toBe(false)
    s = toggleSidesSwapped(s) // mid-game3
    expect(s).toBe(true)
  })
})

// ============================================================================
// visualToLogicalSide
// ============================================================================
describe('visualToLogicalSide', () => {
  it('no swap: visual left -> logical left', () => {
    expect(visualToLogicalSide('left', false)).toBe('left')
    expect(visualToLogicalSide('right', false)).toBe('right')
  })

  it('swapped: visual left -> logical right', () => {
    expect(visualToLogicalSide('left', true)).toBe('right')
    expect(visualToLogicalSide('right', true)).toBe('left')
  })
})

// ============================================================================
// buildScoreState / parseScoreState
// ============================================================================
describe('scoreState serialization', () => {
  it('buildScoreState creates a deep copy', () => {
    const segScores = [{ segmentNo: 1, leftScore: 10, rightScore: 5 }]
    const state = buildScoreState(10, 5, segScores, false, false, 'left')

    expect(state.leftScore).toBe(10)
    expect(state.rightScore).toBe(5)
    expect(state.segmentScores).toEqual(segScores)
    expect(state.segmentScores).not.toBe(segScores)           // different reference
    expect(state.segmentScores[0]).not.toBe(segScores[0])     // deep clone
    expect(state.segmentSwitchPending).toBe(false)
    expect(state.sidesSwapped).toBe(false)
    expect(state.lastScoredSide).toBe('left')
  })

  it('parseScoreState restores from snapshot', () => {
    const snap = {
      leftScore: 30,
      rightScore: 25,
      segmentScores: [
        { segmentNo: 1, leftScore: 10, rightScore: 5 },
        { segmentNo: 2, leftScore: 20, rightScore: 12 },
      ],
      segmentSwitchPending: true,
      sidesSwapped: true,
      lastScoredSide: 'right',
    }
    const parsed = parseScoreState(snap)
    expect(parsed.leftScore).toBe(30)
    expect(parsed.rightScore).toBe(25)
    expect(parsed.segmentScores).toHaveLength(2)
    expect(parsed.segmentSwitchPending).toBe(true)
    expect(parsed.sidesSwapped).toBe(true)
    expect(parsed.lastScoredSide).toBe('right')
  })

  it('parseScoreState handles null/undefined safely', () => {
    const parsed = parseScoreState(null)
    expect(parsed.leftScore).toBe(0)
    expect(parsed.rightScore).toBe(0)
    expect(parsed.segmentScores).toEqual([])
    expect(parsed.segmentSwitchPending).toBe(false)
    expect(parsed.sidesSwapped).toBe(false)
    expect(parsed.lastScoredSide).toBe('')
  })

  it('round-trip: build then parse returns equivalent state', () => {
    const original = buildScoreState(45, 38, [{ segmentNo: 1, leftScore: 10, rightScore: 8 }], true, true, 'left')
    const parsed = parseScoreState(original)
    expect(parsed.leftScore).toBe(original.leftScore)
    expect(parsed.rightScore).toBe(original.rightScore)
    expect(parsed.segmentScores).toEqual(original.segmentScores)
    expect(parsed.segmentSwitchPending).toBe(original.segmentSwitchPending)
    expect(parsed.sidesSwapped).toBe(original.sidesSwapped)
    expect(parsed.lastScoredSide).toBe(original.lastScoredSide)
  })
})

// ============================================================================
// appendSegmentScore
// ============================================================================
describe('appendSegmentScore', () => {
  it('adds a new segment entry', () => {
    const scores = []
    const added = appendSegmentScore(scores, 1, 10, 5)
    expect(added).toBe(true)
    expect(scores).toHaveLength(1)
    expect(scores[0]).toEqual({ segmentNo: 1, leftScore: 10, rightScore: 5 })
  })

  it('is idempotent — does not add duplicate segmentNo', () => {
    const scores = [{ segmentNo: 1, leftScore: 10, rightScore: 5 }]
    const added = appendSegmentScore(scores, 1, 12, 6)
    expect(added).toBe(false)
    expect(scores).toHaveLength(1)
    expect(scores[0].leftScore).toBe(10) // unchanged
  })

  it('adds multiple segments in order', () => {
    const scores = []
    appendSegmentScore(scores, 1, 10, 5)
    appendSegmentScore(scores, 2, 20, 12)
    appendSegmentScore(scores, 3, 30, 18)
    expect(scores).toHaveLength(3)
    expect(scores[0].segmentNo).toBe(1)
    expect(scores[1].segmentNo).toBe(2)
    expect(scores[2].segmentNo).toBe(3)
  })
})

// ============================================================================
// buildRelayItemsFromOrders
// ============================================================================
describe('buildRelayItemsFromOrders', () => {
  it('generates 6 segments for 6-person relay with correct 1+2/2+3/.../6+1 chain', () => {
    const leftOrder = ['A1', 'A2', 'A3', 'A4', 'A5', 'A6']
    const rightOrder = ['B1', 'B2', 'B3', 'B4', 'B5', 'B6']
    const items = buildRelayItemsFromOrders(leftOrder, rightOrder, 6)

    expect(items).toHaveLength(6)

    // R1: 1+2
    expect(items[0].itemCode).toBe('R1')
    expect(items[0].leftMemberIds).toEqual(['A1', 'A2'])
    expect(items[0].rightMemberIds).toEqual(['B1', 'B2'])

    // R2: 2+3
    expect(items[1].itemCode).toBe('R2')
    expect(items[1].leftMemberIds).toEqual(['A2', 'A3'])

    // R6: 6+1 (circular)
    expect(items[5].itemCode).toBe('R6')
    expect(items[5].leftMemberIds).toEqual(['A6', 'A1'])
    expect(items[5].rightMemberIds).toEqual(['B6', 'B1'])
  })

  it('generates 3 segments for 3-person relay', () => {
    const items = buildRelayItemsFromOrders(['X1', 'X2', 'X3'], ['Y1', 'Y2', 'Y3'], 3)
    expect(items).toHaveLength(3)
    expect(items[0].leftMemberIds).toEqual(['X1', 'X2'])
    expect(items[1].leftMemberIds).toEqual(['X2', 'X3'])
    expect(items[2].leftMemberIds).toEqual(['X3', 'X1'])
  })

  it('generates correct displayOrder and itemName', () => {
    const items = buildRelayItemsFromOrders(['a', 'b', 'c', 'd', 'e'], ['f', 'g', 'h', 'i', 'j'], 5)
    expect(items[0].displayOrder).toBe(1)
    expect(items[0].itemName).toBe('第 1 段')
    expect(items[4].displayOrder).toBe(5)
    expect(items[4].itemName).toBe('第 5 段')
  })
})

// ============================================================================
// validateRelayChain
// ============================================================================
describe('validateRelayChain', () => {
  it('valid 6-person chain: 1+2, 2+3, 3+4, 4+5, 5+6, 6+1', () => {
    const pairs = [['A1', 'A2'], ['A2', 'A3'], ['A3', 'A4'], ['A4', 'A5'], ['A5', 'A6'], ['A6', 'A1']]
    expect(validateRelayChain(pairs)).toEqual({ valid: true })
  })

  it('valid 3-person chain', () => {
    const pairs = [['p1', 'p2'], ['p2', 'p3'], ['p3', 'p1']]
    expect(validateRelayChain(pairs)).toEqual({ valid: true })
  })

  it('rejects chain with wrong bridge: 1+2, 2+3, 3+X (X!=1)', () => {
    const pairs = [['A1', 'A2'], ['A2', 'A3'], ['A3', 'WRONG']]
    const result = validateRelayChain(pairs)
    expect(result.valid).toBe(false)
    expect(result.reason).toContain('chain broken')
  })

  it('rejects chain with broken middle link: 1+2, X+3', () => {
    const pairs = [['A1', 'A2'], ['WRONG', 'A3'], ['A3', 'A1']]
    const result = validateRelayChain(pairs)
    expect(result.valid).toBe(false)
    expect(result.reason).toContain('chain broken')
  })

  it('rejects chain where same member leads twice: A1+A2, A2+A3, A3+A2, A2+A1', () => {
    // Adjacency checks all pass, but A2 leads segment 2 and segment 4
    const pairs = [['A1', 'A2'], ['A2', 'A3'], ['A3', 'A2'], ['A2', 'A1']]
    const result = validateRelayChain(pairs)
    expect(result.valid).toBe(false)
    expect(result.reason).toContain('duplicate lead member')
  })

  it('rejects non-pair segment (single player)', () => {
    const pairs = [['A1'], ['A1', 'A2'], ['A2', 'A1']]
    const result = validateRelayChain(pairs)
    expect(result.valid).toBe(false)
    expect(result.reason).toContain('pair')
  })

  it('rejects empty member in segment', () => {
    const pairs = [['A1', ''], ['', 'A3'], ['A3', 'A1']]
    const result = validateRelayChain(pairs)
    expect(result.valid).toBe(false)
    expect(result.reason).toContain('empty member')
  })

  it('rejects less than 3 segments', () => {
    expect(validateRelayChain([['A1', 'A2'], ['A2', 'A1']]).valid).toBe(false)
    expect(validateRelayChain(null).valid).toBe(false)
  })

  it('valid 5-person chain', () => {
    const pairs = [['1', '2'], ['2', '3'], ['3', '4'], ['4', '5'], ['5', '1']]
    expect(validateRelayChain(pairs)).toEqual({ valid: true })
  })
})

// ============================================================================
// buildRelayOrderFromItems
// ============================================================================
describe('buildRelayOrderFromItems', () => {
  it('extracts left order from 6 saved items', () => {
    const items = [
      { leftMemberIds: ['A1', 'A2'], rightMemberIds: ['B1', 'B2'] },
      { leftMemberIds: ['A2', 'A3'], rightMemberIds: ['B2', 'B3'] },
      { leftMemberIds: ['A3', 'A4'], rightMemberIds: ['B3', 'B4'] },
      { leftMemberIds: ['A4', 'A5'], rightMemberIds: ['B4', 'B5'] },
      { leftMemberIds: ['A5', 'A6'], rightMemberIds: ['B5', 'B6'] },
      { leftMemberIds: ['A6', 'A1'], rightMemberIds: ['B6', 'B1'] },
    ]
    const order = buildRelayOrderFromItems(items, 'left', 6)
    expect(order).toEqual(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'])
  })

  it('pads with empty strings for missing items', () => {
    const items = [
      { leftMemberIds: ['A1', 'A2'] },
    ]
    const order = buildRelayOrderFromItems(items, 'left', 6)
    expect(order).toEqual(['A1', '', '', '', '', ''])
  })

  it('handles empty items array', () => {
    const order = buildRelayOrderFromItems([], 'left', 6)
    expect(order).toEqual(['', '', '', '', '', ''])
  })
})

// ============================================================================
// relayStorageKey
// ============================================================================
describe('relayStorageKey', () => {
  it('uses matchId when provided', () => {
    expect(relayStorageKey('match-123')).toBe('team_relay_scoreboard_state_match-123')
  })

  it('falls back to generic key when matchId is empty', () => {
    expect(relayStorageKey('')).toBe('team_relay_scoreboard_state')
  })
})

// ============================================================================
// isLineupComplete
// ============================================================================
describe('isLineupComplete', () => {
  it('true when all items have 2 members on both sides', () => {
    const items = [
      { leftMembers: [{ id: 'a1' }, { id: 'a2' }], rightMembers: [{ id: 'b1' }, { id: 'b2' }] },
      { leftMembers: [{ id: 'a2' }, { id: 'a3' }], rightMembers: [{ id: 'b2' }, { id: 'b3' }] },
    ]
    expect(isLineupComplete(items)).toBe(true)
  })

  it('false when empty array', () => {
    expect(isLineupComplete([])).toBe(false)
  })

  it('false when a side has only 1 member', () => {
    const items = [
      { leftMembers: [{ id: 'a1' }], rightMembers: [{ id: 'b1' }, { id: 'b2' }] },
    ]
    expect(isLineupComplete(items)).toBe(false)
  })

  it('false when null', () => {
    expect(isLineupComplete(null)).toBe(false)
  })
})

// ============================================================================
// validateRelayLineup
// ============================================================================
describe('validateRelayLineup', () => {
  it('valid when both sides fill exactly relayMemberCount', () => {
    const leftOrder = ['A1', 'A2', 'A3', 'A4', 'A5', 'A6']
    const rightOrder = ['B1', 'B2', 'B3', 'B4', 'B5', 'B6']
    expect(validateRelayLineup(leftOrder, rightOrder, 6)).toEqual({ valid: true })
  })

  it('invalid when left has fewer members', () => {
    const result = validateRelayLineup(['A1', 'A2', 'A3'], ['B1', 'B2', 'B3', 'B4', 'B5', 'B6'], 6)
    expect(result.valid).toBe(false)
    expect(result.message).toContain('6')
  })

  it('invalid when right has fewer members', () => {
    const result = validateRelayLineup(['A1', 'A2', 'A3', 'A4', 'A5', 'A6'], ['B1', 'B2'], 6)
    expect(result.valid).toBe(false)
  })

  it('invalid when both are empty', () => {
    const result = validateRelayLineup([], [], 6)
    expect(result.valid).toBe(false)
  })

  it('handles sparse arrays (empty strings filtered out)', () => {
    // order with empty slots between filled ones
    const sparse = ['A1', '', 'A3', '', '', '']
    expect(validateRelayLineup(sparse, sparse, 6).valid).toBe(false)
  })
})

// ============================================================================
// buildFullSnapshot
// ============================================================================
describe('buildFullSnapshot', () => {
  it('wraps score state with history and synced flag', () => {
    const scoreState = buildScoreState(10, 5, [], false, false, 'left')
    const history = [
      { leftScore: 9, rightScore: 5, segmentScores: [], segmentSwitchPending: false, sidesSwapped: false, lastScoredSide: '' },
    ]
    const snap = buildFullSnapshot(scoreState, history, false)

    expect(snap.leftScore).toBe(10)
    expect(snap.rightScore).toBe(5)
    expect(snap.synced).toBe(false)
    expect(snap.historyStack).toHaveLength(1)
    expect(snap.historyStack[0].leftScore).toBe(9)
  })

  it('deep-clones history entries', () => {
    const history = [
      { leftScore: 9, rightScore: 5, segmentScores: [{ segmentNo: 1, leftScore: 10, rightScore: 8 }], segmentSwitchPending: false, sidesSwapped: false, lastScoredSide: '' },
    ]
    const snap = buildFullSnapshot(buildScoreState(10, 5, [], false, false, ''), history, false)
    expect(snap.historyStack[0].segmentScores).not.toBe(history[0].segmentScores)
  })

  it('synced flag reflects input', () => {
    expect(buildFullSnapshot(buildScoreState(0, 0, [], false, false, ''), [], true).synced).toBe(true)
    expect(buildFullSnapshot(buildScoreState(0, 0, [], false, false, ''), [], false).synced).toBe(false)
  })
})

// ============================================================================
// Integration scenarios — relay match simulation
// ============================================================================
describe('relay match simulation (6-person, base 10)', () => {
  const BASE = 10
  const MEMBER_COUNT = 6
  const TARGET = computeTargetScore(BASE, MEMBER_COUNT, MEMBER_COUNT)

  it('full match: segment targets and match end in sequence', () => {
    let leftScore = 0
    let rightScore = 0
    const segmentScores = []
    let segmentSwitchPending = false
    let sidesSwapped = false
    let matchEnded = false
    let currentSegmentNo = 1

    // Helper: simulate one point
    function score(side) {
      if (matchEnded || segmentSwitchPending) return
      if (side === 'left') leftScore += 1
      else rightScore += 1

      if (leftScore >= TARGET || rightScore >= TARGET) {
        appendSegmentScore(segmentScores, currentSegmentNo, leftScore, rightScore)
        matchEnded = true
        return
      }

      const segTarget = computeSegmentTarget(BASE, currentSegmentNo, TARGET)
      if (isSegmentTargetReached(leftScore, rightScore, segTarget)) {
        segmentSwitchPending = true
        appendSegmentScore(segmentScores, currentSegmentNo, leftScore, rightScore)
        return
      }
    }

    function advance() {
      if (!shouldAdvanceSegment(segmentSwitchPending, matchEnded)) return
      segmentSwitchPending = false
      sidesSwapped = toggleSidesSwapped(sidesSwapped)
      currentSegmentNo = computeCurrentSegmentIndex(segmentScores.length, MEMBER_COUNT) + 1
    }

    // ---- Segment 1: left scores to 10 ----
    // ---- Segment 1: score to 10 ----
    for (let i = 0; i < 9; i++) score('left')
    expect(leftScore).toBe(9)

    score('left') // reaches 10
    expect(leftScore).toBe(10)
    expect(segmentSwitchPending).toBe(true)
    expect(segmentScores).toHaveLength(1)
    expect(segmentScores[0]).toEqual({ segmentNo: 1, leftScore: 10, rightScore: 0 })
    advance()
    expect(segmentSwitchPending).toBe(false)
    expect(sidesSwapped).toBe(true)
    expect(currentSegmentNo).toBe(2)

    // ---- Segment 2 (target 20): right=0, need 20 more ----
    for (let i = 0; i < 19; i++) score('right')
    expect(rightScore).toBe(19)
    score('right') // → 20, hits segment target
    expect(rightScore).toBe(20)
    expect(segmentSwitchPending).toBe(true)
    expect(segmentScores).toHaveLength(2)
    advance()
    expect(sidesSwapped).toBe(false)
    expect(currentSegmentNo).toBe(3)

    // ---- Segment 3 (target 30): left=10, need 20 more → left=30 ----
    for (let i = 0; i < 20; i++) score('left')
    expect(leftScore).toBe(30)
    expect(segmentSwitchPending).toBe(true)
    advance()

    // ---- Segment 4 (target 40): left=30, right=20 → need 10 more on left ----
    for (let i = 0; i < 10; i++) score('left')
    expect(leftScore).toBe(40)
    advance()

    // ---- Segment 5 (target 50): left=40 → need 10 more ----
    for (let i = 0; i < 10; i++) score('left')
    expect(leftScore).toBe(50)
    advance()
    expect(segmentScores).toHaveLength(5)
    expect(currentSegmentNo).toBe(6)

    // ---- Final segment 6: left=50, target=60 ----
    for (let i = 0; i < 10; i++) score('left')
    expect(leftScore).toBe(60)
    expect(matchEnded).toBe(true)
    expect(segmentScores).toHaveLength(6)
    expect(segmentScores[5].segmentNo).toBe(6)
  })

  it('ends early if one side dominates — match ends at target', () => {
    let leftScore = 0
    let rightScore = 0
    const segScores = []
    let pending = false
    let ended = false
    let segNo = 1

    function add(side) {
      if (ended || pending) return
      if (side === 'left') leftScore += 1
      else rightScore += 1

      if (leftScore >= TARGET || rightScore >= TARGET) {
        appendSegmentScore(segScores, segNo, leftScore, rightScore)
        ended = true
        return
      }
      const segTarget = computeSegmentTarget(BASE, segNo, TARGET)
      if (isSegmentTargetReached(leftScore, rightScore, segTarget)) {
        pending = true
        appendSegmentScore(segScores, segNo, leftScore, rightScore)
      }
    }

    function advance() {
      if (!shouldAdvanceSegment(pending, ended)) return
      pending = false
      segNo = computeCurrentSegmentIndex(segScores.length, MEMBER_COUNT) + 1
    }

    // Pure left domination — left scores all 60 points without right scoring
    while (!ended) {
      add('left')
      advance()
    }

    expect(leftScore).toBe(60)
    expect(rightScore).toBe(0)
    expect(ended).toBe(true)
    expect(segScores).toHaveLength(6)
  })
})

// ============================================================================
// side swap visual -> logical mapping (full round-trip)
// ============================================================================
describe('visual/logical side mapping round-trip', () => {
  it('after N toggles (N odd=swapped, N even=original)', () => {
    // 6-segment relay: 5 mid-segment switches
    // After 1 switch: swapped
    expect(visualToLogicalSide('left', true)).toBe('right')
    // After 2 switches: back to original
    expect(visualToLogicalSide('left', false)).toBe('left')

    // After 5 switches (odd): swapped
    expect(visualToLogicalSide('left', true)).toBe('right')
  })

  it('scoring on the correct logical side after swap', () => {
    // Before swap: visual left = logical left
    let sidesSwapped = false
    const visualLeft = visualToLogicalSide('left', sidesSwapped)
    expect(visualLeft).toBe('left')

    // After swap: visual left = logical right
    sidesSwapped = true
    expect(visualToLogicalSide('left', sidesSwapped)).toBe('right')
    expect(visualToLogicalSide('right', sidesSwapped)).toBe('left')
  })
})
