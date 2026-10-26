import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import test from 'node:test'
import vm from 'node:vm'
import { computed, nextTick, ref, watch } from 'vue'

const apiSource = readFileSync(new URL('../src/services/api.js', import.meta.url), 'utf8')
const appSource = readFileSync(new URL('../src/App.vue', import.meta.url), 'utf8')

function deferred() {
  let resolve, reject
  const promise = new Promise((done, fail) => { resolve = done; reject = fail })
  return { promise, resolve, reject }
}

function storage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: (key) => values.delete(key),
  }
}

function json(payload, status = 200) {
  return new Response(JSON.stringify(payload), { status, headers: { 'content-type': 'application/json' } })
}

// 在独立上下文执行真实 API 源码，只替换 Vite 的环境变量注入。
function setupApi() {
  const calls = []
  let handler = () => Promise.resolve(json({}))
  const window = { localStorage: storage(), sessionStorage: storage() }
  const context = vm.createContext({
    window, Headers, FormData,
    fetch: (url, options) => { calls.push({ url, options }); return handler(url, options) },
  })
  const source = apiSource.replace('import.meta.env.VITE_API_BASE_URL', 'undefined').replace(/^export /gm, '')
  const service = vm.runInContext(`${source}\n;({ api, saveSession, clearSession, getAccessToken, getRefreshToken, getSessionVersion, normalizeProfile })`, context)
  return { ...service, window, calls, respond: (fn) => { handler = fn } }
}

function setupApp() {
  const service = setupApi()
  const calls = []
  let send = () => Promise.resolve({ verificationId: 'challenge-1', maskedTarget: '138****8000', resendAfter: 0 })
  let submit = () => Promise.resolve(null)
  let privateProfile = () => Promise.resolve({ user: { id: 'user-1', nickname: '用户' } })
  const window = {
    ...service.window,
    location: { host: 'test.local' },
    crypto: { randomUUID: () => 'test-request' },
    setTimeout: () => 1, clearTimeout() {}, setInterval: () => 1, clearInterval() {},
    scrollTo() {}, removeEventListener() {}, addEventListener() {},
  }
  const context = vm.createContext({
    ...service, window, ref, watch, computed, nextTick,
    onMounted() {}, onBeforeUnmount() {},
    views: [], permissionItems: [], settingsNav: [], socialOptions: [], visibilityOptions: [],
    api: {
      sendVerificationCode: (body) => { calls.push({ kind: 'send', body }); return send(body) },
      resetPassword: (body) => { calls.push({ kind: 'reset', body }); return submit(body) },
      getMe: () => privateProfile(), getMyPosts: async () => [], getNotifications: async () => [], listSavedAccounts: async () => [],
    },
    normalizeList: (value) => Array.isArray(value) ? value : value?.items || [],
    normalizePost: (value) => value, normalizeUser: (value) => value,
  })
  const script = appSource.match(/<script setup>([\s\S]*?)<\/script>/)[1]
    .replace(/^import[\s\S]*?from ['"][^'"]+['"]\r?\n/gm, '')
  const names = [
    'activeView', 'resetForm', 'resetVerificationId', 'resetVerificationTarget', 'resetMaskedTarget', 'resetCodeCountdown',
    'resetCodeSending', 'resetError', 'resetSubmitting', 'sendResetCode', 'submitPasswordReset', 'openLogin',
    'changeResetIdentifierType', 'loginMode', 'isAuthenticated', 'activeAccountId', 'profileInfo', 'profileDraft',
    'profilePosts', 'savedPosts', 'notifications', 'conversations', 'chatMessages', 'savedAccounts', 'followers', 'following',
    'posts', 'memberPosts', 'activeConversation', 'chatDraft', 'activePost', 'selectedMember', 'sharePostTarget',
    'sharePanelOpen', 'draft', 'mediaItems', 'draftId', 'composerOpen', 'settingsReady', 'blockedUsers', 'authorizedApps',
    'specialModes', 'visibilitySettings', 'loginForm', 'loadPrivateData',
  ]
  const app = vm.runInContext(`${script}\n;({ ${names.join(', ')} })`, context)
  app.activeView.value = 'reset-password'
  app.resetForm.value.identifier = '13800138000'
  return {
    ...app, service, calls,
    sendWith: (fn) => { send = fn }, submitWith: (fn) => { submit = fn }, profileWith: (fn) => { privateProfile = fn },
  }
}

function fillPassword(app) {
  app.resetForm.value.code = '123456'
  app.resetForm.value.newPassword = 'ExamplePass123'
  app.resetForm.value.confirmPassword = 'ExamplePass123'
}

test('重置验证码 401 只发送一次，不刷新、不重放，保留业务错误', async () => {
  const service = setupApi()
  service.saveSession({ accessToken: 'old-access', refreshToken: 'old-refresh' })
  service.respond(async () => json({ code: 'INVALID_VERIFICATION_CODE', message: '验证失败' }, 401))
  await assert.rejects(service.api.resetPassword({ verificationId: 'challenge-1' }), { status: 401, code: 'INVALID_VERIFICATION_CODE' })
  assert.equal(service.calls.length, 1)
  assert.equal(service.calls[0].url, '/api/v1/auth/password/reset')
  assert.equal(service.getAccessToken(), 'old-access')
})

test('正常登录保存、会话存储和并发刷新重试流程不受影响', async () => {
  const service = setupApi()
  service.saveSession({ token: { accessToken: 'old-access', refreshToken: 'old-refresh' } }, false)
  const version = service.getSessionVersion()
  const refresh = deferred()
  let refreshCalls = 0
  service.respond(async (url, options) => {
    if (url.endsWith('/auth/refresh')) { refreshCalls += 1; return refresh.promise }
    return options.headers.get('Authorization') === 'Bearer new-access' ? json({ id: 'user-1' }) : json({}, 401)
  })
  const first = service.api.getMe()
  const second = service.api.getMe()
  await new Promise((done) => setImmediate(done))
  refresh.resolve(json({ token: { accessToken: 'new-access', refreshToken: 'new-refresh' } }))
  assert.equal((await first).id, 'user-1')
  assert.equal((await second).id, 'user-1')
  assert.equal(refreshCalls, 1)
  assert.equal(service.getSessionVersion(), version)
  assert.equal(service.window.localStorage.getItem('c53-access-token'), null)
  assert.equal(service.window.sessionStorage.getItem('c53-refresh-token'), 'new-refresh')
})

test('clearSession 后在途 refresh 不恢复 token 或重放旧请求', async () => {
  const service = setupApi()
  service.saveSession({ accessToken: 'old-access', refreshToken: 'old-refresh' })
  const refresh = deferred()
  service.respond(async (url) => url.endsWith('/auth/refresh') ? refresh.promise : json({}, 401))
  const request = service.api.getMe()
  await new Promise((done) => setImmediate(done))
  service.clearSession()
  refresh.resolve(json({ accessToken: 'stale-access', refreshToken: 'stale-refresh' }))
  await assert.rejects(request, { code: 'SESSION_CHANGED' })
  assert.equal(service.getAccessToken(), '')
  assert.equal(service.getRefreshToken(), '')
  assert.equal(service.calls.length, 2)
})

test('会话清理后丢弃在途私有响应；重置成功的 204 仍能完成', async () => {
  const service = setupApi()
  service.saveSession({ accessToken: 'old-access' })
  const response = deferred()
  service.respond(() => response.promise)
  const pending = service.api.getMe()
  service.clearSession()
  response.resolve(json({ nickname: '旧用户' }))
  await assert.rejects(pending, { code: 'SESSION_CHANGED' })
  service.respond(async () => new Response(null, { status: 204 }))
  assert.equal(await service.api.resetPassword({}), null)
})

test('手机号发送保存挑战与脱敏目标、按服务端零秒倒计时，提交携带 verificationId', async () => {
  const app = setupApp()
  await app.sendResetCode()
  assert.equal(app.resetVerificationId.value, 'challenge-1')
  assert.equal(app.resetMaskedTarget.value, '138****8000')
  assert.equal(app.resetCodeCountdown.value, 0)
  fillPassword(app)
  await app.submitPasswordReset()
  const body = app.calls.find((call) => call.kind === 'reset').body
  assert.equal(body.identifierType, 'PHONE')
  assert.equal(body.identifier, '13800138000')
  assert.equal(body.verificationId, 'challenge-1')
  assert.equal(body.code, '123456')
  assert.equal(body.newPassword, 'ExamplePass123')
  assert.equal('confirmPassword' in body, false)
})

test('邮箱归一化与修改目标立即清空挑战、验证码和倒计时', async () => {
  const app = setupApp()
  app.changeResetIdentifierType('EMAIL')
  app.resetForm.value.identifier = ' Member@Example.com '
  app.sendWith(async () => ({ verificationId: 'email-challenge', maskedTarget: 'm***@example.com', resendAfterSeconds: 30 }))
  await app.sendResetCode()
  assert.equal(app.calls[0].body.identifier, 'member@example.com')
  assert.equal(app.resetCodeCountdown.value, 30)
  app.resetForm.value.code = '123456'
  app.resetForm.value.identifier = 'other@example.com'
  assert.equal(app.resetVerificationId.value, '')
  assert.equal(app.resetForm.value.code, '')
  assert.equal(app.resetCodeCountdown.value, 0)
  assert.equal(app.resetMaskedTarget.value, '')
})

test('A→B→A 后迟到发送响应不能恢复旧挑战；发送期间防重复', async () => {
  const app = setupApp()
  const response = deferred()
  app.sendWith(() => response.promise)
  const first = app.sendResetCode()
  await app.sendResetCode()
  assert.equal(app.calls.length, 1)
  app.resetForm.value.identifier = '13900139000'
  app.resetForm.value.identifier = '13800138000'
  response.resolve({ verificationId: 'stale-challenge', resendAfterSeconds: 60 })
  await first
  assert.equal(app.resetVerificationId.value, '')
  assert.equal(app.resetCodeCountdown.value, 0)
  assert.equal(app.resetCodeSending.value, false)
})

test('修改目标或离开页面后，迟到发送失败不覆盖当前错误', async () => {
  const app = setupApp()
  const response = deferred()
  app.sendWith(() => response.promise)
  const pending = app.sendResetCode()
  app.openLogin()
  app.resetError.value = '当前错误'
  response.reject(new Error('过期请求错误'))
  await pending
  assert.equal(app.resetError.value, '当前错误')
  assert.equal(app.resetVerificationId.value, '')
})

test('重新发送立即清空旧挑战，发送失败不能继续使用旧码或伪造倒计时', async () => {
  const app = setupApp()
  await app.sendResetCode()
  app.resetForm.value.code = '123456'
  app.sendWith(async () => { throw new Error('服务暂不可用') })
  await app.sendResetCode()
  assert.equal(app.resetVerificationId.value, '')
  assert.equal(app.resetForm.value.code, '')
  assert.equal(app.resetCodeCountdown.value, 0)
  assert.equal(app.resetError.value, '服务暂不可用')
})

test('无挑战不能提交；重置提交防重复且不能同时发送新验证码', async () => {
  const app = setupApp()
  fillPassword(app)
  await app.submitPasswordReset()
  assert.equal(app.calls.length, 0)
  await app.sendResetCode()
  fillPassword(app)
  const response = deferred()
  app.submitWith(() => response.promise)
  const pending = app.submitPasswordReset()
  assert.equal(app.resetSubmitting.value, true)
  await app.submitPasswordReset()
  await app.sendResetCode()
  assert.equal(app.calls.filter((call) => call.kind === 'reset').length, 1)
  assert.equal(app.calls.filter((call) => call.kind === 'send').length, 1)
  response.resolve(null)
  await pending
  assert.equal(app.resetSubmitting.value, false)
})

test('成功清空 token、私有页面与敏感表单并返回密码登录', async () => {
  const app = setupApp()
  app.service.saveSession({ accessToken: 'old-access', refreshToken: 'old-refresh' })
  app.isAuthenticated.value = true
  app.activeAccountId.value = 'old-account'
  app.profileInfo.value = { nickname: '旧用户', phone: '13800138000' }
  app.profileDraft.value = { nickname: '旧用户' }
  for (const name of ['profilePosts', 'savedPosts', 'notifications', 'conversations', 'chatMessages', 'savedAccounts', 'followers', 'following', 'posts', 'memberPosts', 'blockedUsers', 'authorizedApps']) {
    app[name].value = [{ id: 'private' }]
  }
  app.draft.value = '私有草稿'
  app.draftId.value = 'draft-1'
  app.mediaItems.value = [{ mediaId: 'private-media' }]
  app.composerOpen.value = true
  app.settingsReady.value = true
  app.specialModes.value = { private: true }
  app.activePost.value = { id: 'private-post' }
  app.loginForm.value.password = 'old-password'
  await app.sendResetCode()
  fillPassword(app)
  await app.submitPasswordReset()
  await nextTick()
  assert.equal(app.service.getAccessToken(), '')
  assert.equal(app.service.getRefreshToken(), '')
  assert.equal(app.isAuthenticated.value, false)
  assert.equal(app.activeAccountId.value, '')
  assert.equal(app.activeView.value, 'login')
  assert.equal(app.loginMode.value, 'password')
  assert.equal(app.profileInfo.value.nickname, '')
  assert.equal(app.profileDraft.value.nickname, '')
  assert.equal(app.draft.value, '')
  assert.equal(app.mediaItems.value.length, 0)
  assert.equal(app.composerOpen.value, false)
  assert.equal(app.activePost.value, null)
  assert.equal(app.settingsReady.value, false)
  assert.equal(app.specialModes.value.private, false)
  assert.equal(app.resetForm.value.newPassword, '')
  assert.equal(app.resetForm.value.confirmPassword, '')
  assert.equal(app.loginForm.value.password, '')
  assert.equal(app.resetVerificationId.value, '')
  for (const name of ['profilePosts', 'savedPosts', 'notifications', 'conversations', 'chatMessages', 'savedAccounts', 'followers', 'following', 'posts', 'memberPosts', 'blockedUsers', 'authorizedApps']) {
    assert.equal(app[name].value.length, 0, name)
  }
})

test('重置后在途私有聚合响应不能回填旧用户资料', async () => {
  const app = setupApp()
  app.isAuthenticated.value = true
  const response = deferred()
  app.profileWith(() => response.promise)
  const pending = app.loadPrivateData()
  await app.sendResetCode()
  fillPassword(app)
  await app.submitPasswordReset()
  response.resolve({ user: { nickname: '迟到的旧用户' } })
  await pending
  assert.equal(app.profileInfo.value.nickname, '')
  assert.equal(app.savedAccounts.value.length, 0)
})

test('网络不确定失败保留表单与登录状态、无自动重试，并提示新密码登录或重获验证码', async () => {
  const app = setupApp()
  app.isAuthenticated.value = true
  await app.sendResetCode()
  fillPassword(app)
  app.submitWith(async () => { throw Object.assign(new Error('网络错误'), { code: 'NETWORK_ERROR' }) })
  await app.submitPasswordReset()
  assert.equal(app.activeView.value, 'reset-password')
  assert.equal(app.isAuthenticated.value, true)
  assert.equal(app.resetForm.value.newPassword, 'ExamplePass123')
  assert.equal(app.resetSubmitting.value, false)
  assert.match(app.resetError.value, /新密码登录或重新获取验证码/)
  assert.equal(app.calls.filter((call) => call.kind === 'reset').length, 1)
})
