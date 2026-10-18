/**
 * 针对线上部署服务 (https://api.eunomia.cc) 的羽毛球端到端混沌与并发实测脚本。
 *
 * 覆盖：
 * 1. "2026南大羽协破冰活动" (2107360047074291713) 线上只读拓扑、分段规则与轮空签位审计；
 * 2. 裁判登录与密码认证流程 (referee-auth) 恶意/合法行为验证；
 * 3. 多裁判多手机并发冲突排查 (同一比赛抢锁拒绝、同账号换手机接管 kicked、不同场地并行持锁互不干扰)；
 * 4. 退赛链路与结果推送实测 (弃权退赛结算、胜者槽位秒级推送到下一局、下一场立即就绪可裁)；
 * 5. 测试结束后自动将测试赛事归档，保持线上数据整洁。
 */

const BASE_URL = 'https://api.eunomia.cc'
const NAN_DA_TOURNAMENT_ID = '2107360047074291713'

async function request(path, options = {}) {
  const url = `${BASE_URL}${path}`
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }
  const res = await fetch(url, {
    method: options.method || 'GET',
    headers,
    body: options.body ? JSON.stringify(options.body) : undefined,
  })
  const text = await res.text()
  let data
  try {
    data = JSON.parse(text)
  } catch (_) {
    data = text
  }
  return { status: res.status, ok: res.ok, data }
}

function assert(condition, msg) {
  if (!condition) {
    console.error(`❌ [ASSERT_FAIL] ${msg}`)
    throw new Error(msg)
  }
  console.log(`  ✅ ${msg}`)
}

async function main() {
  console.log('===============================================================')
  console.log('🏸 开始执行生产环境 (https://api.eunomia.cc) 实时混沌测试')
  console.log('===============================================================\n')

  // -------------------------------------------------------------
  // 第一阶段：线上"2026南大羽协破冰活动"赛前只读全景审计
  // -------------------------------------------------------------
  console.log('▶ [阶段 1] 审查线上真实比赛：2026南大羽协破冰活动 (ID: ' + NAN_DA_TOURNAMENT_ID + ')')
  const tRes = await request(`/api/v1/tournaments/${NAN_DA_TOURNAMENT_ID}`)
  assert(tRes.ok && tRes.data.code === 0, '成功获取南大破冰比赛详情')
  const tData = tRes.data.data
  console.log(`   赛事名称: ${tData.name}`)
  console.log(`   地点: ${tData.location}`)
  console.log(`   组别数量: ${tData.divisions?.length}`)

  assert(tData.divisions && tData.divisions.length === 5, '包含 5 个组别（男单/女单/男双/女双/混双）')
  for (const div of tData.divisions) {
    console.log(`   - 组别: ${div.name} | 人数: ${div.playerCount} | 轮数: ${div.knockoutRounds} | 签表物化: ${div.knockoutGenerated}`)
    assert(div.knockoutGenerated === true, `组别 ${div.name} 淘汰签表已提前物化`)
  }

  // 重点检查男单组别的特殊分段计分规则
  const msDiv = tData.divisions.find(d => d.name === '男单')
  assert(!!msDiv, '找到男单组别')
  const msBracketRes = await request(`/api/v1/tournaments/${NAN_DA_TOURNAMENT_ID}/divisions/${msDiv.divisionId}/bracket`)
  assert(msBracketRes.ok && msBracketRes.data.code === 0, '男单签表查询成功')
  const msMatches = msBracketRes.data.data.matches
  console.log(`   男单总对战场数: ${msMatches.length}`)
  const r1Matches = msMatches.filter(m => m.roundNum === 1)
  const byeMatches = r1Matches.filter(m => m.status === 2)
  console.log(`   男单首轮总场次: ${r1Matches.length}, 轮空已自动晋级场次: ${byeMatches.length}`)
  assert(byeMatches.length === 9, '男单 23 人 32 签位应恰有 9 个轮空首轮自动完赛 (status=2)')

  // 验证分段规则 (R1-R2 1局21分, R3-R4 3局15分, R5 3局21分)
  const roundRules = msBracketRes.data.data.roundRules || []
  console.log(`   男单分段规则配置数: ${roundRules.length}`)
  assert(roundRules.length === 5, '男单配置了 5 轮分段规则')
  const r1Rule = roundRules.find(r => r.roundNum === 1)
  const r3Rule = roundRules.find(r => r.roundNum === 3)
  const r5Rule = roundRules.find(r => r.roundNum === 5)
  assert(r1Rule?.bestOf === 1 && r1Rule?.pointsToWin === 21, 'R1 为 1 局 21 分')
  assert(r3Rule?.bestOf === 3 && r3Rule?.pointsToWin === 15, 'R3 为 3 局 15 分')
  assert(r5Rule?.bestOf === 3 && r5Rule?.pointsToWin === 21, 'R5 为 3 局 21 分')

  // -------------------------------------------------------------
  // 第二阶段：裁判账号注册与登录认证体系验证
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 2] 模拟裁判注册与登录体系')
  const referees = [
    { username: 'chaos_ref_test_01', password: 'ChaosRef#2026', nickname: '混沌测试裁判01' },
    { username: 'chaos_ref_test_02', password: 'ChaosRef#2026', nickname: '混沌测试裁判02' },
    { username: 'chaos_ref_test_03', password: 'ChaosRef#2026', nickname: '混沌测试裁判03' },
    { username: 'chaos_ref_test_04', password: 'ChaosRef#2026', nickname: '混沌测试裁判04' },
    { username: 'chaos_ref_test_05', password: 'ChaosRef#2026', nickname: '混沌测试裁判05' },
  ]

  for (const ref of referees) {
    // 尝试登录，若不存在则注册
    let loginRes = await request('/api/v1/auth/password-login', {
      method: 'POST',
      body: { username: ref.username, password: ref.password },
    })
    if (!loginRes.ok || loginRes.data.code !== 0) {
      const regRes = await request('/api/v1/auth/register', {
        method: 'POST',
        body: ref,
      })
      if (regRes.ok && regRes.data.code === 0) {
        ref.token = regRes.data.data.token
      } else {
        // 可能已被注册但密码不同，或发生其他情况
        loginRes = await request('/api/v1/auth/password-login', {
          method: 'POST',
          body: { username: ref.username, password: ref.password },
        })
        assert(loginRes.ok && loginRes.data.code === 0, `裁判 ${ref.nickname} 登录成功`)
        ref.token = loginRes.data.data.token
      }
    } else {
      ref.token = loginRes.data.data.token
    }
    assert(!!ref.token, `裁判 ${ref.nickname} 成功持有 JWT Token`)
  }

  // -------------------------------------------------------------
  // 第三阶段：创建专属隔离混沌测试赛事 (避免污染下周真实比赛数据)
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 3] 创建全功能隔离混沌仿真赛事')
  const testTournamentName = `破冰混沌实测_${Date.now().toString().slice(-6)}`
  const refereePassword = '20261006'

  const createReq = {
    name: testTournamentName,
    location: '南大四组团混沌测试馆',
    sportType: 0,
    participantType: 0,
    tournamentType: 0, // 单淘汰
    refereePassword: refereePassword,
    rule: {
      bestOf: 3,
      gamesToWin: 2,
      pointsToWin: 21,
      enableDeuce: true,
      capPoint: 30,
    },
    players: [
      { name: '选手_张三' },
      { name: '选手_李四' },
      { name: '选手_王五' },
      { name: '选手_赵六' },
    ],
  }

  const createRes = await request('/api/v1/tournaments', {
    method: 'POST',
    headers: { Authorization: `Bearer ${referees[0].token}` },
    body: createReq,
  })
  if (!createRes.ok || createRes.data?.code !== 0) {
    console.error('Create error:', createRes.status, createRes.data)
  }
  assert(createRes.ok && createRes.data.code === 0, '仿真赛事创建成功')
  const simTournamentId = createRes.data.data.tournamentId
  console.log(`   仿真赛事 ID: ${simTournamentId}`)

  // -------------------------------------------------------------
  // 第四阶段：裁判输入密码登录核验 (Referee Auth)
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 4] 裁判输入密码核验 (referee-auth) 链路测试')
  
  // 4.1 恶意探针：输错密码应被 400 拒绝且返回友好提示
  const wrongAuthRes = await request(`/api/v1/tournaments/${simTournamentId}/referee-auth`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${referees[1].token}` },
    body: { password: 'wrong_password_xyz' },
  })
  assert(wrongAuthRes.status === 400 && wrongAuthRes.data.message === '裁判密码错误', '输错裁判密码被正确拦截拒绝')

  // 4.2 正确密码授权：裁判 1 ~ 4 均输入正确密码授权
  for (let i = 1; i <= 4; i++) {
    const authRes = await request(`/api/v1/tournaments/${simTournamentId}/referee-auth`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${referees[i].token}` },
      body: { password: refereePassword },
    })
    if (!authRes.ok || authRes.data?.code !== 0) {
      console.error(`Auth failed for ${referees[i].nickname}:`, authRes.status, authRes.data)
    }
    assert(authRes.ok && authRes.data.code === 0 && authRes.data.data.granted === true, `裁判 ${referees[i].nickname} 密码认证成功并获得执裁权 (granted=true)`)
  }

  // -------------------------------------------------------------
  // 第五阶段：多场并发执裁与冲突排查 (重点)
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 5] 4-5场地同时进行多场比赛并发与冲突排查')
  
  const simBracketRes = await request(`/api/v1/tournaments/${simTournamentId}/bracket`, {
    headers: { Authorization: `Bearer ${referees[1].token}` },
  })
  assert(simBracketRes.ok && simBracketRes.data.code === 0, '获取仿真淘汰签表成功')
  const simMatches = simBracketRes.data.data.matches
  const semiMatch1 = simMatches.find(m => m.roundNum === 1 && m.matchIndex === 0)
  const semiMatch2 = simMatches.find(m => m.roundNum === 1 && m.matchIndex === 1)
  const finalMatch = simMatches.find(m => m.roundNum === 2 && m.matchIndex === 0)

  assert(!!semiMatch1 && !!semiMatch2 && !!finalMatch, '4人淘汰赛结构完备 (半决赛1, 半决赛2, 决赛)')

  // 5.1 冲突测试 1：两裁判同时执裁不同场地（1号场与2号场），互不干扰
  console.log('   [测试 5.1] 两名裁判用不同手机同时执裁不同场地...')
  const lockToken1 = 'phone_court_1_' + Date.now()
  const lockToken2 = 'phone_court_2_' + Date.now()

  const [lock1Res, lock2Res] = await Promise.all([
    request(`/api/v1/matches/${semiMatch1.id}/lock`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${referees[1].token}` },
      body: { lockToken: lockToken1 },
    }),
    request(`/api/v1/matches/${semiMatch2.id}/lock`, {
      method: 'POST',
      headers: { Authorization: `Bearer ${referees[2].token}` },
      body: { lockToken: lockToken2 },
    }),
  ])

  console.log('lock1 data:', JSON.stringify(lock1Res.data))
  console.log('lock2 data:', JSON.stringify(lock2Res.data))
  assert(lock1Res.ok && lock1Res.data.data.success === true, '1号场裁判成功锁定半决赛1')
  assert(lock2Res.ok && lock2Res.data.data.success === true, '2号场裁判成功锁定半决赛2')

  // 5.2 冲突测试 2：撞车排查 —— 3号场裁判试图抢占 1号场的半决赛1，应被拒绝
  console.log('   [测试 5.2] 冲突排查：其他手机/裁判尝试抢占已被执裁的场地...')
  const collideLockRes = await request(`/api/v1/matches/${semiMatch1.id}/lock`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${referees[3].token}` },
    body: { lockToken: 'phone_court_3_' + Date.now() },
  })
  assert(collideLockRes.ok && collideLockRes.data.data.success === false, '撞车成功拦截：其他手机无法抢锁正在执裁的场地')
  console.log(`      当前持锁人: ${collideLockRes.data.data.lockedByUserId}, 抢锁被安全拒绝`)

  // 5.3 冲突测试 3：同账号换手机（顶锁机制）
  console.log('   [测试 5.3] 同裁判换备用手机接管比赛（顶锁）...')
  const newPhoneToken = 'phone_court_1_backup_' + Date.now()
  const preemptRes = await request(`/api/v1/matches/${semiMatch1.id}/lock`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${referees[1].token}` },
    body: { lockToken: newPhoneToken },
  })
  assert(preemptRes.ok && preemptRes.data.data.success === true && preemptRes.data.data.kicked === true, '同账号备用手机成功接管，kicked=true 提示旧手机下线')

  // -------------------------------------------------------------
  // 第六阶段：重点排查退赛链路 (Walkover / Retirement)
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 6] 重点排查退赛链路与退赛结算结果一致性')

  // 半决赛2：模拟右侧选手在第二局因伤退赛！
  // 比赛进行中：第一局 21:18 左胜，第二局打到 11:7 时右侧选手退赛
  console.log('   [退赛实测] 半决赛2: 右侧选手打到第二局突发退赛 (retiredSide="right")')
  const retireFinishReq = {
    winnerSide: 'left',
    leftScore: 2,
    rightScore: 0,
    retiredSide: 'right',
    leftGameWins: 2, // 规则契约：退赛时胜方局数补满 gamesToWin
    rightGameWins: 0,
    gameScores: [
      { gameNo: 1, leftScore: 21, rightScore: 18, winnerSide: 'left' },
      { gameNo: 2, leftScore: 11, rightScore: 7, winnerSide: 'left' }, // 发生退赛的未打完局或已打完局
    ],
  }

  const finishSemi2Res = await request(`/api/v1/matches/${semiMatch2.id}/finish`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${referees[2].token}`,
      'X-Match-Lock-Token': lockToken2,
    },
    body: retireFinishReq,
  })
  assert(finishSemi2Res.ok && finishSemi2Res.data.code === 0, '半决赛2退赛完赛提交成功')

  // 检查退赛后的比赛记录详情
  const semi2RecordRes = await request(`/api/v1/matches/${semiMatch2.id}/record`, {
    headers: { Authorization: `Bearer ${referees[2].token}` },
  })
  assert(semi2RecordRes.ok && semi2RecordRes.data.code === 0, '查询退赛比赛战报成功')
  const semi2Record = semi2RecordRes.data.data
  assert(semi2Record.status === 2, '退赛后比赛状态标记为已完赛 (status=2)')
  assert(semi2Record.retiredSide === 'right', 'retiredSide 正确记录为 "right"')
  assert(semi2Record.winnerSide === 'left', '战报胜者方正确判定为左侧 (winnerSide="left")')
  console.log(`      退赛比分展示: ${semi2Record.scoreDisplay}, 胜方: ${semi2Record.winnerSide}`)

  // -------------------------------------------------------------
  // 第七阶段：结果推送排查 (胜者是否立即推送到下一局/决赛)
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 7] 结果推送排查：完赛后下一场对阵槽位秒级更新')

  // 7.1 半决赛2完赛后，立即核查决赛右侧槽位是否已有胜者
  let finalBracketRes = await request(`/api/v1/tournaments/${simTournamentId}/bracket`, {
    headers: { Authorization: `Bearer ${referees[1].token}` },
  })
  let updatedFinalMatch = finalBracketRes.data.data.matches.find(m => m.id === finalMatch.id)
  assert(updatedFinalMatch.rightPlayerId === semiMatch2.leftPlayerId, '半决赛2的胜者（退赛获胜方）已被瞬间推送到决赛 rightPlayerId')
  console.log(`   半决赛2胜者已就位决赛右槽: ${updatedFinalMatch.rightPlayerId}`)

  // 7.2 正常打完半决赛1 (2:0)
  console.log('   正常完赛半决赛1 (21:15, 21:17)...')
  const normalFinishReq = {
    winnerSide: 'left',
    leftScore: 2,
    rightScore: 0,
    leftGameWins: 2,
    rightGameWins: 0,
    gameScores: [
      { gameNo: 1, leftScore: 21, rightScore: 15, winnerSide: 'left' },
      { gameNo: 2, leftScore: 21, rightScore: 17, winnerSide: 'left' },
    ],
  }
  const finishSemi1Res = await request(`/api/v1/matches/${semiMatch1.id}/finish`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${referees[1].token}`,
      'X-Match-Lock-Token': newPhoneToken,
    },
    body: normalFinishReq,
  })
  assert(finishSemi1Res.ok && finishSemi1Res.data.code === 0, '半决赛1完赛提交成功')

  // 7.3 再次核查决赛：决赛两个名额均已就位！
  finalBracketRes = await request(`/api/v1/tournaments/${simTournamentId}/bracket`, {
    headers: { Authorization: `Bearer ${referees[1].token}` },
  })
  updatedFinalMatch = finalBracketRes.data.data.matches.find(m => m.id === finalMatch.id)
  assert(updatedFinalMatch.leftPlayerId === semiMatch1.leftPlayerId, '半决赛1胜者已被瞬间推送到决赛 leftPlayerId')
  assert(updatedFinalMatch.rightPlayerId === semiMatch2.leftPlayerId, '半决赛2胜者稳固在决赛 rightPlayerId')
  assert(updatedFinalMatch.status === 0, '决赛对阵双方全部就位，状态为未开始待执裁 (status=0)')
  console.log(`   决赛对阵完全生成: [${updatedFinalMatch.leftPlayerId}] VS [${updatedFinalMatch.rightPlayerId}]`)

  // 7.4 快速开始决赛执裁
  console.log('   无缝接续：裁判立即锁定并执裁决赛...')
  const finalLockToken = 'phone_final_' + Date.now()
  const finalLockRes = await request(`/api/v1/matches/${finalMatch.id}/lock`, {
    method: 'POST',
    headers: { Authorization: `Bearer ${referees[1].token}` },
    body: { lockToken: finalLockToken },
  })
  assert(finalLockRes.ok && finalLockRes.data.data.success === true, '裁判立即成功锁定决赛，节奏流畅无阻塞')

  // 决胜局交换场地与 Deuce 积分至上限 (21:19, 19:21, 30:29)
  console.log('   决赛激战三局 (决胜局 Deuce 触顶 30:29)...')
  const finalFinishReq = {
    winnerSide: 'left',
    leftScore: 2,
    rightScore: 1,
    leftGameWins: 2,
    rightGameWins: 1,
    gameScores: [
      { gameNo: 1, leftScore: 21, rightScore: 19, winnerSide: 'left' },
      { gameNo: 2, leftScore: 19, rightScore: 21, winnerSide: 'right' },
      { gameNo: 3, leftScore: 30, rightScore: 29, winnerSide: 'left' },
    ],
  }
  const finishFinalRes = await request(`/api/v1/matches/${finalMatch.id}/finish`, {
    method: 'PUT',
    headers: {
      Authorization: `Bearer ${referees[1].token}`,
      'X-Match-Lock-Token': finalLockToken,
    },
    body: finalFinishReq,
  })
  assert(finishFinalRes.ok && finishFinalRes.data.code === 0, '决赛完赛提交成功')

  // 验证赛事最终状态
  const finalTournRes = await request(`/api/v1/tournaments/${simTournamentId}`)
  assert(finalTournRes.ok && finalTournRes.data.data.status === 2, '整场赛事随着决赛终局自动流转为已结束 (status=2)')

  // -------------------------------------------------------------
  // 第八阶段：清理与归档
  // -------------------------------------------------------------
  console.log('\n▶ [阶段 8] 清理与归档测试赛事')
  const archiveRes = await request(`/api/v1/tournaments/${simTournamentId}/archive`, {
    method: 'PUT',
    headers: { Authorization: `Bearer ${referees[0].token}` },
  })
  assert(archiveRes.ok && archiveRes.data.code === 0, '测试赛事已成功归档，保证生产赛事列表整洁')

  console.log('\n===============================================================')
  console.log('🎉 生产环境线上 E2E 真实链路测试全部通过！0 违规，0 异常冲突！')
  console.log('===============================================================')
}

main().catch(err => {
  console.error('\n❌ 运行时捕获严重异常:', err)
  process.exit(1)
})
