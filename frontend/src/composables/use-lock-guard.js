/**
 * 执裁锁遮罩守卫（useLockGuard）
 *
 * 统一五个执裁面（羽毛球记分板/接力赛/团体赛主控/团体赛名单/排球）的锁丢失交互：
 * 彻底取代「只读模式」——锁不可得或中途丢失时弹全屏遮罩（LockMask 组件渲染），
 * 重试走「探测 → 同账号顶替确认 → 抢锁 → 全量恢复」链路。
 *
 * 依赖后端只读探测接口 GET /api/v1/matches/{id}/lock-status（返回
 * { locked, sameAccount, sameToken, lockExpireTime }）。探测请求带
 * X-Match-Lock-Token 头，服务端回 sameToken=true 表示库内锁就是本页 token
 * （排球 redirect 转交锁 / 本页 token 幂等重进），此类同账号持锁不弹顶替确认；
 * 旧后端无 sameToken 字段时安全默认为「非本页锁」，照常弹确认。
 * 接口 404/失败时自动降级：不做顶替确认直接抢锁（旧行为）、不区分丢失原因文案。
 *
 * 依赖注入（便于无头测试）：requestImpl / confirmImpl。
 * 页面职责：提供 acquire（token 归页面管）、onRetrySuccess（必须走全量服务端恢复，
 * 绝不能带旧内存状态续写）、onBack、buildScore。
 */
import { ref } from 'vue'
import { request } from '@/utils/request'
import { matchLockHeader } from '@/utils/match-lock'

const DENIED_TITLE = '无法进入比赛'
const LOST_TITLE = '执裁权已丢失'
const REPLACE_CONFIRM_TITLE = '顶替执裁确认'
const REPLACE_CONFIRM_CONTENT = '检测到当前账号的另一台设备正在执裁，是否顶替？顶替后对方设备将立即停止执裁。'
const RETRY_FAILED = '获取执裁权失败，请稍后重试'
const RECOVER_FAILED = '数据恢复失败，请重试'
const DEFAULT_DENIED_MESSAGE = '当前比赛正由其他设备执裁，请稍后再试'
const LOST_TAKEN_OVER = '执裁权已被其他设备接管'
const LOST_EXPIRED = '网络超时，执裁权已过期'
const LOST_UNKNOWN = '执裁权已丢失（可能已被其他设备接管或网络超时）'
const LOST_HELD_OTHER_ACCOUNT = '正由其他裁判执裁，暂时无法获取执裁权'
const DENIED_NO_PERMISSION = '你已不在该比赛的执裁名单，请联系赛事创建者或管理员'


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
  /** 本页锁 token（探测时放入请求头换回 sameToken；无锁页面不注入，返回空 token） */
  const getLockToken = typeof options.getLockToken === 'function' ? options.getLockToken : () => ''
  const requestImpl = options.requestImpl || request
  const confirmImpl = typeof options.confirmImpl === 'function' ? options.confirmImpl : defaultConfirm
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
  /**
   * 本页会话是否已确认持锁成功：抢锁成功即置 true，收到 onLost（showLost）清零。
   * 用于「恢复失败后用户再点重试」——此时锁仍在本设备手上，探测出 sameAccount
   * 是「自己」，不该再弹一次顶替确认（否则文案误导成另一台设备在执裁）。
   */
  let heldLocally = false
  /**
   * 探测/抢锁命中 403 = 本账号已无执裁权限（被移出裁判名单）：与断网/旧后端不同，
   * 重试永远不可能成功，必须禁用重试并锁定专门文案（见 showDenied 的粘性分支）。
   */
  let forbidden = false

  function clearCooldown() {
    if (cooldownTimer) {
      clearTimeout(cooldownTimer)
      cooldownTimer = null
    }
    // 冷却解除必须同步复位标志：摘掉定时器的路径（back/showLost 重入/stop）
    // 若只清定时器会留下 retryDisabled=true 的死按钮（审查 🟡4）
    retryDisabled.value = false
  }

  function scheduleCooldown() {
    // clearCooldown 会复位标志，故在其后显式置灰（调用方无需重复设置）
    clearCooldown()
    retryDisabled.value = true
    cooldownTimer = setTimeout(() => {
      retryDisabled.value = false
      cooldownTimer = null
    }, OTHER_ACCOUNT_COOLDOWN_MS)
  }

  /** 只读探测：接口缺失/失败返回 null（调用方据此降级）；403（已无执裁权限）返回 { forbidden: true }。 */
  async function probeLockStatus() {
    if (!getMatchId()) return null
    try {
      return await requestImpl('/api/v1/matches/' + getMatchId() + '/lock-status', {
        method: 'GET',
        silent: true,
        // 携带本页锁 token：服务端比对库内锁后回 sameToken，供顶替确认判定
        // 「转交锁/幂等重进」。token 为空时 matchLockHeader 返回空对象，请求形状不变
        header: matchLockHeader(getLockToken()),
      })
    } catch (error) {
      if (error?.statusCode === 403) return { forbidden: true }
      return null
    }
  }

  function showDenied(message) {
    clearCooldown()
    maskMode.value = 'denied'
    maskTitle.value = DENIED_TITLE
    // forbidden 粘性：页面 denyMatchEntry 传来的通用文案不允许覆盖 403 的
    // 专门文案，也不允许重新点亮重试按钮（否则又回到无限重试）
    if (forbidden) {
      maskMessage.value = DENIED_NO_PERMISSION
      retryDisabled.value = true
    } else {
      maskMessage.value = message || DEFAULT_DENIED_MESSAGE
      retryDisabled.value = false
    }
    maskScore.value = null
  }

  /**
   * 执裁中失去锁：先探测再定文案（区分「被接管」与「超时过期」）。
   * 异账号持锁时重试按钮置灰并进入冷却——抢也抢不到，避免无谓请求。
   */
  async function showLost() {
    clearCooldown()
    // 先给兜底文案：探测往返期间遮罩已经可见，不能让它是一张没有说明的卡
    maskMessage.value = LOST_UNKNOWN
    maskMode.value = 'lost'
    maskTitle.value = LOST_TITLE
    maskScore.value = typeof buildScore === 'function' ? buildScore() : null
    // 走到丢失分支说明本页不再持锁：清掉「本地持锁」标记，重试时照常走顶替确认
    heldLocally = false
    const status = await probeLockStatus()
    if (status?.forbidden) {
      // 探测命中 403：已被移出执裁名单，重试无意义（置灰 + 专门文案）
      forbidden = true
      maskMessage.value = DENIED_NO_PERMISSION
      retryDisabled.value = true
      return
    }
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
   * 是否需要弹「顶替执裁确认」：同账号持锁，且这锁不是本页自己的。
   * - heldLocally：本页抢锁成功过（典型是恢复失败后再点重试），探测到的 sameAccount 就是自己；
   * - sameToken：探测请求头带的本页 token 与库内锁一致（排球 redirect 转交锁、
   *   本页 token 幂等重进），由服务端权威判定——不再信任「URL 带 lockToken」的
   *   页面自证（陈旧 URL 会永久跳过确认、静默顶替另一台设备）；旧后端不返回
   *   sameToken 字段时按「非本页锁」安全默认，照常弹确认。
   * 这两种情况弹「另一台设备正在执裁，是否顶替」都是误导用户。
   */
  function shouldConfirmReplace(status) {
    return status?.locked === true
      && status.sameAccount === true
      && status?.sameToken !== true
      && !heldLocally
  }

  /**
   * 带确认的抢锁（进入页面与遮罩重试共用）：
   * - 锁空 → 直接抢；
   * - 同账号持锁且不是自己 → 弹「顶替确认」，取消则不抢（返回 { canceled: true }）；
   * - 异账号持锁 → 照常发起 acquire（服务端会拒绝），由页面按失败走拒绝遮罩；
   * - 探测失败（旧后端）→ 降级直接抢（保持既有静默顶替行为）。
   * acquireFn 由调用方传入（token 归页面管理）。
   */
  async function acquireGuarded(acquireFn) {
    const status = await probeLockStatus()
    if (shouldConfirmReplace(status)) {
      if (!(await confirmImpl())) return { canceled: true }
    }
    if (typeof acquireFn !== 'function') return null
    let result
    try {
      result = await acquireFn()
    } catch (error) {
      // 抢锁命中 403：置粘性标记后原样上抛——页面 catch → denyMatchEntry(通用文案)
      // → showDenied，靠 forbidden 粘性最终呈现 403 专门文案并禁用重试
      if (error?.statusCode === 403) forbidden = true
      throw error
    }
    if (result?.success === true) {
      heldLocally = true
      forbidden = false
    }
    return result
  }

  /** 遮罩上的「重新获取执裁权」：探测 → 确认 → 抢锁 → 成功后交回页面做全量恢复。 */
  async function retry() {
    if (retrying.value || retryDisabled.value) return
    if (typeof acquireImpl !== 'function') return
    retrying.value = true
    try {
      const status = await probeLockStatus()
      if (status?.forbidden) {
        // 探测即 403：已无执裁权限，继续抢锁必 403——短路收口，省一次注定失败的写请求
        forbidden = true
        maskMessage.value = DENIED_NO_PERMISSION
        retryDisabled.value = true
        return
      }
      if (shouldConfirmReplace(status)) {
        if (!(await confirmImpl())) return
      } else if (status?.locked === true && status.sameAccount === false) {
        maskMessage.value = LOST_HELD_OTHER_ACCOUNT
        retryDisabled.value = true
        scheduleCooldown()
        return
      }

      let result
      try {
        result = await acquireImpl()
      } catch (error) {
        // 抢锁命中 403 = 已无执裁权限：专门文案 + 禁用重试；其余失败维持通用文案
        if (error?.statusCode === 403) {
          forbidden = true
          maskMessage.value = DENIED_NO_PERMISSION
          retryDisabled.value = true
          return
        }
        maskMessage.value = RETRY_FAILED
        return
      }
      if (result?.success !== true) {
        // 同账号自己另一设备持有时的失败≠「其他裁判执裁」，文案按是否异账号区分
        maskMessage.value = status?.locked === true && status.sameAccount === false
          ? LOST_HELD_OTHER_ACCOUNT
          : RETRY_FAILED
        if (status?.locked === true && status.sameAccount === false) {
          retryDisabled.value = true
          scheduleCooldown()
        }
        return
      }
      heldLocally = true
      forbidden = false

      // 抢锁成功：遮罩保持到页面全量恢复完成之后——恢复期间页面仍被遮罩与
      // onShow 守卫挡住，绝不出现「旧内存状态 + 可交互」窗口（审查 🔴1/🔴2）
      if (typeof onRetrySuccess === 'function') {
        try {
          await onRetrySuccess(result)
        } catch (_) {
          maskMessage.value = RECOVER_FAILED
          return
        }
      }
      maskMode.value = ''
      maskMessage.value = ''
    } catch (_) {
      maskMessage.value = RETRY_FAILED
    } finally {
      retrying.value = false
    }
  }

  function back() {
    clearCooldown()
    const prevMode = maskMode.value
    const prevMessage = maskMessage.value
    maskMode.value = ''
    maskMessage.value = ''
    if (typeof onBack !== 'function') return
    // onBack 显式返回 false = 没真正离页（导航节流/提交中/内部退栈）：恢复遮罩，
    // 否则会留下「无遮罩 + isReadOnly 全禁」的无出口死页（链路走查）
    const left = onBack()
    if (left === false) {
      maskMode.value = prevMode
      maskMessage.value = prevMessage
      // clearCooldown 已复位按钮，但 forbidden 场景必须保持禁用（重试不可能成功）
      if (forbidden) retryDisabled.value = true
    }
  }

  /** 页面 onUnmounted/onUnload 调用，清冷却定时器。 */
  function stop() {
    clearCooldown()
    forbidden = false
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
