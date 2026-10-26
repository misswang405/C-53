<script setup>
// 私信页面：展示会话列表与当前聊天内容。
// 会话、消息及输入草稿来自父组件，发送结果由父组件同步。
defineProps({
  conversations: { type: Array, required: true },
  activeConversation: { type: Number, required: true },
  currentConversation: { type: Object, default: null },
  messages: { type: Array, required: true },
  chatDraft: { type: String, default: '' },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits(['select-conversation', 'update:chatDraft', 'send'])
</script>

<template>
  <main class="messages-page">
    <section class="conversation-list">
      <div class="message-heading"><div><p class="kicker">MESSAGES</p><h1>私信</h1></div><button type="button">＋</button></div>
      <button
        v-for="conversation in conversations"
        :key="conversation.id"
        type="button"
        :class="{ active: activeConversation === conversation.id }"
        @click="$emit('select-conversation', conversation)"
      >
        <span class="avatar" :class="conversation.tone">{{ conversation.initials }}</span>
        <div><strong>{{ conversation.name }}</strong><p>{{ conversation.preview }}</p></div>
        <time>{{ conversation.time }}</time><i v-if="conversation.unread">{{ conversation.unread }}</i>
      </button>
    </section>

    <section v-if="currentConversation" class="chat-panel">
      <header><span class="avatar coral">{{ currentConversation.initials }}</span><div><strong>{{ currentConversation.name }}</strong><span>在线</span></div><button type="button">•••</button></header>
      <div class="chat-history"><div class="chat-date">今天</div><p v-for="message in messages" :key="message.id" :class="{ mine: message.mine }">{{ message.text }}</p></div>
      <form class="chat-composer" @submit.prevent="$emit('send')">
        <button type="button">＋</button>
        <input :value="chatDraft" type="text" placeholder="输入消息…" @input="$emit('update:chatDraft', $event.target.value)" />
        <button type="submit" :disabled="!chatDraft.trim()">发送</button>
      </form>
    </section>
    <section v-else class="chat-panel message-empty"><p>暂无私信会话</p></section>
  </main>
</template>
