// 媒体上传协调：根据服务端配置选择代理上传或对象存储直传，并完成上传确认。
import { api } from './api'

// 上传限制基本不变，缓存一份避免每次上传都多一次请求
let configCache = null

export async function getMediaConfig() {
  if (!configCache) configCache = await api.getMediaConfig()
  return configCache
}

export function clearMediaConfig() {
  configCache = null
}

/**
 * 按文件大小选择上传通道：
 * - 图片始终走服务端，由后端重编码剥离 EXIF
 * - 视频 ≤10MB 走服务端，超过则申请 STS 凭证后前端直传
 */
export async function uploadMedia(file, { onProgress } = {}) {
  const config = await getMediaConfig()
  const isVideo = file.type === 'video/mp4' || file.type.startsWith('video/')
  if (isVideo && file.size > config.serverUploadMaxBytes) {
    return uploadDirect(file, onProgress)
  }
  const mediaType = isVideo ? 'VIDEO' : 'IMAGE'
  onProgress?.(10)
  const result = await api.uploadMedia(file, mediaType)
  onProgress?.(100)
  return result
}

/**
 * 直传流程：申请票据 → 用临时凭证分片上传到临时目录 → 通知后端确认转正。
 */
async function uploadDirect(file, onProgress) {
  const ticket = await api.requestUploadTicket({
    fileName: file.name,
    contentType: file.type || 'video/mp4',
    sizeBytes: file.size,
    mediaType: 'VIDEO',
  })
  // 动态引入：只有走直传时才加载 690KB 的 OSS SDK，不拖慢首屏
  const { default: OSS } = await import('ali-oss')
  const client = new OSS({
    region: ticket.region,
    endpoint: ticket.endpoint,
    bucket: ticket.bucket,
    accessKeyId: ticket.accessKeyId,
    accessKeySecret: ticket.accessKeySecret,
    stsToken: ticket.securityToken,
    secure: true,
  })
  try {
    const result = await client.multipartUpload(ticket.objectKey, file, {
      partSize: ticket.partSizeBytes,
      progress: (value) => onProgress?.(Math.round(value * 100)),
    })
    // 上传完只是写入临时目录，必须确认后端校验通过后才生成可用的媒体记录
    return await api.confirmMedia(ticket.mediaId, { etag: result?.etag, sizeBytes: file.size })
  } catch (error) {
    // 直传失败时释放服务端占位记录，避免并发名额被长期占用
    api.deleteMedia(ticket.mediaId).catch(() => {})
    throw error
  }
}
