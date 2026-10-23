<script setup>
defineProps({
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
})

defineEmits([
  'update:draft', 'update:composerOpen', 'update:draftTopic', 'update:feedFilter',
  'login', 'publish', 'navigate', 'open-post', 'toggle-like', 'go-comments',
  'share', 'toggle-save', 'add-comment', 'follow',
])
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
        <div class="composer-main"><span class="avatar ink">许</span><textarea :value="draft" :rows="composerOpen ? 4 : 1" placeholder="分享一个想法、进展，或向大家提问…" @input="$emit('update:draft', $event.target.value)" @focus="$emit('update:composerOpen', true)"></textarea></div>
        <Transition name="reveal">
          <div v-if="composerOpen" class="composer-footer">
            <div class="composer-tools">
              <button type="button" aria-label="添加图片">▧</button><button type="button" aria-label="添加链接">↗</button>
              <select :value="draftTopic" aria-label="选择话题" @change="$emit('update:draftTopic', $event.target.value)"><option v-for="topic in topics" :key="topic.name">{{ topic.name }}</option></select>
            </div>
            <div class="composer-actions"><span>{{ draft.length }}/600</span><button class="text-button" type="button" @click="$emit('update:composerOpen', false); $emit('update:draft', '')">取消</button><button class="primary-button" type="button" :disabled="!draft.trim()" @click="$emit('publish')">发布动态</button></div>
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
      <div v-else-if="!posts.length" class="empty-state"><span>◇</span><h2>{{ error ? '内容暂时无法加载' : '这里暂时还是空的' }}</h2><p>{{ error || '还没有可展示的公开文章。' }}</p></div>
    </section>

    <aside class="right-column">
      <section class="side-card">
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
