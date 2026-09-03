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

  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }

  const body = await response.json()
  if (body.code === 0) {
    return body.data
  }

  const message = body.message || '请求失败'
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
