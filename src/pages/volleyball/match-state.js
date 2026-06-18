import { sortVolleyballMembers } from '@/utils/volleyball-team'

const STORAGE_KEY = 'volleyball_scoreboard_state'
export const MAX_HISTORY_ENTRIES = 40

export const VOLLEYBALL_LINEUP_ROUTE = '/pages/volleyball/lineup'
export const VOLLEYBALL_SCOREBOARD_ROUTE = '/pages/volleyball/scoreboard'

export function emptyCourt() {
  return Array(6).fill('')
}

export function createEmptyLiberoSetup() {
  return {
    pairIndexes: [],
    libero1Id: '',
    libero2Id: '',
  }
}

export function createEmptyLiberoRuntime() {
  return {
    role1SlotIndex: -1,
    role2SlotIndex: -1,
    role1PlayerId: '',
    role2PlayerId: '',
  }
}

export function createEmptyMatchEventQueueItem() {
  return {
    seq: 0,
    type: '',
    gameNo: 1,
    leftScore: 0,
    rightScore: 0,
    serveSide: 'left',
    payload: {},
    syncStatus: 'pending',
  }
}

export function cloneCourt(court) {
  const source = Array.isArray(court) ? court.slice(0, 6) : []
  while (source.length < 6) {
    source.push('')
  }
  return source
}

export function cloneLiberoSetup(setup) {
  const source = setup && typeof setup === 'object' ? setup : {}
  const pairIndexes = Array.isArray(source.pairIndexes)
    ? source.pairIndexes
        .slice(0, 2)
        .map((item) => Number(item))
        .filter((item) => Number.isInteger(item) && item >= 0 && item < 6)
    : []
  return {
    pairIndexes,
    libero1Id: source.libero1Id || '',
    libero2Id: source.libero2Id || '',
  }
}

export function cloneLiberoRuntime(runtime) {
  const source = runtime && typeof runtime === 'object' ? runtime : {}
  const normalizeSlotIndex = (value) => {
    const slotIndex = Number(value)
    return Number.isInteger(slotIndex) && slotIndex >= 0 && slotIndex < 6 ? slotIndex : -1
  }
  return {
    role1SlotIndex: normalizeSlotIndex(source.role1SlotIndex),
    role2SlotIndex: normalizeSlotIndex(source.role2SlotIndex),
    role1PlayerId: source.role1PlayerId || '',
    role2PlayerId: source.role2PlayerId || '',
  }
}

export function cloneMatchEventQueueItem(item) {
  const source = item && typeof item === 'object' ? item : {}
  const seq = Number(source.seq || 0)
  const gameNo = Number(source.gameNo || 1)
  const leftScore = Number(source.leftScore || 0)
  const rightScore = Number(source.rightScore || 0)
  const payload = source.payload && typeof source.payload === 'object'
    ? JSON.parse(JSON.stringify(source.payload))
    : {}
  return {
    seq: Number.isInteger(seq) && seq > 0 ? seq : 0,
    type: source.type || '',
    gameNo: Number.isInteger(gameNo) && gameNo > 0 ? gameNo : 1,
    leftScore: Number.isInteger(leftScore) && leftScore >= 0 ? leftScore : 0,
    rightScore: Number.isInteger(rightScore) && rightScore >= 0 ? rightScore : 0,
    serveSide: source.serveSide === 'right' ? 'right' : 'left',
    payload,
    syncStatus: source.syncStatus === 'synced' ? 'synced' : 'pending',
  }
}

export function toggleSide(side) {
  return side === 'right' ? 'left' : 'right'
}

export function normalizeParticipantSide(side) {
  return side === 'right' ? 'right' : 'left'
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
    members: sortVolleyballMembers(
      Array.isArray(participant?.members)
        ? participant.members.map((member) => ({
            id: member.id,
            name: member.name,
            jerseyNumber: Number(member.jerseyNumber || 0),
            libero: !!member.libero,
            captain: !!member.captain,
          }))
        : []
    ),
  }
}

export function createEmptyMatchState() {
  return {
    displaySideSwapped: false,
    screenLeftParticipantSide: 'left',
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
    leftLiberoSetup: createEmptyLiberoSetup(),
    rightLiberoSetup: createEmptyLiberoSetup(),
    leftLiberoRuntime: createEmptyLiberoRuntime(),
    rightLiberoRuntime: createEmptyLiberoRuntime(),
    leftCaptainMemberId: '',
    rightCaptainMemberId: '',
    matchEvents: [],
    nextEventSeq: 1,
    lastSyncedEventSeq: 0,
    draftServeSide: 'left',
    reportMetaDraft: {
      matchTimeText: '',
      chiefRefereeName: '',
      assistantRefereeName: '',
    },
    lineupReady: false,
    finalGameSideSwitchPending: false,
    finalGameSideSwitchHandled: false,
    retiredSide: '',
    matchEnded: false,
    winnerName: '',
    historyStack: [],
  }
}

function swapWinnerSide(side) {
  if (side !== 'left' && side !== 'right') return side || ''
  return toggleSide(side)
}

function swapMatchStateSidesInternal(state) {
  const next = {
    ...state,
    displaySideSwapped: false,
    screenLeftParticipantSide: toggleSide(normalizeParticipantSide(state.screenLeftParticipantSide)),
    leftScore: Number(state.rightScore || 0),
    rightScore: Number(state.leftScore || 0),
    leftGameWins: Number(state.rightGameWins || 0),
    rightGameWins: Number(state.leftGameWins || 0),
    serveSide: toggleSide(state.serveSide === 'right' ? 'right' : 'left'),
    currentGameStartServeSide: toggleSide(state.currentGameStartServeSide === 'right' ? 'right' : 'left'),
    leftTimeouts: Number(state.rightTimeouts ?? 2),
    rightTimeouts: Number(state.leftTimeouts ?? 2),
    leftCourt: cloneCourt(state.rightCourt),
    rightCourt: cloneCourt(state.leftCourt),
    baseLeftCourt: cloneCourt(state.baseRightCourt),
    baseRightCourt: cloneCourt(state.baseLeftCourt),
    draftLeftCourt: cloneCourt(state.draftRightCourt),
    draftRightCourt: cloneCourt(state.draftLeftCourt),
    leftLiberoSetup: cloneLiberoSetup(state.rightLiberoSetup),
    rightLiberoSetup: cloneLiberoSetup(state.leftLiberoSetup),
    leftLiberoRuntime: cloneLiberoRuntime(state.rightLiberoRuntime),
    rightLiberoRuntime: cloneLiberoRuntime(state.leftLiberoRuntime),
    leftCaptainMemberId: state.rightCaptainMemberId || '',
    rightCaptainMemberId: state.leftCaptainMemberId || '',
    draftServeSide: toggleSide(state.draftServeSide === 'right' ? 'right' : 'left'),
    retiredSide: swapWinnerSide(state.retiredSide),
    gameScores: Array.isArray(state.gameScores)
      ? state.gameScores.map((item) => ({
          ...item,
          leftScore: Number(item?.rightScore || 0),
          rightScore: Number(item?.leftScore || 0),
          winnerSide: swapWinnerSide(item?.winnerSide),
        }))
      : [],
  }
  return next
}

export function swapMatchStateSides(state) {
  return normalizeMatchState(swapMatchStateSidesInternal(normalizeMatchState({
    ...state,
    displaySideSwapped: false,
  })))
}

function normalizeHistoryEntry(raw) {
  const normalized = normalizeMatchState({
    ...raw,
    historyStack: [],
  })
  normalized.historyStack = []
  return normalized
}

export function normalizeMatchState(raw) {
  const defaults = createEmptyMatchState()
  const state = raw && typeof raw === 'object' ? raw : {}
  const matchEvents = Array.isArray(state.matchEvents)
    ? state.matchEvents
        .map((item) => cloneMatchEventQueueItem(item))
        .filter((item) => item.seq > 0 && item.type)
        .sort((left, right) => left.seq - right.seq)
    : []
  const maxEventSeq = matchEvents.reduce((max, item) => Math.max(max, item.seq), 0)
  const nextEventSeq = Number(state.nextEventSeq || 0)
  const lastSyncedEventSeq = Number(state.lastSyncedEventSeq || 0)
  const normalized = {
    ...defaults,
    ...state,
    displaySideSwapped: !!state.displaySideSwapped,
    screenLeftParticipantSide: normalizeParticipantSide(state.screenLeftParticipantSide),
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
    leftLiberoSetup: cloneLiberoSetup(state.leftLiberoSetup),
    rightLiberoSetup: cloneLiberoSetup(state.rightLiberoSetup),
    leftLiberoRuntime: cloneLiberoRuntime(state.leftLiberoRuntime),
    rightLiberoRuntime: cloneLiberoRuntime(state.rightLiberoRuntime),
    leftCaptainMemberId: state.leftCaptainMemberId || '',
    rightCaptainMemberId: state.rightCaptainMemberId || '',
    matchEvents,
    nextEventSeq: Number.isInteger(nextEventSeq) && nextEventSeq > maxEventSeq ? nextEventSeq : maxEventSeq + 1,
    lastSyncedEventSeq: Number.isInteger(lastSyncedEventSeq) && lastSyncedEventSeq >= 0 ? lastSyncedEventSeq : 0,
    draftServeSide: state.draftServeSide === 'right' ? 'right' : 'left',
    reportMetaDraft: {
      matchTimeText: state.reportMetaDraft?.matchTimeText || '',
      chiefRefereeName: state.reportMetaDraft?.chiefRefereeName || '',
      assistantRefereeName: state.reportMetaDraft?.assistantRefereeName || '',
    },
    lineupReady: !!state.lineupReady,
    finalGameSideSwitchPending: !!state.finalGameSideSwitchPending,
    finalGameSideSwitchHandled: !!state.finalGameSideSwitchHandled,
    retiredSide: state.retiredSide || '',
    matchEnded: !!state.matchEnded,
    winnerName: state.winnerName || '',
    historyStack: Array.isArray(state.historyStack)
      ? state.historyStack.slice(-MAX_HISTORY_ENTRIES).map((item) => normalizeHistoryEntry(item))
      : [],
  }
  const hasExplicitScreenSide = Object.prototype.hasOwnProperty.call(state, 'screenLeftParticipantSide')
  if (!hasExplicitScreenSide && normalized.displaySideSwapped) {
    return {
      ...swapMatchStateSidesInternal({
        ...normalized,
        displaySideSwapped: false,
        screenLeftParticipantSide: 'left',
      }),
      displaySideSwapped: false,
    }
  }
  return normalized
}

export function buildHistoryEntry(state) {
  return normalizeHistoryEntry(state)
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
  const key = buildMatchStorageKey(matchId)
  const normalized = normalizeMatchState(state)
  try {
    uni.setStorageSync(key, normalized)
  } catch (_) {
    uni.setStorageSync(key, {
      ...normalized,
      historyStack: [],
    })
  }
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
