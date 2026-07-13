export function normalizeRoute(route) {
  return String(route || '').replace(/^\/+/, '')
}

export function tournamentScheduleRoute(tournamentType) {
  return Number(tournamentType || 0) === 0
    ? 'pages/tournament/bracket'
    : 'pages/tournament/groups'
}

export function buildTournamentScheduleUrl(tournamentType, tournamentId) {
  return '/' + tournamentScheduleRoute(tournamentType) + '?id=' + encodeURIComponent(tournamentId)
}

function sameOptionValue(actual, expected) {
  return !actual || String(actual) === String(expected)
}

export function resolveTournamentScheduleNavigation({ pages = [], tournamentId = '', tournamentType = 0 } = {}) {
  if (!tournamentId) {
    return { type: 'back', delta: 1 }
  }

  const targetRoute = tournamentScheduleRoute(tournamentType)
  for (let index = pages.length - 2; index >= 0; index--) {
    const page = pages[index]
    if (
      normalizeRoute(page?.route) === targetRoute
      && sameOptionValue(page?.options?.id, tournamentId)
    ) {
      return { type: 'back', delta: pages.length - 1 - index }
    }
  }

  return { type: 'redirect', url: buildTournamentScheduleUrl(tournamentType, tournamentId) }
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

export function navigateToTournamentSchedule({ pages = [], tournamentId = '', tournamentType = 0, uniApi } = {}) {
  applyNavigation(resolveTournamentScheduleNavigation({ pages, tournamentId, tournamentType }), uniApi)
}

export function teamMatchRoute(isRelayTemplate) {
  return isRelayTemplate
    ? 'pages/tournament/team-relay'
    : 'pages/tournament/team-match'
}

export function buildTeamMatchUrl({ tournamentId = '', matchId = '', isRelayTemplate = false } = {}) {
  return '/' + teamMatchRoute(isRelayTemplate)
    + '?tournamentId=' + encodeURIComponent(tournamentId)
    + '&matchId=' + encodeURIComponent(matchId)
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
