import { describe, expect, it } from 'vitest'
import {
  buildKnockoutBracketLayout,
  calculateFitScale,
  calculateOverviewScale,
  centerBracketPosition,
  clampBracketScale,
  keepViewportCenterPosition,
  KNOCKOUT_BRACKET_LAYOUT,
  KNOCKOUT_BRACKET_VIEWPORT,
} from './knockout-bracket-layout'

function match(id, roundNum, matchIndex, nextMatchId = '') {
  return { id, roundNum, matchIndex, nextMatchId }
}

describe('knockout bracket layout', () => {
  it('places each later-round match at the midpoint of its two source matches', () => {
    const layout = buildKnockoutBracketLayout([
      match('r1-0', 1, 0, 'r2-0'),
      match('r1-1', 1, 1, 'r2-0'),
      match('r1-2', 1, 2, 'r2-1'),
      match('r1-3', 1, 3, 'r2-1'),
      match('r2-0', 2, 0, 'final'),
      match('r2-1', 2, 1, 'final'),
      match('final', 3, 0),
    ])
    const nodeById = new Map(layout.nodes.map((node) => [node.id, node]))

    expect(nodeById.get('r2-0').center).toBe((nodeById.get('r1-0').center + nodeById.get('r1-1').center) / 2)
    expect(nodeById.get('final').center).toBe((nodeById.get('r2-0').center + nodeById.get('r2-1').center) / 2)
    expect(layout.segments.filter((segment) => segment.axis === 'vertical')).toHaveLength(3)
    expect(nodeById.get('r1-0').left).toBe(KNOCKOUT_BRACKET_LAYOUT.boardPaddingX)
    expect(layout.width).toBe(
      nodeById.get('final').left
        + KNOCKOUT_BRACKET_LAYOUT.cardWidth
        + KNOCKOUT_BRACKET_LAYOUT.boardPaddingX,
    )
    expect(layout.firstRoundHeight).toBe(
      nodeById.get('r1-3').top + KNOCKOUT_BRACKET_LAYOUT.cardHeight,
    )
  })

  it('keeps the third-place match below the final without moving the final', () => {
    const layout = buildKnockoutBracketLayout([
      match('semi-0', 1, 0, 'final'),
      match('semi-1', 1, 1, 'final'),
      match('final', 2, 0),
      { ...match('third', 2, 1), matchRole: 1 },
    ])
    const nodeById = new Map(layout.nodes.map((node) => [node.id, node]))

    expect(nodeById.get('final').center).toBe(
      (nodeById.get('semi-0').center + nodeById.get('semi-1').center) / 2,
    )
    expect(nodeById.get('third').top).toBe(
      nodeById.get('final').top
        + KNOCKOUT_BRACKET_LAYOUT.cardHeight
        + KNOCKOUT_BRACKET_LAYOUT.thirdPlaceGap,
    )
  })

  it('limits overview scale and centers the board when it fits', () => {
    expect(calculateOverviewScale({ width: 2000, height: 1000 }, { width: 750, height: 1000 }))
      .toBeCloseTo(0.375, 3)
    expect(calculateFitScale({ width: 2000, height: 1000 }, { width: 750, height: 1000 }))
      .toBeCloseTo(0.351, 3)
    expect(clampBracketScale(0.1)).toBe(KNOCKOUT_BRACKET_VIEWPORT.minScale)
    expect(clampBracketScale(3)).toBe(KNOCKOUT_BRACKET_VIEWPORT.maxScale)
    expect(centerBracketPosition({ width: 500, height: 400 }, { width: 750, height: 1000 }, 1))
      .toEqual({ x: 125, y: 300 })
  })

  it('keeps the same viewport center when zooming with buttons', () => {
    const next = keepViewportCenterPosition(
      { width: 1000, height: 1000 },
      { width: 500, height: 500 },
      { x: -250, y: -100, scale: 1 },
      1.5,
    )

    expect(next.x).toBe(-500)
    expect(next.y).toBe(-275)
  })
})
