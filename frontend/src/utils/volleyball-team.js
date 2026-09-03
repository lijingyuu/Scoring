// Sorts team members for volleyball teams and badminton team tournaments.
export function sortVolleyballMembers(members = []) {
  return (Array.isArray(members) ? members : [])
    .map((member, index) => {
      const rawNumber = member?.jerseyNumber
      const jerseyNumber = rawNumber === null || rawNumber === undefined || rawNumber === ''
        ? null
        : Number(rawNumber)
      return {
        ...member,
        jerseyNumber: Number.isFinite(jerseyNumber) ? jerseyNumber : null,
        captain: !!member?.captain,
        libero: !!member?.libero,
        displayOrder: member?.displayOrder ?? member?.display_order ?? index,
      }
    })
    .sort((left, right) => {
      if (left.captain !== right.captain) {
        return left.captain ? -1 : 1
      }
      if (left.jerseyNumber != null && right.jerseyNumber != null && left.jerseyNumber !== right.jerseyNumber) {
        return left.jerseyNumber - right.jerseyNumber
      }
      if (left.jerseyNumber != null && right.jerseyNumber == null) {
        return -1
      }
      if (left.jerseyNumber == null && right.jerseyNumber != null) {
        return 1
      }
      const nameCompare = String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
      if (nameCompare !== 0) {
        return nameCompare
      }
      return Number(left.displayOrder || 0) - Number(right.displayOrder || 0)
    })
}

export function captainNameOfTeam(team) {
  return sortVolleyballMembers(team?.members || []).find((member) => member.captain)?.name || '-'
}
