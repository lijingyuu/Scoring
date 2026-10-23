import { getMaxEventSeq, isHistoryBearingEvent, isUndoEvent, normalizeRecordEvents } from './badminton-recovery'

/**
 * 羽毛球执裁「事件上云」管道
 *
 * 与排球 pages/volleyball/composables/useScoreboard.js 的事件管道同构（不改动排球代码）：
 * - 每个记分板动作追加一条事件，800ms 防抖后整批 PUT /api/v1/matches/{id}/events；
 * - 失败按 1s→×2→30s 指数退避重试；
 * - 409「事件序号与已有记录冲突」时按后端回带的服务端最大序号重排本地未同步事件并重试一次；
 * - 只读（锁被接管）后停止冲刷与重试；
 * - 终局动作（制胜分/退赛）立即冲刷不等防抖——排球同款，防「最后一分后杀进程丢事件」。
 *
 * 与排球的两点差异（均为了羽毛球「撤销栈」能跨设备重建）：
 * 1. 每条事件 payload 同时内嵌 runtime（动作后状态）与 prevRuntime（动作前状态；undo 快照为 null）；
 * 2. 撤销不重写历史，只追加 score_snapshot(reason:'undo', revertToSeq)，revertToSeq = 被撤销动作
 *    之前最近一条事件的 seq（保留水位，与后端 resolveRevertedEventSeqs 的语义对齐）。
 *
 * 依赖注入（便于无头测试）：events / nextEventSeq / lastSyncedEventSeq 为 Vue ref，
 * request / requestOptions / persist / getMatchId / isReadOnly 由页面提供。
 *
 * 动作映射（actionEventMap）与撤销栈动作集（historyActions）同样可注入：
 * 接力赛 pages/tournament/team-relay.vue 按「段」记分复用本管道，注入 relay 版映射
 * （全部 score_snapshot 类型 + 自定义 reason，避免 side_switch 类型被后端 undo
 * 补偿从 record 剔除——接力赛换边不进撤销栈，被连带剔除会丢换边记录）。
 * 缺省时保持上方羽毛球映射不变。
 */

const EVENT_SEQ_CONFLICT_MARKER = '事件序号与已有记录冲突'
const EVENT_SEQ_CONFLICT_MAX_SEQ_PATTERN = /服务端最大序号\s*(\d+)/

const FLUSH_DEBOUNCE_MS = 800
const RETRY_INITIAL_MS = 1000
const RETRY_MAX_MS = 30000

// 本地缓存防膨胀：超过上限时丢最旧的已同步事件（微信单 key 1MB 上限；
// 撤销栈深度随之收窄到 KEEP_EVENTS_AFTER_PRUNE，对真实比赛足够）
const MAX_STORED_EVENTS = 300
const KEEP_EVENTS_AFTER_PRUNE = 250

/** 记分板动作 → 事件类型/原因映射（只使用后端既有白名单类型） */
const ACTION_EVENT_MAP = {
  init: { type: 'score_snapshot', reason: 'init' },
  score: { type: 'score_snapshot', reason: 'score' },
  undo: { type: 'score_snapshot', reason: 'undo' },
  retire: { type: 'score_snapshot', reason: 'retire' },
  game_end: { type: 'side_switch', reason: 'between_games' },
  side_switch_manual: { type: 'side_switch', reason: 'manual' },
  side_switch_technical: { type: 'side_switch', reason: 'deciding_technical' },
}

/**
 * 计入撤销栈的动作——必须与 use-scoreboard-state.js 中 pushHistory 的时机一一对应
 * （addScore / switchSides / retire 推栈；confirmGameEnd 与决胜局技术换边不推栈）。
 */
const HISTORY_ACTIONS = new Set(['score', 'side_switch_manual', 'retire'])

export function isEventSeqConflictError(error) {
  return String(error?.message || '').includes(EVENT_SEQ_CONFLICT_MARKER)
}

export function parseConflictServerMaxEventSeq(message) {
  const matched = EVENT_SEQ_CONFLICT_MAX_SEQ_PATTERN.exec(String(message || ''))
  if (!matched) return 0
  const maxSeq = Number(matched[1])
  return Number.isInteger(maxSeq) && maxSeq > 0 ? maxSeq : 0
}

/** 由事件列表重建「计入撤销栈的事件序号」序列（遇 undo 弹栈）。 */
export function rebuildHistorySeqs(events) {
  const seqs = []
  const ordered = [...(Array.isArray(events) ? events : [])].sort((left, right) => Number(left?.seq || 0) - Number(right?.seq || 0))
  ordered.forEach((item) => {
    if (isUndoEvent(item)) {
      seqs.pop()
      return
    }
    if (isHistoryBearingEvent(item) && Number(item?.seq || 0) > 0) {
      seqs.push(Number(item.seq))
    }
  })
  return seqs
}

export function createMatchEventPipeline(options = {}) {
  const events = options.events
  const nextEventSeq = options.nextEventSeq
  const lastSyncedEventSeq = options.lastSyncedEventSeq
  const getMatchId = typeof options.getMatchId === 'function' ? options.getMatchId : () => ''
  const isReadOnly = typeof options.isReadOnly === 'function' ? options.isReadOnly : () => false
  const request = options.request
  const actionEventMap = options.actionEventMap || ACTION_EVENT_MAP
  const historyActions = options.historyActions || HISTORY_ACTIONS
  const requestOptions = typeof options.requestOptions === 'function' ? options.requestOptions : (extra) => extra
  const persist = typeof options.persist === 'function' ? options.persist : () => {}
  // W2 防护回调：离线会话中服务端事件被 restart 清空时触发（页面据此丢弃本地旧一局）
  const onServerEventsCleared = typeof options.onServerEventsCleared === 'function' ? options.onServerEventsCleared : null

  // 服务端状态是否已确认。进入页面成功拉到 /record 即已确认；record 拉取失败（离线）
  // 的会话中置 false，冲刷 pending 前必须先探测，防止「restart 清空 + 离线续传」
  // 把旧一局事件整批写进空事件表（复活旧比分）。
  let serverStateConfirmed = true

  function setServerStateConfirmed(confirmed) {
    serverStateConfirmed = confirmed === true
  }

  /**
   * 冲刷前的服务端状态确认（仅在未确认时执行一次探测）：
   * - 不可达 → false（本次不冲刷，保持 pending 与未确认态，下次冲刷再试）；
   * - 可达且事件为空 → 已被 restart 清空：触发 onServerEventsCleared（页面丢弃本地），false；
   * - 可达且有事件 → true 并记住已确认（序号冲突由既有 409 自愈链处理）。
   */
  async function confirmServerStateBeforeFlush() {
    try {
      const record = await request('/api/v1/matches/' + getMatchId() + '/record', { method: 'GET', silent: true })
      if (getMaxEventSeq(record) <= 0) {
        serverStateConfirmed = true
        if (onServerEventsCleared) onServerEventsCleared()
        return false
      }
      serverStateConfirmed = true
      return true
    } catch (_) {
      return false
    }
  }

  let flushTimer = null
  let retryTimer = null
  let retryDelay = RETRY_INITIAL_MS
  let inFlight = null
  let historySeqs = []

  function hasPendingEvents() {
    return events.value.some((item) => item.syncStatus !== 'synced')
  }

  function clearFlushTimer() {
    if (!flushTimer) return
    clearTimeout(flushTimer)
    flushTimer = null
  }

  function clearRetry() {
    if (retryTimer) {
      clearTimeout(retryTimer)
      retryTimer = null
    }
    retryDelay = RETRY_INITIAL_MS
  }

  function scheduleRetry() {
    if (retryTimer) return
    const delay = retryDelay
    retryDelay = Math.min(delay * 2, RETRY_MAX_MS)
    retryTimer = setTimeout(() => {
      retryTimer = null
      if (inFlight || flushTimer) return
      void flushPendingEvents()
    }, delay)
  }

  function scheduleFlush(delay = FLUSH_DEBOUNCE_MS) {
    if (isReadOnly() || !getMatchId() || !hasPendingEvents()) return
    clearFlushTimer()
    flushTimer = setTimeout(() => {
      flushTimer = null
      void flushPendingEvents()
    }, delay)
  }

  /** 本地缓存防膨胀：超过上限时丢最旧的已同步事件。 */
  function pruneSyncedEvents() {
    if (events.value.length <= MAX_STORED_EVENTS) return
    let excess = events.value.length - KEEP_EVENTS_AFTER_PRUNE
    const kept = []
    for (const item of events.value) {
      if (excess > 0 && item.syncStatus === 'synced') {
        excess -= 1
        continue
      }
      kept.push(item)
    }
    events.value = kept
    historySeqs = rebuildHistorySeqs(events.value)
  }

  /**
   * 追加一条由记分板动作派生的事件。
   * 终局动作（制胜分 / 退赛）立即冲刷，不等防抖。
   */
  function appendAction(descriptor) {
    const mapping = actionEventMap[descriptor?.action]
    if (!mapping || !getMatchId()) return null
    const runtime = descriptor.runtime && typeof descriptor.runtime === 'object' ? descriptor.runtime : {}
    const payload = { reason: mapping.reason }
    if (mapping.reason === 'score' && descriptor.scoringSide) {
      payload.scoringSide = descriptor.scoringSide
    }
    if (mapping.reason === 'retire' && descriptor.retiredSide) {
      payload.retiredSide = descriptor.retiredSide
    }
    if (mapping.reason === 'undo') {
      payload.revertToSeq = popUndoRevertToSeq()
    }
    payload.runtime = runtime
    payload.prevRuntime = descriptor.prevRuntime && typeof descriptor.prevRuntime === 'object'
      ? descriptor.prevRuntime
      : null

    const event = {
      seq: Number(nextEventSeq.value || 1),
      type: mapping.type,
      gameNo: Number(runtime.currentGameNo || 1),
      leftScore: Number(runtime.leftScore || 0),
      rightScore: Number(runtime.rightScore || 0),
      serveSide: runtime.serveSide === 'right' ? 'right' : 'left',
      payload,
      syncStatus: 'pending',
    }
    events.value.push(event)
    nextEventSeq.value = event.seq + 1
    if (historyActions.has(descriptor.action)) {
      historySeqs.push(event.seq)
    }
    persist()
    if (mapping.reason === 'retire' || runtime.matchEnded === true) {
      void flushPendingEventsImmediately()
    } else {
      scheduleFlush()
    }
    return event
  }

  /**
   * 撤销生效后应保留的事件水位 R（排球同语义，后端 resolveRevertedEventSeqs 据此过滤）：
   * 取「被撤销动作之前最近一条事件」的 seq。夹在被撤动作前面的非撤销栈事件
   * （如 between_games 局间换边）真实发生过，必须保留，否则记录页会凭空丢局边界。
   */
  function popUndoRevertToSeq() {
    if (!historySeqs.length) return 0
    const undoneSeq = historySeqs[historySeqs.length - 1]
    historySeqs.pop()
    let retained = 0
    events.value.forEach((item) => {
      const seq = Number(item.seq || 0)
      if (seq < undoneSeq && seq > retained) retained = seq
    })
    if (retained === 0 && undoneSeq > 1) {
      // 本地更早事件已被 prune 裁掉（服务端仍在）时，扫描结果会塌陷为 0，
      // 后端 undo 补偿将把全部局边界 side_switch 误标撤销（W-NEW-1）。
      // 「保留水位」的语义本就是被撤动作之前：直接取 undoneSeq-1 兜底，
    // 即使本地与服务端有少量序号空洞也只是多保留，不会多删。
      retained = undoneSeq - 1
    }
    return retained
  }

  async function fetchServerMaxEventSeq() {
    if (!getMatchId()) return 0
    try {
      const record = await request('/api/v1/matches/' + getMatchId() + '/record', { method: 'GET', silent: true })
      return Number(getMaxEventSeq(record) || 0)
    } catch (_) {
      return 0
    }
  }

  /** 按服务端最大序号重排本地未同步事件（已同步事件保持原序号）。 */
  function resyncEventSeqs(serverMaxEventSeq) {
    const baseSeq = Number(serverMaxEventSeq || 0)
    if (!Number.isInteger(baseSeq) || baseSeq <= 0) return false
    let seq = baseSeq + 1
    events.value = events.value.map((item) => {
      if (item.syncStatus === 'synced') return item
      const resequenced = { ...item, seq }
      seq += 1
      return resequenced
    })
    nextEventSeq.value = Math.max(Number(nextEventSeq.value || 1), seq)
    lastSyncedEventSeq.value = Math.max(Number(lastSyncedEventSeq.value || 0), baseSeq)
    historySeqs = rebuildHistorySeqs(events.value)
    persist()
    return true
  }

  function buildPendingPayload() {
    return events.value
      .filter((item) => item.syncStatus !== 'synced')
      .map((item) => ({
        eventSeq: item.seq,
        eventType: item.type,
        gameNo: item.gameNo,
        leftScore: item.leftScore,
        rightScore: item.rightScore,
        serveSide: item.serveSide,
        payloadJson: JSON.stringify(item.payload || {}),
      }))
  }

  /** 单次 PUT 并在成功后标记同步；失败原样抛出，由调用方决定重试策略。 */
  async function putPendingEvents(pendingEvents) {
    await request('/api/v1/matches/' + getMatchId() + '/events', requestOptions({
      method: 'PUT',
      data: { events: pendingEvents },
      silent: true,
    }))
    const syncedSeqs = new Set(pendingEvents.map((item) => item.eventSeq))
    let maxSyncedSeq = Number(lastSyncedEventSeq.value || 0)
    events.value = events.value.map((item) => {
      if (!syncedSeqs.has(item.seq)) return item
      maxSyncedSeq = Math.max(maxSyncedSeq, item.seq)
      return { ...item, syncStatus: 'synced' }
    })
    lastSyncedEventSeq.value = maxSyncedSeq
    pruneSyncedEvents()
    persist()
    clearRetry()
    return true
  }

  async function flushInternal(allowEventSeqResync) {
    if (!getMatchId() || !hasPendingEvents()) return true
    if (isReadOnly()) return false
    if (inFlight) return inFlight

    const task = (async () => {
      try {
        // W2 防护：服务端状态未确认（record 曾拉取失败）时，冲刷前先确认
        // 「服务端事件表非空」——已被 restart 清空则中止并交由页面丢弃本地
        if (!serverStateConfirmed && !(await confirmServerStateBeforeFlush())) return false
        return await putPendingEvents(buildPendingPayload())
      } catch (error) {
        // 序号撞号：重排本地未同步事件后立即重试一次（仅一次），成功即静默收敛。
        // 注意 inFlight 在整条自愈链（含 record 兜底与重试 PUT）期间保持占用，
        // 防止重排窗口内新触发的冲刷并发双写。
        if (allowEventSeqResync && isEventSeqConflictError(error)) {
          const serverMaxEventSeq = parseConflictServerMaxEventSeq(error?.message)
            || await fetchServerMaxEventSeq()
          const realigned = resyncEventSeqs(serverMaxEventSeq)
          if (realigned) {
            try {
              return await putPendingEvents(buildPendingPayload())
            } catch (_) {
              scheduleRetry()
              return false
            }
          }
          scheduleRetry()
          return false
        }
        scheduleRetry()
        return false
      }
    })()

    inFlight = task
    const result = await task
    if (inFlight === task) inFlight = null
    return result
  }

  function flushPendingEvents() {
    return flushInternal(true)
  }

  /**
   * 比赛结束/退赛落定后立即冲刷：不等 800ms 防抖，已有 in-flight 请求则链在其后，
   * 保证结算 finish 发出前本次产生的所有事件都已落库。
   */
  function flushPendingEventsImmediately() {
    clearFlushTimer()
    if (inFlight) {
      return inFlight.then(() => flushPendingEvents())
    }
    return flushPendingEvents()
  }

  /** 只读 / 页面卸载时停止一切网络重试。 */
  function stop() {
    clearFlushTimer()
    clearRetry()
    inFlight = null
  }

  /** 比赛已结算或本地状态被丢弃时清空管道。 */
  function reset() {
    stop()
    events.value = []
    nextEventSeq.value = 1
    lastSyncedEventSeq.value = 0
    historySeqs = []
    // 本地已整体丢弃（结算/restart 丢弃/页面重置）：重置后的会话视为已确认
    serverStateConfirmed = true
  }

  /** 用记录接口（或本地缓存）恢复管道水位与撤销栈水位。 */
  function restore(state = {}) {
    stop()
    const normalized = Array.isArray(state.events)
      ? state.events
      : normalizeRecordEvents(state.record)
    events.value = normalized.map((item) => ({
      seq: Number(item.seq || 0),
      type: String(item.type || ''),
      gameNo: Number(item.gameNo || 1),
      leftScore: Number(item.leftScore || 0),
      rightScore: Number(item.rightScore || 0),
      serveSide: item.serveSide === 'right' ? 'right' : 'left',
      payload: item.payload && typeof item.payload === 'object' ? item.payload : {},
      syncStatus: item.syncStatus === 'pending' ? 'pending' : 'synced',
    }))
    const maxSeq = events.value.reduce((max, item) => Math.max(max, Number(item.seq || 0)), 0)
    nextEventSeq.value = Number(state.nextEventSeq || (maxSeq + 1))
    lastSyncedEventSeq.value = Number(state.lastSyncedEventSeq ?? maxSeq)
    historySeqs = rebuildHistorySeqs(events.value)
  }

  return {
    appendAction,
    scheduleFlush,
    flushPendingEvents,
    flushPendingEventsImmediately,
    hasPendingEvents,
    fetchServerMaxEventSeq,
    setServerStateConfirmed,
    stop,
    reset,
    restore,
    getHistorySeqs: () => [...historySeqs],
  }
}
