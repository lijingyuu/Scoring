import { request } from './request'
import { ensureAuth } from '@/store/auth'

export async function requireMatchOperator(matchId) {
  if (!matchId) {
    uni.showToast({ title: '缺少比赛ID', icon: 'none' })
    return false
  }

  try {
    await ensureAuth()
    const canOperate = await request('/api/v1/matches/' + matchId + '/can-operate', { method: 'GET' })
    if (canOperate === true) return true
    uni.showToast({ title: '请先录入裁判身份后再开始执裁', icon: 'none', duration: 2000 })
    return false
  } catch (error) {
    return false
  }
}
