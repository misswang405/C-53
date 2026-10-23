-- C-53 认证模块数据库表结构 v2.2
-- 位置：后端认证模块资源，启动时由 Spring SQL Init 执行
-- 依据：docs/auth-api.md v1.2、docs/db/auth-database-design.md
-- 目标数据库：MySQL 5.7.8+ / MySQL 8.0，InnoDB，utf8mb4
--
-- v2 主要调整：
--   1. 邮箱/手机号从 users 拆分到 user_contacts，支持多联系方式和后续换绑。
--   2. 设备与设备会话分层，已保存账号绑定到设备而不是单次 Cookie 会话。
--   3. 增加 auth_token_families，Refresh Token 家族可独立撤销和审计。
--   4. 验证码挑战增加 user_id/device_id/provider/provider_message_id/hmac_key_version。
--   5. 登录审计增加 device_id/session_id/request_id。
--   6. HMAC 摘要字段统一带密钥版本，便于后续轮换摘要密钥。
--   7. 同一设备下同一用户最多一个未撤销会话，由唯一索引兜底。
--   8. 审计表不再级联依赖业务表，避免清理会话时丢失审计链路。
--   9. Refresh Token 白名单由 Redis 权威维护，数据库仅保留家族和签发审计。
--
-- 注意：
--   - 时间统一按 UTC 保存，应用连接设置 time_zone = '+00:00'。
--   - 业务 ID 由应用生成（usr_/ct_/dev_/ds_/acc_/ses_/fam_/rt_/ver_）。
--   - 验证码、Refresh Token、设备 Cookie 只保存 HMAC-SHA256 摘要。
--   - Refresh Token 是否有效以 Redis 白名单为准，auth_refresh_tokens 不参与验权。
--   - MySQL 5.7 会解析但忽略 CHECK，应用层仍需校验枚举和长度。

SET NAMES utf8mb4;
SET time_zone = '+00:00';


-- ---------------------------------------------------------------------------
-- 1. 用户身份与公开资料
--    密码只保存哈希；邮箱/手机号在 user_contacts 中维护。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS users (
    id                  VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT '用户 ID，如 usr_01K5...',
    username            VARCHAR(24)  CHARACTER SET ascii COLLATE ascii_general_ci NOT NULL
                            COMMENT '唯一用户名，保存为小写，不含 @',
    nickname            VARCHAR(20)  NOT NULL
                            COMMENT '昵称，1-20 个字符',
    initials            VARCHAR(20)  NOT NULL
                            COMMENT '无头像时展示的字符',
    password_hash       VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                            COMMENT 'Argon2id 或 BCrypt 哈希，禁止明文',
    password_algorithm  VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'argon2id'
                            COMMENT 'argon2id / bcrypt',
    avatar_url          VARCHAR(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NULL
                            COMMENT '头像地址',
    profession          VARCHAR(100) NULL
                            COMMENT '职业或领域',
    city                VARCHAR(100) NULL
                            COMMENT '所在城市',
    bio                 VARCHAR(500) NULL
                            COMMENT '个人简介',
    status              VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE'
                            COMMENT 'ACTIVE / DISABLED / DELETED',
    disabled_reason     VARCHAR(255) NULL
                            COMMENT '停用原因',
    last_login_at       DATETIME(3)  NULL
                            COMMENT '最近一次登录成功时间（UTC）',
    password_changed_at DATETIME(3)  NULL
                            COMMENT '最近一次密码变更时间（UTC）',
    deleted_at          DATETIME(3)  NULL
                            COMMENT '软删除时间；正常用户为 NULL',
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                            ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_username (username),
    KEY idx_users_status_created (status, created_at),
    KEY idx_users_deleted_at (deleted_at),
    CONSTRAINT chk_users_status
        CHECK (status IN ('ACTIVE', 'DISABLED', 'DELETED')),
    CONSTRAINT chk_users_password_algorithm
        CHECK (password_algorithm IN ('argon2id', 'bcrypt')),
    CONSTRAINT chk_users_nickname_length
        CHECK (CHAR_LENGTH(nickname) BETWEEN 1 AND 20),
    CONSTRAINT chk_users_username_length
        CHECK (CHAR_LENGTH(username) BETWEEN 3 AND 24)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '用户身份与公开资料';


-- ---------------------------------------------------------------------------
-- 2. 用户联系方式
--    邮箱/手机号统一存为规范化值；同一联系方式只能有一个 ACTIVE 记录。
--    active_slot/primary_slot 为生成列，用于唯一索引中放行 NULL 值。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_contacts (
    id            VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT '联系方式 ID，如 ct_01K5...',
    user_id       VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT '所属用户',
    contact_type  VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                      COMMENT 'email / phone',
    contact_value VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                      COMMENT '规范化值：邮箱小写或 E.164 手机号',
    verified      TINYINT(1)   NOT NULL DEFAULT 0
                      COMMENT '是否已验证',
    verified_at   DATETIME(3)  NULL
                      COMMENT '验证时间（UTC）',
    is_primary    TINYINT(1)   NOT NULL DEFAULT 0
                      COMMENT '是否为该用户该类型的首选联系方式',
    status        VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE'
                      COMMENT 'ACTIVE / REMOVED',
    removed_at    DATETIME(3)  NULL
                      COMMENT '移除时间；ACTIVE 时为 NULL',
    active_slot   TINYINT GENERATED ALWAYS AS
                      (CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END) STORED
                      COMMENT '仅 ACTIVE 记录参与唯一值约束',
    primary_slot  TINYINT GENERATED ALWAYS AS
                      (CASE WHEN status = 'ACTIVE' AND is_primary = 1 THEN 1 ELSE NULL END) STORED
                      COMMENT '仅 ACTIVE 且首选记录参与唯一约束',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                      ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_uc_value_active (contact_type, contact_value, active_slot),
    UNIQUE KEY uk_uc_user_primary (user_id, contact_type, primary_slot),
    KEY idx_uc_user_type_status (user_id, contact_type, status),
    KEY idx_uc_value_status (contact_type, contact_value, status),
    CONSTRAINT fk_uc_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT chk_uc_type
        CHECK (contact_type IN ('email', 'phone')),
    CONSTRAINT chk_uc_verified
        CHECK (verified IN (0, 1)),
    CONSTRAINT chk_uc_primary
        CHECK (is_primary IN (0, 1)),
    CONSTRAINT chk_uc_status
        CHECK (status IN ('ACTIVE', 'REMOVED')),
    CONSTRAINT chk_uc_removed_not_primary
        CHECK (status = 'ACTIVE' OR is_primary = 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '用户邮箱/手机号';


-- ---------------------------------------------------------------------------
-- 3. 设备
--    设备是稳定身份，可保存多个账号；设备会话只是某次 Cookie 授权。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_devices (
    id                 VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '设备 ID，如 dev_01K5...',
    fingerprint_hash   CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '可选设备指纹摘要',
    fingerprint_key_version SMALLINT UNSIGNED NOT NULL DEFAULT 1
                        COMMENT '设备指纹 HMAC 密钥版本',
    status             VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'ACTIVE'
                           COMMENT 'ACTIVE / REVOKED',
    risk_level         VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'NORMAL'
                        COMMENT 'NORMAL / ELEVATED / HIGH',
    created_ip_address VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '首次访问 IP',
    last_ip_address    VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '最近访问 IP',
    last_user_agent    VARCHAR(512) NULL
                           COMMENT '最近 User-Agent',
    first_seen_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           COMMENT '首次出现时间（UTC）',
    last_seen_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           COMMENT '最近出现时间（UTC）',
    revoked_at         DATETIME(3)  NULL
                           COMMENT '设备撤销时间',
    revoked_reason     VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                           COMMENT '撤销原因',
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ad_fingerprint (fingerprint_hash),
    KEY idx_ad_status_last_seen (status, last_seen_at),
    CONSTRAINT chk_ad_status
        CHECK (status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT chk_ad_risk
        CHECK (risk_level IN ('NORMAL', 'ELEVATED', 'HIGH'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '设备';


-- ---------------------------------------------------------------------------
-- 4. 设备会话
--    对应 HttpOnly 设备 Cookie；只保存 Cookie 值的摘要。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_device_sessions (
    id                 VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '设备会话 ID，如 ds_01K5...',
    device_id          VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '所属设备',
    token_hash         CHAR(64)     CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                           COMMENT '设备 Cookie 值的 HMAC-SHA256',
    hmac_key_version   SMALLINT UNSIGNED NOT NULL DEFAULT 1
                           COMMENT '设备 Cookie HMAC 密钥版本',
    created_ip_address VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_ip_address    VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent         VARCHAR(512) NULL,
    created_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_seen_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at         DATETIME(3)  NOT NULL,
    revoked_at         DATETIME(3)  NULL,
    revoked_reason     VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    updated_at         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                           ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ads_token_hash (token_hash),
    KEY idx_ads_device_active (device_id, revoked_at, expires_at),
    KEY idx_ads_expires (expires_at),
    CONSTRAINT fk_ads_device
        FOREIGN KEY (device_id) REFERENCES auth_devices (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '设备 Cookie 会话';


-- ---------------------------------------------------------------------------
-- 5. 设备已保存账号
--    绑定 device_id，不绑定单次 device_session，Cookie 轮换后仍可保留。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_device_accounts (
    id              VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                        COMMENT '设备账号记录 ID，如 acc_01K5...',
    device_id       VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                        COMMENT '所属设备',
    user_id         VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                        COMMENT '已保存用户',
    authorized_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        COMMENT '首次授权时间（UTC）',
    last_used_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        COMMENT '最近使用时间（UTC）',
    reauth_required TINYINT(1)   NOT NULL DEFAULT 0
                        COMMENT '切换时是否需要重新验证密码',
    reauth_reason   VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                        COMMENT 'new_device / long_unused / risk',
    revoked_at      DATETIME(3)  NULL
                        COMMENT '授权撤销时间',
    revoked_reason  VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                        COMMENT 'logout / user_revoked / risk / account_removed',
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                        ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_ada_device_user (device_id, user_id),
    KEY idx_ada_user_last_used (user_id, last_used_at),
    KEY idx_ada_device_list (device_id, revoked_at, last_used_at),
    CONSTRAINT fk_ada_device
        FOREIGN KEY (device_id) REFERENCES auth_devices (id)
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
-- 6. 登录会话
--    JWT 中的 sid 指向本表；不保存 Access Token 明文。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_sessions (
    id                VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT '会话 ID，如 ses_01K5...',
    user_id           VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    device_id         VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '所属设备；无设备客户端可为 NULL',
    device_session_id VARCHAR(40)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT '创建会话时的设备会话',
    login_method      VARCHAR(16)  CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                          COMMENT 'password / sms_code / email_code / register / switch',
    remember_me       TINYINT(1)   NOT NULL DEFAULT 0
                          COMMENT '是否保持登录',
    ip_address        VARCHAR(45)  CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent        VARCHAR(512) NULL,
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    last_active_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at        DATETIME(3)  NOT NULL
                          COMMENT '会话及 Refresh Token 家族过期时间（UTC）',
    revoked_at        DATETIME(3)  NULL,
    revoked_reason    VARCHAR(32)  CHARACTER SET ascii COLLATE ascii_bin NULL
                          COMMENT 'logout / refresh_reuse / switch_replaced / password_changed / account_disabled',
    active_binding_slot TINYINT GENERATED ALWAYS AS
                          (CASE WHEN revoked_at IS NULL THEN 1 ELSE NULL END) STORED
                          COMMENT '仅未撤销会话参与 device_id + user_id 唯一约束',
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                          ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_as_device_user_active (device_id, user_id, active_binding_slot),
    KEY idx_as_user_active (user_id, revoked_at, expires_at),
    KEY idx_as_device_last_active (device_id, last_active_at),
    KEY idx_as_expires (expires_at),
    CONSTRAINT fk_as_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE CASCADE ON UPDATE RESTRICT,
    CONSTRAINT fk_as_device
        FOREIGN KEY (device_id) REFERENCES auth_devices (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_as_device_session
        FOREIGN KEY (device_session_id) REFERENCES auth_device_sessions (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT chk_as_method
        CHECK (login_method IN ('password', 'sms_code', 'email_code', 'register', 'switch')),
    CONSTRAINT chk_as_remember
        CHECK (remember_me IN (0, 1))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '登录会话';


-- ---------------------------------------------------------------------------
-- 7. Refresh Token 家族
--    一个会话一个家族；重复使用检测命中后撤销整个家族。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_token_families (
    id             VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                       COMMENT '令牌家族 ID，如 fam_01K5...',
    session_id     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at     DATETIME(3) NOT NULL,
    revoked_at     DATETIME(3) NULL,
    revoked_reason VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
                       COMMENT 'logout / reuse_detected / session_revoked',
    compromised_at DATETIME(3) NULL
                       COMMENT '检测到重复使用的时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_atf_session (session_id),
    KEY idx_atf_active (revoked_at, expires_at),
    KEY idx_atf_expires (expires_at),
    CONSTRAINT fk_atf_session
        FOREIGN KEY (session_id) REFERENCES auth_sessions (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = 'Refresh Token 家族';


-- ---------------------------------------------------------------------------
-- 8. Refresh Token 签发审计
--    Redis 白名单是刷新令牌是否有效的唯一判定来源。
--    本表只追加记录签发历史，不保存轮换后的实时状态，也不参与刷新校验。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_refresh_tokens (
    id                   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'Refresh Token 审计记录 ID，如 rt_01K5...',
    family_id            VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_hash           CHAR(64)    CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    hmac_key_version     SMALLINT UNSIGNED NOT NULL DEFAULT 1
                             COMMENT 'Refresh Token HMAC 密钥版本',
    parent_token_id      VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '轮换前令牌 ID（逻辑外键）',
    issued_at            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at           DATETIME(3) NOT NULL,
    request_id           VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '签发请求链路 ID',
    created_ip_address   VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_art_token_hash (token_hash),
    KEY idx_art_family_issued (family_id, issued_at),
    KEY idx_art_parent (parent_token_id),
    KEY idx_art_expires (expires_at),
    CONSTRAINT fk_art_family
        FOREIGN KEY (family_id) REFERENCES auth_token_families (id)
        ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = 'Refresh Token 签发审计（Redis 为白名单权威）';


-- ---------------------------------------------------------------------------
-- 9. 验证码挑战
--    verificationId 即主键 id；只保存验证码摘要。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_verification_challenges (
    id                   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '验证码挑战 ID，如 ver_01K5...',
    user_id              VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '已识别用户；注册或未知账号时为 NULL',
    device_id            VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '发起请求的设备',
    scene                VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'register / login',
    channel              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT 'sms / email',
    target               VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL
                             COMMENT '规范化目标：邮箱小写或 E.164 手机号',
    code_hash            CHAR(64)    CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                             COMMENT '验证码 HMAC-SHA256',
    hmac_key_version     SMALLINT UNSIGNED NOT NULL DEFAULT 1
                             COMMENT '验证码 HMAC 密钥版本，支持后续轮换',
    status               VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING'
                             COMMENT 'PENDING / ACTIVE / CONSUMED / INVALIDATED / FAILED',
    attempts             SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    max_attempts         TINYINT UNSIGNED  NOT NULL DEFAULT 5,
    resend_after_seconds INT UNSIGNED      NOT NULL DEFAULT 60,
    request_id           VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '请求链路 ID',
    request_ip_address   VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent           VARCHAR(512) NULL,
    provider             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL
                             COMMENT '短信/邮件供应商标识',
    provider_message_id  VARCHAR(128) NULL
                             COMMENT '短信/邮件供应商消息 ID',
    send_error_code      VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    sent_at              DATETIME(3) NULL
                             COMMENT '发送成功时间；PENDING/FAILED 为 NULL',
    expires_at           DATETIME(3) NOT NULL
                             COMMENT '过期时间（UTC）',
    last_attempt_at      DATETIME(3) NULL,
    consumed_at          DATETIME(3) NULL,
    invalidated_at       DATETIME(3) NULL,
    created_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at           DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                             ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_avc_target_scene (target, scene, channel, created_at),
    KEY idx_avc_user_scene (user_id, scene, created_at),
    KEY idx_avc_device_created (device_id, created_at),
    KEY idx_avc_status_expires (status, expires_at),
    KEY idx_avc_ip_created (request_ip_address, created_at),
    CONSTRAINT fk_avc_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
    CONSTRAINT fk_avc_device
        FOREIGN KEY (device_id) REFERENCES auth_devices (id)
        ON DELETE SET NULL ON UPDATE RESTRICT,
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
-- 10. 登录尝试审计
--     账号使用 HMAC 摘要记录，避免明文落库。
--     user_id/device_id/session_id 为历史逻辑引用，故意不建外键：
--     清理会话或设备后仍需保留审计链路。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_login_attempts (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    account_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NULL
                     COMMENT 'username / email / phone',
    account_hash CHAR(64)    CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                     COMMENT '规范化账号的 HMAC-SHA256',
    hmac_key_version SMALLINT UNSIGNED NOT NULL DEFAULT 1
                     COMMENT '账号 HMAC 密钥版本',
    device_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    session_id   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    login_method VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                     COMMENT 'password / sms_code / email_code / switch',
    success      TINYINT(1)  NOT NULL,
    failure_code VARCHAR(48) CHARACTER SET ascii COLLATE ascii_bin NULL,
    request_id   VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ip_address   VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent   VARCHAR(512) NULL,
    attempted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_ala_account_time (account_hash, attempted_at),
    KEY idx_ala_ip_time (ip_address, attempted_at),
    KEY idx_ala_user_time (user_id, attempted_at),
    KEY idx_ala_device_time (device_id, attempted_at),
    KEY idx_ala_session_time (session_id, attempted_at),
    KEY idx_ala_attempted (attempted_at),
    CONSTRAINT chk_ala_success
        CHECK (success IN (0, 1)),
    CONSTRAINT chk_ala_account_type
        CHECK (account_type IS NULL OR account_type IN ('username', 'email', 'phone')),
    CONSTRAINT chk_ala_method
        CHECK (login_method IN ('password', 'sms_code', 'email_code', 'switch'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '登录尝试与限流审计';


-- ---------------------------------------------------------------------------
-- 11. 协议同意记录
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS auth_user_agreements (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id        VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    agreement_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                       COMMENT 'community_guidelines / privacy_policy / terms_of_service',
    version        VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    agreed_at      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    request_id     VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    ip_address     VARCHAR(45) CHARACTER SET ascii COLLATE ascii_bin NULL,
    user_agent     VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_aua_user_type_version (user_id, agreement_type, version),
    KEY idx_aua_user_time (user_id, agreed_at),
    CONSTRAINT fk_aua_user
        FOREIGN KEY (user_id) REFERENCES users (id)
        ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT chk_aua_type
        CHECK (agreement_type IN ('community_guidelines', 'privacy_policy', 'terms_of_service'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  ROW_FORMAT = DYNAMIC
  COMMENT = '用户协议同意记录';
