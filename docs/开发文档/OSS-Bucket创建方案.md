# 阿里云 OSS Bucket 创建方案

> 状态：待执行  
> 版本：1.0  
> 日期：2026-10-23  
> 关联：`docs/开发文档/发布功能开发方案.md` 第 8 章、`docs/需求文档/发布功能PRD.md` 第 6.5～6.7 节  
> 适用：C-53 发布功能的图片、视频存储与前端直传

## 1. 目标与原则

| 原则 | 落地方式 |
|---|---|
| 私有存储 | Bucket 读写权限设为「私有」，开启「阻止公共访问」 |
| 环境隔离 | dev / test / prod 各一个 Bucket，避免误删和混用 |
| 最小权限 | 后端使用 RAM 用户，前端直传使用 STS 临时凭证；STS 只能写临时前缀 |
| 路径可控 | 对象 Key 全部由服务端生成，客户端不能拼接路径 |
| 成本可控 | 临时目录 24 小时自动过期，未完成分片自动中止，按环境打标签 |
| 可追溯 | 开启访问日志、配置异常流量告警 |

## 2. 资源规划

| 资源 | 命名示例 | 说明 |
|---|---|---|
| 生产 Bucket | `c53-media-prod-8f3k2a` | 全局唯一，后缀随机 |
| 测试 Bucket | `c53-media-test-8f3k2a` | 联调与验收 |
| 开发 Bucket | `c53-media-dev-8f3k2a` | 本地开发，可与测试合并 |
| 日志 Bucket | `c53-log-prod-8f3k2a` | 存放 OSS 访问日志 |
| RAM 用户 | `c53-app` | 后端进程使用，持有长期 AK |
| RAM 角色 | `c53-upload-role` | 仅用于签发 STS，前端临时使用 |
| 访问策略 | `c53-oss-backend-policy` | 绑定 `c53-app`，控制对象读写 |
| 角色策略 | `c53-oss-upload-policy` | 绑定 `c53-upload-role`，只允许写临时前缀 |

Bucket 命名规则：3～63 个字符，只能包含小写字母、数字和短横线，必须以字母或数字开头和结尾。建议格式：

```text
c53-media-{env}-{6位随机字母数字}
```

选择 Region 的原则：

- 与后端 ECS / 容器所在 Region 一致，走内网、免流量费、延迟最低。
- 与主要用户群接近。当前 `application.yml` 默认 `oss-cn-hangzhou`，如更换 Region 必须同步修改配置。
- 同一个 Bucket 不能跨 Region 访问；SDK 的 `endpoint` 必须与 Bucket 的 Region 匹配。

## 3. Bucket 基础参数

每个环境都按以下参数创建：

| 参数 | 取值 | 说明 |
|---|---|---|
| 地域 | `华东1（杭州）` 或实际部署地域 | 与服务器同地域 |
| 存储类型 | `标准存储` | 图片、视频需频繁读取 |
| 数据冗余 | dev/test `本地冗余 LRS`；prod 建议 `同城冗余 ZRS` | ZRS 成本更高，按预算选择 |
| 读写权限 | `私有` | 所有访问走签名 URL |
| 版本控制 | 默认关闭 | 删除有 30 天数据库保留期，暂不需要版本 |
| 服务端加密 | 开启 `OSS 完全托管（SSE-OSS）` | 免费，静态加密 |
| 阻止公共访问 | 开启 | 防止误配公共读 |
| 静态网站托管 | 关闭 | 不使用 |
| 传输加速 | 关闭 | 本期不需要 |
| 图片处理 | 暂不开启 | 缩略图后续再评估 |
| 资源组 / 标签 | `project=c53`、`env={env}` | 便于成本核算 |

> 版本控制如果后续开启，必须同时配置「非当前版本 30 天清理」的生命周期规则，否则存储成本会持续增长。

## 4. CORS 配置

前端直传与视频播放依赖浏览器跨域，CORS 必须精确配置。

| 项 | dev / test | prod |
|---|---|---|
| 来源 | `http://localhost:5173`、`http://127.0.0.1:5173` | 正式站点域名，如 `https://c53.example.com` |
| 允许方法 | `GET, HEAD, PUT, POST` | 同左 |
| 允许 Headers | `*` | `*` |
| 暴露 Headers | `ETag`、`x-oss-request-id` | 同左 |
| 缓存时间 | 3600 秒 | 3600 秒 |
| Vary: Origin | 开启 | 开启 |

控制台配置片段：

```json
[
  {
    "AllowedOrigin": ["http://localhost:5173", "http://127.0.0.1:5173"],
    "AllowedMethod": ["GET", "HEAD", "PUT", "POST"],
    "AllowedHeader": ["*"],
    "ExposeHeader": ["ETag", "x-oss-request-id"],
    "MaxAgeSeconds": 3600
  }
]
```

说明：

- 分片上传会用到 `POST`（初始化与完成分片），不能只配 `PUT`。
- 不配置 `DELETE`，客户端不需要删除对象；删除由后端完成。
- 禁止使用 `*` 作为来源；生产环境必须写死域名。
- 前端需要读取 `ETag` 做确认校验，必须放在「暴露 Headers」里。
- 如果使用自定义域名或 CDN，把该域名加入来源列表。

## 5. 生命周期规则

在 Bucket 的「数据管理 → 生命周期」中创建两条规则：

| 规则 | 前缀 | 动作 | 说明 |
|---|---|---|---|
| `expire-temp` | `{env}/temp/` | 对象过期删除，1 天 | 临时目录超过 24 小时自动清理 |
| `abort-multipart` | 全 Bucket 或 `{env}/temp/` | 中止未完成的分片上传，1 天 | 防止碎片占用存储 |

`{env}` 取值为 `dev`、`test`、`prod`，与后端 `media.env` 一致。

注意事项：

- 生命周期按天计算，实际清理可能延迟数小时，属于正常现象。
- 不要给正式目录 `{env}/image/`、`{env}/video/` 配置自动过期，媒体清理由业务任务控制。
- 临时对象被生命周期删除后，数据库中仍可能残留 `UPLOADING` 记录，由 `MediaCleanupJob` 置为 `FAILED`。
- 规则创建后立即生效，但只影响之后的对象，不会追溯已删除对象。

## 6. 访问日志与监控

**访问日志**

- 开启「日志管理 → 访问日志」，目标 Bucket 选择 `c53-log-{env}`。
- 日志 Bucket 单独设置生命周期，例如保留 90 天，避免无限增长。
- 日志 Bucket 不配置 CORS，不允许业务访问。

**监控告警**

| 指标 | 建议阈值 | 处理 |
|---|---|---|
| 4xx 错误率 | 5 分钟内 > 5% | 检查签名、CORS、权限 |
| 5xx 错误率 | 5 分钟内 > 1% | 检查 OSS 服务状态 |
| 外网流出流量 | 超过日常峰值 2 倍 | 排查异常下载或直传 |
| 临时目录对象数 | 持续增长 | 检查生命周期规则与清理任务 |
| 存储用量 | 按周环比 | 核对媒体增长与配额 |

**成本标签**

- 每个 Bucket 打标签：`project=c53`、`env=prod`。
- 在费用中心按标签做成本分摊和预算告警。

## 7. 权限设计

### 7.1 权限矩阵

| 身份 | 使用场景 | 允许操作 | 资源范围 |
|---|---|---|---|
| RAM 用户 `c53-app` | 后端进程 | 读写、复制、删除对象，读取元数据 | `c53-media-{env}/*` |
| RAM 角色 `c53-upload-role` | 前端直传 | 写入、分片、完成/中止分片 | 由 STS 会话策略收窄到 `temp/{ownerId}/{uploadId}/*` |
| 匿名访问 | 游客 | 无 | 无 |
| 签名 URL | 图片/视频读取 | `GetObject` | 单个对象，1 小时有效 |

### 7.2 后端 RAM 用户策略

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "oss:PutObject",
        "oss:GetObject",
        "oss:DeleteObject",
        "oss:AbortMultipartUpload",
        "oss:ListParts",
        "oss:ListMultipartUploads"
      ],
      "Resource": [
        "acs:oss:*:*:c53-media-prod-8f3k2a/*"
      ]
    },
    {
      "Effect": "Allow",
      "Action": "sts:AssumeRole",
      "Resource": "acs:ram::<accountId>:role/c53-upload-role"
    }
  ]
}
```

说明：

- `CopyObject` 不是独立 Action，它由源对象的 `oss:GetObject` 和目标对象的 `oss:PutObject` 共同授权。
- `HeadObject` 需要 `oss:GetObject` 权限。
- 生成签名 URL 是本地计算，不需要额外权限。
- 后端默认不需要 `ListObjects`；如果后续做对象对账任务，再单独授权临时目录的列举权限。

### 7.3 STS 角色信任策略

```json
{
  "Version": "1",
  "Statement": [
    {
      "Effect": "Allow",
      "Principal": {
        "RAM": ["acs:ram::<accountId>:user/c53-app"]
      },
      "Action": "sts:AssumeRole"
    }
  ]
}
```

只信任后端 RAM 用户，不使用主账号 root 作为 Principal。

### 7.4 角色权限策略

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
        "oss:AbortMultipartUpload",
        "oss:ListParts"
      ],
      "Resource": ["acs:oss:*:*:c53-media-prod-8f3k2a/*"]
    }
  ]
}
```

角色本身只授予写能力。后端签发 STS 时会再叠加会话策略，把范围收窄到单个上传目录：

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
        "acs:oss:*:*:c53-media-prod-8f3k2a/prod/temp/usr_01K5.../upl_01K6.../*"
      ]
    }
  ]
}
```

安全要求：

- 会话策略与角色策略取交集，缺一不可。
- 不授予 `GetObject`、`ListObjects`、`DeleteObject`，客户端无法读取或删除任何对象。
- 路径中的 `ownerId`、`uploadId` 必须由后端生成，不能使用客户端传入值。
- 凭证有效期 15 分钟，过期后重新申请。
- 会话策略 JSON 控制在 2048 字节以内。

## 8. 应用配置映射

创建完成后，把参数写入部署环境变量，不写入仓库：

| 环境变量 | 示例 | 说明 |
|---|---|---|
| `MEDIA_ENV` | `prod` | 对象 Key 的环境前缀 |
| `MEDIA_OSS_ENDPOINT` | `https://oss-cn-hangzhou.aliyuncs.com` | Bucket 所在地域端点 |
| `MEDIA_OSS_REGION` | `oss-cn-hangzhou` | OSS SDK Region |
| `MEDIA_OSS_BUCKET` | `c53-media-prod-8f3k2a` | Bucket 名 |
| `MEDIA_OSS_ACCESS_KEY_ID` | `LTAI...` | RAM 用户 AK |
| `MEDIA_OSS_ACCESS_KEY_SECRET` | `******` | RAM 用户 SK |
| `MEDIA_OSS_ROLE_ARN` | `acs:ram::<accountId>:role/c53-upload-role` | STS 角色 |
| `MEDIA_STS_ENDPOINT` | `sts.cn-hangzhou.aliyuncs.com` | STS 服务端点 |

补充约定：

- STS 的 `RegionId` 与 OSS Region 不同，取值为 `cn-hangzhou`。实现时由 `media.region` 去掉 `oss-` 前缀得到，或单独增加 `media.sts-region` 配置。
- `RoleSessionName` 使用 `userId`，必须满足 `^[a-zA-Z0-9.\-_]{2,64}$`，现有 `usr_` + ULID 符合要求。
- AK 只在后端环境变量或密钥管理系统中出现；前端只知道 STS 临时凭证。
- 建议每 90 天轮换一次 RAM 用户 AK，并保留双 AK 过渡窗口。

## 9. 验证清单

创建完成后逐项验证：

- [ ] 匿名访问 `https://{bucket}.{endpoint}/{env}/image/...` 返回 403。
- [ ] 后端使用 RAM 用户可正常 `PutObject`、`HeadObject`、`CopyObject`、删除对象。
- [ ] 后端可成功调用 `AssumeRole`，返回的凭证 15 分钟过期。
- [ ] 使用 STS 凭证只能写入 `{env}/temp/{ownerId}/{uploadId}/`，写入其他前缀返回 403。
- [ ] 使用 STS 凭证执行 `GetObject`、`DeleteObject`、`ListObjects` 返回 403。
- [ ] 浏览器预检请求 `OPTIONS` 返回 200，包含 `Access-Control-Allow-Origin`。
- [ ] 分片上传可完成，响应头包含 `ETag`，前端能读取。
- [ ] 生成的签名 URL 在有效期内可访问，过期后返回 403。
- [ ] 临时目录对象在 24 小时后被生命周期删除。
- [ ] 未完成分片在 24 小时后被中止。
- [ ] 访问日志出现在日志 Bucket 中。
- [ ] 告警规则已生效，测试告警可到达负责人。

CORS 预检验证示例：

```bash
curl -i -X OPTIONS "https://c53-media-prod-8f3k2a.oss-cn-hangzhou.aliyuncs.com/prod/temp/usr_x/upl_x/source.mp4" \
  -H "Origin: https://c53.example.com" \
  -H "Access-Control-Request-Method: PUT"
```

预期返回 `204` 或 `200`，并包含：

```text
Access-Control-Allow-Origin: https://c53.example.com
Access-Control-Allow-Methods: GET, HEAD, PUT, POST
Access-Control-Allow-Headers: *
Access-Control-Expose-Headers: ETag, x-oss-request-id
```

## 10. 常见问题

| 现象 | 原因 | 处理 |
|---|---|---|
| 直传返回 403 `AccessDenied` | 会话策略路径与实际上传 Key 不一致 | 检查 `{env}/temp/{ownerId}/{uploadId}/` 拼接 |
| 浏览器报 CORS 错误 | 来源、方法或暴露 Headers 缺失 | 按第 4 节补齐，清浏览器缓存后重试 |
| `SignatureDoesNotMatch` | Endpoint 或 Region 与 Bucket 不匹配 | 检查 `MEDIA_OSS_ENDPOINT` |
| STS `NoPermission` | 信任策略未包含后端 RAM 用户 | 修正 Principal |
| 无法读取 ETag | 未在 CORS 暴露 `ETag` | 加入 ExposeHeader |
| 临时文件越积越多 | 生命周期前缀写错 | 确认前缀为 `{env}/temp/` |
| 上传 500MB 视频超时 | 使用了后端上传通道 | 确认前端走 STS 直传 |
| 视频上传成功但播放失败 | 编码不是 H.264/AAC | 前端提前提示，后端确认阶段拒绝 |

## 11. 执行顺序

1. 开通 OSS，确认计费方式与 Region。
2. 创建三个业务 Bucket 与一个日志 Bucket。
3. 设置私有权限、阻止公共访问、服务端加密。
4. 配置 CORS、生命周期规则、访问日志。
5. 创建 RAM 用户 `c53-app` 并绑定后端策略。
6. 创建 RAM 角色 `c53-upload-role`，配置信任策略与角色策略。
7. 生成 AK，写入部署环境变量。
8. 按第 9 节逐项验证。
9. 记录 Bucket、Role ARN、Region 到项目运维文档，不记录 AK/SK。
