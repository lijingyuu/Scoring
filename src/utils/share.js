const DEFAULT_SHARE_TITLE = 'Eunomia 赛事管理与记分'
const DEFAULT_SHARE_PATH = '/pages/index/index'

function resolveShareValue(value) {
  return typeof value === 'function' ? value() : value
}

function normalizeSharePath(path) {
  const raw = String(path || DEFAULT_SHARE_PATH).trim()
  if (!raw) return DEFAULT_SHARE_PATH
  return raw.startsWith('/') ? raw : '/' + raw
}

function splitSharePath(path) {
  const normalized = normalizeSharePath(path)
  const queryStart = normalized.indexOf('?')
  if (queryStart < 0) {
    return { path: normalized, query: '' }
  }
  return {
    path: normalized.slice(0, queryStart) || DEFAULT_SHARE_PATH,
    query: normalized.slice(queryStart + 1),
  }
}

function normalizeQuery(query) {
  const raw = String(query || '').trim()
  return raw.startsWith('?') ? raw.slice(1) : raw
}

function buildShareTitle(title) {
  const resolved = String(resolveShareValue(title) || '').trim()
  return resolved || DEFAULT_SHARE_TITLE
}

function buildShareImageUrl(imageUrl) {
  const resolved = String(resolveShareValue(imageUrl) || '').trim()
  return resolved || ''
}

/**
 * 构建「发送给朋友」的分享内容。
 *
 * 注意：onShareAppMessage / onShareTimeline 必须直接在页面 <script setup>
 * 中调用（不能只藏在普通 js 工具里）。uni-app 编译器的 uni:mp-runtime-hooks
 * 插件会静态正则扫描页面编译产物，命中这些标识符后才会给页面注入
 * __runtimeHooks 标志；缺少该标志时微信 Page() 不会注册分享回调，
 * 右上角「发送给朋友 / 分享到朋友圈」会保持置灰。
 */
export function buildShareAppMessage(options = {}) {
  const content = {
    title: buildShareTitle(options.title),
    path: normalizeSharePath(resolveShareValue(options.path)),
  }
  const imageUrl = buildShareImageUrl(options.imageUrl)
  if (imageUrl) content.imageUrl = imageUrl
  return content
}

/**
 * 构建「分享到朋友圈」的内容（单页模式）。
 * 朋友圈不接收 path，只接收 query；自动从 path 中提取 id=... 作为 query。
 */
export function buildShareTimeline(options = {}) {
  const path = normalizeSharePath(resolveShareValue(options.path))
  const query = normalizeQuery(
    resolveShareValue(options.timelineQuery) || splitSharePath(path).query,
  )
  const content = {
    title: buildShareTitle(options.timelineTitle || options.title),
    query,
  }
  const imageUrl = buildShareImageUrl(options.imageUrl)
  if (imageUrl) content.imageUrl = imageUrl
  return content
}
