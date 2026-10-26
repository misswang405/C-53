// 业务接口访问与响应适配：统一处理认证令牌、错误和页面展示模型。
// API 地址支持通过环境变量覆盖，默认走前端代理的 v1 接口。
const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
// 访问令牌和刷新令牌分开存储，便于按“保持登录”选择存储介质。
const ACCESS_TOKEN_KEY = 'c53-access-token'
const REFRESH_TOKEN_KEY = 'c53-refresh-token'
// 清理会话后，不允许此前在途的刷新或私有响应恢复旧认证上下文。
let sessionVersion = 0

/** 统一接口异常，保留 HTTP 状态、业务错误码和字段校验详情。 */
export class ApiError extends Error {
  constructor(message, status = 0, code = 'NETWORK_ERROR', details = []) {
    super(message)
    this.name = 'ApiError'
    this.status = status
    this.code = code
    this.details = details
  }
}

export function getAccessToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY) || window.sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

export function getRefreshToken() {
  return window.localStorage.getItem(REFRESH_TOKEN_KEY) || window.sessionStorage.getItem(REFRESH_TOKEN_KEY) || ''
}

// 切换存储前清除旧会话，避免本地存储与会话存储同时残留不同账号令牌。
export function saveSession(session, remember = true) {
  clearSession()
  storeSession(session, remember)
}

function storeSession(session, remember) {
  clearStoredSession()
  const storage = remember ? window.localStorage : window.sessionStorage
  const token = session?.token || session
  if (token?.accessToken) storage.setItem(ACCESS_TOKEN_KEY, token.accessToken)
  if (token?.refreshToken) storage.setItem(REFRESH_TOKEN_KEY, token.refreshToken)
}

export function getSessionVersion() {
  return sessionVersion
}

export function clearSession() {
  sessionVersion += 1
  clearStoredSession()
}

function clearStoredSession() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  window.sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  window.sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}

function readToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY) || window.sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

// 合并并发刷新请求，避免同一刷新令牌被多个请求同时轮换。
let refreshRequest = null
let refreshVersion = -1

function requireCurrentSession(version) {
  if (version !== sessionVersion) throw new ApiError('登录状态已变更，请重新操作', 401, 'SESSION_CHANGED')
}

async function refreshSession() {
  const refreshToken = getRefreshToken()
  const version = sessionVersion
  if (!refreshToken) throw new ApiError('登录状态已失效，请重新登录', 401, 'TOKEN_EXPIRED')
  if (refreshRequest && refreshVersion === version) return refreshRequest
  const remember = Boolean(window.localStorage.getItem(REFRESH_TOKEN_KEY))
  refreshVersion = version

  const pending = (async () => {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ refreshToken }),
    })
    const payload = await response.json().catch(() => null)
    requireCurrentSession(version)
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) clearSession()
      const fallbackMessage = response.status === 503 ? '认证服务暂时不可用，请稍后重试' : '登录状态已失效，请重新登录'
      const fallbackCode = response.status === 503 ? 'SERVICE_UNAVAILABLE' : 'TOKEN_EXPIRED'
      throw new ApiError(payload?.message || fallbackMessage, response.status, payload?.code || fallbackCode, payload?.details)
    }
    const session = payload?.data ?? payload
    // 正常轮换不改变会话代次，避免并发业务请求被误判为跨账号响应。
    storeSession(session, remember)
    return getAccessToken()
  })().finally(() => {
    if (refreshRequest === pending) refreshRequest = null
  })
  refreshRequest = pending
  return pending
}

/** 统一发送认证请求；401 时最多刷新并重试一次，防止刷新失败后递归重试。 */
async function request(path, options = {}, allowRefresh = true) {
  const headers = new Headers(options.headers || {})
  const token = readToken()
  const version = sessionVersion
  const guardSession = token && path !== '/auth/password/reset'
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json; charset=utf-8')
  headers.set('Accept', 'application/json')

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  } catch {
    throw new ApiError('无法连接后端服务，请检查接口地址和服务状态')
  }

  if (guardSession) requireCurrentSession(version)
  if (response.status === 401 && allowRefresh && path !== '/auth/refresh' && getRefreshToken()) {
    const accessToken = await refreshSession()
    headers.set('Authorization', `Bearer ${accessToken}`)
    return request(path, { ...options, headers }, false)
  }

  if (response.status === 204) return null
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json') ? await response.json() : null
  if (guardSession) requireCurrentSession(version)
  if (!response.ok) {
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload?.code, payload?.details)
  }
  return payload?.data ?? payload
}

// 仅过滤空值，保留 false 和 0 等有效查询参数。
function queryString(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

// 按认证、发现与发布、媒体互动、用户关系、私信和设置分组封装业务接口。
export const api = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  loginByCode: (body) => request('/auth/login/code', { method: 'POST', body: JSON.stringify(body) }),
  sendVerificationCode: (body) => request('/auth/verification-codes', { method: 'POST', body: JSON.stringify(body) }),
  register: (body) => request('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  checkUsername: (username) => request(`/auth/username-availability?username=${encodeURIComponent(username)}`, {}, false),
  refreshToken: (refreshToken) => request('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  resetPassword: (body) => request('/auth/password/reset', { method: 'POST', body: JSON.stringify(body) }, false),
  logout: (refreshToken) => request('/auth/logout', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  listSavedAccounts: () => request('/auth/accounts'),
  switchAccount: (accountId) => request('/auth/switch', { method: 'POST', body: JSON.stringify({ accountId }) }),

  getCommunitySummary: () => request('/community/summary'),
  getFeed: (params) => request(`/feed${queryString(params)}`),
  getBookmarks: (params) => request(`/me/bookmarks${queryString(params)}`),
  getTopics: () => request('/topics/trending'),
  getRecommendedCreators: () => request('/creators/recommended'),
  getPost: (postId) => request(`/posts/${postId}`),
  createPost: (body) => request('/posts', { method: 'POST', body: JSON.stringify(body) }),
  updatePost: (postId, body) => request(`/posts/${postId}`, { method: 'PATCH', body: JSON.stringify(body) }),
  publishPost: (postId) => request(`/posts/${postId}/publish`, { method: 'POST' }),
  schedulePost: (postId, scheduledAt) => request(`/posts/${postId}/schedule`, { method: 'POST', body: JSON.stringify({ scheduledAt }) }),
  cancelPostSchedule: (postId) => request(`/posts/${postId}/cancel-schedule`, { method: 'POST' }),
  deletePost: (postId) => request(`/posts/${postId}`, { method: 'DELETE' }),

  getMediaConfig: () => request('/media/config', {}, false),
  uploadMedia: (file, mediaType) => {
    const body = new FormData()
    body.append('file', file)
    body.append('mediaType', mediaType)
    return request('/media/upload', { method: 'POST', body })
  },
  requestUploadTicket: (body) => request('/media/upload-ticket', { method: 'POST', body: JSON.stringify(body) }),
  confirmMedia: (mediaId, body) => request(`/media/${mediaId}/confirm`, { method: 'POST', body: JSON.stringify(body || {}) }),
  deleteMedia: (mediaId) => request(`/media/${mediaId}`, { method: 'DELETE' }),
  setPostLike: (postId, active) => request(`/posts/${postId}/like`, { method: active ? 'PUT' : 'DELETE' }),
  setPostBookmark: (postId, active) => request(`/posts/${postId}/bookmark`, { method: active ? 'PUT' : 'DELETE' }),
  getComments: (postId) => request(`/posts/${postId}/comments`),
  createComment: (postId, content) => request(`/posts/${postId}/comments`, { method: 'POST', body: JSON.stringify({ content }) }),
  createReply: (commentId, content) => request(`/comments/${commentId}/replies`, { method: 'POST', body: JSON.stringify({ content }) }),
  recordShare: (postId, channel) => request(`/posts/${postId}/share-events`, { method: 'POST', body: JSON.stringify({ channel }) }),

  getMe: () => request('/auth/me'),
  getMyProfile: () => request('/me/profile'),
  updateMyProfile: (body) => request('/me/profile', { method: 'PATCH', body: JSON.stringify(body) }),
  uploadAvatar: (file) => {
    const body = new FormData()
    body.append('file', file)
    return request('/me/avatar', { method: 'POST', body })
  },
  getMyPosts: (params) => request(`/me/posts${queryString(params)}`),
  getUserProfile: (userId) => request(`/users/${userId}`),
  getUserPosts: (userId, params) => request(`/users/${userId}/posts${queryString(params)}`),
  getFollowers: (params) => request(`/me/followers${queryString(params)}`),
  getFollowing: (params) => request(`/me/following${queryString(params)}`),
  setFollow: (userId, active) => request(`/users/${userId}/follow`, { method: active ? 'PUT' : 'DELETE' }),

  getConversations: () => request('/conversations'),
  getMessages: (conversationId, params) => request(`/conversations/${conversationId}/messages${queryString(params)}`),
  sendMessage: (conversationId, body) => request(`/conversations/${conversationId}/messages`, { method: 'POST', body: JSON.stringify(body) }),
  markConversationRead: (conversationId) => request(`/conversations/${conversationId}/read`, { method: 'PUT' }),
  getNotifications: () => request('/notifications'),
  markAllNotificationsRead: () => request('/notifications/read-all', { method: 'PUT' }),

  getSettings: () => request('/me/settings'),
  updateVisibility: (body) => request('/me/settings/visibility', { method: 'PATCH', body: JSON.stringify(body) }),
  updateSocial: (body) => request('/me/settings/social', { method: 'PATCH', body: JSON.stringify(body) }),
  updateRecommendations: (body) => request('/me/settings/recommendations', { method: 'PATCH', body: JSON.stringify(body) }),
  updateModes: (body) => request('/me/settings/modes', { method: 'PATCH', body: JSON.stringify(body) }),
  getBlockedUsers: () => request('/me/blocked-users'),
  removeBlockedUser: (userId) => request(`/me/blocked-users/${userId}`, { method: 'DELETE' }),
  getAuthorizedApps: () => request('/me/authorized-apps'),
  revokeAuthorizedApp: (authorizationId) => request(`/me/authorized-apps/${authorizationId}`, { method: 'DELETE' }),
  createDataExport: () => request('/me/data-exports', { method: 'POST' }),
}

const tones = ['blue', 'violet', 'coral', 'green', 'gold', 'ink']

function toneFor(value = '') {
  let hash = 0
  for (const char of value) hash = ((hash << 5) - hash + char.charCodeAt(0)) | 0
  return tones[Math.abs(hash) % tones.length]
}

function relationshipFlags(relationship) {
  return {
    followed: relationship === 'FOLLOWING' || relationship === 'MUTUAL',
    followsYou: relationship === 'FOLLOWED_BY' || relationship === 'MUTUAL',
  }
}

/** 将后端用户字段和关系枚举转换为组件统一使用的展示模型。 */
export function normalizeUser(user = {}) {
  const nickname = user.nickname || user.name || ''
  const username = user.username || ''
  return {
    ...user,
    id: user.id || '',
    name: nickname,
    nickname,
    username,
    handle: username ? `@${username.replace(/^@/, '')}` : '',
    initials: user.initials || nickname.slice(0, 1),
    field: user.profession || user.field || '',
    profession: user.profession || user.field || '',
    avatar: user.avatarUrl || user.avatar || '',
    tone: user.tone || toneFor(user.id || username),
    ...relationshipFlags(user.relationship),
  }
}

/** 在用户模型上补齐资料编辑所需默认值，避免组件反复处理空字段。 */
export function normalizeProfile(profile = {}) {
  const user = normalizeUser(profile)
  return {
    ...profile,
    ...user,
    avatar: profile.avatarUrl || profile.avatar || '',
    realName: profile.realName || '',
    gender: profile.gender || 'PRIVATE',
    birthDate: profile.birthDate || '',
    region: profile.region || '',
    city: profile.city || '',
    address: profile.address || '',
    phone: profile.phone || '',
    email: profile.email || '',
    bio: profile.bio || '',
    stats: profile.stats || { postCount: 0, followerCount: 0, followingCount: 0, viewCount: 0 },
  }
}

/** 递归整理评论与回复，使列表和详情使用同一套评论结构。 */
export function normalizeComment(comment = {}) {
  const author = normalizeUser(comment.author)
  return {
    id: comment.id,
    author: author.name,
    initials: author.initials,
    text: comment.content || comment.text || '',
    time: comment.createdAt || '',
    replies: (comment.replies || []).map(normalizeComment),
  }
}

/** 统一动态正文、媒体顺序和互动字段，同时补齐仅用于页面交互的本地状态。 */
export function normalizePost(post = {}) {
  const author = normalizeUser(post.author)
  const topic = post.topic || {}
  const created = post.createdAt ? new Date(post.createdAt) : null
  return {
    ...post,
    id: post.id,
    author: author.name,
    authorId: author.id,
    handle: author.handle,
    initials: author.initials,
    role: author.field,
    tone: author.tone,
    topic: topic.name || post.topicName || '',
    topicId: topic.id || post.topicId || '',
    title: post.title || '',
    body: post.summary || post.body || '',
    detail: Array.isArray(post.content) ? post.content : String(post.content || '').split(/\n{2,}/).filter(Boolean),
    note: post.note || '',
    likes: Number(post.likeCount || 0),
    liked: Boolean(post.likedByMe),
    saved: Boolean(post.bookmarkedByMe),
    commentCount: Number(post.commentCount || 0),
    comments: (post.comments || []).map(normalizeComment),
    media: (post.media || []).map((item, index) => ({
      mediaId: item.mediaId,
      mediaType: item.mediaType,
      url: item.url,
      width: item.width,
      height: item.height,
      durationMs: item.durationMs,
      sortOrder: item.sortOrder ?? index,
    })),
    status: post.status || '',
    scheduledAt: post.scheduledAt || '',
    publishedAt: post.publishedAt || '',
    editedAt: post.editedAt || '',
    version: Number(post.version || 1),
    ownedByMe: Boolean(post.ownedByMe),
    commentsOpen: false,
    reply: '',
    time: post.displayTime || post.createdAt || '',
    date: created ? `${String(created.getMonth() + 1).padStart(2, '0')}.${String(created.getDate()).padStart(2, '0')}` : '',
    year: created ? String(created.getFullYear()) : '',
  }
}

// 兼容直接数组与分页 items 结构，但游标由调用方按需处理。
export function normalizeList(payload, normalizer = (value) => value) {
  const items = Array.isArray(payload) ? payload : payload?.items || []
  return items.map(normalizer)
}

export function apiBaseUrl() {
  return API_BASE_URL
}
