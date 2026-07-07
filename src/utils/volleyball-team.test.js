import { describe, it, expect } from 'vitest'
import {
  sortVolleyballMembers,
  captainNameOfTeam,
} from '@/utils/volleyball-team'

describe('sortVolleyballMembers', () => {
  it('should put captain first', () => {
    const members = [
      { name: '张三', jerseyNumber: 3, captain: false },
      { name: '李四', jerseyNumber: 1, captain: true },
      { name: '王五', jerseyNumber: 2, captain: false },
    ]
    const sorted = sortVolleyballMembers(members)
    expect(sorted[0].name).toBe('李四')
    expect(sorted[0].captain).toBe(true)
  })

  it('should sort non-captains by jersey number', () => {
    const members = [
      { name: '张三', jerseyNumber: 5, captain: false },
      { name: '李四', jerseyNumber: 2, captain: false },
      { name: '王五', jerseyNumber: 8, captain: false },
    ]
    const sorted = sortVolleyballMembers(members)
    expect(sorted[0].jerseyNumber).toBe(2)
    expect(sorted[1].jerseyNumber).toBe(5)
    expect(sorted[2].jerseyNumber).toBe(8)
  })

  it('should sort by name when jersey numbers equal', () => {
    const members = [
      { name: '陈六', jerseyNumber: 3, captain: false },
      { name: '张三', jerseyNumber: 3, captain: false },
      { name: '李四', jerseyNumber: 3, captain: false },
    ]
    const sorted = sortVolleyballMembers(members)
    expect(sorted[0].name).toBe('陈六')
    expect(sorted[1].name).toBe('李四')
    expect(sorted[2].name).toBe('张三')
  })

  it('should normalize boolean fields', () => {
    const members = [
      { name: '张三', jerseyNumber: 1, captain: 1, libero: null },
    ]
    const sorted = sortVolleyballMembers(members)
    expect(sorted[0].captain).toBe(true)
    expect(sorted[0].libero).toBe(false)
  })

  it('should handle empty input', () => {
    expect(sortVolleyballMembers([])).toEqual([])
    expect(sortVolleyballMembers(null)).toEqual([])
    expect(sortVolleyballMembers(undefined)).toEqual([])
  })
})

describe('captainNameOfTeam', () => {
  it('should return captain name', () => {
    const team = {
      members: [
        { name: '张三', jerseyNumber: 3, captain: false },
        { name: '李四', jerseyNumber: 1, captain: true },
      ],
    }
    expect(captainNameOfTeam(team)).toBe('李四')
  })

  it('should return dash if no captain', () => {
    const team = {
      members: [
        { name: '张三', jerseyNumber: 3, captain: false },
        { name: '李四', jerseyNumber: 1, captain: false },
      ],
    }
    expect(captainNameOfTeam(team)).toBe('-')
  })

  it('should handle empty team', () => {
    expect(captainNameOfTeam({})).toBe('-')
    expect(captainNameOfTeam(null)).toBe('-')
    expect(captainNameOfTeam(undefined)).toBe('-')
  })
})
