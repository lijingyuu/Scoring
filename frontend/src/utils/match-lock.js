import { request } from './request'

export const MATCH_LOCK_TOKEN_HEADER = 'X-Match-Lock-Token'
const MATCH_LOCK_STORAGE_KEY = 'scoring_match_lock_token'

export function createMatchLockToken() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID()
    }
  } catch (_) {
  }
  return 'lock_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 12)
}

function matchLockStorageKey(matchId) {
  return matchId ? `${MATCH_LOCK_STORAGE_KEY}_${matchId}` : MATCH_LOCK_STORAGE_KEY
}

export function loadMatchLockToken(matchId) {
  if (!matchId) return ''
  try {
    return uni.getStorageSync(matchLockStorageKey(matchId)) || ''
  } catch (_) {
    return ''
  }
}

export function saveMatchLockToken(matchId, lockToken) {
  if (!matchId || !lockToken) return
  try {
    uni.setStorageSync(matchLockStorageKey(matchId), lockToken)
  } catch (_) {
    // Token persistence is a recovery aid; lock acquisition remains authoritative.
  }
}

export function clearMatchLockToken(matchId) {
  if (!matchId) return
  try {
    uni.removeStorageSync(matchLockStorageKey(matchId))
  } catch (_) {
    // noop
  }
}

export function matchLockHeader(lockToken) {
  return lockToken ? { [MATCH_LOCK_TOKEN_HEADER]: lockToken } : {}
}

export function acquireMatchLock(matchId, lockToken) {
  return request('/api/v1/matches/' + matchId + '/lock', {
    method: 'POST',
    data: { lockToken },
    silent: true,
  })
}

 /**
  * 锁响应契约：后端 MatchLockService 对 success 与 editable 恒等赋值（editable 为历史兼容别名），
  * 前端判活统一只看 success（2026-10 契约疑点清理；原「success || editable」双字段判活已全线移除）。
  */
export async function acquireMatchLockWithRetry(matchId, lockToken, options = {}) {
  const attempts = Math.max(1, Number(options.attempts || 3))
  const delayMs = Math.max(0, Number(options.delayMs ?? 300))
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const result = await acquireMatchLock(matchId, lockToken)
      if (result?.success === true || attempt === attempts - 1) {
        return result
      }
    } catch (error) {
      // 403=已无执裁权限：权限不会因重试恢复，重试只会在 300ms×N 的空转后必然失败，
      // 立即原样抛给调用方，由页面走 forbidden 收口（文案+禁用重试）。
      // 其余异常（网络抖动等）维持原有重试逻辑。
      if (error?.statusCode === 403) throw error
      if (attempt === attempts - 1) throw error
    }
    if (delayMs > 0) {
      await new Promise((resolve) => setTimeout(resolve, delayMs))
    }
  }
  return null
}

export function heartbeatMatchLock(matchId, lockToken) {
  return request('/api/v1/matches/' + matchId + '/heartbeat', {
    method: 'POST',
    data: { lockToken },
    silent: true,
  })
}

export function releaseMatchLock(matchId, lockToken) {
  if (!matchId || !lockToken) return Promise.resolve()
  return request('/api/v1/matches/' + matchId + '/release', {
    method: 'POST',
    data: { lockToken },
    silent: true,
  }).catch(() => {})
}

export function startMatchLockHeartbeat(matchId, lockToken, onLost) {
  // stopped：本心跳线已被 stop（抢回锁重启心跳 / 页面卸载）。在途请求回来发现它置位，
  // 说明结论已过期，必须丢弃——否则「抢回锁后，之前的失败回信迟到」会再糊一张遮罩。
  // inFlight：上一跳还没回来就跳过这一跳，避免弱网下多个心跳同时在途、乱序返回。
  let stopped = false
  let inFlight = false
  const timer = setInterval(async () => {
    if (inFlight) return
    inFlight = true
    try {
      const result = await heartbeatMatchLock(matchId, lockToken)
      if (stopped) return
      if (result?.success === true) {
        return
      }
      onLost?.()
    } catch (error) {
      if (error?.statusCode === 403) {
        // 403=已无执裁权限（被移出裁判名单），与网络抖动本质不同：重试永远 403，
        // 必须立即走失锁遮罩告知用户，否则用户会继续本地记分而事件永不落库。
        // 已核实 heartbeat 的 HTTP 403 只可能来自权限校验（权限校验先于 token 校验，
        // token 失效返回的是 200 + success=false），不会与写守卫 403 混淆。
        // stopped 置位说明本心跳线已被 stop（抢回锁重启/页面卸载），过期结论同样丢弃。
        if (!stopped) onLost?.()
        return
      }
      // 其余异常按网络抖动处理：不等价于锁已丢失，等待下一次心跳或写接口兜底
      // （75s 租约内容忍丢包，弱网容错语义保持不变）。
    } finally {
      inFlight = false
    }
  }, 15000)

  return () => {
    stopped = true
    clearInterval(timer)
  }
}
