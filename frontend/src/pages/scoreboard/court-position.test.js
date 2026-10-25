import { describe, it, expect } from 'vitest'
import {
  COURT_ROLE_PARTNER,
  COURT_ROLE_RECEIVER,
  COURT_ROLE_SERVER,
  SCREEN_SLOT_COURTS,
  deriveCourtView,
  isDoublesCourtReady,
  nextCourtAnchorsAfterScore,
  normalizeAnchorIndex,
  normalizeCourtMembers,
  parseCourtMembersParam,
  serverCourtName,
} from './court-position'

function baseDoublesState(overrides = {}) {
  return {
    mode: 'doubles',
    placed: true,
    leftMembers: ['A1', 'A2'],
    rightMembers: ['B1', 'B2'],
    leftAnchor: 0,
    rightAnchor: 0,
    serveSide: 'left',
    leftScore: 0,
    rightScore: 0,
    leftTeamName: '甲队',
    rightTeamName: '乙队',
    ...overrides,
  }
}

/** 从视图里找某侧某角色所在槽的半区名。 */
function findSlotByRole(view, side, role) {
  return view.halves[side].slots.find((slot) => slot.role === role)
}

describe('serverCourtName（Law 10.1 / 11.1：偶数右、奇数左）', () => {
  it('0 与偶数分为右发球区，奇数分为左发球区', () => {
    expect(serverCourtName(0)).toBe('right')
    expect(serverCourtName(2)).toBe('right')
    expect(serverCourtName(20)).toBe('right')
    expect(serverCourtName(1)).toBe('left')
    expect(serverCourtName(21)).toBe('left')
  })

  it('非法输入按 0 分处理', () => {
    expect(serverCourtName(undefined)).toBe('right')
    expect(serverCourtName(null)).toBe('right')
  })
})

describe('nextCourtAnchorsAfterScore（Law 11.1.5 / 11.3.1 锚点转移）', () => {
  it('发球方得分 → 该侧锚点翻转（两人互换左右半区）', () => {
    const next = nextCourtAnchorsAfterScore({ scoringSide: 'left', serveSide: 'left', leftAnchor: 0, rightAnchor: 1 })
    expect(next).toEqual({ left: 1, right: 1 })
  })

  it('接发方得分 → 双方锚点都不动（只转移发球权）', () => {
    const next = nextCourtAnchorsAfterScore({ scoringSide: 'right', serveSide: 'left', leftAnchor: 0, rightAnchor: 1 })
    expect(next).toEqual({ left: 0, right: 1 })
  })

  it('非法得分侧原样返回', () => {
    const next = nextCourtAnchorsAfterScore({ scoringSide: 'bad', serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })
    expect(next).toEqual({ left: 0, right: 0 })
  })
})

describe('deriveCourtView 基础视图', () => {
  it('双打 0:0：发球方右区成员发球，接发方右区成员斜对角接发（Law 11.1.1/11.1.4）', () => {
    const view = deriveCourtView(baseDoublesState())
    expect(view.mode).toBe('doubles')
    expect(view.degraded).toBe(false)
    expect(view.serverCourt).toBe('right')

    const server = findSlotByRole(view, 'left', COURT_ROLE_SERVER)
    expect(server.player).toBe('A1')
    expect(server.court).toBe('right')

    const receiver = findSlotByRole(view, 'right', COURT_ROLE_RECEIVER)
    expect(receiver.player).toBe('B1')
    expect(receiver.court).toBe('right')

    const partner = findSlotByRole(view, 'left', COURT_ROLE_PARTNER)
    expect(partner.player).toBe('A2')
    expect(partner.court).toBe('left')
  })

  it('屏幕槽位映射满足斜对角不变量（D6 物理俯视）', () => {
    // 同名半区在屏幕上一侧靠上、另一侧靠下：left 上=左区/下=右区；right 上=右区/下=左区
    expect(SCREEN_SLOT_COURTS.left).toEqual(['left', 'right'])
    expect(SCREEN_SLOT_COURTS.right).toEqual(['right', 'left'])

    for (const score of [0, 1, 2, 5, 20]) {
      for (const serveSide of ['left', 'right']) {
        const state = baseDoublesState({
          leftScore: serveSide === 'left' ? score : 3,
          rightScore: serveSide === 'right' ? score : 3,
          serveSide,
        })
        const view = deriveCourtView(state)
        const serverSlot = findSlotByRole(view, serveSide, COURT_ROLE_SERVER)
        const receiverSide = serveSide === 'left' ? 'right' : 'left'
        const receiverSlot = findSlotByRole(view, receiverSide, COURT_ROLE_RECEIVER)
        // 同名半区（斜对角）：发球员与接发球员必须同名半区
        expect(receiverSlot.court).toBe(serverSlot.court)
        // 且在屏幕上一上一下（行索引相反）
        const serverRow = SCREEN_SLOT_COURTS[serveSide].indexOf(serverSlot.court)
        const receiverRow = SCREEN_SLOT_COURTS[receiverSide].indexOf(receiverSlot.court)
        expect(serverRow + receiverRow).toBe(1)
      }
    }
  })

  it('单打：选手落在与发球半区同名的槽，另一槽为空', () => {
    const view = deriveCourtView({
      mode: 'singles',
      serveSide: 'left',
      leftScore: 3,
      rightScore: 1,
      leftTeamName: '甲',
      rightTeamName: '乙',
    })
    expect(view.mode).toBe('singles')
    expect(view.degraded).toBe(false)
    // 左方 3 分（奇数）→ 左方左发球区
    const server = findSlotByRole(view, 'left', COURT_ROLE_SERVER)
    expect(server.player).toBe('甲')
    expect(server.court).toBe('left')
    const empty = view.halves.left.slots.find((slot) => slot.court === 'right')
    expect(empty.player).toBe('')
    expect(empty.role).toBe(null)
    // 接发方站在同名半区（左区）
    const receiver = findSlotByRole(view, 'right', COURT_ROLE_RECEIVER)
    expect(receiver.player).toBe('乙')
    expect(receiver.court).toBe('left')
  })

  it('双打未录入站位 / 成员缺失 → 降级视图', () => {
    expect(deriveCourtView(baseDoublesState({ placed: false })).degraded).toBe(true)
    expect(deriveCourtView(baseDoublesState({ leftMembers: ['A1'] })).degraded).toBe(true)
    expect(deriveCourtView(baseDoublesState({ rightMembers: [] })).degraded).toBe(true)
    const legacy = deriveCourtView(baseDoublesState({ placed: false }))
    expect(legacy.halves).toBe(null)
  })
})

describe('Law 11.4 发球次序循环涌现（锚点模型的正确性总检验）', () => {
  it('逐分模拟：发球轮转按 A1 → B2 → A2 → B1 循环，且发球员半区恒与分数奇偶一致', () => {
    // 初始：左队 A1 站右区、A2 左区；右队 B1 右区、B2 左区；左队先发。
    // BWF 11.4：发球权依次传递 首发 → 首接的同伴 → 首发的同伴 → 首接 → 循环，
    // 即发球员轮转序列 A1, B2, A2, B1（首发 A1，首接 B1）。
    // 注意：某方的「发球轮」持续到它输掉一分为止——输的那一分仍由原发球员发出。
    let leftAnchor = 0
    let rightAnchor = 0
    let serveSide = 'left'
    const scores = { left: 0, right: 0 }
    const servers = []

    const rally = (winner) => {
      const view = deriveCourtView(baseDoublesState({
        leftAnchor,
        rightAnchor,
        serveSide,
        leftScore: scores.left,
        rightScore: scores.right,
      }))
      const serverSlot = findSlotByRole(view, serveSide, COURT_ROLE_SERVER)
      // Law 11.1.6 不变量：每一轮发球都从与发球方当前分数对应的发球区发出
      expect(serverSlot.court).toBe(serverCourtName(scores[serveSide]))
      servers.push(serverSlot.player)
      scores[winner] += 1
      const next = nextCourtAnchorsAfterScore({
        scoringSide: winner,
        serveSide,
        leftAnchor,
        rightAnchor,
      })
      leftAnchor = next.left
      rightAnchor = next.right
      serveSide = winner
    }

    rally('left'); rally('left'); rally('left'); rally('left')
    rally('right'); rally('right')
    rally('left'); rally('left')
    rally('right'); rally('right')

    expect(servers).toEqual(['A1', 'A1', 'A1', 'A1', 'A1', 'B2', 'B2', 'A2', 'A2', 'B1'])
  })

  it('同一发球员连续发球时：换区连续性成立（发球方连得分为同一人）', () => {
    let state = baseDoublesState({ leftAnchor: 0, rightAnchor: 0, serveSide: 'left' })
    let anchors = { left: 0, right: 0 }
    let serveSide = 'left'
    const scores = { left: 0, right: 0 }
    const seen = []
    for (let i = 0; i < 6; i++) {
      const view = deriveCourtView({ ...state, leftAnchor: anchors.left, rightAnchor: anchors.right, serveSide, leftScore: scores.left, rightScore: scores.right })
      seen.push(findSlotByRole(view, serveSide, COURT_ROLE_SERVER).player)
      scores.left += 1
      const next = nextCourtAnchorsAfterScore({ scoringSide: 'left', serveSide, leftAnchor: anchors.left, rightAnchor: anchors.right })
      anchors = next
    }
    // 左队连得 6 分：始终 A1 发球（Law 11.3.1），但半区随奇偶交替
    expect(seen).toEqual(['A1', 'A1', 'A1', 'A1', 'A1', 'A1'])
  })
})

describe('输入归一化与路由参数解析', () => {
  it('normalizeCourtMembers 过滤空项并截断到 2 人', () => {
    expect(normalizeCourtMembers([' 甲 ', '', null, '乙', '丙'])).toEqual(['甲', '乙'])
    expect(normalizeCourtMembers(undefined)).toEqual([])
    expect(normalizeCourtMembers('not-array')).toEqual([])
  })

  it('normalizeAnchorIndex 只承认 0/1', () => {
    expect(normalizeAnchorIndex(1)).toBe(1)
    expect(normalizeAnchorIndex('1')).toBe(1)
    expect(normalizeAnchorIndex(2)).toBe(0)
    expect(normalizeAnchorIndex(undefined)).toBe(0)
  })

  it('isDoublesCourtReady 校验模式与双方人数', () => {
    expect(isDoublesCourtReady({ mode: 'doubles', leftMembers: ['a', 'b'], rightMembers: ['c', 'd'] })).toBe(true)
    expect(isDoublesCourtReady({ mode: 'singles', leftMembers: ['a', 'b'], rightMembers: ['c', 'd'] })).toBe(false)
    expect(isDoublesCourtReady({ mode: 'doubles', leftMembers: ['a'], rightMembers: ['c', 'd'] })).toBe(false)
  })

  it('parseCourtMembersParam 解析 JSON 数组字符串，异常归为空', () => {
    const encoded = encodeURIComponent(JSON.stringify(['张三', '李四']))
    expect(parseCourtMembersParam(encoded)).toEqual(['张三', '李四'])
    expect(parseCourtMembersParam('')).toEqual([])
    expect(parseCourtMembersParam('broken json')).toEqual([])
    expect(parseCourtMembersParam(encodeURIComponent('["只有一人"]'))).toEqual(['只有一人'])
  })
})
