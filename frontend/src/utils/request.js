// 统一 base URL 规则：H5（有 window/document）走同源，其余按构建环境取 VITE_API_BASE_URL_DEVELOPMENT / VITE_API_BASE_URL
export function getBaseUrl() {
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

// 401 自动重登处理器：由 store/auth.js 通过 setUnauthorizedHandler 注册。
// 这里用注册式而非静态 import，因为 store/auth.js 已 import 本文件，直接互相 import 会形成循环依赖。
let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

function getToken() {
  try {
    return uni.getStorageSync('scoring_token') || ''
  } catch (_) {
    return ''
  }
}

function extractApiErrorMessage(data) {
  // 非 2xx 时后端仍返回 ApiResponse 结构（{code, message}），
  // 优先用业务 message，避免提示退化为“HTTP 403”。
  if (data && typeof data === 'object' && Number.isInteger(data.code) && data.code !== 0) {
    return data.message || ''
  }
  if (typeof data === 'string' && data) {
    try {
      const parsed = JSON.parse(data)
      if (parsed && Number.isInteger(parsed.code) && parsed.code !== 0) return parsed.message || ''
    } catch (_) {
      // 非 JSON body，走 HTTP 状态码提示
    }
  }
  return ''
}

export function request(url, options = {}) {
  const { silent = false, timeout = REQUEST_TIMEOUT, ...requestOptions } = options
  const finalUrl = BASE_URL + url

  // 抽成内部函数：401 静默重登后需要原样重发一次
  function send(attempted, resolve, reject) {
    const token = getToken()
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
        if (res.statusCode === 401) {
          const message = extractApiErrorMessage(res.data) || '登录态已失效'
          // 仅「首次 + 已注册 handler + 本次带 token」时静默重登（不 toast）并重发一次，防循环
          if (!attempted && unauthorizedHandler && token) {
            Promise.resolve()
              .then(() => unauthorizedHandler())
              .then(() => send(true, resolve, reject))
              .catch(() => reject(new Error(message)))
            return
          }
          reject(new Error(message))
          return
        }

        if (res.statusCode !== 200) {
          const message = extractApiErrorMessage(res.data) || `HTTP ${res.statusCode}`
          if (!silent) {
            uni.showToast({ title: message, icon: 'none' })
          }
          reject(new Error(message))
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
  }

  return new Promise((resolve, reject) => {
    send(false, resolve, reject)
  })
}

export function uploadAvatar(filePath, options = {}) {
  const { silent = false, timeout = REQUEST_TIMEOUT } = options
  const finalUrl = BASE_URL + '/api/v1/files/avatars'

  // 与 request 相同：401 静默重登后重发一次
  function send(attempted, resolve, reject) {
    const token = getToken()
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
        if (res.statusCode === 401) {
          const message = extractApiErrorMessage(res.data) || '登录态已失效'
          if (!attempted && unauthorizedHandler && token) {
            Promise.resolve()
              .then(() => unauthorizedHandler())
              .then(() => send(true, resolve, reject))
              .catch(() => reject(new Error(message)))
            return
          }
          reject(new Error(message))
          return
        }

        if (res.statusCode !== 200) {
          const message = extractApiErrorMessage(res.data) || '头像上传失败'
          if (!silent) {
            uni.showToast({ title: message, icon: 'none' })
          }
          reject(new Error(message))
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
  }

  return new Promise((resolve, reject) => {
    send(false, resolve, reject)
  })
}
