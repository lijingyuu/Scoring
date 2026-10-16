import { describe, expect, it } from 'vitest'
import {
  RELAY_RANKING_MODE,
  STANDARD_RANKING_MODE,
  TEAM_RANKING_MODE,
  defaultBaseTemplateForRankingMode,
  getRankingCriterionGroups,
  summarizePriorities,
} from './ranking-options'

describe('ranking custom options', () => {
  it('uses shared options for badminton individual and volleyball', () => {
    const values = getRankingCriterionGroups(STANDARD_RANKING_MODE)
      .flatMap((group) => group.items.map((item) => item.value))

    expect(values).toContain('MATCH_WINS')
    expect(values).toContain('MATCH_WIN_RATE')
    expect(values).toContain('GAME_WINS')
    expect(values).toContain('NET_GAMES')
    expect(values).toContain('GAME_WIN_RATE')
    expect(values).toContain('NET_POINTS')
    expect(values).toContain('POINT_WIN_RATE')
    expect(values).toContain('TWO_WAY_HEAD_TO_HEAD')
    expect(values).toContain('MULTI_HEAD_TO_HEAD')
    expect(values).not.toContain('MATCH_POINTS')
    expect(values).not.toContain('TEAM_ITEM_NET_WINS')
    expect(values).not.toContain('NAME')
  })

  it('uses team-only options for badminton team rankings', () => {
    const values = getRankingCriterionGroups(TEAM_RANKING_MODE)
      .flatMap((group) => group.items.map((item) => item.value))

    expect(values).toContain('TEAM_ITEM_NET_WINS')
    expect(values).toContain('TEAM_ITEM_WIN_RATE')
    expect(values).toContain('TEAM_CHILD_GAME_WINS')
    expect(values).toContain('TEAM_CHILD_NET_GAMES')
    expect(values).toContain('TEAM_CHILD_GAME_WIN_RATE')
    expect(values).toContain('TEAM_CHILD_NET_POINTS')
    expect(values).toContain('TEAM_CHILD_POINT_WIN_RATE')
    expect(values).toContain('MATCH_WIN_RATE')
    expect(values).toContain('TWO_WAY_HEAD_TO_HEAD')
    expect(values).toContain('MULTI_HEAD_TO_HEAD')
    expect(values).not.toContain('MATCH_POINTS')
    expect(values).not.toContain('TEAM_ITEM_WINS')
    expect(values).not.toContain('NAME')
  })

  it('uses relay-only options without team item or game criteria', () => {
    const values = getRankingCriterionGroups(RELAY_RANKING_MODE)
      .flatMap((group) => group.items.map((item) => item.value))

    expect(values).toContain('MATCH_WINS')
    expect(values).toContain('TEAM_CHILD_NET_POINTS')
    expect(values).toContain('TEAM_CHILD_POINT_WIN_RATE')
    expect(values).toContain('TWO_WAY_HEAD_TO_HEAD')
    expect(values).not.toContain('TEAM_ITEM_NET_WINS')
    expect(values).not.toContain('TEAM_ITEM_WIN_RATE')
    expect(values).not.toContain('TEAM_CHILD_GAME_WINS')
    expect(values).not.toContain('TEAM_CHILD_NET_GAMES')
    expect(values).not.toContain('TEAM_CHILD_GAME_WIN_RATE')
  })

  it('keeps custom base templates aligned with ranking mode', () => {
    expect(defaultBaseTemplateForRankingMode(STANDARD_RANKING_MODE, 0)).toBe('BADMINTON_COMMON_1')
    expect(defaultBaseTemplateForRankingMode(STANDARD_RANKING_MODE, 1)).toBe('FIVB_VOLLEYBALL')
    expect(defaultBaseTemplateForRankingMode(TEAM_RANKING_MODE, 0)).toBe('BADMINTON_TEAM_COMMON_1')
    expect(defaultBaseTemplateForRankingMode(RELAY_RANKING_MODE, 0)).toBe('BADMINTON_RELAY_COMMON_1')
  })

  it('summarizes selected priorities for cards', () => {
    expect(summarizePriorities(['MATCH_WINS', 'NET_POINTS', 'HEAD_TO_HEAD']))
      .toBe('胜场数 / 净胜分 / 胜负关系')
    expect(summarizePriorities(['MATCH_WIN_DIFF', 'GAME_WINS']))
      .toBe('净胜场 / 胜局数')
    expect(summarizePriorities(['TEAM_ITEM_NET_WINS', 'TEAM_ITEM_WIN_RATE', 'TEAM_CHILD_POINT_WIN_RATE']))
      .toBe('净胜大分 / 大分得失比 / 小分得失比')
  })
})
