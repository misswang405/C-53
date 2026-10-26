package com.c53.modules.auth.dto;

import java.time.Instant;
import java.util.List;

/**
 * 认证模块对外接口的请求/响应模型。
 *
 * <p>所有 record 只负责 JSON 映射。部分请求同时保留新旧两套字段：
 * 新字段（如 {@code scene}、{@code contact}）是正式契约，
 * 旧字段（如 {@code identifierType}、{@code identifier}）用于兼容旧前端，
 * 由 Service 层统一归一化。</p>
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * 发送验证码请求。
     *
     * @param scene          使用场景：{@code register}、{@code login} 或 {@code reset_password}
     * @param channel        发送渠道：{@code sms} 或 {@code email}
     * @param target         接收方：手机号或邮箱
     * @param identifierType 旧版字段：目标类型
     * @param identifier     旧版字段：目标值
     */
    public record VerificationCodeRequest(
            String scene,
            String channel,
            String target,
            String identifierType,
            String identifier
    ) {
    }

    /**
     * 注册请求。
     *
     * <p>新版字段为 {@code nickname/username/contact/contactType/verificationId/verificationCode/agreed}；
     * 旧版字段用于兼容未升级的前端。</p>
     *
     * @param nickname         昵称
     * @param username         用户名，登录用，全局唯一
     * @param contact          邮箱或手机号
     * @param contactType      联系方式类型：{@code email} 或 {@code phone}
     * @param verificationId   验证挑战 ID
     * @param verificationCode 短信/邮件验证码
     * @param password         明文密码，服务端哈希后存储
     * @param agreed           是否同意用户协议
     * @param identifierType   旧版字段：联系方式类型
     * @param identifier       旧版字段：联系方式值
     * @param code             旧版字段：验证码
     * @param agreeTerms       旧版字段：是否同意协议
     */
    public record RegisterRequest(
            String nickname,
            String username,
            String contact,
            String contactType,
            String verificationId,
            String verificationCode,
            String password,
            Boolean agreed,
            String identifierType,
            String identifier,
            String code,
            Boolean agreeTerms
    ) {
    }

    /**
     * 密码登录请求。
     *
     * @param account        账号：用户名、邮箱或手机号
     * @param password       明文密码
     * @param remember       是否保持登录，决定 Refresh Token 有效期
     * @param identifierType 旧版字段：账号类型
     * @param identifier     旧版字段：账号值
     */
    public record LoginRequest(
            String account,
            String password,
            Boolean remember,
            String identifierType,
            String identifier
    ) {
    }

    /**
     * 验证码登录请求。
     *
     * @param account          账号
     * @param channel          验证码渠道
     * @param verificationId   验证挑战 ID
     * @param verificationCode 验证码
     * @param remember         是否保持登录
     * @param identifierType   旧版字段：账号类型
     * @param identifier       旧版字段：账号值
     * @param code             旧版字段：验证码
     */
    public record CodeLoginRequest(
            String account,
            String channel,
            String verificationId,
            String verificationCode,
            Boolean remember,
            String identifierType,
            String identifier,
            String code
    ) {
    }

    /** 密码找回请求，挑战 ID 必填，不接受登录/注册挑战。 */
    public record ResetPasswordRequest(String identifierType, String identifier,
                                       String verificationId, String code, String newPassword) {
    }

    /** 刷新令牌请求。 */
    public record RefreshRequest(String refreshToken) {
    }

    /**
     * 退出登录请求。
     *
     * @param refreshToken 当前 Refresh Token；传入时会同时吊销整个令牌家族
     */
    public record LogoutRequest(String refreshToken) {
    }

    /**
     * 设备内切换账号请求。
     *
     * @param accountId 目标账号的用户 ID
     */
    public record SwitchRequest(String accountId) {
    }

    /**
     * 用户公开资料。
     *
     * @param id            用户 ID
     * @param nickname      昵称
     * @param username      用户名
     * @param avatarUrl     头像地址
     * @param initials      头像占位文字
     * @param profession    职业
     * @param city          城市
     * @param bio           个人简介
     * @param email         邮箱
     * @param emailVerified 邮箱是否已验证
     * @param phone         手机号
     * @param phoneVerified 手机号是否已验证
     */
    public record UserResponse(
            String id,
            String nickname,
            String username,
            String avatarUrl,
            String initials,
            String profession,
            String city,
            String bio,
            String email,
            boolean emailVerified,
            String phone,
            boolean phoneVerified
    ) {
    }

    /**
     * 登录/注册/切换账号成功后的会话响应。
     *
     * @param accessToken     Access Token
     * @param refreshToken    Refresh Token
     * @param tokenType       令牌类型，固定 {@code Bearer}
     * @param expiresIn       Access Token 剩余有效秒数
     * @param refreshExpiresIn Refresh Token 剩余有效秒数
     * @param sessionId       会话 ID
     * @param user            用户资料
     */
    public record AuthSessionResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            String sessionId,
            UserResponse user
    ) {
    }

    /**
     * 刷新成功响应。
     *
     * <p>每次刷新都会轮换 Refresh Token，因此响应中同时返回新的 Access/Refresh Token。</p>
     *
     * @param accessToken     新 Access Token
     * @param refreshToken    新 Refresh Token
     * @param tokenType       令牌类型，固定 {@code Bearer}
     * @param expiresIn       Access Token 有效秒数
     * @param refreshExpiresIn Refresh Token 有效秒数
     * @param sessionId       会话 ID
     */
    public record RefreshResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            long refreshExpiresIn,
            String sessionId
    ) {
    }

    /**
     * 验证码发送响应。
     *
     * @param verificationId    验证挑战 ID，校验验证码时需回传
     * @param channel           实际使用的渠道
     * @param maskedTarget      脱敏后的接收方
     * @param expiresIn         验证码有效秒数
     * @param resendAfter       距离可重发的秒数
     * @param identifier        旧版字段：目标值
     * @param resendAfterSeconds 旧版字段：可重发秒数
     */
    public record VerificationCodeResponse(
            String verificationId,
            String channel,
            String maskedTarget,
            long expiresIn,
            int resendAfter,
            String identifier,
            int resendAfterSeconds
    ) {
    }

    /**
     * 当前会话信息。
     *
     * @param id           会话 ID
     * @param createdAt    创建时间
     * @param lastActiveAt 最近活跃时间
     */
    public record SessionResponse(String id, Instant createdAt, Instant lastActiveAt) {
    }

    /**
     * 未读数量。
     *
     * @param notifications 未读通知数
     * @param messages      未读私信数
     */
    public record UnreadResponse(long notifications, long messages) {
    }

    /**
     * 当前用户信息响应。
     *
     * @param user    用户资料
     * @param session 当前会话
     * @param unread  未读计数
     */
    public record MeResponse(UserResponse user, SessionResponse session, UnreadResponse unread) {
    }

    /**
     * 设备上已保存的账号。
     *
     * @param accountId       用户 ID
     * @param user            用户资料
     * @param lastUsedAt      最近使用时间
     * @param current         是否为当前登录账号
     * @param reauthRequired  是否需要重新认证
     */
    public record SavedAccountResponse(
            String accountId,
            UserResponse user,
            Instant lastUsedAt,
            boolean current,
            boolean reauthRequired
    ) {
    }

    /**
     * 已保存账号列表。
     *
     * @param items 账号条目
     */
    public record SavedAccountsResponse(List<SavedAccountResponse> items) {
    }

    /**
     * 用户名可用性响应。
     *
     * @param username  规范化后的用户名
     * @param available 是否可用
     */
    public record UsernameAvailabilityResponse(String username, boolean available) {
    }
}
