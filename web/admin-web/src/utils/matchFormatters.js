/**
 * 拆分双打选手名字：
 * 如果识别到 '/'，分割并去除两端空格。
 */
export function splitDoublesName(rawName) {
  if (!rawName || typeof rawName !== 'string') return []
  const normalized = rawName.replace(/／/g, '/')
  if (normalized.includes('/')) {
    const parts = normalized.split('/').map((s) => s.trim()).filter(Boolean)
    return parts.length ? parts : [normalized.trim()]
  }
  return [normalized.trim()]
}

export function isDoublesName(rawName) {
  return splitDoublesName(rawName).length > 1
}

export function isDoublesMatch({ leftName = '', rightName = '', isTeamMatch = false, isVolleyball = false } = {}) {
  if (isTeamMatch || isVolleyball) return false
  return isDoublesName(leftName) || isDoublesName(rightName)
}

export function sportText(tournament) {
  if (!tournament) return '比赛'
  if (tournament.sportType === 1) return '排球'
  if (tournament.participantType === 1 && tournament.teamMatchTemplate === 2) return '羽毛球接力'
  if (tournament.participantType === 1 && tournament.teamMatchTemplate === 3) return '羽毛球自定义多项'
  if (tournament.participantType === 1) return '羽毛球团体'
  return '羽毛球个人'
}

export function tournamentTypeText(type) {
  const val = Number(type ?? 0)
  if (val === 1) return '小组+淘汰'
  if (val === 2) return '循环赛'
  return '淘汰赛'
}

export function matchStatusText(status) {
  const val = Number(status ?? 0)
  if (val === 2) return '已结束'
  if (val === 1) return '进行中'
  return '未开赛'
}

export function formatScoreDisplay(scoreDisplay) {
  if (!scoreDisplay) return ''
  return String(scoreDisplay).replace(/,/g, '  ')
}
