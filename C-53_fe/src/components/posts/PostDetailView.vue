<script setup>
// 动态详情页面：展示正文、媒体、评论及作者操作入口。
// 接收动态详情及编辑状态；互动和发布操作向父组件发出事件。
defineProps({
  post: { type: Object, required: true },
  responseCount: { type: Function, required: true },
  replyingToId: { type: Number, default: null },
  nestedReply: { type: String, default: '' },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits([
  'back',
  'open-topic',
  'toggle-like',
  'go-comments',
  'share',
  'toggle-save',
  'start-reply',
  'update:nestedReply',
  'cancel-reply',
  'add-nested-reply',
  'add-comment',
])
</script>

<template>
  <main class="detail-page">
    <button class="detail-back" type="button" @click="$emit('back')"><span aria-hidden="true">←</span> 返回动态</button>

    <article class="detail-article">
      <header class="detail-header">
        <button class="topic-pill" type="button" @click="$emit('open-topic')"># {{ post.topic }}</button>
        <h1>{{ post.title }}</h1><p>{{ post.body }}</p>
        <div class="detail-author">
          <span class="avatar" :class="post.tone">{{ post.initials }}</span>
          <div><strong>{{ post.author }}</strong><span>{{ post.handle }} · {{ post.role }}</span></div><time>{{ post.time }}</time>
        </div>
      </header>

      <!-- 图片与视频混排时按顺序展示；单个视频单独占满一行 -->
      <div v-if="post.media?.length" class="detail-media" :class="{ 'single-video': post.media[0].mediaType === 'VIDEO' }">
        <template v-for="item in post.media" :key="item.mediaId">
          <img v-if="item.mediaType === 'IMAGE'" :src="item.url" alt="" loading="lazy" />
          <video v-else :src="item.url" controls preload="metadata" playsinline></video>
        </template>
      </div>

      <div class="detail-body">
        <!-- 纯文本按空行分段；没有分段信息时退回整段展示 -->
        <p v-for="paragraph in post.detail?.length ? post.detail : [post.body]" :key="paragraph">{{ paragraph }}</p>
        <aside class="detail-note"><span></span><div><small>创作进展</small><strong>{{ post.note }}</strong></div></aside>
      </div>

      <footer class="detail-actions">
        <button class="like-button" type="button" :class="{ active: post.liked }" :aria-pressed="post.liked" @click="$emit('toggle-like', post)"><span class="heart-icon">{{ post.liked ? '♥' : '♡' }}</span>{{ post.likes }} 人喜欢</button>
        <button type="button" @click="$emit('go-comments', post)"><span class="comment-icon"></span>{{ responseCount(post) }} 条讨论</button>
        <button type="button" @click="$emit('share', post)"><span class="share-icon"></span>分享</button>
        <button class="save-action" type="button" :class="{ active: post.saved }" @click="$emit('toggle-save', post)"><span class="save-icon"></span>{{ post.saved ? '已收藏' : '收藏' }}</button>
      </footer>
    </article>

    <section id="post-discussion" class="detail-discussion" tabindex="-1">
      <div class="discussion-heading"><div><p class="kicker">DISCUSSION</p><h2>一起聊聊</h2></div><span>{{ responseCount(post) }} 条回应</span></div>
      <article
        v-for="comment in post.comments"
        :key="comment.id"
        class="root-comment"
        :class="{ active: replyingToId === comment.id }"
        role="button"
        tabindex="0"
        :aria-label="'回复 ' + comment.author + ' 的评论'"
        @click="$emit('start-reply', comment)"
        @keydown.enter.prevent="$emit('start-reply', comment)"
        @keydown.space.prevent="$emit('start-reply', comment)"
      >
        <span class="avatar">{{ comment.initials }}</span>
        <div class="comment-content">
          <strong>{{ comment.author }}</strong><p>{{ comment.text }}</p>
          <span v-if="replyingToId !== comment.id" class="reply-hint">点击评论回复</span>

          <Transition name="reveal">
            <form v-if="replyingToId === comment.id" class="nested-reply-form" @click.stop @submit.prevent="$emit('add-nested-reply', comment)">
              <span class="avatar tiny ink">许</span>
              <input :value="nestedReply" type="text" :placeholder="'回复 ' + comment.author + '…'" autofocus @input="$emit('update:nestedReply', $event.target.value)" />
              <button class="nested-cancel" type="button" @click="$emit('cancel-reply')">取消</button>
              <button type="submit" :disabled="!nestedReply.trim()">发送</button>
            </form>
          </Transition>

          <div v-if="comment.replies?.length" class="comment-thread" aria-label="评论回复">
            <article v-for="reply in comment.replies" :key="reply.id">
              <span class="avatar tiny ink">{{ reply.initials }}</span>
              <div><div class="nested-meta"><strong>{{ reply.author }}</strong><time>{{ reply.time }}</time></div><p>{{ reply.text }}</p></div>
            </article>
          </div>
        </div>
      </article>

      <form class="detail-reply" @submit.prevent="$emit('add-comment', post)">
        <span class="avatar ink">许</span><textarea v-model="post.reply" rows="3" placeholder="认真回应这个想法…"></textarea>
        <button type="submit" :disabled="!post.reply.trim()">发送回应</button>
      </form>
    </section>
  </main>
</template>
