import { describe, expect, it } from 'vitest'
import { isDoublesMatch, isDoublesName, splitDoublesName } from './match-card-doubles'

describe('match-card-doubles utilities', () => {
  it('splits names with slash into trimmed two-element array and swallows slash', () => {
    expect(splitDoublesName('诸葛孔明 / 周公瑾')).toEqual(['诸葛孔明', '周公瑾'])
    expect(splitDoublesName('张三/李四')).toEqual(['张三', '李四'])
    expect(splitDoublesName('  王五  /  赵六  ')).toEqual(['王五', '赵六'])
  })

  it('handles singles or missing names gracefully', () => {
    expect(splitDoublesName('林丹')).toEqual(['林丹'])
    expect(splitDoublesName('待定')).toEqual(['待定'])
    expect(splitDoublesName('')).toEqual([])
    expect(splitDoublesName(null)).toEqual([])
  })

  it('detects doubles name accurately', () => {
    expect(isDoublesName('诸葛孔明/周公瑾')).toBe(true)
    expect(isDoublesName('林丹')).toBe(false)
    expect(isDoublesName('待定')).toBe(false)
    expect(isDoublesName('///')).toBe(false)
  })

  it('determines doubles match correctly for individual events', () => {
    // Both sides doubles
    expect(isDoublesMatch({ leftName: '诸葛孔明/周公瑾', rightName: '司马仲达/郭奉孝' })).toBe(true)

    // One side doubles, one side TBD
    expect(isDoublesMatch({ leftName: '诸葛孔明/周公瑾', rightName: '待定' })).toBe(true)
    expect(isDoublesMatch({ leftName: '待定', rightName: '诸葛孔明/周公瑾' })).toBe(true)

    // Singles match
    expect(isDoublesMatch({ leftName: '林丹', rightName: '李宗伟' })).toBe(false)

    // Excluded for team match or volleyball
    expect(isDoublesMatch({ leftName: '北京一队/二队', rightName: '上海联队', isTeamMatch: true })).toBe(false)
    expect(isDoublesMatch({ leftName: '排球A/B组', rightName: '排球C队', isVolleyball: true })).toBe(false)
  })
})
