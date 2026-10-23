import { afterEach, describe, expect, it } from 'vitest'
import { computed, ref } from 'vue'
import { setupFuzzerEnvironment } from '../scoreboard/fuzzer-env'
import { createMatchEventPipeline, parseConflictServerMaxEventSeq } from '../scoreboard/match-events'
import { isRestartedMatch, rebuildHistoryStack } from '../scoreboard/badminton-recovery'
import {
  RELAY_ACTION_EVENT_MAP,
  RELAY_HISTORY_ACTIONS,
  buildRelayRecoveredState,
  decideRelayEntrySource,
  shouldUseRelayLocalCache,
} from './relay-recovery'

/**
 * 接力赛「事件上云 + 服务端恢复」测试
 *
 * 驱动真实事件管道（match-events，注入 relay 版动作映射）+ 与 team-relay.vue
 * 动作函数严格同构的无头 harness，验证羽毛球同款的「锁交接 + 分数交接」在接力赛侧成立：
 * - 换设备 / 清缓存后从服务端事件重放恢复比分、各段记录、换边镜像、撤销栈；
 * - 事件全部以 score_snapshot 落库（gameNo=段号，serveSide=最近得分方），
 *   换边事件不被后端 undo 补偿剔除（接力赛换边不进撤销栈）；
 * - 撤销只追加 undo 快照，恢复后撤销栈可继续使用；
 * - 409 序号冲突按服务端最大序号重排后重试；只读（锁被接管）停发事件；
 * - 制胜分事件立即冲刷，结算 finish 前服务端已持有全部事件。
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
 * 假服务端贴近真实契约（与后端 MatchServiceImpl 对齐）：
 * - 同 seq 同 payload → 幂等跳过；同 seq 异 payload → 409（message 带服务端最大序号）；
 * - record() 对齐后端 buildEventRecords：undo 补偿剔除「seq>revertToSeq 的
 *   side_switch 类型事件」（score_snapshot 永不剔除——接力赛换边正是 score_snapshot）。
 */
function createFakeServer() {
  const stored = []
  const batches = []
  const finishCalls = []
  let rejectOnce = null
  let recordGate = null
  return {
    stored,
    batches,
    finishCalls,
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
          // 对齐后端 MatchServiceImpl 校验（审查 W4）：非法字段必须被负向暴露，而不是被假服务端宽松吞掉
          if (!(Number(item.gameNo) > 0)) throw new Error('gameNo 必须大于 0')
          if (item.serveSide !== 'left' && item.serveSide !== 'right') throw new Error('serveSide 仅接受 left/right')
          const batchSeqs = options.data.events.map((candidate) => candidate.eventSeq)
          if (batchSeqs.some((seq, index) => batchSeqs.indexOf(seq) !== index)) {
            throw new Error('同一请求内 eventSeq 重复')
          }
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
      if (String(url).endsWith('/finish')) {
        finishCalls.push({ url: String(url), body: options.data })
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

const STORAGE_KEY = 'team_relay_scoreboard_state_match-1'

/**
 * 与 team-relay.vue 动作函数严格同构的无头 harness（3 段、基准分 10、目标分 30）。
 * 页面改动作逻辑时必须同步这里——本文件是页面动作/恢复链路的行为规格。
 */
function createRelayHarness(server, options = {}) {
  const detail = ref({
    matchStatus: 0,
    relayBaseScore: 10,
    relayTargetScore: 30,
    items: [{}, {}, {}],
  })
  const leftScore = ref(0)
  const rightScore = ref(0)
  const historyStack = ref([])
  const segmentScores = ref([])
  const segmentSwitchPending = ref(false)
  const sidesSwapped = ref(false)
  const lastScoredSide = ref('')
  const isReadOnly = ref(options.readOnly === true)
  const matchEvents = ref([])
  const nextEventSeq = ref(1)
  const lastSyncedEventSeq = ref(0)

  const baseScore = computed(() => Number(detail.value.relayBaseScore || 10))
  const targetScore = computed(() => Number(detail.value.relayTargetScore || baseScore.value * 3))
  const matchEnded = computed(
    () =>
      Number(detail.value.matchStatus || 0) === 2 ||
      (targetScore.value > 0 &&
        (leftScore.value >= targetScore.value || rightScore.value >= targetScore.value)),
  )
  const currentSegmentNo = computed(() => Math.min(segmentScores.value.length, 2) + 1)
  const currentSegmentTarget = computed(() =>
    Math.min(baseScore.value * currentSegmentNo.value, targetScore.value),
  )

  function buildScoreState() {
    return {
      leftScore: leftScore.value,
      rightScore: rightScore.value,
      segmentScores: segmentScores.value.map((item) => ({ ...item })),
      segmentSwitchPending: segmentSwitchPending.value,
      sidesSwapped: sidesSwapped.value,
      lastScoredSide: lastScoredSide.value,
    }
  }

  function buildEventRuntime(pointSegmentNo) {
    // 与页面同构：段截止那一分先记段比分，currentSegmentNo 已前移，须用捕获的段号
    const segmentNo = Number(pointSegmentNo) > 0 ? Number(pointSegmentNo) : currentSegmentNo.value
    return {
      ...buildScoreState(),
      currentSegmentNo: segmentNo,
      currentGameNo: segmentNo,
      serveSide: lastScoredSide.value === 'right' ? 'right' : 'left',
      matchEnded: matchEnded.value,
    }
  }

  function applyScoreState(snapshot) {
    leftScore.value = Number(snapshot?.leftScore || 0)
    rightScore.value = Number(snapshot?.rightScore || 0)
    segmentScores.value = Array.isArray(snapshot?.segmentScores)
      ? snapshot.segmentScores.map((item) => ({ ...item }))
      : []
    segmentSwitchPending.value = !!snapshot?.segmentSwitchPending
    sidesSwapped.value = !!snapshot?.sidesSwapped
    lastScoredSide.value = snapshot?.lastScoredSide || ''
  }

  function pushHistory() {
    historyStack.value.push(buildScoreState())
  }

  function appendCurrentSegmentScore() {
    const segmentNo = currentSegmentNo.value
    if (segmentScores.value.some((item) => item.segmentNo === segmentNo)) return
    segmentScores.value.push({
      segmentNo,
      leftScore: leftScore.value,
      rightScore: rightScore.value,
    })
  }

  // W2：服务端事件被清回调的触发计数（测试断言用）
  const cleared = { count: 0 }

  const pipeline = createMatchEventPipeline({
    events: matchEvents,
    nextEventSeq,
    lastSyncedEventSeq,
    getMatchId: () => 'match-1',
    isReadOnly: () => isReadOnly.value,
    request: (url, requestOptions) => server.request(url, requestOptions),
    requestOptions: (extra) => extra,
    persist: () => {
      // 与页面 buildSnapshot 同构：缓存里带事件与水位（sameSession 续裁数据源）
      uni.setStorageSync(STORAGE_KEY, {
        ...buildScoreState(),
        currentSegmentNo: currentSegmentNo.value,
        historyStack: historyStack.value,
        synced: false,
        matchEvents: matchEvents.value,
        nextEventSeq: nextEventSeq.value,
        lastSyncedEventSeq: lastSyncedEventSeq.value,
      })
    },
    actionEventMap: RELAY_ACTION_EVENT_MAP,
    historyActions: RELAY_HISTORY_ACTIONS,
    // W2：镜像页面回调——服务端事件被清 → 丢弃本地旧一局（清缓存 + 状态归零 + reset）
    onServerEventsCleared: () => {
      cleared.count += 1
      uni.removeStorageSync(STORAGE_KEY)
      applyScoreState({})
      historyStack.value = []
      pipeline.reset()
    },
  })
  pipelines.push(pipeline)

  return {
    detail,
    leftScore,
    rightScore,
    historyStack,
    segmentScores,
    segmentSwitchPending,
    sidesSwapped,
    lastScoredSide,
    isReadOnly,
    matchEvents,
    nextEventSeq,
    lastSyncedEventSeq,
    baseScore,
    targetScore,
    matchEnded,
    currentSegmentNo,
    pipeline,
    cleared,
    buildScoreState,
    buildEventRuntime,
    applyScoreState,
    addScore(side) {
      if (isReadOnly.value) return
      const pointSegmentNo = currentSegmentNo.value
      const prevRuntime = buildEventRuntime()
      pushHistory()
      if (side === 'left') leftScore.value += 1
      else rightScore.value += 1
      lastScoredSide.value = side

      if (leftScore.value >= targetScore.value || rightScore.value >= targetScore.value) {
        appendCurrentSegmentScore()
        pipeline.appendAction({ action: 'score', scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime })
        return
      }
      if (
        leftScore.value >= currentSegmentTarget.value ||
        rightScore.value >= currentSegmentTarget.value
      ) {
        segmentSwitchPending.value = true
        appendCurrentSegmentScore()
        pipeline.appendAction({ action: 'score', scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime })
        return
      }
      pipeline.appendAction({ action: 'score', scoringSide: side, runtime: buildEventRuntime(pointSegmentNo), prevRuntime })
    },
    advanceSegment(shouldSwap = true) {
      if (isReadOnly.value) return
      if (!segmentSwitchPending.value || matchEnded.value) return
      const prevRuntime = buildEventRuntime()
      segmentSwitchPending.value = false
      if (shouldSwap) sidesSwapped.value = !sidesSwapped.value
      pipeline.appendAction({
        action: shouldSwap ? 'side_switch' : 'segment_continue',
        runtime: buildEventRuntime(),
        prevRuntime,
      })
    },
    undo() {
      if (isReadOnly.value) return
      const last = historyStack.value.pop()
      if (!last) return false
      applyScoreState(last)
      pipeline.appendAction({ action: 'undo', runtime: buildEventRuntime() })
      return true
    },
    // 与页面 syncResult 同构（审查 W4：场景 f 此前零覆盖）：冲刷失败中止结算
    async syncResult() {
      const winnerSide = leftScore.value > rightScore.value
        ? 'left'
        : rightScore.value > leftScore.value ? 'right' : ''
      if (!winnerSide) return false
      const eventsFlushed = await pipeline.flushPendingEventsImmediately()
      if (!eventsFlushed) return false
      await server.request('/api/v1/matches/match-1/finish', {
        method: 'PUT',
        data: {
          winnerSide,
          leftScore: leftScore.value,
          rightScore: rightScore.value,
          relaySegmentScores: segmentScores.value.map((item) => ({ ...item })),
        },
      })
      return true
    },
    applyRecovered(recovered) {
      applyScoreState(recovered.snapshot)
      historyStack.value = Array.isArray(recovered.historyStack)
        ? recovered.historyStack.map((item) => ({ ...item }))
        : []
      pipeline.restore({
        events: recovered.events,
        nextEventSeq: recovered.nextEventSeq,
        lastSyncedEventSeq: recovered.lastSyncedEventSeq,
      })
    },
    restoreFromCache() {
      const cache = uni.getStorageSync(STORAGE_KEY)
      if (!cache || typeof cache !== 'object') return false
      applyScoreState(cache)
      historyStack.value = Array.isArray(cache.historyStack) ? cache.historyStack : []
      pipeline.restore({
        events: Array.isArray(cache.matchEvents) ? cache.matchEvents : [],
        nextEventSeq: Number(cache.nextEventSeq || 1),
        lastSyncedEventSeq: Number(cache.lastSyncedEventSeq || 0),
      })
      return true
    },
  }
}

describe('接力赛事件上云与恢复', () => {
  it('加分批量上云：事件以 score_snapshot 落库，gameNo=段号、serveSide=最近得分方、runtime 全量快照', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('left')
    device.addScore('right')
    await device.pipeline.flushPendingEventsImmediately()

    expect(server.stored).toHaveLength(3)
    const last = server.stored[2]
    expect(last.eventType).toBe('score_snapshot')
    expect(last.eventSeq).toBe(3)
    expect(last.gameNo).toBe(1)
    expect(last.leftScore).toBe(2)
    expect(last.rightScore).toBe(1)
    expect(last.serveSide).toBe('right')
    const payload = JSON.parse(last.payloadJson)
    expect(payload.reason).toBe('score')
    expect(payload.scoringSide).toBe('right')
    // runtime 全量快照：段比分、换边、最近得分方随事件上云
    expect(payload.runtime).toMatchObject({
      leftScore: 2,
      rightScore: 1,
      currentSegmentNo: 1,
      segmentSwitchPending: false,
      sidesSwapped: false,
      lastScoredSide: 'right',
      serveSide: 'right',
    })
    expect(payload.runtime.segmentScores).toEqual([])
    // prevRuntime 是加分前快照（1:1）
    expect(payload.prevRuntime).toMatchObject({ leftScore: 2, rightScore: 0 })
    expect(env.storage.getStorageSync(STORAGE_KEY)).toBeTruthy()
  })

  it('段结束弹窗与换段换边：side_switch 事件落库，恢复后段比分/换边镜像/当前段号全部一致', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    // 第 1 段：左队拿到段目标分 10（比分 10:2）
    for (let i = 0; i < 8; i += 1) device.addScore('left')
    device.addScore('right')
    device.addScore('right')
    device.addScore('left')
    device.addScore('left')
    expect(device.segmentSwitchPending.value).toBe(true)
    expect(device.segmentScores.value).toEqual([{ segmentNo: 1, leftScore: 10, rightScore: 2 }])

    device.advanceSegment(true)
    expect(device.segmentSwitchPending.value).toBe(false)
    expect(device.sidesSwapped.value).toBe(true)

    // 第 2 段：原始右（屏幕左）拿 1 分
    device.addScore('right')
    await device.pipeline.flushPendingEventsImmediately()

    // 换段动作以 side_switch 事件的 score_snapshot 形态落库，且不被 undo 剔除语义波及
    const switchEvent = server.stored.find((item) => item.eventSeq === 13)
    expect(switchEvent).toBeTruthy()
    expect(switchEvent.eventType).toBe('score_snapshot')
    expect(JSON.parse(switchEvent.payloadJson).reason).toBe('side_switch')
    // 段截止那一分（先记段比分再发事件）仍归属本段，gameNo 不漂到下一段
    const segmentEndEvent = server.stored.find((item) => item.eventSeq === 12)
    expect(segmentEndEvent.gameNo).toBe(1)
    expect(JSON.parse(segmentEndEvent.payloadJson).runtime.currentSegmentNo).toBe(1)
    const last = server.stored[server.stored.length - 1]
    expect(last.gameNo).toBe(2)
    expect(last.leftScore).toBe(10)
    expect(last.rightScore).toBe(3)

    // 换设备：事件重放恢复（换边镜像、段比分、当前段号）
    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const recovered = buildRelayRecoveredState(record)
    expect(recovered).not.toBeNull()

    const deviceB = createRelayHarness(server)
    deviceB.applyRecovered(recovered)
    expect(deviceB.leftScore.value).toBe(10)
    expect(deviceB.rightScore.value).toBe(3)
    expect(deviceB.sidesSwapped.value).toBe(true)
    expect(deviceB.segmentSwitchPending.value).toBe(false)
    expect(deviceB.segmentScores.value).toEqual([{ segmentNo: 1, leftScore: 10, rightScore: 2 }])
    expect(deviceB.currentSegmentNo.value).toBe(2)
    expect(deviceB.lastScoredSide.value).toBe('right')
  })

  it('段推进不换边：segment_continue 事件恢复后 pending 解除且镜像不变', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    for (let i = 0; i < 10; i += 1) device.addScore('left')
    device.advanceSegment(false)
    await device.pipeline.flushPendingEventsImmediately()

    const continueEvent = server.stored[server.stored.length - 1]
    expect(JSON.parse(continueEvent.payloadJson).reason).toBe('segment_continue')
    expect(device.segmentSwitchPending.value).toBe(false)
    expect(device.sidesSwapped.value).toBe(false)

    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const deviceB = createRelayHarness(server)
    deviceB.applyRecovered(buildRelayRecoveredState(record))
    expect(deviceB.segmentSwitchPending.value).toBe(false)
    expect(deviceB.sidesSwapped.value).toBe(false)
    expect(deviceB.currentSegmentNo.value).toBe(2)
  })

  it('制胜分事件 matchEnded=true 触发立即冲刷，不等防抖', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    // 第 1 段 10:0、第 2 段 20:0、第 3 段打到 30 分制胜
    for (let i = 0; i < 10; i += 1) device.addScore('left')
    device.advanceSegment(false)
    for (let i = 0; i < 10; i += 1) device.addScore('left')
    device.advanceSegment(false)
    for (let i = 0; i < 9; i += 1) device.addScore('left')
    // 10 次加分 + 2 次段推进 + 9 次加分 = 31 条，全部停留在本地（防抖窗口内无 flush）
    expect(server.stored).toHaveLength(0)
    expect(device.pipeline.hasPendingEvents()).toBe(true)

    device.addScore('left')
    expect(device.matchEnded.value).toBe(true)
    // 制胜分（matchEnded=true）触发整批立即上云，不依赖显式 flush
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(server.stored).toHaveLength(32)
    const winning = server.stored[server.stored.length - 1]
    expect(JSON.parse(winning.payloadJson).runtime.matchEnded).toBe(true)
    expect(device.pipeline.hasPendingEvents()).toBe(false)
  })

  it('撤销后恢复：undo 快照带 revertToSeq，换设备撤销栈可继续使用且水位续接', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('left')
    device.addScore('left')
    expect(device.undo()).toBe(true)
    await device.pipeline.flushPendingEventsImmediately()

    const undoEvent = server.stored[server.stored.length - 1]
    expect(undoEvent.eventType).toBe('score_snapshot')
    const payload = JSON.parse(undoEvent.payloadJson)
    expect(payload.reason).toBe('undo')
    // 撤销到「第 2 分之后」：保留水位 = 第 2 条事件
    expect(payload.revertToSeq).toBe(2)

    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')
    const recovered = buildRelayRecoveredState(record)
    expect(recovered.snapshot).toMatchObject({ leftScore: 2, rightScore: 0 })
    // 撤销栈重建：3 次加分撤销 1 次 → 栈内仍有 2 条（可继续撤到 1:0、0:0）
    expect(recovered.historyStack).toHaveLength(2)

    const deviceB = createRelayHarness(server)
    deviceB.applyRecovered(recovered)
    expect(deviceB.leftScore.value).toBe(2)
    expect(deviceB.historyStack.value).toHaveLength(2)

    // 换设备继续撤销 + 新加分：事件序号从服务端最大序号之后续接
    expect(deviceB.nextEventSeq.value).toBe(5)
    expect(deviceB.undo()).toBe(true)
    expect(deviceB.leftScore.value).toBe(1)
    deviceB.addScore('right')
    await deviceB.pipeline.flushPendingEventsImmediately()
    const seqs = server.stored.map((item) => item.eventSeq)
    expect(seqs).toEqual([1, 2, 3, 4, 5, 6])
  })

  it('换边不进撤销栈：undo 只撤加分不吞换边，恢复侧栈重建一致', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    // 初始换边走 confirmInitialSideSwap：页面在 0:0 时直接换边（0:0 换边也上云）
    {
      const prevRuntime = device.buildScoreState()
      device.sidesSwapped.value = !device.sidesSwapped.value
      device.pipeline.appendAction({
        action: 'side_switch',
        runtime: device.buildEventRuntime(),
        prevRuntime,
      })
    }
    device.addScore('left')
    for (let i = 0; i < 9; i += 1) device.addScore('left')
    device.advanceSegment(true)
    device.addScore('right')
    expect(device.undo()).toBe(true)

    // undo 只撤掉最后的加分（原始右那 1 分）：10:0；镜像已被段间换边翻回 false
    expect(device.leftScore.value).toBe(10)
    expect(device.rightScore.value).toBe(0)
    expect(device.sidesSwapped.value).toBe(false)

    await device.pipeline.flushPendingEventsImmediately()
    env.storage.clearStorageSync()
    const record = await server.request('/api/v1/matches/match-1/record')

    // 服务端记录：换边事件（score_snapshot/side_switch）不被 undo 补偿剔除
    const storedReasons = server.stored.map((item) => JSON.parse(item.payloadJson).reason)
    expect(storedReasons).toContain('side_switch')
    const recordReasons = record.events.map((item) => JSON.parse(item.payloadJson).reason)
    expect(recordReasons).toContain('side_switch')

    const deviceB = createRelayHarness(server)
    deviceB.applyRecovered(buildRelayRecoveredState(record))
    expect(deviceB.leftScore.value).toBe(10)
    expect(deviceB.rightScore.value).toBe(0)
    expect(deviceB.sidesSwapped.value).toBe(false)
    // 撤销栈只含加分动作：11 次加分 - 1 次撤销 = 10 条（两次换边不占栈）
    expect(deviceB.historyStack.value).toHaveLength(10)
  })

  it('W2：record 失败会话中冲刷前探测——服务端已被 restart 清空 → 丢弃本地且不上云', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('right')
    // 模拟离线进入：record 未拉到 → 服务端状态未确认
    device.pipeline.setServerStateConfirmed(false)

    // 管理端此刻 restart（服务端事件表为空，stored 为空即此状态）→ 网络恢复后冲刷
    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(false)
    expect(server.stored).toHaveLength(0)
    // 回调触发：本地旧一局被整体丢弃（状态归零 + 管道清空 + 缓存清除）
    expect(device.cleared.count).toBe(1)
    expect(device.leftScore.value).toBe(0)
    expect(device.pipeline.hasPendingEvents()).toBe(false)
    expect(env.storage.getStorageSync(STORAGE_KEY)).toBe('')
  })

  it('W2：服务端不可达 → 本次不冲刷保持 pending；可达且有事件 → 正常续传（409 自愈兜底）', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.pipeline.setServerStateConfirmed(false)

    // 服务端仍不可达：中止冲刷，不清空、保持 pending
    server.rejectNextWith(new Error('network down'))
    const first = await device.pipeline.flushPendingEventsImmediately()
    expect(first).toBe(false)
    expect(server.stored).toHaveLength(0)
    expect(device.cleared.count).toBe(0)
    expect(device.pipeline.hasPendingEvents()).toBe(true)

    // 服务端可达且有事件（他端已记 1 分）→ 放行冲刷；seq 撞号由 409 自愈链重排
    server.stored.push({
      eventSeq: 1,
      eventType: 'score_snapshot',
      gameNo: 1,
      leftScore: 1,
      rightScore: 0,
      serveSide: 'left',
      payloadJson: JSON.stringify({ reason: 'score' }),
    })
    const second = await device.pipeline.flushPendingEventsImmediately()
    expect(second).toBe(true)
    expect(server.stored.length).toBeGreaterThan(1)
    expect(device.cleared.count).toBe(0)
    expect(device.pipeline.hasPendingEvents()).toBe(false)
  })

  it('W2：服务端状态已确认（正常进入）时不探测——同会话离线续裁行为保持不变', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('right')
    // 默认 confirmed=true（进入时 record 已拉到）：即便服务端事件表为空也照常上云
    // （sameSession 部署过渡保护场景：老比赛从未同步过，服务端本来就没有事件）
    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(true)
    expect(server.stored).toHaveLength(2)
    expect(device.cleared.count).toBe(0)
  })

  it('只读（锁被接管）时停止上云', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createRelayHarness(server)

    // 页面层先加分（pending 事件已产生），随后锁被接管进入只读
    device.addScore('left')
    device.addScore('right')
    device.isReadOnly.value = true
    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(false)
    expect(server.stored).toHaveLength(0)
  })

  it('结算前冲刷失败 → 中止结算不发 finish；冲刷成功 → finish 恰好一次（场景 f）', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createRelayHarness(server)

    // 打到制胜分（立即冲刷为 fire-and-forget），先让首次冲刷吃掉一次拒绝
    server.rejectNextWith(new Error('network down'))
    for (let i = 0; i < 30; i += 1) device.addScore('left')
    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(device.pipeline.hasPendingEvents()).toBe(true)
    expect(server.stored).toHaveLength(0)

    // 再挡一次：syncResult 内部的强制冲刷失败 → 必须中止，绝不发出 finish
    server.rejectNextWith(new Error('network down again'))
    await expect(device.syncResult()).resolves.toBe(false)
    expect(server.finishCalls).toHaveLength(0)
    expect(device.pipeline.hasPendingEvents()).toBe(true)

    // 网络恢复：冲刷成功后 finish 恰好发出一次，且 body 带原始坐标与段比分
    await expect(device.syncResult()).resolves.toBe(true)
    expect(server.finishCalls).toHaveLength(1)
    expect(server.finishCalls[0].body.winnerSide).toBe('left')
    expect(server.finishCalls[0].body.leftScore).toBe(30)
    expect(server.finishCalls[0].body.relaySegmentScores).toEqual([
      { segmentNo: 1, leftScore: 10, rightScore: 0 },
      { segmentNo: 2, leftScore: 20, rightScore: 0 },
      { segmentNo: 3, leftScore: 30, rightScore: 0 },
    ])
  })

  it('跨模块契约钉扎（审查 W3）：score_snapshot/reason=side_switch 与 segment_continue 不计入羽毛球撤销栈判定', () => {
    const runtime = { leftScore: 0, rightScore: 0, sidesSwapped: false }
    // 输入形状 = normalizeRecordEvents 归一化后的事件（buildRelayRecoveredState 的真实调用形态）
    const events = [
      {
        seq: 1,
        type: 'score_snapshot',
        gameNo: 1,
        leftScore: 0,
        rightScore: 0,
        serveSide: 'left',
        payload: { reason: 'side_switch', runtime, prevRuntime: runtime },
      },
      {
        seq: 2,
        type: 'score_snapshot',
        gameNo: 1,
        leftScore: 1,
        rightScore: 0,
        serveSide: 'left',
        payload: { reason: 'score', runtime, prevRuntime: { leftScore: 0, rightScore: 0 } },
      },
      {
        seq: 3,
        type: 'score_snapshot',
        gameNo: 2,
        leftScore: 1,
        rightScore: 0,
        serveSide: 'left',
        payload: { reason: 'segment_continue', runtime, prevRuntime: runtime },
      },
    ]
    // 若日后有人把 'side_switch'/'segment_continue' 加进 badminton-recovery 的
    // HISTORY_REASONS，relay 换边/段推进会被误入栈 → 本断言红，防跨模块契约裸奔
    expect(rebuildHistoryStack(events)).toHaveLength(1)
  })

  it('409 序号冲突：按服务端最大序号重排本地未同步事件后重试成功', async () => {
    const server = createFakeServer()
    setupFuzzerEnvironment()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('right')
    server.rejectNextWith(new Error('事件序号与已有记录冲突，请刷新后重试（服务端最大序号 7）'))

    const flushed = await device.pipeline.flushPendingEventsImmediately()
    expect(flushed).toBe(true)
    expect(server.stored.map((item) => item.eventSeq)).toEqual([8, 9])
    expect(parseConflictServerMaxEventSeq('服务端最大序号 7')).toBe(7)
  })

  it('sameSession 本地续裁：缓存含未同步事件时恢复并续传上云', async () => {
    const env = setupFuzzerEnvironment()
    const server = createFakeServer()
    const device = createRelayHarness(server)

    device.addScore('left')
    device.addScore('left')
    // 不 flush，模拟防抖窗口内退出（persist 已随 appendAction 落缓存）
    expect(device.pipeline.hasPendingEvents()).toBe(true)
    expect(env.storage.getStorageSync(STORAGE_KEY).matchEvents).toHaveLength(2)

    const cached = env.storage.getStorageSync(STORAGE_KEY)
    const record = server.record()
    // 同设备重进：本地水位（0）未落后（服务端无事件）且段号齐平 → 本地优先
    expect(shouldUseRelayLocalCache(record, cached, { sameSession: true })).toBe(true)
    expect(decideRelayEntrySource({ record, cached, sameSession: true })).toBe('local')

    const deviceB = createRelayHarness(server)
    expect(deviceB.restoreFromCache()).toBe(true)
    expect(deviceB.leftScore.value).toBe(2)
    expect(deviceB.pipeline.hasPendingEvents()).toBe(true)

    await deviceB.pipeline.flushPendingEventsImmediately()
    expect(server.stored.map((item) => item.eventSeq)).toEqual([1, 2])
  })
})

describe('接力赛进入仲裁（decideRelayEntrySource）', () => {
  function relayRecord(maxSeq, extra = {}) {
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

  it('服务端无事件且本地从未同步过 → 本地优先（改造前老缓存过渡保护）', () => {
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 0 }
    expect(shouldUseRelayLocalCache({ status: 0, events: [] }, cached, { sameSession: false })).toBe(true)
    expect(decideRelayEntrySource({ record: { status: 0, events: [] }, cached, sameSession: false })).toBe('local')
  })

  it('服务端无事件但本地曾同步过 → restarted（防旧比分复活）', () => {
    const cached = { currentSegmentNo: 2, lastSyncedEventSeq: 5 }
    expect(isRestartedMatch({ status: 0, events: [] }, cached)).toBe(true)
    expect(decideRelayEntrySource({ record: { status: 0, events: [] }, cached, sameSession: true })).toBe('restarted')
  })

  it('sameSession 且本地水位/段号齐平 → 本地续裁', () => {
    const record = relayRecord(3)
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 3 }
    expect(shouldUseRelayLocalCache(record, cached, { sameSession: true })).toBe(true)
    expect(decideRelayEntrySource({ record, cached, sameSession: true })).toBe('local')
  })

  it('非 sameSession（换设备/顶锁）→ 服务端重放', () => {
    const record = relayRecord(3)
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 3 }
    expect(shouldUseRelayLocalCache(record, cached, { sameSession: false })).toBe(false)
    expect(decideRelayEntrySource({ record, cached, sameSession: false })).toBe('server')
  })

  it('本地落后于服务端 → 服务端重放', () => {
    const record = relayRecord(5)
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 3 }
    expect(decideRelayEntrySource({ record, cached, sameSession: true })).toBe('server')
  })

  it('已结算（status=2）→ settled 权威', () => {
    const record = relayRecord(3, { status: 2 })
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 3 }
    expect(decideRelayEntrySource({ record, cached, sameSession: true })).toBe('settled')
  })

  it('record=null（记录接口失败）→ 不产生写动作，最多本地/全新', () => {
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 3 }
    expect(decideRelayEntrySource({ record: null, cached, sameSession: true })).toBe('local')
    expect(decideRelayEntrySource({ record: null, cached: null, sameSession: true })).toBe('fresh')
  })

  it('服务端段号领先于本地缓存段号 → 服务端重放', () => {
    const record = relayRecord(12, {
      events: relayRecord(12).events.map((item) => ({ ...item, gameNo: 2 })),
    })
    const cached = { currentSegmentNo: 1, lastSyncedEventSeq: 12 }
    expect(shouldUseRelayLocalCache(record, cached, { sameSession: true })).toBe(false)
    expect(decideRelayEntrySource({ record, cached, sameSession: true })).toBe('server')
  })
})
