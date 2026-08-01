import { describe, expect, it, vi } from 'vitest'
import {
  applyNavigation,
  buildIndividualRecordUrl,
  buildTeamMatchUrl,
  buildTeamRecordUrl,
  buildTournamentScheduleUrl,
  individualRecordRoute,
  normalizeRoute,
  resolveExistingMatchPageNavigation,
  resolveTournamentScheduleNavigation,
  teamMatchRoute,
  teamRecordRoute,
  tournamentScheduleRoute,
} from './tournament-navigation'

function page(route, options = {}) {
  return { route, options }
}

describe('tournament schedule navigation', () => {
  it('uses bracket for pure knockout tournaments', () => {
    expect(tournamentScheduleRoute(0)).toBe('pages/tournament/bracket')
    expect(buildTournamentScheduleUrl(0, 't-1')).toBe('/pages/tournament/bracket?id=t-1')
  })

  it('uses groups for group and round-robin tournaments', () => {
    expect(tournamentScheduleRoute(1)).toBe('pages/tournament/groups')
    expect(tournamentScheduleRoute(2)).toBe('pages/tournament/groups')
    expect(buildTournamentScheduleUrl(2, 't-1')).toBe('/pages/tournament/groups?id=t-1')
  })

  it('navigates back to the nearest round-robin groups page instead of jumping to tournament detail', () => {
    const pages = [
      page('pages/tournament/detail', { id: 't-1' }),
      page('pages/tournament/groups', { id: 't-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveTournamentScheduleNavigation({ pages, tournamentId: 't-1', tournamentType: 2 }))
      .toEqual({ type: 'back', delta: 1 })
  })

  it('navigates back across an intermediate page to the nearest matching schedule page', () => {
    const pages = [
      page('pages/tournament/detail', { id: 't-1' }),
      page('pages/tournament/groups', { id: 't-1' }),
      page('pages/tournament/team-lineup', { tournamentId: 't-1', matchId: 'm-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveTournamentScheduleNavigation({ pages, tournamentId: 't-1', tournamentType: 1 }))
      .toEqual({ type: 'back', delta: 2 })
  })

  it('navigates back to bracket for knockout team matches', () => {
    const pages = [
      page('pages/tournament/detail', { id: 't-1' }),
      page('/pages/tournament/bracket', { id: 't-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveTournamentScheduleNavigation({ pages, tournamentId: 't-1', tournamentType: 0 }))
      .toEqual({ type: 'back', delta: 1 })
  })

  it('skips a schedule page for another tournament and redirects to the correct one', () => {
    const pages = [
      page('pages/tournament/detail', { id: 't-1' }),
      page('pages/tournament/groups', { id: 'other' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveTournamentScheduleNavigation({ pages, tournamentId: 't-1', tournamentType: 2 }))
      .toEqual({ type: 'redirect', url: '/pages/tournament/groups?id=t-1' })
  })

  it('redirects to schedule when no schedule page exists in the stack', () => {
    const pages = [
      page('pages/tournament/detail', { id: 't-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveTournamentScheduleNavigation({ pages, tournamentId: 't-1', tournamentType: 2 }))
      .toEqual({ type: 'redirect', url: '/pages/tournament/groups?id=t-1' })
  })

  it('falls back to a simple navigateBack when tournamentId is missing', () => {
    expect(resolveTournamentScheduleNavigation({ pages: [], tournamentId: '', tournamentType: 2 }))
      .toEqual({ type: 'back', delta: 1 })
  })
})

describe('team match page navigation after lineup save', () => {
  it('returns to the existing five-item team match page instead of stacking a duplicate', () => {
    const pages = [
      page('pages/tournament/groups', { id: 't-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'm-1' }),
      page('pages/tournament/team-lineup', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveExistingMatchPageNavigation({
      pages,
      tournamentId: 't-1',
      matchId: 'm-1',
      isRelayTemplate: false,
    })).toEqual({ type: 'back', delta: 1 })
  })

  it('returns to the existing relay team match page after relay lineup save', () => {
    const pages = [
      page('pages/tournament/groups', { id: 't-1' }),
      page('pages/tournament/team-relay', { tournamentId: 't-1', matchId: 'm-1' }),
      page('pages/tournament/team-lineup', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveExistingMatchPageNavigation({
      pages,
      tournamentId: 't-1',
      matchId: 'm-1',
      isRelayTemplate: true,
    })).toEqual({ type: 'back', delta: 1 })
  })

  it('redirects to the target match page when the previous page is not the same match', () => {
    const pages = [
      page('pages/tournament/groups', { id: 't-1' }),
      page('pages/tournament/team-match', { tournamentId: 't-1', matchId: 'other' }),
      page('pages/tournament/team-lineup', { tournamentId: 't-1', matchId: 'm-1' }),
    ]

    expect(resolveExistingMatchPageNavigation({
      pages,
      tournamentId: 't-1',
      matchId: 'm-1',
      isRelayTemplate: false,
    })).toEqual({ type: 'redirect', url: '/pages/tournament/team-match?tournamentId=t-1&matchId=m-1' })
  })
})

describe('navigation helpers', () => {
  it('normalizes routes with or without a leading slash', () => {
    expect(normalizeRoute('/pages/tournament/groups')).toBe('pages/tournament/groups')
    expect(normalizeRoute('pages/tournament/groups')).toBe('pages/tournament/groups')
  })

  it('builds regular and relay team match URLs', () => {
    expect(teamMatchRoute(false)).toBe('pages/tournament/team-match')
    expect(teamMatchRoute(true)).toBe('pages/tournament/team-relay')
    expect(buildTeamMatchUrl({ tournamentId: 't 1', matchId: 'm/1', isRelayTemplate: true }))
      .toBe('/pages/tournament/team-relay?tournamentId=t%201&matchId=m%2F1')
  })

  it('builds regular and relay team record URLs for settled team matches', () => {
    expect(teamRecordRoute(false)).toBe('pages/tournament/team-record')
    expect(teamRecordRoute(true)).toBe('pages/tournament/relay-record')
    expect(buildTeamRecordUrl({ tournamentId: 't 1', matchId: 'm/1', isRelayTemplate: true }))
      .toBe('/pages/tournament/relay-record?tournamentId=t%201&matchId=m%2F1')
  })

  it('builds individual record URLs for settled individual matches', () => {
    expect(individualRecordRoute()).toBe('pages/tournament/individual-record')
    expect(buildIndividualRecordUrl({ tournamentId: 't 1', matchId: 'm/1' }))
      .toBe('/pages/tournament/individual-record?tournamentId=t%201&matchId=m%2F1')
  })

  it('applies back navigation through uni.navigateBack', () => {
    const uniApi = { navigateBack: vi.fn(), redirectTo: vi.fn() }

    applyNavigation({ type: 'back', delta: 2 }, uniApi)

    expect(uniApi.navigateBack).toHaveBeenCalledWith({ delta: 2 })
    expect(uniApi.redirectTo).not.toHaveBeenCalled()
  })

  it('applies redirect navigation through uni.redirectTo', () => {
    const uniApi = { navigateBack: vi.fn(), redirectTo: vi.fn() }

    applyNavigation({ type: 'redirect', url: '/pages/tournament/groups?id=t-1' }, uniApi)

    expect(uniApi.redirectTo).toHaveBeenCalledWith({ url: '/pages/tournament/groups?id=t-1' })
    expect(uniApi.navigateBack).not.toHaveBeenCalled()
  })
})
