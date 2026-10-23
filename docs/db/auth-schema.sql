-- C-53 登录注册模块数据库表结构
-- 依据：docs/auth-api.md v1.1
-- 目标数据库：MySQL 5.7.8+ / MySQL 8.0，InnoDB，utf8mb4
--
-- 使用说明：
--   1. 先选择目标数据库，或取消下面两行的注释后执行。
--   2. 所有 DATETIME 均按 UTC 保存，应用连接必须设置 time_zone = '+00:00'。
--   3. 业务 ID 由应用生成（如 usr_ / ses_ / ver_ / acc_ 前缀），数据库不负责生成。
--   4. 验证码、Refresh Token、设备 Cookie 只保存不可逆摘要，不保存明文。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

-- CREATE DATABASE IF NOT EXISTS c53
--   CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
-- USE c53;


-- ---------------------------------------------------------------------------
-- 1. 用户账号
--    对应 AuthUser；邮箱、手机号未绑定时为 NULL。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                  VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT '用户 ID，如 usr_01K5...',
    nickname            VARCHAR(20)  NOT NULL
                            COMMENT '昵称，1-20 个字符',
    username            VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL
                            COMMENT '唯一用户名，保存为小写，不含 @',
    password_hash       VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT 'Argon2id 或 bcrypt 加盐哈希，禁止明文',
    avatar_url          VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
                            COMMENT '头像地址',
    initials            VARCHAR(20)  NOT NULL
                            COMMENT '无头像时展示的字符',
    profession          VARCHAR(100) NULL
                            COMMENT '职业或领域',
    city                VARCHAR(100) NULL
                            COMMENT '所在城市',
    bio                 VARCHAR(500) NULL
                            COMMENT '个人简介',
    email               VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL
                            COMMENT '已绑定邮箱，统一转小写；未绑定为 NULL',
    email_verified      TINYINT(1)   NOT NULL DEFAULT 0
                            COMMENT '邮箱是否已验证',
    phone               VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                            COMMENT '已绑定手机号，E.164 格式；未绑定为 NULL',
    phone_verified      TINYINT(1)   NOT NULL DEFAULT 0
                            COMMENT '手机号是否已验证',
    status              VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE'
                            COMMENT 'ACTIVE / DISABLED / DELETED',
    disabled_reason     VARCHAR(255) NULL
                            COMMENT '停用原因',
    last_login_at       DATETIME(3)  NULL
                            COMMENT '最近一次登录成功时间（UTC）',
    password_changed_at DATETIME(3)  NULL
                            COMMENT '最近一次修改密码时间（UTC）',
    deleted_at          DATETIME(3)  NULL
                            COMMENT '软删除时间；正常用户为 NULL',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    UNIQUE KEY uk_users_email (email),
    UNIQUE KEY uk_users_phone (phone),
    KEY idx_users_status_created (status, created_at),
    KEY idx_users_deleted_at (deleted_at),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    CONSTRAINT chk_users_nickname_length
        CHECK (CHAR_LENGTH(nickname) BETWEEN 1 AND 20),
    CONSTRAINT chk_users_username_length
        CHECK (CHAR_LENGTH(username) BETWEEN 3 AND 24),
    CONSTRAINT chk_users_email_verified
        CHECK (email_verified IN (0, 1) AND (email_verified = 0 OR email IS NOT NULL)),
    CONSTRAINT chk_users_phone_verified
        CHECK (phone_verified IN (0, 1) AND (phone_verified = 0 OR phone IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '用户账号与公开资料';


-- ---------------------------------------------------------------------------
-- 2. 设备会话
--    对应 HttpOnly 设备 Cookie（c53_device_session）。
--    数据库只保存 Cookie 值的 HMAC-SHA256，不保存 Cookie 明文。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_device_sessions (
    id                  VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT '设备会话 ID',
    token_hash          CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT '设备 Cookie 值的 HMAC-SHA256（64 位十六进制）',
    device_fingerprint  VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL
                            COMMENT '可选的设备指纹摘要',
    created_ip_address  VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                            COMMENT '首次创建时的 IP，兼容 IPv6',
    last_ip_address     VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                            COMMENT '最近使用时的 IP',
    user_agent          VARCHAR(512) NULL
                            COMMENT '最近一次 User-Agent',
    last_seen_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            COMMENT '最近活跃时间（UTC）',
    expires_at          DATETIME(3)  NOT NULL
                            COMMENT '设备会话过期时间（UTC）',
    revoked_at          DATETIME(3)  NULL
                            COMMENT '撤销时间；有效会话为 NULL',
    revoked_reason      VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                            COMMENT '撤销原因，如 user_revoked / risk_control / expired',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ads_token_hash (token_hash),
    KEY idx_ads_active (revoked_at, expires_at),
    KEY idx_ads_last_seen (last_seen_at)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '设备会话（已保存账号的设备授权）';


-- ---------------------------------------------------------------------------
-- 3. 登录会话
--    对应 AuthSession.sessionId 与 /me 返回的 session 信息。
--    Access Token 使用 JWT，不落库；每次请求校验 JWT 中的 sid 是否对应有效会话。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_sessions (
    id                 VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '会话 ID，如 ses_01K5...',
    user_id            VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '所属用户',
    device_session_id  VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '所属设备会话；服务端任务创建时可为 NULL',
    login_method       VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT 'password / sms_code / email_code / register / switch',
    remember_me        TINYINT(1)   NOT NULL DEFAULT 0
                           COMMENT '是否勾选保持登录',
    ip_address         VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '登录 IP',
    user_agent         VARCHAR(512) NULL
                           COMMENT '登录 User-Agent',
    last_active_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           COMMENT '最近活跃时间（UTC），/me 返回 lastActiveAt',
    expires_at         DATETIME(3)  NOT NULL
                           COMMENT '会话及当前 Refresh Token 家族的过期时间（UTC）',
    revoked_at         DATETIME(3)  NULL
                           COMMENT '撤销时间；有效会话为 NULL',
    revoked_reason     VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT 'logout / refresh_reuse / switch_replaced / password_changed / account_disabled',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           COMMENT '会话创建时间（UTC），/me 返回 createdAt',
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_sessions_user (user_id, revoked_at, expires_at),
    KEY idx_sessions_device_user (device_session_id, user_id, revoked_at, last_active_at),
    KEY idx_sessions_expires (expires_at),
    CONSTRAINT fk_sessions_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_sessions_device
        FOREIGN KEY (device_session_id) REFERENCES auth_device_sessions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_sessions_method
        CHECK (login_method IN ('password', 'sms_code', 'email_code', 'register', 'switch')),
    CONSTRAINT chk_sessions_remember
        CHECK (remember_me IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '登录会话';


-- ---------------------------------------------------------------------------
-- 4. Refresh Token
--    只保存令牌摘要。每次刷新轮换令牌；旧令牌 used_at 非空即视为重复使用。
--    family_id 用于重复使用检测后一次性撤销整个令牌家族。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id                   VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'Refresh Token 记录 ID',
    session_id           VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '所属登录会话',
    family_id            VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '令牌家族 ID；同一会话轮换链路共用',
    token_hash           CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'Refresh Token 值的 HMAC-SHA256',
    parent_token_id      VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '轮换前的令牌 ID（逻辑外键）',
    replaced_by_token_id VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '轮换后的令牌 ID（逻辑外键）',
    issued_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             COMMENT '签发时间（UTC）',
    expires_at           DATETIME(3)  NOT NULL
                             COMMENT '过期时间（UTC）',
    used_at              DATETIME(3)  NULL
                             COMMENT '被用于刷新或已轮换的时间；非空表示不可再次使用',
    revoked_at           DATETIME(3)  NULL
                             COMMENT '撤销时间',
    revoked_reason       VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT 'rotated / logout / reuse_detected / session_revoked',
    created_ip_address   VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '签发时 IP',
    PRIMARY KEY (id),
    UNIQUE KEY uk_art_token_hash (token_hash),
    KEY idx_art_session (session_id, revoked_at, expires_at),
    KEY idx_art_family (family_id, revoked_at),
    KEY idx_art_parent (parent_token_id),
    KEY idx_art_replaced (replaced_by_token_id),
    KEY idx_art_expires (expires_at),
    CONSTRAINT fk_art_session
        FOREIGN KEY (session_id) REFERENCES auth_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = 'Refresh Token 轮换记录';


-- ---------------------------------------------------------------------------
-- 5. 已保存账号
--    对应 SavedAccount.accountId。一个设备会话可保存多个账号；
--    不保存密码、密码摘要或 Refresh Token。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_device_accounts (
    id                VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '设备账号记录 ID，如 acc_01K5...',
    device_session_id VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '所属设备会话',
    user_id           VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '已保存的用户',
    authorized_at     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          COMMENT '该设备首次获得账号授权的时间（UTC）',
    last_used_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          COMMENT '最近使用时间（UTC），对应 lastUsedAt',
    reauth_required   TINYINT(1)   NOT NULL DEFAULT 0
                          COMMENT '切换时是否需要重新验证密码',
    reauth_reason     VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT 'reauth 原因，如 new_device / long_unused / risk',
    revoked_at        DATETIME(3)  NULL
                          COMMENT '设备账号授权撤销时间；有效记录为 NULL',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ada_device_user (device_session_id, user_id),
    KEY idx_ada_user_last_used (user_id, last_used_at),
    KEY idx_ada_device_list (device_session_id, revoked_at, last_used_at),
    CONSTRAINT fk_ada_device
        FOREIGN KEY (device_session_id) REFERENCES auth_device_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_ada_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_ada_reauth
        CHECK (reauth_required IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '设备已保存账号';


-- ---------------------------------------------------------------------------
-- 6. 验证码挑战
--    短信/邮箱验证码共用。只保存验证码摘要；verificationId 即主键 id。
--    新验证码发送成功后，同一 target + scene + channel 的旧挑战置为 INVALIDATED。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_verification_challenges (
    id                   VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '验证码挑战 ID，如 ver_01K5...',
    scene                VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'register / login',
    channel              VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'sms / email',
    target               VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL
                             COMMENT '规范化目标：邮箱小写或 E.164 手机号',
    code_hash            CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '验证码的 HMAC-SHA256（64 位十六进制），禁止明文',
    status               VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING'
                             COMMENT 'PENDING / ACTIVE / CONSUMED / INVALIDATED / FAILED',
    attempts             SMALLINT UNSIGNED NOT NULL DEFAULT 0
                             COMMENT '已校验次数',
    max_attempts         TINYINT UNSIGNED  NOT NULL DEFAULT 5
                             COMMENT '最大校验次数，默认 5',
    resend_after_seconds INT UNSIGNED      NOT NULL DEFAULT 60
                             COMMENT '重发间隔秒数，对应 resendAfter',
    request_ip_address   VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '请求发送验证码时的 IP',
    device_session_id    VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '发起请求的设备会话（逻辑关联）',
    user_agent           VARCHAR(512) NULL
                             COMMENT '请求 User-Agent',
    sent_at              DATETIME(3)  NULL
                             COMMENT '发送成功时间；PENDING/FAILED 时为 NULL',
    expires_at           DATETIME(3)  NOT NULL
                             COMMENT '验证码过期时间（UTC）',
    last_attempt_at      DATETIME(3)  NULL
                             COMMENT '最近一次校验时间（UTC）',
    consumed_at          DATETIME(3)  NULL
                             COMMENT '成功消费时间；只能消费一次',
    invalidated_at       DATETIME(3)  NULL
                             COMMENT '因重发新验证码而失效的时间',
    created_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_avc_target_scene (target, scene, channel, created_at),
    KEY idx_avc_status_expires (status, expires_at),
    KEY idx_avc_ip_created (request_ip_address, created_at),
    KEY idx_avc_device_created (device_session_id, created_at),
    CONSTRAINT chk_avc_scene
        CHECK (scene IN ('register', 'login')),
    CONSTRAINT chk_avc_channel
        CHECK (channel IN ('sms', 'email')),
    CONSTRAINT chk_avc_status
        CHECK (status IN ('PENDING', 'ACTIVE', 'CONSUMED', 'INVALIDATED', 'FAILED')),
    CONSTRAINT chk_avc_attempts
        CHECK (attempts <= max_attempts)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '短信/邮箱验证码挑战';


-- ---------------------------------------------------------------------------
-- 7. 登录尝试审计
--    用于账号/IP/设备维度的限流与安全审计。
--    account_hash 为规范化账号的 HMAC-SHA256，避免明文记录账号。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_login_attempts (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id           VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '已匹配到的用户；未匹配时为 NULL',
    account_type      VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT 'username / email / phone',
    account_hash      CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '规范化账号的 HMAC-SHA256',
    ip_address        VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '请求 IP，兼容 IPv6',
    device_session_id VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '设备会话（逻辑关联）',
    login_method      VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT 'password / sms_code / email_code / switch',
    success           TINYINT(1)   NOT NULL
                          COMMENT '1 成功，0 失败',
    failure_code      VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '失败业务码，如 INVALID_CREDENTIALS',
    user_agent        VARCHAR(512) NULL,
    attempted_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          COMMENT '尝试时间（UTC）',
    PRIMARY KEY (id),
    KEY idx_ala_account_time (account_hash, attempted_at),
    KEY idx_ala_ip_time (ip_address, attempted_at),
    KEY idx_ala_user_time (user_id, attempted_at),
    KEY idx_ala_attempted (attempted_at),
    CONSTRAINT fk_ala_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_ala_success
        CHECK (success IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '登录尝试与限流审计';


-- ---------------------------------------------------------------------------
-- 8. 协议同意记录
--    注册请求只有一个 agreed 字段，服务端按当前协议版本写入多条记录。
--    例如 community_guidelines 与 privacy_policy 各写一条。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user_agreements (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                       COMMENT '同意的用户',
    agreement_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                       COMMENT 'community_guidelines / privacy_policy / terms_of_service',
    version        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                       COMMENT '协议版本号',
    agreed_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                       COMMENT '同意时间（UTC）',
    ip_address     VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL
                       COMMENT '同意时 IP',
    user_agent     VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_aua_user_type_version (user_id, agreement_type, version),
    KEY idx_aua_user_time (user_id, agreed_at),
    CONSTRAINT fk_aua_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_aua_type
        CHECK (agreement_type IN ('community_guidelines', 'privacy_policy', 'terms_of_service'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '用户协议同意记录';


