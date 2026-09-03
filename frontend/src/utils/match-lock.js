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

export async function acquireMatchLockWithRetry(matchId, lockToken, options = {}) {
  const attempts = Math.max(1, Number(options.attempts || 3))
  const delayMs = Math.max(0, Number(options.delayMs ?? 300))
  for (let attempt = 0; attempt < attempts; attempt += 1) {
    try {
      const result = await acquireMatchLock(matchId, lockToken)
      if (result?.success === true || result?.editable === true || attempt === attempts - 1) {
        return result
      }
    } catch (error) {
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
