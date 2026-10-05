// 多组别（divisions）草稿的共享逻辑：名单解析、手写签表/手写分组的容量与校验。
// 创建页（CreateTournamentView）与组别表单组件（DivisionFormPanel）共用，保证口径一致。
import { DRAW_SLOT_EMPTY, MAX_DRAW_SLOTS } from './drawSlots'

/** 解析名单文本：每行一名选手，行首数字为种子序号（与创建 payload 提交口径一致） */
export function parseDivisionPlayers(text) {
  return String(text || '')
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter(Boolean)
    .map((line) => {
      const match = line.match(/^(\d+)[.\s、-]*(.+)$/)
      return match ? { seed: Number(match[1]), name: match[2].trim() } : { seed: null, name: line }
    })
}

/** 名单项 key 列表（= 名单提交顺序的 0-based 下标字符串） */
export function divisionRosterKeys(text) {
  return parseDivisionPlayers(text).map((_, index) => String(index))
}

/** type0 组别手写签表容量 = 1 << 淘汰轮数（显式轮数制；单组别页是“人数向上取幂”，口径不同） */
export function divisionDrawCapacity(d) {
  const rounds = Number(d.knockoutRounds)
  if (!Number.isInteger(rounds) || rounds < 1 || rounds > 10) return 0
  return 2 ** rounds
}

export function divisionDrawUnavailableReason(d, rosterCount) {
  const capacity = divisionDrawCapacity(d)
  if (capacity > MAX_DRAW_SLOTS) return `手写签表最多支持 ${MAX_DRAW_SLOTS} 个签位，当前轮数需要 ${capacity} 个`
  if (rosterCount < 2) return '至少需要 2 名选手才能手写签表'
  return ''
}

/** 契约：组数 = 淘汰名额 ÷ 每组出线（需整除）；不可整除返回 0 */
export function divisionGroupCount(d) {
  const slots = Number(d.knockoutSlots)
  const perGroup = Number(d.qualifiersPerGroup)
  if (!Number.isInteger(slots) || !Number.isInteger(perGroup) || perGroup < 1 || slots < 2) return 0
  if (slots % perGroup !== 0) return 0
  return slots / perGroup
}

/** 契约：每组人数下限 = max(2, 每组出线) */
export function divisionMinPerGroup(d) {
  return Math.max(2, Number(d.qualifiersPerGroup) || 0)
}

export function divisionGroupsUnavailableReason(d, rosterCount) {
  const count = divisionGroupCount(d)
  if (!count) return '淘汰名额需能被每组出线整除才能手写分组'
  const minPerGroup = divisionMinPerGroup(d)
  const required = count * minPerGroup
  if (rosterCount < required) {
    return `手写分组至少需要 ${required} 名选手（${count} 组 × 每组 ${minPerGroup} 人）`
  }
  return ''
}

/**
 * 手写分组客户端校验：组数、每组下限、全覆盖无重复无越界。
 * 返回 { ok, message, problems }，口径与单组别页 manualGroupsValidation 一致。
 */
export function validateDivisionGroups(groups, rosterKeys, groupCount, minPerGroup) {
  const problems = []
  if (!groupCount) {
    problems.push('淘汰名额需能被每组出线整除（至少 2 个淘汰名额）')
    return { ok: false, message: problems.join('；'), problems }
  }
  const list = (Array.isArray(groups) ? groups : [])
    .map((group) => (Array.isArray(group) ? group.map((key) => String(key)) : []))
  const keys = (rosterKeys || []).map((key) => String(key))
  const known = new Set(keys)
  const seen = new Set()
  const duplicated = new Set()
  for (const group of list) {
    for (const key of group) {
      if (seen.has(key)) duplicated.add(key)
      seen.add(key)
    }
  }
  const unknownCount = [...seen].filter((key) => !known.has(key)).length
  const unplacedCount = keys.filter((key) => !seen.has(key)).length
  const shortGroups = list
    .map((group, index) => ({ index, size: group.length }))
    .filter((item) => item.size < minPerGroup)
  if (list.length !== groupCount) problems.push(`小组数量应为 ${groupCount} 个`)
  if (unknownCount) problems.push(`有 ${unknownCount} 个名单项已不在名单中，请先移出`)
  if (duplicated.size) problems.push(`有 ${duplicated.size} 个名单项出现在多个小组`)
  if (unplacedCount) problems.push(`还有 ${unplacedCount} 个名单项没有分组`)
  for (const item of shortGroups) problems.push(`第 ${item.index + 1} 组不足 ${minPerGroup} 人`)
  return { ok: !problems.length, message: problems.join('；'), problems }
}

/**
 * 名单/轮数变化后把手写签位数组对齐到框架容量：
 * 保留轮空位与仍存在的名单项 key，失效项与新增槽位记为「未选择」。
 */
export function syncDivisionDrawSlots(draft, capacity) {
  const keys = new Set(divisionRosterKeys(draft.playersText))
  const previous = Array.isArray(draft.manualSlots) ? draft.manualSlots : []
  draft.manualSlots = Array.from({ length: capacity }, (_, index) => {
    const current = previous[index]
    if (current === null) return null
    return keys.has(current) ? current : DRAW_SLOT_EMPTY
  })
}

/**
 * 名单/组数变化后对齐分组数组：组数不足补空组，已不存在的名单项与重复项清理掉；
 * 组数减少时被裁掉的小组成员直接丢弃（回到未分组，由用户重新安排），避免座次被静默迁移。
 */
export function syncDivisionGroups(draft, count) {
  if (!count) {
    draft.manualGroups = []
    return
  }
  const keys = new Set(divisionRosterKeys(draft.playersText))
  const previous = Array.isArray(draft.manualGroups) ? draft.manualGroups : []
  const seen = new Set()
  draft.manualGroups = Array.from({ length: count }, (_, index) => {
    const group = Array.isArray(previous[index]) ? previous[index] : []
    const cleaned = []
    for (const raw of group) {
      const key = String(raw)
      if (!keys.has(key) || seen.has(key)) continue
      seen.add(key)
      cleaned.push(key)
    }
    return cleaned
  })
}
