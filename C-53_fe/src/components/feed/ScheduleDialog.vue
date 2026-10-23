<script setup>
import { computed, ref, watch } from 'vue'

const props = defineProps({
  open: { type: Boolean, default: false },
  initial: { type: String, default: '' },
  minLeadMinutes: { type: Number, default: 5 },
  busy: { type: Boolean, default: false },
})

const emit = defineEmits(['close', 'confirm'])

const value = ref('')
const error = ref('')

function toLocalInput(date) {
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`
}

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
  if (selected.getTime() < Date.now() + props.minLeadMinutes * 60000) {
    error.value = `发布时间至少要在 ${props.minLeadMinutes} 分钟之后`
    return
  }
  error.value = ''
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
