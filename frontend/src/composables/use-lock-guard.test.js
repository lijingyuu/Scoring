import { afterEach, describe, expect, it, vi } from 'vitest'
import { useLockGuard } from './use-lock-guard'

/**
 * useLockGuard 无头单测：探测 → 顶替确认 → 抢锁 → 恢复 的全部分支。
 * request/confirm/acquire 全部注入替身，不依赖网络与真实锁服务。
 */

function createHarness(overrides = {}) {
  const calls = { probe: [], probeOptions: [], confirm: 0, acquire: 0, retrySuccess: 0, backs: 0 }
  // 探测结果队列：对象 → 正常返回；Error 实例 → 抛出（模拟 403 等非 2xx 探测失败）
  const statusQueue = []
  const confirmResults = []
  const acquireResults = []
  // onRetrySuccess 可编程行为队列：Error → 抛错；function → 执行（可断言执行时序）；空 → 默认成功
  const retrySuccessQueue = []

  const guard = useLockGuard({
    getMatchId: () => 'm1',
    requestImpl: async (url, options) => {
      calls.probe.push(url)
      calls.probeOptions.push(options)
      if (statusQueue.length) {
        const item = statusQueue.shift()
        if (item instanceof Error) throw item
        return item
      }
      throw new Error('接口不可用')
    },
    confirmImpl: async () => {
      calls.confirm += 1
      return confirmResults.length ? confirmResults.shift() : true
    },
    acquire: async () => {
      calls.acquire += 1
      const result = acquireResults.length ? acquireResults.shift() : { success: true }
      if (result instanceof Error) throw result
      return result
    },
    onRetrySuccess: async (result) => {
      calls.retrySuccess += 1
      const behavior = retrySuccessQueue.length ? retrySuccessQueue.shift() : null
      if (behavior instanceof Error) throw behavior
      if (typeof behavior === 'function') await behavior(guard, result)
    },
    onBack: () => {
      calls.backs += 1
    },
    buildScore: () => ({ leftName: '甲', rightName: '乙', leftScore: 10, rightScore: 3 }),
    ...overrides,
  })

  return { guard, calls, statusQueue, confirmResults, acquireResults, retrySuccessQueue }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('showLost：丢失原因文案与按钮状态', () => {
  it('探测失败（旧后端/断网）→ 通用文案，可重试', async () => {
    const { guard } = createHarness()
    await guard.showLost()
    expect(guard.maskMode.value).toBe('lost')
    expect(guard.maskTitle.value).toBe('执裁权已丢失')
    expect(guard.maskMessage.value).toContain('可能已被其他设备接管或网络超时')
    expect(guard.retryDisabled.value).toBe(false)
    // lost 遮罩展示当前比分
    expect(guard.maskScore.value).toEqual({ leftName: '甲', rightName: '乙', leftScore: 10, rightScore: 3 })
  })

  it('锁已空 → 超时过期文案', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push({ locked: false, sameAccount: false })
    await guard.showLost()
    expect(guard.maskMessage.value).toBe('网络超时，执裁权已过期')
    expect(guard.retryDisabled.value).toBe(false)
  })

  it('异账号持锁 → 正由其他裁判执裁 + 重试置灰', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push({ locked: true, sameAccount: false })
    await guard.showLost()
    expect(guard.maskMessage.value).toBe('正由其他裁判执裁，暂时无法获取执裁权')
    expect(guard.retryDisabled.value).toBe(true)
  })

  it('同账号持锁 → 被接管文案，仍可重试（走顶替确认）', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push({ locked: true, sameAccount: true })
    await guard.showLost()
    expect(guard.maskMessage.value).toBe('执裁权已被其他设备接管')
    expect(guard.retryDisabled.value).toBe(false)
  })
})

describe('retry：探测 → 确认 → 抢锁 → 恢复', () => {
  it('锁空时直接抢锁（无确认弹窗），成功后清遮罩并回调全量恢复', async () => {
    const { guard, calls, statusQueue } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })

    await guard.retry()
    expect(calls.confirm).toBe(0)
    expect(calls.acquire).toBe(1)
    expect(guard.maskMode.value).toBe('')
    expect(calls.retrySuccess).toBe(1)
  })

  it('同账号持锁：确认同意 → 抢锁成功且 kicked（提示由页面 beginLockSession 统一处理）', async () => {
    const { guard, calls, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: true, sameAccount: true })
    acquireResults.push({ success: true, kicked: true })

    await guard.retry()
    expect(calls.confirm).toBe(1)
    expect(calls.acquire).toBe(1)
    expect(guard.maskMode.value).toBe('')
  })

  it('审查🔴1：onRetrySuccess 抛错 → 遮罩保持、提示恢复失败，不进入可交互旧状态', async () => {
    const { guard, calls, statusQueue, retrySuccessQueue } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    retrySuccessQueue.push(new Error('恢复网络失败'))

    await guard.retry()
    expect(calls.acquire).toBe(1)
    expect(calls.retrySuccess).toBe(1)
    // 遮罩必须保持到恢复成功之后
    expect(guard.maskMode.value).toBe('lost')
    expect(guard.maskMessage.value).toBe('数据恢复失败，请重试')
    // retrying 已复位，用户可再次点重试
    expect(guard.retrying.value).toBe(false)
  })

  it('审查🔴2：恢复执行期间遮罩保持非空（onShow 守卫窗口）', async () => {
    const { guard, statusQueue, retrySuccessQueue } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    let maskDuringRecovery = null
    retrySuccessQueue.push(async (g) => {
      maskDuringRecovery = g.maskMode.value
    })

    await guard.retry()
    expect(maskDuringRecovery).toBe('lost')
    expect(guard.maskMode.value).toBe('')
  })

  it('审查🟡3：同账号持锁确认后抢锁失败 → 文案为获取失败而非「其他裁判执裁」', async () => {
    const { guard, calls, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: true, sameAccount: true })
    acquireResults.push({ success: false })

    await guard.retry()
    expect(calls.acquire).toBe(1)
    expect(guard.maskMessage.value).toBe('获取执裁权失败，请稍后重试')
    expect(guard.retryDisabled.value).toBe(false)
  })

  it('审查🟡4：back()/showLost 清除冷却时同步复位 retryDisabled，重试按钮不死锁', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push({ locked: true, sameAccount: false })
    await guard.showLost()
    expect(guard.retryDisabled.value).toBe(true)

    guard.back()
    expect(guard.retryDisabled.value).toBe(false)

    // 再次进入 lost（同账号分支早退）也不残留 disabled
    statusQueue.push({ locked: true, sameAccount: true })
    await guard.showLost()
    expect(guard.retryDisabled.value).toBe(false)
  })

  it('同账号持锁：确认取消 → 不抢锁，遮罩保持', async () => {
    const { guard, calls, statusQueue, confirmResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: true, sameAccount: true })
    confirmResults.push(false)

    await guard.retry()
    expect(calls.confirm).toBe(1)
    expect(calls.acquire).toBe(0)
    expect(guard.maskMode.value).toBe('lost')
  })

  it('异账号持锁：不发 acquire，置灰并进入 30s 冷却', async () => {
    vi.useFakeTimers()
    const { guard, calls, statusQueue } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: true, sameAccount: false })

    await guard.retry()
    expect(calls.acquire).toBe(0)
    expect(guard.maskMessage.value).toBe('正由其他裁判执裁，暂时无法获取执裁权')
    expect(guard.retryDisabled.value).toBe(true)

    vi.advanceTimersByTime(30000)
    expect(guard.retryDisabled.value).toBe(false)
  })

  it('抢锁失败（success=false）→ 遮罩保持并提示失败', async () => {
    const { guard, calls, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    acquireResults.push({ success: false })

    await guard.retry()
    expect(calls.acquire).toBe(1)
    expect(guard.maskMode.value).toBe('lost')
    expect(guard.maskMessage.value).toBe('获取执裁权失败，请稍后重试')
  })

  it('重试进行中禁止并发重入', async () => {
    const { guard, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    let releaseAcquire
    acquireResults.push(new Promise((resolve) => { releaseAcquire = resolve }))

    const first = guard.retry()
    const second = guard.retry()
    await Promise.resolve()
    releaseAcquire({ success: true })
    await Promise.all([first, second])
    // 第二次调用因 retrying=true 直接返回，不产生第二次抢锁
  })
})

describe('acquireGuarded：进入页面的带确认抢锁', () => {
  it('锁空 → 直接抢，无确认', async () => {
    const { guard, calls, statusQueue } = createHarness()
    statusQueue.push({ locked: false, sameAccount: false })
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ success: true })
    expect(calls.confirm).toBe(0)
    expect(acquireFn).toHaveBeenCalledTimes(1)
  })

  it('同账号持锁 + 取消 → { canceled: true } 且不抢', async () => {
    const { guard, calls, statusQueue, confirmResults } = createHarness()
    statusQueue.push({ locked: true, sameAccount: true })
    confirmResults.push(false)
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ canceled: true })
    expect(acquireFn).not.toHaveBeenCalled()
  })

  it('探测失败（旧后端）→ 降级直接抢（保持静默顶替旧行为）', async () => {
    const { guard, calls, statusQueue } = createHarness()
    // statusQueue 留空 → requestImpl 抛错 → probe 返回 null
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ success: true })
    expect(calls.confirm).toBe(0)
    expect(acquireFn).toHaveBeenCalledTimes(1)
  })
})

describe('showDenied / back', () => {
  it('showDenied 设置拒绝遮罩且不带比分', () => {
    const { guard } = createHarness()
    guard.showDenied('当前比赛正由其他设备执裁，请稍后再试')
    expect(guard.maskMode.value).toBe('denied')
    expect(guard.maskTitle.value).toBe('无法进入比赛')
    expect(guard.maskMessage.value).toBe('当前比赛正由其他设备执裁，请稍后再试')
    expect(guard.maskScore.value).toBeNull()
  })

  it('back 清遮罩并回调退出', () => {
    const { guard, calls } = createHarness()
    guard.showDenied()
    guard.back()
    expect(guard.maskMode.value).toBe('')
    expect(calls.backs).toBe(1)
  })
})

describe('链路走查补丁：误导弹窗与死页', () => {
  it('sameToken=true（本页 token 与库内锁一致，如排球 redirect 转交）：同账号也不弹顶替确认，直接抢', async () => {
    const { guard, calls, statusQueue } = createHarness()
    statusQueue.push({ locked: true, sameAccount: true, sameToken: true })
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ success: true })
    expect(calls.confirm).toBe(0)
    expect(acquireFn).toHaveBeenCalledTimes(1)
  })

  it('sameToken=false（换设备后的旧会话/陈旧 URL token）：同账号照常弹顶替确认', async () => {
    const { guard, calls, statusQueue, confirmResults } = createHarness()
    statusQueue.push({ locked: true, sameAccount: true, sameToken: false })
    confirmResults.push(true)
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ success: true })
    expect(calls.confirm).toBe(1)
    expect(acquireFn).toHaveBeenCalledTimes(1)
  })

  it('旧后端降级：探测返回无 sameToken 字段 → 安全默认为非本页锁，照常弹确认', async () => {
    const { guard, calls, statusQueue, confirmResults } = createHarness()
    statusQueue.push({ locked: true, sameAccount: true })
    confirmResults.push(true)
    const acquireFn = vi.fn(async () => ({ success: true }))

    const result = await guard.acquireGuarded(acquireFn)
    expect(result).toEqual({ success: true })
    expect(calls.confirm).toBe(1)
    expect(acquireFn).toHaveBeenCalledTimes(1)
  })

  it('probe 请求头携带 X-Match-Lock-Token（服务端据此回 sameToken）', async () => {
    const { guard, calls, statusQueue } = createHarness({ getLockToken: () => 'tok-abc' })
    statusQueue.push({ locked: false, sameAccount: false, sameToken: false })

    await guard.acquireGuarded(async () => ({ success: true }))
    expect(calls.probe.length).toBe(1)
    expect(calls.probeOptions[0]?.header).toEqual({ 'X-Match-Lock-Token': 'tok-abc' })
  })

  it('恢复失败后再点重试：锁仍在本机，不再弹「另一台设备正在执裁」', async () => {
    const { guard, calls, statusQueue, retrySuccessQueue } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    retrySuccessQueue.push(new Error('恢复网络失败'))

    await guard.retry()
    expect(guard.maskMessage.value).toBe('数据恢复失败，请重试')
    expect(calls.confirm).toBe(0)

    // 第二次重试：探测到 sameAccount=true 且 sameToken=false（本机 token 与库内
    // 不一致也无所谓），但 heldLocally=true 独立拦截——那正是本页刚抢到的锁
    statusQueue.push({ locked: true, sameAccount: true, sameToken: false })
    await guard.retry()
    expect(calls.confirm).toBe(0)
    expect(calls.acquire).toBe(2)
    expect(guard.maskMode.value).toBe('')
  })

  it('back：onBack 返回 false（没真离页）→ 恢复遮罩，避免无出口死页', () => {
    const { guard } = createHarness({ onBack: () => false })
    guard.showDenied('当前比赛正由其他设备执裁，请稍后再试')

    guard.back()
    expect(guard.maskMode.value).toBe('denied')
    expect(guard.maskMessage.value).toBe('当前比赛正由其他设备执裁，请稍后再试')
  })
})

// 模拟 request.js 非 200 分支 reject 的 Error：statusCode=403 = 已被移出执裁名单
function forbiddenError() {
  const error = new Error('你已不在该比赛的执裁名单')
  error.statusCode = 403
  return error
}

describe('403 无执裁权限（被移出裁判名单）：专门文案 + 禁用重试', () => {
  it('showLost 探测 403 → 无权限文案且重试禁用（不再无限重试）', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push(forbiddenError())

    await guard.showLost()
    expect(guard.maskMode.value).toBe('lost')
    expect(guard.maskMessage.value).toBe('你已不在该比赛的执裁名单，请联系赛事创建者或管理员')
    expect(guard.retryDisabled.value).toBe(true)
  })

  it('retry 抢锁抛 403 → 无权限文案且重试禁用', async () => {
    const { guard, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push({ locked: false, sameAccount: false })
    acquireResults.push(forbiddenError())

    await guard.retry()
    expect(guard.maskMessage.value).toBe('你已不在该比赛的执裁名单，请联系赛事创建者或管理员')
    expect(guard.retryDisabled.value).toBe(true)
  })

  it('retry 探测 403 → 专门文案 + 重试禁用，acquire 不被调用（短路收口，省一次注定失败的抢锁）', async () => {
    const { guard, calls, statusQueue, acquireResults } = createHarness()
    await guard.showLost()
    statusQueue.push(forbiddenError())
    // 若短路失效误发 acquire 会「成功」并清遮罩，借此暴露回归
    acquireResults.push({ success: true })

    await guard.retry()
    expect(calls.acquire).toBe(0)
    expect(acquireResults.length).toBe(1)
    expect(guard.maskMessage.value).toBe('你已不在该比赛的执裁名单，请联系赛事创建者或管理员')
    expect(guard.retryDisabled.value).toBe(true)
    // try 块内 return 走 finally 复位 retrying，不遗留并发锁
    expect(guard.retrying.value).toBe(false)
  })

  it('403 粘性：随后 showDenied(通用文案) 不覆盖 403 文案、不重新点亮重试按钮', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push(forbiddenError())
    await guard.showLost()

    guard.showDenied('当前比赛正由其他设备执裁，请稍后再试')
    expect(guard.maskMode.value).toBe('denied')
    expect(guard.maskMessage.value).toBe('你已不在该比赛的执裁名单，请联系赛事创建者或管理员')
    expect(guard.retryDisabled.value).toBe(true)
  })

  it('acquireGuarded：acquireFn 抛 403 → 异常原样上抛，随后 showDenied 呈现 403 文案', async () => {
    const { guard, statusQueue } = createHarness()
    statusQueue.push({ locked: false, sameAccount: false })
    const error = forbiddenError()
    const acquireFn = async () => { throw error }

    await expect(guard.acquireGuarded(acquireFn)).rejects.toBe(error)
    guard.showDenied('暂时无法取得执裁权，请稍后再试')
    expect(guard.maskMode.value).toBe('denied')
    expect(guard.maskMessage.value).toBe('你已不在该比赛的执裁名单，请联系赛事创建者或管理员')
    expect(guard.retryDisabled.value).toBe(true)
  })
})
