import { afterEach, describe, expect, it, vi } from 'vitest'
import { ref } from 'vue'
import { createMatchEventPipeline } from './match-events'

/**
 * match-events 管道级测试：钉住 B1（退出时「先冲刷后释锁」）的调用模式与时序坑。
 *
 * 页面 onUnload 模式（scoreboard/index.vue 等）：
 *   const flushed = pipeline.flushPendingEventsImmediately()
 *   void flushed.catch(() => {}).finally(() => { release(); pipeline.stop() })
 *   pipeline.stop()   // 同步这次只清「此刻已挂」的防抖/重试定时器
 *
 * 两个被钉住的事实：
 * 1. in-flight 冲刷结算失败会在「第一次 stop 之后」才 arm 重试定时器（现状语义），
 *    故 finally 里结算后的第二次 stop 不可省——删掉它测试就红；
 * 2. 带第二次 stop 的完整调用模式下，释锁（release）发生在冲刷结算之后，
 *    且释锁后不会出现重试请求连环打（每次重试必 403 的无界循环）。
 *
 * request 全注入、events 为普通 Vue ref，不依赖 uni API 与真实锁服务。
 */

function createHarness() {
  const events = ref([])
  const nextEventSeq = ref(2)
  const lastSyncedEventSeq = ref(0)
  const order = []
  let putCalls = 0
  const pipeline = createMatchEventPipeline({
    events,
    nextEventSeq,
    lastSyncedEventSeq,
    getMatchId: () => 'm-b1',
    isReadOnly: () => false,
    // PUT /events 永远失败（模拟网络故障）：冲刷结算必走 scheduleRetry 分支
    request: async (url) => {
      if (String(url).endsWith('/events')) {
        putCalls += 1
        order.push('put')
        throw new Error('network down')
      }
      throw new Error('unexpected probe: ' + url)
    },
    persist: () => {},
  })
  // 直接注入一条 pending 事件：只考察冲刷/释锁时序，不经过动作映射
  events.value = [{
    seq: 1, type: 'score_snapshot', gameNo: 1, leftScore: 1, rightScore: 0,
    serveSide: 'left', payload: {}, syncStatus: 'pending',
  }]
  return { pipeline, order, putCount: () => putCalls }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('B1：退出时先冲刷后释锁的调用模式', () => {
  it('页面 onUnload 模式：释锁在冲刷结算之后，且释锁后无重试请求连环打', async () => {
    vi.useFakeTimers()
    const { pipeline, order, putCount } = createHarness()

    const flushed = pipeline.flushPendingEventsImmediately()
    void flushed
      .catch(() => {})
      .finally(() => {
        order.push('release')
        // 释锁后 in-flight 冲刷若失败，重试定时器会在结算时才 arm，补一次 stop 掐断
        pipeline.stop()
      })
    // 同步 stop（发起冲刷之后）：只清此刻已挂的防抖/重试定时器
    pipeline.stop()

    await flushed.catch(() => {})
    await vi.advanceTimersByTimeAsync(60000)

    // PUT 仅 1 次：释锁后的重试风暴被 finally 里的 stop 掐死
    expect(putCount()).toBe(1)
    // 顺序佐证：先冲刷请求，冲刷结算后才 release（B1 的时序前提）
    expect(order).toEqual(['put', 'release'])
  })

  it('钉住坑本身：第一次 stop 后 in-flight 冲刷失败仍 arm 重试定时器，第二次 stop 才掐掉', async () => {
    vi.useFakeTimers()
    const { pipeline, putCount } = createHarness()

    const flushed = pipeline.flushPendingEventsImmediately()
    // 故意只做第一次 stop（模拟删掉 finally 二次 stop 的回归形态）
    pipeline.stop()
    await flushed.catch(() => {})
    expect(putCount()).toBe(1)

    // 冲刷结算失败已 arm 1s 重试：第一次 stop 管不到「结算之后才挂上」的定时器
    await vi.advanceTimersByTimeAsync(1000)
    await vi.advanceTimersByTimeAsync(0)
    expect(putCount()).toBe(2)

    // 第二次 stop（页面 finally 中那次）掐掉下一次重试
    pipeline.stop()
    await vi.advanceTimersByTimeAsync(60000)
    expect(putCount()).toBe(2)
  })
})
