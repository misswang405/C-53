<script setup>
import ProfilePostList from './ProfilePostList.vue'

defineProps({
  profileInfo: { type: Object, required: true },
  profileInitial: { type: String, required: true },
  posts: { type: Array, required: true },
  responseCount: { type: Function, required: true },
})

defineEmits(['share-profile', 'edit', 'scroll-content', 'open-connections', 'open-post', 'toggle-like', 'go-comments', 'share'])
</script>

<template>
  <main class="profile-page">
    <section class="profile-hero">
      <div class="profile-identity">
        <div class="profile-avatar"><img v-if="profileInfo.avatar" :src="profileInfo.avatar" alt="" /><span v-else>{{ profileInitial }}</span><i aria-label="当前在线"></i></div>
        <div class="profile-name"><p class="kicker">C—53 MEMBER · 2025</p><h1>{{ profileInfo.nickname }}</h1><span>@{{ profileInfo.username }}</span></div>
      </div>
      <div class="profile-actions"><button class="profile-share" type="button" @click="$emit('share-profile')">↗</button><button class="edit-profile" type="button" @click="$emit('edit')">编辑个人资料</button></div>
      <p class="profile-bio">{{ profileInfo.bio }}</p>
      <div class="profile-meta"><span><i class="meta-pin"></i>{{ profileInfo.city || profileInfo.region }} / C—53</span><span><i class="meta-link"></i>c53.space/xuzhiyuan</span></div>
      <div class="profile-stats">
        <button class="profile-stat" type="button" @click="$emit('scroll-content')"><strong>{{ posts.length }}</strong><span>公开内容</span><small>查看内容 ↓</small></button>
        <button class="profile-stat" type="button" @click="$emit('open-connections', 'followers')"><strong>{{ profileInfo.stats?.followerCount || 0 }}</strong><span>关注者</span><small>查看列表 ↗</small></button>
        <button class="profile-stat" type="button" @click="$emit('open-connections', 'following')"><strong>{{ profileInfo.stats?.followingCount || 0 }}</strong><span>正在关注</span><small>查看列表 ↗</small></button>
        <div class="profile-stat"><strong>{{ profileInfo.stats?.viewCount || 0 }}</strong><span>内容浏览</span><small>近 30 天</small></div>
      </div>
    </section>
    <section id="profile-content" class="profile-content">
      <header class="profile-content-heading"><div><p class="kicker">PUBLISHED</p><h2>我的发布</h2></div><span>按时间排序</span></header>
      <ProfilePostList own-profile :posts="posts" :response-count="responseCount" @open-post="$emit('open-post', $event)" @toggle-like="$emit('toggle-like', $event)" @go-comments="$emit('go-comments', $event)" @share="$emit('share', $event)" />
    </section>
  </main>
</template>
