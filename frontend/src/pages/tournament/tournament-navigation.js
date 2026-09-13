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

 /**
  * 组别一致性判定：任一侧未知（空）即视为一致并直接回退——
  * 栈内赛程页 URL 不带 divisionId 时（从详情页进入、或用户用组别切换条切过组别），
  * 该页面实例自身持有正确的组别状态，回退即可；只有双方都明确且不同才需要 redirect 换组别。
  * 这样单组别赛事与改动前的 back 行为完全等价。
  */
 function sameDivisionOption(actual, expected) {
   const stackDivision = String(actual ?? '').trim()
   const targetDivision = String(expected ?? '').trim()
   if (!stackDivision || !targetDivision) return true
   return stackDivision === targetDivision
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
