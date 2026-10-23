# C-53

C-53 是一个前后端分离的个人博客 / 内容社区项目，包含登录认证、内容发布、Feed 浏览等模块。

## 技术栈

| 端 | 技术 |
|---|---|
| 前端 | Vue 3、Vite 6，手写 CSS，无 UI 组件库 |
| 后端 | Java 21、Spring Boot 3.2.5、Spring Security、MyBatis-Plus、MySQL 8、Redis 7 |
| 文档 | Markdown：需求文档、开发文档、数据库设计 |

## 目录结构

```text
C-53
├── C-53_fe/          # Vue 3 前端
├── C-53_be/          # Spring Boot 后端
├── docs/             # 需求、接口、数据库与开发文档
├── docker-compose.yml
└── README.md
```

## 本地启动

### 1. 准备本地配置

仓库不提交含环境信息的本地配置，需要从示例文件复制：

```bash
cd C-53_be
cp maven-settings.example.xml maven-settings.xml
cp .mvn/maven.config.example .mvn/maven.config
cp src/main/resources/application.example.yml src/main/resources/application.yml
```

Windows PowerShell：

```powershell
cd C-53_be
Copy-Item maven-settings.example.xml maven-settings.xml
Copy-Item .mvn/maven.config.example .mvn/maven.config
Copy-Item src/main/resources/application.example.yml src/main/resources/application.yml
```

按本地环境修改 `application.yml`，或通过环境变量覆盖，详见下方环境变量表。

### 2. 启动 MySQL 与 Redis

```bash
docker compose up -d
```

`docker-compose.yml` 中的数据库名默认为 `c53`，而 `application.example.yml` 默认 `c_53`。使用 Docker Compose 时请设置 `DB_NAME=c53`，或修改本地 `application.yml`。

### 3. 启动后端

```bash
cd C-53_be
mvn spring-boot:run
```

默认地址：`http://localhost:8080/api/v1`

### 4. 启动前端

```bash
cd C-53_fe
npm install
npm run dev
```

默认地址：`http://localhost:5173`，开发代理会把 `/api` 转发到 `http://127.0.0.1:8080`。

## 环境变量

| 变量 | 说明 |
|---|---|
| `DB_HOST` / `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | MySQL 连接 |
| `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` / `REDIS_DATABASE` | Redis 连接 |
| `AUTH_JWT_SECRET` | JWT 签名密钥，生产必须替换 |
| `AUTH_HMAC_SECRET` | 验证码 / Refresh Token 摘要密钥，生产必须替换 |
| `AUTH_VERIFICATION_DEV_MODE` | 开发环境验证码模拟开关，生产必须关闭 |
| `AUTH_CORS_ORIGINS` | 允许的前端来源，逗号分隔 |

## 安全约定

- 不提交 `application.yml`、`maven-settings.xml`、`.mvn/maven.config`、`.env`、证书和密钥文件。
- 生产环境必须通过环境变量或密钥管理注入数据库密码、Redis 密码、JWT 与 HMAC 密钥。
- `docker-compose.yml` 中的密码仅用于本地开发，生产部署必须覆盖。
- `AUTH_VERIFICATION_DEV_MODE=true` 仅用于开发，生产必须关闭并接入真实的短信 / 邮件发送器。

## 文档

- `docs/需求文档/发布功能PRD.md`
- `docs/开发文档/登录模块开发方案.md`
- `docs/开发文档/auth-api.md`
- `docs/db/auth-database-design.md`
- `docs/后端工程目录规范.md`
