package com.c53.modules.auth.service;

import com.c53.common.DependencyUnavailableException;
import com.c53.modules.auth.entity.AuthCleanupTaskEntity;
import com.c53.modules.auth.mapper.AuthCleanupTaskMapper;
import com.c53.modules.auth.mapper.AuthRefreshTokenMapper;
import com.c53.modules.auth.redis.RefreshTokenStore;
import com.c53.modules.auth.service.impl.AuthCleanupServiceImpl;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Map;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** 使用固定时钟验证补偿调用；不声明验证了 Spring 事务代理或数据库并发语义。 */
class AuthCleanupServiceTest {
    private final AuthCleanupTaskMapper tasks = mock(AuthCleanupTaskMapper.class);
    private final AuthRefreshTokenMapper tokens = mock(AuthRefreshTokenMapper.class);
    private final RefreshTokenStore store = mock(RefreshTokenStore.class);
    private final Instant now = Instant.parse("2026-10-26T08:00:00Z");
    private final AuthCleanupService service = new AuthCleanupServiceImpl(tasks, tokens, store,
            Clock.fixed(now, ZoneOffset.UTC));

    @Test
    void enqueuePersistsPendingTaskWithImmediateRetry() {
        when(tasks.insert(any(AuthCleanupTaskEntity.class))).thenReturn(1);
        service.enqueue("fam_1", now.plusSeconds(300), "password_changed");
        ArgumentCaptor<AuthCleanupTaskEntity> captor = ArgumentCaptor.forClass(AuthCleanupTaskEntity.class);
        verify(tasks).insert(captor.capture());
        AuthCleanupTaskEntity task = captor.getValue();
        assertThat(task.getId()).startsWith("acl_");
        assertThat(task.getFamilyId()).isEqualTo("fam_1");
        assertThat(task.getExpiresAt()).isEqualTo(now.plusSeconds(300));
        assertThat(task.getReason()).isEqualTo("password_changed");
        assertThat(task.getStatus()).isEqualTo("PENDING");
        assertThat(task.getAttempts()).isZero();
        assertThat(task.getNextRetryAt()).isEqualTo(now);
        assertThat(task.getCreatedAt()).isEqualTo(now);
        assertThat(task.getUpdatedAt()).isEqualTo(now);
        verifyNoInteractions(tokens, store);
    }

    @Test
    void enqueueRejectsUnsuccessfulInsertInsteadOfDroppingCleanupTask() {
        when(tasks.insert(any(AuthCleanupTaskEntity.class))).thenReturn(0);
        assertThatThrownBy(() -> service.enqueue("fam_1", now.plusSeconds(300), "password_changed"))
                .isInstanceOf(IllegalStateException.class).hasMessage("auth cleanup task was not inserted");
    }

    @Test
    void enqueueDatabaseFailurePropagatesToCaller() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("database down");
        when(tasks.insert(any(AuthCleanupTaskEntity.class))).thenThrow(failure);
        assertThatThrownBy(() -> service.enqueue("fam_1", now.plusSeconds(300), "password_changed"))
                .isSameAs(failure);
        verifyNoInteractions(store);
    }

    @Test
    void retryPassesAuditHashesAndCompletesOnlyAfterRedisRevocation() {
        AuthCleanupTaskEntity task = task("acl_1", "fam_1", now.plusSeconds(300), 2);
        List<String> hashes = List.of("audit_hash_1", "audit_hash_2");
        when(tasks.selectPending(now, 100)).thenReturn(List.of(task));
        when(tokens.selectHashesByFamily("fam_1")).thenReturn(hashes);

        service.retryPending();

        var order = inOrder(tokens, store, tasks);
        order.verify(tokens).selectHashesByFamily("fam_1");
        order.verify(store).revokeFamily("fam_1", "password_changed", Duration.ofSeconds(300), hashes);
        order.verify(tasks).markCompleted("acl_1", now);
        verify(tasks, never()).deferRetry(anyString(), anyInt(), any(), any());
    }

    @Test
    void redisFailureDefersPendingTaskAndNeverMarksCompleted() {
        AuthCleanupTaskEntity task = task("acl_1", "fam_1", now.plusSeconds(300), 2);
        when(tasks.selectPending(now, 100)).thenReturn(List.of(task));
        when(tokens.selectHashesByFamily("fam_1")).thenReturn(List.of("audit_hash"));
        doThrow(new DependencyUnavailableException("redis unavailable")).when(store)
                .revokeFamily(anyString(), anyString(), any(), any());

        service.retryPending();

        verify(tasks).deferRetry("acl_1", 2, now.plusSeconds(20), now);
        verify(tasks, never()).markCompleted(anyString(), any());
    }

    @Test
    void retryAfterRedisRecoveryCompletesPreviouslyDeferredTask() {
        AuthCleanupTaskEntity task = task("acl_1", "fam_1", now.plusSeconds(300), 0);
        when(tasks.selectPending(now, 100)).thenReturn(List.of(task));
        when(tokens.selectHashesByFamily("fam_1")).thenReturn(List.of("audit_hash"));
        doThrow(new DependencyUnavailableException("redis unavailable")).doNothing().when(store)
                .revokeFamily(anyString(), anyString(), any(), any());

        service.retryPending();
        service.retryPending();

        verify(tasks).deferRetry("acl_1", 0, now.plusSeconds(5), now);
        verify(tasks).markCompleted("acl_1", now);
        verify(store, times(2)).revokeFamily("fam_1", "password_changed", Duration.ofSeconds(300),
                List.of("audit_hash"));
    }

    @Test
    void expiredAndExactlyExpiredTasksKeepAtLeastOneSecondRevocationMarker() {
        when(tasks.selectPending(now, 100)).thenReturn(List.of(
                task("acl_1", "fam_1", now.minusSeconds(60), 0),
                task("acl_2", "fam_2", now, 0)));
        when(tokens.selectHashesByFamily(anyString())).thenReturn(List.of("audit_hash"));

        service.retryPending();

        verify(store).revokeFamily("fam_1", "password_changed", Duration.ofSeconds(1), List.of("audit_hash"));
        verify(store).revokeFamily("fam_2", "password_changed", Duration.ofSeconds(1), List.of("audit_hash"));
        verify(tasks).markCompleted("acl_1", now);
        verify(tasks).markCompleted("acl_2", now);
    }

    @Test
    void failedDeferDoesNotPreventOtherTasksFromCompleting() {
        when(tasks.selectPending(now, 100)).thenReturn(List.of(
                task("acl_1", "fam_1", now.plusSeconds(300), 0),
                task("acl_2", "fam_2", now.plusSeconds(300), 0)));
        when(tokens.selectHashesByFamily(anyString())).thenReturn(List.of());
        doThrow(new DependencyUnavailableException("redis unavailable")).when(store)
                .revokeFamily("fam_1", "password_changed", Duration.ofSeconds(300), List.of());
        when(tasks.deferRetry("acl_1", 0, now.plusSeconds(5), now))
                .thenThrow(new DataAccessResourceFailureException("database down"));

        service.retryPending();

        verify(tasks).markCompleted("acl_2", now);
        verify(tasks, never()).markCompleted("acl_1", now);
    }

    @Test
    void cleanupMapperXmlBindsAllThreeStatementsAndTheirParameters() throws Exception {
        String resource = "mapper/com/c53/modules/auth/mapper/AuthCleanupTaskMapper.xml";
        String namespace = AuthCleanupTaskMapper.class.getName() + ".";
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        try (InputStream input = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(input, configuration, resource, configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement(namespace + "selectPending")).isTrue();
        assertThat(configuration.hasStatement(namespace + "markCompleted")).isTrue();
        assertThat(configuration.hasStatement(namespace + "deferRetry")).isTrue();
        var pending = configuration.getMappedStatement(namespace + "selectPending");
        assertThat(pending.getResultMaps().get(0).getType()).isEqualTo(AuthCleanupTaskEntity.class);
        var pendingSql = pending.getBoundSql(Map.of("now", now, "limit", 100));
        assertThat(pendingSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("now", "limit");
        assertThat(pendingSql.getSql()).contains("status = 'PENDING'", "next_retry_at <= ?", "LIMIT ?");
        var completedSql = configuration.getMappedStatement(namespace + "markCompleted")
                .getBoundSql(Map.of("id", "acl_1", "now", now));
        assertThat(completedSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("now", "now", "id");
        assertThat(completedSql.getSql()).contains("status = 'COMPLETED'", "status = 'PENDING'");
        var deferredSql = configuration.getMappedStatement(namespace + "deferRetry")
                .getBoundSql(Map.of("id", "acl_1", "attempts", 2,
                        "nextRetryAt", now.plusSeconds(20), "now", now));
        assertThat(deferredSql.getParameterMappings()).extracting(ParameterMapping::getProperty)
                .containsExactly("nextRetryAt", "now", "id", "attempts");
        assertThat(deferredSql.getSql()).contains("attempts = attempts + 1",
                "status = 'PENDING'", "attempts = ?");
    }

    private AuthCleanupTaskEntity task(String id, String familyId, Instant expiresAt, int attempts) {
        AuthCleanupTaskEntity task = new AuthCleanupTaskEntity();
        task.setId(id);
        task.setFamilyId(familyId);
        task.setExpiresAt(expiresAt);
        task.setReason("password_changed");
        task.setStatus("PENDING");
        task.setAttempts(attempts);
        return task;
    }
}
