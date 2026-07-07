import { describe, expect, it } from 'vitest'
import { findStandings, groupMatchesByRound, hasVisibleGroupContent } from './groups-data'

describe('tournament groups data helpers', () => {
  it('groups round-robin matches by camelCase round number', () => {
    const rounds = groupMatchesByRound([
      { id: 'm2', roundNum: 2 },
      { id: 'm1', roundNum: 1 },
      { id: 'm3', roundNum: 2 },
    ])

    expect(rounds).toEqual([
      { roundNum: 1, matches: [{ id: 'm1', roundNum: 1 }] },
      { roundNum: 2, matches: [{ id: 'm2', roundNum: 2 }, { id: 'm3', roundNum: 2 }] },
    ])
  })

  it('accepts snake_case fields defensively for mini-program payload differences', () => {
    const rounds = groupMatchesByRound([
      { id: 'm1', round_num: 1 },
      { id: 'm2', round_num: 1 },
    ])
    const standings = findStandings({ groups: [{ group_no: 1, standings: [{ playerId: 'p1' }] }] }, 1)

    expect(rounds).toHaveLength(1)
    expect(rounds[0].matches).toHaveLength(2)
    expect(standings).toEqual([{ playerId: 'p1' }])
  })

  it('reports visible content when standings or matches exist', () => {
    expect(hasVisibleGroupContent([{ groupNo: 1, matches: [{ id: 'm1', roundNum: 1 }] }], { groups: [] })).toBe(true)
    expect(hasVisibleGroupContent([{ groupNo: 1, matches: [] }], { groups: [{ groupNo: 1, standings: [{ playerId: 'p1' }] }] })).toBe(true)
    expect(hasVisibleGroupContent([{ groupNo: 1, matches: [] }], { groups: [] })).toBe(false)
  })
})
