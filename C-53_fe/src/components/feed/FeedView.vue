<script setup>
// 信息流页面：展示动态、收藏列表及草稿编辑入口。
import { computed } from 'vue'
import MediaUploader from './MediaUploader.vue'

// 动态列表、草稿和上传状态由父组件统一提供，避免页面切换时出现多份状态。
const props = defineProps({
  activeView: { type: String, required: true },
  isAuthenticated: { type: Boolean, required: true },
  draft: { type: String, default: '' },
  composerOpen: { type: Boolean, default: false },
  draftTopic: { type: String, required: true },
  topics: { type: Array, required: true },
  feedFilter: { type: String, required: true },
  posts: { type: Array, required: true },
  people: { type: Array, required: true },
  responseCount: { type: Function, required: true },
  loading: { type: Boolean, default: false },
  error: { type: String, default: '' },
  mediaItems: { type: Array, default: () => [] },
  draftState: { type: String, default: '' },
  uploading: { type: Boolean, default: false },
  busy: { type: Boolean, default: false },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits([
  'update:draft', 'update:composerOpen', 'update:draftTopic', 'update:feedFilter',
  'update:mediaItems', 'update:uploading', 'login', 'publish', 'schedule', 'cancel-draft', 'media-error',
  'navigate', 'open-post', 'toggle-like', 'go-comments', 'share', 'toggle-save',
  'add-comment', 'follow',
])

// 按码点计数，让 emoji 与中文和用户直觉一致（与后端 600 字限制口径相同）
const draftLength = computed(() => Array.from(props.draft || '').length)
// 上传和发布期间都禁止提交，避免媒体还没就绪就发出动态
const canSubmit = computed(() => Boolean(props.draft.trim()) && !props.busy && !props.uploading)
const draftStateText = computed(() => ({
  saving: '保存中…',
  saved: '草稿已保存',
  error: '草稿保存失败',
}[props.draftState] || ''))
// 关注流与公开流使用不同空态：未登录先提示登录，已登录提示去关注
const emptyTitle = computed(() => {
  if (props.feedFilter !== '正在关注') return '这里暂时还是空的'
  return props.isAuthenticated ? '还没有关注动态' : '登录后查看关注动态'
})
const emptyHint = computed(() => {
  if (props.feedFilter !== '正在关注') return '还没有可展示的公开文章。'
  return props.isAuthenticated
    ? '关注感兴趣的创作者，他们的更新会出现在这里。'
    : '登录后即可看到你关注的创作者的最新动态。'
})
</script>

<template>
  <main class="content-grid">
    <section class="feed-column">
      <div class="page-intro">
        <div>
          <p class="kicker">{{ activeView === 'saved' ? 'YOUR COLLECTION' : 'C—53 COMMUNITY' }}</p>
          <h1>{{ activeView === 'saved' ? '稍后阅读' : '和有趣的人，一起把想法做出来。' }}</h1>
          <p v-if="activeView === 'feed'">分享正在发生的事，也参与别人的世界。</p>
        </div>
        <div v-if="activeView === 'feed' && people.length" class="member-stack" aria-label="今日活跃成员">
          <span v-for="person in people.slice(0, 3)" :key="person.id" class="avatar mini" :class="person.tone">{{ person.initials }}</span>
          <span v-if="people.length > 3" class="avatar mini gold">＋{{ people.length - 3 }}</span>
        </div>
      </div>

      <section v-if="activeView === 'feed' && isAuthenticated" class="composer-card" :class="{ expanded: composerOpen }">
        <div class="composer-main"><span class="avatar ink">许</span><textarea :value="draft" :rows="composerOpen ? 4 : 1" maxlength="600" placeholder="分享一个想法、进展，或向大家提问…" @input="$emit('update:draft', $event.target.value)" @focus="$emit('update:composerOpen', true)"></textarea></div>
        <Transition name="reveal">
          <div v-if="composerOpen" class="composer-expanded">
            <MediaUploader
              :model-value="mediaItems"
              :max-images="9"
              :max-videos="1"
              :disabled="busy"
              @update:model-value="$emit('update:mediaItems', $event)"
              @update:uploading="$emit('update:uploading', $event)"
              @error="$emit('media-error', $event)"
            />
            <div class="composer-footer">
              <div class="composer-tools">
                <select :value="draftTopic" aria-label="选择话题" @change="$emit('update:draftTopic', $event.target.value)"><option v-for="topic in topics" :key="topic.name">{{ topic.name }}</option></select>
              </div>
              <div class="composer-actions">
                <span class="draft-state" :class="draftState">{{ draftStateText }}</span>
                <span>{{ draftLength }}/600</span>
                <button class="text-button" type="button" :disabled="busy" @click="$emit('cancel-draft')">取消</button>
                <button class="text-button" type="button" :disabled="!canSubmit" @click="$emit('schedule')">定时</button>
                <button class="primary-button" type="button" :disabled="!canSubmit" @click="$emit('publish')">{{ busy ? '发布中…' : '发布动态' }}</button>
              </div>
            </div>
          </div>
        </Transition>
      </section>

      <button v-else-if="activeView === 'feed'" class="guest-composer" type="button" @click="$emit('login')"><span class="avatar">访</span><span><strong>登录后分享你的想法</strong><small>参与讨论、收藏内容并认识更多创作者</small></span><b>立即登录 →</b></button>

      <div class="feed-toolbar">
        <div v-if="activeView === 'feed'" class="segmented"><button v-for="filter in ['为你推荐', '正在关注', '最新发布']" :key="filter" type="button" :class="{ active: feedFilter === filter }" @click="$emit('update:feedFilter', filter)">{{ filter }}</button></div>
        <p v-else>{{ posts.length }} 条收藏</p><button class="sort-button" type="button">最新 <span>⌄</span></button>
      </div>

      <TransitionGroup name="feed" tag="div" class="feed-list">
        <article v-for="post in posts" :key="post.id" class="post-card" role="button" tabindex="0" :aria-label="'查看动态详情：' + post.title" @click="$emit('open-post', post)" @keydown.enter.prevent="$emit('open-post', post)" @keydown.space.prevent="$emit('open-post', post)">
          <header class="post-header"><span class="avatar" :class="post.tone">{{ post.initials }}</span><div class="author-copy"><strong>{{ post.author }}</strong><span>{{ post.handle }} · {{ post.role }}</span></div><time>{{ post.time }}</time><button class="more-button" type="button" aria-label="更多操作" @click.stop>•••</button></header>
          <div class="post-copy"><button class="topic-pill" type="button" @click.stop="$emit('navigate', 'discover')"># {{ post.topic }}</button><h2>{{ post.title }}</h2><p>{{ post.body }}</p><div class="post-note"><span></span>{{ post.note }}</div></div>
          <div v-if="post.media?.length" class="post-media" :class="{ 'single-video': post.media[0].mediaType === 'VIDEO' }">
            <template v-for="item in post.media.slice(0, 3)" :key="item.mediaId">
              <img v-if="item.mediaType === 'IMAGE'" :src="item.url" alt="" loading="lazy" />
              <video v-else :src="item.url" preload="metadata" muted playsinline></video>
            </template>
            <span v-if="post.media.length > 3" class="media-more">＋{{ post.media.length - 3 }}</span>
          </div>
          <footer class="post-actions" @click.stop>
            <button class="like-button" type="button" :class="{ active: post.liked }" :aria-pressed="post.liked" @click="$emit('toggle-like', post)"><span class="heart-icon">{{ post.liked ? '♥' : '♡' }}</span>{{ post.likes }}</button>
            <button type="button" @click="$emit('go-comments', post)"><span class="comment-icon"></span>{{ responseCount(post) }}</button>
            <button type="button" @click="$emit('share', post)"><span class="share-icon"></span>分享</button>
            <button class="save-action" type="button" :class="{ active: post.saved }" aria-label="收藏" @click="$emit('toggle-save', post)"><span class="save-icon"></span></button>
          </footer>
          <Transition name="reveal">
            <section v-if="post.commentsOpen" class="comments" @click.stop>
              <article v-for="comment in post.comments" :key="comment.id"><span class="avatar tiny">{{ comment.initials }}</span><p><strong>{{ comment.author }}</strong>{{ comment.text }}</p></article>
              <form class="reply-box" @submit.prevent="$emit('add-comment', post)"><span class="avatar tiny ink">许</span><input v-model="post.reply" type="text" placeholder="认真回应这个想法…" /><button type="submit" :disabled="!post.reply.trim()">发送</button></form>
            </section>
          </Transition>
        </article>
      </TransitionGroup>
      <div v-if="loading" class="empty-state"><span>◌</span><h2>正在加载内容</h2><p>请稍候。</p></div>
      <div v-else-if="!posts.length" class="empty-state"><span>◇</span><h2>{{ error ? '内容暂时无法加载' : emptyTitle }}</h2><p>{{ error || emptyHint }}</p></div>
    </section>

    <aside class="right-column">
      <section v-if="people.length" class="side-card">
        <div class="card-heading"><h2>值得认识的人</h2><button type="button" @click="$emit('navigate', 'discover')">查看全部</button></div>
        <article v-for="person in people" :key="person.id" class="person-row"><span class="avatar" :class="person.tone">{{ person.initials }}</span><div><strong>{{ person.name }}</strong><span>{{ person.field }}</span></div><button type="button" :class="{ followed: person.followed }" @click="$emit('follow', person)">{{ person.followed ? '已关注' : '关注' }}</button></article>
      </section>
      <section class="side-card topic-card">
        <div class="card-heading"><h2>热门话题</h2><button type="button" @click="$emit('navigate', 'discover')">发现</button></div>
        <button v-for="(topic, index) in topics" :key="topic.name" class="trend-row" type="button" @click="$emit('navigate', 'discover')"><span>0{{ index + 1 }}</span><div><strong>{{ topic.name }}</strong><small>{{ topic.count }} 条讨论</small></div><i :style="{ background: topic.color }"></i></button>
      </section>
      <p class="side-footer">C—53 · 让想法彼此照亮<br />社区公约　隐私　关于</p>
    </aside>
  </main>
</template>
