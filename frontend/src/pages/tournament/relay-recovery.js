/**
 * 接力赛「分数交接」纯函数集合：事件上云的动作映射 + 服务端记录恢复 + 进入仲裁。
 *
 * 与羽毛球 badminton-recovery.js 同构，复用其通用纯函数（payload 结构、undo 水位、
 * 撤销栈重建语义完全一致），差异只在「局」换「段」：
 * - 接力赛按段记分、无发球权：事件 gameNo = 段号（currentSegmentNo），
 *   serveSide 借用 lastScoredSide（最近得分方，同为 left/right，满足后端字段校验）。
 * - 事件类型统一 score_snapshot，reason 区分动作：score / undo / side_switch / segment_continue。
 *   刻意不用 side_switch 事件类型：后端 undo 补偿会把 seq>revertToSeq 的 side_switch
 *   事件从 record 剔除，而接力赛换边不进撤销栈，被连带剔除会丢换边记录。
 * - runtime / prevRuntime 为「参赛方原始坐标」的全量比分快照（buildScoreState 六字段
 *   + currentSegmentNo/matchEnded/serveSide），sidesSwapped 只影响屏幕镜像；
 *   恢复时直接 applyScoreState(runtime)，无需羽毛球那样的镜像转换。
 *
 * record 形如 GET /api/v1/matches/{id}/record 的返回；接力赛 finish 时
 * relaySegmentScores 优先落 match.game_scores，因此 record.gameScores 即每段比分。
 */

import {
  findLatestRuntime,
  getMaxEventSeq,
  isMatchSettled,
  isRestartedMatch,
  normalizeRecordEvents,
  rebuildHistoryStack,
} from '../scoreboard/badminton-recovery'

/** 记分板动作 → 事件映射（全部走后端白名单类型 score_snapshot，reason 自定义）。 */
export const RELAY_ACTION_EVENT_MAP = {
  score: { type: 'score_snapshot', reason: 'score' },
  undo: { type: 'score_snapshot', reason: 'undo' },
  side_switch: { type: 'score_snapshot', reason: 'side_switch' },
  segment_continue: { type: 'score_snapshot', reason: 'segment_continue' },
}

/**
 * 计入事件撤销栈水位的动作——必须与 team-relay.vue 中 pushHistory 的时机一一对应
 * （只有加分推栈；初始换边与段推进不推栈）。
 */
export const RELAY_HISTORY_ACTIONS = new Set(['score'])

/** 事件 runtime → applyScoreState 可直接消费的比分状态（原始坐标）。 */
export function relayRuntimeToScoreState(runtime) {
  if (!runtime || typeof runtime !== 'object') return null
  return {
    leftScore: Number(runtime.leftScore || 0),
    rightScore: Number(runtime.rightScore || 0),
    segmentScores: (Array.isArray(runtime.segmentScores) ? runtime.segmentScores : [])
      .map((item) => ({
        segmentNo: Number(item?.segmentNo || 0),
        leftScore: Number(item?.leftScore || 0),
        rightScore: Number(item?.rightScore || 0),
      }))
      .filter((item) => item.segmentNo > 0),
    segmentSwitchPending: !!runtime.segmentSwitchPending,
    sidesSwapped: !!runtime.sidesSwapped,
    lastScoredSide: runtime.lastScoredSide === 'right' ? 'right' : (runtime.lastScoredSide === 'left' ? 'left' : ''),
  }
}

/** record.gameScores（= relaySegmentScores 落库）→ segmentScores。 */
function recordGameScoresToSegments(record) {
  const source = Array.isArray(record?.gameScores) ? record.gameScores : []
  return source
    .map((item) => ({
      segmentNo: Number(item?.gameNo || 0),
      leftScore: Number(item?.leftScore || 0),
      rightScore: Number(item?.rightScore || 0),
    }))
    .filter((item) => item.segmentNo > 0)
    .sort((left, right) => left.segmentNo - right.segmentNo)
}

function parseScoreDisplay(text) {
  const matched = String(text || '').match(/^(\d+):(\d+)$/)
  if (!matched) return [0, 0]
  return [Number(matched[1] || 0), Number(matched[2] || 0)]
}

/**
 * 由服务端记录构造恢复态：
 * - snapshot：原始坐标比分状态，可直接 applyScoreState；
 * - historyStack：撤销栈（rebuildHistoryStack 返回各撤销动作的 prevRuntime，
 *   与页面 historyStack 元素同构，换设备接管后可继续撤销）；
 * - events / nextEventSeq / lastSyncedEventSeq：事件与水位，供管道续传增量。
 */
export function buildRelayRecoveredState(record) {
  const events = normalizeRecordEvents(record)
  const runtime = findLatestRuntime(events)
  let snapshot = relayRuntimeToScoreState(runtime)
  if (!snapshot) {
    // 兜底：事件无 runtime（不应发生）时从记录构造最小恢复态
    const latest = events.length ? events[events.length - 1] : null
    const display = parseScoreDisplay(record?.scoreDisplay)
    snapshot = {
      leftScore: Number(latest?.leftScore || display[0] || 0),
      rightScore: Number(latest?.rightScore || display[1] || 0),
      segmentScores: recordGameScoresToSegments(record),
      segmentSwitchPending: false,
      sidesSwapped: false,
      lastScoredSide: '',
    }
  }
  const maxSeq = getMaxEventSeq(events)
  return {
    events,
    snapshot,
    historyStack: rebuildHistoryStack(events),
    nextEventSeq: maxSeq + 1,
    lastSyncedEventSeq: maxSeq,
  }
}

/** 服务端侧当前段号：事件 gameNo / 每段比分里出现过的最大段号。 */
export function getServerSegmentNo(record) {
  const events = normalizeRecordEvents(record)
  const eventSegmentNo = events.reduce((max, item) => Math.max(max, Number(item.gameNo || 0)), 0)
  const gameScores = Array.isArray(record?.gameScores) ? record.gameScores : []
  const scoredSegmentNo = gameScores.reduce((max, item) => Math.max(max, Number(item?.gameNo || 0)), 0)
  return Math.max(eventSegmentNo, scoredSegmentNo, 1)
}

/**
 * 本地缓存 vs 服务端记录的仲裁（对齐 badminton shouldUseLocalCache 决策表，段号适配）。
 *
 * | 条件 | 数据源 |
 * | 已结算（status 2/3） | 服务端 |
 * | 服务端无事件且本地从未同步过（含改造前老缓存） | 本地（部署过渡保护） |
 * | 服务端无事件但本地曾同步过 → 服务端事件被清（restart 重开） | 服务端（防旧比分复活） |
 * | sameSession 且本地已同步水位 ≥ 服务端最大序号 | 本地（同设备续裁，含未同步事件） |
 * | 其余（换设备 / 顶锁 / 本地落后） | 服务端重放 |
 */
export function shouldUseRelayLocalCache(record, cached, options = {}) {
  if (!cached || typeof cached !== 'object') return false
  if (isMatchSettled(record)) return false
  const serverMaxSeq = getMaxEventSeq(record)
  if (serverMaxSeq <= 0) {
    return Number(cached.lastSyncedEventSeq || 0) <= 0
  }
  if (!options.sameSession) return false
  const localSyncedSeq = Number(cached.lastSyncedEventSeq || 0)
  if (localSyncedSeq < serverMaxSeq) return false
  const serverSegmentNo = getServerSegmentNo(record)
  const localSegmentNo = Number(
    cached.currentSegmentNo
      || (Array.isArray(cached.segmentScores) ? cached.segmentScores.length : 0)
      || 0,
  )
  return localSegmentNo >= serverSegmentNo
}

/**
 * 进入比赛的数据源决策（纯函数，便于单测覆盖交集）。
 * 返回 'settled' | 'restarted' | 'server' | 'local' | 'fresh'。
 * 注意 record=null（记录接口失败）绝不产生写动作：最多走本地/全新，天然规避
 * 「网络抖动时把 0:0 基线写上云覆盖真实比分」的事故。
 */
export function decideRelayEntrySource({ record, cached, sameSession }) {
  if (record && isMatchSettled(record) && !shouldUseRelayLocalCache(record, cached, { sameSession })) {
    return 'settled'
  }
  if (isRestartedMatch(record, cached)) return 'restarted'
  if (record && !shouldUseRelayLocalCache(record, cached, { sameSession }) && getMaxEventSeq(record) > 0) {
    return 'server'
  }
  if (cached) return 'local'
  return 'fresh'
}
