import { describe, expect, it } from 'vitest'
import {
  findStandings,
  formatStandingCell,
  getStandingColumns,
  getStandingRankText,
  groupMatchesByRound,
  hasVisibleGroupContent,
} from './groups-data'

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

  it('prefers display rank text for round robin rankings', () => {
    expect(getStandingRankText({ displayRankText: '1', rank: 2 }, true)).toBe('1')
    expect(getStandingRankText({ displayRankText: '2', rank: 3 }, false)).toBe('2')
    expect(getStandingRankText({ rank: 3 }, false)).toBe('3')
    expect(getStandingRankText({}, true)).toBe('-')
  })

  it('uses net game and net point columns for difference ranking templates', () => {
    expect(getStandingColumns({ template: 'BWF_BADMINTON' }).map((column) => column.key))
      .toEqual(['record', 'netGames', 'netPoints'])
    expect(getStandingColumns({ template: 'CAMPUS_VOLLEYBALL' }).map((column) => column.key))
      .toEqual(['record', 'netGames', 'netPoints'])
    expect(getStandingColumns(null).map((column) => column.key))
      .toEqual(['record', 'netGames', 'netPoints'])
  })

  it('uses match points and ratio columns for FIVB ranking template', () => {
    expect(getStandingColumns({ template: 'FIVB_VOLLEYBALL' }))
      .toEqual([
        { key: 'record', label: '胜负' },
        { key: 'matchPoints', label: '积分' },
        { key: 'gameWinRate', label: '胜负局比' },
        { key: 'pointWinRate', label: '得失分比' },
      ])
  })

  it('uses team standing columns when team ranking criteria are enabled', () => {
    expect(getStandingColumns({ template: 'BADMINTON_TEAM_COMMON_1', priorities: ['MATCH_WINS', 'TEAM_ITEM_NET_WINS'] }))
      .toEqual([
        { key: 'record', label: '胜负' },
        { key: 'teamItemNetWins', label: '场内大分' },
        { key: 'netGames', label: '场内局' },
        { key: 'netPoints', label: '局内小分' },
      ])
  })

  it('uses custom priority columns for custom ranking templates', () => {
    expect(getStandingColumns({
      template: 'CUSTOM',
      priorities: ['MATCH_WINS', 'MATCH_WIN_DIFF', 'MATCH_WIN_RATE', 'GAME_WINS', 'TWO_WAY_HEAD_TO_HEAD'],
    }))
      .toEqual([
        { key: 'matchWins', label: '胜场' },
        { key: 'matchWinDiff', label: '净胜场' },
        { key: 'matchWinRate', label: '胜负场比' },
        { key: 'gameWins', label: '胜局数' },
      ])
  })

  it('uses custom team item and child point columns', () => {
    expect(getStandingColumns({
      template: 'CUSTOM',
      priorities: ['TEAM_ITEM_NET_WINS', 'TEAM_ITEM_WIN_RATE', 'TEAM_CHILD_GAME_WINS', 'TEAM_CHILD_NET_GAMES', 'TEAM_CHILD_GAME_WIN_RATE', 'TEAM_CHILD_NET_POINTS', 'TEAM_CHILD_POINT_WIN_RATE'],
    }))
      .toEqual([
        { key: 'teamItemNetWins', label: '净胜大分' },
        { key: 'teamItemWinRate', label: '大分得失比' },
        { key: 'gameWins', label: '胜局数' },
        { key: 'netGames', label: '净胜局' },
        { key: 'gameWinRate', label: '胜负局比' },
        { key: 'netPoints', label: '净胜小分' },
        { key: 'pointWinRate', label: '小分得失比' },
      ])
  })

  it('formats standing cells for table display', () => {
    const standing = {
      matchWins: 2,
      matchLosses: 1,
      matchWinRate: '2.0000',
      matchPoints: 5,
      teamItemNetWins: 2,
      teamItemWins: 7,
      teamItemWinRate: '3.5000',
      gameWins: 6,
      netGames: 3,
      netPoints: -4,
      gameWinRate: '999999.0000',
      pointWinRate: 1.23456,
    }

    expect(formatStandingCell(standing, 'record')).toBe('2-1')
    expect(formatStandingCell(standing, 'matchWins')).toBe('2')
    expect(formatStandingCell(standing, 'matchWinDiff')).toBe('+1')
    expect(formatStandingCell(standing, 'matchWinRate')).toBe('2.0000')
    expect(formatStandingCell(standing, 'gameWins')).toBe('6')
    expect(formatStandingCell(standing, 'matchPoints')).toBe('5')
    expect(formatStandingCell(standing, 'teamItemWins')).toBe('7')
    expect(formatStandingCell(standing, 'teamItemNetWins')).toBe('+2')
    expect(formatStandingCell(standing, 'teamItemWinRate')).toBe('3.5000')
    expect(formatStandingCell(standing, 'netGames')).toBe('+3')
    expect(formatStandingCell(standing, 'netPoints')).toBe('-4')
    expect(formatStandingCell(standing, 'gameWinRate')).toBe('∞')
    expect(formatStandingCell(standing, 'pointWinRate')).toBe('1.2346')
  })
})
