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
  if (isRoundRobin) {
    return standing?.displayRankText || "-"
  }
  return String(standing?.rank ?? "-")
}
