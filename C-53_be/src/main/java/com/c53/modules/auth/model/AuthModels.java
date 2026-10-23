package com.c53.modules.auth.model;

import java.time.Instant;

/**
 * 认证模块内部行模型。
 *
 * <p>与 Entity 的区别：Entity 与单表一一对应，供 MyBatis-Plus 增删改查使用；
 * 这里的 record 是 Repository 查询后返回的只读视图，可能包含联表字段，
 * 只服务于 Service 层，不直接暴露给 HTTP 接口。</p>
 */
public final class AuthModels {

    private AuthModels() {
    }

    /**
     * 用户及其主联系方式。
     *
     * @param id                用户 ID
     * @param username          用户名
     * @param nickname          昵称
     * @param initials          头像占位文字
     * @param passwordHash      密码哈希
     * @param passwordAlgorithm 密码算法标识，例如 bcrypt
     * @param avatarUrl         头像地址
     * @param profession        职业
     * @param city              城市
     * @param bio               个人简介
     * @param status            用户状态，例如 active/disabled
     * @param email             主邮箱
     * @param emailVerified     邮箱是否已验证
     * @param phone             主手机号
     * @param phoneVerified     手机号是否已验证
     */
    public record UserRow(
            String id,
            String username,
            String nickname,
            String initials,
            String passwordHash,
            String passwordAlgorithm,
            String avatarUrl,
            String profession,
            String city,
            String bio,
            String status,
            String email,
            boolean emailVerified,
            String phone,
            boolean phoneVerified
    ) {
    }

    /**
     * 登录会话。
     *
     * @param id          会话 ID
     * @param userId      用户 ID
     * @param deviceId    设备 ID
     * @param loginMethod 登录方式：password/code/switch
     * @param rememberMe  是否保持登录
     * @param createdAt   创建时间
     * @param lastActiveAt 最近活跃时间
     * @param expiresAt   过期时间
     * @param revokedAt   吊销时间
     * @param revokedReason 吊销原因
     */
    public record SessionRow(
            String id,
            String userId,
            String deviceId,
            String loginMethod,
            boolean rememberMe,
            Instant createdAt,
            Instant lastActiveAt,
            Instant expiresAt,
            Instant revokedAt,
            String revokedReason
    ) {
        /**
         * 判断会话在指定时刻是否仍然有效。
         *
         * @param now 当前时间
         * @return 未吊销且未过期返回 {@code true}
         */
        public boolean activeAt(Instant now) {
            return revokedAt == null && expiresAt != null && expiresAt.isAfter(now);
        }
    }

    /**
     * Refresh Token 家族。
     *
     * <p>一个会话对应一个家族，家族内每次刷新都会产生新令牌。
     * 家族一旦因令牌重放被标记为泄露，整个家族全部失效。</p>
     *
     * @param id            家族 ID
     * @param sessionId     所属会话 ID
     * @param expiresAt     家族过期时间
     * @param revokedAt     吊销时间
     * @param revokedReason 吊销原因
     * @param compromisedAt 判定泄露的时间
     */
    public record FamilyRow(
            String id,
            String sessionId,
            Instant expiresAt,
            Instant revokedAt,
            String revokedReason,
            Instant compromisedAt
    ) {
    }

    /**
     * 刷新令牌所属关系。
     *
     * @param sessionId 会话 ID
     * @param familyId  家族 ID
     */
    public record FamilyRef(String sessionId, String familyId) {
    }

    /**
     * 设备会话（设备 Cookie 对应的登录态）。
     *
     * @param id           设备会话 ID
     * @param deviceId     设备 ID
     * @param expiresAt    过期时间
     * @param revokedAt    吊销时间
     * @param deviceStatus 设备状态，例如 active/revoked
     */
    public record DeviceSessionRow(
            String id,
            String deviceId,
            Instant expiresAt,
            Instant revokedAt,
            String deviceStatus
    ) {
    }

    /**
     * 设备上已授权/已保存的账号。
     *
     * @param accountId       用户 ID
     * @param userId          用户 ID
     * @param lastUsedAt      最近使用时间
     * @param reauthRequired  是否需要重新认证
     * @param nickname        昵称
     * @param username        用户名
     * @param avatarUrl       头像地址
     * @param initials        头像占位文字
     * @param activeSessionId 该账号在此设备上的有效会话 ID，无则为 {@code null}
     */
    public record DeviceAccountRow(
            String accountId,
            String userId,
            Instant lastUsedAt,
            boolean reauthRequired,
            String nickname,
            String username,
            String avatarUrl,
            String initials,
            String activeSessionId
    ) {
    }

    /**
     * 验证挑战。
     *
     * @param id                 挑战 ID
     * @param userId             关联用户，注册场景可为空
     * @param deviceId           发起设备
     * @param scene              场景：register/login
     * @param channel            渠道：sms/email
     * @param target             接收方
     * @param codeHash           验证码 HMAC 摘要
     * @param status             状态：pending/consumed/invalidated/expired
     * @param attempts           已尝试次数
     * @param maxAttempts        最大尝试次数
     * @param resendAfterSeconds 可重发间隔秒数
     * @param expiresAt          过期时间
     * @param createdAt          创建时间
     * @param sentAt             发送时间
     */
    public record ChallengeRow(
            String id,
            String userId,
            String deviceId,
            String scene,
            String channel,
            String target,
            String codeHash,
            String status,
            int attempts,
            int maxAttempts,
            int resendAfterSeconds,
            Instant expiresAt,
            Instant createdAt,
            Instant sentAt
    ) {
    }

    /**
     * Redis 中保存的 Refresh Token 元数据。
     *
     * @param tokenId   令牌 ID
     * @param sessionId 会话 ID
     * @param familyId  令牌家族 ID
     * @param userId    用户 ID
     * @param issuedAt  签发时间（epoch 秒）
     * @param expiresAt 过期时间（epoch 秒）
     */
    public record RefreshTokenMeta(
            String tokenId,
            String sessionId,
            String familyId,
            String userId,
            long issuedAt,
            long expiresAt
    ) {
    }

    /**
     * 已使用令牌标记，用于检测刷新令牌重放。
     *
     * @param familyId  令牌家族 ID
     * @param sessionId 会话 ID
     * @param userId    用户 ID
     * @param usedAt    使用时间（epoch 秒）
     */
    public record UsedMarker(
            String familyId,
            String sessionId,
            String userId,
            long usedAt
    ) {
    }

    /**
     * 新生成的令牌及其存储信息。
     *
     * @param raw       返回给客户端的原始令牌
     * @param hash      入库的 HMAC 摘要
     * @param tokenId   令牌 ID
     * @param issuedAt  签发时间
     * @param expiresAt 过期时间
     */
    public record TokenBundle(String raw, String hash, String tokenId, Instant issuedAt, Instant expiresAt) {
    }

    /**
     * 设备解析结果。
     *
     * @param deviceId       设备 ID
     * @param newCookieValue 需要下发给客户端的新设备令牌；无需更新时为 {@code null}
     */
    public record DeviceResolution(String deviceId, String newCookieValue) {
    }
}
