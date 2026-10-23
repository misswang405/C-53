<script setup>
import { computed, onBeforeUnmount, ref } from 'vue'
import { api } from '../../services/api'
import { uploadMedia } from '../../services/upload'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  maxImages: { type: Number, default: 9 },
  maxVideos: { type: Number, default: 1 },
  disabled: { type: Boolean, default: false },
})

const emit = defineEmits(['update:modelValue', 'update:uploading', 'error'])

const input = ref(null)
const uploading = ref(false)
const progress = ref(0)
const objectUrls = []

const imageCount = computed(() => props.modelValue.filter((item) => item.mediaType === 'IMAGE').length)
const videoCount = computed(() => props.modelValue.filter((item) => item.mediaType === 'VIDEO').length)
const canAdd = computed(() => !props.disabled && !uploading.value
  && imageCount.value < props.maxImages && videoCount.value < props.maxVideos)

function pick() {
  if (canAdd.value) input.value?.click()
}

async function handleFiles(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''
  if (!files.length) return
  uploading.value = true
  emit('update:uploading', true)
  try {
    for (const file of files) {
      const isVideo = file.type.startsWith('video/')
      if (isVideo && videoCount.value >= props.maxVideos) {
        emit('error', '每条动态最多上传 1 个视频')
        continue
      }
      if (!isVideo && imageCount.value >= props.maxImages) {
        emit('error', `最多上传 ${props.maxImages} 张图片`)
        continue
      }
      if (!isVideo && !['image/jpeg', 'image/png', 'image/webp'].includes(file.type)) {
        emit('error', '仅支持 JPEG、PNG、WebP 图片')
        continue
      }
      if (isVideo && file.type !== 'video/mp4') {
        emit('error', '视频仅支持 MP4 格式')
        continue
      }
      progress.value = 0
      const uploaded = await uploadMedia(file, { onProgress: (value) => { progress.value = value } })
      const previewUrl = URL.createObjectURL(file)
      objectUrls.push(previewUrl)
      emit('update:modelValue', [...props.modelValue, { ...uploaded, previewUrl }])
    }
  } catch (error) {
    emit('error', error?.message || '上传失败，请稍后再试')
  } finally {
    uploading.value = false
    progress.value = 0
    emit('update:uploading', false)
  }
}

function remove(item) {
  const index = props.modelValue.indexOf(item)
  if (index >= 0) {
    emit('update:modelValue', props.modelValue.filter((_, position) => position !== index))
  }
  if (item.mediaId) api.deleteMedia(item.mediaId).catch(() => {})
}

onBeforeUnmount(() => {
  objectUrls.forEach((url) => URL.revokeObjectURL(url))
})
</script>

<template>
  <div class="media-uploader">
    <div class="media-grid">
      <figure v-for="item in modelValue" :key="item.mediaId" class="media-tile">
        <img v-if="item.mediaType === 'IMAGE'" :src="item.previewUrl || item.url" :alt="'已上传图片 ' + item.mediaId" />
        <video v-else :src="item.previewUrl || item.url" controls preload="metadata"></video>
        <span v-if="item.mediaType === 'VIDEO'" class="media-badge">视频</span>
        <button type="button" class="media-remove" aria-label="移除媒体" :disabled="disabled" @click="remove(item)">×</button>
      </figure>
      <button v-if="canAdd" class="media-add" type="button" :disabled="disabled" @click="pick">
        <span>＋</span><small>{{ uploading ? progress + '%' : '图片 / 视频' }}</small>
      </button>
    </div>
    <div v-if="uploading" class="media-progress" role="progressbar" :aria-valuenow="progress" aria-valuemin="0" aria-valuemax="100">
      <span :style="{ width: progress + '%' }"></span>
    </div>
    <p class="media-tip">图片最多 {{ maxImages }} 张（单张 ≤10MB）；视频 1 个（≤500MB，大于 10MB 自动直传）</p>
    <input ref="input" class="media-input" type="file" accept="image/jpeg,image/png,image/webp,video/mp4" multiple @change="handleFiles" />
  </div>
</template>
