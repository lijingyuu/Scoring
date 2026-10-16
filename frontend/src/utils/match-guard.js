import { request } from './request'
import { ensureAuth } from '@/store/auth'

/**
 * 设计约定（两类失败必须分开处理，不能混）：
 * - 网络失败（request 抛错）：无法判断权限，属于「可重试」——弹 modal 让用户重试，
 *   绝不静默返回 false（否则调用方的 setTimeout(navigateBack, 1500) 会把刚填一半的阵容丢掉）。
 * - 有响应但 canOperate !== true（无权限）：属于「不可重试」——toast 提示后返回 false。
 *
 * request 走 silent（不 toast），网络错误的提示统一由下面的 modal 承担，避免 toast + modal 双弹。
 */

/** 弹「网络异常」modal，用户点重试 resolve(true)，点返回 / modal 失败 resolve(false)；永不悬挂 */
function confirmRetryAfterNetworkError() {
  return new Promise((resolve) => {
    let settled = false
    const settle = (value) => {
      if (settled) return
      settled = true
      resolve(value)
    }

    uni.showModal({
      title: '网络异常',
      content: '无法确认执裁权限，请检查网络后重试',
      confirmText: '重试',
      cancelText: '返回', // 微信限制 cancelText 最多 4 个字符
      success: (res) => settle(res?.confirm === true),
      // 弹窗失败 / 调用完成却没有回调时按「返回」处理，保证 Promise 一定 settle
      fail: () => settle(false),
      complete: () => settle(false),
    })
  })
}

export async function requireMatchOperator(matchId) {
  if (!matchId) {
    uni.showToast({ title: '缺少比赛ID', icon: 'none' })
    return false
  }

  try {
    await ensureAuth()
    const canOperate = await request('/api/v1/matches/' + matchId + '/can-operate', { method: 'GET', silent: true })
    if (canOperate === true) return true
    uni.showToast({ title: '请先录入裁判身份后再开始执裁', icon: 'none', duration: 2000 })
    return false
  } catch (error) {
    const retry = await confirmRetryAfterNetworkError()
    if (!retry) return false
    // 用户选择重试：重新走完整流程，可重复多次直到成功或用户放弃
    return requireMatchOperator(matchId)
  }
}
