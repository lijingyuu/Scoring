function getBaseUrl() {
  try {
    const info = uni.getSystemInfoSync()
    if (info.uniPlatform === 'web') return ''
  } catch (_) {
    // noop
  }

  const devBaseUrl = import.meta.env.VITE_API_BASE_URL_DEVELOPMENT || ''
  const prodBaseUrl = import.meta.env.VITE_API_BASE_URL || ''
  return devBaseUrl || prodBaseUrl
}

const BASE_URL = getBaseUrl()

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
    const header = {
      ...(options.header || {}),
    }

    if (token) {
      header.Authorization = 'Bearer ' + token
    }

    uni.request({
      url: BASE_URL + url,
      ...options,
      header,
      success(res) {
        if (res.statusCode !== 200) {
          if (!options.silent) {
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
        if (!options.silent) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new Error(message))
      },
      fail(err) {
        const message = err?.errMsg || '网络请求失败'
        if (!options.silent) {
          uni.showToast({ title: message, icon: 'none' })
        }
        reject(new Error(message))
      },
    })
  })
}
