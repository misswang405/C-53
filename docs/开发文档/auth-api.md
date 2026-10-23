# C—53 登录注册模块接口文档

> 文档版本：1.2  
> 更新日期：2026-10-22  
> 对应前端：登录页、注册页、设置页账号切换与退出登录  
> 说明：本文仅定义接口契约，不包含后端实现代码。

## 1. 模块范围

本模块包含：

- 用户名、邮箱或手机号密码登录
- 手机号短信验证码登录
- 邮箱验证码登录
- 使用短信或邮箱验证码完成用户注册
- Access Token 刷新
- 当前登录状态恢复
- 退出登录
- 已保存账号列表
- 同一设备快速切换账号
- 用户名可用性检查

游客不需要登录即可浏览首页和公开文章。发布、评论、点赞、收藏、关注、私信、个人资料和设置等写操作必须登录。

## 2. 基础约定

### 2.1 接口地址

前端通过环境变量 `VITE_API_BASE_URL` 配置接口根地址。

```text
http://localhost:8080/api/v1
```

未配置时，前端默认请求同源路径：

```text
/api/v1
```

正式接口为 `/api/v1/auth/*` 与 `/api/v1/me`。当前后端同时保留 `/auth/*`、`/me`、`/auth/me` 过渡别名，别名与正式路径共用同一业务逻辑。

### 2.2 数据格式

- 请求与响应使用 UTF-8 编码。
- 普通请求使用 `Content-Type: application/json; charset=utf-8`。
- 时间使用 ISO 8601 UTC，例如 `2026-09-24T06:30:00Z`。
- 所有业务 ID 均为字符串。

### 2.3 认证请求头

需要登录的接口携带：

```http
Authorization: Bearer <accessToken>
```

### 2.4 通用成功响应

```json
{
  "code": "OK",
  "data": {},
  "requestId": "req_01K5..."
}
```

### 2.5 通用错误响应

```json
{
  "code": "VALIDATION_ERROR",
  "message": "用户名格式不正确",
  "details": [
    {
      "field": "username",
      "reason": "invalid_format"
    }
  ],
  "requestId": "req_01K5..."
}
```

## 3. 数据模型

### 3.1 AuthUser

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `id` | string | 是 | 用户 ID |
| `nickname` | string | 是 | 昵称 |
| `username` | string | 是 | 唯一用户名，不包含 `@` |
| `avatarUrl` | string/null | 是 | 头像地址 |
| `initials` | string | 是 | 无头像时展示的字符 |
| `profession` | string/null | 否 | 职业或领域 |
| `city` | string/null | 否 | 所在城市 |
| `bio` | string/null | 否 | 个人简介 |
| `email` | string/null | 否 | 已绑定邮箱；未绑定时为 `null` |
| `emailVerified` | boolean | 是 | 邮箱是否已验证 |
| `phone` | string/null | 否 | 已绑定手机号；未绑定时为 `null` |
| `phoneVerified` | boolean | 是 | 手机号是否已验证 |

### 3.2 AuthSession

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `accessToken` | string | 是 | 接口访问令牌 |
| `refreshToken` | string | 是 | 刷新令牌 |
| `tokenType` | string | 是 | 固定为 `Bearer` |
| `expiresIn` | integer | 是 | Access Token 有效秒数 |
| `refreshExpiresIn` | integer | 是 | Refresh Token 有效秒数 |
| `sessionId` | string | 是 | 当前会话 ID |
| `user` | AuthUser | 是 | 当前登录用户 |

### 3.3 SavedAccount

已保存账号只返回展示信息，严禁返回密码、密码摘要或 Refresh Token。

| 字段 | 类型 | 必填 | 说明 |
|---|---|---:|---|
| `accountId` | string | 是 | 设备账号记录 ID |
| `user` | AuthUser | 是 | 用户摘要 |
| `lastUsedAt` | datetime | 是 | 最近使用时间 |
| `current` | boolean | 是 | 是否为当前账号 |
| `reauthRequired` | boolean | 是 | 切换时是否需要重新验证密码 |

## 4. 接口总览

| 方法 | 路径 | 认证 | 用途 |
|---|---|---:|---|
| POST | `/auth/verification-codes` | 否 | 发送短信或邮箱验证码 |
| POST | `/auth/register` | 否 | 创建账号并建立会话 |
| GET | `/auth/username-availability` | 否 | 检查用户名是否可用 |
| POST | `/auth/login` | 否 | 账号密码登录 |
| POST | `/auth/login/code` | 否 | 手机号或邮箱验证码登录 |
| POST | `/auth/refresh` | Refresh Token | 刷新访问令牌 |
| POST | `/auth/logout` | 是 | 退出当前会话 |
| GET | `/auth/accounts` | 设备会话 | 获取本设备已保存账号 |
| POST | `/auth/switch` | 设备会话 | 快速切换已保存账号 |
| GET | `/me` | 是 | 恢复当前登录状态 |

### 4.1 发送验证码

`POST /auth/verification-codes`

短信和邮箱验证码共用此接口，通过 `channel` 区分发送渠道，通过 `scene` 限定验证码用途。

请求体：

```json
{
  "scene": "register",
  "channel": "sms",
  "target": "+8613812345678"
}
```

邮箱验证码示例：

```json
{
  "scene": "login",
  "channel": "email",
  "target": "linche@example.com"
}
```

| 字段 | 必填 | 规则 |
|---|---:|---|
| `scene` | 是 | `register` 或 `login`；验证码只能用于指定场景 |
| `channel` | 是 | `sms` 或 `email` |
| `target` | 是 | `sms` 时使用 E.164 手机号；`email` 时使用合法邮箱地址 |

成功响应：HTTP `202 Accepted`

```json
{
  "code": "OK",
  "data": {
    "verificationId": "ver_01K5...",
    "channel": "sms",
    "maskedTarget": "+86 138****5678",
    "expiresIn": 300,
    "resendAfter": 60
  },
  "requestId": "req_01K5..."
}
```

业务要求：

- `verificationId` 是本次验证码挑战的唯一标识，提交注册或验证码登录时必须原样携带。
- 验证码建议为 6 位数字，有效期 5 分钟，只能成功使用一次。
- 同一验证码最多允许校验 5 次；超过次数后立即失效。
- 新验证码发送成功后，同一目标、场景下的旧验证码应失效。
- `login` 场景无论账号是否存在，接口均返回相同结构，避免泄露账号注册状态。
- 发送结果只返回脱敏目标，响应和服务日志中不得出现验证码明文。

## 5. 注册

### 5.1 创建账号

`POST /auth/register`

请求体：

```json
{
  "nickname": "林澈",
  "username": "linche",
  "contact": "linche@example.com",
  "contactType": "email",
  "verificationId": "ver_01K5...",
  "verificationCode": "482913",
  "password": "example-password",
  "agreed": true
}
```

字段规则：

| 字段 | 必填 | 规则 |
|---|---:|---|
| `nickname` | 是 | 1～20 个字符，去除首尾空格 |
| `username` | 是 | 3～24 位，仅允许小写字母、数字、下划线和短横线 |
| `contact` | 是 | 合法邮箱或 E.164 手机号；必须与验证码发送目标一致 |
| `contactType` | 是 | `email` 或 `phone`；必须与验证码渠道对应 |
| `verificationId` | 是 | 调用验证码发送接口获得，且场景必须为 `register` |
| `verificationCode` | 是 | 用户收到的 6 位短信或邮箱验证码 |
| `password` | 是 | 最少 8 位，建议同时包含字母和数字；最大 72 字节 |
| `agreed` | 是 | 必须为 `true` |

成功响应：HTTP `201 Created`

```json
{
  "code": "OK",
  "data": {
    "accessToken": "eyJ...",
    "refreshToken": "rt_v1_...",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshExpiresIn": 604800,
    "sessionId": "ses_01K5...",
    "user": {
      "id": "usr_01K5...",
      "nickname": "林澈",
      "username": "linche",
      "avatarUrl": null,
      "initials": "林",
      "profession": null,
      "city": null,
      "bio": null,
      "email": "linche@example.com",
      "emailVerified": true,
      "phone": null,
      "phoneVerified": false
    }
  },
  "requestId": "req_01K5..."
}
```

业务要求：

- `username`、邮箱和手机号均需唯一。
- 用户名比较应忽略大小写，保存时统一转为小写。
- 密码必须使用 Argon2id、bcrypt 等安全算法加盐哈希，禁止明文保存。
- 注册成功后直接创建登录会话，无需前端再次调用登录接口。
- 必须记录用户同意的协议版本和同意时间。
- 注册必须校验短信或邮箱验证码；验证成功后将对应的 `phoneVerified` 或 `emailVerified` 标记为 `true`。
- `verificationId`、验证码、联系方式、渠道和 `register` 场景必须完全匹配，验证成功后原子化消费验证码。

### 5.2 检查用户名

`GET /auth/username-availability?username=linche`

响应：

```json
{
  "code": "OK",
  "data": {
    "username": "linche",
    "available": true
  }
}
```

该接口只用于改善输入体验，注册接口仍必须再次执行唯一性校验。

## 6. 登录

### 6.1 账号密码登录

`POST /auth/login`

请求体：

```json
{
  "account": "linche@example.com",
  "password": "example-password",
  "remember": true
}
```

| 字段 | 必填 | 说明 |
|---|---:|---|
| `account` | 是 | 用户名、邮箱或手机号 |
| `password` | 是 | 用户密码 |
| `remember` | 否 | 默认 `false`；决定 Refresh Token 生命周期 |

成功响应与注册接口的 `AuthSession` 相同，HTTP `200 OK`。

登录失败统一返回：

```json
{
  "code": "INVALID_CREDENTIALS",
  "message": "账号或密码错误",
  "requestId": "req_01K5..."
}
```

不得通过错误文案区分“账号不存在”和“密码错误”，避免账号枚举攻击。

### 6.2 手机号或邮箱验证码登录

`POST /auth/login/code`

请求验证码时将 `scene` 设为 `login`，发送完成后提交：

```json
{
  "account": "+8613812345678",
  "channel": "sms",
  "verificationId": "ver_01K5...",
  "verificationCode": "482913",
  "remember": true
}
```

邮箱登录只需将 `channel` 改为 `email`，并将 `account` 设置为邮箱地址。

| 字段 | 必填 | 说明 |
|---|---:|---|
| `account` | 是 | 已绑定的手机号或邮箱，必须与验证码发送目标一致 |
| `channel` | 是 | `sms` 或 `email` |
| `verificationId` | 是 | 验证码挑战 ID，场景必须为 `login` |
| `verificationCode` | 是 | 用户收到的 6 位验证码 |
| `remember` | 否 | 默认 `false`；决定 Refresh Token 生命周期 |

成功响应与账号密码登录相同，返回 `AuthSession`，HTTP `200 OK`。验证码只能登录已有账号，不自动创建新账号。

## 7. 刷新令牌

`POST /auth/refresh`

请求体：

```json
{
  "refreshToken": "rt_v1_..."
}
```

响应：

```json
{
  "code": "OK",
  "data": {
    "accessToken": "eyJ...new",
    "refreshToken": "rt_v1_...new",
    "tokenType": "Bearer",
    "expiresIn": 900,
    "refreshExpiresIn": 604800,
    "sessionId": "ses_01K5..."
  }
}
```

要求：

- Refresh Token 使用 `rt_v1_<随机值>` 格式；服务端只保存 HMAC-SHA256 摘要。
- Redis 白名单是 Refresh Token 有效性的唯一权威源，刷新校验不查询 MySQL Token 审计表。
- Refresh Token 必须轮换；新令牌签发后旧令牌立即失效。
- 检测到旧令牌重复使用时，应撤销整个令牌族并要求重新登录。
- 同一旧令牌并发刷新时，5 秒宽限期内后到请求返回 `409 REFRESH_IN_PROGRESS`；宽限期后再次使用旧令牌按重放处理。
- 前端只在收到 `401 TOKEN_EXPIRED` 时刷新一次。收到 `401 INVALID_REFRESH_TOKEN` 后清空本地会话并回到登录页。
- Redis 不可用、连接超时或写入失败时返回 `503 SERVICE_UNAVAILABLE`；前端必须保留本地 Access/Refresh Token，提示用户稍后重试，不得因此清空会话或强制退出。

## 8. 恢复当前会话

`GET /me`

请求头：

```http
Authorization: Bearer <accessToken>
```

响应：

```json
{
  "code": "OK",
  "data": {
    "user": {
      "id": "usr_01K5...",
      "nickname": "林澈",
      "username": "linche",
      "avatarUrl": null,
      "initials": "林"
    },
    "session": {
      "id": "ses_01K5...",
      "createdAt": "2026-09-24T06:30:00Z",
      "lastActiveAt": "2026-09-24T07:10:00Z"
    },
    "unread": {
      "notifications": 0,
      "messages": 0
    }
  }
}
```

前端启动时若本地存在 Access Token，应调用该接口确认会话有效，不能仅根据本地布尔值判断已登录。

## 9. 退出登录

`POST /auth/logout`

请求头：

```http
Authorization: Bearer <accessToken>
```

成功响应：HTTP `204 No Content`。

后端应撤销当前 `sessionId` 对应的 Access Token 和 Refresh Token。无论接口是否成功，前端都应清除本机令牌并回到公开首页。

## 10. 已保存账号与账号切换

### 10.1 获取本设备账号

`GET /auth/accounts`

该接口依赖安全的设备会话 Cookie，例如：

```http
Cookie: c53_device_session=<opaque-value>
```

响应：

```json
{
  "code": "OK",
  "data": {
    "items": [
      {
        "accountId": "acc_01K5...",
        "user": {
          "id": "usr_01K5...",
          "nickname": "林澈",
          "username": "linche",
          "avatarUrl": null,
          "initials": "林"
        },
        "lastUsedAt": "2026-09-24T07:10:00Z",
        "current": true,
        "reauthRequired": false
      }
    ]
  }
}
```

无已保存账号时返回空数组，不允许后端返回演示账号。

### 10.2 切换账号

`POST /auth/switch`

请求体：

```json
{
  "accountId": "acc_01K5..."
}
```

成功响应为新的 `AuthSession`。

若 `reauthRequired` 为 `true`，返回：

```json
{
  "code": "REAUTH_REQUIRED",
  "message": "请重新验证该账号",
  "details": [
    {
      "accountId": "acc_01K5..."
    }
  ]
}
```

安全要求：

- 设备会话 Cookie 必须设置 `HttpOnly`、`Secure`、`SameSite=Lax` 或更严格策略。
- 已保存账号功能只保存可撤销的设备授权，不保存密码。
- 新设备、长期未使用设备或风险设备必须重新验证密码。
- 用户应能在安全设置中查看并撤销其他设备会话。

## 11. 错误码

| HTTP 状态 | 业务码 | 场景 |
|---:|---|---|
| 400 | `VALIDATION_ERROR` | 请求字段缺失或格式错误 |
| 400 | `AGREEMENT_REQUIRED` | 未同意社区公约或隐私政策 |
| 400 | `VERIFICATION_TARGET_MISMATCH` | 验证码对应目标、渠道或场景不一致 |
| 401 | `INVALID_CREDENTIALS` | 账号或密码错误 |
| 401 | `INVALID_VERIFICATION_CODE` | 验证码错误或已经使用 |
| 401 | `VERIFICATION_CODE_EXPIRED` | 验证码已过期 |
| 401 | `VERIFICATION_ATTEMPTS_EXCEEDED` | 验证码错误次数超过限制 |
| 401 | `TOKEN_EXPIRED` | Access Token 已过期 |
| 401 | `INVALID_REFRESH_TOKEN` | Refresh Token 无效或已撤销 |
| 401 | `REAUTH_REQUIRED` | 切换账号前需要重新验证 |
| 403 | `ACCOUNT_DISABLED` | 账号被停用 |
| 409 | `USERNAME_TAKEN` | 用户名已被使用 |
| 409 | `CONTACT_TAKEN` | 邮箱或手机号已注册 |
| 409 | `REFRESH_IN_PROGRESS` | 同一 Refresh Token 的并发刷新正在处理中，短时间后重试 |
| 429 | `VERIFICATION_RESEND_TOO_SOON` | 验证码发送间隔未结束 |
| 429 | `RATE_LIMITED` | 请求过于频繁 |
| 502 | `VERIFICATION_SEND_FAILED` | 短信或邮件服务暂时不可用 |
| 503 | `SERVICE_UNAVAILABLE` | Redis 等认证依赖暂时不可用；刷新令牌时不得清除本地会话 |
| 500 | `INTERNAL_ERROR` | 服务内部异常 |

## 12. 限流建议

| 接口 | 建议限制 |
|---|---|
| 登录 | 5 次/分钟/IP，20 次/小时/账号 |
| 注册 | 3 次/小时/IP，5 次/天/设备 |
| 发送短信验证码 | 1 次/60 秒/手机号，5 次/小时/手机号，10 次/天/手机号 |
| 发送邮箱验证码 | 1 次/60 秒/邮箱，10 次/小时/邮箱，20 次/天/邮箱 |
| 校验验证码 | 5 次/验证码挑战；失败后递增延迟 |
| 用户名检查 | 30 次/分钟/IP |
| 刷新令牌 | 20 次/小时/会话 |
| 账号切换 | 10 次/分钟/设备 |

连续登录失败后应采用递增延迟、验证码或临时锁定，不能无限尝试。

验证码还应按 IP、设备指纹和目标地址组合限流。触发高风险规则时可以要求图形验证码，但不得向前端暴露目标账号是否存在。

### 12.1 验证码安全要求

- 验证码应使用密码学安全随机数生成，不得使用可预测的递增值或时间戳。
- 服务端只保存验证码摘要，并绑定 `verificationId`、目标、渠道、场景、有效期和尝试次数。
- 禁止在应用日志、链路追踪、监控事件或第三方回调日志中记录验证码明文。
- 短信和邮件模板不得包含密码、Refresh Token 或其他长期凭证。
- 邮箱地址统一转为规范化小写；手机号统一转为 E.164 格式后再匹配。
- 发送服务超时或失败时不得创建可用的验证码挑战。

## 13. 前端状态规则

### 13.1 游客

- 可以访问首页、发现页、公开文章详情和公开用户主页。
- 顶部显示“立即登录”。
- 登录与注册页面为独立页面，不显示社区左侧目录和顶部导航。
- 访问私信、收藏、发布、评论、关注、个人主页或设置时跳转登录页。

### 13.2 已登录用户

- Access Token 有效时加载个人资料、通知、收藏、关注关系、私信和设置。
- Access Token 无效且刷新返回 `401/403` 时转为游客状态；刷新返回 `503 SERVICE_UNAVAILABLE` 时保留本地凭据并允许重试。
- 退出登录后清空令牌、个人资料、私信、通知、收藏和其他私有缓存。

### 13.3 令牌保存

- 勾选“保持登录”：会话可以跨浏览器重启保留。
- 未勾选“保持登录”：会话只保留到当前浏览器会话结束。
- 生产环境优先使用 `HttpOnly` Refresh Token Cookie；Access Token 仅保存在内存中。
- 禁止把密码或完整用户敏感资料写入浏览器存储。

## 14. 联调验收清单

- 用户名、邮箱和手机号均可使用密码登录。
- 已绑定手机号可以获取短信验证码并完成登录。
- 已绑定邮箱可以获取邮箱验证码并完成登录。
- 手机注册必须先完成短信验证码校验，邮箱注册必须先完成邮箱验证码校验。
- 注册验证码不能用于登录，登录验证码不能用于注册。
- 验证码过期、错误、重复使用、目标不匹配和重发过快均返回约定错误码。
- 错误账号与错误密码返回统一提示。
- 注册用户名或联系方式重复时返回明确字段错误。
- 注册成功后直接进入登录状态。
- 刷新令牌轮换后旧令牌不可再次使用。
- 旧 Refresh Token 重放时整个令牌族被撤销，后续刷新返回 `401 INVALID_REFRESH_TOKEN`。
- 并发刷新同一 Refresh Token 时只有一个请求成功，其余请求不能签发第二个有效令牌。
- Redis 重启后能从 AOF 恢复未过期白名单；Redis 不可用时刷新返回 `503 SERVICE_UNAVAILABLE`。
- 收到刷新 `503` 后前端保留本地凭据，Redis 恢复后可以继续刷新。
- 页面刷新后可以通过 `/me` 恢复会话。
- 退出后所有受保护接口返回 `401`。
- 游客仍可正常浏览公开文章。
- 登录、注册页不显示社区目录和顶部导航。
- 已保存账号为空时前端不展示任何演示账号。
- 快速切换账号不会读取或传输明文密码。
- 所有中文消息使用 UTF-8 返回。
