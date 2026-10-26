package com.c53.modules.auth.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.c53.common.ApiException;
import com.c53.common.DependencyUnavailableException;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.dto.AuthDtos.VerificationCodeResponse;
import com.c53.modules.auth.entity.AuthVerificationChallengeEntity;
import com.c53.modules.auth.entity.UserContactEntity;
import com.c53.modules.auth.mapper.AuthVerificationChallengeMapper;
import com.c53.modules.auth.mapper.UserContactMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.service.impl.VerificationServiceImpl;
import com.c53.modules.auth.support.HmacService;
import com.c53.modules.auth.support.VerificationException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.env.MockEnvironment;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/** 只验证业务分支和 Mapper 调用，不模拟数据库行锁/事务隔离保证。 */
class VerificationServiceTest {
    private final AuthVerificationChallengeMapper challengeMapper = mock(AuthVerificationChallengeMapper.class);
    private final UserContactMapper contactMapper = mock(UserContactMapper.class);
    private final UserMapper userMapper = mock(UserMapper.class);
    private final VerificationSender sender = mock(VerificationSender.class);
    private final RefreshTokenStore store = mock(RefreshTokenStore.class);
    private final AuthProperties properties = new AuthProperties();
    private final HmacService hmac = new HmacService(properties);
    private final MockEnvironment environment = new MockEnvironment();
    private final Instant now = Instant.now();
    private VerificationService service;

    @BeforeAll
    static void initTables() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, AuthVerificationChallengeEntity.class);
        TableInfoHelper.initTableInfo(assistant, UserContactEntity.class);
    }

    @BeforeEach
    void setUp() {
        service = new VerificationServiceImpl(challengeMapper, contactMapper, userMapper, sender,
                hmac, properties, store, environment);
        when(store.incrementCounter(any(), any(), any())).thenReturn(1L);
        when(sender.send(any(), any(), any())).thenReturn(new VerificationSender.SendResult("test", "msg_1"));
    }

    @Test
    void resetSendUnknownAndKnownTargetsHaveSameResponseShapeAndRateBudgets() {
        UserContactEntity contact = new UserContactEntity();
        contact.setUserId("usr_1");
        when(contactMapper.selectOne(any())).thenReturn(null, contact);

        VerificationCodeResponse unknown = sendReset();
        VerificationCodeResponse known = sendReset();

        assertThat(unknown.verificationId()).isNotBlank().isNotEqualTo(known.verificationId());
        assertThat(unknown).usingRecursiveComparison().ignoringFields("verificationId").isEqualTo(known);
        verify(store, times(2)).incrementCounter(eq("reset-send:ip"),
                eq(hmac.hashAccount("ip:127.0.0.1")), eq(Duration.ofMinutes(1)));
        verify(store, times(2)).incrementCounter(eq("reset-send:target"),
                eq(hmac.hashAccount("email:alice@example.com")), eq(Duration.ofHours(1)));
        verify(store, times(2)).incrementCounter(eq("reset-send:ip-day"),
                eq(hmac.hashAccount("ip:127.0.0.1")), eq(Duration.ofDays(1)));
        verify(store, times(2)).incrementCounter(eq("reset-send:target-day"),
                eq(hmac.hashAccount("email:alice@example.com")), eq(Duration.ofDays(1)));
        verify(store, times(2)).incrementCounter(eq("reset-send:cooldown"),
                eq(hmac.hashAccount("email:alice@example.com")), eq(properties.getVerification().getResendInterval()));
        ArgumentCaptor<AuthVerificationChallengeEntity> inserts = ArgumentCaptor.forClass(AuthVerificationChallengeEntity.class);
        verify(challengeMapper, times(2)).insert(inserts.capture());
        assertThat(inserts.getAllValues()).extracting(AuthVerificationChallengeEntity::getUserId)
                .containsExactly(null, "usr_1");
        verify(sender, times(2)).send(eq("email"), eq("alice@example.com"), anyString());
    }

    @Test
    void resetSendRedisFailureFailsClosedBeforeLookingUpTargetOrSending() {
        when(store.incrementCounter(any(), any(), any()))
                .thenThrow(new DependencyUnavailableException("redis unavailable"));
        assertThatThrownBy(this::sendReset).isInstanceOf(DependencyUnavailableException.class);
        verifyNoInteractions(contactMapper, sender, challengeMapper);
    }

    @Test
    void resetSendCooldownRejectsBeforeSending() {
        when(store.incrementCounter(eq("reset-send:cooldown"), any(), any())).thenReturn(2L);
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("RATE_LIMITED"));
        verifyNoInteractions(sender, challengeMapper, contactMapper);
    }

    @Test
    void resetSendDailyIpBudgetRejectsBeforeTargetLookupOrSending() {
        when(store.incrementCounter(eq("reset-send:ip-day"), any(), any()))
                .thenReturn((long) properties.getLimits().getResetSendPerIpPerDay() + 1);
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("RATE_LIMITED"));
        verifyNoInteractions(sender, challengeMapper, contactMapper);
    }

    @Test
    void resetSendDailyTargetBudgetRejectsBeforeTargetLookupOrSending() {
        when(store.incrementCounter(eq("reset-send:target-day"), any(), any()))
                .thenReturn((long) properties.getLimits().getResetSendPerTargetPerDay() + 1);
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("RATE_LIMITED"));
        verifyNoInteractions(sender, challengeMapper, contactMapper);
    }

    @Test
    void resetSendDevModeIsRejectedInProduction() {
        properties.getVerification().setDevMode(true);
        environment.setActiveProfiles("production");
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("VERIFICATION_CONFIG_ERROR"));
        verifyNoInteractions(store, sender, challengeMapper);
    }

    @Test
    void resetSendDevModeRequiresExplicitDevOrTestProfile() {
        properties.getVerification().setDevMode(true);
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("VERIFICATION_CONFIG_ERROR"));
        verifyNoInteractions(store, sender, challengeMapper);
    }

    @Test
    void resetSendDevModeAllowsExplicitTestProfile() {
        properties.getVerification().setDevMode(true);
        environment.setActiveProfiles("test");
        assertThat(sendReset().verificationId()).isNotBlank();
        verify(sender).send("email", "alice@example.com", properties.getVerification().getDevCode());
    }

    @Test
    void resetSendProductionProfileOverridesDevProfile() {
        properties.getVerification().setDevMode(true);
        environment.setActiveProfiles("dev", "prod");
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("VERIFICATION_CONFIG_ERROR"));
        verifyNoInteractions(store, sender, challengeMapper);
    }

    @Test
    void verifyBindsTargetChannelAndSceneBeforeConsuming() {
        when(challengeMapper.selectOne(any())).thenReturn(challenge("ACTIVE", 0));
        for (List<String> expected : List.of(
                List.of("login", "email", "alice@example.com"),
                List.of("reset_password", "sms", "alice@example.com"),
                List.of("reset_password", "email", "other@example.com"))) {
            assertThatThrownBy(() -> service.verifyAndConsume("ver_1", "123456",
                    expected.get(0), expected.get(1), expected.get(2), now))
                    .isInstanceOfSatisfying(VerificationException.class,
                            error -> assertThat(error.getCode()).isEqualTo("VERIFICATION_TARGET_MISMATCH"));
        }
        verify(challengeMapper, never()).update(isNull(), any());
    }

    @Test
    void wrongCodeIncrementsAttemptsAndLeavesChallengeActiveBeforeLimit() {
        when(challengeMapper.selectOne(any())).thenReturn(challenge("ACTIVE", 0));
        assertVerifyError("000000", "INVALID_VERIFICATION_CODE");
        LambdaUpdateWrapper<AuthVerificationChallengeEntity> update = captureUpdate();
        assertThat(update.getSqlSet()).contains("attempts", "last_attempt_at", "status");
        assertThat(update.getParamNameValuePairs().values()).contains(1, "ACTIVE");
    }

    @Test
    void wrongCodeAtLimitMarksChallengeFailed() {
        when(challengeMapper.selectOne(any())).thenReturn(challenge("ACTIVE", 4));
        assertVerifyError("000000", "VERIFICATION_ATTEMPTS_EXCEEDED");
        assertThat(captureUpdate().getParamNameValuePairs().values()).contains(5, "FAILED");
    }

    @Test
    void pendingConsumedInvalidatedAndFailedChallengesCannotBeConsumed() {
        for (String status : List.of("PENDING", "CONSUMED", "INVALIDATED", "FAILED")) {
            when(challengeMapper.selectOne(any())).thenReturn(challenge(status, 0));
            assertVerifyError("123456", "FAILED".equals(status)
                    ? "VERIFICATION_ATTEMPTS_EXCEEDED" : "INVALID_VERIFICATION_CODE");
        }
        verify(challengeMapper, never()).update(isNull(), any());
    }

    @Test
    void expiredChallengeIsMarkedFailedWithoutConsumption() {
        AuthVerificationChallengeEntity expired = challenge("ACTIVE", 0);
        expired.setExpiresAt(now);
        when(challengeMapper.selectOne(any())).thenReturn(expired);
        assertVerifyError("123456", "VERIFICATION_CODE_EXPIRED");
        assertThat(captureUpdate().getParamNameValuePairs().values()).contains("FAILED", "EXPIRED");
    }

    @Test
    void validCodeRequiresConditionalConsumptionToAffectExactlyOneRow() {
        when(challengeMapper.selectOne(any())).thenReturn(challenge("ACTIVE", 0));
        when(challengeMapper.update(isNull(), any())).thenReturn(1);
        assertThat(service.verifyAndConsume("ver_1", "123456", "reset_password", "email",
                "alice@example.com", now).id()).isEqualTo("ver_1");
        LambdaUpdateWrapper<AuthVerificationChallengeEntity> update = captureUpdate();
        assertThat(update.getSqlSegment()).contains("status");
        assertThat(update.getParamNameValuePairs().values()).contains("ACTIVE", "CONSUMED");
    }

    @Test
    void lostConditionalConsumptionIsRejected() {
        when(challengeMapper.selectOne(any())).thenReturn(challenge("ACTIVE", 0));
        when(challengeMapper.update(isNull(), any())).thenReturn(0);
        assertVerifyError("123456", "INVALID_VERIFICATION_CODE");
    }

    @Test
    void senderFailureMarksChallengeFailed() {
        when(sender.send(any(), any(), any())).thenThrow(new IllegalStateException("gateway down"));
        assertThatThrownBy(this::sendReset).isInstanceOfSatisfying(ApiException.class,
                error -> assertThat(error.getCode()).isEqualTo("VERIFICATION_SEND_FAILED"));
        ArgumentCaptor<LambdaUpdateWrapper<AuthVerificationChallengeEntity>> updates = wrapperCaptor();
        verify(challengeMapper, times(2)).update(isNull(), updates.capture());
        assertThat(updates.getAllValues().get(1).getParamNameValuePairs().values())
                .contains("FAILED", "SEND_ERROR");
    }

    private VerificationCodeResponse sendReset() {
        return service.send("reset_password", "email", " Alice@Example.com ",
                null, "req_test", "127.0.0.1", "JUnit");
    }

    private void assertVerifyError(String code, String errorCode) {
        assertThatThrownBy(() -> service.verifyAndConsume("ver_1", code,
                "reset_password", "email", "alice@example.com", now))
                .isInstanceOfSatisfying(VerificationException.class,
                        error -> assertThat(error.getCode()).isEqualTo(errorCode));
    }

    private AuthVerificationChallengeEntity challenge(String status, int attempts) {
        AuthVerificationChallengeEntity entity = new AuthVerificationChallengeEntity();
        entity.setId("ver_1");
        entity.setUserId("usr_1");
        entity.setScene("reset_password");
        entity.setChannel("email");
        entity.setTarget("alice@example.com");
        entity.setCodeHash(hmac.hashVerificationCode("ver_1", "123456"));
        entity.setStatus(status);
        entity.setAttempts(attempts);
        entity.setMaxAttempts(5);
        entity.setResendAfterSeconds(60);
        entity.setCreatedAt(now.minusSeconds(60));
        entity.setExpiresAt(now.plusSeconds(300));
        return entity;
    }

    private LambdaUpdateWrapper<AuthVerificationChallengeEntity> captureUpdate() {
        ArgumentCaptor<LambdaUpdateWrapper<AuthVerificationChallengeEntity>> captor = wrapperCaptor();
        verify(challengeMapper).update(isNull(), captor.capture());
        captor.getValue().getSqlSegment();
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<LambdaUpdateWrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(LambdaUpdateWrapper.class);
    }
}
