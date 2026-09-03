function getBaseUrl() {
  try {
    if (typeof window !== 'undefined' && typeof document !== 'undefined') return ''
  } catch (_) {
    // noop
  }

  if (import.meta.env.DEV) {
    return import.meta.env.VITE_API_BASE_URL_DEVELOPMENT || ''
  }
  return import.meta.env.VITE_API_BASE_URL || ''
}

const BASE_URL = getBaseUrl()
const REQUEST_TIMEOUT = 10000

function getToken() {
  try {
    return uni.getStorageSync('scoring_token') || ''
  } catch (_) {
    return ''
  }
}

export function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const { silent = false, timeout = REQUEST_TIMEOUT, ...requestOptions } = options
    const finalUrl = BASE_URL + url
    const header = {
      ...(requestOptions.header || {}),
    }

    if (token) {
      header.Authorization = 'Bearer ' + token
    }

    uni.request({
      ...requestOptions,
      url: finalUrl,
      timeout,
      header,
      success(res) {
        if (res.statusCode !== 200) {
          if (!silent) {
            uni.showToast({ title: `HTTP ${res.statusCode}`, icon: 'none' })
          }
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }

        const body = res.data || {}
        if (body.code === 0) {
          resolve(body.data)
          return
        }

        const message = body.message || '请求失败'
        if (!silent) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new Error(message))
      },
      fail(err) {
        const message = err?.errMsg || '网络请求失败'
        console.error('[request] failed', {
          method: requestOptions.method || 'GET',
          url: finalUrl,
          message,
        })
        if (!silent) {
          uni.showToast({ title: '网络请求失败，请检查网络后重试', icon: 'none' })
        }
        reject(new Error(message))
      },
    })
  })
}

export function uploadAvatar(filePath, options = {}) {
  return new Promise((resolve, reject) => {
    const token = getToken()
    const { silent = false, timeout = REQUEST_TIMEOUT } = options
    const finalUrl = BASE_URL + '/api/v1/files/avatars'
    const header = {}

    if (token) {
      header.Authorization = 'Bearer ' + token
    }

    uni.uploadFile({
      url: finalUrl,
      filePath,
      name: 'file',
      header,
      timeout,
      success(res) {
        if (res.statusCode !== 200) {
          if (!silent) {
            uni.showToast({ title: `HTTP ${res.statusCode}`, icon: 'none' })
          }
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }

        let body = {}
        try {
          body = typeof res.data === 'string' ? JSON.parse(res.data) : (res.data || {})
        } catch (_) {
          body = {}
        }

        if (body.code === 0 && body.data?.url) {
          resolve(body.data.url)
          return
        }

        const message = body.message || '头像上传失败'
        if (!silent) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new Error(message))
      },
      fail(err) {
        const message = err?.errMsg || '头像上传失败'
        console.error('[request] avatar upload failed', {
          url: finalUrl,
          message,
        })
        if (!silent) {
          uni.showToast({ title: '头像上传失败，请检查网络后重试', icon: 'none' })
        }
        reject(new Error(message))
      },
    })
  })
}
