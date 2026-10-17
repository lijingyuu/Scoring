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

export function getStandingRankText(standing) {
  if (standing?.displayRankText) return standing.displayRankText
  return String(standing?.rank ?? '-')
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

export function resolveStandingColumns({ rankingTemplate, isTeamTournament, isVolleyball, teamMatchTemplate } = {}) {
  if (isVolleyball) {
    if (rankingTemplate === 'POINT_RATIO') return POINT_RATE_COLUMNS
    return FIVB_COLUMNS
  }

  if (isTeamTournament) {
    if (teamMatchTemplate === 2) return RELAY_COLUMNS
    return TEAM_COLUMNS
  }

  if (rankingTemplate === 'POINT_RATIO') return POINT_RATE_COLUMNS
  return DIFFERENCE_COLUMNS
}

export function getStandingCellText(standing, colKey) {
  if (!standing) return '-'
  if (colKey === 'record') {
    const wins = standing.matchWins ?? 0
    const losses = standing.matchLosses ?? 0
    return `${wins}胜${losses}负`
  }
  if (colKey === 'netGames') {
    const net = Number(standing.netGames ?? (standing.gameWins ?? 0) - (standing.gameLosses ?? 0))
    return net > 0 ? `+${net}` : String(net)
  }
  if (colKey === 'netPoints') {
    const net = Number(standing.netPoints ?? (standing.pointsFor ?? 0) - (standing.pointsAgainst ?? 0))
    return net > 0 ? `+${net}` : String(net)
  }
  if (colKey === 'pointWinRate') {
    if (standing.pointWinRate === null || standing.pointWinRate === undefined) return '-'
    const rate = Number(standing.pointWinRate)
    return Number.isFinite(rate) ? rate.toFixed(3) : String(standing.pointWinRate)
  }
  if (colKey === 'gameWinRate') {
    if (standing.gameWinRate === null || standing.gameWinRate === undefined) return '-'
    const rate = Number(standing.gameWinRate)
    return Number.isFinite(rate) ? rate.toFixed(3) : String(standing.gameWinRate)
  }
  if (colKey === 'matchPoints') {
    return String(standing.matchPoints ?? 0)
  }
  if (colKey === 'teamItemNetWins') {
    const net = Number(standing.teamItemNetWins ?? (standing.teamItemWins ?? 0) - (standing.teamItemLosses ?? 0))
    return net > 0 ? `+${net}` : String(net)
  }

  const customVal = standing[colKey]
  if (customVal !== undefined && customVal !== null) {
    return String(customVal)
  }
  return '-'
}

export function groupName(groupNo) {
  const value = Number(groupNo || 0)
  if (value >= 1 && value <= 26) return `${String.fromCharCode(64 + value)}组`
  return value > 0 ? `第${value}组` : '小组赛'
}
