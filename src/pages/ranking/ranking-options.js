export const RANKING_CUSTOM_INPUT_PREFIX = 'ranking_custom_input_'
export const RANKING_CUSTOM_RESULT_PREFIX = 'ranking_custom_result_'

export const STANDARD_RANKING_MODE = 'standard'
export const TEAM_RANKING_MODE = 'team'

const CRITERION_LABELS = {
  MATCH_WINS: '胜场数',
  MATCH_WIN_DIFF: '净胜场',
  MATCH_WIN_RATE: '胜负场比',
  GAME_WINS: '胜局数',
  NET_GAMES: '净胜局',
  GAME_WIN_RATE: '胜负局比',
  NET_POINTS: '净胜分',
  POINT_WIN_RATE: '得失分比',
  TWO_WAY_HEAD_TO_HEAD: '两队直胜',
  MULTI_HEAD_TO_HEAD: '多队小循环',
  HEAD_TO_HEAD: '胜负关系',
  TEAM_ITEM_NET_WINS: '净胜大分',
  TEAM_ITEM_WIN_RATE: '大分得失比',
  TEAM_CHILD_GAME_WINS: '胜局数',
  TEAM_CHILD_NET_GAMES: '净胜局',
  TEAM_CHILD_GAME_WIN_RATE: '胜负局比',
  TEAM_CHILD_NET_POINTS: '净胜小分',
  TEAM_CHILD_POINT_WIN_RATE: '小分得失比',
}

const STANDARD_GROUPS = [
  {
    title: '比赛胜负',
    items: [
      { value: 'MATCH_WINS', label: '胜场数' },
      { value: 'MATCH_WIN_DIFF', label: '净胜场' },
      { value: 'MATCH_WIN_RATE', label: '胜负场比' },
    ],
  },
  {
    title: '胜负局',
    items: [
      { value: 'GAME_WINS', label: '胜局数' },
      { value: 'NET_GAMES', label: '净胜局' },
      { value: 'GAME_WIN_RATE', label: '胜负局比' },
    ],
  },
  {
    title: '局内得失分',
    items: [
      { value: 'NET_POINTS', label: '净胜分' },
      { value: 'POINT_WIN_RATE', label: '得失分比' },
    ],
  },
  {
    title: '胜负关系',
    items: [
      { value: 'TWO_WAY_HEAD_TO_HEAD', label: '两队直胜' },
      { value: 'MULTI_HEAD_TO_HEAD', label: '多队小循环' },
    ],
  },
]

const TEAM_GROUPS = [
  {
    title: '团体赛胜负',
    items: [
      { value: 'MATCH_WINS', label: '胜场数' },
      { value: 'MATCH_WIN_DIFF', label: '净胜场' },
      { value: 'MATCH_WIN_RATE', label: '胜负场比' },
    ],
  },
  {
    title: '场内大分',
    items: [
      { value: 'TEAM_ITEM_NET_WINS', label: '净胜大分' },
      { value: 'TEAM_ITEM_WIN_RATE', label: '大分得失比' },
    ],
  },
  {
    title: '场内局',
    items: [
      { value: 'TEAM_CHILD_GAME_WINS', label: '胜局数' },
      { value: 'TEAM_CHILD_NET_GAMES', label: '净胜局' },
      { value: 'TEAM_CHILD_GAME_WIN_RATE', label: '胜负局比' },
    ],
  },
  {
    title: '局内小分',
    items: [
      { value: 'TEAM_CHILD_NET_POINTS', label: '净胜小分' },
      { value: 'TEAM_CHILD_POINT_WIN_RATE', label: '小分得失比' },
    ],
  },
  {
    title: '胜负关系',
    items: [
      { value: 'TWO_WAY_HEAD_TO_HEAD', label: '两队直胜' },
      { value: 'MULTI_HEAD_TO_HEAD', label: '多队小循环' },
    ],
  },
]

export function rankingStorageKey(prefix, key) {
  return prefix + String(key || 'default')
}

export function getRankingCriterionGroups(mode) {
  return mode === TEAM_RANKING_MODE ? TEAM_GROUPS : STANDARD_GROUPS
}

export function getCriterionLabel(value) {
  return CRITERION_LABELS[value] || String(value || '')
}

export function summarizePriorities(priorities) {
  const list = Array.isArray(priorities) ? priorities.filter(Boolean) : []
  if (!list.length) return '点击进入，按先后顺序选择排名指标。'
  return list.map(getCriterionLabel).join(' / ')
}

export function defaultBaseTemplateForRankingMode(mode, sportType = 0) {
  if (mode === TEAM_RANKING_MODE) return 'BADMINTON_TEAM_COMMON_1'
  return Number(sportType) === 1 ? 'FIVB_VOLLEYBALL' : 'BADMINTON_COMMON_1'
}
