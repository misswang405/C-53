<script setup>
// 分享弹层：展示分享目标并提供渠道选择和链接复制入口。
// 接收弹窗开关、分享目标和链接主机展示文本，不直接执行分享接口。
defineProps({
  open: { type: Boolean, default: false },
  target: { type: Object, default: null },
  host: { type: String, required: true },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
defineEmits(['close', 'share-to', 'copy-link'])
</script>

<template>
  <Transition name="share">
    <div v-if="open" class="share-backdrop" @click.self="$emit('close')" @keydown.esc="$emit('close')">
      <section class="share-panel" role="dialog" aria-modal="true" aria-labelledby="share-title">
        <header><div><p class="kicker">SHARE</p><h2 id="share-title">分享这个想法</h2></div><button type="button" aria-label="关闭分享" @click="$emit('close')">×</button></header>
        <div class="share-preview"><span class="avatar" :class="target?.tone">{{ target?.initials }}</span><div><strong>{{ target?.title }}</strong><p>{{ target?.author }} · #{{ target?.topic }}</p></div></div>
        <div class="share-options">
          <button type="button" @click="$emit('share-to', 'wechat')"><span class="share-app wechat">微</span><strong>微信</strong><small>好友 / 朋友圈</small></button>
          <button type="button" @click="$emit('share-to', 'qq')"><span class="share-app qq">Q</span><strong>QQ</strong><small>好友 / QQ 空间</small></button>
          <button type="button" @click="$emit('share-to', 'douyin')"><span class="share-app douyin">♪</span><strong>抖音</strong><small>私信好友</small></button>
        </div>
        <button class="copy-share-link" type="button" @click="$emit('copy-link')"><span>↗</span><div><strong>复制链接</strong><small>{{ host }}</small></div><b>复制</b></button>
        <p class="share-tip">将在支持的设备上打开系统分享面板</p>
      </section>
    </div>
  </Transition>
</template>
