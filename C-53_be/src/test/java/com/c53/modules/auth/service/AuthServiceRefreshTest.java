package com.c53.modules.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.c53.common.ApiException;
import com.c53.common.DependencyUnavailableException;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.dto.AuthDtos.LoginRequest;
import com.c53.modules.auth.dto.AuthDtos.RefreshRequest;
import com.c53.modules.auth.dto.AuthDtos.RefreshResponse;
import com.c53.modules.auth.entity.AuthSessionEntity;
import com.c53.modules.auth.entity.AuthTokenFamilyEntity;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.AuthDeviceAccountMapper;
import com.c53.modules.auth.mapper.AuthLoginAttemptMapper;
import com.c53.modules.auth.mapper.AuthRefreshTokenMapper;
import com.c53.modules.auth.mapper.AuthSessionMapper;
import com.c53.modules.auth.mapper.AuthTokenFamilyMapper;
import com.c53.modules.auth.mapper.AuthUserAgreementMapper;
import com.c53.modules.auth.mapper.UserContactMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.model.AuthModels.DeviceResolution;
import com.c53.modules.auth.model.AuthModels.RefreshTokenMeta;
import com.c53.modules.auth.model.AuthModels.TokenBundle;
import com.c53.modules.auth.model.AuthModels.UsedMarker;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.service.AuthService.RequestMeta;
import com.c53.modules.auth.service.impl.AuthServiceImpl;
import com.c53.modules.auth.service.impl.TokenServiceImpl;
import com.c53.modules.auth.support.HmacService;
import com.c53.security.AuthPrincipal;
import com.c53.security.JwtService;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link AuthServiceImpl} 刷新令牌与登录失败回滚路径的单元测试。
 *
 * <p>使用 Mockito 模拟所有 Mapper 和 Redis，只保留真实的令牌/HMAC 实现，
 * 重点验证：
 * <ul>
 *   <li>正常刷新会轮换令牌并延长会话</li>
 *   <li>并发宽限期内返回 409，而不是误判为重放</li>
 *   <li>宽限期外的旧令牌重放会吊销会话和家族</li>
 *   <li>Redis 不可用时快速失败，不继续查 MySQL</li>
 *   <li>退出登录同时吊销数据库和 Redis</li>
 *   <li>创建会话时 Redis 写失败会回滚新会话</li>
 * </ul></p>
 */
class AuthServiceRefreshTest {

    // 数据访问层全部使用 Mock，避免测试依赖真实数据库
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserContactMapper userContactMapper = mock(UserContactMapper.class);
    private final AuthSessionMapper sessionMapper = mock(AuthSessionMapper.class);
    private final AuthTokenFamilyMapper familyMapper = mock(AuthTokenFamilyMapper.class);
    private final AuthRefreshTokenMapper refreshTokenMapper = mock(AuthRefreshTokenMapper.class);
    private final AuthLoginAttemptMapper loginAttemptMapper = mock(AuthLoginAttemptMapper.class);
    private final AuthUserAgreementMapper agreementMapper = mock(AuthUserAgreementMapper.class);
    private final AuthDeviceAccountMapper deviceAccountMapper = mock(AuthDeviceAccountMapper.class);
    private final VerificationService verificationService = mock(VerificationService.class);
    private final DeviceService deviceService = mock(DeviceService.class);
    private final RefreshTokenStore refreshTokenStore = mock(RefreshTokenStore.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);

    // 配置、HMAC、JWT 和令牌服务保留真实实现，覆盖真实的摘要/签发逻辑
    private final AuthProperties properties = new AuthProperties();
    private final HmacService hmacService = new HmacService(properties);
    private final JwtService jwtService = new JwtService(properties);
    private final TokenService tokenService = new TokenServiceImpl(jwtService, hmacService, properties);

    private AuthService authService;

    /**
     * 纯单元测试不会启动 MyBatis，需手动注册实体元数据；
     * 否则 {@code lambdaUpdate().set(...)} 会因缺少 lambda 缓存而报错。
     */
    @BeforeAll
    static void initMybatisPlusTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuthSessionEntity.class);
        TableInfoHelper.initTableInfo(assistant, AuthTokenFamilyEntity.class);
    }

    /**
     * 让被 mock 的事务模板直接同步执行回调，测试中无需真实事务管理器。
     */
    @BeforeEach
    void setUp() {
        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(transactionStatus);
        });
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(transactionStatus);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        authService = new AuthServiceImpl(userMapper, userContactMapper, sessionMapper, familyMapper,
                refreshTokenMapper, loginAttemptMapper, agreementMapper, deviceAccountMapper,
                verificationService, deviceService, refreshTokenStore, tokenService, hmacService,
                passwordEncoder, properties, transactionTemplate);
    }

    /**
     * 正常刷新：Redis 轮换成功 → 延长会话/家族 → 返回新的 Access/Refresh Token。
     */
    @Test
    void refreshRotatesTokenAndRenewsSession() {
        Instant now = Instant.now();
        TokenBundle oldToken = tokenService.newRefreshToken(now.minusSeconds(10), now.plus(Duration.ofDays(7)));
        RefreshTokenMeta oldMeta = meta(oldToken, now.plus(Duration.ofDays(7)));
        when(refreshTokenStore.getToken(oldToken.hash())).thenReturn(Optional.of(oldMeta));
        when(refreshTokenStore.isFamilyRevoked("fam_1")).thenReturn(false);
        when(refreshTokenStore.current("fam_1")).thenReturn(Optional.of(oldToken.hash()));
        when(sessionMapper.selectById("ses_1")).thenReturn(sessionEntity(now.plus(Duration.ofDays(7))));
        when(userMapper.selectById("usr_1")).thenReturn(userEntity());
        when(userContactMapper.selectList(any())).thenReturn(List.of());
        when(refreshTokenStore.incrementCounter(anyString(), anyString(), any())).thenReturn(1L);
        when(refreshTokenStore.rotate(any(), eq(oldToken.hash()), anyString(), any(), any(), any()))
                .thenReturn(1);

        RefreshResponse response = authService.refresh(new RefreshRequest(oldToken.raw()), requestMeta());

        assertThat(response.accessToken()).isNotBlank();
        // 新 Refresh Token 必须与旧的不同，体现轮换语义
        assertThat(response.refreshToken()).startsWith("rt_v1_").isNotEqualTo(oldToken.raw());
        assertThat(response.refreshExpiresIn()).isEqualTo(Duration.ofDays(7).toSeconds());
        // 会话与家族的有效期都要被延长，并写入签发审计
        verify(sessionMapper).update(isNull(), any());
        verify(familyMapper).update(isNull(), any());
        verify(refreshTokenMapper).insert(any());
    }

    /**
     * 并发刷新：旧令牌已被另一个请求消费，但仍在宽限期内 → 409，不吊销家族。
     */
    @Test
    void concurrentRefreshWithinGraceReturnsConflictInsteadOfRevokingFamily() {
        Instant now = Instant.now();
        TokenBundle oldToken = tokenService.newRefreshToken(now.minusSeconds(10), now.plus(Duration.ofDays(7)));
        when(refreshTokenStore.getToken(oldToken.hash())).thenReturn(Optional.empty());
        when(refreshTokenStore.getUsed(oldToken.hash())).thenReturn(Optional.of(
                new UsedMarker("fam_1", "ses_1", "usr_1", now.toEpochMilli())));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(oldToken.raw()), requestMeta()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.CONFLICT);
                    assertThat(exception.getCode()).isEqualTo("REFRESH_IN_PROGRESS");
                });
        // 宽限期内的并发不应被当成攻击，因此不能吊销家族
        verify(refreshTokenStore, never()).revokeFamily(anyString(), anyString(), any(), any());
    }

    /**
     * 重放攻击：旧令牌在宽限期外再次使用 → 401，并吊销会话和整个令牌家族。
     */
    @Test
    void reusedTokenAfterGraceRevokesSessionAndTokenFamily() {
        Instant now = Instant.now();
        TokenBundle oldToken = tokenService.newRefreshToken(now.minusSeconds(120),
                now.plus(Duration.ofDays(7)));
        when(refreshTokenStore.getToken(oldToken.hash())).thenReturn(Optional.empty());
        when(refreshTokenStore.getUsed(oldToken.hash())).thenReturn(Optional.of(
                new UsedMarker("fam_1", "ses_1", "usr_1", now.minusSeconds(30).toEpochMilli())));
        when(sessionMapper.selectById("ses_1")).thenReturn(sessionEntity(now.plus(Duration.ofDays(7))));
        when(familyMapper.selectOne(any())).thenReturn(familyEntity(now.plus(Duration.ofDays(7))));
        when(familyMapper.selectById("fam_1")).thenReturn(familyEntity(now.plus(Duration.ofDays(7))));
        when(refreshTokenMapper.selectHashesByFamily("fam_1")).thenReturn(List.of(oldToken.hash()));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(oldToken.raw()), requestMeta()))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getCode()).isEqualTo("INVALID_REFRESH_TOKEN");
                });
        // 会话、家族都要在 MySQL 中吊销一次，并清理 Redis 家族
        verify(sessionMapper).update(isNull(), any());
        verify(familyMapper).update(isNull(), any());
        verify(refreshTokenStore).revokeFamily(eq("fam_1"), eq("reuse_detected"), any(),
                eq(List.of(oldToken.hash())));
    }

    /**
     * Redis 不可用时必须快速失败（503），不能回退到 MySQL 判断令牌有效性。
     */
    @Test
    void redisFailureFailsClosedWithoutCheckingMysql() {
        Instant now = Instant.now();
        TokenBundle oldToken = tokenService.newRefreshToken(now.minusSeconds(10), now.plus(Duration.ofDays(7)));
        when(refreshTokenStore.getToken(oldToken.hash()))
                .thenThrow(new DependencyUnavailableException("redis unavailable"));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest(oldToken.raw()), requestMeta()))
                .isInstanceOfSatisfying(DependencyUnavailableException.class, exception -> {
                    assertThat(exception.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.getCode()).isEqualTo("SERVICE_UNAVAILABLE");
                });
        // 失败关闭：不继续查询用户和会话，避免绕过 Redis 白名单
        verify(userMapper, never()).selectById(anyString());
        verify(sessionMapper, never()).selectById(anyString());
    }

    /**
     * 退出登录：数据库中会话/家族被吊销，Redis 家族同步清理。
     */
    @Test
    void logoutRevokesDatabaseSessionAndRedisFamily() {
        Instant now = Instant.now();
        when(sessionMapper.selectById("ses_1")).thenReturn(sessionEntity(now.plus(Duration.ofDays(7))));
        when(familyMapper.selectOne(any())).thenReturn(familyEntity(now.plus(Duration.ofDays(7))));
        when(familyMapper.selectById("fam_1")).thenReturn(familyEntity(now.plus(Duration.ofDays(7))));
        when(refreshTokenMapper.selectHashesByFamily("fam_1")).thenReturn(List.of("hash_1"));

        authService.logout(new AuthPrincipal("usr_1", "ses_1"), null);

        verify(sessionMapper).update(isNull(), any());
        verify(familyMapper).update(isNull(), any());
        verify(refreshTokenStore).revokeFamily(eq("fam_1"), eq("logout"), any(), eq(List.of("hash_1")));
    }

    /**
     * 登录时 Redis 写失败：新会话必须回滚，且不能把登录当成成功返回。
     */
    @Test
    void loginRedisFailureRevokesNewSessionAndDoesNotReturnSuccess() {
        Instant now = Instant.now();
        when(loginAttemptMapper.selectCount(any())).thenReturn(0L);
        when(userMapper.selectOne(any())).thenReturn(userEntity());
        when(userMapper.selectById("usr_1")).thenReturn(userEntity());
        when(userContactMapper.selectList(any())).thenReturn(List.of());
        when(passwordEncoder.matches("TestPass123!", "hash")).thenReturn(true);
        when(deviceService.resolveOrCreate(any(), any(), any()))
                .thenReturn(new DeviceResolution("dev_1", null));
        when(sessionMapper.selectList(any())).thenReturn(List.of(sessionEntity(now.plus(Duration.ofDays(7)))));
        when(sessionMapper.selectById(anyString())).thenReturn(sessionEntity(now.plus(Duration.ofDays(7))));
        when(familyMapper.selectOne(any())).thenReturn(familyEntity(now.plus(Duration.ofDays(7))));
        doThrow(new DependencyUnavailableException("redis unavailable"))
                .when(refreshTokenStore).writeInitial(any(), anyString(), any(), any());

        assertThatThrownBy(() -> authService.login(new LoginRequest("alice", "TestPass123!",
                false, null, null), requestMeta()))
                .isInstanceOf(DependencyUnavailableException.class);

        // 新会话和家族被吊销，且不会继续执行令牌轮换
        verify(sessionMapper, atLeast(2)).update(isNull(), any());
        verify(familyMapper, atLeast(2)).update(isNull(), any());
        verify(refreshTokenStore, never()).rotate(any(), anyString(), anyString(), any(), any(), any());
    }

    /**
     * 构造与 {@code ses_1/fam_1/usr_1} 对应的刷新令牌元数据。
     */
    private RefreshTokenMeta meta(TokenBundle token, Instant expiresAt) {
        return new RefreshTokenMeta(token.tokenId(), "ses_1", "fam_1", "usr_1",
                token.issuedAt().toEpochMilli(), expiresAt.toEpochMilli());
    }

    /**
     * 构造有效会话实体。
     */
    private AuthSessionEntity sessionEntity(Instant expiresAt) {
        Instant now = Instant.now();
        AuthSessionEntity entity = new AuthSessionEntity();
        entity.setId("ses_1");
        entity.setUserId("usr_1");
        entity.setDeviceId("dev_1");
        entity.setLoginMethod("password");
        entity.setRememberMe(true);
        entity.setCreatedAt(now.minusSeconds(60));
        entity.setLastActiveAt(now);
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    /**
     * 构造令牌家族实体。
     */
    private AuthTokenFamilyEntity familyEntity(Instant expiresAt) {
        AuthTokenFamilyEntity entity = new AuthTokenFamilyEntity();
        entity.setId("fam_1");
        entity.setSessionId("ses_1");
        entity.setExpiresAt(expiresAt);
        return entity;
    }

    /**
     * 构造状态正常、密码算法为 bcrypt 的用户实体。
     */
    private UserEntity userEntity() {
        UserEntity entity = new UserEntity();
        entity.setId("usr_1");
        entity.setUsername("alice");
        entity.setNickname("Alice");
        entity.setInitials("A");
        entity.setPasswordHash("hash");
        entity.setPasswordAlgorithm("bcrypt");
        entity.setStatus("ACTIVE");
        return entity;
    }

    /**
     * 构造测试用请求上下文。
     */
    private RequestMeta requestMeta() {
        return new RequestMeta("req_test", "127.0.0.1", "JUnit", null);
    }
}
