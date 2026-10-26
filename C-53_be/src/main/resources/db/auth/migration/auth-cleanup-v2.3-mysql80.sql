-- C-53 认证模块 v2.3 增量迁移：MySQL 8.0.16+
-- 仅供人工评审和执行，应用不会自动执行本文件。
--
-- 本文件针对启用 CHECK 强制校验的 MySQL 8.0.16+。不要与 MySQL 5.7 脚本混用。
-- 执行前必须确认旧约束名为 chk_avc_scene，并先备份、检查现有数据。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE auth_verification_challenges
    MODIFY scene VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'register / login / reset_password',
    DROP CHECK chk_avc_scene,
    ADD CONSTRAINT chk_avc_scene
        CHECK (scene IN ('register', 'login', 'reset_password'));

CREATE TABLE IF NOT EXISTS auth_cleanup_tasks (
    id            VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT '清理任务 ID，如 acl_01K5...',
    family_id     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT '待吊销令牌家族 ID，逻辑引用，无级联删除',
    expires_at    DATETIME(3) NOT NULL
                      COMMENT '家族原过期时间（UTC），计算吊销标记剩余 TTL',
    reason        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT '吊销原因，如 password_changed / logout / session_revoked',
    status        VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING'
                      COMMENT 'PENDING / COMPLETED；失败保留 PENDING',
    attempts      INT UNSIGNED NOT NULL DEFAULT 0
                      COMMENT '已记录失败次数，也是延期的乐观并发版本',
    next_retry_at DATETIME(3) NOT NULL
                      COMMENT '下一次可重试时间（UTC）',
    completed_at  DATETIME(3) NULL
                      COMMENT 'Redis 清理成功后的时间（UTC）',
    created_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                      ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_act_pending_retry (status, next_retry_at, id),
    KEY idx_act_family (family_id),
    CONSTRAINT chk_act_status
        CHECK (status IN ('PENDING', 'COMPLETED')),
    CONSTRAINT chk_act_completed
        CHECK ((status = 'PENDING' AND completed_at IS NULL)
            OR (status = 'COMPLETED' AND completed_at IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = 'Redis 家族撤销持久化补偿任务';
