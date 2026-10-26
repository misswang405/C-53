<script setup>
// 成员主页：展示其他成员的资料、关注关系和公开动态。
import ProfilePostList from './ProfilePostList.vue'

// 成员资料和动态由父组件加载，关注按钮文案复用统一关系规则。
defineProps({
  member: { type: Object, required: true },
  posts: { type: Array, required: true },
  responseCount: { type: Function, required: true },
  connectionLabel: { type: Function, required: true },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits(['back', 'toggle-connection', 'open-post', 'toggle-like', 'go-comments', 'share'])
</script>

<template>
  <main class="profile-page member-profile-page">
    <button class="detail-back member-back" type="button" @click="$emit('back')"><span>←</span> 返回我的主页</button>
    <section class="profile-hero member-profile-hero">
      <div class="profile-identity">
        <span class="avatar member-profile-avatar" :class="member.tone">{{ member.initials }}</span>
        <div class="profile-name"><p class="kicker">C—53 CREATOR</p><h1>{{ member.name }}</h1><span>{{ member.handle }} · {{ member.field }}</span></div>
      </div>
      <div class="profile-actions">
        <button class="member-follow-action" :class="{ mutual: member.followed && member.followsYou, followback: !member.followed && member.followsYou, followed: member.followed && !member.followsYou }" type="button" @click="$emit('toggle-connection', member)">{{ connectionLabel(member) }}</button>
      </div>
      <p class="profile-bio">持续分享关于{{ member.field }}的工作、观察与未完成的想法。欢迎从一条认真回应开始认识彼此。</p>
      <div class="profile-meta"><span><i class="meta-pin"></i>杭州 / C—53</span><span><i class="meta-link"></i>c53.space/{{ member.handle.replace('@', '') }}</span></div>
      <div class="profile-stats member-stats">
        <div class="profile-stat"><strong>{{ member.stats?.postCount ?? posts.length }}</strong><span>公开内容</span></div><div class="profile-stat"><strong>{{ member.stats?.followerCount || 0 }}</strong><span>关注者</span></div><div class="profile-stat"><strong>{{ member.stats?.followingCount || 0 }}</strong><span>正在关注</span></div><div class="profile-stat"><strong>{{ member.stats?.viewCount || 0 }}</strong><span>内容浏览</span></div>
      </div>
    </section>
    <section class="profile-content">
      <header class="profile-content-heading"><div><p class="kicker">PUBLISHED</p><h2>TA 的发布</h2></div><span>{{ posts.length }} 篇公开内容</span></header>
      <ProfilePostList :posts="posts" :response-count="responseCount" @open-post="$emit('open-post', $event)" @toggle-like="$emit('toggle-like', $event)" @go-comments="$emit('go-comments', $event)" @share="$emit('share', $event)" />
    </section>
  </main>
</template>
