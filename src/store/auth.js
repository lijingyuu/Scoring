import { reactive } from 'vue'
import { request } from '@/utils/request'

const TOKEN_KEY = 'scoring_token'

const state = reactive({
  token: '',
  profile: null,
  profileCompleted: false,
  loading: false,
  popupVisible: false,
  nickname: '',
  avatarUrl: '',
})

let ensureAuthPromise = null
let requireProfilePromise = null
let resolveRequireProfile = null
let rejectRequireProfile = null

function loadToken() {
  if (!state.token) {
    state.token = uni.getStorageSync(TOKEN_KEY) || ''
  }
  return state.token
}

function resetProfileState() {
  state.profile = null
  state.profileCompleted = false
  state.nickname = ''
  state.avatarUrl = ''
}

function setToken(token) {
  state.token = token || ''
  if (state.token) {
    uni.setStorageSync(TOKEN_KEY, state.token)
    return
  }
  uni.removeStorageSync(TOKEN_KEY)
  resetProfileState()
}

function shouldResetToken(message) {
  return /401|登录态已失效|请先登录|无效token|用户不存在/i.test(message || '')
}

async function fetchProfile() {
  if (!loadToken()) return null

  try {
    const profile = await request('/api/v1/users/me', { method: 'GET', silent: true })
    state.profile = profile || null
    state.profileCompleted = !!profile?.profileCompleted
    state.nickname = profile?.nickname || ''
    state.avatarUrl = profile?.avatarUrl || ''
    return profile
  } catch (error) {
    if (shouldResetToken(error?.message)) {
      setToken('')
    }
    throw error
  }
}

export { fetchProfile }

export async function ensureAuth() {
  if (loadToken()) return state.token
  if (ensureAuthPromise) return ensureAuthPromise

  ensureAuthPromise = new Promise((resolve, reject) => {
    uni.login({
      provider: 'weixin',
      success: async (loginRes) => {
        if (!loginRes.code) {
          ensureAuthPromise = null
          reject(new Error('微信登录失败：未获取到 code'))
          return
        }

        try {
          const data = await request('/api/v1/auth/wechat-login', {
            method: 'POST',
            data: { code: loginRes.code },
            silent: true,
          })

          if (!data?.token) {
            throw new Error('登录失败：后端没有返回 token')
          }

          setToken(data.token)
          state.profileCompleted = !!data.profileCompleted
          resolve(state.token)
        } catch (error) {
          setToken('')
          reject(error)
        } finally {
          ensureAuthPromise = null
        }
      },
      fail: (error) => {
        ensureAuthPromise = null
        reject(new Error(error?.errMsg || '微信登录失败'))
      },
    })
  })

  return ensureAuthPromise
}

export async function requireProfile() {
  await ensureAuth()

  if (!state.profile) {
    try {
      await fetchProfile()
    } catch (error) {
      if (shouldResetToken(error?.message)) {
        await ensureAuth()
        await fetchProfile()
      } else {
        throw error
      }
    }
  }

  if (state.profileCompleted) return state.profile
  if (requireProfilePromise) return requireProfilePromise

  state.popupVisible = true
  requireProfilePromise = new Promise((resolve, reject) => {
    resolveRequireProfile = resolve
    rejectRequireProfile = reject
  })
  return requireProfilePromise
}

export async function submitProfile() {
  if (!state.nickname.trim()) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  if (!state.avatarUrl) {
    uni.showToast({ title: '请选择头像', icon: 'none' })
    return
  }

  state.loading = true
  try {
    const profile = await request('/api/v1/auth/profile', {
      method: 'POST',
      data: {
        nickname: state.nickname.trim(),
        avatarUrl: state.avatarUrl,
      },
    })
    state.profile = profile || null
    state.profileCompleted = !!profile?.profileCompleted
    state.popupVisible = false
    if (resolveRequireProfile) {
      resolveRequireProfile(profile)
    }
  } catch (error) {
    if (rejectRequireProfile) {
      rejectRequireProfile(error)
    }
    throw error
  } finally {
    state.loading = false
    requireProfilePromise = null
    resolveRequireProfile = null
    rejectRequireProfile = null
  }
}

export function closeProfilePopup() {
  state.popupVisible = false
  if (rejectRequireProfile) {
    rejectRequireProfile(new Error('你取消了资料补全'))
  }
  requireProfilePromise = null
  resolveRequireProfile = null
  rejectRequireProfile = null
}

export async function bootstrapAuth() {
  loadToken()
  if (!state.token) {
    return
  }

  try {
    await fetchProfile()
  } catch (_) {
    // noop
  }
}

export const authState = state
