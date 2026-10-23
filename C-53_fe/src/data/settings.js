export const settingsNav = [
  { id: 'account', label: '账户与登录', icon: '人' },
  { id: 'permissions', label: '系统权限', icon: '⌁' },
  { id: 'visibility', label: '内容可见范围', icon: '◉' },
  { id: 'social', label: '社交权限', icon: '◎' },
  { id: 'recommendation', label: '个性化推荐', icon: '◇' },
  { id: 'data', label: '数据管理', icon: '↧' },
  { id: 'modes', label: '特殊模式', icon: '◐' },
]

export const permissionItems = [
  { id: 'camera', name: '相机', description: '用于拍摄头像和发布照片', icon: '◉', windows: 'ms-settings:privacy-webcam', mac: 'x-apple.systempreferences:com.apple.preference.security?Privacy_Camera' },
  { id: 'photos', name: '相册', description: '用于选择头像和内容图片', icon: '▧', windows: 'ms-settings:privacy-pictures', mac: 'x-apple.systempreferences:com.apple.preference.security?Privacy_Photos' },
  { id: 'microphone', name: '麦克风', description: '用于语音消息和音频内容', icon: '♩', windows: 'ms-settings:privacy-microphone', mac: 'x-apple.systempreferences:com.apple.preference.security?Privacy_Microphone' },
  { id: 'location', name: '位置', description: '用于展示附近活动与城市信息', icon: '⌖', windows: 'ms-settings:privacy-location', mac: 'x-apple.systempreferences:com.apple.preference.security?Privacy_LocationServices' },
  { id: 'contacts', name: '通讯录', description: '用于发现你可能认识的人', icon: '♙', windows: 'ms-settings:privacy-contacts', mac: 'x-apple.systempreferences:com.apple.preference.security?Privacy_Contacts' },
]

export const visibilityOptions = ['公开', '好友可见', '仅自己可见']
export const socialOptions = ['所有人', '我关注的人', '互相关注的人', '不允许']
