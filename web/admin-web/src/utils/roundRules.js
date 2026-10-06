// 分段规则（分轮规则）共享逻辑：作用域推导、覆盖校验、段 → roundRules 扁平化。
// 单组别创建页（CreateTournamentView）与多组别表单组件（DivisionFormPanel）共用，保证口径一致。

// 赛段 id 单一发放源：抽屉与两个使用方（单组别/组别面板）共用，保证全局单调不重号（复审 P1）
let segmentIdSeq = 0
export function nextRoundRuleSegmentId() {
  segmentIdSeq += 1
  return segmentIdSeq
}

/** 作用域 key：stageType-roundNum（0-0=小组赛，1-N=淘汰赛第 N 轮） */
export function roundRuleScopeKey(stageType, roundNum) {
  return `${stageType}-${roundNum}`
}

/**
 * 推导某赛制的全部规则作用域。
 * type1（小组+淘汰）：小组赛(0,0) + 淘汰赛 1..log2(capacity)；
 * type0（纯淘汰）：淘汰赛 1..log2(capacity)；
 * type2（循环赛）不支持分段规则，调用方应先拦截，这里 capacity<2 时返回空。
 * @param {number} tournamentType 0/1/2
 * @param {number} knockoutCapacity 淘汰赛框架容量（type1=knockoutSlots，type0=1<<knockoutRounds）
 */
export function buildRoundRuleScopes(tournamentType, knockoutCapacity) {
  const scopes = []
  const capacity = Number(knockoutCapacity)
  if (Number(tournamentType) === 1) {
    scopes.push({ stageType: 0, roundNum: 0, key: roundRuleScopeKey(0, 0), label: '小组赛' })
  }
  if (!Number.isFinite(capacity) || capacity < 2) return scopes
  const roundCount = Math.log2(capacity)
  for (let round = 1; round <= roundCount; round += 1) {
    const from = capacity / (2 ** (round - 1))
    const to = from / 2
    scopes.push({
      stageType: 1,
      roundNum: round,
      key: roundRuleScopeKey(1, round),
      label: to === 1 ? '决赛' : `${from}进${to}`,
    })
  }
  return scopes
}

/**
 * 覆盖校验：每个作用域恰好被一个赛段分配（后端创建期为"全有或全无"，缺/多都拒绝）。
 * @returns {string} '' 表示通过，否则为错误文案
 */
export function validateSegmentCoverage(segments, scopes) {
  if (!scopes.length) return '请先设置有效的淘汰轮数'
  const assigned = new Set()
  for (const segment of segments) {
    if (!segment.scopeKeys.length) return `请为「${segment.name || '未命名赛段'}」选择比赛阶段，或删除该赛段`
    for (const key of segment.scopeKeys) {
      if (assigned.has(key)) return '同一个比赛阶段不能重复分配'
      assigned.add(key)
    }
  }
  const missing = scopes.filter((scope) => !assigned.has(scope.key))
  if (missing.length) return `还有比赛阶段未分配：${missing.map((scope) => scope.label).join('、')}`
  return ''
}

/** 赛段 → roundRules 扁平化（按作用域声明顺序展开；未分配的 key 忽略，由覆盖校验兜底） */
export function flattenSegments(segments, scopes) {
  const scopeMap = new Map(scopes.map((scope) => [scope.key, scope]))
  return (segments || []).flatMap((segment) => segment.scopeKeys
    .filter((key) => scopeMap.has(key))
    .map((key) => {
      const scope = scopeMap.get(key)
      return {
        stageType: scope.stageType,
        roundNum: scope.roundNum,
        label: scope.label,
        rule: segment.rule,
      }
    }))
}

/** 找到某作用域所在赛段的规则（如"季军赛沿用决赛段规则"）；未分配返回 null */
export function findSegmentRuleForScope(segments, scopes, scopeKey) {
  const segment = (segments || []).find((item) => item.scopeKeys.includes(scopeKey))
  if (!segment) return null
  const scope = scopes.find((item) => item.key === scopeKey)
  return scope ? segment.rule : null
}

/** 赛段摘要：作用域 label 列表（用于面板外部的分段概览） */
export function formatSegmentScopeList(segment, scopes) {
  const scopeMap = new Map(scopes.map((scope) => [scope.key, scope.label]))
  return segment.scopeKeys.map((key) => scopeMap.get(key)).filter(Boolean).join('、') || '未选择'
}
