export function normalizeRoute(route) {
  return String(route || '').replace(/^\/+/, '')
}

export function tournamentScheduleRoute(tournamentType) {
  return Number(tournamentType || 0) === 0
    ? 'pages/tournament/bracket'
    : 'pages/tournament/groups'
}

export function buildTournamentScheduleUrl(tournamentType, tournamentId, divisionId) {
  let url = '/' + tournamentScheduleRoute(tournamentType) + '?id=' + encodeURIComponent(tournamentId)
  if (divisionId) {
    url += '&divisionId=' + encodeURIComponent(divisionId)
  }
  return url
}

function sameOptionValue(actual, expected) {
  return !actual || String(actual) === String(expected)
}

/** 组别要求严格一致：都为空视为一致，一方有值另一方为空视为不一致 */
function sameDivisionOption(actual, expected) {
  return String(actual ?? '').trim() === String(expected ?? '').trim()
}

/** divisionId 有值时返回 '&divisionId=xxx'，无值时返回空串（旧调用输出零变化） */
function buildDivisionIdQuery(divisionId) {
  return divisionId ? '&divisionId=' + encodeURIComponent(divisionId) : ''
}

export function resolveTournamentScheduleNavigation({ pages = [], tournamentId = '', tournamentType = 0, divisionId = '' } = {}) {
  if (!tournamentId) {
    return { type: 'back', delta: 1 }
  }

  const targetRoute = tournamentScheduleRoute(tournamentType)
  for (let index = pages.length - 2; index >= 0; index--) {
    const page = pages[index]
    if (
      normalizeRoute(page?.route) === targetRoute
      && sameOptionValue(page?.options?.id, tournamentId)
      && sameDivisionOption(page?.options?.divisionId, divisionId)
    ) {
      return { type: 'back', delta: pages.length - 1 - index }
    }
  }

  return { type: 'redirect', url: buildTournamentScheduleUrl(tournamentType, tournamentId, divisionId) }
}

export function applyNavigation(navigation, uniApi) {
  if (!navigation || !uniApi) return
  if (navigation.type === 'back') {
    uniApi.navigateBack({ delta: navigation.delta || 1 })
    return
  }
  if (navigation.type === 'redirect') {
    uniApi.redirectTo({ url: navigation.url })
  }
}

export function navigateToTournamentSchedule({ pages = [], tournamentId = '', tournamentType = 0, divisionId = '', uniApi } = {}) {
  applyNavigation(resolveTournamentScheduleNavigation({ pages, tournamentId, tournamentType, divisionId }), uniApi)
}

export function teamMatchRoute(isRelayTemplate) {
  return isRelayTemplate
    ? 'pages/tournament/team-relay'
    : 'pages/tournament/team-match'
}

export function buildTeamMatchUrl({ tournamentId = '', matchId = '', isRelayTemplate = false, divisionId = '' } = {}) {
  return '/' + teamMatchRoute(isRelayTemplate)
    + '?tournamentId=' + encodeURIComponent(tournamentId)
    + '&matchId=' + encodeURIComponent(matchId)
    + buildDivisionIdQuery(divisionId)
}

export function teamRecordRoute(isRelayTemplate) {
  return isRelayTemplate
    ? 'pages/tournament/relay-record'
    : 'pages/tournament/team-record'
}

export function buildTeamRecordUrl({ tournamentId = '', matchId = '', isRelayTemplate = false, divisionId = '' } = {}) {
  return '/' + teamRecordRoute(isRelayTemplate)
    + '?tournamentId=' + encodeURIComponent(tournamentId)
    + '&matchId=' + encodeURIComponent(matchId)
    + buildDivisionIdQuery(divisionId)
}

export function individualRecordRoute() {
  return 'pages/tournament/individual-record'
}

export function buildIndividualRecordUrl({ tournamentId = '', matchId = '', divisionId = '' } = {}) {
  return '/' + individualRecordRoute()
    + '?tournamentId=' + encodeURIComponent(tournamentId)
    + '&matchId=' + encodeURIComponent(matchId)
    + buildDivisionIdQuery(divisionId)
}

export function resolveExistingMatchPageNavigation({
  pages = [],
  tournamentId = '',
  matchId = '',
  isRelayTemplate = false,
} = {}) {
  if (!tournamentId || !matchId) {
    return { type: 'redirect', url: buildTeamMatchUrl({ tournamentId, matchId, isRelayTemplate }) }
  }

  const targetRoute = teamMatchRoute(isRelayTemplate)
  for (let index = pages.length - 2; index >= 0; index--) {
    const page = pages[index]
    if (
      normalizeRoute(page?.route) === targetRoute
      && sameOptionValue(page?.options?.tournamentId, tournamentId)
      && sameOptionValue(page?.options?.matchId, matchId)
    ) {
      return { type: 'back', delta: pages.length - 1 - index }
    }
  }

  return { type: 'redirect', url: buildTeamMatchUrl({ tournamentId, matchId, isRelayTemplate }) }
}

export function navigateToExistingMatchPage({ pages = [], tournamentId = '', matchId = '', isRelayTemplate = false, uniApi } = {}) {
  applyNavigation(resolveExistingMatchPageNavigation({ pages, tournamentId, matchId, isRelayTemplate }), uniApi)
}
