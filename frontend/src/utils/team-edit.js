// 查看队伍页「创建者编辑队伍」的纯逻辑：运动类型判断、新队员校验、更新请求体组装。
// 后端约定：仅创建者可改队名/追加队员/修改已有队员姓名号码；不支持增删队伍与删除队员。

export function isVolleyballSport(sportType) {
  return Number(sportType || 0) === 1
}

/**
 * 校验单个待添加队员。
 * 返回错误文案（通过）为空字符串。
 * 排球：姓名必填、球衣号码必填且为正整数、自由人必须带号码。
 * 羽毛球：姓名必填即可（号码/自由人字段忽略）。
 */
export function validateNewMember(member, volleyball) {
  const name = String(member?.name || '').trim()
  if (!name) {
    return '请填写队员姓名'
  }
  if (!volleyball) {
    return ''
  }

  const rawNumber = member?.jerseyNumber
  const jerseyNumber = Number(rawNumber)
  if (rawNumber === null || rawNumber === undefined || rawNumber === '' || !Number.isInteger(jerseyNumber) || jerseyNumber <= 0) {
    return '请填写有效的球衣号码（正整数）'
  }
  if (member?.libero && !jerseyNumber) {
    return '自由人必须填写球衣号码'
  }
  return ''
}

/**
 * 校验单个待修改队员（姓名必填；排球号码必填且为正整数）。
 * 返回错误文案（通过）为空字符串。
 */
export function validateMemberEdit(member, volleyball) {
  const name = String(member?.name || '').trim()
  if (!name) {
    return '请填写队员姓名'
  }
  if (!volleyball) {
    return ''
  }
  const rawNumber = member?.jerseyNumber
  const jerseyNumber = Number(rawNumber)
  if (rawNumber === null || rawNumber === undefined || rawNumber === '' || !Number.isInteger(jerseyNumber) || jerseyNumber <= 0) {
    return '请填写有效的球衣号码（正整数）'
  }
  return ''
}

/**
 * 组装 PUT /tournaments/{id}/teams/{participantId} 的请求体。
 * rename 为 true 时携带队名；member 非空时携带 addMembers；
 * memberUpdate 非空时携带 updateMembers（羽毛球不带号码字段）。
 */
export function buildUpdateTeamPayload({ teamName, rename, member, memberUpdate, volleyball }) {
  const payload = {}
  const name = String(teamName || '').trim()
  if (rename) {
    payload.name = name
  }
  const cleanName = String(member?.name || '').trim()
  if (cleanName) {
    const entry = { name: cleanName }
    if (volleyball) {
      entry.jerseyNumber = Number(member.jerseyNumber)
      entry.libero = !!member.libero
    }
    payload.addMembers = [entry]
  }
  const updateName = String(memberUpdate?.name || '').trim()
  if (memberUpdate?.memberId && updateName) {
    const entry = { memberId: memberUpdate.memberId, name: updateName }
    if (volleyball) {
      entry.jerseyNumber = Number(memberUpdate.jerseyNumber)
    }
    payload.updateMembers = [entry]
  }
  return payload
}
