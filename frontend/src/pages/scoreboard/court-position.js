/**
 * 羽毛球「四半区站位」推导纯函数（无副作用，可无头测试）
 *
 * 规则依据 BWF《Laws of Badminton》V5.0（2025-04-26 生效，条款号见 docs/plans/
 * 羽毛球双打拆分与四半区沙盘-可行性报告-20261025.md §3）：
 * - Law 10.1.1/10.1.2、11.1.1/11.1.2：发球方本局得分为偶数（含 0）从右发球区
 *   发球，奇数从左发球区发球；
 * - Law 9.1.3、11.1.4：接发球员站在与发球员斜对角的发球区——同名半区
 *   （双方各自的"右"对"右"、"左"对"左"）在屏幕俯视图上恰呈对角；
 * - Law 11.1.5、11.3.1：己方处于发球状态且得分时，同一发球员换到另一发球区
 *   继续发球（即两人互换左右半区）；接发方得分不换位，只取得发球权。
 *   由此「每侧一个锚点（当前谁站本方右发球区）+ 比分奇偶」即可完整推导全部
 *   站位，发球次序循环（Law 11.4）作为不变量自动涌现（见单测）；
 * - Law 9.5：发球瞬间同伴可站己方场区任意位置——沙盘中同伴半区仅是习惯站位提示；
 * - Law 8：换边只交换网两侧场地，不导致队员互换左右半区（锚点随参赛方走）。
 *
 * 坐标约定：
 * - side 'left'/'right' 一律为屏幕坐标（当前屏幕左/右侧参赛方），
 *   与 use-scoreboard-state 的快照坐标一致；事件/runtime 坐标由状态机负责镜像；
 * - 半区名 'right'/'left' 为该侧选手**自身视角**的右/左发球区；
 * - 屏幕俯视映射（可行性报告 D6）：左队面朝右，其右发球区在屏幕左下；
 *   右队面朝左，其右发球区在屏幕右上——同名半区天然呈斜对角。
 */

/** 屏幕槽位自上而下的半区名（本方视角）。 */
export const SCREEN_SLOT_COURTS = {
  left: ['left', 'right'],
  right: ['right', 'left'],
}

export const COURT_COURT_LABELS = {
  left: '左发球区',
  right: '右发球区',
}

export const COURT_ROLE_SERVER = 'server'
export const COURT_ROLE_RECEIVER = 'receiver'
export const COURT_ROLE_PARTNER = 'partner'

const COURT_RULE_HINTS = {
  singles: '发球方偶数分在右发球区发球 · 单打发球区长而窄',
  doubles: '发球方偶数分在右发球区发球 · 双打发球区短而宽',
}

/** 发球方本局分数对应的发球半区名：偶数（含 0）右，奇数左（Law 10.1/11.1）。 */
export function serverCourtName(score) {
  return Number(score || 0) % 2 === 0 ? 'right' : 'left'
}

/** 归一化成员名单：字符串数组，去空白、过滤空项、最多取 2 人。 */
export function normalizeCourtMembers(raw) {
  if (!Array.isArray(raw)) return []
  return raw
    .map((item) => String(item == null ? '' : item).trim())
    .filter(Boolean)
    .slice(0, 2)
}

/** 锚点归一化：只有 0/1 合法。 */
export function normalizeAnchorIndex(value) {
  return Number(value) === 1 ? 1 : 0
}

/** 双打站位数据是否齐备（模式双打 + 双方各恰好 2 名成员）。 */
export function isDoublesCourtReady(court) {
  if (!court || court.mode !== 'doubles') return false
  return normalizeCourtMembers(court.leftMembers).length === 2
    && normalizeCourtMembers(court.rightMembers).length === 2
}

/**
 * 一分落地后的锚点转移（Law 11.1.5 / 11.3.1）：
 * 发球方得分 → 该侧两人互换左右半区（锚点翻转，同一发球员换区继续发）；
 * 接发方得分 → 双方都不换位（只转移发球权）。
 */
export function nextCourtAnchorsAfterScore({ scoringSide, serveSide, leftAnchor, rightAnchor }) {
  const anchors = {
    left: normalizeAnchorIndex(leftAnchor),
    right: normalizeAnchorIndex(rightAnchor),
  }
  if (scoringSide !== 'left' && scoringSide !== 'right') return anchors
  if (serveSide !== scoringSide) return anchors
  anchors[scoringSide] = 1 - anchors[scoringSide]
  return anchors
}

/**
 * 由当前局面推导四半区视图（纯函数，页面用 computed 包裹调用）。
 *
 * 输入（屏幕坐标）：
 * - mode 'singles'|'doubles'、placed（双打初始站位是否已确认）
 * - leftMembers/rightMembers（双打成员名，长度 2）、leftAnchor/rightAnchor（0|1，
 *   站在本方右发球区的成员下标）
 * - serveSide、leftScore/rightScore（当前局）
 * - leftTeamName/rightTeamName（参赛方显示名，单打视图与降级视图用）
 *
 * 输出：
 * - degraded：双打但站位数据缺失/未录入（旧比赛、恢复缺 pos）→ 页面只显示组合名
 * - halves.{left,right}.slots：自上而下两个槽位 { court, label, player, role }
 *   role：server（发球员，强高亮）/ receiver（接发球员，次高亮）/ partner（同伴，
 *   弱化）/ null（单打的空槽或降级态）
 * - serverSide / serverCourt / ruleHint
 */
export function deriveCourtView(state) {
  const mode = state?.mode === 'doubles' ? 'doubles' : 'singles'
  const serveSide = state?.serveSide === 'right' ? 'right' : 'left'
  const leftScore = Number(state?.leftScore || 0)
  const rightScore = Number(state?.rightScore || 0)
  const serverCourt = serverCourtName(serveSide === 'left' ? leftScore : rightScore)
  const leftTeamName = String(state?.leftTeamName || '左队')
  const rightTeamName = String(state?.rightTeamName || '右队')

  if (mode === 'singles') {
    return {
      mode,
      degraded: false,
      serverSide: serveSide,
      serverCourt,
      ruleHint: COURT_RULE_HINTS.singles,
      halves: {
        left: buildSinglesHalf('left', leftTeamName, serveSide, serverCourt),
        right: buildSinglesHalf('right', rightTeamName, serveSide, serverCourt),
      },
    }
  }

  const ready = isDoublesCourtReady(state) && !!state?.placed
  if (!ready) {
    return {
      mode,
      degraded: true,
      serverSide: serveSide,
      serverCourt,
      ruleHint: COURT_RULE_HINTS.doubles,
      halves: null,
    }
  }

  const leftMembers = normalizeCourtMembers(state.leftMembers)
  const rightMembers = normalizeCourtMembers(state.rightMembers)
  return {
    mode,
    degraded: false,
    serverSide: serveSide,
    serverCourt,
    ruleHint: COURT_RULE_HINTS.doubles,
    halves: {
      left: buildDoublesHalf('left', leftMembers, normalizeAnchorIndex(state.leftAnchor), serveSide, serverCourt),
      right: buildDoublesHalf('right', rightMembers, normalizeAnchorIndex(state.rightAnchor), serveSide, serverCourt),
    },
  }
}

function buildSlot(court, player, role) {
  return {
    court,
    label: COURT_COURT_LABELS[court],
    player: player || '',
    role: role || null,
  }
}

/** 单打：唯一选手落在与发球半区同名的槽（发球方按自身分数奇偶；接发方斜对角同名）。 */
function buildSinglesHalf(side, teamName, serveSide, serverCourt) {
  const slots = SCREEN_SLOT_COURTS[side].map((court) => {
    if (court !== serverCourt) return buildSlot(court, '', null)
    const role = serveSide === side ? COURT_ROLE_SERVER : COURT_ROLE_RECEIVER
    return buildSlot(court, teamName, role)
  })
  return { slots }
}

/** 双打：锚点成员站本方右发球区，另一人站左发球区；发球侧与半区名一致者为发球员。 */
function buildDoublesHalf(side, members, anchor, serveSide, serverCourt) {
  const rightCourtPlayer = members[anchor]
  const leftCourtPlayer = members[1 - anchor]
  const slots = SCREEN_SLOT_COURTS[side].map((court) => {
    const player = court === 'right' ? rightCourtPlayer : leftCourtPlayer
    const role = court === serverCourt
      ? (serveSide === side ? COURT_ROLE_SERVER : COURT_ROLE_RECEIVER)
      : COURT_ROLE_PARTNER
    return buildSlot(court, player, role)
  })
  return { slots }
}

/**
 * 从页面路由参数解析双打成员名单（team-match.vue 以 JSON 数组字符串传递）。
 * 任何异常都归为「解析失败」，页面据此走降级路径，绝不抛错阻断执裁。
 */
export function parseCourtMembersParam(raw) {
  if (!raw) return []
  try {
    const decoded = decodeURIComponent(String(raw))
    const parsed = JSON.parse(decoded)
    return normalizeCourtMembers(parsed)
  } catch (_) {
    return []
  }
}
