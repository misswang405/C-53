-- C-53 认证模块 v2.3 增量迁移：MySQL 5.7
-- 仅供人工评审和执行，应用不会自动执行本文件。
--
-- MySQL 5.7 会解析但忽略 CHECK，不能依赖数据库拒绝非法 scene；应用层必须接受
-- register/login/reset_password 并校验长度和枚举。5.7 不执行 DROP CHECK / ADD CHECK，
-- 只更新 scene 注释；全新环境基线中的扩展 CHECK 在 5.7 上同样不强制执行。
--
-- 不要在 MySQL 8 执行：MySQL 8 必须先 DROP 旧 chk_avc_scene，再 ADD 新约束，
-- 对应脚本为 auth-cleanup-v2.3-mysql80.sql。执行前请备份并确认当前基线版本。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

ALTER TABLE auth_verification_challenges
    MODIFY scene VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
        COMMENT 'register / login / reset_password';

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
    KEY idx_act_family (family_id)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = 'Redis 家族撤销持久化补偿任务';
