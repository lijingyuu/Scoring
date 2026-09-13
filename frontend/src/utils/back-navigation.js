import { normalizeRoute, tournamentScheduleRoute } from '@/pages/tournament/tournament-navigation'

/**
 * 页面返回导航工具。
 *
 * 背景：用户从分享卡片（朋友 / 群聊）或扫码直接进入某个页面时，
 * 该页面就是页面栈的第一个页面（栈长度为 1）。此时 uni.navigateBack()
 * 没有上一页可退，会静默失败——表现为「点返回没反应」。
 *
 * 三层返回决策（只在「页面是栈底」时才介入，正常浏览完全不受影响）：
 *   ① 栈 > 1：uni.navigateBack() 真实回退（行为与原来一致）；
 *   ② 栈 = 1：查 PARENT_MAP 层级表，redirectTo 逻辑父页，逐级上溯；
 *   ③ 查不到 / 参数不足：switchTab 回首页 tab 兜底（方案一）。
 * 所有跳转均挂 fail 兜底（朋友圈单页模式等禁跳场景），绝不卡死。
 * 另有转场连点保护：转场动画期间重复调用会被忽略，避免第二次导航
 * 因平台导航锁 fail 而误触发回首页的竞态。
 *
 * 当前层级表覆盖（可分享/赛后记录链）：
 *   bracket / groups          → detail?id
 *   4 个赛后记录页             → 赛程页（bracket/groups 按 tournamentType）→ detail → 首页
 *   detail                    → 首页 tab（无需查表，兜底天然覆盖）
 */

export const HOME_TAB_URL = '/pages/index/index'

function buildDetailUrl(id) {
  return '/pages/tournament/detail?id=' + encodeURIComponent(id)
}

/**
 * 赛后记录页的父页解析：回本场所属的赛程页（淘汰赛 → bracket，小组/循环 → groups）。
 * - tournamentType 已随数据加载 → 精准回赛程页；
 * - 仅知道 tournamentId（数据未加载）→ 降级回赛事详情页；
 * - 连 tournamentId 都没有 → null，走首页兜底。
 */
function scheduleParentResolver(query, context) {
  const tournamentId = String(query.tournamentId || context?.tournamentId || '').trim()
  if (!tournamentId) return null
  // 组别只拼到赛程页上；赛事详情页（无组别概念）不携带 divisionId
  const divisionId = String(query.divisionId || context?.divisionId || '').trim()
  if (context?.tournamentType == null) {
    return { type: 'page', url: buildDetailUrl(tournamentId) }
  }
  return {
    type: 'page',
    url: '/' + tournamentScheduleRoute(context.tournamentType) + '?id=' + encodeURIComponent(tournamentId)
      + (divisionId ? '&divisionId=' + encodeURIComponent(divisionId) : ''),
  }
}

/** bracket / groups → detail?id */
function schedulePageResolver(query) {
  const id = String(query.id || '').trim()
  return id ? { type: 'page', url: buildDetailUrl(id) } : null
}

/**
 * 逻辑父页层级表：route（无前导斜杠）→ resolver(query, context)。
 * resolver 返回 { type: 'page'|'tab', url } 或 null（null = 交回首页兜底）。
 */
const PARENT_MAP = {
  'pages/tournament/bracket': schedulePageResolver,
  'pages/tournament/groups': schedulePageResolver,
  'pages/tournament/individual-record': scheduleParentResolver,
  'pages/tournament/team-record': scheduleParentResolver,
  'pages/tournament/relay-record': scheduleParentResolver,
  'pages/volleyball/record': scheduleParentResolver,
}

/**
 * 安全获取当前页面栈。非小程序环境（如单测）返回空数组。
 */
export function getPageStack() {
  try {
    if (typeof getCurrentPages === 'function') {
      return getCurrentPages() || []
    }
  } catch (_) {
    // noop
  }
  return []
}

/**
 * 是否存在可回退的上一页。
 */
export function canNavigateBack() {
  return getPageStack().length > 1
}

/**
 * 回到首页 tab。switchTab 只支持 tabBar 页面；
 * 若传入非 tab 页面地址，则兜底用 reLaunch 打开。
 */
export function navigateHome(fallbackUrl = HOME_TAB_URL) {
  uni.switchTab({
    url: fallbackUrl,
    fail: () => {
      uni.reLaunch({ url: fallbackUrl })
    },
  })
}

/**
 * 转场连点保护：navigateBackOrHome 发起导航后的短窗口内忽略重复调用。
 *
 * 背景：页面转场动画（微信约 300ms）期间连点返回时，第二次导航会因
 * 平台导航锁而 fail，若不拦截会误触发「fail → 回首页」的兑态。
 * 窗口只需覆盖转场时长；窗口外的正常连续返回不受影响。
 */
const NAVIGATION_LOCK_WINDOW_MS = 600
let lastNavigationStartedAt = 0

/** 重置连点保护锁（仅供测试隔离使用）。 */
export function resetNavigationLock() {
  lastNavigationStartedAt = 0
}

/**
 * 栈底时解析当前页的逻辑父页（层级表）。
 * query 取自栈底页面自身的 options（小程序 Page 原生携带）；
 * context 由调用方传入页面已加载的数据（如 tournamentType）。
 */
export function resolveLogicalParent(context = null) {
  const stack = getPageStack()
  const current = stack[stack.length - 1]
  if (!current) return null
  const resolver = PARENT_MAP[normalizeRoute(current.route)]
  if (typeof resolver !== 'function') return null
  const query = current.options || current.$page?.options || {}
  try {
    return resolver(query, context || null) || null
  } catch (_) {
    return null
  }
}

/**
 * 智能返回（三层决策，见文件头注释）。
 *
 * @param {object} [options]
 * @param {number} [options.delta=1] 回退的层数
 * @param {string} [options.fallbackUrl] 无法回退且查表无果时的落地页，默认首页 tab
 * @param {object} [options.context] 页面已加载的数据（如 tournamentId / tournamentType），供层级表解析
 */
export function navigateBackOrHome({ delta = 1, fallbackUrl = HOME_TAB_URL, context = null } = {}) {
  // 转场中连点：直接忽略（不延长窗口，避免连续点击永远无法解锁）
  const now = Date.now()
  if (now - lastNavigationStartedAt < NAVIGATION_LOCK_WINDOW_MS) return
  lastNavigationStartedAt = now

  if (canNavigateBack()) {
    uni.navigateBack({
      delta,
      fail: () => navigateHome(fallbackUrl), // 兜底：个别平台/时序下栈判断失效
    })
    return
  }

  const parent = resolveLogicalParent(context)
  if (!parent) {
    navigateHome(fallbackUrl)
    return
  }
  if (parent.type === 'tab') {
    uni.switchTab({
      url: parent.url,
      fail: () => uni.reLaunch({ url: parent.url }),
    })
    return
  }
  // redirectTo 替换当前页：栈长保持 1，连点返回即沿层级表逐级上溯
  uni.redirectTo({
    url: parent.url,
    fail: () => navigateHome(fallbackUrl),
  })
}
