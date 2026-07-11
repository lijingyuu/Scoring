/**
 * Relay scoring pure-logic helpers.
 *
 * Extracted from team-relay.vue and team-lineup.vue so the state machine
 * and chain validations can be unit-tested independently of the UI layer.
 */

// ---- segment / score computations -------------------------------------------

/**
 * @param {number} segmentScoresLength  current length of segmentScores array
 * @param {number} itemsLength          total number of relay segments (items)
 * @returns {number} 0-based index of the current segment
 */
export function computeCurrentSegmentIndex(segmentScoresLength, itemsLength) {
  if (!itemsLength) return 0
  return Math.min(segmentScoresLength, itemsLength - 1)
}

/**
 * @param {number} baseScore        points-per-segment (relayBaseScore)
 * @param {number} itemsLength      total relay segments
 * @param {number} relayMemberCount fallback segments (used pre-lineup)
 * @returns {number}
 */
export function computeTargetScore(baseScore, itemsLength, relayMemberCount) {
  return baseScore * (itemsLength || relayMemberCount || 6)
}

/**
 * @param {number} baseScore
 * @param {number} currentSegmentNo  1-based current-segment number
 * @param {number} targetScore
 * @returns {number}
 */
export function computeSegmentTarget(baseScore, currentSegmentNo, targetScore) {
  return Math.min(baseScore * currentSegmentNo, targetScore)
}

/**
 * @param {number} matchStatus  DB match status, 2 = finished
 * @param {number} targetScore
 * @param {number} leftScore
 * @param {number} rightScore
 * @returns {boolean}
 */
export function isRelayMatchEnded(matchStatus, targetScore, leftScore, rightScore) {
  if (matchStatus === 2) return true
  return targetScore > 0 && (leftScore >= targetScore || rightScore >= targetScore)
}

/**
 * @param {number} leftScore
 * @param {number} rightScore
 * @param {number} segmentTarget
 * @returns {boolean}
 */
export function isSegmentTargetReached(leftScore, rightScore, segmentTarget) {
  return leftScore >= segmentTarget || rightScore >= segmentTarget
}

/**
 * Guard for advanceSegment(): only allow when pending and match not ended.
 * @param {boolean} segmentSwitchPending
 * @param {boolean} matchEnded
 * @returns {boolean}
 */
export function shouldAdvanceSegment(segmentSwitchPending, matchEnded) {
  return segmentSwitchPending && !matchEnded
}

/**
 * @param {boolean} currentSwapped
 * @returns {boolean}
 */
export function toggleSidesSwapped(currentSwapped) {
  return !currentSwapped
}

/**
 * Map a visual side ('left'|'right') back to the logical/original side.
 * @param {'left'|'right'} visualSide
 * @param {boolean} sidesSwapped
 * @returns {'left'|'right'}
 */
export function visualToLogicalSide(visualSide, sidesSwapped) {
  if (!sidesSwapped) return visualSide
  return visualSide === 'left' ? 'right' : 'left'
}

/**
 * Which logical side is displayed on the given visual side.
 * @param {'left'|'right'} visualSide
 * @param {boolean} sidesSwapped
 * @returns {'left'|'right'}
 */
export function logicalSideOnVisual(visualSide, sidesSwapped) {
  return visualToLogicalSide(visualSide, sidesSwapped)
}

// ---- score-state snapshot ---------------------------------------------------

/**
 * @param {number} leftScore
 * @param {number} rightScore
 * @param {Array<{segmentNo:number,leftScore:number,rightScore:number}>} segmentScores
 * @param {boolean} segmentSwitchPending
 * @param {boolean} sidesSwapped
 * @param {string} lastScoredSide
 * @returns {object}
 */
export function buildScoreState(leftScore, rightScore, segmentScores, segmentSwitchPending, sidesSwapped, lastScoredSide) {
  return {
    leftScore,
    rightScore,
    segmentScores: segmentScores.map((item) => ({ ...item })),
    segmentSwitchPending,
    sidesSwapped,
    lastScoredSide,
  }
}

/**
 * Returns a new object with the snapshot fields applied (does NOT mutate refs).
 * @param {object} snapshot
 * @returns {{ leftScore:number, rightScore:number, segmentScores:Array, segmentSwitchPending:boolean, sidesSwapped:boolean, lastScoredSide:string }}
 */
export function parseScoreState(snapshot) {
  return {
    leftScore: Number(snapshot?.leftScore || 0),
    rightScore: Number(snapshot?.rightScore || 0),
    segmentScores: Array.isArray(snapshot?.segmentScores)
      ? snapshot.segmentScores.map((item) => ({ ...item }))
      : [],
    segmentSwitchPending: !!snapshot?.segmentSwitchPending,
    sidesSwapped: !!snapshot?.sidesSwapped,
    lastScoredSide: snapshot?.lastScoredSide || '',
  }
}

// ---- segment score recording ------------------------------------------------

/**
 * Append a segment-end score record (idempotent).
 * @param {Array<{segmentNo:number}>} segmentScores  mutable array
 * @param {number} segmentNo
 * @param {number} leftScore
 * @param {number} rightScore
 * @returns {boolean} true if a new entry was added
 */
export function appendSegmentScore(segmentScores, segmentNo, leftScore, rightScore) {
  if (segmentScores.some((item) => item.segmentNo === segmentNo)) return false
  segmentScores.push({ segmentNo, leftScore, rightScore })
  return true
}

// ---- relay chain generation & validation ------------------------------------

/**
 * Build segment items from the two sides' player orders.
 * Each side's order is an array of member IDs (length = memberCount).
 * Generates: order[0]+order[1], order[1]+order[2], ..., order[N-1]+order[0].
 *
 * @param {string[]} leftOrder
 * @param {string[]} rightOrder
 * @param {number} memberCount
 * @returns {Array<{displayOrder:number,itemCode:string,itemName:string,playerCount:number,leftMemberIds:string[],rightMemberIds:string[],leftMembers:Array,rightMembers:Array,status:number}>}
 */
export function buildRelayItemsFromOrders(leftOrder, rightOrder, memberCount) {
  const items = []
  for (let i = 0; i < memberCount; i++) {
    const nextIndex = (i + 1) % memberCount
    items.push({
      displayOrder: i + 1,
      itemCode: 'R' + (i + 1),
      itemName: '第 ' + (i + 1) + ' 段',
      playerCount: 2,
      leftMemberIds: [leftOrder[i] || '', leftOrder[nextIndex] || ''],
      rightMemberIds: [rightOrder[i] || '', rightOrder[nextIndex] || ''],
      leftMembers: [],
      rightMembers: [],
      status: 0,
    })
  }
  return items
}

/**
 * Validate the relay chain: each segment is a pair, adjacent segments share
 * the "right" player of the current == "left" player of the next, and all
 * first members are unique (each player appears exactly once as the lead).
 *
 * @param {Array<[string,string]>} pairs  ordered list of [firstId, secondId]
 * @returns {{ valid: boolean, reason?: string }}
 */
export function validateRelayChain(pairs) {
  if (!Array.isArray(pairs) || pairs.length < 3) {
    return { valid: false, reason: 'at least 3 segments required' }
  }
  const firstMembers = new Set()
  for (let i = 0; i < pairs.length; i++) {
    const current = pairs[i]
    const next = pairs[(i + 1) % pairs.length]
    if (!Array.isArray(current) || current.length !== 2) {
      return { valid: false, reason: 'segment ' + (i + 1) + ' must be a pair' }
    }
    if (!current[0] || !current[1]) {
      return { valid: false, reason: 'segment ' + (i + 1) + ' has empty member' }
    }
    if (firstMembers.has(current[0])) {
      return { valid: false, reason: 'duplicate lead member in segment ' + (i + 1) }
    }
    firstMembers.add(current[0])
    if (current[1] !== next[0]) {
      return { valid: false, reason: 'chain broken at segment ' + (i + 1) + ': ' + current[1] + ' != ' + next[0] }
    }
  }
  return { valid: true }
}

/**
 * Extract the relay order array from existing saved items.
 * @param {Array} items      sorted relay items from the API
 * @param {'left'|'right'} side
 * @param {number} count     expected member count
 * @returns {string[]}
 */
export function buildRelayOrderFromItems(items, side, count) {
  const key = side === 'left' ? 'leftMemberIds' : 'rightMemberIds'
  const order = Array.from({ length: count }, () => '')
  items.forEach((item, index) => {
    if (index < count && Array.isArray(item[key])) {
      order[index] = item[key][0] || ''
    }
  })
  return order
}

// ---- utilities --------------------------------------------------------------

/**
 * @param {string} matchId
 * @returns {string}
 */
export function relayStorageKey(matchId) {
  return matchId ? 'team_relay_scoreboard_state_' + matchId : 'team_relay_scoreboard_state'
}

/**
 * @param {Array} items  relay segment items from the API
 * @returns {boolean}
 */
export function isLineupComplete(items) {
  return Array.isArray(items) && items.length > 0 && items.every((item) => {
    return Array.isArray(item.leftMembers) && item.leftMembers.length === 2
      && Array.isArray(item.rightMembers) && item.rightMembers.length === 2
  })
}

/**
 * Validate that the lineup is ready for relay scoring.
 * @param {string[]} leftOrder   compact order array (empty slots removed)
 * @param {string[]} rightOrder
 * @param {number} relayMemberCount
 * @returns {{ valid: boolean, message?: string }}
 */
export function validateRelayLineup(leftOrder, rightOrder, relayMemberCount) {
  const leftCount = leftOrder.filter(Boolean).length
  const rightCount = rightOrder.filter(Boolean).length
  if (leftCount !== relayMemberCount || rightCount !== relayMemberCount) {
    return {
      valid: false,
      message: '接力赛每队需要填满 ' + relayMemberCount + ' 名出场队员',
    }
  }
  return { valid: true }
}

/**
 * Build full snapshot (score + history + synced).
 * @param {object} scoreState   result of buildScoreState()
 * @param {Array} historyStack
 * @param {boolean} synced
 * @returns {object}
 */
export function buildFullSnapshot(scoreState, historyStack, synced) {
  return {
    ...scoreState,
    historyStack: historyStack.map((item) => ({
      ...item,
      segmentScores: Array.isArray(item.segmentScores)
        ? item.segmentScores.map((score) => ({ ...score }))
        : [],
    })),
    synced,
  }
}
