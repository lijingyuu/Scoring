export function sortVolleyballMembers(members = []) {
  return (Array.isArray(members) ? members : [])
    .map((member) => ({
      ...member,
      jerseyNumber: Number(member?.jerseyNumber || 0),
      captain: !!member?.captain,
      libero: !!member?.libero,
    }))
    .sort((left, right) => {
      if (left.captain !== right.captain) {
        return left.captain ? -1 : 1
      }
      if (left.jerseyNumber !== right.jerseyNumber) {
        return left.jerseyNumber - right.jerseyNumber
      }
      return String(left.name || '').localeCompare(String(right.name || ''), 'zh-CN')
    })
}

export function captainNameOfTeam(team) {
  return sortVolleyballMembers(team?.members || []).find((member) => member.captain)?.name || '-'
}
