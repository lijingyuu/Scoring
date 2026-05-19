/**
 * 全局网络请求工具
 * 基于 uni.request 封装，支持环境适配、响应拦截、统一错误处理
 */

function getBaseUrl() {
  try {
    const info = uni.getSystemInfoSync()
    // H5 环境返回空字符串，走 Vite 代理
    if (info.uniPlatform === 'web') return ''
  } catch (_) { /* fallback */ }
  // 微信小程序等环境直连后端真实 IP
  return 'http://10.4.117.181:8080';
}

const BASE_URL = getBaseUrl()

/**
 * 发起网络请求
 * @param {string} url    请求路径，例如 /api/v1/tournaments
 * @param {object} options 请求选项（method、data、header 等），会透传给 uni.request
 * @returns {Promise<any>} 成功后 resolve(ApiResponse.data)，失败后 reject
 */
export function request(url, options = {}) {
  return new Promise((resolve, reject) => {
    uni.request({
      url: BASE_URL + url,
      ...options,
      success(res) {
        if (res.statusCode !== 200) {
          uni.showToast({ title: '网络异常', icon: 'none' })
          reject(new Error(`HTTP ${res.statusCode}`))
          return
        }

        const body = res.data
        if (body.code === 0) {
          resolve(body.data)
        } else {
          uni.showToast({ title: body.message || '请求失败', icon: 'none' })
          reject(new Error(body.message || '请求失败'))
        }
      },
      fail(err) {
        uni.showToast({ title: '网络请求失败', icon: 'none' })
        reject(err)
      },
    })
  })
}
