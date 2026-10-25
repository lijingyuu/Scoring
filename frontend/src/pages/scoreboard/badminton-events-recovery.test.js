import { afterEach, describe, expect, it } from 'vitest'
import { setupFuzzerEnvironment } from './fuzzer-env'
import { useScoreboardState } from './use-scoreboard-state'
import { createMatchEventPipeline, parseConflictServerMaxEventSeq, rebuildHistorySeqs } from './match-events'
import {
  buildRecoveredState,
  decideEntrySource,
  getMaxEventSeq,
  hasEventProgress,
  isMatchSettled,
  isRestartedMatch,
  rebuildHistoryStack,
  resolveEntryTeamNames,
  shouldUseLocalCache,
} from './badminton-recovery'
/**
 * 羽毛球「事件上云 + 服务端恢复」测试
 *
 * 驱动真实状态机（use-scoreboard-state）+ 真实事件管道（match-events），
 * 验证排球同款的「锁交接 + 分数交接」在羽毛球侧成立：
 * - 换设备 / 清缓存后从服务端事件重放恢复比分、各局记录、发球方、撤销栈；
 * - 换边（含局间换边）后事件仍以参赛方原始坐标落库；
 * - 撤销只追加 undo 快照，恢复后撤销栈可继续使用；
 * - 409 序号冲突按服务端最大序号重排后重试；
 * - 只读（锁被接管）停发事件。
 */

const pipelines = []

afterEach(() => {
  pipelines.splice(0).forEach((item) => item.stop())
})

function createDeferred() {
  let resolve
  const promise = new Promise((r) => { resolve = r })
  return { promise, resolve }
}

/**
 * 假服务端尽量贴近真实契约（审查 W9：防「假服务端零契约覆盖」）：
 * - 同 seq 同 payload → 幂等跳过；同 seq 异 payload → 409（message 带服务端最大序号）；
 * - batches 记录每次 PUT 的 seq 批次，用于断言「并发冲刷合并」；
 * - gateRecord/resolveRecord 手工挂起 /record，用于构造 409 自愈窗口。
 */
function createFakeServer() {
  const stored = []
  const batches = []
  let rejectOnce = null
  let recordGate = null
  return {
    stored,
    batches,
    rejectNextWith(error) {
      rejectOnce = error
    },
    gateRecord() {
      recordGate = createDeferred()
    },
    resolveRecord(record) {
      if (!recordGate) return
      recordGate.resolve(record)
      recordGate = null
    },
    record() {
      // T-1：对齐后端 buildEventRecords 语义——undo 补偿会把「seq>revertToSeq 的
      // 已撤销可回滚事件（side_switch 等）」从 record 里剔除（score_snapshot 不剔除）
      const revertibleTypes = new Set(['substitution', 'timeout', 'captain_change', 'side_switch'])
      const revertedSeqs = new Set()
      const revertibleSeqs = []
      for (const item of stored) {
        let payload = {}
        try { payload = JSON.parse(item.payloadJson || '{}') } catch (_) { payload = {} }
        if (item.eventType === 'score_snapshot' && payload.reason === 'undo' && payload.revertToSeq != null) {
          const revertToSeq = Number(payload.revertToSeq) || 0
          for (const seq of revertibleSeqs) {
            if (seq > revertToSeq) revertedSeqs.add(seq)
          }
          continue
        }
        if (revertibleTypes.has(item.eventType)) revertibleSeqs.push(item.eventSeq)
      }
      return {
        status: 0,
        events: stored
          .filter((item) => !revertedSeqs.has(item.eventSeq))
          .map((item) => ({
            eventSeq: item.eventSeq,
            eventType: item.eventType,
            gameNo: item.gameNo,
            leftScore: item.leftScore,
            rightScore: item.rightScore,
            serveSide: item.serveSide,
            payloadJson: item.payloadJson,
          })),
      }
    },
    async request(url, options = {}) {
      if (rejectOnce) {
        const error = rejectOnce
        rejectOnce = null
        throw error
      }
      if (String(url).endsWith('/events')) {
        batches.push(options.data.events.map((item) => item.eventSeq))
        for (const item of options.data.events) {
          const existing = stored.find((candidate) => candidate.eventSeq === item.eventSeq)
          if (existing) {
            if (existing.payloadJson === item.payloadJson) continue
            const maxSeq = stored.reduce((max, candidate) => Math.max(max, candidate.eventSeq), 0)
            throw new Error(`事件序号与已有记录冲突，请刷新后重试（服务端最大序号 ${maxSeq}）`)
          }
          stored.push({ ...item })
        }
        return {}
      }
      if (String(url).endsWith('/record')) {
        if (recordGate) return recordGate.promise
        return this.record()
      }
      return {}
    },
  }
}

/** 一个「设备」= 一份本地状态机 + 一条事件管道。 */
function createDevice(server, options = {}) {
  let pipeline = null
  const state = useScoreboardState({
    onAction: (descriptor) => pipeline?.appendAction(descriptor),
  })
  pipeline = createMatchEventPipeline({
    events: state.matchEvents,
    nextEventSeq: state.nextEventSeq,
    lastSyncedEventSeq: state.lastSyncedEventSeq,
    getMatchId: () => state.matchId.value,
    isReadOnly: () => options.readOnly === true,
    request: (url, requestOptions) => server.request(url, requestOptions),
    requestOptions: (extra) => extra,
    persist: () => state.saveStateToStorage(),
  })
  state.matchId.value = 'match-1'
  pipelines.push(pipeline)
  return { state, pipeline }
}

function playPoints(state, side, count) {
  for (let index = 0; index < count; index += 1) {
    state.addScore(side)
  }
}

describe('羽毛球事件上云与恢复', () => {
  it('换设备后按服务端事件恢复比分/发球方/撤销栈，并可继续增量上云', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const deviceA = createDevice(server)
    deviceA.state.leftTeam.value = '甲'
    deviceA.state.rightTeam.value = '乙'
    deviceA.state.matchStartTime.value = Date.now()

    playPoints(deviceA.state, 'left', 2)
    playPoints(deviceA.state, 'right', 1)
    await deviceA.pipeline.flushPendingEventsImmediately()

    expect(server.stored).toHaveLength(3)
    expect(server.stored[2].leftScore).toBe(2)
    expect(server.stored[2].rightScore).toBe(1)
    expect(server.stored[2].serveSide).toBe('right')

    // 换设备：本地缓存清空，服务端事件仍在
    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    expect(shouldUseLocalCache(record, null, { sameSession: false })).toBe(false)

    const deviceB = createDevice(server)
    const recovered = buildRecoveredState(record)
    expect(recovered).not.toBeNull()
    expect(deviceB.state.applyRecoveredState(recovered)).toBe(true)

    expect(deviceB.state.leftScore.value).toBe(2)
    expect(deviceB.state.rightScore.value).toBe(1)
    expect(deviceB.state.serveSide.value).toBe('right')
    expect(deviceB.state.leftTeam.value).toBe('甲')
    expect(deviceB.state.rightTeam.value).toBe('乙')
    // 三次加分 → 撤销栈 3 条（每次加分前推栈）
    expect(deviceB.state.historyStack.value).toHaveLength(3)

    // 换设备后撤销仍可用：回退到第 3 分之前（2:0）
    expect(deviceB.state.undo()).toBe(true)
    expect(deviceB.state.leftScore.value).toBe(2)
    expect(deviceB.state.rightScore.value).toBe(0)

    // 事件序号水位续接：下一条事件从 4 开始，坐标为原始坐标
    playPoints(deviceB.state, 'right', 1)
    await deviceB.pipeline.flushPendingEventsImmediately()
    // 序列：1~3 加分、4 撤销、5 本次加分
    expect(server.stored[server.stored.length - 1].eventSeq).toBe(5)
    expect(server.stored[server.stored.length - 1].leftScore).toBe(2)
    expect(server.stored[server.stored.length - 1].rightScore).toBe(1)
  })

  it('双打站位随事件上云：撤销到栈底/中途后换设备，court 字段随恢复完整还原（Y4 轻量版）', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const deviceA = createDevice(server)
    const s = deviceA.state
    s.leftTeam.value = '甲一/甲二'
    s.rightTeam.value = '乙一/乙二'
    s.matchStartTime.value = Date.now()

    // 进入双打模式并录入开局站位：乙一/乙二 先发，左锚 0、右锚 1
    expect(s.applyCourtContext({
      mode: 'doubles',
      leftMembers: ['甲一', '甲二'],
      rightMembers: ['乙一', '乙二'],
    })).toBe(true)
    expect(s.setupDoublesCourt({ serveSide: 'right', leftAnchor: 0, rightAnchor: 1 })).toBe(true)

    // 乙（发球方）得分 → 右锚点翻转 1→0；甲得分 → 换发不翻转
    playPoints(s, 'right', 1)
    playPoints(s, 'left', 1)
    await deviceA.pipeline.flushPendingEventsImmediately()
    // 事件序列：1 court_setup(lineup_snapshot) + 2 score + 3 score
    expect(server.stored).toHaveLength(3)
    expect(server.stored[0].eventType).toBe('lineup_snapshot')

    // —— 场景 A：撤销一分（栈中剩 1 条）→ 清缓存换设备
    expect(s.undo()).toBe(true)
    await deviceA.pipeline.flushPendingEventsImmediately()
    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    // Y4 第 2 项锚点：无缓存 + 非同会话 → 服务端重放
    expect(decideEntrySource({ record, cached: null, sameSession: false })).toBe('server')
    // 同会话且本地水位追平 → 本地续裁（court 已随本地快照持久化）
    expect(decideEntrySource({
      record,
      cached: { lastSyncedEventSeq: 4, currentGameNo: 1 },
      sameSession: true,
    })).toBe('local')

    const deviceB = createDevice(server)
    expect(deviceB.state.applyRecoveredState(buildRecoveredState(record))).toBe(true)
    const b = deviceB.state
    expect(b.leftScore.value).toBe(0)
    expect(b.rightScore.value).toBe(1)
    expect(b.serveSide.value).toBe('right')
    expect(b.historyStack.value).toHaveLength(1)
    // 恢复态 court：已录入 + 0:1 时刻的锚点（右锚点已翻转一次）
    expect(b.courtPlaced.value).toBe(true)
    expect(b.leftCourtMembers.value).toEqual(['甲一', '甲二'])
    expect(b.rightCourtMembers.value).toEqual(['乙一', '乙二'])
    expect(b.leftAnchorIndex.value).toBe(0)
    expect(b.rightAnchorIndex.value).toBe(0)
    // 撤销栈顶（可继续撤销回 0:0）的快照必须携带 court（prevRuntime 链路）
    const top = b.historyStack.value[b.historyStack.value.length - 1]
    expect(top.court).toBeDefined()
    expect(top.court.placed).toBe(true)
    expect(top.court.rightAnchor).toBe(1)

    // —— 场景 B：继续撤销到栈底（0:0）→ 再换设备恢复
    expect(b.undo()).toBe(true)
    await deviceB.pipeline.flushPendingEventsImmediately()
    env.storage.clearStorageSync()
    const record2 = await server.request('/api/v1/matches/match-1/record')
    const deviceC = createDevice(server)
    expect(deviceC.state.applyRecoveredState(buildRecoveredState(record2))).toBe(true)
    const c = deviceC.state
    expect(c.leftScore.value).toBe(0)
    expect(c.rightScore.value).toBe(0)
    expect(c.historyStack.value).toHaveLength(0)
    // 栈底恢复：录入状态保留（undo 不清 placed），锚点与先发方回到录入初始值
    expect(c.courtPlaced.value).toBe(true)
    expect(c.leftAnchorIndex.value).toBe(0)
    expect(c.rightAnchorIndex.value).toBe(1)
    expect(c.serveSide.value).toBe('right')
    expect(c.courtPlacementPending.value).toBe(false)
  })

  it('跨局并换边后恢复：局号/逐局比分/屏幕坐标全部一致', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const deviceA = createDevice(server)
    deviceA.state.leftTeam.value = '甲'
    deviceA.state.rightTeam.value = '乙'
    deviceA.state.matchStartTime.value = Date.now()
    deviceA.state.applyRules({ bestOf: 3, gamesToWin: 2, pointsToWin: 5, enableDeuce: false, capPoint: 5 })

    // 第 1 局：甲 5:0 拿下（屏左=甲）
    playPoints(deviceA.state, 'left', 5)
    expect(deviceA.state.isGameEndPromptActive.value).toBe(true)
    expect(deviceA.state.confirmGameEnd()).toBe(true)
    // 自第 2 局起每局结束换边：屏左变为乙
    expect(deviceA.state.sidesSwapped.value).toBe(true)
    expect(deviceA.state.leftTeam.value).toBe('乙')

    // 第 2 局：屏左（乙）拿 2 分
    playPoints(deviceA.state, 'left', 2)
    await deviceA.pipeline.flushPendingEventsImmediately()

    // 事件落库为参赛方原始坐标：原始左（甲）第 1 局 5:0，第 2 局 0:2
    const lastEvent = server.stored[server.stored.length - 1]
    expect(lastEvent.gameNo).toBe(2)
    expect(lastEvent.leftScore).toBe(0)
    expect(lastEvent.rightScore).toBe(2)

    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const deviceB = createDevice(server)
    deviceB.state.applyRecoveredState(buildRecoveredState(record))

    expect(deviceB.state.currentGameNo.value).toBe(2)
    expect(deviceB.state.sidesSwapped.value).toBe(true)
    expect(deviceB.state.leftTeam.value).toBe('乙')
    expect(deviceB.state.rightTeam.value).toBe('甲')
    expect(deviceB.state.leftScore.value).toBe(2)
    expect(deviceB.state.rightScore.value).toBe(0)
    expect(deviceB.state.gameScores.value).toEqual([
      { gameNo: 1, leftScore: 0, rightScore: 5, winnerSide: 'right' },
    ])
    expect(deviceB.state.leftGameWins.value).toBe(0)
    expect(deviceB.state.rightGameWins.value).toBe(1)
  })

  it('撤销后恢复：服务端只多一条 undo 快照，撤销栈水位正确', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const deviceA = createDevice(server)
    deviceA.state.leftTeam.value = '甲'
    deviceA.state.rightTeam.value = '乙'
    deviceA.state.matchStartTime.value = Date.now()

    playPoints(deviceA.state, 'left', 3)
    expect(deviceA.state.undo()).toBe(true)
    await deviceA.pipeline.flushPendingEventsImmediately()

    const undoEvent = server.stored[server.stored.length - 1]
    expect(JSON.parse(undoEvent.payloadJson).reason).toBe('undo')
    // 撤销到「第 2 分之后」：水位 = 第 2 条事件
    expect(JSON.parse(undoEvent.payloadJson).revertToSeq).toBe(2)

    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const recovered = buildRecoveredState(record)
    const deviceB = createDevice(server)
    deviceB.state.applyRecoveredState(recovered)

    expect(deviceB.state.leftScore.value).toBe(2)
    expect(deviceB.state.rightScore.value).toBe(0)
    // 撤销栈重建：3 次加分撤销 1 次 → 栈内仍有 2 条（可继续撤到 1:0、0:0）
    expect(deviceB.state.historyStack.value).toHaveLength(2)

    // 事件水位继续单调：恢复后新事件从「服务端最大序号 + 1」开始（之后的撤销继续递增）
    expect(deviceB.state.nextEventSeq.value).toBe(getMaxEventSeq(record) + 1)
    expect(deviceB.state.undo()).toBe(true)
    expect(deviceB.state.leftScore.value).toBe(1)
    expect(deviceB.state.undo()).toBe(true)
    expect(deviceB.state.leftScore.value).toBe(0)
    expect(deviceB.state.nextEventSeq.value).toBe(getMaxEventSeq(record) + 3)
  })

  it('退赛：以 score_snapshot(reason=retire) 落库并在换设备后恢复退赛态', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const deviceA = createDevice(server)
    deviceA.state.leftTeam.value = '甲'
    deviceA.state.rightTeam.value = '乙'
    deviceA.state.matchStartTime.value = Date.now()

    playPoints(deviceA.state, 'left', 1)
    expect(deviceA.state.retire('right')).toBe(true)
    await deviceA.pipeline.flushPendingEventsImmediately()

    const retireEvent = server.stored[server.stored.length - 1]
    expect(retireEvent.eventType).toBe('score_snapshot')
    const payload = JSON.parse(retireEvent.payloadJson)
    expect(payload.reason).toBe('retire')
    expect(payload.retiredSide).toBe('right')

    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const recovered = buildRecoveredState(record)
    expect(recovered.snapshot.retiredSide).toBe('right')
    expect(recovered.snapshot.matchEnded).toBe(true)
    expect(recovered.snapshot.leftGameWins).toBe(deviceA.state.matchRules.value.gamesToWin)

    const deviceB = createDevice(server)
    deviceB.state.applyRecoveredState(recovered)
    expect(deviceB.state.isLocked.value).toBe(true)
    expect(deviceB.state.lockTitle.value).toBe('乙 已退赛')
  })

  it('换边后事件仍以参赛方原始坐标写入（手动换边）', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.leftTeam.value = '甲'
    device.state.rightTeam.value = '乙'
    device.state.matchStartTime.value = Date.now()

    playPoints(device.state, 'left', 1)
    expect(device.state.switchSides()).toBe(true)
    // 换边后屏左=乙；给屏左加分等价于原始右得分
    playPoints(device.state, 'left', 1)
    await device.pipeline.flushPendingEventsImmediately()

    const lastEvent = server.stored[server.stored.length - 1]
    expect(lastEvent.leftScore).toBe(1)
    expect(lastEvent.rightScore).toBe(1)
    expect(lastEvent.serveSide).toBe('right')
    // 换边事件本身也落库
    const sideSwitch = server.stored.find((item) => item.eventType === 'side_switch')
    expect(sideSwitch).toBeTruthy()
    expect(JSON.parse(sideSwitch.payloadJson).reason).toBe('manual')
  })

  it('结算前强制冲刷：flushPendingEventsImmediately 把未同步事件全部落库', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.matchStartTime.value = Date.now()

    playPoints(device.state, 'left', 4)
    expect(server.stored).toHaveLength(0)
    expect(device.pipeline.hasPendingEvents()).toBe(true)

    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(true)
    expect(server.stored).toHaveLength(4)
    expect(device.state.lastSyncedEventSeq.value).toBe(4)
    expect(device.pipeline.hasPendingEvents()).toBe(false)
  })

  it('只读（锁被接管）时停止上云', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server, { readOnly: true })
    device.state.matchStartTime.value = Date.now()

    playPoints(device.state, 'left', 2)
    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(false)
    expect(server.stored).toHaveLength(0)
  })

  it('409 序号冲突：解析服务端最大序号并重排本地未同步事件后重试成功', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.matchStartTime.value = Date.now()

    playPoints(device.state, 'left', 2)
    server.rejectNextWith(new Error('事件序号与已有记录冲突，请刷新后重试（服务端最大序号 7）'))

    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(true)
    expect(server.stored.map((item) => item.eventSeq)).toEqual([8, 9])
    expect(parseConflictServerMaxEventSeq('服务端最大序号 7')).toBe(7)
    expect(parseConflictServerMaxEventSeq('无序号信息')).toBe(0)
  })

  it('init 基线事件不算实质进度，但可用于恢复规则基线', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.leftTeam.value = '甲'
    device.state.rightTeam.value = '乙'
    device.state.applyRules({ bestOf: 5, gamesToWin: 3, pointsToWin: 15, enableDeuce: true, capPoint: 21 })

    device.pipeline.appendAction({ action: 'init', runtime: device.state.buildEventRuntime() })
    await device.pipeline.flushPendingEventsImmediately()

    expect(server.stored).toHaveLength(1)
    expect(JSON.parse(server.stored[0].payloadJson).reason).toBe('init')
    expect(hasEventProgress(server.stored)).toBe(false)

    const recovered = buildRecoveredState(server.record())
    expect(recovered.snapshot.matchRules.bestOf).toBe(5)
    expect(recovered.snapshot.matchRules.pointsToWin).toBe(15)
    expect(recovered.snapshot.leftTeam).toBe('甲')
  })
})

describe('本地缓存与服务端记录的仲裁', () => {
  function recordWithEvents(maxSeq, extra = {}) {
    return {
      status: 0,
      events: Array.from({ length: maxSeq }, (_, index) => ({
        eventSeq: index + 1,
        eventType: 'score_snapshot',
        gameNo: 1,
        leftScore: index + 1,
        rightScore: 0,
        serveSide: 'left',
        payloadJson: JSON.stringify({ reason: 'score' }),
      })),
      ...extra,
    }
  }

  it('服务端无事件时本地优先（部署过渡保护）', () => {
    const cached = { currentGameNo: 1, lastSyncedEventSeq: 0 }
    expect(shouldUseLocalCache({ status: 0, events: [] }, cached, { sameSession: false })).toBe(true)
    expect(shouldUseLocalCache({ status: 0, events: [] }, cached, { sameSession: true })).toBe(true)
  })

  it('无本地缓存时必须用服务端', () => {
    expect(shouldUseLocalCache(recordWithEvents(3), null, { sameSession: true })).toBe(false)
  })

  it('sameSession 且本地水位领先 → 本地续裁', () => {
    const cached = { currentGameNo: 1, lastSyncedEventSeq: 3 }
    expect(shouldUseLocalCache(recordWithEvents(3), cached, { sameSession: true })).toBe(true)
  })

  it('非 sameSession（换设备/顶锁）→ 服务端重放', () => {
    const cached = { currentGameNo: 1, lastSyncedEventSeq: 3 }
    expect(shouldUseLocalCache(recordWithEvents(3), cached, { sameSession: false })).toBe(false)
  })

  it('本地落后于服务端 → 服务端重放', () => {
    const cached = { currentGameNo: 1, lastSyncedEventSeq: 2 }
    expect(shouldUseLocalCache(recordWithEvents(3), cached, { sameSession: true })).toBe(false)
  })

  it('已结算（status=2）→ 服务端权威，且恢复态标记 settled', () => {
    const record = recordWithEvents(3, { status: 2 })
    const cached = { currentGameNo: 1, lastSyncedEventSeq: 3 }
    expect(shouldUseLocalCache(record, cached, { sameSession: true })).toBe(false)
    expect(isMatchSettled(record)).toBe(true)
    expect(buildRecoveredState(record).settled).toBe(true)
  })
})

describe('撤销栈与事件形态纯函数', () => {
  function scoreEvent(seq, reason, prevScore, score, extra = {}) {
    return {
      seq,
      type: 'score_snapshot',
      gameNo: 1,
      leftScore: score,
      rightScore: 0,
      serveSide: 'left',
      payload: {
        reason,
        prevRuntime: { leftScore: prevScore, rightScore: 0, currentGameNo: 1, sidesSwapped: false, serveSide: 'left', gameScores: [] },
        runtime: { leftScore: score, rightScore: 0, currentGameNo: 1, sidesSwapped: false, serveSide: 'left', gameScores: [] },
        ...extra,
      },
      syncStatus: 'synced',
    }
  }

  it('undo 弹栈：撤销后该次加分不再计入撤销栈', () => {
    const events = [
      scoreEvent(1, 'score', 0, 1),
      scoreEvent(2, 'score', 1, 2),
      scoreEvent(3, 'undo', 2, 1, { revertToSeq: 1 }),
    ]
    expect(rebuildHistoryStack(events).map((item) => item.leftScore)).toEqual([0])
    expect(rebuildHistorySeqs(events)).toEqual([1])
  })

  it('局间换边不计入撤销栈，手动换边计入', () => {
    const events = [
      scoreEvent(1, 'score', 0, 1),
      { seq: 2, type: 'side_switch', gameNo: 2, leftScore: 0, rightScore: 0, serveSide: 'left', payload: { reason: 'between_games', prevRuntime: { leftScore: 1, rightScore: 0, sidesSwapped: false } }, syncStatus: 'synced' },
      { seq: 3, type: 'side_switch', gameNo: 2, leftScore: 0, rightScore: 0, serveSide: 'right', payload: { reason: 'manual', prevRuntime: { leftScore: 0, rightScore: 0, sidesSwapped: true } }, syncStatus: 'synced' },
    ]
    expect(rebuildHistoryStack(events).map((item) => item.sidesSwapped)).toEqual([false, true])
  })
})

describe('审查回归：撤销水位 / 终局冲刷 / 并发合并 / 缓存裁剪', () => {
  it('B2：撤销水位取「被撤动作前最近一条事件」，夹层的 between_games 不被标记撤销', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.leftTeam.value = '甲'
    device.state.rightTeam.value = '乙'
    device.state.matchStartTime.value = Date.now()
    device.state.applyRules({ bestOf: 3, gamesToWin: 2, pointsToWin: 2, enableDeuce: false, capPoint: 2 })

    // 第 1 局 2:0 → 局末换边（seq3）→ 第 2 局 1 分（seq4）→ 撤销（seq5）
    playPoints(device.state, 'left', 2)
    device.state.confirmGameEnd()
    playPoints(device.state, 'left', 1)
    expect(device.state.undo()).toBe(true)
    await device.pipeline.flushPendingEventsImmediately()

    const undoPayload = JSON.parse(server.stored[server.stored.length - 1].payloadJson)
    expect(undoPayload.reason).toBe('undo')
    // 保留水位 = seq3（局间换边真实发生过）；若误取「撤销栈新栈顶 2」，后端会把局边界一并抹掉
    expect(undoPayload.revertToSeq).toBe(3)

    // 撤销后状态 = 第 2 局 0:0（e4 被撤销，e3 保留）
    const recovered = buildRecoveredState(server.record())
    expect(recovered.snapshot.currentGameNo).toBe(2)
    expect(recovered.snapshot.leftScore).toBe(0)
    expect(recovered.snapshot.rightScore).toBe(0)
  })

  it('W1：制胜分（matchEnded）立即冲刷事件，不等 800ms 防抖', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.matchStartTime.value = Date.now()
    device.state.applyRules({ bestOf: 1, gamesToWin: 1, pointsToWin: 2, enableDeuce: false, capPoint: 2 })

    // 一局定胜负：首分触发决胜局技术换边弹窗（选不换边），次分为制胜分（matchEnded）
    device.state.addScore('left')
    expect(device.state.handleFinalGameSideSwitch(false)).toBe(true)
    device.state.addScore('left')
    expect(device.state.matchEnded.value).toBe(true)
    await new Promise((resolve) => setTimeout(resolve, 0))

    expect(server.stored).toHaveLength(3)
    expect(JSON.parse(server.stored[2].payloadJson).runtime.matchEnded).toBe(true)
  })

  it('W4：决胜局「不换边」也落事件，换设备恢复后不再重问、记分不被打断', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.leftTeam.value = '甲'
    device.state.rightTeam.value = '乙'
    device.state.matchStartTime.value = Date.now()
    device.state.applyRules({ bestOf: 1, gamesToWin: 1, pointsToWin: 2, enableDeuce: false, capPoint: 2 })

    device.state.addScore('left') // 1 分即触发技术换边弹窗
    expect(device.state.isFinalGameSideSwitchPromptActive.value).toBe(true)
    expect(device.state.handleFinalGameSideSwitch(false)).toBe(true)
    await device.pipeline.flushPendingEventsImmediately()

    const payload = JSON.parse(server.stored[server.stored.length - 1].payloadJson)
    expect(payload.reason).toBe('deciding_technical')
    expect(payload.runtime.finalGameSideSwitchHandled).toBe(true)
    expect(payload.runtime.sidesSwapped).toBe(false)

    // 换设备恢复：不再弹技术换边窗，且能正常记分
    setupFuzzerEnvironment()
    const deviceB = createDevice(server)
    deviceB.state.applyRecoveredState(buildRecoveredState(server.record()))
    expect(deviceB.state.isFinalGameSideSwitchPromptActive.value).toBe(false)
    expect(deviceB.state.addScore('right')).toBe(true)
  })

  it('W2：409 自愈窗口内 inFlight 保持占用，并发冲刷合并为同一条链', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.matchStartTime.value = Date.now()

    playPoints(device.state, 'left', 2)
    // 409 不带序号 → 走 GET /record 兜底；用门挡住响应制造自愈窗口
    server.rejectNextWith(new Error('事件序号与已有记录冲突，请刷新后重试'))
    server.gateRecord()

    const firstFlush = device.pipeline.flushPendingEventsImmediately()
    const secondFlush = device.pipeline.flushPendingEventsImmediately()
    // 让初次 PUT 失败、/record 兜底请求真实到达门上，再由服务端应答
    await new Promise((resolve) => setTimeout(resolve, 0))
    server.resolveRecord({
      status: 0,
      events: Array.from({ length: 7 }, (_, index) => ({
        eventSeq: index + 1,
        eventType: 'score_snapshot',
        gameNo: 1,
        leftScore: 1,
        rightScore: 0,
        serveSide: 'left',
        payloadJson: '{"reason":"score"}',
      })),
    })
    const results = await Promise.all([firstFlush, secondFlush])

    expect(results).toEqual([true, true])
    // 关键判别：被 409 拒掉的 [1,2] 批次绝不落库。若 inFlight 被提前置空，
    // 并发冲刷会用旧序号再发一批 [1,2]，云端出现 [1,2,8,9] 的双写污染。
    expect(server.batches).toEqual([[8, 9]])
    expect(server.stored.map((item) => item.eventSeq)).toEqual([8, 9])
  })

  it('W6：本地事件列表超上限时丢弃最旧的已同步事件，且水位不回退', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    device.state.matchStartTime.value = Date.now()

    for (let index = 0; index < 310; index += 1) {
      device.pipeline.appendAction({
        action: 'score',
        scoringSide: 'left',
        prevRuntime: null,
        runtime: { leftScore: index + 1, rightScore: 0, currentGameNo: 1, serveSide: 'left' },
      })
    }
    await device.pipeline.flushPendingEventsImmediately()

    expect(server.stored).toHaveLength(310)
    expect(device.state.matchEvents.value).toHaveLength(250)

    device.pipeline.appendAction({
      action: 'score',
      scoringSide: 'left',
      prevRuntime: null,
      runtime: { leftScore: 311, rightScore: 0, currentGameNo: 1, serveSide: 'left' },
    })
    await device.pipeline.flushPendingEventsImmediately()
    expect(server.stored[server.stored.length - 1].eventSeq).toBe(311)
  })
})

describe('审查回归：进入数据源决策（B1/B3）', () => {
  it('B1：record 拉取失败（null）绝不走服务端写路径', () => {
    expect(decideEntrySource({ record: null, cached: null, sameSession: true })).toBe('fresh')
    expect(decideEntrySource({ record: null, cached: { currentGameNo: 1, lastSyncedEventSeq: 0 }, sameSession: false })).toBe('local')
    expect(decideEntrySource({ record: null, cached: { currentGameNo: 2, lastSyncedEventSeq: 9 }, sameSession: true })).toBe('local')
  })

  it('B3：云端事件被清而本地曾同步过 → 判定为 restart，丢弃本地缓存', () => {
    const cached = { currentGameNo: 3, lastSyncedEventSeq: 42, matchEnded: true }
    expect(isRestartedMatch({ status: 0, events: [] }, cached)).toBe(true)
    expect(decideEntrySource({ record: { status: 0, events: [] }, cached, sameSession: true })).toBe('restarted')
    // 未同步过的老缓存不受影响（部署过渡保护）
    const legacy = { currentGameNo: 3, lastSyncedEventSeq: 0, matchEnded: true }
    expect(isRestartedMatch({ status: 0, events: [] }, legacy)).toBe(false)
    expect(decideEntrySource({ record: { status: 0, events: [] }, cached: legacy, sameSession: false })).toBe('local')
  })

  it('云端有事件 → 服务端重放优先于非同会话本地；已结算 → settled', () => {
    const record = {
      status: 0,
      events: [{ eventSeq: 1, eventType: 'score_snapshot', gameNo: 1, leftScore: 5, rightScore: 0, serveSide: 'left', payloadJson: '{"reason":"score"}' }],
    }
    expect(decideEntrySource({ record, cached: null, sameSession: false })).toBe('server')
    expect(decideEntrySource({ record, cached: { currentGameNo: 1, lastSyncedEventSeq: 1 }, sameSession: true })).toBe('local')
    expect(decideEntrySource({ record: { ...record, status: 2 }, cached: null, sameSession: false })).toBe('settled')
  })
})

describe('第二轮审查回归：B-NEW-1 / W-NEW-1 / W-NEW-2+T-1', () => {
  it('B-NEW-1：fresh 路由队名无条件生效；恢复路径恢复名优先', () => {
    // fresh：路由队名必须生效（队名 ref 初始「左队/右队」为真值，不能用 !leftTeam 判断）
    const freshNames = resolveEntryTeamNames(
      { leftTeam: '左队', rightTeam: '右队' },
      { leftName: encodeURIComponent('林丹'), rightName: encodeURIComponent('安赛龙') },
      { fresh: true },
    )
    expect(freshNames).toEqual({ leftTeam: '林丹', rightTeam: '安赛龙' })
    // fresh 且路由缺名：保留当前值
    expect(resolveEntryTeamNames({ leftTeam: '左队', rightTeam: '右队' }, {}, { fresh: true }))
      .toEqual({ leftTeam: '左队', rightTeam: '右队' })
    // 恢复路径：恢复名优先
    const recovered = resolveEntryTeamNames(
      { leftTeam: '甲', rightTeam: '乙' },
      { leftName: encodeURIComponent('路由名'), rightName: encodeURIComponent('路由名乙') },
      { fresh: false },
    )
    expect(recovered).toEqual({ leftTeam: '甲', rightTeam: '乙' })
    // 恢复名为空：路由名兜底
    expect(resolveEntryTeamNames({ leftTeam: '', rightTeam: '' }, { leftName: encodeURIComponent('甲'), rightName: '' }, { fresh: false }))
      .toEqual({ leftTeam: '甲', rightTeam: '右队' })
  })

  it('W-NEW-1：本地事件被裁剪后撤销，水位不塌陷为 0', () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createDevice(server)
    // 模拟 prune 后的本地态：只剩 seq=300 一条（服务端仍持有 1..299）
    device.pipeline.restore({
      events: [{
        seq: 300,
        type: 'score_snapshot',
        gameNo: 1,
        leftScore: 5,
        rightScore: 3,
        serveSide: 'left',
        payload: { reason: 'score', runtime: { leftScore: 5, rightScore: 3 }, prevRuntime: { leftScore: 4, rightScore: 3 } },
        syncStatus: 'synced',
      }],
      nextEventSeq: 301,
      lastSyncedEventSeq: 300,
    })
    device.pipeline.appendAction({
      action: 'undo',
      prevRuntime: null,
      runtime: { leftScore: 4, rightScore: 3, currentGameNo: 1, serveSide: 'left' },
    })
    const events = device.state.matchEvents.value
    const undoEvent = events[events.length - 1]
    // 修复前 retained 扫描本地为 0 → 后端把 seq>0 的全部局边界误标撤销
    expect(undoEvent.payload.revertToSeq).toBe(299)
  })

  it('W-NEW-2+T-1：撤销手动换边后换设备恢复，撤销栈深度与真机一致', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const deviceA = createDevice(server)
    deviceA.state.leftTeam.value = '甲'
    deviceA.state.rightTeam.value = '乙'
    deviceA.state.matchStartTime.value = Date.now()

    playPoints(deviceA.state, 'left', 1) // e1 score
    deviceA.state.switchSides()          // e2 manual side_switch
    deviceA.state.undo()                 // e3 undo（撤销换边 → 回 1:0 未换边）
    await deviceA.pipeline.flushPendingEventsImmediately()

    const deviceADepth = deviceA.state.historyStack.value.length
    expect(deviceADepth).toBe(1)

    // 换设备：真实后端会把 seq>revertToSeq 的 manual side_switch 从 record 剔除
    setupFuzzerEnvironment()
    const record = server.record()
    expect(record.events.some((item) => item.eventType === 'side_switch')).toBe(false)

    const deviceB = createDevice(server)
    const recovered = buildRecoveredState(record)
    deviceB.state.applyRecoveredState(recovered)

    // 修复前：rebuildHistoryStack 遇 undo 无条件弹栈 → 栈深 0（少一次撤销）
    expect(deviceB.state.historyStack.value).toHaveLength(deviceADepth)
    expect(deviceB.state.leftScore.value).toBe(1)
    expect(deviceB.state.rightScore.value).toBe(0)
    expect(deviceB.state.sidesSwapped.value).toBe(false)
    expect(deviceB.state.undo()).toBe(true)
    expect(deviceB.state.leftScore.value).toBe(0)
  })
})
