<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import LoginView from './components/auth/LoginView.vue'
import RegisterView from './components/auth/RegisterView.vue'
import ResetPasswordView from './components/auth/ResetPasswordView.vue'
import DiscoverView from './components/discover/DiscoverView.vue'
import FeedView from './components/feed/FeedView.vue'
import ScheduleDialog from './components/feed/ScheduleDialog.vue'
import AppSidebar from './components/layout/AppSidebar.vue'
import AppTopbar from './components/layout/AppTopbar.vue'
import MessagesView from './components/messages/MessagesView.vue'
import PostDetailView from './components/posts/PostDetailView.vue'
import ConnectionsPanel from './components/profile/ConnectionsPanel.vue'
import MemberProfileView from './components/profile/MemberProfileView.vue'
import ProfileEditorView from './components/profile/ProfileEditorView.vue'
import ProfileView from './components/profile/ProfileView.vue'
import SettingsView from './components/settings/SettingsView.vue'
import SharePanel from './components/share/SharePanel.vue'
import {
  api,
  clearSession,
  getAccessToken,
  getRefreshToken,
  normalizeComment,
  normalizeList,
  normalizePost,
  normalizeProfile,
  normalizeUser,
  saveSession,
} from './services/api'
import { views } from './data/navigation'
import { permissionItems, settingsNav, socialOptions, visibilityOptions } from './data/settings'

const people = ref([])
const followers = ref([])
const following = ref([])
const posts = ref([])
const savedPosts = ref([])
const profilePosts = ref([])
const memberPosts = ref([])
const conversations = ref([])
const chatMessages = ref([])
const notifications = ref([])
const topics = ref([])
const savedAccounts = ref([])
const onlineCount = ref(0)
const dataLoading = ref(false)
const dataError = ref('')

const activeView = ref('feed')
const isAuthenticated = ref(Boolean(getAccessToken()))
const activeAccountId = ref('')
const loginForm = ref({ account: '', password: '', code: '', remember: true })
const loginError = ref('')
const passwordVisible = ref(false)
const loginVerificationTarget = ref('')
const loginVerificationId = ref('')
const loginMaskedTarget = ref('')
const loginCodeCountdown = ref(0)
const loginCodeSending = ref(false)
const loginReturnView = ref('feed')
const loginMode = ref('code')
const isCodeLogin = computed(() => loginMode.value === 'code')
const registerForm = ref({ nickname: '', username: '', identifierType: 'PHONE', identifier: '', code: '', password: '', confirmPassword: '', agreed: false })
const registerError = ref('')
const registerPasswordVisible = ref(false)
const registerConfirmPasswordVisible = ref(false)
const registerVerificationTarget = ref('')
const registerVerificationId = ref('')
const registerMaskedTarget = ref('')
const registerCodeCountdown = ref(0)
const registerCodeSending = ref(false)
const resetForm = ref({ identifierType: 'PHONE', identifier: '', code: '', newPassword: '', confirmPassword: '' })
const resetError = ref('')
const resetPasswordVisible = ref(false)
const resetConfirmPasswordVisible = ref(false)
const resetVerificationTarget = ref('')
const resetMaskedTarget = ref('')
const resetCodeCountdown = ref(0)
const resetCodeSending = ref(false)
const feedFilter = ref('为你推荐')
const searchQuery = ref('')
const composerOpen = ref(false)
const draft = ref('')
const draftTopic = ref('独立创作')
const draftId = ref('')
const draftVersion = ref(0)
const draftState = ref('')
const draftRequestId = ref(newClientRequestId())
const mediaItems = ref([])
const uploadInProgress = ref(false)
const publishBusy = ref(false)
const scheduleOpen = ref(false)
const scheduleBusy = ref(false)
const lastDraftSavedAt = ref(0)
let draftSaveTimer = 0
const notificationsOpen = ref(false)
const profileOpen = ref(false)
const mobileNavOpen = ref(false)
const activeConversation = ref(null)
const chatDraft = ref('')
const toast = ref('')
const activePost = ref(null)
const replyingToId = ref(null)
const nestedReply = ref('')
const sharePostTarget = ref(null)
const sharePanelOpen = ref(false)
const shareHost = window.location.host || 'C—53'
const profileConnectionsOpen = ref(false)
const profileConnectionsType = ref('followers')
const connectionSearch = ref('')
const selectedMember = ref(null)
const profileEditorOpen = ref(false)
const birthPickerOpen = ref(false)
const settingsActiveSection = ref('account')
const settingsReady = ref(false)
const permissionStates = ref({ camera: '未授权', photos: '使用时询问', microphone: '未授权', location: '未授权', contacts: '未授权' })
const visibilitySettings = ref({ profile: '公开', posts: '公开', saved: '仅自己可见', following: '好友可见' })
const socialSettings = ref({ messages: '互相关注的人', comments: '所有人', mentions: '我关注的人' })
const recommendationSettings = ref({ content: true, ads: false })
const specialModes = ref({ teen: false, elder: false, private: false })
const blockedListOpen = ref(false)
const blockedUsers = ref([])
const authorizedApps = ref([])
let loginCodeTimer = 0
let registerCodeTimer = 0
let resetCodeTimer = 0
const profileInfo = ref({
  avatar: '',
  nickname: '',
  username: '',
  realName: '',
  gender: 'PRIVATE',
  birthDate: '',
  region: '',
  city: '',
  address: '',
  phone: '',
  email: '',
  bio: '',
  stats: { postCount: 0, followerCount: 0, followingCount: 0, viewCount: 0 },
})
const profileDraft = ref({ ...profileInfo.value })

const visiblePosts = computed(() => {
  let source = activeView.value === 'saved' ? savedPosts.value : posts.value
  if (!searchQuery.value.trim()) return source
  const query = searchQuery.value.trim().toLowerCase()
  return source.filter((post) =>
    [post.author, post.title, post.body, post.topic].some((value) => value.toLowerCase().includes(query)),
  )
})

const profilePublishedPosts = computed(() => {
  return profilePosts.value
})

const activeConnections = computed(() =>
  profileConnectionsType.value === 'followers' ? followers.value : following.value,
)

const filteredConnections = computed(() => {
  const query = connectionSearch.value.trim().toLowerCase()
  if (!query) return activeConnections.value
  return activeConnections.value.filter((person) =>
    [person.name, person.handle, person.field].some((value) => value.toLowerCase().includes(query)),
  )
})

const selectedMemberPosts = computed(() => {
  return memberPosts.value
})

const profileInitial = computed(() => profileInfo.value.nickname.trim().slice(0, 1) || '')

const birthDateParts = computed(() => {
  const [year, month, day] = (profileDraft.value.birthDate || '').split('-').map(Number)
  const now = new Date()
  return {
    year: year || now.getFullYear() - 25,
    month: month || 1,
    day: day || 1,
  }
})

const birthPickerYears = computed(() => {
  const currentYear = new Date().getFullYear()
  return Array.from({ length: 101 }, (_, index) => currentYear - index)
})

const birthPickerDays = computed(() => {
  const { year, month } = birthDateParts.value
  return Array.from({ length: new Date(year, month, 0).getDate() }, (_, index) => index + 1)
})

const formattedBirthDate = computed(() => {
  if (!profileDraft.value.birthDate) return '选择出生日期'
  const { year, month, day } = birthDateParts.value
  return `${year} 年 ${String(month).padStart(2, '0')} 月 ${String(day).padStart(2, '0')} 日`
})

const draftAge = computed(() => {
  if (!profileDraft.value.birthDate) return '—'
  const birth = new Date(profileDraft.value.birthDate)
  const today = new Date()
  let age = today.getFullYear() - birth.getFullYear()
  const beforeBirthday =
    today.getMonth() < birth.getMonth() ||
    (today.getMonth() === birth.getMonth() && today.getDate() < birth.getDate())
  if (beforeBirthday) age -= 1
  return Number.isFinite(age) && age >= 0 ? age : '—'
})

const draftZodiac = computed(() => {
  if (!profileDraft.value.birthDate) return '—'
  const [, monthText, dayText] = profileDraft.value.birthDate.split('-')
  const month = Number(monthText)
  const day = Number(dayText)
  const signs = ['摩羯座', '水瓶座', '双鱼座', '白羊座', '金牛座', '双子座', '巨蟹座', '狮子座', '处女座', '天秤座', '天蝎座', '射手座']
  const boundaries = [20, 19, 21, 20, 21, 22, 23, 23, 23, 24, 23, 22]
  if (!month || !day) return '—'
  return day < boundaries[month - 1] ? signs[month - 1] : signs[month % 12]
})

const currentConversation = computed(() =>
  conversations.value.find((item) => item.id === activeConversation.value),
)
const isAuthView = computed(() => ['login', 'register', 'reset-password'].includes(activeView.value))

function showToast(message) {
  toast.value = message
  window.setTimeout(() => { toast.value = '' }, 2200)
}

// 统一错误文案：后端业务提示和网络错误均为中文，直接展示；
// 前端运行时异常（如英文的 JS 报错）不暴露给用户，改用中文兜底。
function errorText(error, fallback = '操作失败，请稍后重试') {
  const message = typeof error?.message === 'string' ? error.message.trim() : ''
  return /[一-龥]/.test(message) ? message : fallback
}

const feedScopes = { 为你推荐: 'RECOMMENDED', 正在关注: 'FOLLOWING', 最新发布: 'LATEST' }
const visibilityFromApi = { PUBLIC: '公开', FRIENDS: '好友可见', PRIVATE: '仅自己可见' }
const visibilityToApi = { 公开: 'PUBLIC', 好友可见: 'FRIENDS', 仅自己可见: 'PRIVATE' }
const socialFromApi = { EVERYONE: '所有人', FOLLOWING: '我关注的人', MUTUAL: '互相关注的人', NONE: '不允许' }
const socialToApi = { 所有人: 'EVERYONE', 我关注的人: 'FOLLOWING', 互相关注的人: 'MUTUAL', 不允许: 'NONE' }
const genderFromApi = { PRIVATE: '保密', MALE: '男', FEMALE: '女', CUSTOM: '自定义' }
const genderToApi = { 保密: 'PRIVATE', 男: 'MALE', 女: 'FEMALE', 自定义: 'CUSTOM' }

function newClientRequestId() {
  if (window.crypto?.randomUUID) return window.crypto.randomUUID()
  return `${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

function currentTopicId() {
  return topics.value.find((item) => item.name === draftTopic.value)?.id || topics.value[0]?.id || ''
}

function resetComposer() {
  window.clearTimeout(draftSaveTimer)
  draft.value = ''
  mediaItems.value = []
  draftId.value = ''
  draftVersion.value = 0
  draftState.value = ''
  lastDraftSavedAt.value = 0
  draftRequestId.value = newClientRequestId()
  composerOpen.value = false
}

/**
 * 保存草稿。首次保存创建草稿记录，后续按版本号更新；
 * 冲突时不覆盖服务端内容，提示用户刷新。
 */
async function saveDraft(force = false) {
  if (!isAuthenticated.value || publishBusy.value) return null
  const content = draft.value.trim()
  const mediaIds = mediaItems.value.map((item) => item.mediaId).filter(Boolean)
  if (!content && !mediaIds.length) return null
  if (!force && draftId.value && Date.now() - lastDraftSavedAt.value < 15000) return null
  draftState.value = 'saving'
  try {
    let saved
    if (draftId.value) {
      saved = await api.updatePost(draftId.value, {
        content,
        topicId: currentTopicId(),
        mediaIds,
        version: draftVersion.value,
      })
    } else {
      saved = await api.createPost({
        content,
        topicId: currentTopicId(),
        mediaIds,
        status: 'DRAFT',
        clientRequestId: draftRequestId.value,
      })
      draftId.value = saved.id
    }
    draftVersion.value = Number(saved.version || draftVersion.value + 1)
    lastDraftSavedAt.value = Date.now()
    draftState.value = 'saved'
    return draftId.value
  } catch (error) {
    draftState.value = 'error'
    if (error?.status === 409) showToast('草稿已在其他设备更新，请刷新页面后重试')
    return null
  }
}

async function preparePostForSubmit() {
  const content = draft.value.trim()
  const mediaIds = mediaItems.value.map((item) => item.mediaId).filter(Boolean)
  if (draftId.value) {
    const updated = await api.updatePost(draftId.value, {
      content,
      topicId: currentTopicId(),
      mediaIds,
      version: draftVersion.value,
    })
    draftVersion.value = Number(updated.version || draftVersion.value + 1)
    return draftId.value
  }
  const created = await api.createPost({
    content,
    topicId: currentTopicId(),
    mediaIds,
    status: 'DRAFT',
    clientRequestId: draftRequestId.value,
  })
  draftId.value = created.id
  draftVersion.value = Number(created.version || 1)
  return draftId.value
}

function cancelDraft() {
  // 已保存的草稿保留在服务端，可在“我的发布”里继续编辑
  resetComposer()
}

function handleMediaError(message) {
  showToast(message || '媒体处理失败，请稍后再试')
}

function normalizeTopic(topic = {}) {
  return {
    id: topic.id || '',
    name: topic.name || '',
    count: String(topic.discussionCount ?? topic.count ?? 0),
    color: topic.color || '#5965f2',
  }
}

function normalizeConversation(conversation = {}) {
  const participant = normalizeUser(conversation.participant || conversation.user)
  return {
    ...conversation,
    id: conversation.id,
    name: conversation.name || participant.name,
    initials: conversation.initials || participant.initials,
    tone: conversation.tone || participant.tone,
    preview: conversation.lastMessage?.preview || conversation.preview || '',
    time: conversation.lastMessage?.createdAt || conversation.updatedAt || '',
    unread: Number(conversation.unreadCount || 0),
  }
}

function normalizeNotification(notification = {}) {
  return {
    ...notification,
    actor: normalizeUser(notification.actor),
    preview: notification.preview || '',
    createdAt: notification.createdAt || '',
  }
}

function normalizeAccount(account = {}) {
  const user = normalizeUser(account.user || account)
  return { ...user, id: account.accountId || user.id }
}

async function loadFeed() {
  dataLoading.value = true
  dataError.value = ''
  try {
    const payload = await api.getFeed({ scope: feedScopes[feedFilter.value] || 'RECOMMENDED', limit: 20 })
    posts.value = normalizeList(payload, normalizePost)
  } catch (error) {
    posts.value = []
    dataError.value = errorText(error)
  } finally {
    dataLoading.value = false
  }
}

async function loadPublicData() {
  const [feedResult, topicsResult, creatorsResult, summaryResult] = await Promise.allSettled([
    api.getFeed({ scope: feedScopes[feedFilter.value], limit: 20 }),
    api.getTopics(),
    api.getRecommendedCreators(),
    api.getCommunitySummary(),
  ])
  if (feedResult.status === 'fulfilled') {
    posts.value = normalizeList(feedResult.value, normalizePost)
    dataError.value = ''
  } else {
    posts.value = []
    dataError.value = feedResult.reason?.message || '公开内容暂时无法加载'
  }
  topics.value = topicsResult.status === 'fulfilled' ? normalizeList(topicsResult.value, normalizeTopic) : []
  people.value = creatorsResult.status === 'fulfilled' ? normalizeList(creatorsResult.value, normalizeUser) : []
  onlineCount.value = summaryResult.status === 'fulfilled' ? Number(summaryResult.value?.onlineCount || 0) : 0
}

async function loadPrivateData() {
  if (!isAuthenticated.value) return
  const [profileResult, postsResult, notificationsResult, accountsResult] = await Promise.allSettled([
    api.getMyProfile(),
    api.getMyPosts({ limit: 50 }),
    api.getNotifications(),
    api.listSavedAccounts(),
  ])

  if (profileResult.status === 'rejected' && profileResult.reason?.status === 401) {
    clearSession()
    isAuthenticated.value = false
    return
  }
  if (profileResult.status === 'fulfilled') {
    profileInfo.value = normalizeProfile(profileResult.value)
    profileInfo.value.gender = genderFromApi[profileInfo.value.gender] || '保密'
    profileDraft.value = { ...profileInfo.value }
    activeAccountId.value = profileInfo.value.id || ''
  }
  profilePosts.value = postsResult.status === 'fulfilled' ? normalizeList(postsResult.value, normalizePost) : []
  notifications.value = notificationsResult.status === 'fulfilled' ? normalizeList(notificationsResult.value, normalizeNotification) : []
  savedAccounts.value = accountsResult.status === 'fulfilled' ? normalizeList(accountsResult.value, normalizeAccount) : []
}

async function loadBookmarks() {
  try {
    savedPosts.value = normalizeList(await api.getBookmarks({ limit: 50 }), normalizePost)
  } catch (error) {
    savedPosts.value = []
    showToast(errorText(error))
  }
}

async function loadConversations() {
  try {
    conversations.value = normalizeList(await api.getConversations(), normalizeConversation)
    if (!conversations.value.some((item) => item.id === activeConversation.value)) {
      activeConversation.value = conversations.value[0]?.id || null
    }
    if (activeConversation.value) await loadMessages(activeConversation.value)
  } catch (error) {
    conversations.value = []
    chatMessages.value = []
    showToast(errorText(error))
  }
}

async function loadMessages(conversationId) {
  if (!conversationId) return
  const payload = await api.getMessages(conversationId, { limit: 50 })
  chatMessages.value = normalizeList(payload, (message) => ({
    id: message.id,
    mine: Boolean(message.mine),
    text: message.content || '',
    createdAt: message.createdAt || '',
  }))
}

async function loadSettingsData() {
  settingsReady.value = false
  const [settingsResult, blockedResult, appsResult] = await Promise.allSettled([
    api.getSettings(),
    api.getBlockedUsers(),
    api.getAuthorizedApps(),
  ])
  if (settingsResult.status === 'fulfilled') {
    const settings = settingsResult.value || {}
    visibilitySettings.value = {
      profile: visibilityFromApi[settings.visibility?.profile] || '公开',
      posts: visibilityFromApi[settings.visibility?.posts] || '公开',
      saved: visibilityFromApi[settings.visibility?.bookmarks] || '仅自己可见',
      following: visibilityFromApi[settings.visibility?.followingList] || '好友可见',
    }
    socialSettings.value = {
      messages: socialFromApi[settings.social?.messages] || '互相关注的人',
      comments: socialFromApi[settings.social?.comments] || '所有人',
      mentions: socialFromApi[settings.social?.mentions] || '我关注的人',
    }
    recommendationSettings.value = {
      content: Boolean(settings.recommendations?.personalizedContent),
      ads: Boolean(settings.recommendations?.personalizedAds),
    }
    specialModes.value = {
      teen: Boolean(settings.modes?.teen),
      elder: Boolean(settings.modes?.elder),
      private: Boolean(settings.modes?.private),
    }
  }
  blockedUsers.value = blockedResult.status === 'fulfilled' ? normalizeList(blockedResult.value, normalizeUser) : []
  authorizedApps.value = appsResult.status === 'fulfilled'
    ? normalizeList(appsResult.value, (app) => ({ id: app.id, name: app.appName, scope: (app.scopes || []).join('、'), lastUsed: app.lastUsedAt || '', icon: app.appName?.slice(0, 1) || '' }))
    : []
  await nextTick()
  settingsReady.value = true
}

async function initializeApp() {
  dataLoading.value = true
  await loadPublicData()
  if (isAuthenticated.value) await loadPrivateData()
  dataLoading.value = false
}

function switchView(view) {
  if (!isAuthenticated.value && ['messages', 'saved', 'profile', 'settings'].includes(view)) {
    openLogin(view)
    return
  }
  activePost.value = null
  selectedMember.value = null
  birthPickerOpen.value = false
  profileEditorOpen.value = false
  activeView.value = view
  mobileNavOpen.value = false
  notificationsOpen.value = false
  profileOpen.value = false
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

async function publishPost() {
  if (!requireAuthentication('feed')) return false
  if (!draft.value.trim()) {
    showToast('写点什么再发布吧')
    return false
  }
  if (publishBusy.value) return false
  publishBusy.value = true
  draftState.value = 'saving'
  try {
    let result
    if (draftId.value) {
      await api.updatePost(draftId.value, {
        content: draft.value.trim(),
        topicId: currentTopicId(),
        mediaIds: mediaItems.value.map((item) => item.mediaId).filter(Boolean),
        version: draftVersion.value,
      })
      result = await api.publishPost(draftId.value)
    } else {
      result = await api.createPost({
        content: draft.value.trim(),
        topicId: currentTopicId(),
        mediaIds: mediaItems.value.map((item) => item.mediaId).filter(Boolean),
        status: 'PUBLISHED',
        clientRequestId: draftRequestId.value,
      })
    }
    const created = normalizePost(result)
    posts.value.unshift(created)
    profilePosts.value.unshift(created)
    resetComposer()
    activeView.value = 'feed'
    showToast('动态已发布')
    return true
  } catch (error) {
    draftState.value = ''
    showToast(errorText(error))
    return false
  } finally {
    publishBusy.value = false
  }
}

function openSchedule() {
  if (!requireAuthentication('feed')) return
  if (!draft.value.trim()) {
    showToast('写点什么再定时发布吧')
    return
  }
  scheduleOpen.value = true
}

async function confirmSchedule(scheduledAt) {
  if (scheduleBusy.value) return
  scheduleBusy.value = true
  draftState.value = 'saving'
  try {
    const postId = await preparePostForSubmit()
    await api.schedulePost(postId, scheduledAt)
    resetComposer()
    scheduleOpen.value = false
    activeView.value = 'feed'
    showToast('已设置定时发布')
  } catch (error) {
    draftState.value = ''
    showToast(errorText(error))
  } finally {
    scheduleBusy.value = false
  }
}

async function toggleLike(post) {
  if (!requireAuthentication(activeView.value)) return
  const next = !post.liked
  try {
    const result = await api.setPostLike(post.id, next)
    post.liked = Boolean(result?.active ?? next)
    post.likes = Number(result?.count ?? post.likes + (next ? 1 : -1))
  } catch (error) {
    showToast(errorText(error))
  }
}

async function toggleSave(post) {
  if (!requireAuthentication(activeView.value)) return
  const next = !post.saved
  try {
    const result = await api.setPostBookmark(post.id, next)
    post.saved = Boolean(result?.active ?? next)
    if (post.saved && !savedPosts.value.some((item) => item.id === post.id)) savedPosts.value.unshift(post)
    if (!post.saved) savedPosts.value = savedPosts.value.filter((item) => item.id !== post.id)
    showToast(post.saved ? '已加入稍后阅读' : '已移出稍后阅读')
  } catch (error) {
    showToast(errorText(error))
  }
}

async function addComment(post) {
  if (!requireAuthentication(activeView.value)) return
  const text = post.reply.trim()
  if (!text) return
  try {
    post.comments.push(normalizeComment(await api.createComment(post.id, text)))
    post.commentCount = Number(post.commentCount || 0) + 1
    post.reply = ''
    post.commentsOpen = true
    showToast('回复已发送')
  } catch (error) {
    showToast(errorText(error))
  }
}

function startReply(comment) {
  if (replyingToId.value === comment.id) return
  replyingToId.value = comment.id
  nestedReply.value = ''
}

function cancelNestedReply() {
  replyingToId.value = null
  nestedReply.value = ''
}

async function addNestedReply(comment) {
  if (!requireAuthentication(activeView.value)) return
  const text = nestedReply.value.trim()
  if (!text) return
  try {
    if (!comment.replies) comment.replies = []
    comment.replies.push(normalizeComment(await api.createReply(comment.id, text)))
    if (activePost.value) activePost.value.commentCount = Number(activePost.value.commentCount || 0) + 1
    nestedReply.value = ''
    replyingToId.value = null
    showToast('回复已发送')
  } catch (error) {
    showToast(errorText(error))
  }
}

function responseCount(post) {
  if (Number.isFinite(post.commentCount)) return post.commentCount
  return (post.comments || []).reduce((total, comment) => total + 1 + (comment.replies?.length || 0), 0)
}

async function follow(person) {
  if (!requireAuthentication(activeView.value)) return
  const next = !person.followed
  try {
    const result = await api.setFollow(person.id, next)
    person.followed = Boolean(result?.following ?? next)
    person.followsYou = Boolean(result?.followsMe ?? person.followsYou)
    showToast(person.followed ? '已关注 ' + person.name : '已取消关注')
  } catch (error) {
    showToast(errorText(error))
  }
}

function sharePost(post) {
  sharePostTarget.value = post
  sharePanelOpen.value = true
}

function closeSharePanel() {
  sharePanelOpen.value = false
  sharePostTarget.value = null
}

async function shareTo(platform) {
  if (!sharePostTarget.value) return
  const platformNames = { wechat: '微信', qq: 'QQ', douyin: '抖音' }
  const shareData = {
    title: sharePostTarget.value.title,
    text: sharePostTarget.value.body,
    url: window.location.href,
  }
  void api.recordShare(sharePostTarget.value.id, platform.toUpperCase()).catch(() => {})

  if (navigator.share) {
    try {
      await navigator.share(shareData)
      closeSharePanel()
      showToast('已打开系统分享')
      return
    } catch {
      return
    }
  }

  await navigator.clipboard?.writeText(shareData.title + '\n' + shareData.url)
  closeSharePanel()
  showToast('链接已复制，请打开' + platformNames[platform] + '分享')
}

async function copyShareLink() {
  if (!sharePostTarget.value) return
  await navigator.clipboard?.writeText(window.location.href)
  closeSharePanel()
  showToast('分享链接已复制')
}

function scrollToProfileContent() {
  nextTick(() => document.getElementById('profile-content')?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

async function openConnections(type) {
  profileConnectionsType.value = type
  connectionSearch.value = ''
  profileConnectionsOpen.value = true
  try {
    const payload = type === 'followers'
      ? await api.getFollowers({ limit: 50 })
      : await api.getFollowing({ limit: 50 })
    const normalized = normalizeList(payload, normalizeUser)
    if (type === 'followers') followers.value = normalized
    else following.value = normalized
  } catch (error) {
    showToast(errorText(error))
  }
}

function closeConnections() {
  profileConnectionsOpen.value = false
  connectionSearch.value = ''
}

async function toggleConnection(person) {
  if (!requireAuthentication(activeView.value)) return
  const nextState = !person.followed
  try {
    const result = await api.setFollow(person.id, nextState)
    ;[followers.value, following.value, people.value].forEach((list) => {
      list.forEach((item) => {
        if (item.id === person.id) {
          item.followed = Boolean(result?.following ?? nextState)
          item.followsYou = Boolean(result?.followsMe ?? item.followsYou)
        }
      })
    })
    person.followed = Boolean(result?.following ?? nextState)
    person.followsYou = Boolean(result?.followsMe ?? person.followsYou)
    showToast(nextState ? (person.followsYou ? '已回关 ' : '已关注 ') + person.name : '已取消关注')
  } catch (error) {
    showToast(errorText(error))
  }
}

function connectionLabel(person) {
  if (person.followed && person.followsYou) return '互相关注'
  if (!person.followed && person.followsYou) return '回关'
  return person.followed ? '已关注' : '关注'
}

async function openMemberProfile(person) {
  selectedMember.value = person
  activePost.value = null
  profileEditorOpen.value = false
  activeView.value = 'member-profile'
  closeConnections()
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  try {
    const [profile, published] = await Promise.all([
      api.getUserProfile(person.id),
      api.getUserPosts(person.id, { limit: 50 }),
    ])
    selectedMember.value = normalizeUser(profile)
    memberPosts.value = normalizeList(published, normalizePost)
  } catch (error) {
    memberPosts.value = []
    showToast(errorText(error))
  }
}

function closeMemberProfile() {
  selectedMember.value = null
  memberPosts.value = []
  switchView('profile')
}

function openProfileEditor() {
  if (!requireAuthentication('profile')) return
  profileDraft.value = { ...profileInfo.value }
  birthPickerOpen.value = false
  profileEditorOpen.value = true
  profileOpen.value = false
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function closeProfileEditor() {
  birthPickerOpen.value = false
  profileEditorOpen.value = false
  profileDraft.value = { ...profileInfo.value }
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

async function handleAvatarUpload(event) {
  const file = event.target.files?.[0]
  if (!file) return
  if (!file.type.startsWith('image/')) {
    showToast('请选择图片文件')
    return
  }
  if (file.size > 4 * 1024 * 1024) {
    showToast('头像图片请控制在 4MB 以内')
    return
  }
  try {
    const result = await api.uploadAvatar(file)
    profileDraft.value.avatar = result?.avatarUrl || ''
    showToast('头像已上传')
  } catch (error) {
    showToast(errorText(error))
  }
}

async function saveProfile() {
  const nickname = profileDraft.value.nickname.trim()
  const username = profileDraft.value.username.trim().replace(/^@/, '')
  if (!nickname || !username) {
    showToast('请填写昵称和用户名')
    return
  }
  try {
    const updated = await api.updateMyProfile({
      nickname,
      username,
      realName: profileDraft.value.realName.trim() || null,
      bio: profileDraft.value.bio.trim(),
      gender: genderToApi[profileDraft.value.gender] || 'PRIVATE',
      birthDate: profileDraft.value.birthDate || null,
      region: profileDraft.value.region.trim() || null,
      city: profileDraft.value.city.trim() || null,
      address: profileDraft.value.address.trim() || null,
      phone: profileDraft.value.phone.trim() || null,
      email: profileDraft.value.email.trim() || null,
    })
    profileInfo.value = normalizeProfile(updated)
    profileInfo.value.gender = genderFromApi[profileInfo.value.gender] || '保密'
    profileDraft.value = { ...profileInfo.value }
    birthPickerOpen.value = false
    profileEditorOpen.value = false
    showToast('个人资料已更新')
  } catch (error) {
    showToast(errorText(error))
  }
}

function scrollBirthPickerToSelection(behavior = 'smooth') {
  nextTick(() => {
    document.getElementById('birth-picker-panel')?.querySelectorAll('.date-wheel-scroll').forEach((column) => {
      const selected = column.querySelector('.selected')
      if (!selected) return
      column.scrollTo({
        top: selected.offsetTop - column.clientHeight / 2 + selected.clientHeight / 2,
        behavior,
      })
    })
  })
}

function openBirthPicker() {
  birthPickerOpen.value = true
  scrollBirthPickerToSelection('auto')
  nextTick(() => document.getElementById('birth-picker-panel')?.focus({ preventScroll: true }))
}

function closeBirthPicker() {
  birthPickerOpen.value = false
}

function selectBirthPart(part, value) {
  let { year, month, day } = birthDateParts.value
  if (part === 'year') year = value
  if (part === 'month') month = value
  if (part === 'day') day = value
  day = Math.min(day, new Date(year, month, 0).getDate())
  profileDraft.value.birthDate = `${year}-${String(month).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  scrollBirthPickerToSelection()
}

function clearBirthDate() {
  profileDraft.value.birthDate = ''
  closeBirthPicker()
}

function openSettings() {
  if (!requireAuthentication('settings')) return
  settingsActiveSection.value = 'account'
  switchView('settings')
}

function requireAuthentication(returnView = 'feed') {
  if (isAuthenticated.value) return true
  openLogin(returnView)
  showToast('请先登录后继续')
  return false
}

function openLogin(returnView = 'feed') {
  loginReturnView.value = returnView === 'login' ? 'feed' : returnView
  loginError.value = ''
  activePost.value = null
  profileEditorOpen.value = false
  profileOpen.value = false
  notificationsOpen.value = false
  mobileNavOpen.value = false
  activeView.value = 'login'
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function openRegister() {
  registerError.value = ''
  activePost.value = null
  profileEditorOpen.value = false
  profileOpen.value = false
  notificationsOpen.value = false
  mobileNavOpen.value = false
  activeView.value = 'register'
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function openPasswordReset() {
  resetError.value = ''
  activePost.value = null
  profileEditorOpen.value = false
  profileOpen.value = false
  notificationsOpen.value = false
  mobileNavOpen.value = false
  activeView.value = 'reset-password'
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

function normalizePhone(value = '') {
  const compact = value.trim().replace(/[\s-]/g, '')
  if (/^1[3-9]\d{9}$/.test(compact)) return compact
  if (/^(?:\+?86)1[3-9]\d{9}$/.test(compact)) return compact.replace(/^\+?86/, '')
  return ''
}

function normalizeEmail(value = '') {
  const email = value.trim().toLowerCase()
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email) ? email : ''
}

function normalizeIdentifier(value = '') {
  const phone = normalizePhone(value)
  if (phone) return { identifierType: 'PHONE', identifier: phone }
  const email = normalizeEmail(value)
  if (email) return { identifierType: 'EMAIL', identifier: email }
  return null
}

function normalizeIdentifierForType(value, identifierType) {
  const identifier = identifierType === 'EMAIL' ? normalizeEmail(value) : normalizePhone(value)
  return identifier ? { identifierType, identifier } : null
}

function identifierKey(identity) {
  return identity ? `${identity.identifierType}:${identity.identifier}` : ''
}

function isValidPassword(password) {
  return /^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(password)
}

function clearLoginCodeTimer() {
  if (loginCodeTimer) window.clearInterval(loginCodeTimer)
  loginCodeTimer = 0
}

function resetLoginVerification() {
  clearLoginCodeTimer()
  loginVerificationTarget.value = ''
  loginVerificationId.value = ''
  loginMaskedTarget.value = ''
  loginCodeCountdown.value = 0
  loginForm.value.code = ''
}

function changeLoginMode(mode) {
  loginMode.value = mode === 'password' ? 'password' : 'code'
  loginError.value = ''
  if (loginMode.value === 'password') resetLoginVerification()
  else loginForm.value.password = ''
}

function startLoginCodeCountdown(seconds = 60) {
  clearLoginCodeTimer()
  loginCodeCountdown.value = Math.max(0, Number(seconds) || 0)
  if (!loginCodeCountdown.value) return
  loginCodeTimer = window.setInterval(() => {
    loginCodeCountdown.value -= 1
    if (loginCodeCountdown.value <= 0) clearLoginCodeTimer()
  }, 1000)
}

async function sendLoginCode() {
  const identity = normalizeIdentifier(loginForm.value.account)
  if (!identity) {
    loginError.value = '请输入正确的手机号或邮箱'
    return
  }
  if (loginCodeSending.value || loginCodeCountdown.value > 0) return

  loginCodeSending.value = true
  loginError.value = ''
  try {
    const challenge = await api.sendVerificationCode({ scene: 'LOGIN', ...identity })
    if (identifierKey(normalizeIdentifier(loginForm.value.account)) !== identifierKey(identity)) return
    loginVerificationTarget.value = identifierKey(identity)
    loginVerificationId.value = challenge.verificationId || ''
    loginMaskedTarget.value = challenge.maskedTarget || challenge.identifier || identity.identifier
    startLoginCodeCountdown(challenge.resendAfterSeconds || 60)
  } catch (error) {
    loginError.value = errorText(error)
  } finally {
    loginCodeSending.value = false
  }
}

function applyAccount(account) {
  activeAccountId.value = account.id
  profileInfo.value = normalizeProfile(account.user || account)
  profileInfo.value.gender = genderFromApi[profileInfo.value.gender] || '保密'
}

async function submitLogin() {
  const account = loginForm.value.account.trim()
  const identity = normalizeIdentifier(account)
  const username = !identity && /^[A-Za-z0-9_-]{3,24}$/.test(account) ? account.toLowerCase() : ''
  if (!identity && !(!isCodeLogin.value && username)) {
    loginError.value = isCodeLogin.value ? '请输入正确的手机号或邮箱' : '请输入正确的手机号、邮箱或用户名'
    return
  }
  if (isCodeLogin.value && !/^\d{6}$/.test(loginForm.value.code)) {
    loginError.value = '请输入 6 位验证码'
    return
  }
  if (!isCodeLogin.value && !loginForm.value.password) {
    loginError.value = '请输入密码'
    return
  }

  try {
    const session = isCodeLogin.value
      ? await api.loginByCode({
          ...identity,
          verificationId: loginVerificationId.value || undefined,
          verificationCode: loginForm.value.code,
          remember: loginForm.value.remember,
        })
      : await api.login({
          ...(identity || { account: username }),
          password: loginForm.value.password,
          remember: loginForm.value.remember,
        })
    saveSession(session, loginForm.value.remember)
    applyAccount(session.user)
    isAuthenticated.value = true
    loginError.value = ''
    loginForm.value.password = ''
    resetLoginVerification()
    await loadPrivateData()
    const destination = loginReturnView.value === 'login' ? 'feed' : loginReturnView.value
    switchView(destination)
    showToast(`欢迎回来，${profileInfo.value.nickname}`)
  } catch (error) {
    loginError.value = errorText(error)
  }
}

async function quickLogin(account) {
  await switchAccount(account)
  if (isAuthenticated.value) switchView(loginReturnView.value || 'feed')
}

function clearRegisterCodeTimer() {
  if (registerCodeTimer) window.clearInterval(registerCodeTimer)
  registerCodeTimer = 0
}

function resetRegisterVerification() {
  clearRegisterCodeTimer()
  registerVerificationTarget.value = ''
  registerVerificationId.value = ''
  registerMaskedTarget.value = ''
  registerCodeCountdown.value = 0
  registerForm.value.code = ''
}

function startRegisterCodeCountdown(seconds = 60) {
  clearRegisterCodeTimer()
  registerCodeCountdown.value = Math.max(0, Number(seconds) || 0)
  if (!registerCodeCountdown.value) return
  registerCodeTimer = window.setInterval(() => {
    registerCodeCountdown.value -= 1
    if (registerCodeCountdown.value <= 0) clearRegisterCodeTimer()
  }, 1000)
}

function changeRegisterIdentifierType(identifierType) {
  registerForm.value.identifierType = identifierType === 'EMAIL' ? 'EMAIL' : 'PHONE'
  registerForm.value.identifier = ''
  registerError.value = ''
  resetRegisterVerification()
}

async function sendRegisterCode() {
  const identity = normalizeIdentifierForType(registerForm.value.identifier, registerForm.value.identifierType)
  if (!identity) {
    registerError.value = registerForm.value.identifierType === 'EMAIL' ? '请输入正确的邮箱地址' : '请输入正确的中国大陆手机号'
    return
  }
  if (registerCodeSending.value || registerCodeCountdown.value > 0) return

  registerCodeSending.value = true
  registerError.value = ''
  try {
    const challenge = await api.sendVerificationCode({ scene: 'REGISTER', ...identity })
    if (identifierKey(normalizeIdentifierForType(registerForm.value.identifier, registerForm.value.identifierType)) !== identifierKey(identity)) return
    registerVerificationTarget.value = identifierKey(identity)
    registerVerificationId.value = challenge.verificationId || ''
    registerMaskedTarget.value = challenge.maskedTarget || challenge.identifier || identity.identifier
    startRegisterCodeCountdown(challenge.resendAfterSeconds || 60)
  } catch (error) {
    registerError.value = errorText(error)
  } finally {
    registerCodeSending.value = false
  }
}

async function submitRegistration() {
  const nickname = registerForm.value.nickname.trim()
  const username = registerForm.value.username.trim().toLowerCase()
  const identity = normalizeIdentifierForType(registerForm.value.identifier, registerForm.value.identifierType)
  if (!nickname || [...nickname].length > 20) {
    registerError.value = '昵称需为 1～20 个字符'
    return
  }
  if (!/^[a-z0-9_-]{3,24}$/.test(username)) {
    registerError.value = '用户名需为 3～24 位小写字母、数字、下划线或短横线'
    return
  }
  if (!identity) {
    registerError.value = registerForm.value.identifierType === 'EMAIL' ? '请输入正确的邮箱地址' : '请输入正确的中国大陆手机号'
    return
  }
  if (!/^\d{6}$/.test(registerForm.value.code)) {
    registerError.value = '请输入 6 位验证码'
    return
  }
  if (!isValidPassword(registerForm.value.password)) {
    registerError.value = '密码至少 8 位，并且必须同时包含字母和数字'
    return
  }
  if (registerForm.value.password !== registerForm.value.confirmPassword) {
    registerError.value = '两次输入的密码不一致'
    return
  }
  if (!registerForm.value.agreed) {
    registerError.value = '请先同意社区公约和隐私政策'
    return
  }

  try {
    const availability = await api.checkUsername(username)
    if (!availability?.available) {
      registerError.value = '用户名已被使用'
      return
    }
    const session = await api.register({
      nickname,
      username,
      ...identity,
      verificationId: registerVerificationId.value || undefined,
      verificationCode: registerForm.value.code,
      password: registerForm.value.password,
      agreed: true,
    })
    saveSession(session, true)
    applyAccount(session.user)
    isAuthenticated.value = true
    const welcomeName = session.user?.nickname || '新朋友'
    registerForm.value = { nickname: '', username: '', identifierType: 'PHONE', identifier: '', code: '', password: '', confirmPassword: '', agreed: false }
    resetRegisterVerification()
    registerPasswordVisible.value = false
    registerConfirmPasswordVisible.value = false
    await loadPrivateData()
    switchView('profile')
    showToast(`欢迎加入 C—53，${welcomeName}`)
  } catch (error) {
    registerError.value = errorText(error)
  }
}

function clearResetCodeTimer() {
  if (resetCodeTimer) window.clearInterval(resetCodeTimer)
  resetCodeTimer = 0
}

function resetPasswordVerification() {
  clearResetCodeTimer()
  resetVerificationTarget.value = ''
  resetMaskedTarget.value = ''
  resetCodeCountdown.value = 0
  resetForm.value.code = ''
}

function startResetCodeCountdown(seconds = 60) {
  clearResetCodeTimer()
  resetCodeCountdown.value = Math.max(0, Number(seconds) || 0)
  if (!resetCodeCountdown.value) return
  resetCodeTimer = window.setInterval(() => {
    resetCodeCountdown.value -= 1
    if (resetCodeCountdown.value <= 0) clearResetCodeTimer()
  }, 1000)
}

function changeResetIdentifierType(identifierType) {
  resetForm.value.identifierType = identifierType === 'EMAIL' ? 'EMAIL' : 'PHONE'
  resetForm.value.identifier = ''
  resetError.value = ''
  resetPasswordVerification()
}

async function sendResetCode() {
  const identity = normalizeIdentifierForType(resetForm.value.identifier, resetForm.value.identifierType)
  if (!identity) {
    resetError.value = resetForm.value.identifierType === 'EMAIL' ? '请输入正确的邮箱地址' : '请输入正确的中国大陆手机号'
    return
  }
  if (resetCodeSending.value || resetCodeCountdown.value > 0) return

  resetCodeSending.value = true
  resetError.value = ''
  try {
    const challenge = await api.sendVerificationCode({ scene: 'RESET_PASSWORD', ...identity })
    if (identifierKey(normalizeIdentifierForType(resetForm.value.identifier, resetForm.value.identifierType)) !== identifierKey(identity)) return
    resetVerificationTarget.value = identifierKey(identity)
    resetMaskedTarget.value = challenge.identifier || identity.identifier
    startResetCodeCountdown(challenge.resendAfterSeconds || 60)
  } catch (error) {
    resetError.value = errorText(error)
  } finally {
    resetCodeSending.value = false
  }
}

async function submitPasswordReset() {
  const identity = normalizeIdentifierForType(resetForm.value.identifier, resetForm.value.identifierType)
  if (!identity) {
    resetError.value = resetForm.value.identifierType === 'EMAIL' ? '请输入正确的邮箱地址' : '请输入正确的中国大陆手机号'
    return
  }
  if (!/^\d{6}$/.test(resetForm.value.code)) {
    resetError.value = '请输入 6 位验证码'
    return
  }
  if (!isValidPassword(resetForm.value.newPassword)) {
    resetError.value = '新密码至少 8 位，并且必须同时包含字母和数字'
    return
  }
  if (resetForm.value.newPassword !== resetForm.value.confirmPassword) {
    resetError.value = '两次输入的新密码不一致'
    return
  }

  try {
    await api.resetPassword({ ...identity, code: resetForm.value.code, newPassword: resetForm.value.newPassword })
    resetForm.value = { identifierType: 'PHONE', identifier: '', code: '', newPassword: '', confirmPassword: '' }
    resetPasswordVerification()
    resetPasswordVisible.value = false
    resetConfirmPasswordVisible.value = false
    loginMode.value = 'password'
    openLogin(loginReturnView.value)
    showToast('密码已重置，请重新登录')
  } catch (error) {
    resetError.value = errorText(error)
  }
}

async function switchAccount(account) {
  if (account.id === activeAccountId.value) return
  try {
    const session = await api.switchAccount(account.id)
    saveSession(session, true)
    applyAccount(session.user)
    isAuthenticated.value = true
    await loadPrivateData()
    showToast(`已切换至 ${profileInfo.value.nickname}`)
  } catch (error) {
    showToast(errorText(error))
  }
}

async function logout() {
  const refreshToken = getRefreshToken()
  try { if (refreshToken) await api.logout(refreshToken) } catch { /* 本地会话仍需清理 */ }
  clearSession()
  isAuthenticated.value = false
  profileInfo.value = normalizeProfile()
  profilePosts.value = []
  savedPosts.value = []
  notifications.value = []
  conversations.value = []
  chatMessages.value = []
  savedAccounts.value = []
  profileOpen.value = false
  notificationsOpen.value = false
  switchView('feed')
  showToast('已安全退出登录')
}

function startPublishing() {
  if (!requireAuthentication('feed')) return
  composerOpen.value = true
  switchView('feed')
}

function toggleNotifications() {
  if (!requireAuthentication('feed')) return
  notificationsOpen.value = !notificationsOpen.value
  profileOpen.value = false
}

async function markAllNotificationsRead() {
  try {
    await api.markAllNotificationsRead()
    notifications.value = []
    notificationsOpen.value = false
  } catch (error) {
    showToast(errorText(error))
  }
}

function scrollToSettingsSection(section) {
  settingsActiveSection.value = section
  revealActiveSettingsNav()
  nextTick(() => document.getElementById(`settings-${section}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }))
}

function revealActiveSettingsNav() {
  nextTick(() => {
    const item = document.querySelector(`[data-settings-section="${settingsActiveSection.value}"]`)
    const navigation = item?.parentElement
    if (!item || !navigation || navigation.scrollWidth <= navigation.clientWidth) return
    navigation.scrollTo({
      left: item.offsetLeft - navigation.clientWidth / 2 + item.clientWidth / 2,
      behavior: 'smooth',
    })
  })
}

let settingsScrollFrame

function syncSettingsSection() {
  settingsScrollFrame = undefined
  if (activeView.value !== 'settings') return

  const atPageBottom = window.scrollY + window.innerHeight >= document.documentElement.scrollHeight - 12
  let currentSection = settingsNav[0].id

  if (atPageBottom) {
    currentSection = settingsNav.at(-1).id
  } else {
    const trackingLine = Math.min(180, window.innerHeight * .28)
    for (const item of settingsNav) {
      const section = document.getElementById(`settings-${item.id}`)
      if (section && section.getBoundingClientRect().top <= trackingLine) currentSection = item.id
    }
  }

  if (settingsActiveSection.value !== currentSection) {
    settingsActiveSection.value = currentSection
    revealActiveSettingsNav()
  }
}

function handleSettingsScroll() {
  if (settingsScrollFrame) return
  settingsScrollFrame = window.requestAnimationFrame(syncSettingsSection)
}

function openSystemPermission(permission) {
  const isMac = /Mac|iPhone|iPad/.test(navigator.platform)
  const target = isMac ? permission.mac : permission.windows
  try {
    window.location.href = target
    showToast(`正在打开${permission.name}权限设置`)
  } catch {
    showToast(`请在系统设置中打开${permission.name}权限`)
  }
}

async function updateSpecialMode(mode) {
  if (mode === 'teen' && specialModes.value.teen) specialModes.value.elder = false
  if (mode === 'elder' && specialModes.value.elder) specialModes.value.teen = false
  if (mode === 'private' && specialModes.value.private) {
    Object.keys(visibilitySettings.value).forEach((key) => { visibilitySettings.value[key] = '仅自己可见' })
  }
  const labels = { teen: '青少年模式', elder: '长辈模式', private: '私密模式' }
  try {
    await api.updateModes({ ...specialModes.value })
    showToast(`${labels[mode]}已${specialModes.value[mode] ? '开启' : '关闭'}`)
  } catch (error) {
    showToast(errorText(error))
    await loadSettingsData()
  }
}

async function removeBlockedUser(user) {
  try {
    await api.removeBlockedUser(user.id)
    blockedUsers.value = blockedUsers.value.filter((item) => item.id !== user.id)
    showToast(`已将 ${user.name} 移出黑名单`)
  } catch (error) {
    showToast(errorText(error))
  }
}

async function revokeAuthorizedApp(app) {
  try {
    await api.revokeAuthorizedApp(app.id)
    authorizedApps.value = authorizedApps.value.filter((item) => item.id !== app.id)
    showToast(`已解除 ${app.name} 授权`)
  } catch (error) {
    showToast(errorText(error))
  }
}

async function exportPersonalData() {
  if (!requireAuthentication('settings')) return
  try {
    await api.createDataExport()
    showToast('导出任务已创建，完成后会通过通知提醒')
  } catch (error) {
    showToast(errorText(error))
  }
}

async function sendMessage() {
  if (!requireAuthentication('messages')) return
  const text = chatDraft.value.trim()
  if (!text) return
  if (!activeConversation.value) return
  try {
    const message = await api.sendMessage(activeConversation.value, { clientMessageId: crypto.randomUUID(), type: 'TEXT', content: text })
    chatMessages.value.push({ id: message.id, mine: true, text: message.content, createdAt: message.createdAt })
    chatDraft.value = ''
    showToast('消息已发送')
  } catch (error) {
    showToast(errorText(error))
  }
}

async function selectConversation(conversation) {
  activeConversation.value = conversation.id
  conversation.unread = 0
  try {
    await Promise.all([loadMessages(conversation.id), api.markConversationRead(conversation.id)])
  } catch (error) {
    showToast(errorText(error))
  }
}

async function openPost(post) {
  activePost.value = post
  replyingToId.value = null
  nestedReply.value = ''
  notificationsOpen.value = false
  profileOpen.value = false
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
  try {
    const [detail, comments] = await Promise.all([
      api.getPost(post.id),
      api.getComments(post.id),
    ])
    activePost.value = normalizePost(detail)
    activePost.value.comments = normalizeList(comments, normalizeComment)
  } catch (error) {
    showToast(errorText(error))
  }
}

function closePost() {
  activePost.value = null
  replyingToId.value = null
  nestedReply.value = ''
  nextTick(() => window.scrollTo({ top: 0, behavior: 'smooth' }))
}

async function goToComments(post) {
  if (activePost.value?.id !== post.id) await openPost(post)
  if (activePost.value) activePost.value.commentsOpen = true
  notificationsOpen.value = false
  profileOpen.value = false
  nextTick(() => {
    const discussion = document.getElementById('post-discussion')
    discussion?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    discussion?.focus({ preventScroll: true })
  })
}

let webMcpLifecycle
let settingsSaveTimer

function scheduleSettingsSave(section) {
  if (!settingsReady.value || activeView.value !== 'settings') return
  window.clearTimeout(settingsSaveTimer)
  settingsSaveTimer = window.setTimeout(async () => {
    try {
      if (section === 'visibility') {
        await api.updateVisibility({
          profile: visibilityToApi[visibilitySettings.value.profile],
          posts: visibilityToApi[visibilitySettings.value.posts],
          bookmarks: visibilityToApi[visibilitySettings.value.saved],
          followingList: visibilityToApi[visibilitySettings.value.following],
        })
      }
      if (section === 'social') {
        await api.updateSocial({
          messages: socialToApi[socialSettings.value.messages],
          comments: socialToApi[socialSettings.value.comments],
          mentions: socialToApi[socialSettings.value.mentions],
        })
      }
      if (section === 'recommendations') {
        await api.updateRecommendations({
          personalizedContent: recommendationSettings.value.content,
          personalizedAds: recommendationSettings.value.ads,
        })
      }
    } catch (error) {
      showToast(errorText(error))
    }
  }, 350)
}

watch(visibilitySettings, () => scheduleSettingsSave('visibility'), { deep: true })
watch(socialSettings, () => scheduleSettingsSave('social'), { deep: true })
watch(recommendationSettings, () => scheduleSettingsSave('recommendations'), { deep: true })
watch(feedFilter, () => {
  if (activeView.value === 'feed') void loadFeed()
})

// 话题列表加载后，把默认话题纠正为服务端存在的项
watch(topics, (list) => {
  if (!list.length) return
  if (!list.some((topic) => topic.name === draftTopic.value)) draftTopic.value = list[0].name
}, { immediate: true })

// 停止输入 3 秒后自动保存草稿，持续输入时由 saveDraft 的 15 秒节流兜底
watch([draft, draftTopic, mediaItems], () => {
  if (!composerOpen.value || !isAuthenticated.value) return
  window.clearTimeout(draftSaveTimer)
  draftSaveTimer = window.setTimeout(() => { void saveDraft() }, 3000)
}, { deep: true })

watch(() => loginForm.value.account, (account) => {
  if (identifierKey(normalizeIdentifier(account)) === loginVerificationTarget.value) return
  resetLoginVerification()
  loginError.value = ''
})

watch(() => [registerForm.value.identifierType, registerForm.value.identifier], () => {
  const identity = normalizeIdentifierForType(registerForm.value.identifier, registerForm.value.identifierType)
  if (identifierKey(identity) === registerVerificationTarget.value) return
  resetRegisterVerification()
  registerError.value = ''
})

watch(() => [resetForm.value.identifierType, resetForm.value.identifier], () => {
  const identity = normalizeIdentifierForType(resetForm.value.identifier, resetForm.value.identifierType)
  if (identifierKey(identity) === resetVerificationTarget.value) return
  resetPasswordVerification()
  resetError.value = ''
})

watch(activeView, (view) => {
  window.removeEventListener('scroll', handleSettingsScroll)
  if (view === 'saved' && isAuthenticated.value) void loadBookmarks()
  if (view === 'messages' && isAuthenticated.value) void loadConversations()
  if (view === 'profile' && isAuthenticated.value) void loadPrivateData()
  if (view === 'settings' && isAuthenticated.value) {
    void loadSettingsData()
    nextTick(() => {
      syncSettingsSection()
      window.addEventListener('scroll', handleSettingsScroll, { passive: true })
    })
  }
})

onMounted(() => {
  void initializeApp()
  const context = document.modelContext
  if (!context?.registerTool) return

  webMcpLifecycle = new AbortController()
  void Promise.resolve(
    context.registerTool(
      {
        name: 'publish_community_post',
        title: '发布社区动态',
        description: '在 C-53 共创星球发布一条公开动态，并将它显示在共同动态顶部。',
        inputSchema: {
          type: 'object',
          properties: {
            content: { type: 'string', minLength: 1, maxLength: 600 },
            topic: { type: 'string', enum: topics.value.map((topic) => topic.name) },
          },
          required: ['content'],
          additionalProperties: false,
        },
        annotations: { readOnlyHint: false, untrustedContentHint: true },
        async execute(input) {
          const content = typeof input?.content === 'string' ? input.content.trim() : ''
          const topic = topics.value.some((item) => item.name === input?.topic) ? input.topic : topics.value[0]?.name
          if (!content || Array.from(content).length > 600) throw new Error('动态内容需要保持在 1 到 600 字之间')

          draft.value = content
          draftTopic.value = topic
          mediaItems.value = []
          const published = await publishPost()
          if (!published) throw new Error('动态发布失败，请稍后重试')
          await nextTick()
          return { status: 'published', postId: posts.value[0]?.id || '', topic }
        },
      },
      { signal: webMcpLifecycle.signal },
    ),
  ).catch(() => {})
})

onBeforeUnmount(() => {
  webMcpLifecycle?.abort()
  clearLoginCodeTimer()
  clearRegisterCodeTimer()
  clearResetCodeTimer()
  window.removeEventListener('scroll', handleSettingsScroll)
  if (settingsScrollFrame) window.cancelAnimationFrame(settingsScrollFrame)
  window.clearTimeout(settingsSaveTimer)
  window.clearTimeout(draftSaveTimer)
})
</script>

<template>
  <div class="app-shell" :class="{ 'auth-shell': isAuthView }">
    <AppSidebar
      v-if="!isAuthView"
      :views="views"
      :topics="topics"
      :active-view="activeView"
      :open="mobileNavOpen"
      @navigate="switchView"
      @close="mobileNavOpen = false"
    />

    <div v-if="!isAuthView && mobileNavOpen" class="mobile-scrim" @click="mobileNavOpen = false"></div>

    <section class="main-stage" :class="{ 'auth-stage': isAuthView }">
      <AppTopbar
        v-if="!isAuthView"
        v-model:search-query="searchQuery"
        :is-authenticated="isAuthenticated"
        :notifications-open="notificationsOpen"
        :profile-open="profileOpen"
        :profile-info="profileInfo"
        :profile-initial="profileInitial"
        @open-mobile-nav="mobileNavOpen = true"
        @toggle-notifications="toggleNotifications"
        @close-notifications="notificationsOpen = false"
        @start-publishing="startPublishing"
        @toggle-profile="profileOpen = !profileOpen; notificationsOpen = false"
        @navigate="switchView"
        @open-settings="openSettings"
        @open-login="openLogin('feed')"
      />

      <ProfileEditorView
        v-if="profileEditorOpen"
        :draft="profileDraft"
        :birth-picker-open="birthPickerOpen"
        :formatted-birth-date="formattedBirthDate"
        :birth-date-parts="birthDateParts"
        :birth-picker-years="birthPickerYears"
        :birth-picker-days="birthPickerDays"
        :age="draftAge"
        :zodiac="draftZodiac"
        @close="closeProfileEditor"
        @save="saveProfile"
        @avatar-upload="handleAvatarUpload"
        @open-birth-picker="openBirthPicker"
        @close-birth-picker="closeBirthPicker"
        @select-birth-part="selectBirthPart"
        @clear-birth-date="clearBirthDate"
      />

      <LoginView
        v-else-if="activeView === 'login'"
        :form="loginForm"
        :error="loginError"
        :password-visible="passwordVisible"
        :is-code-login="isCodeLogin"
        :code-countdown="loginCodeCountdown"
        :code-sending="loginCodeSending"
        :masked-target="loginMaskedTarget"
        :accounts="savedAccounts"
        @submit="submitLogin"
        @back="switchView('feed')"
        @clear-error="loginError = ''"
        @change-mode="changeLoginMode"
        @toggle-password="passwordVisible = !passwordVisible"
        @send-code="sendLoginCode"
        @forgot="openPasswordReset"
        @quick-login="quickLogin"
        @register="openRegister"
      />

      <RegisterView
        v-else-if="activeView === 'register'"
        :form="registerForm"
        :error="registerError"
        :password-visible="registerPasswordVisible"
        :confirm-password-visible="registerConfirmPasswordVisible"
        :code-countdown="registerCodeCountdown"
        :code-sending="registerCodeSending"
        :masked-target="registerMaskedTarget"
        @submit="submitRegistration"
        @back-home="switchView('feed')"
        @back-login="openLogin(loginReturnView)"
        @clear-error="registerError = ''"
        @change-identifier-type="changeRegisterIdentifierType"
        @send-code="sendRegisterCode"
        @toggle-password="registerPasswordVisible = !registerPasswordVisible"
        @toggle-confirm-password="registerConfirmPasswordVisible = !registerConfirmPasswordVisible"
      />

      <ResetPasswordView
        v-else-if="activeView === 'reset-password'"
        :form="resetForm"
        :error="resetError"
        :password-visible="resetPasswordVisible"
        :confirm-password-visible="resetConfirmPasswordVisible"
        :code-countdown="resetCodeCountdown"
        :code-sending="resetCodeSending"
        :masked-target="resetMaskedTarget"
        @submit="submitPasswordReset"
        @back-home="switchView('feed')"
        @back-login="openLogin(loginReturnView)"
        @clear-error="resetError = ''"
        @change-identifier-type="changeResetIdentifierType"
        @send-code="sendResetCode"
        @toggle-password="resetPasswordVisible = !resetPasswordVisible"
        @toggle-confirm-password="resetConfirmPasswordVisible = !resetConfirmPasswordVisible"
      />

      <PostDetailView
        v-else-if="activePost"
        v-model:nested-reply="nestedReply"
        :post="activePost"
        :response-count="responseCount"
        :replying-to-id="replyingToId"
        @back="closePost"
        @open-topic="activePost = null; switchView('discover')"
        @toggle-like="toggleLike"
        @go-comments="goToComments"
        @share="sharePost"
        @toggle-save="toggleSave"
        @start-reply="startReply"
        @cancel-reply="cancelNestedReply"
        @add-nested-reply="addNestedReply"
        @add-comment="addComment"
      />

      <SettingsView
        v-else-if="activeView === 'settings'"
        :settings-nav="settingsNav"
        :active-section="settingsActiveSection"
        :profile-info="profileInfo"
        :profile-initial="profileInitial"
        :accounts="savedAccounts"
        :active-account-id="activeAccountId"
        :permission-items="permissionItems"
        :permission-states="permissionStates"
        :visibility-settings="visibilitySettings"
        :visibility-options="visibilityOptions"
        :social-settings="socialSettings"
        :social-options="socialOptions"
        :blocked-list-open="blockedListOpen"
        :blocked-users="blockedUsers"
        :recommendation-settings="recommendationSettings"
        :authorized-apps="authorizedApps"
        :special-modes="specialModes"
        @back="switchView('profile')"
        @navigate-section="scrollToSettingsSection"
        @switch-account="switchAccount"
        @add-account="openLogin('settings')"
        @logout="logout"
        @open-permission="openSystemPermission"
        @toggle-blocked-list="blockedListOpen = !blockedListOpen"
        @remove-blocked="removeBlockedUser"
        @export-data="exportPersonalData"
        @revoke-app="revokeAuthorizedApp"
        @update-mode="updateSpecialMode"
      />

      <FeedView
        v-else-if="activeView === 'feed' || activeView === 'saved'"
        v-model:draft="draft"
        v-model:composer-open="composerOpen"
        v-model:draft-topic="draftTopic"
        v-model:feed-filter="feedFilter"
        :active-view="activeView"
        :is-authenticated="isAuthenticated"
        :topics="topics"
        :posts="visiblePosts"
        :people="people"
        :response-count="responseCount"
        :media-items="mediaItems"
        :draft-state="draftState"
        :uploading="uploadInProgress"
        :busy="publishBusy"
        @update:media-items="mediaItems = $event"
        @update:uploading="uploadInProgress = $event"
        @schedule="openSchedule"
        @cancel-draft="cancelDraft"
        @media-error="handleMediaError"
        @login="openLogin('feed')"
        @publish="publishPost"
        @navigate="switchView"
        @open-post="openPost"
        @toggle-like="toggleLike"
        @go-comments="goToComments"
        @share="sharePost"
        @toggle-save="toggleSave"
        @add-comment="addComment"
        @follow="follow"
      />

      <DiscoverView
        v-else-if="activeView === 'discover'"
        :topics="topics"
        :people="people"
        @open-profile="openMemberProfile"
        @follow="follow"
      />

      <MessagesView
        v-else-if="activeView === 'messages'"
        v-model:chat-draft="chatDraft"
        :conversations="conversations"
        :active-conversation="activeConversation"
        :current-conversation="currentConversation"
        :messages="chatMessages"
        @select-conversation="selectConversation"
        @send="sendMessage"
      />

      <MemberProfileView
        v-else-if="activeView === 'member-profile' && selectedMember"
        :member="selectedMember"
        :posts="selectedMemberPosts"
        :response-count="responseCount"
        :connection-label="connectionLabel"
        @back="closeMemberProfile"
        @toggle-connection="toggleConnection"
        @open-post="openPost"
        @toggle-like="toggleLike"
        @go-comments="goToComments"
        @share="sharePost"
      />

      <ProfileView
        v-else-if="activeView === 'profile'"
        :profile-info="profileInfo"
        :profile-initial="profileInitial"
        :posts="profilePublishedPosts"
        :response-count="responseCount"
        @share-profile="sharePost(profilePublishedPosts[0])"
        @edit="openProfileEditor"
        @scroll-content="scrollToProfileContent"
        @open-connections="openConnections"
        @open-post="openPost"
        @toggle-like="toggleLike"
        @go-comments="goToComments"
        @share="sharePost"
      />
    </section>

    <ScheduleDialog
      :open="scheduleOpen"
      :busy="scheduleBusy"
      @close="scheduleOpen = false"
      @confirm="confirmSchedule"
    />

    <SharePanel
      :open="sharePanelOpen"
      :target="sharePostTarget"
      :host="shareHost"
      @close="closeSharePanel"
      @share-to="shareTo"
      @copy-link="copyShareLink"
    />

    <ConnectionsPanel
      v-model:search="connectionSearch"
      :open="profileConnectionsOpen"
      :type="profileConnectionsType"
      :people="filteredConnections"
      :connection-label="connectionLabel"
      @close="closeConnections"
      @open-profile="openMemberProfile"
      @toggle-connection="toggleConnection"
    />

    <Transition name="toast"><div v-if="toast" class="toast"><span>✓</span>{{ toast }}</div></Transition>
  </div>
</template>
