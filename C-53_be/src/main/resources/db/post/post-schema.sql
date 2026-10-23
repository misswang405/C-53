-- C-53 发布模块数据库表结构 v1.0
-- 位置：后端发布模块资源，启动时由 Spring SQL Init 执行
-- 依据：docs/需求文档/发布功能PRD.md v1.1、docs/开发文档/发布功能开发方案.md v1.0
-- 目标数据库：MySQL 5.7.8+ / MySQL 8.0，InnoDB，utf8mb4
--
-- 注意：
--   - 时间统一按 UTC 保存，应用连接设置 time_zone = '+00:00'。
--   - 业务 ID 由应用生成（pst_/med_/tpc_），前缀 + ULID。
--   - 媒体对象存阿里云 OSS，数据库只保存 bucket 与 object_key。
--   - MySQL 5.7 会解析但忽略 CHECK，应用层仍需校验枚举和长度。

SET NAMES utf8mb4;
SET time_zone = '+00:00';


-- ---------------------------------------------------------------------------
-- 1. 动态
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS posts (
    id                VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '动态 ID，如 pst_01K5...',
    author_id         VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '作者用户 ID',
    status            VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'DRAFT'
                          COMMENT 'DRAFT / SCHEDULED / PUBLISHED / DELETED',
    content           TEXT         NOT NULL
                          COMMENT '纯文本正文，保留换行；应用层限制 600 码点',
    topic_id          VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '话题 ID',
    media_type        VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT 'IMAGE / VIDEO；无媒体为 NULL',
    scheduled_at      DATETIME(3)  NULL
                          COMMENT '计划发布时间（UTC）',
    published_at      DATETIME(3)  NULL
                          COMMENT '公开时间（UTC）',
    edited_at         DATETIME(3)  NULL
                          COMMENT '最近编辑时间（UTC）',
    version           INT          NOT NULL DEFAULT 1
                          COMMENT '乐观锁版本号',
    client_request_id VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '创建幂等键，同一作者内唯一',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at        DATETIME(3)  NULL
                          COMMENT '软删除时间；正常为 NULL',
    PRIMARY KEY (id),
    UNIQUE KEY uk_posts_author_request (author_id, client_request_id),
    KEY idx_posts_author_status_updated (author_id, status, updated_at),
    KEY idx_posts_feed (status, published_at, id),
    KEY idx_posts_schedule (status, scheduled_at),
    KEY idx_posts_topic_published (topic_id, status, published_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '动态：草稿、定时、已发布、已删除';


-- ---------------------------------------------------------------------------
-- 2. 媒体资源
--    图片和视频先进入临时态；直传确认后转正；发布后绑定到动态。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS media_assets (
    id               VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT '媒体 ID，如 med_01K5...',
    owner_id         VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT '上传者用户 ID',
    media_type       VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT 'IMAGE / VIDEO；TEXT 预留',
    upload_mode      VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT 'SERVER / DIRECT',
    storage_provider VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'OSS',
    bucket           VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_general_ci NULL
                         COMMENT 'OSS Bucket',
    object_key       VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL
                         COMMENT '正式对象 Key，不对外暴露',
    temp_object_key  VARCHAR(512) CHARACTER SET ascii COLLATE ascii_bin NULL
                         COMMENT '直传临时对象 Key，转正后清空',
    mime             VARCHAR(64)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT 'image/jpeg、image/png、video/mp4',
    size_bytes       BIGINT       NOT NULL DEFAULT 0
                         COMMENT '实际字节数',
    width            INT          NULL
                         COMMENT '像素宽',
    height           INT          NULL
                         COMMENT '像素高',
    duration_ms      BIGINT       NULL
                         COMMENT '视频时长（毫秒）',
    checksum         VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
                         COMMENT 'ETag 或校验和',
    post_id          VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                         COMMENT '绑定的动态 ID',
    sort_order       INT          NOT NULL DEFAULT 0
                         COMMENT '动态内展示顺序',
    status           VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                         COMMENT 'INIT / UPLOADING / READY / BOUND / FAILED / DELETED',
    expires_at       DATETIME(3)  NULL
                         COMMENT '凭证或临时对象过期时间（UTC）',
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                         ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at       DATETIME(3)  NULL
                         COMMENT '清理时间；正常为 NULL',
    PRIMARY KEY (id),
    KEY idx_media_post_sort (post_id, sort_order),
    KEY idx_media_owner_status (owner_id, status),
    KEY idx_media_temp (status, expires_at),
    KEY idx_media_deleted (status, deleted_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '媒体资源元数据，对象存 OSS';


-- ---------------------------------------------------------------------------
-- 3. 话题（最小只读依赖）
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS topics (
    id         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                   COMMENT '话题 ID，如 tpc_01K5...',
    name       VARCHAR(32) NOT NULL
                   COMMENT '话题名，唯一',
    status     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE'
                   COMMENT 'ACTIVE / DISABLED',
    post_count BIGINT      NOT NULL DEFAULT 0
                   COMMENT '已发布动态数',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                   ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_topics_name (name),
    KEY idx_topics_status_count (status, post_count)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '话题';


-- ---------------------------------------------------------------------------
-- 4. 开发环境种子话题
--    使用固定 ID，重复执行不报错；生产环境由运营导入。
-- ---------------------------------------------------------------------------
INSERT IGNORE INTO topics (id, name, status, post_count) VALUES
    ('tpc_seed_daily',   '日常',   'ACTIVE', 0),
    ('tpc_seed_tech',    '技术',   'ACTIVE', 0),
    ('tpc_seed_design',  '设计',   'ACTIVE', 0),
    ('tpc_seed_reading', '读书',   'ACTIVE', 0),
    ('tpc_seed_city',    '城市',   'ACTIVE', 0),
    ('tpc_seed_idea',    '灵感',   'ACTIVE', 0);
