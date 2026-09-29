const TOKEN_KEY = 'scoring_admin_token'
const API_PREFIX = '/api/v1'

let unauthorizedHandler = null

export function setUnauthorizedHandler(handler) {
  unauthorizedHandler = handler
}

export function getToken() {
  return localStorage.getItem(TOKEN_KEY) || ''
}

export function setToken(token) {
  if (token) localStorage.setItem(TOKEN_KEY, token)
  else localStorage.removeItem(TOKEN_KEY)
}

export function clearToken() {
  localStorage.removeItem(TOKEN_KEY)
}

function isAuthFailure(message) {
  return /401|登录态已失效|请先登录|无效token/i.test(message || '')
}

export async function apiRequest(path, options = {}) {
  const headers = {
    'Content-Type': 'application/json',
    ...(options.headers || {}),
  }
  if (!options.public && getToken()) {
    headers.Authorization = `Bearer ${getToken()}`
  }

  const response = await fetch(API_PREFIX + path, {
    method: options.method || 'GET',
    headers,
    body: options.body == null ? undefined : JSON.stringify(options.body),
  })

  let body = null
  try {
    body = await response.json()
  } catch (_) {
    // 非 JSON 响应（如网关错误页），走 HTTP 状态码提示
  }

  if (!response.ok) {
    // 后端异常仍返回 ApiResponse 结构，优先用业务 message
    const message = (body && typeof body.code === 'number' && body.code !== 0 && body.message)
      || `HTTP ${response.status}`
    // 401 一定按鉴权失效处理（后端已把过期/伪造 token 统一映射为 HTTP 401）
    if (response.status === 401 || isAuthFailure(message)) {
      clearToken()
      if (unauthorizedHandler) unauthorizedHandler()
    }
    throw new Error(message)
  }

  if (body && body.code === 0) {
    return body.data
  }

  const message = (body && body.message) || '请求失败'
  if (isAuthFailure(message)) {
    clearToken()
    if (unauthorizedHandler) unauthorizedHandler()
  }
  throw new Error(message)
}

export function register(payload) {
  return apiRequest('/auth/register', { method: 'POST', body: payload, public: true })
}

export function passwordLogin(payload) {
  return apiRequest('/auth/password-login', { method: 'POST', body: payload, public: true })
}

export function pcQrCode() {
  return apiRequest('/auth/pc/qr-code', { method: 'POST', public: true })
}

export function pcLoginStatus(ticket) {
  return apiRequest(`/auth/pc/status?ticket=${encodeURIComponent(ticket)}`, { public: true })
}

export function fetchMe() {
  return apiRequest('/users/me')
}

export function fetchCreatedTournaments() {
  return apiRequest('/tournaments/mine/created')
}

export function fetchFavoriteTournaments() {
  return apiRequest('/tournaments/mine/favorites')
}

export function searchTournaments(keyword) {
  return apiRequest(`/tournaments?keyword=${encodeURIComponent(keyword)}`)
}

export function createTournament(payload) {
  return apiRequest('/tournaments', { method: 'POST', body: payload })
}

export function fetchTournamentDivisions(tournamentId) {
  return apiRequest(`/tournaments/${encodeURIComponent(tournamentId)}/divisions`)
}

export function fetchDivisionBracket(tournamentId, divisionId) {
  return apiRequest(`/tournaments/${encodeURIComponent(tournamentId)}/divisions/${encodeURIComponent(divisionId)}/bracket`)
}

/** 手写签表编辑：knockoutSlotOrder 元素为 playerId 字符串，null = 轮空 */
export function updateDivisionDrawSlots(tournamentId, divisionId, knockoutSlotOrder) {
  return apiRequest(`/tournaments/${encodeURIComponent(tournamentId)}/divisions/${encodeURIComponent(divisionId)}/draw-slots`, {
    method: 'PUT',
    body: { knockoutSlotOrder },
  })
}
