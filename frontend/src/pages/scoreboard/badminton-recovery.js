/**
 * 羽毛球记分恢复（Recovery）纯函数集合
 *
 * 与排球 pages/volleyball/score-recovery.js 同构（不改动排球代码），差异点：
 * 羽毛球服务端比分（scoreDisplay/gameScores）只在 finish 时落库，局中恢复完全依赖
 * 事件重放；本模块把「最新事件的 runtime 快照」作为当前状态权威来源，并用
 * prevRuntime 重建撤销栈，使换设备接管后撤销功能仍可用。
 *
 * record 形如 GET /api/v1/matches/{id}/record 的返回：
 *   { status, scoreDisplay, gameScores, leftGameWins, rightGameWins, retiredSide,
 *     winnerSide, bestOf, gamesToWin, pointsToWin, enableDeuce, capPoint, left, right, events }
 * events[i] 形如 { eventSeq, eventType, gameNo, leftScore, rightScore, serveSide, payloadJson }
 *
 * 坐标约定：事件与 runtime 一律为「参赛方原始坐标」（左/右以赛前对阵为准），
 * sidesSwapped 单独记录；渲染前经 runtimeToSnapshot 镜像到屏幕坐标。
 */

const FINISHED_STATUSES = new Set([2, 3])

const SCORE_SNAPSHOT = 'score_snapshot'
const SIDE_SWITCH = 'side_switch'

/** 计入撤销栈的 payload.reason（与 use-scoreboard-state 的 pushHistory 时机对应） */
const HISTORY_REASONS = new Set(['score', 'retire', ''])

export function parseEventPayload(item) {
  const raw = item?.payloadJson ?? item?.payload
  if (raw && typeof raw === 'object') return raw
  if (typeof raw === 'string' && raw.trim()) {
    try {
      const parsed = JSON.parse(raw)
      return parsed && typeof parsed === 'object' ? parsed : {}
    } catch (_) {
      return {}
    }
  }
  return {}
}

/** 记录接口事件 → 本地事件结构（与 match-events.js 内部结构一致）。 */
export function normalizeEventRecord(item) {
  const payload = parseEventPayload(item)
  return {
    seq: Number(item?.eventSeq ?? item?.seq ?? 0),
    type: String(item?.eventType ?? item?.type ?? ''),
    gameNo: Number(item?.gameNo || 1),
    leftScore: Number(item?.leftScore || 0),
    rightScore: Number(item?.rightScore || 0),
    serveSide: item?.serveSide === 'right' ? 'right' : 'left',
    payload,
    syncStatus: 'synced',
  }
}

export function normalizeRecordEvents(record) {
  const source = Array.isArray(record) ? record : (Array.isArray(record?.events) ? record.events : [])
  return source
    .map(normalizeEventRecord)
    .filter((item) => item.seq > 0 && item.type)
    .sort((left, right) => left.seq - right.seq)
}

/** undo 快照的补偿水位；非 undo 返回 null。 */
export function getUndoRevertToSeq(event) {
  if (String(event?.type || '') !== SCORE_SNAPSHOT) return null
  const payload = event?.payload && typeof event.payload === 'object'
    ? event.payload
    : parseEventPayload(event)
  if (String(payload?.reason || '') !== 'undo') return null
  const raw = payload?.revertToSeq
  if (raw == null) return null
  const value = Number(raw)
  return Number.isInteger(value) && value >= 0 ? value : null
}

export function isUndoEvent(event) {
  return getUndoRevertToSeq(event) !== null
}

/** 该事件是否对应一次「推入撤销栈」的动作（addScore / switchSides / retire）。 */
export function isHistoryBearingEvent(event) {
  const type = String(event?.type || '')
  const payload = event?.payload && typeof event.payload === 'object'
    ? event.payload
    : parseEventPayload(event)
  const reason = String(payload?.reason ?? '')
  if (type === SCORE_SNAPSHOT) return HISTORY_REASONS.has(reason)
  if (type === SIDE_SWITCH) return reason === 'manual'
  return false
}

/** 服务端（或本地事件列表）最大事件序号。 */
export function getMaxEventSeq(recordOrEvents) {
  const events = Array.isArray(recordOrEvents)
    ? recordOrEvents
    : normalizeRecordEvents(recordOrEvents)
  return events.reduce((max, item) => {
    const seq = Number(item?.seq ?? item?.eventSeq ?? 0)
    return Number.isInteger(seq) && seq > max ? seq : max
  }, 0)
}

/** 事件里是否已有实质进度（init 基线不算）。 */
export function hasEventProgress(recordOrEvents) {
  const events = Array.isArray(recordOrEvents)
    ? recordOrEvents
    : normalizeRecordEvents(recordOrEvents)
  return events.some((item) => {
    const payload = item?.payload && typeof item.payload === 'object' ? item.payload : {}
    if (String(item?.type || '') === SCORE_SNAPSHOT && String(payload?.reason || '') === 'init') return false
    return Number(item?.leftScore || 0) > 0
      || Number(item?.rightScore || 0) > 0
      || String(payload?.reason || '') === 'retire'
      || Number(payload?.revertToSeq || 0) > 0
      || String(item?.type || '') === SIDE_SWITCH
  })
}

export function isMatchSettled(record) {
  return FINISHED_STATUSES.has(Number(record?.status || 0))
}

/** 从最新事件向前找带 runtime 的快照（原始坐标）。 */
export function findLatestRuntime(events) {
  const ordered = [...(Array.isArray(events) ? events : [])].sort((left, right) => Number(right?.seq || 0) - Number(left?.seq || 0))
  for (const item of ordered) {
    const payload = item?.payload && typeof item.payload === 'object' ? item.payload : parseEventPayload(item)
    if (payload?.runtime && typeof payload.runtime === 'object') return payload.runtime
  }
  return null
}

/**
 * 撤销栈重建：按 seq 升序扫描，计入撤销栈的事件压入其 prevRuntime，遇 undo 弹栈。
 * 语义与 use-scoreboard-state 中「pushHistory 于动作前、undo 弹一条」完全等价，
 * 因此换设备接管后可以继续撤销。
 */
export function rebuildHistoryStack(events) {
  // 栈条目带 seq：后端 buildEventRecords 会把「seq>revertToSeq 的已撤销 side_switch」从
  // record 里剔除（W-NEW-2），被撤条目不在列表时若仍无条件弹栈，会错删更早的无辜条目。
  const entries = []
  const ordered = [...(Array.isArray(events) ? events : [])].sort((left, right) => Number(left?.seq || 0) - Number(right?.seq || 0))
  ordered.forEach((item) => {
    if (isUndoEvent(item)) {
      const revertToSeq = Number(getUndoRevertToSeq(item) || 0)
      const top = entries[entries.length - 1]
      if (top && Number(top.seq || 0) > revertToSeq) {
        entries.pop()
      }
      return
    }
    if (!isHistoryBearingEvent(item)) return
    const payload = item?.payload && typeof item.payload === 'object' ? item.payload : parseEventPayload(item)
    const prevRuntime = payload?.prevRuntime
    if (prevRuntime && typeof prevRuntime === 'object') {
      entries.push({ seq: Number(item?.seq || 0), runtime: prevRuntime })
    }
  })
  return entries.map((entry) => entry.runtime)
}

function normalizeGameScores(gameScores, sidesSwapped) {
  const source = Array.isArray(gameScores) ? gameScores : []
  return source.map((game) => {
    const leftScore = Number(game?.leftScore || 0)
    const rightScore = Number(game?.rightScore || 0)
    const winnerSide = game?.winnerSide === 'left' || game?.winnerSide === 'right'
      ? game.winnerSide
      : leftScore > rightScore ? 'left' : 'right'
    if (!sidesSwapped) {
      return { gameNo: Number(game?.gameNo || 0), leftScore, rightScore, winnerSide }
    }
    return {
      gameNo: Number(game?.gameNo || 0),
      leftScore: rightScore,
      rightScore: leftScore,
      winnerSide: winnerSide === 'left' ? 'right' : 'left',
    }
  }).sort((left, right) => left.gameNo - right.gameNo)
}

function swapSide(side) {
  if (side === 'left') return 'right'
  if (side === 'right') return 'left'
  return ''
}

/**
 * 原始坐标 runtime → 可直接 applySnapshot 的屏幕坐标快照。
 * sidesSwapped=true 时整体镜像（队名/比分/局分/发球方/退赛方/逐局比分），
 * 镜像后 sidesSwapped 仍为 true，与 use-scoreboard-state 的 toOriginal* 语义闭环。
 */
export function runtimeToSnapshot(runtime) {
  if (!runtime || typeof runtime !== 'object') return null
  const swapped = !!runtime.sidesSwapped
  const leftScore = Number(runtime.leftScore || 0)
  const rightScore = Number(runtime.rightScore || 0)
  return {
    leftTeam: (swapped ? runtime.rightTeam : runtime.leftTeam) || '',
    rightTeam: (swapped ? runtime.leftTeam : runtime.rightTeam) || '',
    leftScore: swapped ? rightScore : leftScore,
    rightScore: swapped ? leftScore : rightScore,
    leftGameWins: Number(swapped ? (runtime.rightGameWins || 0) : (runtime.leftGameWins || 0)),
    rightGameWins: Number(swapped ? (runtime.leftGameWins || 0) : (runtime.rightGameWins || 0)),
    currentGameNo: Number(runtime.currentGameNo || 1),
    gameScores: normalizeGameScores(runtime.gameScores, swapped),
    serveSide: swapped ? (swapSide(runtime.serveSide) || 'left') : (runtime.serveSide === 'right' ? 'right' : 'left'),
    retiredSide: swapped ? swapSide(runtime.retiredSide) : (runtime.retiredSide || ''),
    matchEnded: !!runtime.matchEnded,
    winnerName: runtime.winnerName || '',
    matchStartTime: Number(runtime.matchStartTime || 0),
    matchDuration: runtime.matchDuration || '0分0秒',
    finalGameSideSwitchPending: !!runtime.finalGameSideSwitchPending,
    finalGameSideSwitchHandled: !!runtime.finalGameSideSwitchHandled,
    gameEndPromptPending: !!runtime.gameEndPromptPending,
    gameEndPromptHandled: !!runtime.gameEndPromptHandled,
    matchRules: runtime.matchRules && typeof runtime.matchRules === 'object' ? { ...runtime.matchRules } : undefined,
    sidesSwapped: swapped,
  }
}

function buildFallbackSnapshot(record, events) {
  const latest = events.length ? events[events.length - 1] : null
  const gameScores = normalizeGameScores(
    Array.isArray(record?.gameScores) && record.gameScores.length
      ? record.gameScores
      : events.filter((item) => String(item.type) === SCORE_SNAPSHOT),
    false,
  )
  return {
    leftTeam: record?.left?.name || '',
    rightTeam: record?.right?.name || '',
    leftScore: Number(latest?.leftScore || 0),
    rightScore: Number(latest?.rightScore || 0),
    leftGameWins: Number(record?.leftGameWins || 0),
    rightGameWins: Number(record?.rightGameWins || 0),
    currentGameNo: Number(latest?.gameNo || 1),
    gameScores: Array.isArray(record?.gameScores) && record.gameScores.length ? gameScores : [],
    serveSide: latest?.serveSide === 'right' ? 'right' : 'left',
    retiredSide: record?.retiredSide || '',
    matchEnded: isMatchSettled(record),
    winnerName: record?.winnerSide === 'left'
      ? (record?.left?.name || '')
      : record?.winnerSide === 'right' ? (record?.right?.name || '') : '',
    matchStartTime: 0,
    matchDuration: '0分0秒',
    finalGameSideSwitchPending: false,
    finalGameSideSwitchHandled: false,
    gameEndPromptPending: false,
    gameEndPromptHandled: false,
    matchRules: record?.pointsToWin == null ? undefined : {
      bestOf: Number(record.bestOf || 3),
      gamesToWin: Number(record.gamesToWin || Math.floor(Number(record.bestOf || 3) / 2) + 1),
      pointsToWin: Number(record.pointsToWin || 21),
      enableDeuce: record.enableDeuce !== false,
      capPoint: Number(record.capPoint || 30),
    },
    sidesSwapped: false,
  }
}

/**
 * 由记录构造恢复态：
 * - snapshot：屏幕坐标，可直接 applySnapshot；
 * - historyStack：屏幕坐标的撤销栈（可直接赋值给 historyStack ref）；
 * - events：归一化事件（已同步），用于继续增量上云；
 * - nextEventSeq / lastSyncedEventSeq：事件水位。
 */
export function buildRecoveredState(record) {
  const events = normalizeRecordEvents(record)
  const runtime = findLatestRuntime(events)
  const snapshot = runtime ? runtimeToSnapshot(runtime) : buildFallbackSnapshot(record, events)
  if (!snapshot) return null
  const maxSeq = getMaxEventSeq(events)
  return {
    events,
    snapshot,
    historyStack: rebuildHistoryStack(events).map((item) => runtimeToSnapshot(item)).filter(Boolean),
    nextEventSeq: maxSeq + 1,
    lastSyncedEventSeq: maxSeq,
    matchEnded: !!snapshot.matchEnded,
    settled: isMatchSettled(record),
    usedRuntime: !!runtime,
  }
}

/**
 * 本地缓存 vs 服务端记录的仲裁。
/**
 * 本地缓存 vs 服务端记录的仲裁。
 *
 * | 条件 | 数据源 |
 * | 已结算（status 2/3） | 服务端 |
 * | 服务端无事件且本地从未同步过（lastSyncedEventSeq=0，含改造前老缓存） | 本地（部署过渡保护） |
 * | 服务端无事件但本地曾同步过（>0）→ 服务端事件被清（restart 重开） | 服务端（防旧比分复活） |
 * | sameSession 且本地已同步水位 ≥ 服务端最大序号 | 本地（同设备续裁，含未同步事件） |
 * | 其余（换设备 / 顶锁 / 本地落后） | 服务端重放 |
 */
export function shouldUseLocalCache(record, cached, options = {}) {
  if (!cached || typeof cached !== 'object') return false
  if (isMatchSettled(record)) return false
  const serverMaxSeq = getMaxEventSeq(record)
  if (serverMaxSeq <= 0) {
    // 服务端无事件：只有「本地从未同步过」才优先本地（改造前老比赛的过渡保护）。
    // 本地曾同步过而服务端现在是空的，说明云端事件被 restart 清掉了，必须以服务端为准。
    return Number(cached.lastSyncedEventSeq || 0) <= 0
  }
  if (!options.sameSession) return false
  const localSyncedSeq = Number(cached.lastSyncedEventSeq || 0)
  if (localSyncedSeq < serverMaxSeq) return false
  const serverGameNo = getServerGameNo(record)
  return Number(cached.currentGameNo || 0) >= serverGameNo
}

/**
 * 服务端事件被清空（restart 重开）而本地还持有「曾同步过」的缓存
 * → 本地缓存属于已被重开的旧一局，进入时应整体丢弃，防止复活旧比分甚至重新结算旧结果。
 */
export function isRestartedMatch(record, cached) {
  if (!record || !cached || typeof cached !== 'object') return false
  return getMaxEventSeq(record) <= 0 && Number(cached.lastSyncedEventSeq || 0) > 0
}

/**
 * 进入比赛的数据源决策（纯函数，便于单测覆盖交集）。
 * 返回 'settled' | 'restarted' | 'server' | 'local' | 'fresh'。
 * 注意 record=null（记录接口失败）绝不产生写动作：最多走本地/全新，天然规避
 * 「网络抖动时把 0:0 基线写上云覆盖真实比分」的事故。
 */
export function decideEntrySource({ record, cached, sameSession }) {
  if (record && isMatchSettled(record) && !shouldUseLocalCache(record, cached, { sameSession })) {
    return 'settled'
  }
  if (isRestartedMatch(record, cached)) return 'restarted'
  if (record && !shouldUseLocalCache(record, cached, { sameSession }) && getMaxEventSeq(record) > 0) {
    return 'server'
  }
  if (cached) return 'local'
  return 'fresh'
}

/** 服务端侧当前局号：事件/逐局比分里出现过的最大局号。 */
export function getServerGameNo(record) {
  const events = normalizeRecordEvents(record)
  const eventGameNo = events.reduce((max, item) => Math.max(max, Number(item.gameNo || 0)), 0)
  const gameScores = Array.isArray(record?.gameScores) ? record.gameScores : []
  const scoredGameNo = gameScores.reduce((max, item) => Math.max(max, Number(item?.gameNo || 0)), 0)
  return Math.max(eventGameNo, scoredGameNo, 1)
}

export {
  SCORE_SNAPSHOT,
  SIDE_SWITCH,
}

/**
 * 进入比赛时决定双方显示名（B-NEW-1 回归修复）。
 *
 * 队名 ref 初始值「左队/右队」是**真值**，不能用 `!leftTeam` 判断是否已赋过名：
 * - fresh（全新/restart 后）：路由队名**无条件**生效（对齐重构前行为），仅路由缺失时保留当前/默认名；
 * - 恢复路径（server/local）：恢复出的名字优先，仅恢复名为空时才用路由名兜底。
 */
export function resolveEntryTeamNames(current, options, { fresh }) {
  const leftNameFromRoute = options?.leftName ? decodeURIComponent(options.leftName) : ''
  const rightNameFromRoute = options?.rightName ? decodeURIComponent(options.rightName) : ''
  if (fresh) {
    return {
      leftTeam: leftNameFromRoute || current?.leftTeam || '左队',
      rightTeam: rightNameFromRoute || current?.rightTeam || '右队',
    }
  }
  return {
    leftTeam: current?.leftTeam || leftNameFromRoute || '左队',
    rightTeam: current?.rightTeam || rightNameFromRoute || '右队',
  }
}
