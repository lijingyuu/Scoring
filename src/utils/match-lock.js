import { request } from './request'

export const MATCH_LOCK_TOKEN_HEADER = 'X-Match-Lock-Token'

export function createMatchLockToken() {
  try {
    if (typeof crypto !== 'undefined' && crypto.randomUUID) {
      return crypto.randomUUID()
    }
  } catch (_) {
  }
  return 'lock_' + Date.now().toString(36) + '_' + Math.random().toString(36).slice(2, 12)
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
  const timer = setInterval(async () => {
    try {
      const result = await heartbeatMatchLock(matchId, lockToken)
      if (result?.success === true || result?.editable === true) {
        return
      }
      onLost?.()
    } catch (_) {
      // 网络异常不等于锁已丢失，等待下一次心跳或写接口兜底。
    }
  }, 15000)

  return () => clearInterval(timer)
}
