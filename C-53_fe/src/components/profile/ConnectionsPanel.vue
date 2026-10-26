<script setup>
// 关注关系弹层：展示并搜索关注者或正在关注的成员。
// 关系类型、筛选后的成员及关系文案函数由父组件提供。
defineProps({
  open: { type: Boolean, default: false },
  type: { type: String, required: true },
  search: { type: String, default: '' },
  people: { type: Array, required: true },
  connectionLabel: { type: Function, required: true },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits(['close', 'update:search', 'open-profile', 'toggle-connection'])
</script>

<template>
  <Transition name="connections">
    <div v-if="open" class="connections-backdrop" @click.self="$emit('close')">
      <section class="connections-panel" role="dialog" aria-modal="true" :aria-label="type === 'followers' ? '关注者列表' : '正在关注列表'">
        <header>
          <div><p class="kicker">CONNECTIONS</p><h2>{{ type === 'followers' ? '关注者' : '正在关注' }}</h2><span>{{ type === 'followers' ? '326 人关注了你' : '你正在关注 184 人' }}</span></div>
          <button type="button" aria-label="关闭列表" @click="$emit('close')">×</button>
        </header>
        <label class="connections-search"><span class="search-icon" aria-hidden="true"></span><input :value="search" type="search" placeholder="搜索姓名、账号或领域" autofocus @input="$emit('update:search', $event.target.value)" /></label>
        <div class="connections-list">
          <article v-for="person in people" :key="person.id" role="button" tabindex="0" :aria-label="'查看 ' + person.name + ' 的个人主页'" @click="$emit('open-profile', person)" @keydown.enter.prevent="$emit('open-profile', person)">
            <span class="avatar" :class="person.tone">{{ person.initials }}</span>
            <div><strong>{{ person.name }}</strong><span>{{ person.handle }} · {{ person.field }}</span><small>{{ person.note }}</small></div>
            <button type="button" :class="{ mutual: person.followed && person.followsYou, followback: !person.followed && person.followsYou, followed: person.followed && !person.followsYou }" @click.stop="$emit('toggle-connection', person)" @keydown.stop>{{ connectionLabel(person) }}</button>
          </article>
          <div v-if="!people.length" class="connections-empty"><span>◇</span><p>没有找到匹配的人</p></div>
        </div>
      </section>
    </div>
  </Transition>
</template>
