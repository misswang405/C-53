package com.c53.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.common.ApiException;
import com.c53.common.support.IdGenerator;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.dto.AuthDtos.VerificationCodeResponse;
import com.c53.modules.auth.entity.AuthVerificationChallengeEntity;
import com.c53.modules.auth.entity.UserContactEntity;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.AuthVerificationChallengeMapper;
import com.c53.modules.auth.mapper.UserContactMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.model.AuthModels.ChallengeRow;
import com.c53.modules.auth.model.AuthModels.UserRow;
import com.c53.modules.auth.service.VerificationService;
import com.c53.modules.auth.service.VerificationSender;
import com.c53.modules.auth.support.AuthUtils;
import com.c53.modules.auth.support.HmacService;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.support.VerificationException;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 验证码服务实现。
 *
 * <p>安全要点：
 * <ul>
 *   <li>验证码明文不落库，只存 {@code challengeId + code} 的 HMAC 摘要</li>
 *   <li>同一目标 60 秒内只能发送一次，新挑战会使旧挑战失效</li>
 *   <li>校验在行锁内完成，失败次数超限后挑战变为 FAILED</li>
 *   <li>校验成功后挑战变为 CONSUMED，且只有状态为 ACTIVE 时才能更新成功</li>
 * </ul></p>
 */
@Service
public class VerificationServiceImpl implements VerificationService {

    private static final Logger log = LoggerFactory.getLogger(VerificationServiceImpl.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AuthVerificationChallengeMapper challengeMapper;
    private final UserContactMapper userContactMapper;
    private final UserMapper userMapper;
    private final VerificationSender verificationSender;
    private final HmacService hmacService;
    private final AuthProperties properties;
    private final RefreshTokenStore refreshTokenStore;
    private final Environment environment;

    public VerificationServiceImpl(AuthVerificationChallengeMapper challengeMapper,
                                   UserContactMapper userContactMapper,
                                   UserMapper userMapper,
                                   VerificationSender verificationSender,
                                   HmacService hmacService,
                                   AuthProperties properties,
                                   RefreshTokenStore refreshTokenStore,
                                   Environment environment) {
        this.challengeMapper = challengeMapper;
        this.userContactMapper = userContactMapper;
        this.userMapper = userMapper;
        this.verificationSender = verificationSender;
        this.hmacService = hmacService;
        this.properties = properties;
        this.refreshTokenStore = refreshTokenStore;
        this.environment = environment;
    }

    /**
     * 发送验证码。
     *
     * <p>流程：归一化参数 → 注册场景检查联系方式是否已占用 → 重发限流 →
     * 生成/摘要验证码 → 作废旧挑战并写入新挑战 → 调用发送网关 →
     * 更新挑战状态。发送失败时挑战标记为 FAILED。</p>
     *
     * @param sceneValue   场景原始值
     * @param channelValue 渠道原始值
     * @param targetValue  接收方原始值
     * @param deviceId     设备 ID
     * @param requestId    请求追踪 ID
     * @param ip           请求 IP
     * @param userAgent    User-Agent
     * @return 发送结果
     */
    @Override
    public VerificationCodeResponse send(String sceneValue, String channelValue, String targetValue,
                                         String deviceId, String requestId, String ip, String userAgent) {
        // 1. 归一化场景、渠道和目标
        String scene = AuthUtils.normalizeScene(sceneValue);
        String channel = AuthUtils.normalizeChannel(channelValue);
        if (scene == null || channel == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "验证码场景或渠道不正确");
        }
        String target = AuthUtils.normalizeTarget(channel, targetValue);
        if (target == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "手机号或邮箱格式不正确");
        }

        if (properties.getVerification().isDevMode()
                && (!environment.acceptsProfiles(Profiles.of("dev", "test"))
                || environment.acceptsProfiles(Profiles.of("prod", "production")))) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "VERIFICATION_CONFIG_ERROR",
                    "验证码服务未配置，请联系管理员");
        }
        if ("reset_password".equals(scene)) {
            limit("reset-send:ip", hmacService.hashAccount("ip:" + ip), Duration.ofMinutes(1),
                    properties.getLimits().getResetSendPerIpPerMinute());
            limit("reset-send:target", hmacService.hashAccount(channel + ":" + target), Duration.ofHours(1),
                    properties.getLimits().getResetSendPerTargetPerHour());
            limit("reset-send:ip-day", hmacService.hashAccount("ip:" + ip), Duration.ofDays(1),
                    properties.getLimits().getResetSendPerIpPerDay());
            limit("reset-send:target-day", hmacService.hashAccount(channel + ":" + target), Duration.ofDays(1),
                    properties.getLimits().getResetSendPerTargetPerDay());
            limit("reset-send:cooldown", hmacService.hashAccount(channel + ":" + target),
                    properties.getVerification().getResendInterval(), 1);
        }

        // 2. 注册场景下，联系方式已被占用则无需发送
        String contactType = "sms".equals(channel) ? "phone" : "email";
        Optional<String> owner = contactOwner(contactType, target);
        if ("register".equals(scene) && owner.isPresent()) {
            throw ApiException.conflict("CONTACT_TAKEN", "邮箱或手机号已注册");
        }

        // 3. 重发限流：窗口内已有挑战时返回 429，并给出剩余等待秒数
        Instant now = Instant.now();
        Duration interval = properties.getVerification().getResendInterval();
        Optional<ChallengeRow> recent = findRecentActive(target, scene, channel, now.minus(interval));
        if (recent.isPresent()) {
            long elapsed = Duration.between(recent.get().createdAt(), now).toSeconds();
            int retryAfter = (int) Math.max(1, interval.toSeconds() - elapsed);
            throw ApiException.tooManyRequests("VERIFICATION_RESEND_TOO_SOON",
                    "验证码发送过于频繁，请稍后重试",
                    List.of(Map.of("retryAfter", retryAfter)));
        }

        // 4. 生成验证码（开发模式使用固定值）并计算摘要
        String challengeId = IdGenerator.newId("ver");
        String code = properties.getVerification().isDevMode()
                ? properties.getVerification().getDevCode()
                : String.format("%06d", RANDOM.nextInt(1_000_000));
        String codeHash = hmacService.hashVerificationCode(challengeId, code);
        Instant expiresAt = now.plus(properties.getVerification().getTtl());

        // 5. 旧挑战全部失效，保证只有最新验证码可用
        invalidateActive(target, scene, channel, now);
        insertChallenge(challengeId, owner.orElse(null), deviceId, scene, channel, target,
                codeHash, hmacService.keyVersion(), properties.getVerification().getMaxAttempts(),
                (int) interval.toSeconds(), requestId, ip, userAgent, now, expiresAt);

        // 6. 调用发送网关；失败时记录状态并向上抛出
        try {
            VerificationSender.SendResult result = verificationSender.send(channel, target, code);
            markActive(challengeId, result.provider(), result.messageId(), now);
        } catch (ApiException exception) {
            markFailed(challengeId, exception.getCode(), now);
            throw exception;
        } catch (RuntimeException exception) {
            markFailed(challengeId, "SEND_ERROR", now);
            log.warn("verification sender failed channel={} target={}", channel,
                    AuthUtils.maskTarget(channel, target));
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VERIFICATION_SEND_FAILED",
                    "短信或邮件服务暂时不可用");
        }

        return new VerificationCodeResponse(
                challengeId,
                channel,
                AuthUtils.maskTarget(channel, target),
                properties.getVerification().getTtl().toSeconds(),
                (int) interval.toSeconds(),
                AuthUtils.maskTarget(channel, target),
                (int) interval.toSeconds());
    }

    /**
     * 校验并消费验证码。
     *
     * <p>使用 {@code FOR UPDATE} 行锁，保证并发请求串行处理：
     * 只有一个请求能把状态从 ACTIVE 改为 CONSUMED。</p>
     *
     * @param challengeId 挑战 ID
     * @param code        用户输入的验证码
     * @param scene       期望场景
     * @param channel     期望渠道
     * @param target      期望目标
     * @param now         当前时间
     * @return 校验通过的挑战
     */
    @Override
    public ChallengeRow verifyAndConsume(String challengeId, String code, String scene,
                                         String channel, String target, Instant now) {
        try {
            return verifyLocked(challengeId, code, scene, channel, target, now);
        } catch (ApiException exception) {
            throw new VerificationException(exception);
        }
    }

    private ChallengeRow verifyLocked(String challengeId, String code, String scene,
                                      String channel, String target, Instant now) {
        // 1. 加行锁读取挑战
        AuthVerificationChallengeEntity entity = challengeMapper.selectOne(
                Wrappers.<AuthVerificationChallengeEntity>lambdaQuery()
                        .eq(AuthVerificationChallengeEntity::getId, challengeId)
                        .last("FOR UPDATE"));
        if (entity == null) {
            throw invalidCode();
        }
        ChallengeRow challenge = toRow(entity);

        // 2. 场景、渠道、目标必须与挑战一致，防止跨场景复用
        if (!scene.equals(challenge.scene()) || !channel.equals(challenge.channel())
                || !target.equals(challenge.target())) {
            throw ApiException.badRequest("VERIFICATION_TARGET_MISMATCH", "验证码对应目标、渠道或场景不一致");
        }
        // 仅 ACTIVE 挑战可参与校验，PENDING 不允许被错误尝试激活。
        if (!"ACTIVE".equals(challenge.status()) && !"FAILED".equals(challenge.status())) {
            throw invalidCode();
        }
        // 4. 失败次数已耗尽
        if ("FAILED".equals(challenge.status()) || challenge.attempts() >= challenge.maxAttempts()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "VERIFICATION_ATTEMPTS_EXCEEDED",
                    "验证码错误次数超过限制");
        }
        // 5. 过期检查
        if (!challenge.expiresAt().isAfter(now)) {
            markFailed(challenge.id(), "EXPIRED", now);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "VERIFICATION_CODE_EXPIRED", "验证码已过期");
        }
        // 6. 常量时间比较摘要
        String expected = hmacService.hashVerificationCode(challenge.id(), code == null ? "" : code.trim());
        if (!hmacService.matches(expected, challenge.codeHash())) {
            int attempts = incrementAttempts(challenge.id(), challenge.attempts(),
                    challenge.maxAttempts(), now);
            if (attempts >= challenge.maxAttempts()) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "VERIFICATION_ATTEMPTS_EXCEEDED",
                        "验证码错误次数超过限制");
            }
            throw invalidCode();
        }
        // 7. 原子消费；更新失败说明已被其他并发请求使用
        if (!consume(challenge.id(), now)) {
            throw invalidCode();
        }
        return challenge;
    }

    /**
     * 查询某目标当前最新可用的挑战 ID。
     *
     * @param target  接收方
     * @param scene   场景
     * @param channel 渠道
     * @param now     当前时间
     * @return 挑战 ID；不存在时返回 {@code null}
     */
    @Override
    public String latestActiveId(String target, String scene, String channel, Instant now) {
        AuthVerificationChallengeEntity entity = challengeMapper.selectOne(
                Wrappers.<AuthVerificationChallengeEntity>lambdaQuery()
                        .eq(AuthVerificationChallengeEntity::getTarget, target)
                        .eq(AuthVerificationChallengeEntity::getScene, scene)
                        .eq(AuthVerificationChallengeEntity::getChannel, channel)
                        .eq(AuthVerificationChallengeEntity::getStatus, "ACTIVE")
                        .gt(AuthVerificationChallengeEntity::getExpiresAt, now)
                        .orderByDesc(AuthVerificationChallengeEntity::getCreatedAt)
                        .last("LIMIT 1"));
        return entity == null ? null : entity.getId();
    }

    /**
     * 根据联系方式反查用户。
     *
     * @param contactType 联系方式类型
     * @param target      联系方式值
     * @return 用户信息；不存在时为空
     */
    @Override
    public Optional<UserRow> findUserForTarget(String contactType, String target) {
        return findContact(contactType, target).flatMap(contact -> findUser(contact.getUserId()));
    }

    /**
     * 查询联系方式所属用户 ID。
     *
     * @param contactType  联系方式类型
     * @param contactValue 联系方式值
     * @return 用户 ID；未找到时为空
     */
    private Optional<String> contactOwner(String contactType, String contactValue) {
        return findContact(contactType, contactValue).map(UserContactEntity::getUserId);
    }

    /**
     * 查询首选有效联系方式。
     *
     * @param contactType  联系方式类型
     * @param contactValue 联系方式值
     * @return 联系方式；未找到时为空
     */
    private Optional<UserContactEntity> findContact(String contactType, String contactValue) {
        return Optional.ofNullable(userContactMapper.selectOne(
                Wrappers.<UserContactEntity>lambdaQuery()
                        .eq(UserContactEntity::getContactType, contactType)
                        .eq(UserContactEntity::getContactValue, contactValue)
                        .eq(UserContactEntity::getStatus, "ACTIVE")
                        .orderByDesc(UserContactEntity::getIsPrimary)
                        .orderByAsc(UserContactEntity::getCreatedAt)
                        .last("LIMIT 1")));
    }

    /**
     * 按用户 ID 组装用户行，附带主邮箱/手机号。
     *
     * @param userId 用户 ID
     * @return 用户行；不存在或已软删除时为空
     */
    private Optional<UserRow> findUser(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            return Optional.empty();
        }
        // 一次性取出联系方式，避免逐条查询
        List<UserContactEntity> contacts = userContactMapper.selectList(
                Wrappers.<UserContactEntity>lambdaQuery()
                        .eq(UserContactEntity::getUserId, userId)
                        .eq(UserContactEntity::getStatus, "ACTIVE")
                        .orderByDesc(UserContactEntity::getIsPrimary)
                        .orderByAsc(UserContactEntity::getCreatedAt));
        UserContactEntity email = firstContact(contacts, "email");
        UserContactEntity phone = firstContact(contacts, "phone");
        return Optional.of(new UserRow(user.getId(), user.getUsername(), user.getNickname(),
                user.getInitials(), user.getPasswordHash(), user.getPasswordAlgorithm(),
                user.getAvatarUrl(), user.getProfession(), user.getCity(), user.getBio(), user.getStatus(),
                email == null ? null : email.getContactValue(),
                email != null && Boolean.TRUE.equals(email.getVerified()),
                phone == null ? null : phone.getContactValue(),
                phone != null && Boolean.TRUE.equals(phone.getVerified())));
    }

    /**
     * 取指定类型的第一个联系方式。
     *
     * @param contacts 已排序的联系方式列表
     * @param type     类型
     * @return 联系方式；不存在时返回 {@code null}
     */
    private static UserContactEntity firstContact(List<UserContactEntity> contacts, String type) {
        return contacts.stream().filter(contact -> type.equals(contact.getContactType())).findFirst().orElse(null);
    }

    /**
     * 查询重发窗口内刚创建的挑战。
     *
     * @param target  接收方
     * @param scene   场景
     * @param channel 渠道
     * @param since   窗口起点
     * @return 最近挑战；不存在时为空
     */
    private Optional<ChallengeRow> findRecentActive(String target, String scene, String channel, Instant since) {
        AuthVerificationChallengeEntity entity = challengeMapper.selectOne(
                Wrappers.<AuthVerificationChallengeEntity>lambdaQuery()
                        .eq(AuthVerificationChallengeEntity::getTarget, target)
                        .eq(AuthVerificationChallengeEntity::getScene, scene)
                        .eq(AuthVerificationChallengeEntity::getChannel, channel)
                        .in(AuthVerificationChallengeEntity::getStatus, "PENDING", "ACTIVE")
                        .gt(AuthVerificationChallengeEntity::getCreatedAt, since)
                        .orderByDesc(AuthVerificationChallengeEntity::getCreatedAt)
                        .last("LIMIT 1"));
        return entity == null ? Optional.empty() : Optional.of(toRow(entity));
    }

    /**
     * 使某目标所有未完成的挑战失效。
     *
     * @param target  接收方
     * @param scene   场景
     * @param channel 渠道
     * @param now     失效时间
     */
    private void invalidateActive(String target, String scene, String channel, Instant now) {
        challengeMapper.update(null, Wrappers.<AuthVerificationChallengeEntity>lambdaUpdate()
                .eq(AuthVerificationChallengeEntity::getTarget, target)
                .eq(AuthVerificationChallengeEntity::getScene, scene)
                .eq(AuthVerificationChallengeEntity::getChannel, channel)
                .in(AuthVerificationChallengeEntity::getStatus, "PENDING", "ACTIVE")
                .set(AuthVerificationChallengeEntity::getStatus, "INVALIDATED")
                .set(AuthVerificationChallengeEntity::getInvalidatedAt, now)
                .set(AuthVerificationChallengeEntity::getUpdatedAt, now));
    }

    /**
     * 写入新挑战，状态为 PENDING，发送成功后转为 ACTIVE。
     *
     * @param id                 挑战 ID
     * @param userId             关联用户
     * @param deviceId           设备 ID
     * @param scene              场景
     * @param channel            渠道
     * @param target             接收方
     * @param codeHash           验证码摘要
     * @param keyVersion         摘要密钥版本
     * @param maxAttempts        最大尝试次数
     * @param resendAfterSeconds 可重发间隔
     * @param requestId          请求追踪 ID
     * @param ip                 请求 IP
     * @param userAgent          User-Agent
     * @param now                创建时间
     * @param expiresAt          过期时间
     */
    private void insertChallenge(String id, String userId, String deviceId, String scene, String channel,
                                 String target, String codeHash, int keyVersion, int maxAttempts,
                                 int resendAfterSeconds, String requestId, String ip, String userAgent,
                                 Instant now, Instant expiresAt) {
        AuthVerificationChallengeEntity entity = new AuthVerificationChallengeEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setScene(scene);
        entity.setChannel(channel);
        entity.setTarget(target);
        entity.setCodeHash(codeHash);
        entity.setHmacKeyVersion(keyVersion);
        entity.setStatus("PENDING");
        entity.setAttempts(0);
        entity.setMaxAttempts(maxAttempts);
        entity.setResendAfterSeconds(resendAfterSeconds);
        entity.setRequestId(requestId);
        entity.setRequestIpAddress(ip);
        entity.setUserAgent(userAgent);
        entity.setExpiresAt(expiresAt);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        challengeMapper.insert(entity);
    }

    /**
     * 标记挑战为已发送。
     *
     * @param id                挑战 ID
     * @param provider          供应商
     * @param providerMessageId 供应商消息 ID
     * @param now               发送时间
     */
    private void markActive(String id, String provider, String providerMessageId, Instant now) {
        challengeMapper.update(null, Wrappers.<AuthVerificationChallengeEntity>lambdaUpdate()
                .eq(AuthVerificationChallengeEntity::getId, id)
                .eq(AuthVerificationChallengeEntity::getStatus, "PENDING")
                .set(AuthVerificationChallengeEntity::getStatus, "ACTIVE")
                .set(AuthVerificationChallengeEntity::getProvider, provider)
                .set(AuthVerificationChallengeEntity::getProviderMessageId, providerMessageId)
                .set(AuthVerificationChallengeEntity::getSentAt, now)
                .set(AuthVerificationChallengeEntity::getUpdatedAt, now));
    }

    /**
     * 标记挑战发送失败。
     *
     * @param id        挑战 ID
     * @param errorCode 错误码
     * @param now       失败时间
     */
    private void markFailed(String id, String errorCode, Instant now) {
        challengeMapper.update(null, Wrappers.<AuthVerificationChallengeEntity>lambdaUpdate()
                .eq(AuthVerificationChallengeEntity::getId, id)
                .set(AuthVerificationChallengeEntity::getStatus, "FAILED")
                .set(AuthVerificationChallengeEntity::getSendErrorCode, errorCode)
                .set(AuthVerificationChallengeEntity::getUpdatedAt, now));
    }

    /**
     * 累加失败次数；达到上限时把状态置为 FAILED。
     *
     * @param id              挑战 ID
     * @param currentAttempts 当前次数
     * @param maxAttempts     上限
     * @param now             尝试时间
     * @return 累加后的次数
     */
    private int incrementAttempts(String id, int currentAttempts, int maxAttempts, Instant now) {
        int next = currentAttempts + 1;
        challengeMapper.update(null, Wrappers.<AuthVerificationChallengeEntity>lambdaUpdate()
                .eq(AuthVerificationChallengeEntity::getId, id)
                .set(AuthVerificationChallengeEntity::getAttempts, next)
                .set(AuthVerificationChallengeEntity::getLastAttemptAt, now)
                .set(AuthVerificationChallengeEntity::getStatus,
                        next >= maxAttempts ? "FAILED" : "ACTIVE")
                .set(AuthVerificationChallengeEntity::getUpdatedAt, now));
        return next;
    }

    /**
     * 原子消费挑战：仅当状态仍为 ACTIVE 时更新为 CONSUMED。
     *
     * @param id  挑战 ID
     * @param now 消费时间
     * @return 消费成功返回 {@code true}
     */
    private boolean consume(String id, Instant now) {
        return challengeMapper.update(null, Wrappers.<AuthVerificationChallengeEntity>lambdaUpdate()
                .eq(AuthVerificationChallengeEntity::getId, id)
                .eq(AuthVerificationChallengeEntity::getStatus, "ACTIVE")
                .set(AuthVerificationChallengeEntity::getStatus, "CONSUMED")
                .set(AuthVerificationChallengeEntity::getConsumedAt, now)
                .set(AuthVerificationChallengeEntity::getUpdatedAt, now)) == 1;
    }

    /**
     * 实体转内部行模型。
     *
     * @param entity 挑战实体
     * @return 行模型
     */
    private static ChallengeRow toRow(AuthVerificationChallengeEntity entity) {
        return new ChallengeRow(entity.getId(), entity.getUserId(), entity.getDeviceId(),
                entity.getScene(), entity.getChannel(), entity.getTarget(), entity.getCodeHash(),
                entity.getStatus(), entity.getAttempts(), entity.getMaxAttempts(),
                entity.getResendAfterSeconds(), entity.getExpiresAt(), entity.getCreatedAt(),
                entity.getSentAt());
    }

    /**
     * 统一的“验证码错误”异常。
     *
     * @return 401 业务异常
     */
    private void limit(String scope, String identity, Duration ttl, int limit) {
        long count = refreshTokenStore.incrementCounter(scope, identity, ttl);
        if (count > limit) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "验证码请求过于频繁",
                    List.of(Map.of("retryAfter", ttl.toSeconds())));
        }
    }

    private static ApiException invalidCode() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_CODE", "验证码错误或已经使用");
    }
}
