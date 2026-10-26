<script setup>
// 顶部导航：提供搜索、通知、发布和账号菜单入口。
// 搜索词、菜单开关及登录用户信息由父组件维护。
defineProps({
  searchQuery: { type: String, default: '' },
  isAuthenticated: { type: Boolean, required: true },
  notificationsOpen: { type: Boolean, default: false },
  profileOpen: { type: Boolean, default: false },
  profileInfo: { type: Object, required: true },
  profileInitial: { type: String, default: '' },
  notifications: { type: Array, default: () => [] },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits([
  'update:searchQuery',
  'open-mobile-nav',
  'toggle-notifications',
  'close-notifications',
  'start-publishing',
  'toggle-profile',
  'mark-all-notifications-read',
  'navigate',
  'open-settings',
  'open-login',
])
</script>

<template>
  <header class="topbar">
    <button class="mobile-menu icon-button" type="button" aria-label="打开导航" @click="$emit('open-mobile-nav')">
      <span></span><span></span>
    </button>

    <label class="search-box">
      <span class="search-icon" aria-hidden="true"></span>
      <input
        :value="searchQuery"
        type="search"
        placeholder="搜索创作者、话题或内容"
        @input="$emit('update:searchQuery', $event.target.value)"
      />
      <kbd>⌘ K</kbd>
    </label>

    <div class="top-actions">
      <template v-if="isAuthenticated">
        <div class="popover-wrap">
          <button class="icon-button notification-button" type="button" aria-label="通知" @click="$emit('toggle-notifications')">
            <span class="bell-icon"></span><i></i>
          </button>
          <Transition name="pop">
            <div v-if="notificationsOpen" class="popover notification-panel">
              <div class="popover-heading"><strong>通知</strong><button v-if="notifications.length" type="button" @click="$emit('mark-all-notifications-read')">全部已读</button></div>
              <article v-for="notification in notifications" :key="notification.id">
                <span class="avatar mini" :class="notification.actor?.tone">{{ notification.actor?.initials }}</span>
                <p><strong>{{ notification.actor?.name }}</strong> {{ notification.preview }}<br /><small>{{ notification.createdAt }}</small></p>
              </article>
              <p v-if="!notifications.length" class="popover-empty">暂时没有新通知</p>
            </div>
          </Transition>
        </div>

        <button class="create-button" type="button" @click="$emit('start-publishing')"><span>＋</span> 发布</button>

        <div class="popover-wrap">
          <button class="profile-button" type="button" aria-label="个人菜单" @click="$emit('toggle-profile')">
            <span class="avatar ink">
              <img v-if="profileInfo.avatar" :src="profileInfo.avatar" alt="" />
              <template v-else>{{ profileInitial }}</template>
            </span>
            <span class="status-dot"></span>
          </button>
          <Transition name="pop">
            <div v-if="profileOpen" class="popover profile-panel">
              <strong>{{ profileInfo.nickname }}</strong>
              <small>@{{ profileInfo.username }}</small>
              <button type="button" @click="$emit('navigate', 'profile')">个人主页</button>
              <button type="button" @click="$emit('open-settings')">设置</button>
            </div>
          </Transition>
        </div>
      </template>

      <button v-else class="login-entry" type="button" @click="$emit('open-login')">立即登录</button>
    </div>
  </header>
</template>
