# C-53 发布功能需求文档（PRD）

> 状态：初稿，待评审  
> 版本：1.1  
> 日期：2026-10-23  
> 输入：`C-53_fe/src/services/api.js`、`FeedView.vue`、`App.vue` 发布动作、`docs/后端工程目录规范.md`、`docs/开发文档/登录模块开发方案.md`  
> 变更记录：v1.0 初版（短动态、草稿、图片、定时）；v1.1 引入阿里云 OSS、文字/图片/视频分类存储、小文件后端上传与大文件前端直传双通道、STS 临时凭证与上传限流，视频本期仅存原片。  
> 已确认决策：正文存 MySQL；大小文件分流阈值 10MB；视频仅存原片不转码；前端直传采用 STS 临时凭证。

## 1. 分析维度总览

发布功能是一条从编辑、暂存、媒体上传、校验、入库到分发的完整链路。本期因引入 OSS 和双通道上传，分析维度扩展为 13 项。

| # | 分析维度 | 需要回答的关键问题 | 对应章节 |
|---|---|---|---|
| 1 | 产品定位与目标 | 发布功能解决什么问题，成功如何衡量 | 第 2 章 |
| 2 | 用户角色与场景 | 谁能发布、谁能看、谁只能读 | 第 3 章 |
| 3 | 内容形态与编辑体验 | 发什么、写多长、怎么分段、怎么选话题 | 第 4、6 章 |
| 4 | 发布流程与状态机 | 草稿、定时、已发布、删除如何流转 | 第 7 章 |
| 5 | 草稿与自动保存 | 何时保存、失败怎么办、多端冲突怎么办 | 第 6.3 节 |
| 6 | 媒体分类与存储路径 | 文字/图片/视频如何区分、对象如何命名 | 第 6.5 节 |
| 7 | 大文件直传 | STS 如何签发、路径如何限制、如何确认 | 第 6.7 节 |
| 8 | 小文件后端上传 | 校验、重编码、事务与补偿 | 第 6.6 节 |
| 9 | 上传限流与成本保护 | 如何防刷、配额、临时对象清理 | 第 6.10、10.4 节 |
| 10 | 定时发布 | 时间精度、时区、取消、多实例重复执行 | 第 6.12 节 |
| 11 | 编辑与删除 | 谁能改、改完如何展示、删除是否可恢复 | 第 6.13 节 |
| 12 | 数据模型与接口 | 表、字段、索引、路径、状态码 | 第 8、9 章 |
| 13 | 权限、安全、非功能 | 认证、越权、OSS 安全、性能、验收 | 第 10～15 章 |

## 2. 背景与目标

### 2.1 现状

- 后端 `C-53_be` 目前只实现认证模块 `com.c53.modules.auth`，没有内容、话题、媒体相关表与接口。
- 前端 `C-53_fe` 已有发布框、顶栏「＋ 发布」入口和 `POST /api/v1/posts` 调用，但后端未实现，Feed 仍使用 `data/community.js` 假数据。
- 项目未接入对象存储、未启用定时任务、无分页插件；`SecurityConfig` 当前 `anyRequest().permitAll()`。
- 现有上传能力仅有前端未实现的图片按钮，后端没有 `MultipartFile` 相关配置。

### 2.2 问题

1. 登录用户无法真正发布内容，发布动作只存在前端内存中。
2. 图片、视频没有统一存储与访问方案，直接放服务器磁盘会带来容量、带宽和迁移问题。
3. 大文件如果全部经后端中转，会占用应用线程、内存和出口带宽，易被刷。
4. 没有草稿、定时、编辑、删除，内容生命周期不完整。
5. 前端已按契约发起请求，后端缺失会导致 404，无法联调。

### 2.3 目标

| 类型 | 目标 |
|---|---|
| 业务目标 | 打通「编辑 → 草稿 → 媒体上传 → 发布 → Feed 展示」闭环，支持文字、图片、视频短动态 |
| 存储目标 | 所有图片、视频统一存入阿里云 OSS，按类型与日期分类，正文仍存 MySQL |
| 性能目标 | 大文件不经过应用服务器；小文件由后端完成安全校验与重编码 |
| 安全目标 | 前端直传使用短期 STS 凭证，路径由服务端生成，权限最小化，不暴露长期 AK |
| 可衡量指标 | 发布成功率 ≥ 99.5%；直传成功率 ≥ 99%；自动保存失败率 < 1%；发布 P95 < 300ms（不含上传）；定时发布延迟 ≤ 60 秒 |

### 2.4 成功标准

- 登录用户可在 30 秒内发布一条带话题、可选图片或视频的动态。
- 10MB 以内的文件走后端上传，超过 10MB 的视频走前端直传，应用服务器不承载大文件流量。
- 直传凭证过期、路径越权、类型伪造、超大文件均被拒绝。
- 同一用户双击发布或断网重试，只产生一条动态。

## 3. 用户角色与场景

### 3.1 角色

| 角色 | 定义 | 权限 |
|---|---|---|
| 游客 | 未登录访问者 | 浏览已发布内容；不可发布、上传、编辑、删除 |
| 登录用户 | 已通过认证的 `ACTIVE` 用户 | 发布、存草稿、上传媒体、定时发布、编辑/删除自己的内容 |
| 作者 | 某条动态的创建者 | 对本人内容及媒体拥有编辑、改期、删除权限 |
| 管理员 | 本期不存在 | 无后台、无审核、无强制下线能力（范围外） |

> 权限判断以 `author_id / owner_id = 当前登录用户 ID` 为准；不引入角色表，也不在 JWT 中增加角色字段。

### 3.2 核心场景

| 场景 | 前置 | 主流程 | 结果 |
|---|---|---|---|
| S1 纯文字发布 | 已登录 | 输入正文 → 选话题 → 发布 | 立即出现在 Feed 顶部与个人主页 |
| S2 图文发布 | 已登录 | 输入正文 → 选择 ≤10MB 图片 → 后端上传 → 发布 | 正文下方按顺序展示图片 |
| S3 视频发布 | 已登录 | 输入正文 → 选择视频 → 申请 STS → 前端直传 OSS → 确认 → 发布 | 视频在详情页播放 |
| S4 草稿续写 | 已登录 | 输入部分内容后离开 → 再次打开 | 恢复最近草稿，可继续编辑 |
| S5 定时发布 | 已登录 | 完成内容 → 选择未来时间 → 确认 | 到点自动公开，期间可取消或改期 |
| S6 修改已发布内容 | 作者 | 编辑正文/话题/媒体 → 保存 | 内容更新，显示「已编辑」 |
| S7 删除 | 作者 | 删除 → 二次确认 | 内容与媒体对外不可见 |

### 3.3 用户故事

- 作为登录用户，我希望上传视频时浏览器直接把文件传到 OSS，不占用业务服务器带宽。
- 作为登录用户，我希望上传中断后能继续，不必从头再传。
- 作为登录用户，我希望草稿自动保存，避免误关页面后重写。
- 作为作者，我希望只有我能编辑、删除自己的内容和使用自己的媒体。
- 作为读者，我希望草稿、定时内容和未确认的媒体不可见。

## 4. 名词与内容模型

| 名词 | 定义 |
|---|---|
| 动态（Post） | 本期内容形态：1～600 字纯文本，可带 1 个话题，以及 0～9 张图片或 0～1 个视频（图片与视频互斥） |
| 文字（TEXT） | 动态正文，存 MySQL `posts.content`，不上传 OSS；`TEXT` 仅作为内容分类标识保留 |
| 图片（IMAGE） | JPEG/PNG/WebP，≤10MB，统一走后端上传并重编码 |
| 视频（VIDEO） | MP4（H.264/AAC），≤500MB、≤5 分钟，≤10MB 走后端上传，>10MB 走前端直传 |
| 媒体（Media） | 图片或视频的元数据与 OSS 对象，先进入临时态，发布时绑定 |
| 草稿（Draft） | 尚未公开的动态，仅作者可见 |
| 定时动态（Scheduled） | 已设置未来发布时间的动态，公开前仅作者可见 |
| 话题（Topic） | 内容归属主题，来自全局话题列表 |
| 直传（Direct Upload） | 客户端持 STS 临时凭证直接 PUT 到 OSS，不经过业务服务器 |
| 服务端上传（Server Upload） | 客户端把文件传给后端，由后端校验、重编码后写入 OSS |
| 转正（Confirm） | 直传完成后，后端校验临时对象并复制到正式分类路径 |

## 5. 范围

### 5.1 本期范围

| 编号 | 能力 | 说明 |
|---|---|---|
| FR-01 | 发布入口 | 顶栏「＋ 发布」与 Feed 发布框均可进入编辑态 |
| FR-02 | 文字内容 | 1～600 字纯文本，支持换行与空行分段，正文存 MySQL |
| FR-03 | 话题选择 | 单选，发布时校验话题存在且启用 |
| FR-04 | 草稿 | 创建、自动保存、草稿列表、继续编辑 |
| FR-05 | OSS 接入 | 图片、视频统一存阿里云 OSS，按 `类型/年/月/日/用户` 分类 |
| FR-06 | 小文件后端上传 | ≤10MB 文件经后端校验、图片重编码后写入 OSS |
| FR-07 | 大文件前端直传 | >10MB 视频由后端签发 STS 临时凭证，客户端直传 OSS，上传后确认转正 |
| FR-08 | 上传限流与配额 | 凭证申请、上传次数、并发数、每日配额、临时对象清理 |
| FR-09 | 立即发布 | 校验通过后转为已发布，进入 Feed 与个人主页 |
| FR-10 | 定时发布 | 指定未来时间自动发布，支持取消与改期 |
| FR-11 | 编辑与删除 | 作者可修改正文、话题、媒体；删除为软删 |
| FR-12 | 我的发布列表 | 按草稿、定时、已发布筛选 |
| FR-13 | 幂等与防重复 | 重复提交、双击、断网重试不产生重复动态或重复媒体 |

### 5.2 不在本期范围

- 长文/文章、Markdown、富文本编辑器、封面图。
- 视频转码、压缩、截帧、封面生成、HLS 切片（本期仅存原片）。
- 评论、点赞、收藏、转发。
- 内容审核流、举报、管理员后台、强制下线。
- @ 提及、链接预览、音频附件。
- 内容版本历史、多人协作、多端草稿实时同步。
- 回收站恢复、定时任务可视化运维后台。
- 公开读 CDN 域名与防盗链的完整运营配置（本期先用私有读 + 签名 URL，见 D3）。

### 5.3 依赖

| 依赖 | 状态 | 影响 |
|---|---|---|
| 认证模块（JWT、会话校验） | 已实现 | 发布与上传接口复用 `AuthPrincipal` |
| 阿里云 OSS Bucket、RAM Role、STS | 待开通/配置 | 直传和后端上传的前置条件 |
| 话题列表接口 `GET /api/v1/topics/trending` | 未实现 | 发布选择话题依赖，需与话题模块同步交付 |
| 定时任务 | 未实现 | 需启用 `@EnableScheduling` 与 Redis 锁 |
| Feed/详情接口 | 未实现 | 发布后展示依赖 Feed 模块；本期至少保证 `GET /posts/{id}` 可读 |
| 前端 OSS SDK | 未引入 | 直传需要 `ali-oss` 浏览器 SDK 或等价分片上传实现 |

## 6. 功能需求

### 6.1 发布入口

- 顶栏「＋ 发布」：已登录时打开发布编辑器；未登录时跳转登录，登录成功后回到发布态。
- Feed 发布框：聚焦后展开，包含正文输入、话题选择、媒体按钮、字数统计、取消/发布按钮。
- 同一时间只保留一个编辑会话，避免多个弹层同时写草稿。

### 6.2 文字编辑规则

| 规则 | 要求 |
|---|---|
| 存储位置 | 正文存 MySQL `posts.content`，不写入 OSS |
| 内容长度 | 去除首尾空白后 1～600 个 Unicode 码点；前后端计数口径一致（emoji 按 1 个字符计） |
| 换行 | 保留用户换行；连续 3 个及以上空行折叠为 1 个空行 |
| 分段展示 | 按空行切分为段落，详情页逐段渲染；段内换行保留 |
| 空白内容 | 仅空白字符视为空，禁用发布 |
| 内容类型 | 纯文本，不接受 HTML 标签作为富文本解释；前端插值渲染，天然转义 |
| 话题 | 单选；发布时话题必须存在且启用；草稿允许暂不选 |
| 媒体组合 | 0～9 张图片或 0～1 个视频；图片与视频不可同时存在（D4） |

### 6.3 草稿与自动保存

**草稿创建**

- 用户首次输入内容后，前端本地暂存，并在满足触发条件时创建服务端草稿。
- 草稿与作者绑定，同一作者可有多篇草稿；每篇独立编辑。
- 草稿不出现在 Feed、他人主页和公开搜索中。

**自动保存触发**

| 条件 | 行为 |
|---|---|
| 停止输入 3 秒 | 触发一次自动保存 |
| 持续输入 | 每 15 秒最多保存一次 |
| 媒体确认完成、话题变更、手动保存 | 立即触发保存 |
| 页面隐藏/关闭前 | 使用本地缓存兜底，不保证网络请求一定发出 |

**自动保存状态**

- 编辑器显示「保存中 / 已保存 HH:mm / 未保存」三态。
- 保存失败时保留本地内容，按 2s、5s、15s 退避重试 3 次；仍失败提示「草稿未保存，请检查网络」并提供手动重试。
- 自动保存失败不得清空编辑器内容。

**并发与冲突**

- 草稿使用 `version` 乐观锁；保存请求携带当前版本。
- 版本不一致返回 `409 VERSION_CONFLICT`，前端提示「内容已在其他窗口修改」，提供「载入最新」或「覆盖保存」。
- 覆盖保存以最新版本号为基准重新提交，并记录覆盖事件。
- 本期不做多端实时合并。

**草稿上限与保留**

- 建议每用户最多 50 篇草稿、保留 90 天（D6）。
- 草稿中已绑定媒体保持 `BOUND`；草稿删除后媒体转为待清理。

### 6.4 媒体上传总览

**分流规则**

| 媒体 | 大小 | 上传通道 | 说明 |
|---|---|---|---|
| 图片 JPEG/PNG/WebP | ≤10MB | 服务端上传 | 必须重编码、剥离 EXIF；>10MB 直接拒绝，要求压缩 |
| 视频 MP4 | ≤10MB | 服务端上传 | 仅校验与转存，不重编码 |
| 视频 MP4 | 10MB～500MB | 前端直传 | STS 临时凭证 + 分片上传 + 确认转正 |
| 视频 | >500MB | 拒绝 | 返回 `MEDIA_TOO_LARGE` |

> 阈值 `10MB` 由后端配置（`media.server-upload-max-size`），前端通过 `GET /media/config` 获取，避免两端写死。图片不接受直传，保证服务端始终有机会重编码与清理元数据。

**统一媒体状态**

`INIT` → `UPLOADING` → `READY` → `BOUND`；失败为 `FAILED`，删除为 `DELETED`。

- 只有 `READY` 的媒体可以被发布绑定；`BOUND` 表示已被动态引用。
- 直传在 `confirm` 成功前保持 `UPLOADING`，不可绑定、不可对外访问。

### 6.5 分类存储与对象路径

**分类标识**

| 分类 | `media_type` | 是否落 OSS | 存储位置 |
|---|---|---|---|
| 文字 | `TEXT` | 否 | MySQL `posts.content` |
| 图片 | `IMAGE` | 是 | `image/` 前缀 |
| 视频 | `VIDEO` | 是 | `video/` 前缀 |

文字虽然不落 OSS，但媒体表保留 `TEXT` 枚举，便于后续扩展文字归档或统一内容视图；接口与数据模型中的内容类型始终显式返回。

**对象 Key 方案**

采用「环境 + 类型 + 日期分层 + 用户 + ULID」而非纯时间戳文件名：

```text
正式对象：
{env}/{mediaType}/{yyyy}/{MM}/{dd}/{ownerId}/{mediaId}.{ext}

示例：
prod/image/2026/10/23/usr_01K5ABC.../med_01K6DEF....jpg
prod/video/2026/10/23/usr_01K5ABC.../med_01K6GHI....mp4

临时对象（直传）：
{env}/temp/{ownerId}/{uploadId}/source.{ext}
```

选择理由：

- **类型前缀**让图片、视频的权限、生命周期、CDN 规则、统计和成本核算相互隔离。
- **年/月/日分层**避免单目录对象过多，便于按日期做生命周期、归档和批量清理；比把时间戳塞进文件名更直观，也便于运营排查。
- **用户目录**让 STS 策略可以精确限定到 `temp/{ownerId}/`，从权限层面阻止跨用户写入。
- **ULID** 保证唯一、可排序，且不可被枚举；不使用原始文件名，避免中文、空格、路径穿越和隐私泄露。
- **环境前缀**隔离 dev/test/prod，避免误删与混用。

**命名约束**

- `mediaId` 由服务端生成（`med_` + ULID），客户端不得指定对象路径。
- 扩展名由服务端根据校验后的 MIME 决定，不采用用户上传的原始扩展名。
- Bucket 私有，不对外暴露 `bucket` 与 `objectKey`；接口只返回媒体 ID 与短期签名 URL。

### 6.6 小文件后端上传

适用于所有图片和 ≤10MB 的视频。

1. 客户端以 `multipart/form-data` 调用 `POST /api/v1/media/upload`，字段 `file`、`mediaType`。
2. 服务端校验：登录状态、限流、Content-Length、文件魔数、MIME 白名单、图片尺寸/视频时长。
3. 图片重新编码（统一转 JPEG/WebP，剥离 EXIF/GPS）；视频不重编码，仅校验容器与编码。
4. 生成 `mediaId` 与正式对象路径，上传 OSS，成功后写入 `media_assets`，状态 `READY`。
5. OSS 写入失败返回 `OSS_UNAVAILABLE`，不保留半成品记录；数据库写入失败时补偿删除 OSS 对象。
6. 响应返回 `mediaId、mediaType、url（短期签名）、width/height、durationMs、sizeBytes`。

**校验要求**

- 图片：魔数判定 JPEG/PNG/WebP；单边 ≤8000px；重编码后长边 ≤2048px、质量 85（D9）。
- 视频：仅 MP4 容器，H.264 + AAC；≤10MB；时长 ≤5 分钟；分辨率 ≤1920×1080。
- 拒绝 SVG、GIF、HEIC、可执行文件和伪造扩展名。
- Spring 配置 `max-file-size=10MB`、`max-request-size=12MB`，超限统一返回 413。

### 6.7 大文件前端直传（STS）

#### 6.7.1 流程

1. 客户端选择视频，前端本地校验格式、大小、时长；超过 500MB 或 5 分钟直接拒绝。
2. 调用 `POST /api/v1/media/upload-ticket`，提交 `{fileName, contentType, sizeBytes, mediaType}`。
3. 后端校验登录、限流、类型、大小，生成 `uploadId` 与临时对象路径，调用阿里云 STS `AssumeRole` 获取临时凭证。
4. 后端返回 `{uploadId, mediaId, accessKeyId, accessKeySecret, securityToken, expiration, region, bucket, endpoint, objectKey, partSize}`。
5. 客户端使用 `ali-oss` SDK 以 STS 凭证分片直传；超过 `partSize` 自动分片，支持暂停/继续/取消和进度展示。
6. 上传完成后调用 `POST /api/v1/media/{mediaId}/confirm`。
7. 后端 `HeadObject` 校验对象大小、MIME、ETag，必要时读取视频头部校验时长与编码；校验通过后把临时对象复制到正式分类路径，删除临时对象，状态置 `READY`。
8. 客户端拿到 `READY` 后即可绑定发布。

#### 6.7.2 STS 策略最小化

后端签发 STS 时使用专用 RAM Role，并通过会话策略把权限限制到单个上传目录：

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "oss:PutObject",
        "oss:InitiateMultipartUpload",
        "oss:UploadPart",
        "oss:CompleteMultipartUpload",
        "oss:AbortMultipartUpload"
      ],
      "Resource": [
        "acs:oss:*:*:{bucket}/{env}/temp/{ownerId}/{uploadId}/*"
      ]
    }
  ]
}
```

安全要求：

- 不授予 `ListObjects`、`GetObject`、`DeleteObject`、`PutBucket*` 等权限；正式对象的复制与删除由后端角色完成。
- `uploadId`、`ownerId`、`objectKey` 全部由服务端生成，客户端只读使用，不能拼路径。
- 凭证有效期 15 分钟（可配置）；过期后前端重新申请，不复用旧凭证。
- 同一用户的有效凭证数量受限，防止批量签发。
- STS 会话策略无法表达 `content-length-range`，因此采用「声明大小 + 上传后 HeadObject 校验 + 超限删除并记风控」补偿；Bucket 开启访问日志与异常告警。
- Bucket 为私有读；CORS 仅允许站点域名，方法限定 `PUT/HEAD/POST`，暴露必要响应头。

#### 6.7.3 分片与断点续传

- 默认分片 5MB，可配置；分片数上限由 OSS 规则约束。
- 前端记录 `uploadId`、已上传分片 ETag 到本地存储，页面刷新后可续传。
- 用户取消或超过 24 小时未完成，服务端/清理任务调用 `AbortMultipartUpload` 并删除临时对象。
- 直传失败不产生 `READY` 媒体；发布接口拒绝 `UPLOADING` 状态的媒体。

#### 6.7.4 确认与转正

- `confirm` 必须校验媒体归属、状态为 `UPLOADING`、临时对象存在。
- 校验内容：对象大小 ≤ 声明大小且 ≤500MB、MIME 为 `video/mp4`、视频时长 ≤5 分钟、ETag 与客户端上报一致（可选）。
- 校验通过后执行 OSS `CopyObject` 到正式路径，删除临时对象，更新 `object_key`、`size_bytes`、`duration_ms`、`status=READY`。
- `confirm` 幂等：重复调用返回同一媒体信息，不重复复制。
- 校验失败：删除临时对象，状态 `FAILED`，返回 `MEDIA_CONFIRM_FAILED`，客户端可重新上传。

### 6.8 图片规则

| 项 | 限制 |
|---|---|
| 格式 | JPEG、PNG、WebP；按魔数判断 |
| 大小 | ≤10MB；超过直接拒绝并提示压缩 |
| 数量 | 每条动态 ≤9 张 |
| 尺寸 | 单边 ≤8000px；重编码后长边 ≤2048px |
| 处理 | 统一重编码，剥离 EXIF/GPS；生成稳定 MIME 与扩展名 |
| 预览 | 使用短期签名 URL；上传完成后展示缩略图 |
| 排序 | 按用户拖动顺序写入 `sort_order` |
| 删除 | 未绑定可直接删除；已绑定需先解绑或随动态删除 |

### 6.9 视频规则

| 项 | 限制 |
|---|---|
| 格式 | MP4 容器，H.264 视频 + AAC 音频 |
| 大小 | ≤500MB；≤10MB 走服务端上传，>10MB 走直传 |
| 时长 | ≤5 分钟 |
| 分辨率 | ≤1920×1080；帧率 ≤60fps |
| 数量 | 每条动态 ≤1 个视频 |
| 处理 | 本期仅存原片，不转码、不压缩、不截帧 |
| 播放 | 详情页 `<video controls preload="metadata">`，使用短期签名 URL，支持 Range |
| 封面 | 本期不生成；由浏览器展示首帧（D5） |
| 兼容提示 | 上传前提示用户使用 MP4/H.264，避免浏览器无法播放 |

### 6.10 限流、配额与成本保护

| 操作 | 限制（建议值） | Redis Key |
|---|---|---|
| 申请直传凭证 | 5 次/分钟/用户，20 次/小时/用户 | `post:ticket:{userId}` |
| 服务端上传 | 30 次/小时/用户，并发 ≤3 | `post:upload:{userId}` |
| 直传并发 | 同一用户同时 ≤3 个进行中上传 | `post:uploading:{userId}` |
| 图片每日配额 | 200 张/天/用户 | `post:quota:image:{userId}:{yyyyMMdd}` |
| 视频每日配额 | 10 个/天/用户 | `post:quota:video:{userId}:{yyyyMMdd}` |
| 直传流量配额 | 2GB/天/用户（可按运营调整） | `post:quota:traffic:{userId}:{yyyyMMdd}` |
| 发布 | 10 次/小时/用户 | `post:publish:{userId}` |
| 自动保存 | 60 次/分钟/用户 | `post:autosave:{userId}` |
| 详情读取 | 120 次/分钟/IP | `post:read:{ip}` |

- 超限返回 429 `RATE_LIMITED`，`details` 携带 `retryAfter`。
- Redis 不可用时：限流与配额 fail-open 并告警；发布幂等由数据库唯一键兜底，上传仍受 OSS 侧策略和确认校验保护。
- 成本保护：临时目录 24 小时生命周期自动删除；未确认对象不计入用户配额退还可视情况处理；每日对账 OSS 流量与业务计数。

### 6.11 立即发布

1. 提交前校验：登录有效、正文非空且 ≤600 字、话题有效、媒体均属于本人且状态为 `READY`、图片/视频组合合法。
2. 服务端在事务内写入动态、绑定媒体（置 `BOUND`）、更新话题计数（如启用）。
3. 发布成功返回 201 与新动态；前端清空编辑器与本地草稿，切换 Feed「最新发布」并提示。
4. 发布失败返回明确错误码；编辑器内容保留，可修改后重试。
5. 发布接口幂等：同一 `clientRequestId` 重复请求返回首次创建的动态，不重复写入。
6. 发布成功后发现媒体异常（如 OSS 对象被删除），不阻塞发布，但记录告警并在详情降级隐藏媒体。

### 6.12 定时发布

**时间规则**

- `scheduledAt` 必须晚于当前时间至少 5 分钟（D8），最远不超过 1 年。
- 客户端传 ISO 8601（带时区偏移），服务端统一转 UTC 存储。
- 定时动态在到点前仅作者可见，详情接口对他人返回 404。
- 到点前作者可改期或取消；取消后回到草稿，内容与媒体保留。

**调度实现要求**

- 使用 `@Scheduled` 每分钟扫描 `status='SCHEDULED' AND scheduled_at <= now()`，单批限量循环处理。
- Redis 分布式锁（`post:schedule:lock:{slot}`）保证多实例同一批次只有一个执行者。
- 条件更新 `UPDATE ... SET status='PUBLISHED', published_at=scheduled_at WHERE id=? AND status='SCHEDULED'`，天然幂等。
- 单条失败记录日志并保持 `SCHEDULED`，下一轮重试；连续失败超过阈值告警。
- `published_at` 取计划时间，便于 Feed 排序；扫描延迟单独监控。

### 6.13 编辑、删除与我的发布

**编辑**

- 仅作者可编辑；非作者返回 403，非作者查看草稿/定时返回 404。
- 可编辑正文、话题、媒体及排序；不可修改作者与创建时间。
- 已发布内容编辑后立即生效，更新 `edited_at`，详情显示「已编辑」；本期不保留历史版本（D7）。
- 编辑使用 `version` 乐观锁；移除的媒体解除绑定并进入待清理。
- 替换视频时，新视频必须已完成确认；旧视频解除绑定。

**删除**

- 仅作者可删除；软删除，写 `deleted_at` 与状态 `DELETED`。
- 删除后立即从 Feed、详情、个人主页、话题聚合消失；他人访问返回 404。
- 本期不提供回收站恢复（D10）；数据库记录保留 30 天，媒体对象由清理任务在保留期后删除。
- 删除幂等：重复删除返回 204。

**我的发布列表**

- `GET /api/v1/me/posts?status=DRAFT|SCHEDULED|PUBLISHED&cursor=&limit=`。
- 草稿/定时按 `updated_at DESC`，已发布按 `published_at DESC`。
- 列表项包含：ID、摘要、状态、话题、媒体类型与首图/封面、创建/更新/计划时间、编辑标识。
- 草稿支持「继续编辑」「删除」；定时支持「改期」「取消定时」「删除」。

### 6.14 发布后一致性

- 发布事务提交后，动态立即可被 `GET /posts/{id}` 读取，并出现在 Feed 第一页。
- 若 Feed 使用缓存，发布/编辑/删除后按作者或全局维度失效；本期无缓存时直接查库。
- 话题 `post_count` 在发布、删除、换话题时同事务增减；定时发布在调度事务内更新。
- 媒体签名 URL 不写入数据库，按请求动态签发，避免过期链接固化。

## 7. 状态机与业务规则

### 7.1 动态状态

| 状态 | 含义 | 对外可见 | 可编辑 | 可删除 |
|---|---|---|---|---|
| `DRAFT` | 草稿 | 否（仅作者） | 是 | 是 |
| `SCHEDULED` | 已定时 | 否（仅作者） | 是 | 是 |
| `PUBLISHED` | 已发布 | 是 | 是 | 是 |
| `DELETED` | 已软删除 | 否 | 否 | 幂等返回 204 |

### 7.2 动态状态迁移

| 当前状态 | 动作 | 目标状态 | 说明 |
|---|---|---|---|
| `DRAFT` | 立即发布 | `PUBLISHED` | 校验通过，写 `published_at` |
| `DRAFT` | 设置定时 | `SCHEDULED` | 写 `scheduled_at` |
| `SCHEDULED` | 到点调度成功 | `PUBLISHED` | 条件更新，幂等 |
| `SCHEDULED` | 取消定时 | `DRAFT` | 清空 `scheduled_at` |
| `SCHEDULED` | 改期 | `SCHEDULED` | 校验新时间 |
| `PUBLISHED` | 编辑 | `PUBLISHED` | 更新 `edited_at` |
| 任意非删除态 | 作者删除 | `DELETED` | 写 `deleted_at` |
| `DELETED` | 任何编辑/发布 | 不变 | 返回 404 或 409 |

### 7.3 媒体状态

| 状态 | 含义 | 可绑定发布 | 说明 |
|---|---|---|---|
| `INIT` | 已签发直传凭证 | 否 | 等待客户端上传 |
| `UPLOADING` | 上传中 | 否 | 直传未确认或后端上传未完成 |
| `READY` | 可用 | 是 | 已通过校验并位于正式路径 |
| `BOUND` | 已绑定动态 | 是（同一条） | 发布成功 |
| `FAILED` | 校验失败/上传失败 | 否 | 可重新上传 |
| `DELETED` | 已删除 | 否 | 待物理清理 |

### 7.4 业务规则

1. 正文按 Unicode 码点计数，去除首尾空白后 1～600。
2. 草稿允许正文为空；发布/定时时正文必须非空。
3. 图片与视频互斥，同一条动态只能出现一种媒体类型（D4）。
4. 媒体必须属于当前用户且为 `READY` 才能绑定；绑定后为 `BOUND`。
5. 已发布内容编辑后不回到草稿，不生成新动态。
6. 定时时间使用 UTC 存储，展示按用户本地时区格式化。
7. 同一作者同一 `clientRequestId` 只能创建一条动态。
8. 非作者访问草稿/定时内容统一返回 404；非作者编辑/删除返回 403。
9. 用户被禁用后不可发布、编辑、上传；已发布内容是否隐藏由账号模块决定。
10. 对象路径只由服务端生成，客户端传入的路径、文件名不参与拼接。

## 8. 数据模型

### 8.1 `posts`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `VARCHAR(40) ascii_bin` PK | `pst_` + ULID |
| `author_id` | `VARCHAR(40)` | 作者用户 ID，索引 |
| `status` | `VARCHAR(16)` | `DRAFT/SCHEDULED/PUBLISHED/DELETED` |
| `content` | `TEXT` | 正文，保留换行；应用层限制 600 码点 |
| `topic_id` | `VARCHAR(40)` NULL | 话题 ID |
| `media_type` | `VARCHAR(16)` NULL | `IMAGE/VIDEO`；无媒体时为空 |
| `scheduled_at` | `DATETIME(3)` NULL | 计划发布时间（UTC） |
| `published_at` | `DATETIME(3)` NULL | 实际/计划公开时间（UTC） |
| `edited_at` | `DATETIME(3)` NULL | 最近编辑时间 |
| `version` | `INT NOT NULL DEFAULT 1` | 乐观锁 |
| `client_request_id` | `VARCHAR(64)` NULL | 创建幂等键 |
| `created_at` | `DATETIME(3)` | 默认当前时间 |
| `updated_at` | `DATETIME(3)` | `ON UPDATE CURRENT_TIMESTAMP(3)` |
| `deleted_at` | `DATETIME(3)` NULL | 软删除时间 |

索引：

- `uk_posts_author_request (author_id, client_request_id)`：幂等唯一键。
- `idx_posts_author_status_updated (author_id, status, updated_at)`：我的发布列表。
- `idx_posts_feed (status, published_at, id)`：Feed 与详情排序。
- `idx_posts_schedule (status, scheduled_at)`：定时扫描。

### 8.2 `media_assets`

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `VARCHAR(40) ascii_bin` PK | `med_` + ULID |
| `owner_id` | `VARCHAR(40)` | 上传者，绑定/删除时校验 |
| `media_type` | `VARCHAR(16)` | `TEXT/IMAGE/VIDEO`；TEXT 预留不落 OSS |
| `upload_mode` | `VARCHAR(16)` | `SERVER/DIRECT` |
| `storage_provider` | `VARCHAR(16)` | `OSS` |
| `bucket` | `VARCHAR(64)` NULL | OSS Bucket，TEXT 为空 |
| `object_key` | `VARCHAR(512)` NULL | 正式对象 Key；不对外暴露 |
| `temp_object_key` | `VARCHAR(512)` NULL | 直传临时 Key，转正后清空 |
| `mime` | `VARCHAR(64)` | `image/jpeg`、`video/mp4` 等 |
| `size_bytes` | `BIGINT` | 实际大小 |
| `width` / `height` | `INT` NULL | 图片/视频像素尺寸 |
| `duration_ms` | `BIGINT` NULL | 视频时长 |
| `checksum` | `VARCHAR(64)` NULL | ETag/SHA-256，用于确认 |
| `post_id` | `VARCHAR(40)` NULL | 绑定动态 |
| `sort_order` | `INT` | 动态内顺序 |
| `status` | `VARCHAR(16)` | `INIT/UPLOADING/READY/BOUND/FAILED/DELETED` |
| `expires_at` | `DATETIME(3)` NULL | 直传凭证/临时对象过期时间 |
| `created_at` | `DATETIME(3)` | 创建时间 |
| `updated_at` | `DATETIME(3)` | 更新时间 |
| `deleted_at` | `DATETIME(3)` NULL | 清理时间 |

索引：`idx_media_post_sort (post_id, sort_order)`、`idx_media_owner_status (owner_id, status)`、`idx_media_temp (status, expires_at)`、`idx_media_post (post_id)`。

### 8.3 `topics`（最小依赖）

| 字段 | 类型 | 说明 |
|---|---|---|
| `id` | `VARCHAR(40)` PK | `tpc_` + ULID |
| `name` | `VARCHAR(32)` | 话题名，唯一 |
| `status` | `VARCHAR(16)` | `ACTIVE/DISABLED` |
| `post_count` | `BIGINT` | 已发布动态数 |
| `created_at/updated_at` | `DATETIME(3)` | 时间 |

> 话题模块归属待 D2 确认；若独立模块先交付，发布模块只依赖其只读接口与 `topics` 表。

### 8.4 数据约定

- 主键由应用生成，复用 `IdGenerator`，新增前缀 `pst_`、`med_`、`tpc_`。
- 时间统一 `Instant` + UTC，数据库 `DATETIME(3)`，接口输出 ISO 8601。
- 建表脚本放 `C-53_be/src/main/resources/db/post/post-schema.sql`，使用 `CREATE TABLE IF NOT EXISTS`，追加到 `spring.sql.init.schema-locations`。
- 多表写入使用 `TransactionTemplate` 显式事务；OSS 调用不放在数据库事务内，失败时补偿删除对象。
- 分页使用游标 `(published_at, id)` 或 `(updated_at, id)`，避免深分页。
- 签名 URL 不入库，按请求签发；数据库只保存 `bucket/object_key`。

## 9. 接口需求

### 9.1 通用约定

- 基础路径 `/api/v1`，认证方式 `Authorization: Bearer <accessToken>`。
- 成功响应 `{ "code": "OK", "data": ..., "requestId": "req_..." }`。
- 错误响应 `{ "code": "...", "message": "...", "details": [...], "requestId": "..." }`。
- 创建成功 201；删除成功 204；幂等重复提交 200 与已存在资源。
- 所有时间为 ISO 8601 UTC。
- 媒体响应中的 `url` 为短期签名地址（建议 1 小时），不是永久地址。

### 9.2 接口总览

| 方法 | 路径 | 认证 | 成功状态 | 说明 |
|---|---|---|---:|---|
| GET | `/api/v1/media/config` | 可选 | 200 | 上传阈值、允许类型、单文件上限 |
| POST | `/api/v1/media/upload` | 是 | 201 | 小文件后端上传（multipart，≤10MB） |
| POST | `/api/v1/media/upload-ticket` | 是 | 201 | 签发 STS 直传凭证与临时路径 |
| POST | `/api/v1/media/{id}/confirm` | 是 | 200 | 直传完成后确认并转正 |
| GET | `/api/v1/media/{id}` | 是 | 200 | 媒体元数据与签名 URL |
| DELETE | `/api/v1/media/{id}` | 是 | 204 | 删除未绑定/本人媒体 |
| POST | `/api/v1/posts` | 是 | 201 | 创建草稿、立即发布或定时发布 |
| PATCH | `/api/v1/posts/{id}` | 是 | 200 | 编辑内容，带 `version` |
| POST | `/api/v1/posts/{id}/publish` | 是 | 200 | 草稿立即发布 |
| POST | `/api/v1/posts/{id}/schedule` | 是 | 200 | 设置或修改定时 |
| POST | `/api/v1/posts/{id}/cancel-schedule` | 是 | 200 | 取消定时，回到草稿 |
| DELETE | `/api/v1/posts/{id}` | 是 | 204 | 作者软删除 |
| GET | `/api/v1/posts/{id}` | 可选 | 200 | 详情；草稿/定时仅作者可见 |
| GET | `/api/v1/me/posts` | 是 | 200 | 我的发布列表 |
| GET | `/api/v1/topics/trending` | 可选 | 200 | 发布可选话题（依赖话题模块） |

### 9.3 关键请求与响应

**获取上传配置**

```http
GET /api/v1/media/config
```

```json
{
  "code": "OK",
  "data": {
    "serverUploadMaxBytes": 10485760,
    "imageMaxBytes": 10485760,
    "videoMaxBytes": 524288000,
    "videoMaxDurationSeconds": 300,
    "imageMaxCount": 9,
    "videoMaxCount": 1,
    "allowedImageTypes": ["image/jpeg", "image/png", "image/webp"],
    "allowedVideoTypes": ["video/mp4"],
    "ticketTtlSeconds": 900,
    "partSizeBytes": 5242880
  },
  "requestId": "req_01K5..."
}
```

**小文件后端上传**

```http
POST /api/v1/media/upload
Authorization: Bearer <token>
Content-Type: multipart/form-data
file=@photo.jpg
mediaType=IMAGE
```

```json
{
  "code": "OK",
  "data": {
    "mediaId": "med_01K5...",
    "mediaType": "IMAGE",
    "url": "https://bucket.oss-cn-hangzhou.aliyuncs.com/...?Expires=...&Signature=...",
    "width": 2048,
    "height": 1365,
    "sizeBytes": 482133,
    "status": "READY"
  },
  "requestId": "req_01K5..."
}
```

**申请直传凭证**

```http
POST /api/v1/media/upload-ticket
Authorization: Bearer <token>
Content-Type: application/json

{
  "fileName": "demo.mp4",
  "contentType": "video/mp4",
  "sizeBytes": 104857600,
  "mediaType": "VIDEO"
}
```

```json
{
  "code": "OK",
  "data": {
    "mediaId": "med_01K6...",
    "uploadId": "upl_01K6...",
    "accessKeyId": "STS.NT...",
    "accessKeySecret": "***",
    "securityToken": "CAIS...",
    "expiration": "2026-10-23T09:15:00Z",
    "region": "oss-cn-hangzhou",
    "endpoint": "https://oss-cn-hangzhou.aliyuncs.com",
    "bucket": "c53-media-prod",
    "objectKey": "prod/temp/usr_01K5.../upl_01K6.../source.mp4",
    "partSizeBytes": 5242880,
    "status": "INIT"
  },
  "requestId": "req_01K6..."
}
```

**确认直传**

```http
POST /api/v1/media/med_01K6.../confirm
Authorization: Bearer <token>
Content-Type: application/json

{ "etag": "F1A2...", "sizeBytes": 104857600 }
```

```json
{
  "code": "OK",
  "data": {
    "mediaId": "med_01K6...",
    "mediaType": "VIDEO",
    "objectKey": "prod/video/2026/10/23/usr_01K5.../med_01K6....mp4",
    "url": "https://bucket.oss-cn-hangzhou.aliyuncs.com/...Expires=...",
    "sizeBytes": 104857600,
    "durationMs": 42350,
    "width": 1920,
    "height": 1080,
    "status": "READY"
  },
  "requestId": "req_01K6..."
}
```

**创建动态**

```http
POST /api/v1/posts
Authorization: Bearer <token>
Content-Type: application/json

{
  "content": "最近在重新思考内容社区的节奏。\n\n不是让人停不下来，而是让每一次停留都更有意义。",
  "topicId": "tpc_01K5...",
  "mediaIds": ["med_01K6..."],
  "status": "PUBLISHED",
  "scheduledAt": null,
  "clientRequestId": "0f9c2f1e-...-uuid"
}
```

- `status`：`DRAFT`、`PUBLISHED`、`SCHEDULED`；`SCHEDULED` 必须传 `scheduledAt`。
- `mediaIds` 顺序即展示顺序；图片 ≤9，视频 ≤1，二者不可混用。
- 兼容旧字段 `imageIds`，内部转换为 `mediaIds`。
- 成功返回动态对象，字段对齐前端 `normalizePost`：`id、author、topic、content、mediaType、media、status、createdAt、publishedAt、editedAt、version`。

**编辑动态**

```http
PATCH /api/v1/posts/{id}
{ "content": "...", "topicId": "...", "mediaIds": ["med_..."], "version": 3 }
```

- 版本不匹配返回 `409 VERSION_CONFLICT`，`details` 带服务端当前 `version`。
- 已发布内容编辑成功后更新 `editedAt`，状态保持 `PUBLISHED`。

### 9.4 错误码

| HTTP | code | 场景 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 字段缺失、格式错误、正文超长 |
| 400 | `CONTENT_EMPTY` | 发布时正文为空 |
| 400 | `INVALID_SCHEDULE_TIME` | 定时时间过近/过远/格式错误 |
| 400 | `MEDIA_TYPE_MISMATCH` | 图片与视频混用、类型与接口不符 |
| 401 | `UNAUTHORIZED` | 未登录或 Access Token 失效 |
| 403 | `FORBIDDEN` | 编辑/删除他人内容或媒体 |
| 404 | `POST_NOT_FOUND` | 内容不存在、已删除、非作者访问草稿/定时 |
| 404 | `MEDIA_NOT_FOUND` | 媒体不存在或不属于当前用户 |
| 404 | `TOPIC_NOT_FOUND` | 话题不存在或已停用 |
| 409 | `VERSION_CONFLICT` | 乐观锁版本不一致 |
| 409 | `INVALID_STATUS_TRANSITION` | 非法状态迁移 |
| 409 | `MEDIA_NOT_READY` | 绑定未完成上传/确认的媒体 |
| 413 | `PAYLOAD_TOO_LARGE` | 超过后端上传阈值或图片 10MB 限制 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 格式不在白名单或魔数不符 |
| 422 | `MEDIA_INVALID` | 媒体归属、数量、尺寸、时长不合法 |
| 422 | `MEDIA_CONFIRM_FAILED` | 直传对象校验失败 |
| 422 | `VIDEO_TOO_LONG` | 视频超过 5 分钟 |
| 429 | `RATE_LIMITED` | 发布/上传/凭证申请超限 |
| 503 | `OSS_UNAVAILABLE` | OSS 或 STS 不可用 |
| 504 | `UPLOAD_TICKET_EXPIRED` | 凭证或临时对象已过期 |

## 10. 权限、安全与风控

### 10.1 认证与授权

- `SecurityConfig` 需新增显式规则：
  - `POST/PATCH/DELETE /api/v1/posts/**`、`/api/v1/media/**` → `authenticated()`。
  - `GET /api/v1/posts/**`、`GET /api/v1/topics/**` → 放行，游客可读。
  - `GET /api/v1/media/config` 放行；媒体详情需认证。
- Controller 使用 `@AuthenticationPrincipal AuthPrincipal principal` 获取 `userId`。
- 写操作在 Service 再次校验会话有效（参考 `AuthServiceImpl.me`），不只信任 JWT 签名。
- 作者/媒体归属校验与资源状态校验分开：非作者访问私有内容返回 404，非作者执行写操作返回 403。

### 10.2 OSS 安全

- 使用专用 RAM Role + STS，不使用主账号 AK；长期密钥只存在于服务端环境变量/密钥管理。
- STS 会话策略仅允许目标临时前缀的写操作，不授予读、列、删权限。
- 对象路径、`uploadId`、`mediaId` 全部服务端生成；拒绝任何客户端传入的路径。
- Bucket 私有读；对外访问使用 1 小时签名 URL；详情/列表按需签发，不在数据库固化。
- CORS 白名单限定站点域名；方法仅为 `PUT/HEAD/POST`；不开放 `*`。
- 开启 OSS 访问日志、异常流量告警和生命周期规则：`temp/` 24 小时删除、未完成分片 24 小时中止。
- 上传后确认时校验对象大小、MIME、ETag、视频时长；超限对象立即删除并记录风控事件。
- 不记录 STS 密钥、签名 URL、视频二进制到日志。

### 10.3 内容安全

- 正文纯文本，前端使用插值，禁止 `v-html`；后端不解析 HTML。
- 拒绝包含 `\u0000` 等控制字符的正文，统一换行符为 `\n`。
- 基础敏感词过滤：命中返回 422 `CONTENT_REJECTED`（词表与策略见 D9）。
- 图片重编码剥离 EXIF/GPS；视频不提供公开直链，避免原片被批量抓取。
- 日志仅记录 `postId`、`mediaId`、`authorId`、状态、耗时、错误码。

### 10.4 防刷与限流

- 限流表见 6.10；核心目标是保护 STS 签发接口、后端上传接口和 OSS 成本。
- 同一用户有效直传凭证数量受限；凭证过期后必须重新申请。
- 每日配额按用户维度计数，跨天重置；异常用户可人工加入黑名单（本期仅数据库配置）。
- Redis 故障时 fail-open 并告警；发布幂等由数据库唯一键兜底，上传安全由 STS 前缀限制与确认校验兜底。

### 10.5 幂等与重复提交

- 创建动态以 `(author_id, client_request_id)` 唯一索引兜底；捕获冲突后查询并返回已存在动态。
- 前端进入发布流程时生成 `clientRequestId`，重试复用；发布成功后重置。
- 自动保存使用 `version` 乐观锁，重复提交同一版本只成功一次。
- `upload-ticket` 每次生成新 `uploadId`；同一客户端重复申请由限流约束。
- `confirm` 幂等，重复调用返回同一媒体。
- 定时任务使用状态条件更新，重复扫描不会重复发布。

## 11. 非功能需求

### 11.1 性能

| 指标 | 目标 |
|---|---|
| 发布接口 P95 | < 300ms（不含上传） |
| 草稿自动保存 P95 | < 200ms |
| 详情读取 P95 | < 150ms |
| 小文件后端上传 P95 | < 1s（图片 5MB 以内） |
| 直传凭证签发 P95 | < 400ms |
| 直传确认 P95 | < 800ms（含 CopyObject，视频大小相关） |
| 我的发布列表 P95 | < 250ms |
| 定时发布延迟 | ≤ 60 秒 |

### 11.2 可用性与一致性

- 发布主链路依赖 MySQL；媒体链路依赖 OSS。OSS 不可用时允许发布纯文字动态，带媒体发布返回 `OSS_UNAVAILABLE` 并保留草稿。
- 发布前必须确保媒体为 `READY`，不允许「正文已发布但媒体缺失」的半成品。
- 后端上传顺序：校验 → 重编码 → 写 OSS → 写数据库；数据库失败时补偿删除 OSS 对象。
- 直传顺序：签发 → 上传 → 确认 → 转正 → 绑定；未确认对象不进入正式目录。
- Redis 仅用于限流、锁和可选缓存，不作为发布成功的必要条件。

### 11.3 时区与时间

- 全部 UTC 存储，接口输出 ISO 8601。
- 定时时间由客户端带时区提交，服务端转换后存储；展示由前端本地化。
- 定时扫描使用统一注入的 `Clock`，避免多实例时钟偏差。
- 直传凭证过期时间以服务端时间为准。

### 11.4 可观测性

- 结构化日志字段：`requestId`、`userId`、`postId`、`mediaId`、`uploadMode`、`status`、`durationMs`、`errorCode`、`ossRequestId`。
- 指标：发布 QPS/成功率/P95、直传与后端上传比例、上传成功率、凭证签发量、确认失败率、定时任务延迟、限流命中、OSS 流量与存储增量。
- 告警：发布错误率 > 1% 持续 5 分钟；直传确认失败率 > 2%；STS 签发失败；定时任务连续 2 轮未执行；临时目录对象数异常增长。

### 11.5 容量假设

- 日发布量 ≤1 万条；单用户受每日配额限制。
- 图片：单条 ≤9 张、单张 ≤10MB；视频：单条 ≤500MB。
- 存储：按月新增 100GB 预估，实际以 OSS 监控为准；临时对象 24 小时自动清理。
- 数据保留：已发布内容长期；草稿 90 天；临时对象 24 小时；删除记录与媒体保留 30 天。

## 12. 前端交互需求

| 模块 | 需求 |
|---|---|
| 入口 | 复用 `AppTopbar.vue`「＋ 发布」与 `FeedView.vue` 发布框；未登录跳登录并回跳 |
| 编辑器 | 保持手写 CSS 风格，不引入 UI 组件库；正文 `textarea`，实时字数 |
| 草稿状态 | 「保存中/已保存 HH:mm/未保存」，失败可重试；进入时恢复最近草稿 |
| 话题 | 下拉单选，来自 `GET /topics/trending`；无可用话题时允许草稿但阻止发布 |
| 图片上传 | 选择、预览、进度、拖动排序、单张删除、9 张上限；调用后端上传接口 |
| 视频直传 | 选择后读取元数据校验；申请凭证；使用 `ali-oss` 分片直传；进度、暂停、继续、取消；完成后确认 |
| 断点续传 | 本地记录 `uploadId` 与分片进度，刷新后可继续；过期提示重新上传 |
| 媒体组合 | 选择图片后禁用视频，反之亦然，并提示互斥规则 |
| 定时 | 时间选择器，展示本地时间并提示时区；提交前校验 ≥5 分钟 |
| 发布反馈 | 成功清空编辑态、切「最新发布」、Toast；失败保留内容并展示错误文案 |
| 编辑/删除 | 详情页作者可见「编辑」「删除」；删除二次确认 |
| 列表 | 个人主页增加草稿/定时管理入口，显示状态与计划时间 |
| 兼容 | 保留 `api.createPost({ topicId, content })` 调用，缺省 `status` 默认 `PUBLISHED`；`imageIds` 兼容为 `mediaIds` |

前端改造文件建议：`src/services/api.js`（新增媒体上传、凭证、确认接口）、`src/App.vue`（发布/草稿/定时/上传状态）、`src/components/feed/FeedView.vue`（编辑器与媒体选择）、`src/components/posts/PostDetailView.vue`（编辑/删除/视频播放）、`src/style.css`。新增依赖 `ali-oss`（仅直传使用）。

## 13. 埋点与数据指标

| 事件 | 触发时机 | 关键属性 |
|---|---|---|
| `post_publish_clicked` | 点击发布 | `hasTopic`、`mediaType`、`mediaCount`、`contentLength` |
| `post_publish_succeeded` | 发布成功 | `postId`、`status`、`durationMs`、`mediaType` |
| `post_publish_failed` | 发布失败 | `errorCode`、`durationMs` |
| `draft_autosave_succeeded` | 自动保存成功 | `postId`、`durationMs`、`version` |
| `draft_autosave_failed` | 自动保存失败 | `errorCode`、`retryCount` |
| `media_upload_mode` | 上传开始 | `mode=SERVER/DIRECT`、`mediaType`、`sizeBytes` |
| `media_upload_succeeded` | 上传/确认成功 | `mediaId`、`durationMs`、`sizeBytes`、`partCount` |
| `media_upload_failed` | 上传/确认失败 | `errorCode`、`stage`、`sizeBytes` |
| `upload_ticket_requested` | 申请凭证 | `sizeBytes`、`result` |
| `post_scheduled` | 设置定时 | `scheduledOffsetSeconds` |
| `post_schedule_fired` | 定时触发 | `delaySeconds`、`success` |

核心指标：发布成功率、直传成功率、确认失败率、草稿转发布率、自动保存失败率、定时准点率、限流命中率、OSS 流量成本/用户。

## 14. 验收标准

### 14.1 功能验收

- [ ] 登录用户可发布 1～600 字纯文本动态，Feed 与个人主页立即可见。
- [ ] 正文仅空白、超过 600 字、无有效话题时无法发布并给出明确提示。
- [ ] 草稿可自动保存、恢复、继续编辑；草稿不出现在 Feed 与他人主页。
- [ ] 同一作者双击发布或相同 `clientRequestId` 重试，只产生一条动态。
- [ ] ≤10MB 图片经后端上传成功，服务端完成重编码并剥离 EXIF。
- [ ] >10MB 图片被拒绝并提示压缩，不签发直传凭证。
- [ ] ≤10MB 视频走后端上传；>10MB 视频走 STS 直传，浏览器不经过业务服务器。
- [ ] 直传支持分片、进度、暂停、续传、取消；断网后可从已完成分片继续。
- [ ] 凭证过期后上传失败并提示重新申请；旧凭证不能继续写。
- [ ] 客户端无法修改 `objectKey` 或写入其他用户目录。
- [ ] 直传完成后未确认的媒体不能绑定发布。
- [ ] 确认时大小、MIME、时长不合格的对象被删除并返回明确错误。
- [ ] 图片与视频不能混用；图片 ≤9、视频 ≤1。
- [ ] 可设置 ≥5 分钟后的定时发布；到点 ±60 秒内公开；取消后回到草稿且内容保留。
- [ ] 作者可编辑已发布内容并显示编辑标识；非作者编辑返回 403。
- [ ] 作者可删除；删除后 Feed、详情、个人主页均不可见；重复删除返回 204。
- [ ] 草稿/定时内容对非作者返回 404。
- [ ] 断网时自动保存失败但编辑器内容保留，可手动重试。

### 14.2 安全与边界验收

- [ ] 未登录调用发布、上传、凭证接口返回 401。
- [ ] 使用他人媒体 ID 绑定发布返回 422 或 404。
- [ ] 伪造扩展名、SVG、非 MP4 文件、魔数不符的文件被拒绝。
- [ ] 直传 STS 凭证无 List/Get/Delete 权限，越权请求 OSS 被拒绝。
- [ ] 私有对象直接访问无签名 URL 时被拒绝；签名 URL 过期后不可访问。
- [ ] 超出凭证申请、上传、发布频率返回 429，并带 `retryAfter`。
- [ ] 临时对象超过 24 小时被生命周期规则删除；未完成分片被中止。
- [ ] 并发编辑同一草稿，后提交者收到 409 并可选择载入最新或覆盖。
- [ ] 定时任务多实例部署时，同一条动态只发布一次；到点前删除后不会复活。
- [ ] 正文含 HTML 标签时按纯文本展示，不执行脚本。
- [ ] 日志不包含正文全文、STS 密钥、签名 URL、媒体二进制。

### 14.3 非功能验收

- [ ] 发布 P95 < 300ms、自动保存 P95 < 200ms、凭证签发 P95 < 400ms（本地基准）。
- [ ] 500MB 视频直传期间，应用服务器 CPU、内存、出口带宽无明显上升。
- [ ] `post-schema.sql` 可在空库执行，重复执行不报错。
- [ ] `SecurityConfig` 写接口均要求认证。
- [ ] OSS 生命周期、CORS、日志与告警配置完成并有截图/配置记录。

## 15. 里程碑与交付物

| 阶段 | 内容 | 建议人天 | 交付物 |
|---|---|---:|---|
| M0 | 评审 PRD，确认 D1～D10；开通 OSS/RAM/STS | 1.5 | 评审通过的 PRD、OSS 配置清单 |
| M1 | 数据模型、schema、接口契约、SecurityConfig、OSS SDK 接入 | 2 | `post-schema.sql`、接口文档、存储抽象 |
| M2 | 文字发布、草稿、自动保存、编辑、删除、我的列表 | 3 | 发布闭环后端接口 |
| M3 | 小文件后端上传、图片重编码、媒体绑定 | 2 | 服务端上传能力 |
| M4 | STS 凭证、直传确认转正、分片续传、清理任务 | 3 | 大文件直传能力 |
| M5 | 定时发布、分布式锁、调度任务 | 1.5 | 定时发布能力 |
| M6 | 前端编辑器、上传交互、视频播放、联调 | 3 | 前后端联调版本 |
| M7 | 安全测试、限流/配额压测、验收与文档 | 2 | 验收版本 |
| 缓冲 | OSS 配置、网络、评审返工 | 2 | — |
| **合计** |  | **20** | 约 20 人天 |

交付物：PRD（本文）、发布模块接口契约、`post-schema.sql`、后端 `com.c53.modules.post` 实现、OSS 配置与生命周期规则说明、前端发布与上传改造、测试报告。

## 16. 风险

| 风险 | 影响 | 应对 |
|---|---|---|
| OSS/RAM/STS 未开通或权限配置错误 | 直传与后端上传全部失败 | M0 完成配置与连通性验证；使用最小权限策略演练 |
| STS 无法限制 content-length | 恶意用户上传超大文件产生费用 | 声明大小 + HeadObject 校验 + 超限删除 + 日配额 + 告警 |
| 浏览器 CORS 配置错误 | 直传失败 | 限定域名与方法，联调前用最小页面验证 |
| 视频格式不兼容 | 上传成功但无法播放 | 仅接受 MP4/H.264，前端读取元数据提示用户 |
| 临时对象与未完成分片堆积 | 存储成本上升 | 生命周期规则 24 小时清理 + AbortMultipartUpload 任务 |
| 签名 URL 过期 | 图片/视频打不开 | URL 有效期 1 小时，前端失败重取详情；不写入数据库 |
| 大文件直传中断 | 用户重复上传 | 分片 + 本地记录 + 续传 + 明确进度 |
| 图片与视频互斥影响体验 | 用户想图文+视频 | 本期明确限制，后续以独立内容类型扩展 |
| 限流 fail-open | Redis 故障时可能被刷 | OSS 配额、确认校验、日志告警与人工封禁兜底 |
| 话题模块未交付 | 无法选择有效话题 | 先提供最小 `topics` 只读接口或合并排期 |

## 17. 待确认决策

| 编号 | 决策点 | 建议方案 | 影响 |
|---|---|---|---|
| D1 | OSS 资源归属 | 独立 Bucket、独立 RAM Role、按环境前缀隔离 | 决定运维与权限边界 |
| D2 | 话题归属与是否必填 | 建议必填；`topics` 由话题模块维护，发布模块只读 | 影响联调依赖 |
| D3 | 媒体读取方式 | Bucket 私有 + 1 小时签名 URL；后续可切 CDN 私有鉴权 | 影响访问性能与安全 |
| D4 | 图片与视频是否互斥 | 本期互斥，一条动态只允许一种媒体类型 | 影响编辑器与数据模型 |
| D5 | 视频封面 | 本期不生成；可后续由前端截帧上传封面 | 影响详情页体验 |
| D6 | 草稿保留期与上限 | 90 天 / 每用户 50 篇 | 影响清理任务 |
| D7 | 已发布编辑是否留版本 | 本期不保留，仅记录 `edited_at` | 影响审计与存储 |
| D8 | 定时最小提前量与精度 | ≥5 分钟，分钟级执行 | 影响前端校验与调度频率 |
| D9 | 敏感词/内容审核 | 本期基础词表过滤，不做人工审核 | 影响内容安全与错误码 |
| D10 | 删除与媒体清理 | 软删不可恢复；数据保留 30 天，媒体延后清理 | 影响清理任务 |

## 18. 评审清单

- [ ] 确认 OSS Bucket、Region、RAM Role、STS 权限与临时目录生命周期规则。
- [ ] 确认 10MB 分流阈值与前端可配置读取方式。
- [ ] 确认图片禁止直传、视频 >10MB 直传的策略。
- [ ] 确认视频上限（500MB、5 分钟、MP4/H.264）与仅存原片不转码。
- [ ] 确认图片与视频互斥。
- [ ] 确认媒体读取方式（私有 Bucket + 签名 URL）与 URL 有效期。
- [ ] 确认直传临时目录 → 确认转正的流程与 24 小时清理。
- [ ] 确认 STS 无法限制大小情况下的补偿校验与配额策略。
- [ ] 确认限流阈值、每日配额与直传流量配额。
- [ ] 确认 D2 话题归属与交付顺序。
- [ ] 确认 D6 草稿、D9 敏感词策略。
- [ ] 确认接口路径与前端 `api.js` 兼容（`POST /posts`、`GET /me/posts`、`imageIds → mediaIds`）。
- [ ] 确认 `SecurityConfig` 新增规则不影响认证模块现有路由。
