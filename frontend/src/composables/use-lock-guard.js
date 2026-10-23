/**
 * 执裁锁遮罩守卫（useLockGuard）
 *
 * 统一五个执裁面（羽毛球记分板/接力赛/团体赛主控/团体赛名单/排球）的锁丢失交互：
 * 彻底取代「只读模式」——锁不可得或中途丢失时弹全屏遮罩（LockMask 组件渲染），
 * 重试走「探测 → 同账号顶替确认 → 抢锁 → 全量恢复」链路。
 *
 * 依赖后端只读探测接口 GET /api/v1/matches/{id}/lock-status（返回
 * { locked, sameAccount, lockExpireTime }）。接口 404/失败时自动降级：
 * 不做顶替确认直接抢锁（旧行为）、不区分丢失原因文案。
 *
 * 依赖注入（便于无头测试）：requestImpl / confirmImpl / toastImpl。
 * 页面职责：提供 acquire（token 归页面管）、onRetrySuccess（必须走全量服务端恢复，
 * 绝不能带旧内存状态续写）、onBack、buildScore。
 */
import { ref } from 'vue'
import { request } from '@/utils/request'

const DENIED_TITLE = '无法进入比赛'
const LOST_TITLE = '执裁权已丢失'
const REPLACE_CONFIRM_TITLE = '顶替执裁确认'
const REPLACE_CONFIRM_CONTENT = '检测到当前账号的另一台设备正在执裁，是否顶替？顶替后对方设备将立即停止执裁。'
const RETRIEVED_TOAST = '已从另一台设备取回执裁权'

const DEFAULT_DENIED_MESSAGE = '当前比赛正由其他设备执裁，请稍后再试'
const LOST_TAKEN_OVER = '执裁权已被其他设备接管'
const LOST_EXPIRED = '网络超时，执裁权已过期'
const LOST_UNKNOWN = '执裁权已丢失（可能已被其他设备接管或网络超时）'
const LOST_HELD_OTHER_ACCOUNT = '正由其他裁判执裁，暂时无法获取执裁权'
const RETRY_FAILED = '获取执裁权失败，请稍后重试'

const OTHER_ACCOUNT_COOLDOWN_MS = 30000

function defaultConfirm() {
  return new Promise((resolve) => {
    uni.showModal({
      title: REPLACE_CONFIRM_TITLE,
      content: REPLACE_CONFIRM_CONTENT,
      confirmText: '顶替执裁',
      cancelText: '取消',
      success: (res) => resolve(!!res?.confirm),
      fail: () => resolve(false),
    })
  })
}

export function useLockGuard(options = {}) {
  const getMatchId = typeof options.getMatchId === 'function' ? options.getMatchId : () => ''
  const requestImpl = options.requestImpl || request
  const confirmImpl = typeof options.confirmImpl === 'function' ? options.confirmImpl : defaultConfirm
  const toastImpl = typeof options.toastImpl === 'function'
    ? options.toastImpl
    : (title) => uni.showToast({ title, icon: 'none' })
  const acquireImpl = options.acquire
  const onRetrySuccess = options.onRetrySuccess
  const onBack = options.onBack
  const buildScore = options.buildScore

  /** '' | 'denied'（进入拒绝）| 'lost'（执裁中失去） */
  const maskMode = ref('')
  const maskTitle = ref('')
  const maskMessage = ref('')
  const maskScore = ref(null)
  const retryDisabled = ref(false)
  const retrying = ref(false)

  let cooldownTimer = null

  function clearCooldown() {
    if (cooldownTimer) {
      clearTimeout(cooldownTimer)
      cooldownTimer = null
    }
  }

  function scheduleCooldown() {
    clearCooldown()
    cooldownTimer = setTimeout(() => {
      retryDisabled.value = false
      cooldownTimer = null
    }, OTHER_ACCOUNT_COOLDOWN_MS)
  }

  /** 只读探测：接口缺失/失败返回 null（调用方据此降级）。 */
  async function probeLockStatus() {
    if (!getMatchId()) return null
    try {
      return await requestImpl('/api/v1/matches/' + getMatchId() + '/lock-status', {
        method: 'GET',
        silent: true,
      })
    } catch (_) {
      return null
    }
  }

  function showDenied(message) {
    clearCooldown()
    maskMode.value = 'denied'
    maskTitle.value = DENIED_TITLE
    maskMessage.value = message || DEFAULT_DENIED_MESSAGE
    maskScore.value = null
    retryDisabled.value = false
  }

  /**
   * 执裁中失去锁：先探测再定文案（区分「被接管」与「超时过期」）。
   * 异账号持锁时重试按钮置灰并进入冷却——抢也抢不到，避免无谓请求。
   */
  async function showLost() {
    clearCooldown()
    maskMode.value = 'lost'
    maskTitle.value = LOST_TITLE
    maskScore.value = typeof buildScore === 'function' ? buildScore() : null
    const status = await probeLockStatus()
    if (!status) {
      maskMessage.value = LOST_UNKNOWN
      retryDisabled.value = false
      return
    }
    if (status.locked === true) {
      maskMessage.value = LOST_TAKEN_OVER
      if (status.sameAccount !== true) {
        maskMessage.value = LOST_HELD_OTHER_ACCOUNT
        retryDisabled.value = true
        scheduleCooldown()
      }
      return
    }
    maskMessage.value = LOST_EXPIRED
    retryDisabled.value = false
  }

  /**
   * 带确认的抢锁（进入页面与遮罩重试共用）：
   * - 锁空 → 直接抢；
   * - 同账号持锁 → 弹「顶替确认」，取消则不抢（返回 { canceled: true }）；
   * - 异账号持锁 → 照常发起 acquire（服务端会拒绝），由页面按失败走拒绝遮罩；
   * - 探测失败（旧后端）→ 降级直接抢（保持既有静默顶替行为）。
   * acquireFn 由调用方传入（token 归页面管理）。
   */
  async function acquireGuarded(acquireFn) {
    const status = await probeLockStatus()
    if (status?.locked === true && status.sameAccount === true) {
      if (!(await confirmImpl())) return { canceled: true }
    }
    if (typeof acquireFn !== 'function') return null
    return acquireFn()
  }

  /** 遮罩上的「重新获取执裁权」：探测 → 确认 → 抢锁 → 成功后交回页面做全量恢复。 */
  async function retry() {
    if (retrying.value || retryDisabled.value) return
    if (typeof acquireImpl !== 'function') return
    retrying.value = true
    try {
      const status = await probeLockStatus()
      if (status?.locked === true && status.sameAccount === true) {
        if (!(await confirmImpl())) return
      } else if (status?.locked === true && status.sameAccount === false) {
        maskMessage.value = LOST_HELD_OTHER_ACCOUNT
        retryDisabled.value = true
        scheduleCooldown()
        return
      }
      const result = await acquireImpl()
      if (result?.success === true) {
        if (result?.kicked === true) toastImpl(RETRIEVED_TOAST)
        maskMode.value = ''
        maskMessage.value = ''
        if (typeof onRetrySuccess === 'function') await onRetrySuccess(result)
        return
      }
      maskMessage.value = status?.locked === true ? LOST_HELD_OTHER_ACCOUNT : RETRY_FAILED
      if (status?.locked === true && status.sameAccount === false) {
        retryDisabled.value = true
        scheduleCooldown()
      }
    } catch (_) {
      maskMessage.value = RETRY_FAILED
    } finally {
      retrying.value = false
    }
  }

  function back() {
    clearCooldown()
    maskMode.value = ''
    maskMessage.value = ''
    if (typeof onBack === 'function') onBack()
  }

  /** 页面 onUnmounted/onUnload 调用，清冷却定时器。 */
  function stop() {
    clearCooldown()
  }

  return {
    maskMode,
    maskTitle,
    maskMessage,
    maskScore,
    retryDisabled,
    retrying,
    showDenied,
    showLost,
    acquireGuarded,
    retry,
    back,
    stop,
  }
}
