import { describe, it, expect, beforeEach } from 'vitest'
import { setupFuzzerEnvironment } from './fuzzer-env'
import { useScoreboardState } from './use-scoreboard-state'
import { runtimeToSnapshot } from './badminton-recovery'
import { deriveCourtView } from './court-position'

/**
 * 四半区站位与状态机集成测试：
 * 锚点转移（Law 11.1.5/11.3.1）、开局录入、纠错交换、撤销回滚、
 * 换边镜像（屏幕坐标）、事件 runtime 原始坐标、恢复闭环与旧数据降级。
 */

function createCourtScoreboard() {
  setupFuzzerEnvironment()
  const actions = []
  const scoreboard = useScoreboardState({
    onAction: (descriptor) => actions.push(descriptor),
  })
  scoreboard.matchId.value = 'court_test_match'
  scoreboard.leftTeam.value = '甲组'
  scoreboard.rightTeam.value = '乙组'
  return { scoreboard, actions }
}

function makeDoubles(scoreboard) {
  const applied = scoreboard.applyCourtContext({
    mode: 'doubles',
    leftMembers: ['甲1', '甲2'],
    rightMembers: ['乙1', '乙2'],
  })
  expect(applied).toBe(true)
}

function viewOf(scoreboard) {
  return deriveCourtView({
    mode: scoreboard.courtMode.value,
    placed: scoreboard.courtPlaced.value,
    leftMembers: scoreboard.leftCourtMembers.value,
    rightMembers: scoreboard.rightCourtMembers.value,
    leftAnchor: scoreboard.leftAnchorIndex.value,
    rightAnchor: scoreboard.rightAnchorIndex.value,
    serveSide: scoreboard.serveSide.value,
    leftScore: scoreboard.leftScore.value,
    rightScore: scoreboard.rightScore.value,
    leftTeamName: scoreboard.leftTeam.value,
    rightTeamName: scoreboard.rightTeam.value,
  })
}

describe('applyCourtContext 与开局录入', () => {
  it('注入双打上下文后进入待录入状态，确认后落 court_setup 事件', () => {
    const { scoreboard, actions } = createCourtScoreboard()
    makeDoubles(scoreboard)

    expect(scoreboard.isDoublesCourt.value).toBe(true)
    expect(scoreboard.courtPlacementPending.value).toBe(true)
    expect(viewOf(scoreboard).degraded).toBe(true)

    const ok = scoreboard.setupDoublesCourt({ serveSide: 'right', leftAnchor: 1, rightAnchor: 0 })
    expect(ok).toBe(true)
    expect(scoreboard.courtPlacementPending.value).toBe(false)
    expect(scoreboard.serveSide.value).toBe('right')
    expect(scoreboard.leftAnchorIndex.value).toBe(1)

    const setupActions = actions.filter((item) => item.action === 'court_setup')
    expect(setupActions).toHaveLength(1)
    expect(setupActions[0].runtime.court.mode).toBe('doubles')
    expect(setupActions[0].runtime.court.placed).toBe(true)
  })

  it('非法参数被拒绝：缺发球方 / 未注入成员 / 重复录入', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    expect(scoreboard.setupDoublesCourt({ serveSide: 'middle' })).toBe(false)
    expect(scoreboard.setupDoublesCourt({})).toBe(false)

    const bare = createCourtScoreboard()
    expect(bare.scoreboard.setupDoublesCourt({ serveSide: 'left' })).toBe(false)

    scoreboard.setupDoublesCourt({ serveSide: 'left' })
    expect(scoreboard.setupDoublesCourt({ serveSide: 'right' })).toBe(false)
  })

  it('已有恢复站位时路由上下文不覆盖（只补位）', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 1, rightAnchor: 1 })
    // 换设备恢复后再带路由参数：锚点不得被冲掉
    expect(scoreboard.applyCourtContext({
      mode: 'doubles',
      leftMembers: ['甲1', '甲2'],
      rightMembers: ['乙1', '乙2'],
    })).toBe(false)
    expect(scoreboard.leftAnchorIndex.value).toBe(1)
    expect(scoreboard.courtPlaced.value).toBe(true)
  })
})

describe('加分锚点转移（Law 11.1.5 / 11.3.1）', () => {
  it('发球方得分翻转锚点；接发方得分不翻转', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })

    scoreboard.addScore('left')
    expect(scoreboard.leftAnchorIndex.value).toBe(1)

    scoreboard.addScore('left')
    expect(scoreboard.leftAnchorIndex.value).toBe(0)

    scoreboard.addScore('right')
    expect(scoreboard.leftAnchorIndex.value).toBe(0)
    expect(scoreboard.rightAnchorIndex.value).toBe(0)
    expect(scoreboard.serveSide.value).toBe('right')
  })

  it('0:0 右区成员发球：左发右接，接发球员斜对角（同名半区）', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })

    const view = viewOf(scoreboard)
    const leftServer = view.halves.left.slots.find((slot) => slot.role === 'server')
    const rightReceiver = view.halves.right.slots.find((slot) => slot.role === 'receiver')
    expect(leftServer.player).toBe('甲1')
    expect(leftServer.court).toBe('right')
    expect(rightReceiver.player).toBe('乙1')
    expect(rightReceiver.court).toBe('right')
  })

  it('未录入站位（降级态）时加分不产生锚点副作用', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    // 有进度的旧比赛：不弹录入框，加分正常、锚点不动
    scoreboard.addScore('left')
    expect(scoreboard.leftAnchorIndex.value).toBe(0)
    expect(scoreboard.courtPlacementPending.value).toBe(false)
  })
})

describe('撤销与站位纠错', () => {
  it('撤销随快照回滚锚点', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })
    scoreboard.addScore('left')
    expect(scoreboard.leftAnchorIndex.value).toBe(1)
    scoreboard.undo()
    expect(scoreboard.leftScore.value).toBe(0)
    expect(scoreboard.leftAnchorIndex.value).toBe(0)
    expect(scoreboard.courtPlaced.value).toBe(true)
  })

  it('swapCourtPlayers 交换指定侧锚点并落 court_swap 事件', () => {
    const { scoreboard, actions } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })

    expect(scoreboard.swapCourtPlayers('left')).toBe(true)
    expect(scoreboard.leftAnchorIndex.value).toBe(1)
    expect(scoreboard.swapCourtPlayers('bad')).toBe(false)
    const swaps = actions.filter((item) => item.action === 'court_swap')
    expect(swaps).toHaveLength(1)
    expect(swaps[0].courtSide).toBe('left')
    expect(swaps[0].runtime.court.leftAnchor).toBe(1)
  })

  it('单打挑边：第一局第一分前可改首发球方，有进度后拒绝', () => {
    const { scoreboard } = createCourtScoreboard()
    expect(scoreboard.setFirstServeSide('right')).toBe(true)
    expect(scoreboard.serveSide.value).toBe('right')
    scoreboard.addScore('right')
    expect(scoreboard.setFirstServeSide('left')).toBe(false)
    expect(scoreboard.serveSide.value).toBe('right')
  })
})

describe('换边镜像（屏幕坐标一致性）', () => {
  it('手动换边后成员/锚点随参赛方镜像，视图玩家跟随', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 1, rightAnchor: 0 })

    scoreboard.switchSides()
    expect(scoreboard.sidesSwapped.value).toBe(true)
    expect(scoreboard.leftCourtMembers.value).toEqual(['乙1', '乙2'])
    expect(scoreboard.rightCourtMembers.value).toEqual(['甲1', '甲2'])
    // 锚点随各自参赛方走：原左锚点 1 现在在屏幕右侧
    expect(scoreboard.rightAnchorIndex.value).toBe(1)
    expect(scoreboard.leftAnchorIndex.value).toBe(0)

    const view = viewOf(scoreboard)
    // 换边后右区站的是甲2（锚点 1 → members[1]）
    const rightRight = view.halves.right.slots.find((slot) => slot.court === 'right')
    expect(rightRight.player).toBe('甲2')
  })

  it('事件 runtime 始终为原始坐标（换边前后甲组恒在 court.leftMembers）', () => {
    const { scoreboard, actions } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })
    scoreboard.addScore('left')
    scoreboard.switchSides()
    scoreboard.addScore('left')

    const lastScore = [...actions].reverse().find((item) => item.action === 'score')
    expect(lastScore.runtime.sidesSwapped).toBe(true)
    // 屏幕左=乙组，但原始坐标 court.leftMembers 恒为甲组
    expect(lastScore.runtime.court.leftMembers).toEqual(['甲1', '甲2'])
    // 甲组得 1 分（原始坐标 leftScore=1）且其锚点已翻转一次
    expect(lastScore.runtime.court.leftAnchor).toBe(1)
    expect(lastScore.runtime.leftScore).toBe(1)
  })
})

describe('恢复闭环（runtime → snapshot → applySnapshot）', () => {
  beforeEach(() => {
    setupFuzzerEnvironment()
  })

  it('换边后的 runtime 经 runtimeToSnapshot 镜像回屏幕坐标并可直接恢复', () => {
    const { scoreboard } = createCourtScoreboard()
    makeDoubles(scoreboard)
    scoreboard.setupDoublesCourt({ serveSide: 'left', leftAnchor: 0, rightAnchor: 0 })
    scoreboard.addScore('left')
    scoreboard.addScore('left')
    scoreboard.switchSides()

    const runtime = scoreboard.buildEventRuntime()
    const snapshot = runtimeToSnapshot(runtime)
    expect(snapshot.court).toBeDefined()
    expect(snapshot.court.leftMembers).toEqual(['乙1', '乙2'])
    expect(snapshot.court.rightMembers).toEqual(['甲1', '甲2'])
    // 甲组得 2 分，其间发球方得分翻转锚点两次回到 0
    expect(snapshot.court.rightAnchor).toBe(0)
    expect(snapshot.sidesSwapped).toBe(true)

    // 用恢复态覆盖一个全新状态机：站位与比分一致
    const second = createCourtScoreboard()
    second.scoreboard.applySnapshot(snapshot)
    expect(second.scoreboard.leftCourtMembers.value).toEqual(['乙1', '乙2'])
    expect(second.scoreboard.leftScore.value).toBe(0)
    expect(second.scoreboard.rightScore.value).toBe(2)
    expect(second.scoreboard.courtPlaced.value).toBe(true)
  })

  it('旧 runtime（无 court）恢复不报错并保持单打默认视图', () => {
    const { scoreboard } = createCourtScoreboard()
    const legacyRuntime = scoreboard.buildEventRuntime()
    expect(legacyRuntime.court.mode).toBe('singles')
    const snapshot = runtimeToSnapshot({ ...legacyRuntime, court: undefined })
    expect(snapshot.court).toBeUndefined()
    expect(() => scoreboard.applySnapshot(snapshot)).not.toThrow()
    expect(scoreboard.courtMode.value).toBe('singles')
  })
})
