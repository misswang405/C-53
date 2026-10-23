<script setup>
defineProps({
  topics: { type: Array, required: true },
  people: { type: Array, required: true },
})

defineEmits(['open-profile', 'follow'])
</script>

<template>
  <main class="discover-page">
    <div class="page-intro wide"><div><p class="kicker">EXPLORE TOGETHER</p><h1>去看看，大家最近在关心什么。</h1><p>从话题出发，找到同频的人和正在生长的想法。</p></div></div>
    <section class="topic-grid">
      <button v-for="(topic, index) in topics" :key="topic.name" class="topic-block" type="button">
        <span>0{{ index + 1 }}</span><i :style="{ background: topic.color }"></i><h2>{{ topic.name }}</h2><p>{{ topic.count }} 条讨论 · 今天有新内容</p><b>进入话题 ↗</b>
      </button>
    </section>

    <section class="member-section">
      <div class="section-title"><p class="kicker">PEOPLE TO MEET</p><h2>本周活跃创作者</h2></div>
      <div class="member-grid">
        <article
          v-for="person in people"
          :key="person.id"
          role="button"
          tabindex="0"
          :aria-label="'进入 ' + person.name + ' 的个人主页'"
          @click="$emit('open-profile', person)"
          @keydown.enter.prevent="$emit('open-profile', person)"
        >
          <span class="avatar large" :class="person.tone">{{ person.initials }}</span>
          <h3>{{ person.name }}</h3>
          <p>{{ person.handle }} · {{ person.field }}</p>
          <button type="button" :class="{ followed: person.followed }" @click.stop="$emit('follow', person)" @keydown.stop>{{ person.followed ? '已关注' : '关注' }}</button>
        </article>
      </div>
    </section>
  </main>
</template>
