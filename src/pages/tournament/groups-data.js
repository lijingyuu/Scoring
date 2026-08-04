export function groupMatchesByRound(matches) {
  if (!Array.isArray(matches)) return []
  const map = {}
  for (const match of matches) {
    const roundNum = Number(match?.roundNum ?? match?.round_num)
    if (!Number.isFinite(roundNum) || roundNum <= 0) continue
    if (!map[roundNum]) map[roundNum] = []
    map[roundNum].push(match)
  }
  return Object.keys(map)
    .sort((a, b) => Number(a) - Number(b))
    .map((roundNum) => ({
      roundNum: Number(roundNum),
      matches: map[roundNum],
    }))
}

export function findStandings(standings, groupNo) {
  const groups = Array.isArray(standings?.groups) ? standings.groups : []
  return groups.find((group) => Number(group.groupNo ?? group.group_no) === Number(groupNo))?.standings || []
}

export function hasVisibleGroupContent(groups, standings) {
  if (!Array.isArray(groups) || !groups.length) return false
  return groups.some((group) => {
    const standingsRows = findStandings(standings, group?.groupNo ?? group?.group_no)
    const rounds = groupMatchesByRound(group?.matches)
    return standingsRows.length > 0 || rounds.length > 0
  })
}

export function getStandingRankText(standing, isRoundRobin) {
  if (standing?.displayRankText) return standing.displayRankText
  return String(standing?.rank ?? "-")
}

const DIFFERENCE_COLUMNS = [
  { key: 'record', label: '胜负' },
  { key: 'netGames', label: '净胜局' },
  { key: 'netPoints', label: '净胜分' },
]

const POINT_RATE_COLUMNS = [
  { key: 'record', label: '胜负' },
  { key: 'netGames', label: '净胜局' },
  { key: 'pointWinRate', label: '得失分比' },
]

const FIVB_COLUMNS = [
  { key: 'record', label: '胜负' },
  { key: 'matchPoints', label: '积分' },
  { key: 'gameWinRate', label: '胜负局比' },
  { key: 'pointWinRate', label: '得失分比' },
]

const TEAM_COLUMNS = [
  { key: 'record', label: '胜负' },
  { key: 'teamItemNetWins', label: '场内大分' },
  { key: 'netGames', label: '场内局' },
  { key: 'netPoints', label: '局内小分' },
]

const RELAY_COLUMNS = [
  { key: 'record', label: '胜负' },
  { key: 'pointWinRate', label: '小分得失比' },
]

const CUSTOM_COLUMN_MAP = {
  MATCH_WINS: { key: 'matchWins', label: '胜场' },
  MATCH_WIN_DIFF: { key: 'matchWinDiff', label: '净胜场' },
  MATCH_WIN_RATE: { key: 'matchWinRate', label: '胜负场比' },
  GAME_WINS: { key: 'gameWins', label: '胜局数' },
  NET_GAMES: { key: 'netGames', label: '净胜局' },
  GAME_WIN_RATE: { key: 'gameWinRate', label: '胜负局比' },
  NET_POINTS: { key: 'netPoints', label: '净胜分' },
  POINT_WIN_RATE: { key: 'pointWinRate', label: '得失分比' },
  TEAM_ITEM_NET_WINS: { key: 'teamItemNetWins', label: '净胜大分' },
  TEAM_ITEM_WIN_RATE: { key: 'teamItemWinRate', label: '大分得失比' },
  TEAM_CHILD_GAME_WINS: { key: 'gameWins', label: '胜局数' },
  TEAM_CHILD_NET_GAMES: { key: 'netGames', label: '净胜局' },
  TEAM_CHILD_GAME_WIN_RATE: { key: 'gameWinRate', label: '胜负局比' },
  TEAM_CHILD_NET_POINTS: { key: 'netPoints', label: '净胜小分' },
  TEAM_CHILD_POINT_WIN_RATE: { key: 'pointWinRate', label: '小分得失比' },
}

const CUSTOM_CRITERION_SOURCE = {
  MATCH_WINS: 'MATCH_RESULT',
  MATCH_WIN_DIFF: 'MATCH_RESULT',
  MATCH_WIN_RATE: 'MATCH_RESULT',
  GAME_WINS: 'GAME_RESULT',
  NET_GAMES: 'GAME_RESULT',
  GAME_WIN_RATE: 'GAME_RESULT',
  NET_POINTS: 'POINT_RESULT',
  POINT_WIN_RATE: 'POINT_RESULT',
  TWO_WAY_HEAD_TO_HEAD: 'HEAD_TO_HEAD',
  MULTI_HEAD_TO_HEAD: 'HEAD_TO_HEAD',
  HEAD_TO_HEAD: 'HEAD_TO_HEAD',
  TEAM_ITEM_NET_WINS: 'TEAM_ITEM_RESULT',
  TEAM_ITEM_WIN_RATE: 'TEAM_ITEM_RESULT',
  TEAM_CHILD_GAME_WINS: 'TEAM_GAME_RESULT',
  TEAM_CHILD_NET_GAMES: 'TEAM_GAME_RESULT',
  TEAM_CHILD_GAME_WIN_RATE: 'TEAM_GAME_RESULT',
  TEAM_CHILD_NET_POINTS: 'TEAM_POINT_RESULT',
  TEAM_CHILD_POINT_WIN_RATE: 'TEAM_POINT_RESULT',
}

export function getStandingColumns(rankingConfig) {
  if (rankingConfig?.template === 'CUSTOM' && Array.isArray(rankingConfig?.priorities)) {
    const priorities = rankingConfig.priorities
    const seenSources = new Set()
    const columns = priorities
      .map((criterion) => {
        const column = CUSTOM_COLUMN_MAP[criterion]
        if (!column) return null
        const source = CUSTOM_CRITERION_SOURCE[criterion]
        if (source && seenSources.has(source)) return null
        if (source) seenSources.add(source)
        return column
      })
      .filter(Boolean)
    if (columns.length) return columns
  }
  if (rankingConfig?.template === 'FIVB_VOLLEYBALL') return FIVB_COLUMNS
  if (
    rankingConfig?.template === 'BADMINTON_RELAY_COMMON_1'
    || (Array.isArray(rankingConfig?.priorities)
      && rankingConfig.priorities.includes('TEAM_CHILD_POINT_WIN_RATE')
      && !rankingConfig.priorities.includes('TEAM_ITEM_NET_WINS'))
  ) {
    return RELAY_COLUMNS
  }
  if (Array.isArray(rankingConfig?.priorities) && rankingConfig.priorities.includes('TEAM_ITEM_NET_WINS')) {
    return TEAM_COLUMNS
  }
  if (Array.isArray(rankingConfig?.priorities) && rankingConfig.priorities.includes('POINT_WIN_RATE')) {
    return POINT_RATE_COLUMNS
  }
  return DIFFERENCE_COLUMNS
}

export function formatStandingCell(standing, columnKey) {
  if (columnKey === 'record') {
    return `${numberText(standing?.matchWins)}-${numberText(standing?.matchLosses)}`
  }
  if (columnKey === 'matchWins') {
    return numberText(standing?.matchWins)
  }
  if (columnKey === 'matchWinDiff') {
    return signedNumberText(Number(standing?.matchWins) - Number(standing?.matchLosses))
  }
  if (columnKey === 'matchWinRate') {
    return rateText(standing?.matchWinRate)
  }
  if (columnKey === 'gameWins') {
    return numberText(standing?.gameWins)
  }
  if (columnKey === 'netGames') {
    return signedNumberText(standing?.netGames)
  }
  if (columnKey === 'netPoints') {
    return signedNumberText(standing?.netPoints)
  }
  if (columnKey === 'matchPoints') {
    return numberText(standing?.matchPoints)
  }
  if (columnKey === 'teamItemNetWins') {
    return signedNumberText(standing?.teamItemNetWins)
  }
  if (columnKey === 'teamItemWins') {
    return numberText(standing?.teamItemWins)
  }
  if (columnKey === 'teamItemWinRate') {
    return rateText(standing?.teamItemWinRate)
  }
  if (columnKey === 'gameWinRate') {
    return rateText(standing?.gameWinRate)
  }
  if (columnKey === 'pointWinRate') {
    return rateText(standing?.pointWinRate)
  }
  return '-'
}

function numberText(value) {
  const number = Number(value)
  return Number.isFinite(number) ? String(number) : '0'
}

function signedNumberText(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0'
  return number > 0 ? `+${number}` : String(number)
}

function rateText(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return '0.0000'
  if (number >= 999999) return '∞'
  return number.toFixed(4)
}
