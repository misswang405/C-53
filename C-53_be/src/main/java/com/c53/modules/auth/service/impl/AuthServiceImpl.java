package com.c53.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.common.ApiException;
import com.c53.common.DependencyUnavailableException;
import com.c53.common.support.IdGenerator;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.dto.AuthDtos;
import com.c53.modules.auth.dto.AuthDtos.AuthSessionResponse;
import com.c53.modules.auth.dto.AuthDtos.CodeLoginRequest;
import com.c53.modules.auth.dto.AuthDtos.LoginRequest;
import com.c53.modules.auth.dto.AuthDtos.LogoutRequest;
import com.c53.modules.auth.dto.AuthDtos.MeResponse;
import com.c53.modules.auth.dto.AuthDtos.RefreshRequest;
import com.c53.modules.auth.dto.AuthDtos.RefreshResponse;
import com.c53.modules.auth.dto.AuthDtos.RegisterRequest;
import com.c53.modules.auth.dto.AuthDtos.SavedAccountResponse;
import com.c53.modules.auth.dto.AuthDtos.SavedAccountsResponse;
import com.c53.modules.auth.dto.AuthDtos.SessionResponse;
import com.c53.modules.auth.dto.AuthDtos.SwitchRequest;
import com.c53.modules.auth.dto.AuthDtos.UnreadResponse;
import com.c53.modules.auth.dto.AuthDtos.UserResponse;
import com.c53.modules.auth.dto.AuthDtos.UsernameAvailabilityResponse;
import com.c53.modules.auth.entity.AuthDeviceAccountEntity;
import com.c53.modules.auth.entity.AuthLoginAttemptEntity;
import com.c53.modules.auth.entity.AuthRefreshTokenEntity;
import com.c53.modules.auth.entity.AuthSessionEntity;
import com.c53.modules.auth.entity.AuthTokenFamilyEntity;
import com.c53.modules.auth.entity.AuthUserAgreementEntity;
import com.c53.modules.auth.entity.UserContactEntity;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.AuthDeviceAccountMapper;
import com.c53.modules.auth.mapper.AuthLoginAttemptMapper;
import com.c53.modules.auth.mapper.AuthRefreshTokenMapper;
import com.c53.modules.auth.mapper.AuthSessionMapper;
import com.c53.modules.auth.mapper.AuthTokenFamilyMapper;
import com.c53.modules.auth.mapper.AuthUserAgreementMapper;
import com.c53.modules.auth.mapper.UserContactMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.model.AuthModels.DeviceAccountRow;
import com.c53.modules.auth.model.AuthModels.DeviceResolution;
import com.c53.modules.auth.model.AuthModels.FamilyRef;
import com.c53.modules.auth.model.AuthModels.FamilyRow;
import com.c53.modules.auth.model.AuthModels.RefreshTokenMeta;
import com.c53.modules.auth.model.AuthModels.SessionRow;
import com.c53.modules.auth.model.AuthModels.TokenBundle;
import com.c53.modules.auth.model.AuthModels.UsedMarker;
import com.c53.modules.auth.model.AuthModels.UserRow;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.service.AuthService;
import com.c53.modules.auth.service.DeviceService;
import com.c53.modules.auth.service.TokenService;
import com.c53.modules.auth.service.VerificationService;
import com.c53.modules.auth.support.AuthUtils;
import com.c53.modules.auth.support.HmacService;
import com.c53.security.AuthPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 认证主流程实现。
 *
 * <p>核心安全模型：
 * <ul>
 *   <li>密码使用 BCrypt(强度 12) 哈希，登录失败同时按账号和 IP 限流</li>
 *   <li>登录成功后创建会话和令牌家族，签发 Access Token + Refresh Token</li>
 *   <li>Refresh Token 采用轮换机制，Redis 保存当前令牌指针和已用标记</li>
 *   <li>检测到旧令牌重放时吊销整个家族，并记录泄露时间</li>
 *   <li>MySQL 保存会话/家族/审计，Redis 是刷新令牌有效性的权威来源</li>
 * </ul>
 * 事务边界通过 {@link TransactionTemplate} 显式控制，避免长事务包裹外部调用。</p>
 */
@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthServiceImpl.class);

    private final UserMapper userMapper;
    private final UserContactMapper userContactMapper;
    private final AuthSessionMapper sessionMapper;
    private final AuthTokenFamilyMapper familyMapper;
    private final AuthRefreshTokenMapper refreshTokenMapper;
    private final AuthLoginAttemptMapper loginAttemptMapper;
    private final AuthUserAgreementMapper agreementMapper;
    private final AuthDeviceAccountMapper deviceAccountMapper;
    private final VerificationService verificationService;
    private final DeviceService deviceService;
    private final RefreshTokenStore refreshTokenStore;
    private final TokenService tokenService;
    private final HmacService hmacService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final TransactionTemplate transactionTemplate;

    public AuthServiceImpl(UserMapper userMapper, UserContactMapper userContactMapper,
                           AuthSessionMapper sessionMapper, AuthTokenFamilyMapper familyMapper,
                           AuthRefreshTokenMapper refreshTokenMapper,
                           AuthLoginAttemptMapper loginAttemptMapper,
                           AuthUserAgreementMapper agreementMapper,
                           AuthDeviceAccountMapper deviceAccountMapper,
                           VerificationService verificationService, DeviceService deviceService,
                           RefreshTokenStore refreshTokenStore, TokenService tokenService,
                           HmacService hmacService, PasswordEncoder passwordEncoder,
                           AuthProperties properties, TransactionTemplate transactionTemplate) {
        this.userMapper = userMapper;
        this.userContactMapper = userContactMapper;
        this.sessionMapper = sessionMapper;
        this.familyMapper = familyMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.loginAttemptMapper = loginAttemptMapper;
        this.agreementMapper = agreementMapper;
        this.deviceAccountMapper = deviceAccountMapper;
        this.verificationService = verificationService;
        this.deviceService = deviceService;
        this.refreshTokenStore = refreshTokenStore;
        this.tokenService = tokenService;
        this.hmacService = hmacService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 注册新用户。
     *
     * <p>流程：字段归一化与校验 → 唯一性预检查 → Redis 可用性检查和限流 →
     * 在事务内消费验证码并写入用户/联系方式/协议记录 →
     * 在事务外创建设备和会话。验证码消费与用户写入放在同一事务，
     * 避免“验证码已用但注册失败”。</p>
     *
     * @param request 注册请求
     * @param meta    请求上下文
     * @return 会话数据和设备 Cookie
     */
    @Override
    public AuthResult<AuthSessionResponse> register(RegisterRequest request, RequestMeta meta) {
        // 1. 归一化新老字段，统一成内部表示
        String username = AuthUtils.normalizeUsername(request.username());
        String nickname = AuthUtils.trimToNull(request.nickname());
        String rawContact = firstNonBlank(request.contact(), request.identifier());
        String contactType = normalizeContactType(firstNonBlank(request.contactType(), request.identifierType()));
        String target = AuthUtils.normalizeTarget(contactType, rawContact);
        String verificationCode = firstNonBlank(request.verificationCode(), request.code());
        boolean agreed = request.agreed() != null ? request.agreed() : Boolean.TRUE.equals(request.agreeTerms());

        // 2. 基础字段校验
        AuthUtils.require(username != null, "用户名需为 3～24 位小写字母、数字、下划线或短横线");
        AuthUtils.require(nickname != null && nickname.codePointCount(0, nickname.length()) <= 20,
                "昵称需为 1～20 个字符");
        AuthUtils.require(contactType != null && target != null, "联系方式格式不正确");
        AuthUtils.require(verificationCode != null && verificationCode.matches("\\d{6}"), "请输入 6 位验证码");
        AuthUtils.validatePassword(request.password());
        if (!agreed) {
            throw ApiException.badRequest("AGREEMENT_REQUIRED", "请先同意社区公约和隐私政策");
        }
        // 3. 唯一性预检查，给出更友好的错误；真正的一致性由数据库唯一索引兜底
        if (usernameExists(username)) {
            throw ApiException.conflict("USERNAME_TAKEN", "用户名已被使用");
        }
        if (contactExists(contactType, target)) {
            throw ApiException.conflict("CONTACT_TAKEN", "邮箱或手机号已注册");
        }

        // 4. 依赖探活和注册限流
        assertDependencyAvailable();
        enforceRateLimit("register:ip", identityKey(meta.ip()), Duration.ofHours(1),
                properties.getLimits().getRegistrationPerIpPerHour(), "注册请求过于频繁");

        Instant now = Instant.now();
        String channel = "phone".equals(contactType) ? "sms" : "email";
        // 5. 优先使用客户端回传的挑战 ID，缺失时取该目标最新可用挑战
        String challengeId = firstNonBlank(request.verificationId(),
                verificationService.latestActiveId(target, "register", channel, now));
        if (challengeId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_CODE", "请先获取注册验证码");
        }

        String userId = IdGenerator.newId("usr");
        String passwordHash = passwordEncoder.encode(request.password());
        // 6. 验证码消费和用户写入在同一事务中完成
        UserRow user = transactionTemplate.execute(status -> {
            verificationService.verifyAndConsume(challengeId, verificationCode, "register", channel, target, now);
            // 事务内再次检查，缩小与并发注册的竞争窗口
            if (usernameExists(username)) {
                throw ApiException.conflict("USERNAME_TAKEN", "用户名已被使用");
            }
            if (contactExists(contactType, target)) {
                throw ApiException.conflict("CONTACT_TAKEN", "邮箱或手机号已注册");
            }
            insertUser(userId, username, nickname, AuthUtils.initials(nickname), passwordHash, "bcrypt", now);
            insertContact(IdGenerator.newId("ct"), userId, contactType, target, true, true, now);
            recordAgreement(userId, "community_guidelines", meta, now);
            recordAgreement(userId, "privacy_policy", meta, now);
            return findUserById(userId)
                    .orElseThrow(() -> new IllegalStateException("registered user could not be loaded"));
        });

        // 7. 注册成功后直接建立登录态
        DeviceResolution device = deviceService.resolveOrCreate(meta.deviceCookie(), meta.ip(), meta.userAgent());
        SessionTokens tokens = createSession(user, device.deviceId(), "register", true, meta);
        upsertDeviceAccount(device.deviceId(), user.id(), now);
        updateLastLogin(user.id(), now);
        return new AuthResult<>(authSession(user, tokens), device.newCookieValue());
    }

    /**
     * 刷新令牌。
     *
     * <p>流程：格式校验 → 查 Redis 元数据 → 校验家族吊销状态 →
     * 校验当前令牌指针 → 校验会话和用户状态 → 限流 →
     * 原子轮换 → 延长会话有效期 → 写审计。
     * 旧令牌重复使用时区分“并发宽限期”和真正的重放。</p>
     *
     * @param request 刷新请求
     * @param meta    请求上下文
     * @return 新令牌
     */
    @Override
    public RefreshResponse refresh(RefreshRequest request, RequestMeta meta) {
        // 1. 格式校验并计算摘要
        TokenService.ParsedRefreshToken parsed = tokenService.parseRefreshToken(
                request == null ? null : request.refreshToken());
        if (parsed == null) {
            throw invalidRefreshToken();
        }
        Instant now = Instant.now();
        // 2. Redis 中没有元数据时，判断是普通失效还是重放
        Optional<RefreshTokenMeta> optionalMeta = refreshTokenStore.getToken(parsed.hash());
        if (optionalMeta.isEmpty()) {
            handleMissingToken(parsed.hash(), now);
        }
        RefreshTokenMeta oldMeta = optionalMeta.orElseThrow();
        // 3. 家族已吊销则直接拒绝
        if (refreshTokenStore.isFamilyRevoked(oldMeta.familyId())) {
            throw invalidRefreshToken();
        }
        // 4. 只有家族当前指针指向的令牌才能刷新
        String current = refreshTokenStore.current(oldMeta.familyId()).orElse(null);
        if (current == null || !parsed.hash().equals(current)) {
            handleRotationConflict(oldMeta, parsed.hash(), now);
        }

        // 5. 会话和用户必须仍然有效，且归属一致
        SessionRow session = findSessionById(oldMeta.sessionId()).orElse(null);
        UserRow user = findUserById(oldMeta.userId()).orElse(null);
        if (session == null || user == null || !session.activeAt(now)
                || !"ACTIVE".equals(user.status())
                || !oldMeta.sessionId().equals(session.id())
                || !oldMeta.userId().equals(session.userId())) {
            cleanupFamily(oldMeta.familyId(), "session_revoked",
                    properties.getRefresh().getRememberTtl());
            throw invalidRefreshToken();
        }

        // 6. 依赖探活和单会话刷新限流
        assertDependencyAvailable();
        enforceRateLimit("refresh:session", session.id(), Duration.ofHours(1),
                properties.getLimits().getRefreshPerSessionPerHour(), "刷新请求过于频繁");

        Duration ttl = tokenService.refreshTtl(session.rememberMe());
        Instant newExpiry = now.plus(ttl);
        TokenBundle newToken = tokenService.newRefreshToken(now, newExpiry);
        RefreshTokenMeta newMeta = new RefreshTokenMeta(newToken.tokenId(), oldMeta.sessionId(),
                oldMeta.familyId(), oldMeta.userId(), newToken.issuedAt().toEpochMilli(),
                newToken.expiresAt().toEpochMilli());

        // 7. Redis 原子轮换；返回码见 RefreshTokenStore#rotate
        int result = refreshTokenStore.rotate(oldMeta, parsed.hash(), newToken.hash(), newMeta, ttl, ttl);
        if (result == 1) {
            // 8. 轮换成功后再延长 MySQL 会话；失败则回滚 Redis，保持两端一致
            Duration oldTokenTtl = remaining(Duration.between(now, Instant.ofEpochMilli(oldMeta.expiresAt())));
            Duration familyTtl = remaining(Duration.between(now, session.expiresAt()));
            try {
                transactionTemplate.executeWithoutResult(status ->
                        renewSessionAndFamily(session.id(), oldMeta.familyId(), now, newExpiry));
            } catch (RuntimeException exception) {
                boolean restored = refreshTokenStore.rollbackRotation(oldMeta, parsed.hash(),
                        newToken.hash(), oldTokenTtl, familyTtl);
                log.error("failed to extend session after refresh; redis rollback restored={}", restored, exception);
                throw exception;
            }
            recordRefreshAudit(newToken, oldMeta.familyId(), oldMeta.tokenId(), meta, now);
            return new RefreshResponse(
                    tokenService.accessToken(session.userId(), session.id(), now),
                    newToken.raw(), "Bearer", tokenService.accessTtlSeconds(),
                    ttl.toSeconds(), session.id());
        }
        // 0：并发刷新已消费旧令牌
        if (result == 0) {
            handleRotationConflict(oldMeta, parsed.hash(), now);
        }
        // -1：家族已吊销
        if (result == -1) {
            throw invalidRefreshToken();
        }
        // -2 及其他：视为重放，吊销整个家族
        replayDetected(oldMeta.sessionId(), oldMeta.familyId(), now);
        throw invalidRefreshToken();
    }

    /**
     * 保证时长至少为 1 秒，避免 Redis EXPIRE 收到 0 或负数。
     *
     * @param duration 原始时长
     * @return 非负且至少 1 秒的时长
     */
    private static Duration remaining(Duration duration) {
        return duration == null || duration.isNegative() || duration.isZero()
                ? Duration.ofSeconds(1) : duration;
    }

    /**
     * 退出登录：吊销当前会话和整个令牌家族，并清理 Redis 白名单。
     *
     * @param principal 当前认证身份
     * @param request   可选的 Refresh Token（当前实现以会话为准）
     */
    @Override
    public void logout(AuthPrincipal principal, LogoutRequest request) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Instant now = Instant.now();
        // 先在事务内吊销 MySQL 记录，再清理 Redis，避免 Redis 失败导致会话仍可用
        Optional<String> familyId = transactionTemplate.execute(status ->
                revokeSessionAndFamily(principal.sessionId(), "logout", "logout", now, false));
        familyId.ifPresent(id -> cleanupFamily(id, "logout", properties.getRefresh().getRememberTtl()));
    }

    /**
     * 查询设备上已保存的账号。
     *
     * <p>只返回状态正常、未被吊销的用户，并标记当前会话对应的账号。</p>
     *
     * @param deviceCookie     设备 Cookie
     * @param currentSessionId 当前会话 ID
     * @param meta             请求上下文
     * @return 已保存账号列表
     */
    @Override
    public SavedAccountsResponse accounts(String deviceCookie, String currentSessionId, RequestMeta meta) {
        // 必须已有有效设备会话
        String deviceId = deviceService.resolveExisting(deviceCookie, meta.ip(), meta.userAgent());
        Instant now = Instant.now();
        List<SavedAccountResponse> items = new ArrayList<>();
        List<AuthDeviceAccountEntity> accounts = deviceAccountMapper.selectList(
                Wrappers.<AuthDeviceAccountEntity>lambdaQuery()
                        .eq(AuthDeviceAccountEntity::getDeviceId, deviceId)
                        .isNull(AuthDeviceAccountEntity::getRevokedAt)
                        .orderByDesc(AuthDeviceAccountEntity::getLastUsedAt));
        for (AuthDeviceAccountEntity account : accounts) {
            // 跳过已删除或已停用的用户
            Optional<UserRow> optionalUser = findUserById(account.getUserId());
            if (optionalUser.isEmpty() || !"ACTIVE".equals(optionalUser.get().status())) {
                continue;
            }
            UserRow savedUser = optionalUser.get();
            String activeSessionId = activeSessionId(deviceId, savedUser.id(), now);
            DeviceAccountRow row = new DeviceAccountRow(account.getId(), savedUser.id(),
                    account.getLastUsedAt(), Boolean.TRUE.equals(account.getReauthRequired()),
                    savedUser.nickname(), savedUser.username(), savedUser.avatarUrl(), savedUser.initials(),
                    activeSessionId);
            // 列表只暴露头像和昵称等摘要信息
            UserResponse user = new UserResponse(row.userId(), row.nickname(), row.username(),
                    row.avatarUrl(), row.initials(), null, null, null, null, false, null, false);
            items.add(new SavedAccountResponse(row.accountId(), user, row.lastUsedAt(),
                    currentSessionId != null && currentSessionId.equals(row.activeSessionId()),
                    row.reauthRequired()));
        }
        return new SavedAccountsResponse(items);
    }

    /**
     * 切换账号。
     *
     * <p>必须已有可信设备；目标账号必须在该设备已保存且无需重新认证。
     * 切换会创建新会话，并在同一事务中吊销该设备上该用户的其他会话。</p>
     *
     * @param request 切换请求
     * @param meta    请求上下文
     * @return 新会话数据和设备 Cookie
     */
    @Override
    public AuthResult<AuthSessionResponse> switchAccount(SwitchRequest request, RequestMeta meta) {
        if (request == null || request.accountId() == null || request.accountId().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "缺少 accountId");
        }
        assertDependencyAvailable();
        // 1. 设备必须有效
        String deviceId = deviceService.resolveExisting(meta.deviceCookie(), meta.ip(), meta.userAgent());
        enforceRateLimit("switch:device", deviceId, Duration.ofMinutes(1),
                properties.getLimits().getSwitchPerDevicePerMinute(), "账号切换过于频繁");

        // 2. 账号关系必须属于当前设备且未吊销
        AuthDeviceAccountEntity account = deviceAccountMapper.selectOne(
                Wrappers.<AuthDeviceAccountEntity>lambdaQuery()
                        .eq(AuthDeviceAccountEntity::getId, request.accountId())
                        .eq(AuthDeviceAccountEntity::getDeviceId, deviceId)
                        .isNull(AuthDeviceAccountEntity::getRevokedAt)
                        .last("LIMIT 1"));
        if (account == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "ACCOUNT_NOT_FOUND", "账号记录不存在");
        }
        if (Boolean.TRUE.equals(account.getReauthRequired())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REAUTH_REQUIRED", "请重新验证该账号",
                    List.of(Map.of("accountId", account.getId())));
        }
        UserRow user = findUserById(account.getUserId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号不可用"));
        if (!"ACTIVE".equals(user.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已被停用");
        }

        // 3. 创建新会话并更新最近使用时间
        Instant now = Instant.now();
        SessionTokens tokens = createSession(user, deviceId, "switch", true, meta);
        touchDeviceAccount(deviceId, user.id(), now);
        recordLoginAttempt(user.id(), "username", user.username(), deviceId, tokens.sessionId(),
                "switch", true, null, meta, now);
        return new AuthResult<>(authSession(user, tokens), null);
    }

    /**
     * 查询当前用户信息。
     *
     * <p>即使 Access Token 未过期，也要求会话在数据库中仍有效，
     * 这样退出登录或风控吊销可以立即生效。</p>
     *
     * @param principal 当前认证身份
     * @return 当前用户信息
     */
    @Override
    public MeResponse me(AuthPrincipal principal) {
        if (principal == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
        }
        Instant now = Instant.now();
        SessionRow session = findSessionById(principal.sessionId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录状态已失效"));
        if (!session.activeAt(now) || !principal.userId().equals(session.userId())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录状态已失效");
        }
        UserRow user = findUserById(principal.userId())
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "SESSION_REVOKED", "登录状态已失效"));
        if (!"ACTIVE".equals(user.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已被停用");
        }
        // 访问即刷新会话活跃时间
        touchSession(session.id(), now);
        return new MeResponse(userResponse(user),
                new SessionResponse(session.id(), session.createdAt(), now),
                new UnreadResponse(0, 0));
    }

    /**
     * 检查用户名可用性。
     *
     * @param username 原始用户名
     * @return 规范化用户名及是否可用
     */
    @Override
    public UsernameAvailabilityResponse usernameAvailability(String username) {
        String normalized = AuthUtils.normalizeUsername(username);
        if (normalized == null) {
            return new AuthDtos.UsernameAvailabilityResponse(username, false);
        }
        return new AuthDtos.UsernameAvailabilityResponse(normalized, !usernameExists(normalized));
    }

    /**
     * 密码登录。
     *
     * <p>账号不存在时也执行一次 BCrypt 校验，使响应时间与真实校验接近，
     * 降低通过耗时枚举账号的风险。</p>
     *
     * @param request 登录请求
     * @param meta    请求上下文
     * @return 会话数据和设备 Cookie
     */
    @Override
    public AuthResult<AuthSessionResponse> login(LoginRequest request, RequestMeta meta) {
        String rawAccount = firstNonBlank(request.account(), request.identifier());
        String accountType = AuthUtils.accountType(rawAccount);
        String account = AuthUtils.normalizeAccount(rawAccount);
        if (account == null || request.password() == null || request.password().isBlank()) {
            throw ApiException.badRequest("VALIDATION_ERROR", "请输入账号和密码");
        }
        Instant now = Instant.now();
        // 1. 先检查历史失败次数，避免攻击者持续尝试
        enforceLoginFailureLimit(hmacService.hashAccount(accountType + ":" + account), meta.ip(), now);

        Optional<UserRow> optionalUser = findUserByAccount(account, accountType);
        if (optionalUser.isEmpty()) {
            // 2. 账号不存在：执行一次假哈希，保持响应时间稳定
            passwordEncoder.matches(request.password(), passwordEncoder.encode("c53-dummy-" + now.toEpochMilli()));
            recordLoginAttempt(null, accountType, account, null, null, "password",
                    false, "INVALID_CREDENTIALS", meta, now);
            throw invalidCredentials();
        }
        UserRow user = optionalUser.get();
        // 3. 只接受 bcrypt 算法，防止历史弱算法被绕过
        boolean valid = "bcrypt".equalsIgnoreCase(user.passwordAlgorithm())
                && passwordEncoder.matches(request.password(), user.passwordHash());
        if (!valid) {
            recordLoginAttempt(user.id(), accountType, account, null, null, "password",
                    false, "INVALID_CREDENTIALS", meta, now);
            throw invalidCredentials();
        }
        if (!"ACTIVE".equals(user.status())) {
            recordLoginAttempt(user.id(), accountType, account, null, null, "password",
                    false, "ACCOUNT_DISABLED", meta, now);
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已被停用");
        }

        // 4. 校验通过后建立会话
        assertDependencyAvailable();
        DeviceResolution device = deviceService.resolveOrCreate(meta.deviceCookie(), meta.ip(), meta.userAgent());
        SessionTokens tokens = createSession(user, device.deviceId(), "password",
                Boolean.TRUE.equals(request.remember()), meta);
        upsertDeviceAccount(device.deviceId(), user.id(), now);
        updateLastLogin(user.id(), now);
        recordLoginAttempt(user.id(), accountType, account, device.deviceId(), tokens.sessionId(),
                "password", true, null, meta, now);
        return new AuthResult<>(authSession(user, tokens), device.newCookieValue());
    }

    /**
     * 验证码登录。
     *
     * <p>验证码消费先于用户查询，避免通过错误提示判断账号是否存在。</p>
     *
     * @param request 验证码登录请求
     * @param meta    请求上下文
     * @return 会话数据和设备 Cookie
     */
    @Override
    public AuthResult<AuthSessionResponse> loginByCode(CodeLoginRequest request, RequestMeta meta) {
        String rawAccount = firstNonBlank(request.account(), request.identifier());
        String channel = AuthUtils.normalizeChannel(firstNonBlank(request.channel(), request.identifierType()));
        String contactType = "sms".equals(channel) ? "phone" : "email";
        String target = AuthUtils.normalizeTarget(channel, rawAccount);
        String code = firstNonBlank(request.verificationCode(), request.code());
        if (channel == null || target == null || code == null || !code.matches("\\d{6}")) {
            throw ApiException.badRequest("VALIDATION_ERROR", "请输入正确的账号和 6 位验证码");
        }

        Instant now = Instant.now();
        // 1. 找到挑战并消费验证码
        String challengeId = firstNonBlank(request.verificationId(),
                verificationService.latestActiveId(target, "login", channel, now));
        if (challengeId == null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_VERIFICATION_CODE", "请先获取登录验证码");
        }
        transactionTemplate.executeWithoutResult(status ->
                verificationService.verifyAndConsume(challengeId, code, "login", channel, target, now));

        // 2. 验证码有效后再查用户
        Optional<UserRow> optionalUser = findUserByAccount(target, contactType);
        if (optionalUser.isEmpty()) {
            recordLoginAttempt(null, contactType, target, null, null,
                    "sms".equals(channel) ? "sms_code" : "email_code", false,
                    "INVALID_CREDENTIALS", meta, now);
            throw invalidCredentials();
        }
        UserRow user = optionalUser.get();
        if (!"ACTIVE".equals(user.status())) {
            throw ApiException.forbidden("ACCOUNT_DISABLED", "账号已被停用");
        }

        // 3. 建立会话
        assertDependencyAvailable();
        DeviceResolution device = deviceService.resolveOrCreate(meta.deviceCookie(), meta.ip(), meta.userAgent());
        SessionTokens tokens = createSession(user, device.deviceId(),
                "sms".equals(channel) ? "sms_code" : "email_code",
                Boolean.TRUE.equals(request.remember()), meta);
        upsertDeviceAccount(device.deviceId(), user.id(), now);
        updateLastLogin(user.id(), now);
        recordLoginAttempt(user.id(), contactType, target, device.deviceId(), tokens.sessionId(),
                "sms".equals(channel) ? "sms_code" : "email_code", true, null, meta, now);
        return new AuthResult<>(authSession(user, tokens), device.newCookieValue());
    }

    /**
     * 创建登录会话。
     *
     * <p>步骤：事务内吊销该设备上同用户的旧会话 → 写入新会话和令牌家族 →
     * 事务外写入 Redis 白名单 → Redis 失败时回滚 MySQL 和旧会话 →
     * 清理旧家族并写审计。</p>
     *
     * @param user        用户
     * @param deviceId    设备 ID
     * @param loginMethod 登录方式
     * @param remember    是否保持登录
     * @param meta        请求上下文
     * @return 会话令牌信息
     */
    private SessionTokens createSession(UserRow user, String deviceId, String loginMethod,
                                        boolean remember, RequestMeta meta) {
        Instant now = Instant.now();
        Duration ttl = tokenService.refreshTtl(remember);
        Instant expiresAt = now.plus(ttl);
        String sessionId = IdGenerator.newId("ses");
        String familyId = IdGenerator.newId("fam");
        // 1. 同一设备同一用户只保留一个有效会话，旧会话稍后在 Redis 中清理
        List<FamilyRef> replaced = transactionTemplate.execute(status -> {
            List<FamilyRef> refs = deviceId == null ? List.of()
                    : revokeActiveSessions(deviceId, user.id(), "switch_replaced", now);
            insertSession(sessionId, user.id(), deviceId, loginMethod, remember,
                    meta.ip(), meta.userAgent(), now, expiresAt);
            insertFamily(familyId, sessionId, now, expiresAt);
            return refs == null ? List.of() : refs;
        });

        TokenBundle token = tokenService.newRefreshToken(now, expiresAt);
        RefreshTokenMeta tokenMeta = new RefreshTokenMeta(token.tokenId(), sessionId, familyId,
                user.id(), token.issuedAt().toEpochMilli(), token.expiresAt().toEpochMilli());
        try {
            // 2. Redis 写入失败时，MySQL 中新会话也要回滚，并恢复刚被吊销的旧会话
            refreshTokenStore.writeInitial(tokenMeta, token.hash(), ttl, ttl);
        } catch (DependencyUnavailableException exception) {
            Instant failedAt = Instant.now();
            transactionTemplate.executeWithoutResult(status -> {
                revokeSessionAndFamily(sessionId, "session_creation_failed",
                        "session_revoked", failedAt, false);
                restoreSessionsAfterFailedLogin(replaced, failedAt);
            });
            throw exception;
        }
        // 3. 清理被替换家族的 Redis 白名单
        if (replaced != null) {
            for (FamilyRef ref : replaced) {
                if (ref.familyId() != null) {
                    cleanupFamily(ref.familyId(), "session_revoked", ttl);
                }
            }
        }
        recordRefreshAudit(token, familyId, null, meta, now);
        return new SessionTokens(sessionId, familyId, token, ttl);
    }

    /**
     * 处理 Redis 中找不到令牌元数据的情况。
     *
     * <p>如果存在已用标记且仍在并发宽限期内，说明另一个刷新请求正在处理；
     * 否则视为令牌重放。</p>
     *
     * @param tokenHash 令牌摘要
     * @param now       当前时间
     */
    private void handleMissingToken(String tokenHash, Instant now) {
        Optional<UsedMarker> used = refreshTokenStore.getUsed(tokenHash);
        if (used.isEmpty()) {
            throw invalidRefreshToken();
        }
        UsedMarker marker = used.get();
        if (withinConcurrentGrace(marker.usedAt(), now)) {
            throw refreshInProgress();
        }
        replayDetected(marker.sessionId(), marker.familyId(), now);
        throw invalidRefreshToken();
    }

    /**
     * 处理轮换冲突：并发刷新返回 409，确认重放则吊销家族。
     *
     * @param oldMeta   旧令牌元数据
     * @param tokenHash 旧令牌摘要
     * @param now       当前时间
     */
    private void handleRotationConflict(RefreshTokenMeta oldMeta, String tokenHash, Instant now) {
        Optional<UsedMarker> used = refreshTokenStore.getUsed(tokenHash);
        if (used.isPresent() && withinConcurrentGrace(used.get().usedAt(), now)) {
            throw refreshInProgress();
        }
        replayDetected(oldMeta.sessionId(), oldMeta.familyId(), now);
        throw invalidRefreshToken();
    }

    /**
     * 判断令牌使用时间是否落在并发宽限期内。
     *
     * @param usedAtMillis 令牌被使用的时间（epoch 毫秒）
     * @param now          当前时间
     * @return 在宽限期内返回 {@code true}
     */
    private boolean withinConcurrentGrace(long usedAtMillis, Instant now) {
        long elapsed = now.toEpochMilli() - usedAtMillis;
        return elapsed >= 0 && elapsed <= properties.getRefresh().getConcurrentGrace().toMillis();
    }

    /**
     * 检测到刷新令牌重放：吊销会话和家族，并标记为泄露。
     *
     * <p>MySQL 吊销失败也要继续清理 Redis，尽量让泄露令牌尽快失效。</p>
     *
     * @param sessionId 会话 ID
     * @param familyId  家族 ID
     * @param now       当前时间
     */
    private void replayDetected(String sessionId, String familyId, Instant now) {
        log.warn("refresh token reuse detected; revoking family sessionId={} familyId={}", sessionId, familyId);
        try {
            transactionTemplate.executeWithoutResult(status ->
                    revokeSessionAndFamily(sessionId, "refresh_reuse", "reuse_detected", now, true));
        } catch (RuntimeException exception) {
            log.error("failed to revoke compromised session in mysql", exception);
        }
        cleanupFamily(familyId, "reuse_detected", properties.getRefresh().getRememberTtl());
    }

    /**
     * 清理 Redis 中的令牌家族。
     *
     * <p>优先使用家族真实过期时间作为吊销标记 TTL；查询失败时退回配置值。
     * 清理失败只记录日志，不影响主流程。</p>
     *
     * @param familyId    家族 ID
     * @param reason      吊销原因
     * @param fallbackTtl 兜底 TTL
     */
    private void cleanupFamily(String familyId, String reason, Duration fallbackTtl) {
        if (familyId == null) {
            return;
        }
        try {
            Duration ttl = fallbackTtl;
            Optional<FamilyRow> family = findFamilyById(familyId);
            if (family.isPresent() && family.get().expiresAt() != null
                    && family.get().expiresAt().isAfter(Instant.now())) {
                ttl = Duration.between(Instant.now(), family.get().expiresAt());
            }
            refreshTokenStore.revokeFamily(familyId, reason, ttl, tokenHashes(familyId));
        } catch (RuntimeException exception) {
            log.warn("failed to clean redis token family familyId={} reason={}", familyId, reason);
        }
    }

    /**
     * 写入刷新令牌签发审计。
     *
     * <p>审计属于旁路能力，写入失败不影响认证主流程。</p>
     *
     * @param token         新令牌
     * @param familyId      家族 ID
     * @param parentTokenId 父令牌 ID
     * @param meta          请求上下文
     * @param now           签发时间
     */
    private void recordRefreshAudit(TokenBundle token, String familyId, String parentTokenId,
                                    RequestMeta meta, Instant now) {
        try {
            AuthRefreshTokenEntity entity = new AuthRefreshTokenEntity();
            entity.setId(token.tokenId());
            entity.setFamilyId(familyId);
            entity.setTokenHash(token.hash());
            entity.setHmacKeyVersion(hmacService.keyVersion());
            entity.setParentTokenId(parentTokenId);
            entity.setIssuedAt(now);
            entity.setExpiresAt(token.expiresAt());
            entity.setRequestId(meta.requestId());
            entity.setCreatedIpAddress(meta.ip());
            refreshTokenMapper.insert(entity);
        } catch (DataAccessException exception) {
            log.warn("refresh token audit write failed familyId={} tokenId={}", familyId, token.tokenId());
        }
    }

    /**
     * 写入登录尝试审计。
     *
     * <p>账号以 HMAC 摘要保存，审计写入失败不影响登录结果。</p>
     *
     * @param userId            用户 ID
     * @param accountType       账号类型
     * @param normalizedAccount 规范化账号
     * @param deviceId          设备 ID
     * @param sessionId         会话 ID
     * @param method            登录方式
     * @param success           是否成功
     * @param failureCode       失败错误码
     * @param meta              请求上下文
     * @param now               尝试时间
     */
    private void recordLoginAttempt(String userId, String accountType, String normalizedAccount,
                                    String deviceId, String sessionId, String method,
                                    boolean success, String failureCode, RequestMeta meta, Instant now) {
        try {
            AuthLoginAttemptEntity entity = new AuthLoginAttemptEntity();
            entity.setUserId(userId);
            entity.setAccountType(accountType == null ? null : accountType.toLowerCase(Locale.ROOT));
            entity.setAccountHash(hmacService.hashAccount(accountType + ":" + normalizedAccount));
            entity.setHmacKeyVersion(hmacService.keyVersion());
            entity.setDeviceId(deviceId);
            entity.setSessionId(sessionId);
            entity.setLoginMethod(method);
            entity.setSuccess(success);
            entity.setFailureCode(failureCode);
            entity.setRequestId(meta.requestId());
            entity.setIpAddress(meta.ip());
            entity.setUserAgent(meta.userAgent());
            entity.setAttemptedAt(now);
            loginAttemptMapper.insert(entity);
        } catch (DataAccessException exception) {
            log.warn("login attempt audit write failed method={} success={}", method, success);
        }
    }

    /**
     * 检查登录失败次数是否超限。
     *
     * <p>同时按账号和 IP 统计：前者防止定向撞库，后者防止单 IP 广撒网。</p>
     *
     * @param accountHash 账号摘要
     * @param ip          客户端 IP
     * @param now         当前时间
     */
    private void enforceLoginFailureLimit(String accountHash, String ip, Instant now) {
        Instant since = now.minus(properties.getLimits().getLoginFailureWindow());
        long accountFailures = loginAttemptMapper.selectCount(
                Wrappers.<AuthLoginAttemptEntity>lambdaQuery()
                        .eq(AuthLoginAttemptEntity::getSuccess, false)
                        .eq(AuthLoginAttemptEntity::getAccountHash, accountHash)
                        .ge(AuthLoginAttemptEntity::getAttemptedAt, since));
        long ipFailures = loginAttemptMapper.selectCount(
                Wrappers.<AuthLoginAttemptEntity>lambdaQuery()
                        .eq(AuthLoginAttemptEntity::getSuccess, false)
                        .eq(AuthLoginAttemptEntity::getIpAddress, ip)
                        .ge(AuthLoginAttemptEntity::getAttemptedAt, since));
        if (accountFailures >= properties.getLimits().getLoginFailuresPerAccount()
                || ipFailures >= properties.getLimits().getLoginPerIpPerMinute()) {
            throw ApiException.tooManyRequests("RATE_LIMITED", "登录尝试过于频繁，请稍后重试",
                    List.of(Map.of("retryAfter", 300)));
        }
    }

    /**
     * 基于 Redis 固定窗口的通用限流。
     *
     * @param scope    限流维度
     * @param identity 维度值
     * @param ttl      窗口长度
     * @param limit    窗口内最大次数
     * @param message  超限提示
     */
    private void enforceRateLimit(String scope, String identity, Duration ttl, int limit, String message) {
        long count = refreshTokenStore.incrementCounter(scope, identity, ttl);
        if (count > limit) {
            throw ApiException.tooManyRequests("RATE_LIMITED", message,
                    List.of(Map.of("retryAfter", ttl.toSeconds())));
        }
    }

    /**
     * 探测 Redis 是否可用，不可用时抛出 503。
     */
    private void assertDependencyAvailable() {
        refreshTokenStore.ping();
    }

    /**
     * 新增或恢复设备账号关系。
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @param now      当前时间
     */
    private void upsertDeviceAccount(String deviceId, String userId, Instant now) {
        try {
            AuthDeviceAccountEntity entity = new AuthDeviceAccountEntity();
            entity.setId(IdGenerator.newId("acc"));
            entity.setDeviceId(deviceId);
            entity.setUserId(userId);
            entity.setAuthorizedAt(now);
            entity.setLastUsedAt(now);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            deviceAccountMapper.upsert(entity);
        } catch (DataAccessException exception) {
            log.warn("failed to update saved device account deviceId={} userId={}", deviceId, userId);
        }
    }

    /**
     * 更新设备账号的最近使用时间。
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @param now      当前时间
     */
    private void touchDeviceAccount(String deviceId, String userId, Instant now) {
        try {
            deviceAccountMapper.update(null, Wrappers.<AuthDeviceAccountEntity>lambdaUpdate()
                    .eq(AuthDeviceAccountEntity::getDeviceId, deviceId)
                    .eq(AuthDeviceAccountEntity::getUserId, userId)
                    .set(AuthDeviceAccountEntity::getLastUsedAt, now)
                    .set(AuthDeviceAccountEntity::getUpdatedAt, now));
        } catch (DataAccessException exception) {
            log.warn("failed to touch saved device account deviceId={} userId={}", deviceId, userId);
        }
    }

    /**
     * 查询某设备上某用户当前有效的会话 ID。
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @param now      当前时间
     * @return 会话 ID；不存在时返回 {@code null}
     */
    private String activeSessionId(String deviceId, String userId, Instant now) {
        AuthSessionEntity session = sessionMapper.selectOne(
                Wrappers.<AuthSessionEntity>lambdaQuery()
                        .eq(AuthSessionEntity::getDeviceId, deviceId)
                        .eq(AuthSessionEntity::getUserId, userId)
                        .isNull(AuthSessionEntity::getRevokedAt)
                        .gt(AuthSessionEntity::getExpiresAt, now)
                        .orderByDesc(AuthSessionEntity::getLastActiveAt)
                        .last("LIMIT 1"));
        return session == null ? null : session.getId();
    }

    /**
     * 按用户 ID 组装用户行，并附带主邮箱/手机号。
     *
     * @param userId 用户 ID
     * @return 用户行；不存在或已软删除时为空
     */
    private Optional<UserRow> findUserById(String userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            return Optional.empty();
        }
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
     * 按登录账号查询用户。
     *
     * @param normalizedAccount 规范化账号
     * @param accountType       账号类型
     * @return 用户行；不存在时为空
     */
    private Optional<UserRow> findUserByAccount(String normalizedAccount, String accountType) {
        if ("email".equals(accountType) || "phone".equals(accountType)) {
            // 邮箱/手机号走联系方式表
            UserContactEntity contact = userContactMapper.selectOne(
                    Wrappers.<UserContactEntity>lambdaQuery()
                            .eq(UserContactEntity::getContactType, accountType)
                            .eq(UserContactEntity::getContactValue, normalizedAccount)
                            .eq(UserContactEntity::getStatus, "ACTIVE")
                            .orderByDesc(UserContactEntity::getIsPrimary)
                            .orderByAsc(UserContactEntity::getCreatedAt)
                            .last("LIMIT 1"));
            return contact == null ? Optional.empty() : findUserById(contact.getUserId());
        }
        // 用户名直接查用户表
        UserEntity user = userMapper.selectOne(
                Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getUsername, normalizedAccount)
                        .isNull(UserEntity::getDeletedAt)
                        .last("LIMIT 1"));
        return user == null ? Optional.empty() : findUserById(user.getId());
    }

    /**
     * 判断用户名是否已存在（包含软删除用户）。
     *
     * @param username 规范化用户名
     * @return 已存在返回 {@code true}
     */
    private boolean usernameExists(String username) {
        return userMapper.selectCount(Wrappers.<UserEntity>lambdaQuery()
                .eq(UserEntity::getUsername, username)) > 0;
    }

    /**
     * 判断有效联系方式是否已被占用。
     *
     * @param contactType  联系方式类型
     * @param contactValue 联系方式值
     * @return 已占用返回 {@code true}
     */
    private boolean contactExists(String contactType, String contactValue) {
        return userContactMapper.selectCount(Wrappers.<UserContactEntity>lambdaQuery()
                .eq(UserContactEntity::getContactType, contactType)
                .eq(UserContactEntity::getContactValue, contactValue)
                .eq(UserContactEntity::getStatus, "ACTIVE")) > 0;
    }

    /**
     * 插入用户。
     *
     * @param userId       用户 ID
     * @param username     用户名
     * @param nickname     昵称
     * @param initials     头像占位文字
     * @param passwordHash 密码哈希
     * @param algorithm    密码算法
     * @param now          创建时间
     */
    private void insertUser(String userId, String username, String nickname, String initials,
                            String passwordHash, String algorithm, Instant now) {
        UserEntity entity = new UserEntity();
        entity.setId(userId);
        entity.setUsername(username);
        entity.setNickname(nickname);
        entity.setInitials(initials);
        entity.setPasswordHash(passwordHash);
        entity.setPasswordAlgorithm(algorithm);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userMapper.insert(entity);
    }

    /**
     * 插入用户联系方式。
     *
     * @param contactId 联系方式 ID
     * @param userId    用户 ID
     * @param type      类型
     * @param value     值
     * @param verified  是否已验证
     * @param primary   是否主联系方式
     * @param now       创建时间
     */
    private void insertContact(String contactId, String userId, String type, String value,
                               boolean verified, boolean primary, Instant now) {
        UserContactEntity entity = new UserContactEntity();
        entity.setId(contactId);
        entity.setUserId(userId);
        entity.setContactType(type);
        entity.setContactValue(value);
        entity.setVerified(verified);
        entity.setVerifiedAt(verified ? now : null);
        entity.setIsPrimary(primary);
        entity.setStatus("ACTIVE");
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        userContactMapper.insert(entity);
    }

    /**
     * 更新用户最近登录时间。
     *
     * @param userId 用户 ID
     * @param now    登录时间
     */
    private void updateLastLogin(String userId, Instant now) {
        userMapper.update(null, Wrappers.<UserEntity>lambdaUpdate()
                .eq(UserEntity::getId, userId)
                .set(UserEntity::getLastLoginAt, now)
                .set(UserEntity::getUpdatedAt, now));
    }

    /**
     * 记录用户同意协议。
     *
     * @param userId 用户 ID
     * @param type   协议类型
     * @param meta   请求上下文
     * @param now    同意时间
     */
    private void recordAgreement(String userId, String type, RequestMeta meta, Instant now) {
        AuthUserAgreementEntity entity = new AuthUserAgreementEntity();
        entity.setUserId(userId);
        entity.setAgreementType(type);
        entity.setVersion("2026-10-22");
        entity.setAgreedAt(now);
        entity.setRequestId(meta.requestId());
        entity.setIpAddress(meta.ip());
        entity.setUserAgent(meta.userAgent());
        agreementMapper.upsert(entity);
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
     * 按 ID 查询会话并转为行模型。
     *
     * @param sessionId 会话 ID
     * @return 会话行；不存在时为空
     */
    private Optional<SessionRow> findSessionById(String sessionId) {
        AuthSessionEntity entity = sessionMapper.selectById(sessionId);
        return entity == null ? Optional.empty() : Optional.of(new SessionRow(
                entity.getId(), entity.getUserId(), entity.getDeviceId(), entity.getLoginMethod(),
                Boolean.TRUE.equals(entity.getRememberMe()), entity.getCreatedAt(),
                entity.getLastActiveAt(), entity.getExpiresAt(), entity.getRevokedAt(),
                entity.getRevokedReason()));
    }

    /**
     * 按会话查询令牌家族。
     *
     * @param sessionId 会话 ID
     * @return 家族；不存在时为空
     */
    private Optional<FamilyRow> findFamilyBySession(String sessionId) {
        return Optional.ofNullable(familyMapper.selectOne(Wrappers.<AuthTokenFamilyEntity>lambdaQuery()
                .eq(AuthTokenFamilyEntity::getSessionId, sessionId)
                .last("LIMIT 1"))).map(AuthServiceImpl::toFamilyRow);
    }

    /**
     * 按 ID 查询令牌家族。
     *
     * @param familyId 家族 ID
     * @return 家族；不存在时为空
     */
    private Optional<FamilyRow> findFamilyById(String familyId) {
        AuthTokenFamilyEntity entity = familyMapper.selectById(familyId);
        return entity == null ? Optional.empty() : Optional.of(toFamilyRow(entity));
    }

    /**
     * 家族实体转行模型。
     *
     * @param entity 家族实体
     * @return 家族行
     */
    private static FamilyRow toFamilyRow(AuthTokenFamilyEntity entity) {
        return new FamilyRow(entity.getId(), entity.getSessionId(), entity.getExpiresAt(),
                entity.getRevokedAt(), entity.getRevokedReason(), entity.getCompromisedAt());
    }

    /**
     * 插入登录会话。
     *
     * @param id          会话 ID
     * @param userId      用户 ID
     * @param deviceId    设备 ID
     * @param loginMethod 登录方式
     * @param rememberMe  是否保持登录
     * @param ip          登录 IP
     * @param userAgent   User-Agent
     * @param now         创建时间
     * @param expiresAt   过期时间
     */
    private void insertSession(String id, String userId, String deviceId, String loginMethod,
                               boolean rememberMe, String ip, String userAgent,
                               Instant now, Instant expiresAt) {
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setId(id);
        entity.setUserId(userId);
        entity.setDeviceId(deviceId);
        entity.setLoginMethod(loginMethod);
        entity.setRememberMe(rememberMe);
        entity.setIpAddress(ip);
        entity.setUserAgent(userAgent);
        entity.setCreatedAt(now);
        entity.setLastActiveAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setUpdatedAt(now);
        sessionMapper.insert(entity);
    }

    /**
     * 插入令牌家族。
     *
     * @param id        家族 ID
     * @param sessionId 会话 ID
     * @param now       创建时间
     * @param expiresAt 过期时间
     */
    private void insertFamily(String id, String sessionId, Instant now, Instant expiresAt) {
        AuthTokenFamilyEntity entity = new AuthTokenFamilyEntity();
        entity.setId(id);
        entity.setSessionId(sessionId);
        entity.setCreatedAt(now);
        entity.setExpiresAt(expiresAt);
        familyMapper.insert(entity);
    }

    /**
     * 吊销某设备上某用户的所有有效会话和家族。
     *
     * <p>先查出现有会话/家族引用，再批量更新，返回值用于失败时恢复。</p>
     *
     * @param deviceId 设备 ID
     * @param userId   用户 ID
     * @param reason   吊销原因
     * @param now      吊销时间
     * @return 被吊销的会话/家族引用
     */
    private List<FamilyRef> revokeActiveSessions(String deviceId, String userId, String reason, Instant now) {
        List<AuthSessionEntity> activeSessions = sessionMapper.selectList(
                Wrappers.<AuthSessionEntity>lambdaQuery()
                        .eq(AuthSessionEntity::getDeviceId, deviceId)
                        .eq(AuthSessionEntity::getUserId, userId)
                        .isNull(AuthSessionEntity::getRevokedAt));
        List<FamilyRef> refs = new ArrayList<>();
        List<String> sessionIds = new ArrayList<>();
        for (AuthSessionEntity session : activeSessions) {
            sessionIds.add(session.getId());
            AuthTokenFamilyEntity family = familyMapper.selectOne(
                    Wrappers.<AuthTokenFamilyEntity>lambdaQuery()
                            .eq(AuthTokenFamilyEntity::getSessionId, session.getId())
                            .last("LIMIT 1"));
            refs.add(new FamilyRef(session.getId(), family == null ? null : family.getId()));
        }
        if (sessionIds.isEmpty()) {
            return refs;
        }
        sessionMapper.update(null, Wrappers.<AuthSessionEntity>lambdaUpdate()
                .in(AuthSessionEntity::getId, sessionIds)
                .isNull(AuthSessionEntity::getRevokedAt)
                .set(AuthSessionEntity::getRevokedAt, now)
                .set(AuthSessionEntity::getRevokedReason, reason)
                .set(AuthSessionEntity::getUpdatedAt, now));
        familyMapper.update(null, Wrappers.<AuthTokenFamilyEntity>lambdaUpdate()
                .in(AuthTokenFamilyEntity::getSessionId, sessionIds)
                .isNull(AuthTokenFamilyEntity::getRevokedAt)
                .set(AuthTokenFamilyEntity::getRevokedAt, now)
                .set(AuthTokenFamilyEntity::getRevokedReason, reason));
        return refs;
    }

    /**
     * 登录失败时恢复因切换账号被吊销的会话。
     *
     * <p>只恢复原因为 {@code switch_replaced} 的记录。</p>
     *
     * @param sessions 需要恢复的引用
     * @param now      当前时间
     */
    private void restoreSessionsAfterFailedLogin(List<FamilyRef> sessions, Instant now) {
        for (FamilyRef ref : sessions) {
            if (ref == null || ref.sessionId() == null) {
                continue;
            }
            sessionMapper.update(null, Wrappers.<AuthSessionEntity>lambdaUpdate()
                    .eq(AuthSessionEntity::getId, ref.sessionId())
                    .eq(AuthSessionEntity::getRevokedReason, "switch_replaced")
                    .set(AuthSessionEntity::getRevokedAt, null)
                    .set(AuthSessionEntity::getRevokedReason, null)
                    .set(AuthSessionEntity::getUpdatedAt, now));
            familyMapper.update(null, Wrappers.<AuthTokenFamilyEntity>lambdaUpdate()
                    .eq(AuthTokenFamilyEntity::getSessionId, ref.sessionId())
                    .eq(AuthTokenFamilyEntity::getRevokedReason, "switch_replaced")
                    .set(AuthTokenFamilyEntity::getRevokedAt, null)
                    .set(AuthTokenFamilyEntity::getRevokedReason, null));
        }
    }

    /**
     * 刷新成功后延长会话和家族有效期。
     *
     * @param sessionId 会话 ID
     * @param familyId  家族 ID
     * @param now       当前时间
     * @param expiresAt 新过期时间
     */
    private void renewSessionAndFamily(String sessionId, String familyId, Instant now, Instant expiresAt) {
        sessionMapper.update(null, Wrappers.<AuthSessionEntity>lambdaUpdate()
                .eq(AuthSessionEntity::getId, sessionId)
                .set(AuthSessionEntity::getLastActiveAt, now)
                .set(AuthSessionEntity::getExpiresAt, expiresAt)
                .set(AuthSessionEntity::getUpdatedAt, now));
        familyMapper.update(null, Wrappers.<AuthTokenFamilyEntity>lambdaUpdate()
                .eq(AuthTokenFamilyEntity::getId, familyId)
                .set(AuthTokenFamilyEntity::getExpiresAt, expiresAt));
    }

    /**
     * 更新会话最近活跃时间。
     *
     * @param sessionId 会话 ID
     * @param now       当前时间
     */
    private void touchSession(String sessionId, Instant now) {
        sessionMapper.update(null, Wrappers.<AuthSessionEntity>lambdaUpdate()
                .eq(AuthSessionEntity::getId, sessionId)
                .set(AuthSessionEntity::getLastActiveAt, now)
                .set(AuthSessionEntity::getUpdatedAt, now));
    }

    /**
     * 吊销会话及其家族。
     *
     * <p>只在字段为空时写入吊销时间和原因，保留首次吊销依据；
     * {@code compromised=true} 时同时记录泄露时间。</p>
     *
     * @param sessionId     会话 ID
     * @param sessionReason 会话吊销原因
     * @param familyReason  家族吊销原因
     * @param now           吊销时间
     * @param compromised   是否标记泄露
     * @return 家族 ID（存在时）
     */
    private Optional<String> revokeSessionAndFamily(String sessionId, String sessionReason,
                                                    String familyReason, Instant now,
                                                    boolean compromised) {
        AuthSessionEntity session = sessionMapper.selectById(sessionId);
        AuthTokenFamilyEntity family = familyMapper.selectOne(
                Wrappers.<AuthTokenFamilyEntity>lambdaQuery()
                        .eq(AuthTokenFamilyEntity::getSessionId, sessionId)
                        .last("LIMIT 1"));
        if (session != null) {
            var update = Wrappers.<AuthSessionEntity>lambdaUpdate()
                    .eq(AuthSessionEntity::getId, sessionId)
                    .set(AuthSessionEntity::getUpdatedAt, now);
            if (session.getRevokedAt() == null) {
                update.set(AuthSessionEntity::getRevokedAt, now);
            }
            if (session.getRevokedReason() == null) {
                update.set(AuthSessionEntity::getRevokedReason, sessionReason);
            }
            sessionMapper.update(null, update);
        }
        if (family != null) {
            var update = Wrappers.<AuthTokenFamilyEntity>lambdaUpdate()
                    .eq(AuthTokenFamilyEntity::getId, family.getId());
            if (family.getRevokedAt() == null) {
                update.set(AuthTokenFamilyEntity::getRevokedAt, now);
            }
            if (family.getRevokedReason() == null) {
                update.set(AuthTokenFamilyEntity::getRevokedReason, familyReason);
            }
            if (compromised && family.getCompromisedAt() == null) {
                update.set(AuthTokenFamilyEntity::getCompromisedAt, now);
            }
            familyMapper.update(null, update);
        }
        return family == null ? Optional.empty() : Optional.of(family.getId());
    }

    /**
     * 查询家族下所有历史令牌摘要。
     *
     * @param familyId 家族 ID
     * @return 摘要列表，查询为空时返回空列表
     */
    private List<String> tokenHashes(String familyId) {
        List<String> hashes = refreshTokenMapper.selectHashesByFamily(familyId);
        return hashes == null ? List.of() : hashes;
    }

    /**
     * 组装登录成功响应。
     *
     * @param user   用户
     * @param tokens 会话令牌
     * @return 会话响应
     */
    private AuthSessionResponse authSession(UserRow user, SessionTokens tokens) {
        Instant now = Instant.now();
        return new AuthSessionResponse(
                tokenService.accessToken(user.id(), tokens.sessionId(), now),
                tokens.token().raw(), "Bearer", tokenService.accessTtlSeconds(),
                tokens.ttl().toSeconds(), tokens.sessionId(), userResponse(user));
    }

    /**
     * 用户行转对外响应。
     *
     * @param user 用户行
     * @return 用户响应
     */
    private UserResponse userResponse(UserRow user) {
        return new UserResponse(user.id(), user.nickname(), user.username(), user.avatarUrl(),
                user.initials(), user.profession(), user.city(), user.bio(),
                user.email(), user.emailVerified(), user.phone(), user.phoneVerified());
    }

    /**
     * 规范化联系方式类型。
     *
     * @param value 原始值
     * @return {@code phone}/{@code email}，非法时返回 {@code null}
     */
    private static String normalizeContactType(String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "phone", "mobile", "sms" -> "phone";
            case "email" -> "email";
            default -> null;
        };
    }

    /**
     * 取第一个非空白字符串，用于兼容新旧字段。
     *
     * @param first  首选值
     * @param second 备用值
     * @return 去空白后的值；都为空时返回 {@code null}
     */
    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second == null || second.isBlank() ? null : second.trim();
    }

    /**
     * 生成限流维度值，缺失时归入 unknown 桶。
     *
     * @param value 原始值
     * @return 可用的维度值
     */
    private static String identityKey(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    /**
     * 统一的账号或密码错误异常。
     *
     * @return 401 业务异常
     */
    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "账号或密码错误");
    }

    /**
     * 统一的 Refresh Token 无效异常。
     *
     * @return 401 业务异常
     */
    private static ApiException invalidRefreshToken() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN", "Refresh Token 无效或已撤销");
    }

    /**
     * 并发刷新冲突异常。
     *
     * @return 409 业务异常
     */
    private static ApiException refreshInProgress() {
        return new ApiException(HttpStatus.CONFLICT, "REFRESH_IN_PROGRESS", "刷新请求正在处理中，请稍后重试");
    }

    /**
     * 新会话的令牌集合。
     *
     * @param sessionId 会话 ID
     * @param familyId  家族 ID
     * @param token     新 Refresh Token
     * @param ttl       Refresh Token 有效期
     */
    private record SessionTokens(String sessionId, String familyId, TokenBundle token, Duration ttl) {
    }
}
