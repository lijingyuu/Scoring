// 手写签表（manual draw）通用逻辑：容量计算、签位校验、随机填充。
// 签位模型：数组，元素含义 —— '' = 未选择（UI 占位）、null = 轮空（空签位）、其它 = 名单项 key。

/** 未选择占位（提交前必须显式选择名单项或轮空） */
export const DRAW_SLOT_EMPTY = ''
/** 轮空（空签位），提交 payload 时为 null */
export const DRAW_SLOT_BYE = null
/** 契约上限：手写签表最多 64 个签位 */
export const MAX_DRAW_SLOTS = 64

export function isPowerOfTwo(value) {
  const size = Number(value)
  return Number.isInteger(size) && size > 0 && (size & (size - 1)) === 0
}

/** 大于等于 count 的最小 2 的幂（下限 2），即淘汰赛框架容量 */
export function drawCapacityFor(count) {
  const target = Math.max(2, Math.trunc(Number(count)) || 0)
  let capacity = 2
  while (capacity < target) capacity *= 2
  return capacity
}

function isPlaced(slot) {
  return slot !== null && slot !== undefined && slot !== DRAW_SLOT_EMPTY
}

/**
 * 校验签位数组。
 * @returns {{ ok: boolean, message: string, bothByeMatches: number[], hasEmpty: boolean, unplacedKeys: string[], duplicatedKeys: string[] }}
 * bothByeMatches 为“两个签位都是轮空”的场次下标（0-based，第 index+1 场）。
 */
export function validateDrawSlots(slots, rosterKeys = []) {
  const list = Array.isArray(slots) ? slots : []
  const keys = (rosterKeys || []).map((key) => String(key))
  const hasEmpty = list.some((slot) => slot === DRAW_SLOT_EMPTY)
  const placedKeys = list.filter(isPlaced).map((slot) => String(slot))
  const placedCount = new Map()
  for (const key of placedKeys) placedCount.set(key, (placedCount.get(key) || 0) + 1)
  const duplicatedKeys = [...placedCount.entries()].filter(([, count]) => count > 1).map(([key]) => key)
  const usedKeys = new Set(placedKeys)
  const unknownKeys = [...usedKeys].filter((key) => !keys.includes(key))
  const unplacedKeys = keys.filter((key) => !usedKeys.has(key))
  const bothByeMatches = []
  let message = ''

  if (!isPowerOfTwo(list.length) || list.length < 2) {
    message = '签位数量必须是 2 的幂且不少于 2'
  } else if (list.length > MAX_DRAW_SLOTS) {
    message = `手写签表最多支持 ${MAX_DRAW_SLOTS} 个签位`
  } else {
    for (let index = 0; index < list.length; index += 2) {
      if (list[index] === DRAW_SLOT_BYE && list[index + 1] === DRAW_SLOT_BYE) {
        bothByeMatches.push(index / 2)
      }
    }
    if (duplicatedKeys.length) message = '同一个名单项不能出现在多个签位上'
    else if (unknownKeys.length) message = '签位包含名单外的选项，请重新安排签位'
    else if (unplacedKeys.length) message = `还有 ${unplacedKeys.length} 个名单项没有安排签位`
    else if (hasEmpty) message = '还有签位未选择，请点击签位后选择名单项或「轮空位」'
    else if (bothByeMatches.length) {
      message = `第 ${bothByeMatches.map((index) => index + 1).join('、')} 场的两个签位都是轮空，请调整`
    }
  }

  return { ok: !message, message, bothByeMatches, hasEmpty, unplacedKeys, duplicatedKeys, unknownKeys }
}

function shuffle(items) {
  const list = items.slice()
  for (let index = list.length - 1; index > 0; index -= 1) {
    const swap = Math.floor(Math.random() * (index + 1))
    ;[list[index], list[swap]] = [list[swap], list[index]]
  }
  return list
}

/**
 * 把尚未放置的名单项随机填入空签位；已手动放置的签位与显式轮空位优先保持不变。
 * 剩余的未选择签位转轮空时按场避让（见 settleEmptySlots）：无法满足时保留「未选择」，
 * 由校验拦下提交并提示用户，而不是随机产出双轮空（审查 P2-1）。
 */
export function fillUnplacedSlotsRandomly(slots, rosterKeys = []) {
  const list = Array.isArray(slots) ? slots.slice() : []
  const keys = (rosterKeys || []).map((key) => String(key))
  const usedKeys = new Set(list.filter(isPlaced).map((slot) => String(slot)))
  const unplaced = shuffle(keys.filter((key) => !usedKeys.has(key)))
  const emptyIndices = shuffle(list.map((slot, index) => (slot === DRAW_SLOT_EMPTY ? index : -1)).filter((index) => index >= 0))
  const byeIndices = shuffle(list.map((slot, index) => (slot === DRAW_SLOT_BYE ? index : -1)).filter((index) => index >= 0))
  const targets = [...emptyIndices, ...byeIndices]
  unplaced.forEach((key, order) => {
    const target = targets[order]
    if (target !== undefined) list[target] = key
  })
  return settleEmptySlots(list)
}

/** 随机填入后仍有未选择的签位时转轮空；同一场已有轮空的场保留「未选择」交给用户处理 */
function settleEmptySlots(list) {
  const next = list.slice()
  for (let index = 0; index < next.length; index += 1) {
    if (next[index] !== DRAW_SLOT_EMPTY) continue
    const otherIndex = index % 2 === 0 ? index + 1 : index - 1
    if (next[otherIndex] === DRAW_SLOT_BYE) continue
    next[index] = DRAW_SLOT_BYE
  }
  return next
}
