<script setup>
// 设置页面：展示账号、安全、隐私和偏好设置入口。
// 各设置分组共享父组件维护的对象，持久化与账号操作由父组件处理。
defineProps({
  settingsNav: { type: Array, required: true },
  activeSection: { type: String, required: true },
  profileInfo: { type: Object, required: true },
  profileInitial: { type: String, required: true },
  accounts: { type: Array, required: true },
  activeAccountId: { type: String, required: true },
  permissionItems: { type: Array, required: true },
  permissionStates: { type: Object, required: true },
  visibilitySettings: { type: Object, required: true },
  visibilityOptions: { type: Array, required: true },
  socialSettings: { type: Object, required: true },
  socialOptions: { type: Array, required: true },
  blockedListOpen: { type: Boolean, default: false },
  blockedUsers: { type: Array, required: true },
  recommendationSettings: { type: Object, required: true },
  authorizedApps: { type: Array, required: true },
  specialModes: { type: Object, required: true },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits([
  'back',
  'navigate-section',
  'switch-account',
  'add-account',
  'logout',
  'open-permission',
  'toggle-blocked-list',
  'remove-blocked',
  'export-data',
  'revoke-app',
  'update-mode',
])
</script>

<template>
  <main class="settings-page">
    <header class="settings-hero">
      <div><p class="kicker">ACCOUNT CONTROL</p><h1>设置与隐私</h1><p>集中管理你的系统权限、内容边界和数据使用方式。</p></div>
      <button class="detail-back" type="button" @click="$emit('back')"><span>←</span> 返回个人主页</button>
    </header>

    <div class="settings-layout">
      <aside class="settings-nav" aria-label="设置导航">
        <button
          v-for="item in settingsNav"
          :key="item.id"
          :data-settings-section="item.id"
          type="button"
          :class="{ active: activeSection === item.id }"
          @click="$emit('navigate-section', item.id)"
        ><span>{{ item.icon }}</span>{{ item.label }}</button>
      </aside>

      <div class="settings-content">
        <section id="settings-account" class="settings-card account-settings-card" style="--section-accent: #17181d">
          <header class="settings-card-heading"><div><span>00</span><h2>账户与登录</h2><p>管理当前账号、切换用户或安全退出登录。</p></div><small>账户安全</small></header>
          <div class="current-account-card">
            <span class="avatar account-avatar" :class="accounts.find((account) => account.id === activeAccountId)?.tone || 'ink'">{{ profileInitial }}</span>
            <div><small>当前登录</small><strong>{{ profileInfo.nickname }}</strong><span>@{{ profileInfo.username }} · {{ profileInfo.city }}</span></div>
            <i><span></span>在线</i>
          </div>
          <div class="saved-account-list">
            <div class="settings-subheading"><strong>切换用户</strong><span>{{ accounts.length }} 个已保存账号</span></div>
            <button v-for="account in accounts" :key="account.id" type="button" :class="{ active: account.id === activeAccountId }" @click="$emit('switch-account', account)">
              <span class="avatar mini" :class="account.tone">{{ account.initials }}</span>
              <span><strong>{{ account.nickname }}</strong><small>@{{ account.username }}</small></span>
              <b>{{ account.id === activeAccountId ? '当前账号' : '切换' }}</b>
            </button>
          </div>
          <footer class="account-settings-actions">
            <button type="button" @click="$emit('add-account')">＋ 添加其他账号</button>
            <button class="logout-button" type="button" @click="$emit('logout')">退出登录</button>
          </footer>
        </section>

        <section id="settings-permissions" class="settings-card" style="--section-accent: #5965f2">
          <header class="settings-card-heading"><div><span>01</span><h2>系统权限入口</h2><p>跳转到系统设置，管理设备能力的访问权限。</p></div><small>设备权限</small></header>
          <div class="permission-grid">
            <article v-for="permission in permissionItems" :key="permission.id" class="permission-item">
              <span class="settings-item-icon">{{ permission.icon }}</span>
              <div><strong>{{ permission.name }}</strong><p>{{ permission.description }}</p><small>{{ permissionStates[permission.id] }}</small></div>
              <button type="button" @click="$emit('open-permission', permission)">系统设置 <span>↗</span></button>
            </article>
          </div>
        </section>

        <section id="settings-visibility" class="settings-card" style="--section-accent: #2a9d78">
          <header class="settings-card-heading"><div><span>02</span><h2>内容可见范围</h2><p>分别决定资料与内容可以被哪些人看到。</p></div><small>隐私边界</small></header>
          <div class="settings-rows">
            <label class="settings-row"><div><strong>个人资料</strong><span>头像、昵称、简介和地区信息</span></div><select v-model="visibilitySettings.profile"><option v-for="option in visibilityOptions" :key="option">{{ option }}</option></select></label>
            <label class="settings-row"><div><strong>动态内容</strong><span>你发布的公开动态和讨论</span></div><select v-model="visibilitySettings.posts"><option v-for="option in visibilityOptions" :key="option">{{ option }}</option></select></label>
            <label class="settings-row"><div><strong>收藏内容</strong><span>稍后阅读和收藏的内容列表</span></div><select v-model="visibilitySettings.saved"><option v-for="option in visibilityOptions" :key="option">{{ option }}</option></select></label>
            <label class="settings-row"><div><strong>关注列表</strong><span>关注者和正在关注的人</span></div><select v-model="visibilitySettings.following"><option v-for="option in visibilityOptions" :key="option">{{ option }}</option></select></label>
          </div>
        </section>

        <section id="settings-social" class="settings-card" style="--section-accent: #e6684a">
          <header class="settings-card-heading"><div><span>03</span><h2>社交权限</h2><p>控制他人与你发起互动的方式。</p></div><small>互动管理</small></header>
          <div class="settings-rows">
            <label class="settings-row"><div><strong>谁可以私信我</strong><span>限制新会话的发起范围</span></div><select v-model="socialSettings.messages"><option v-for="option in socialOptions" :key="option">{{ option }}</option></select></label>
            <label class="settings-row"><div><strong>谁可以评论我</strong><span>控制动态下的评论权限</span></div><select v-model="socialSettings.comments"><option v-for="option in socialOptions" :key="option">{{ option }}</option></select></label>
            <label class="settings-row"><div><strong>谁可以 @我</strong><span>控制内容和评论中的提及权限</span></div><select v-model="socialSettings.mentions"><option v-for="option in socialOptions" :key="option">{{ option }}</option></select></label>
            <div class="settings-row expandable-row">
              <div><strong>黑名单管理</strong><span>{{ blockedUsers.length }} 位用户无法与你互动</span></div>
              <button type="button" :aria-expanded="blockedListOpen" @click="$emit('toggle-blocked-list')">{{ blockedListOpen ? '收起' : '管理名单' }} <span>{{ blockedListOpen ? '↑' : '→' }}</span></button>
            </div>
            <Transition name="reveal">
              <div v-if="blockedListOpen" class="management-list">
                <article v-for="user in blockedUsers" :key="user.id"><span class="avatar mini">{{ user.initials }}</span><div><strong>{{ user.name }}</strong><small>{{ user.handle }}</small></div><button type="button" @click="$emit('remove-blocked', user)">移出</button></article>
                <p v-if="!blockedUsers.length" class="management-empty">黑名单中暂无用户</p>
              </div>
            </Transition>
          </div>
        </section>

        <section id="settings-recommendation" class="settings-card" style="--section-accent: #d29b32">
          <header class="settings-card-heading"><div><span>04</span><h2>个性化推荐</h2><p>决定推荐系统可以如何使用你的行为偏好。</p></div><small>算法偏好</small></header>
          <div class="settings-rows">
            <label class="settings-row switch-row"><div><strong>个性化内容推荐</strong><span>根据阅读、关注与互动推荐更相关的内容</span></div><input v-model="recommendationSettings.content" type="checkbox" /><span class="toggle-switch" aria-hidden="true"></span></label>
            <label class="settings-row switch-row"><div><strong>个性化广告推荐</strong><span>允许使用站内活动信息优化广告相关性</span></div><input v-model="recommendationSettings.ads" type="checkbox" /><span class="toggle-switch" aria-hidden="true"></span></label>
          </div>
        </section>

        <section id="settings-data" class="settings-card" style="--section-accent: #6573a8">
          <header class="settings-card-heading"><div><span>05</span><h2>数据管理</h2><p>下载个人数据，检查并撤销第三方授权。</p></div><small>数据控制</small></header>
          <div class="data-export-card">
            <span class="settings-item-icon">↧</span>
            <div><strong>导出个人数据</strong><p>包含个人资料、动态、关注关系及当前隐私设置，导出为 JSON 文件。</p></div>
            <button type="button" @click="$emit('export-data')">开始导出</button>
          </div>
          <div class="authorized-apps">
            <div class="settings-subheading"><strong>已授权的第三方应用</strong><span>{{ authorizedApps.length }} 个</span></div>
            <article v-for="app in authorizedApps" :key="app.id"><span class="app-mark">{{ app.icon }}</span><div><strong>{{ app.name }}</strong><span>{{ app.scope }} · 最近使用 {{ app.lastUsed }}</span></div><button type="button" @click="$emit('revoke-app', app)">解除授权</button></article>
            <p v-if="!authorizedApps.length" class="management-empty">当前没有已授权的第三方应用</p>
          </div>
        </section>

        <section id="settings-modes" class="settings-card" style="--section-accent: #845ec2">
          <header class="settings-card-heading"><div><span>06</span><h2>特殊模式</h2><p>根据使用场景调整内容、字号和隐私策略。</p></div><small>体验模式</small></header>
          <div class="mode-grid">
            <label class="mode-card" :class="{ active: specialModes.teen }"><span>青</span><div><strong>青少年模式</strong><p>减少使用时长并过滤不适宜内容</p></div><input v-model="specialModes.teen" type="checkbox" @change="$emit('update-mode', 'teen')" /><i class="toggle-switch"></i></label>
            <label class="mode-card" :class="{ active: specialModes.elder }"><span>长</span><div><strong>长辈模式</strong><p>放大字号、按钮并简化页面层级</p></div><input v-model="specialModes.elder" type="checkbox" @change="$emit('update-mode', 'elder')" /><i class="toggle-switch"></i></label>
            <label class="mode-card" :class="{ active: specialModes.private }"><span>隐</span><div><strong>私密模式</strong><p>一键将所有内容调整为仅自己可见</p></div><input v-model="specialModes.private" type="checkbox" @change="$emit('update-mode', 'private')" /><i class="toggle-switch"></i></label>
          </div>
        </section>
      </div>
    </div>
  </main>
</template>
