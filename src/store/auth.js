import { reactive } from 'vue'
import { request } from '@/utils/request'

const TOKEN_KEY = 'scoring_token'

function isWebDevMode() {
  try {
    return uni.getSystemInfoSync().uniPlatform === 'web'
  } catch (_) {
    return false
  }
}

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
let autoPromptedProfileToken = ''

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
  autoPromptedProfileToken = ''
  resetProfileState()
}

function shouldResetToken(message) {
  return /401|登录态已失效|请先登录|无效token|用户不存在/i.test(message || '')
}

function applyProfile(profile) {
  state.profile = profile || null
  state.profileCompleted = !!profile?.profileCompleted
  state.nickname = profile?.nickname || ''
  state.avatarUrl = profile?.avatarUrl || ''
}

function promptIncompleteProfileOnce() {
  if (!state.token || state.profileCompleted || state.popupVisible) return
  if (autoPromptedProfileToken === state.token) return

  autoPromptedProfileToken = state.token
  state.popupVisible = true
}

async function fetchProfile() {
  if (!loadToken() && !isWebDevMode()) return null

  try {
    const profile = await request('/api/v1/users/me', { method: 'GET', silent: true })
    applyProfile(profile)
    return profile
  } catch (error) {
    if (shouldResetToken(error?.message)) {
      setToken('')
      if (!isWebDevMode()) {
        await ensureAuth()
        const profile = await request('/api/v1/users/me', { method: 'GET', silent: true })
        applyProfile(profile)
        return profile
      }
    }
    throw error
  }
}

export { fetchProfile }

export async function ensureAuth() {
  if (isWebDevMode()) {
    if (!state.token) {
      state.token = '__web_dev__'
      uni.setStorageSync(TOKEN_KEY, state.token)
    }
    return state.token
  }

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
          promptIncompleteProfileOnce()
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

// 主动引导和具体操作拦截共用同一个资料弹窗；只有拦截具体操作时才创建等待 Promise。
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

export async function guardProfileBeforeAction(message = '请先完善个人资料') {
  try {
    await requireProfile()
    return true
  } catch (error) {
    const title = error?.message === '你取消了资料补全'
      ? message
      : (error?.message || '操作失败')
    uni.showToast({ title, icon: 'none' })
    return false
  }
}

export async function openProfileEditor() {
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

  state.popupVisible = true
}

export async function submitProfile(nickname, avatarUrl) {
  // accept explicit params (from popup local state);
  // fall back to global state for backward compatibility
  const nick = (nickname || state.nickname || '').trim()
  const avatar = avatarUrl || state.avatarUrl || ''

  if (!nick) {
    uni.showToast({ title: '请输入昵称', icon: 'none' })
    return
  }
  if (!avatar) {
    uni.showToast({ title: '请选择头像', icon: 'none' })
    return
  }

  state.loading = true
  try {
    const profile = await request('/api/v1/auth/profile', {
      method: 'POST',
      data: {
        nickname: nick,
        avatarUrl: avatar,
      },
    })
    applyProfile(profile)
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
    promptIncompleteProfileOnce()
  } catch (_) {
    // noop
  }
}

export const authState = state
