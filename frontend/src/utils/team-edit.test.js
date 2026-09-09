import { describe, expect, it } from 'vitest'
import { buildUpdateTeamPayload, isVolleyballSport, validateNewMember } from './team-edit'

describe('isVolleyballSport', () => {
  it('treats sportType 1 as volleyball', () => {
    expect(isVolleyballSport(1)).toBe(true)
    expect(isVolleyballSport('1')).toBe(true)
  })

  it('treats other values as badminton', () => {
    expect(isVolleyballSport(0)).toBe(false)
    expect(isVolleyballSport(null)).toBe(false)
    expect(isVolleyballSport(undefined)).toBe(false)
  })
})

describe('validateNewMember', () => {
  it('requires a name', () => {
    expect(validateNewMember({ name: '  ' }, true)).toBe('请填写队员姓名')
    expect(validateNewMember(null, true)).toBe('请填写队员姓名')
  })

  it('volleyball requires a positive integer jersey number', () => {
    expect(validateNewMember({ name: '张三' }, true)).toBe('请填写有效的球衣号码（正整数）')
    expect(validateNewMember({ name: '张三', jerseyNumber: '' }, true)).toBe('请填写有效的球衣号码（正整数）')
    expect(validateNewMember({ name: '张三', jerseyNumber: 0 }, true)).toBe('请填写有效的球衣号码（正整数）')
    expect(validateNewMember({ name: '张三', jerseyNumber: 7.5 }, true)).toBe('请填写有效的球衣号码（正整数）')
    expect(validateNewMember({ name: '张三', jerseyNumber: '8' }, true)).toBe('')
    expect(validateNewMember({ name: '张三', jerseyNumber: 8, libero: true }, true)).toBe('')
  })

  it('badminton only requires a name', () => {
    expect(validateNewMember({ name: '李四' }, false)).toBe('')
    expect(validateNewMember({ name: '李四', jerseyNumber: null }, false)).toBe('')
  })
})

describe('buildUpdateTeamPayload', () => {
  it('carries name only when renaming', () => {
    expect(buildUpdateTeamPayload({ teamName: '雷暴', rename: false, member: null, volleyball: true }))
      .toEqual({})
    expect(buildUpdateTeamPayload({ teamName: ' 雷暴 ', rename: true, member: null, volleyball: true }))
      .toEqual({ name: '雷暴' })
  })

  it('carries volleyball member with jersey and libero flag', () => {
    expect(buildUpdateTeamPayload({
      teamName: '雷暴',
      rename: true,
      member: { name: ' 王五 ', jerseyNumber: '9', libero: true },
      volleyball: true,
    })).toEqual({
      name: '雷暴',
      addMembers: [{ name: '王五', jerseyNumber: 9, libero: true }],
    })
  })

  it('strips jersey fields for badminton members', () => {
    expect(buildUpdateTeamPayload({
      teamName: '鹰队',
      rename: false,
      member: { name: '赵六', jerseyNumber: 3, libero: true },
      volleyball: false,
    })).toEqual({
      addMembers: [{ name: '赵六' }],
    })
  })

  it('omits empty member silently', () => {
    expect(buildUpdateTeamPayload({ teamName: '鹰队', rename: false, member: { name: '  ' }, volleyball: false }))
      .toEqual({})
  })
})
