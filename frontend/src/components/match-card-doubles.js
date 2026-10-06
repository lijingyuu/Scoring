/**
 * 拆分双打选手名字：
 * 如果识别到 '/'，吞掉斜杠，将左右两个名字分别取出并 trim 过滤。
 * 否则返回包含原名字的单元素数组。
 */
export function splitDoublesName(rawName) {
  if (!rawName || typeof rawName !== 'string') return []
  if (rawName.includes('/')) {
    const parts = rawName.split('/').map((s) => s.trim()).filter(Boolean)
    return parts.length ? parts : [rawName.trim()]
  }
  return [rawName.trim()]
}

/**
 * 判断名字串是否为双打形式（包含有效斜杠且拆分为至少两人）
 */
export function isDoublesName(rawName) {
  return splitDoublesName(rawName).length > 1
}

/**
 * 判断单场比赛卡片是否启用羽毛球双打布局：
 * 1. 排除团体赛（isTeamMatch !== true）
 * 2. 排除排球比赛（isVolleyball !== true）
 * 3. 左侧或右侧名字串中识别到 '/'
 */
export function isDoublesMatch({ leftName = '', rightName = '', isTeamMatch = false, isVolleyball = false } = {}) {
  if (isTeamMatch || isVolleyball) return false
  return isDoublesName(leftName) || isDoublesName(rightName)
}
