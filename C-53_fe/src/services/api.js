const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || '/api/v1').replace(/\/$/, '')
const ACCESS_TOKEN_KEY = 'c53-access-token'
const REFRESH_TOKEN_KEY = 'c53-refresh-token'

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

export function saveSession(session, remember = true) {
  const storage = remember ? window.localStorage : window.sessionStorage
  const token = session?.token || session
  clearSession()
  if (token?.accessToken) storage.setItem(ACCESS_TOKEN_KEY, token.accessToken)
  if (token?.refreshToken) storage.setItem(REFRESH_TOKEN_KEY, token.refreshToken)
}

export function clearSession() {
  window.localStorage.removeItem(ACCESS_TOKEN_KEY)
  window.localStorage.removeItem(REFRESH_TOKEN_KEY)
  window.sessionStorage.removeItem(ACCESS_TOKEN_KEY)
  window.sessionStorage.removeItem(REFRESH_TOKEN_KEY)
}

function readToken() {
  return window.localStorage.getItem(ACCESS_TOKEN_KEY) || window.sessionStorage.getItem(ACCESS_TOKEN_KEY) || ''
}

let refreshRequest = null

async function refreshSession() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) throw new ApiError('登录状态已失效，请重新登录', 401, 'TOKEN_EXPIRED')
  if (refreshRequest) return refreshRequest

  refreshRequest = (async () => {
    const response = await fetch(`${API_BASE_URL}/auth/refresh`, {
      method: 'POST',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json; charset=utf-8' },
      body: JSON.stringify({ refreshToken }),
    })
    const payload = await response.json().catch(() => null)
    if (!response.ok) {
      if (response.status === 401 || response.status === 403) clearSession()
      const fallbackMessage = response.status === 503 ? '认证服务暂时不可用，请稍后重试' : '登录状态已失效，请重新登录'
      const fallbackCode = response.status === 503 ? 'SERVICE_UNAVAILABLE' : 'TOKEN_EXPIRED'
      throw new ApiError(payload?.message || fallbackMessage, response.status, payload?.code || fallbackCode, payload?.details)
    }
    const session = payload?.data ?? payload
    saveSession(session, Boolean(window.localStorage.getItem(REFRESH_TOKEN_KEY)))
    return getAccessToken()
  })().finally(() => { refreshRequest = null })

  return refreshRequest
}

async function request(path, options = {}, allowRefresh = true) {
  const headers = new Headers(options.headers || {})
  const token = readToken()
  if (token) headers.set('Authorization', `Bearer ${token}`)
  if (options.body && !(options.body instanceof FormData)) headers.set('Content-Type', 'application/json; charset=utf-8')
  headers.set('Accept', 'application/json')

  let response
  try {
    response = await fetch(`${API_BASE_URL}${path}`, { ...options, headers })
  } catch {
    throw new ApiError('无法连接后端服务，请检查接口地址和服务状态')
  }

  if (response.status === 401 && allowRefresh && path !== '/auth/refresh' && getRefreshToken()) {
    const accessToken = await refreshSession()
    headers.set('Authorization', `Bearer ${accessToken}`)
    return request(path, { ...options, headers }, false)
  }

  if (response.status === 204) return null
  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json') ? await response.json() : null
  if (!response.ok) {
    throw new ApiError(payload?.message || `请求失败（${response.status}）`, response.status, payload?.code, payload?.details)
  }
  return payload?.data ?? payload
}

function queryString(params = {}) {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') search.set(key, String(value))
  })
  const value = search.toString()
  return value ? `?${value}` : ''
}

export const api = {
  login: (body) => request('/auth/login', { method: 'POST', body: JSON.stringify(body) }),
  loginByCode: (body) => request('/auth/login/code', { method: 'POST', body: JSON.stringify(body) }),
  sendVerificationCode: (body) => request('/auth/verification-codes', { method: 'POST', body: JSON.stringify(body) }),
  register: (body) => request('/auth/register', { method: 'POST', body: JSON.stringify(body) }),
  checkUsername: (username) => request(`/auth/username-availability?username=${encodeURIComponent(username)}`, {}, false),
  refreshToken: (refreshToken) => request('/auth/refresh', { method: 'POST', body: JSON.stringify({ refreshToken }) }),
  resetPassword: (body) => request('/auth/password/reset', { method: 'POST', body: JSON.stringify(body) }),
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
    commentsOpen: false,
    reply: '',
    time: post.displayTime || post.createdAt || '',
    date: created ? `${String(created.getMonth() + 1).padStart(2, '0')}.${String(created.getDate()).padStart(2, '0')}` : '',
    year: created ? String(created.getFullYear()) : '',
  }
}

export function normalizeList(payload, normalizer = (value) => value) {
  const items = Array.isArray(payload) ? payload : payload?.items || []
  return items.map(normalizer)
}

export function apiBaseUrl() {
  return API_BASE_URL
}
