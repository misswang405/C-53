<script setup>
defineProps({
  views: { type: Array, required: true },
  topics: { type: Array, required: true },
  activeView: { type: String, required: true },
  open: { type: Boolean, default: false },
  onlineCount: { type: Number, default: 0 },
})

defineEmits(['navigate', 'close'])
</script>

<template>
  <aside class="side-rail" :class="{ open }">
    <div class="brand-row">
      <button class="brand" type="button" aria-label="回到共同动态" @click="$emit('navigate', 'feed')">
        <span class="brand-orbit"><i></i></span><span>C—53</span>
      </button>
      <button class="mobile-close icon-button" type="button" aria-label="关闭导航" @click="$emit('close')">×</button>
    </div>

    <div class="space-label">
      <span class="space-avatar">共</span>
      <div><strong>共创星球</strong><small>开放社区</small></div>
      <span class="chevron">⌄</span>
    </div>

    <nav class="main-nav" aria-label="主要导航">
      <button
        v-for="item in views"
        :key="item.id"
        type="button"
        :class="{ active: activeView === item.id }"
        @click="$emit('navigate', item.id)"
      >
        <span class="nav-icon" :class="item.icon" aria-hidden="true"></span>
        <span>{{ item.label }}</span>
        <b v-if="item.badge">{{ item.badge }}</b>
      </button>
    </nav>

    <div class="side-section">
      <p>正在发生</p>
      <button
        v-for="topic in topics.slice(0, 3)"
        :key="topic.name"
        class="topic-link"
        type="button"
        @click="$emit('navigate', 'discover')"
      >
        <i :style="{ background: topic.color }"></i>
        <span>{{ topic.name }}</span>
        <small>{{ topic.count }}</small>
      </button>
    </div>

    <div v-if="onlineCount > 0" class="side-note">
      <span class="live-dot"></span>
      <p><strong>{{ onlineCount }} 人在线</strong><br />这里正有新的讨论</p>
    </div>
  </aside>
</template>
