<script setup>
// 定时发布弹窗：收集本地日期时间并提交计划发布时间。
import { computed, ref, watch } from 'vue'

// 通过属性接收弹窗开关和提交状态，计划时间由组件内部编辑。
const props = defineProps({
  open: { type: Boolean, default: false },
  initial: { type: String, default: '' },
  minLeadMinutes: { type: Number, default: 5 },
  busy: { type: Boolean, default: false },
})

// 将用户操作与双向绑定变更通知父组件，由父组件统一处理业务状态。
const emit = defineEmits(['close', 'confirm'])

const value = ref('')
const error = ref('')

// datetime-local 只认本地时间字符串，不能直接塞 ISO（UTC）字符串，否则会偏移时区
function toLocalInput(date) {
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

// 每次打开都重置为已有时间或“一小时后”，避免沿用上次的残留值
watch(() => props.open, (open) => {
  if (!open) return
  const base = props.initial ? new Date(props.initial) : new Date(Date.now() + 60 * 60 * 1000)
  value.value = toLocalInput(Number.isNaN(base.getTime()) ? new Date(Date.now() + 3600000) : base)
  error.value = ''
}, { immediate: true })

const minValue = computed(() => toLocalInput(new Date(Date.now() + props.minLeadMinutes * 60000)))

function confirm() {
  if (!value.value) {
    error.value = '请选择发布时间'
    return
  }
  const selected = new Date(value.value)
  if (Number.isNaN(selected.getTime())) {
    error.value = '时间格式不正确'
    return
  }
  // 与后端 shortest lead 保持一致，前端先拦截明显无效的时间
  if (selected.getTime() < Date.now() + props.minLeadMinutes * 60000) {
    error.value = `发布时间至少要在 ${props.minLeadMinutes} 分钟之后`
    return
  }
  error.value = ''
  // 统一按 UTC 提交，展示时再换回本地时区
  emit('confirm', selected.toISOString())
}
</script>

<template>
  <Transition name="share">
    <div v-if="open" class="share-backdrop" @click.self="emit('close')" @keydown.esc="emit('close')">
      <section class="schedule-dialog" role="dialog" aria-modal="true" aria-labelledby="schedule-title">
        <header>
          <div><p class="kicker">SCHEDULE</p><h2 id="schedule-title">定时发布</h2></div>
          <button type="button" aria-label="关闭" @click="emit('close')">×</button>
        </header>
        <label class="schedule-field">
          <span>发布时间</span>
          <input v-model="value" type="datetime-local" :min="minValue" />
        </label>
        <p v-if="error" class="schedule-error">{{ error }}</p>
        <p class="schedule-hint">到点后由服务端自动发布，时区按本地时间换算为 UTC 存储。</p>
        <footer>
          <button class="text-button" type="button" @click="emit('close')">取消</button>
          <button class="primary-button" type="button" :disabled="busy" @click="confirm">{{ busy ? '设置中…' : '确定定时' }}</button>
        </footer>
      </section>
    </div>
  </Transition>
</template>
