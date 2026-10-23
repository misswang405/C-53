<script setup>
defineProps({
  posts: { type: Array, required: true },
  ownProfile: { type: Boolean, default: false },
  responseCount: { type: Function, required: true },
})

defineEmits(['open-post', 'toggle-like', 'go-comments', 'share'])
</script>

<template>
  <div class="profile-post-list">
    <article
      v-for="post in posts"
      :key="post.id"
      class="profile-post-row"
      tabindex="0"
      :aria-label="'查看内容：' + post.title"
      @click="$emit('open-post', post)"
      @keydown.enter.prevent="$emit('open-post', post)"
    >
      <time><strong>{{ ownProfile ? post.date : 'NEW' }}</strong><span>{{ ownProfile ? post.year : post.time }}</span></time>
      <div class="profile-timeline"><i></i></div>
      <div class="profile-post-main">
        <div class="profile-post-meta"><span># {{ post.topic }}</span><small>{{ post.time }}</small></div>
        <h3>{{ post.title }}</h3><p>{{ post.body }}</p>
        <footer @click.stop>
          <button class="like-button" type="button" :class="{ active: post.liked }" :aria-pressed="post.liked" @click="$emit('toggle-like', post)"><span class="heart-icon">{{ post.liked ? '♥' : '♡' }}</span>{{ post.likes }}</button>
          <button type="button" @click="$emit('go-comments', post)"><span class="comment-icon"></span>{{ responseCount(post) }}</button>
          <button type="button" @click="$emit('share', post)"><span class="share-icon"></span>分享</button>
          <button class="profile-read" type="button" @click="$emit('open-post', post)">阅读全文 <span>→</span></button>
        </footer>
      </div>
    </article>
  </div>
</template>
