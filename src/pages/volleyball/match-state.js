const STORAGE_KEY = 'volleyball_scoreboard_state'

export const VOLLEYBALL_LINEUP_ROUTE = '/pages/volleyball/lineup'
export const VOLLEYBALL_SCOREBOARD_ROUTE = '/pages/volleyball/scoreboard'

export function emptyCourt() {
  return Array(6).fill('')
}

export function cloneCourt(court) {
  const source = Array.isArray(court) ? court.slice(0, 6) : []
  while (source.length < 6) {
    source.push('')
  }
  return source
}

export function toggleSide(side) {
  return side === 'right' ? 'left' : 'right'
}

export function formatTeamName(name) {
  if (!name) return ''
  const text = String(name).trim()
  if (!text) return ''
  return text.endsWith('队') ? text : text + '队'
}

export function normalizeTeam(participant) {
  return {
    id: participant?.id || '',
    name: participant?.name || '队伍',
    members: Array.isArray(participant?.members)
      ? participant.members.map((member) => ({
          id: member.id,
          name: member.name,
          jerseyNumber: Number(member.jerseyNumber || 0),
          libero: !!member.libero,
          captain: !!member.captain,
        }))
      : [],
  }
}

export function createEmptyMatchState() {
  return {
    leftScore: 0,
    rightScore: 0,
    leftGameWins: 0,
    rightGameWins: 0,
    currentGameNo: 1,
    gameScores: [],
    serveSide: 'left',
    currentGameStartServeSide: 'left',
    leftTimeouts: 2,
    rightTimeouts: 2,
    leftCourt: emptyCourt(),
    rightCourt: emptyCourt(),
    baseLeftCourt: emptyCourt(),
    baseRightCourt: emptyCourt(),
    draftLeftCourt: emptyCourt(),
    draftRightCourt: emptyCourt(),
    draftServeSide: 'left',
    lineupReady: false,
    retiredSide: '',
    matchEnded: false,
    winnerName: '',
    historyStack: [],
  }
}

export function normalizeMatchState(raw) {
  const defaults = createEmptyMatchState()
  const state = raw && typeof raw === 'object' ? raw : {}
  return {
    ...defaults,
    ...state,
    leftScore: Number(state.leftScore || 0),
    rightScore: Number(state.rightScore || 0),
    leftGameWins: Number(state.leftGameWins || 0),
    rightGameWins: Number(state.rightGameWins || 0),
    currentGameNo: Number(state.currentGameNo || 1),
    gameScores: Array.isArray(state.gameScores) ? state.gameScores.map((item) => ({ ...item })) : [],
    serveSide: state.serveSide === 'right' ? 'right' : 'left',
    currentGameStartServeSide: state.currentGameStartServeSide === 'right' ? 'right' : 'left',
    leftTimeouts: Number(state.leftTimeouts ?? 2),
    rightTimeouts: Number(state.rightTimeouts ?? 2),
    leftCourt: cloneCourt(state.leftCourt),
    rightCourt: cloneCourt(state.rightCourt),
    baseLeftCourt: cloneCourt(state.baseLeftCourt),
    baseRightCourt: cloneCourt(state.baseRightCourt),
    draftLeftCourt: cloneCourt(state.draftLeftCourt),
    draftRightCourt: cloneCourt(state.draftRightCourt),
    draftServeSide: state.draftServeSide === 'right' ? 'right' : 'left',
    lineupReady: !!state.lineupReady,
    retiredSide: state.retiredSide || '',
    matchEnded: !!state.matchEnded,
    winnerName: state.winnerName || '',
    historyStack: Array.isArray(state.historyStack) ? state.historyStack.map((item) => ({ ...item })) : [],
  }
}

export function buildMatchStorageKey(matchId) {
  return matchId ? `${STORAGE_KEY}_${matchId}` : STORAGE_KEY
}

export function loadMatchState(matchId) {
  try {
    const cache = uni.getStorageSync(buildMatchStorageKey(matchId))
    if (!cache || typeof cache !== 'object') return null
    return normalizeMatchState(cache)
  } catch (_) {
    return null
  }
}

export function saveMatchState(matchId, state) {
  uni.setStorageSync(buildMatchStorageKey(matchId), normalizeMatchState(state))
}

export function clearMatchState(matchId) {
  try {
    uni.removeStorageSync(buildMatchStorageKey(matchId))
  } catch (_) {
    // noop
  }
}

export function buildMatchQuery(params = {}) {
  return Object.keys(params)
    .filter((key) => params[key] !== undefined && params[key] !== null && params[key] !== '')
    .map((key) => `${encodeURIComponent(key)}=${encodeURIComponent(params[key])}`)
    .join('&')
}

export function buildLineupUrl(params = {}) {
  const query = buildMatchQuery(params)
  return query ? `${VOLLEYBALL_LINEUP_ROUTE}?${query}` : VOLLEYBALL_LINEUP_ROUTE
}

export function buildScoreboardUrl(params = {}) {
  const query = buildMatchQuery(params)
  return query ? `${VOLLEYBALL_SCOREBOARD_ROUTE}?${query}` : VOLLEYBALL_SCOREBOARD_ROUTE
}
