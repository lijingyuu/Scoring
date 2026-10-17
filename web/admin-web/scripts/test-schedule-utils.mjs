import assert from 'node:assert/strict'
import {
  buildKnockoutBracketLayout,
  clampBracketScale,
  calculateFitScale,
  centerBracketPosition,
  getKnockoutParticipantName,
  isThirdPlaceMatch,
} from '../src/utils/bracketLayout.js'
import {
  groupMatchesByRound,
  findStandings,
  hasVisibleGroupContent,
  getStandingRankText,
  getStandingCellText,
  resolveStandingColumns,
  groupName,
} from '../src/utils/groupStandings.js'
import {
  splitDoublesName,
  isDoublesName,
  isDoublesMatch,
  sportText,
  tournamentTypeText,
  matchStatusText,
} from '../src/utils/matchFormatters.js'

console.log('Testing bracketLayout...')
{
  const mockMatches = [
    { id: 'm1', roundNum: 1, matchIndex: 0, leftPlayerId: 'p1', rightPlayerId: 'p2', nextMatchId: 'm3' },
    { id: 'm2', roundNum: 1, matchIndex: 1, leftPlayerId: 'p3', rightPlayerId: 'p4', nextMatchId: 'm3' },
    { id: 'm3', roundNum: 2, matchIndex: 0, leftPlayerId: 'p1', rightPlayerId: 'p3' },
    { id: 'm4', roundNum: 2, matchIndex: 1, matchRole: 1, leftPlayerId: 'p2', rightPlayerId: 'p4' }, // 季军赛
  ]

  const layout = buildKnockoutBracketLayout(mockMatches)
  assert.equal(layout.rounds.length, 2, 'Should have 2 rounds')
  assert.equal(layout.nodes.length, 4, 'Should have 4 nodes (2 R1 + 1 Final + 1 Third Place)')
  assert.ok(layout.segments.length > 0, 'Should generate connector segments')

  const thirdPlaceNode = layout.nodes.find((n) => isThirdPlaceMatch(n.match))
  assert.ok(thirdPlaceNode, 'Should have third place node')
  assert.ok(thirdPlaceNode.top > 0, 'Third place node top should be positioned')

  // Participant name tests
  const playerMap = new Map([['p1', 'Alice / Bob'], ['p2', 'Charlie / David']])
  assert.equal(getKnockoutParticipantName({ match: mockMatches[0], side: 'left', playerMap }), 'Alice / Bob')
  assert.equal(getKnockoutParticipantName({ match: { roundNum: 1, leftPlayerId: 'p1' }, side: 'right', playerMap }), '轮空位')
  assert.equal(getKnockoutParticipantName({ match: { roundNum: 2 }, side: 'left', playerMap }), '待定')

  // Viewport scale & fit tests
  assert.equal(clampBracketScale(0.05), 0.2)
  assert.equal(clampBracketScale(2.5), 1.6)
  assert.equal(clampBracketScale(1.0), 1.0)

  const fit = calculateFitScale({ width: 800, height: 600 }, { width: 1200, height: 800 })
  assert.ok(fit > 0.5 && fit <= 1.1)

  const center = centerBracketPosition({ width: 600, height: 400 }, { width: 1000, height: 800 }, 1.0)
  assert.equal(center.x, 200)
}
console.log('✓ bracketLayout passed')

console.log('Testing groupStandings...')
{
  const matches = [
    { id: 'gm1', roundNum: 1, leftPlayerId: 'p1', rightPlayerId: 'p2' },
    { id: 'gm2', roundNum: 2, leftPlayerId: 'p1', rightPlayerId: 'p3' },
    { id: 'gm3', roundNum: 1, leftPlayerId: 'p3', rightPlayerId: 'p4' },
  ]
  const rounds = groupMatchesByRound(matches)
  assert.equal(rounds.length, 2)
  assert.equal(rounds[0].roundNum, 1)
  assert.equal(rounds[0].matches.length, 2)
  assert.equal(rounds[1].roundNum, 2)
  assert.equal(rounds[1].matches.length, 1)

  const mockStandingsData = {
    groups: [
      {
        groupNo: 1,
        standings: [
          { playerId: 'p1', playerName: 'Player 1', rank: 1, matchWins: 2, matchLosses: 0, netGames: 4, netPoints: 20, qualified: true },
          { playerId: 'p2', playerName: 'Player 2', rank: 2, matchWins: 1, matchLosses: 1, netGames: 0, netPoints: 0, qualified: true },
        ],
      },
    ],
  }
  const standings = findStandings(mockStandingsData, 1)
  assert.equal(standings.length, 2)
  assert.equal(getStandingRankText(standings[0]), '1')
  assert.equal(getStandingCellText(standings[0], 'record'), '2胜0负')
  assert.equal(getStandingCellText(standings[0], 'netGames'), '+4')
  assert.equal(getStandingCellText(standings[0], 'netPoints'), '+20')

  assert.equal(groupName(1), 'A组')
  assert.equal(groupName(2), 'B组')
  assert.equal(groupName(26), 'Z组')

  const cols = resolveStandingColumns({ rankingTemplate: 'DEFAULT', isTeamTournament: false })
  assert.ok(cols.some((c) => c.key === 'record'))
  assert.ok(cols.some((c) => c.key === 'netGames'))
}
console.log('✓ groupStandings passed')

console.log('Testing matchFormatters...')
{
  assert.deepEqual(splitDoublesName('Alice / Bob'), ['Alice', 'Bob'])
  assert.deepEqual(splitDoublesName('张三／李四'), ['张三', '李四'])
  assert.deepEqual(splitDoublesName('Single Player'), ['Single Player'])
  assert.equal(isDoublesName('Alice / Bob'), true)
  assert.equal(isDoublesName('张三／李四'), true)
  assert.equal(isDoublesName('Single'), false)
  assert.equal(isDoublesMatch({ leftName: 'Alice / Bob', isTeamMatch: false }), true)
  assert.equal(isDoublesMatch({ leftName: 'Team A', isTeamMatch: true }), false)

  assert.equal(sportText({ sportType: 1 }), '排球')
  assert.equal(sportText({ sportType: 0, participantType: 0 }), '羽毛球个人')
  assert.equal(tournamentTypeText(0), '淘汰赛')
  assert.equal(tournamentTypeText(1), '小组+淘汰')
  assert.equal(tournamentTypeText(2), '循环赛')
  assert.equal(matchStatusText(0), '未开赛')
  assert.equal(matchStatusText(1), '进行中')
  assert.equal(matchStatusText(2), '已结束')
}
console.log('✓ matchFormatters passed')

console.log('ALL TESTS PASSED!')
