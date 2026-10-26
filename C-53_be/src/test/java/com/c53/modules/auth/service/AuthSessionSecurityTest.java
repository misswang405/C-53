package com.c53.modules.auth.service;

import com.c53.common.ApiException;
import com.c53.common.presence.OnlinePresenceRecorder;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.entity.AuthSessionEntity;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.AuthSessionMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.service.impl.AuthSessionGuardImpl;
import com.c53.security.AuthPrincipal;
import com.c53.security.JwtAuthenticationFilter;
import com.c53.security.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuthSessionSecurityTest {
    private final AuthSessionMapper sessions = mock(AuthSessionMapper.class);
    private final UserMapper users = mock(UserMapper.class);
    private final OnlinePresenceRecorder presence = mock(OnlinePresenceRecorder.class);
    private final FilterChain chain = mock(FilterChain.class);
    private final JwtService jwt = new JwtService(new AuthProperties());
    private final AuthSessionGuard guard = new AuthSessionGuardImpl(sessions, users);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, presence, guard, new ObjectMapper());
    private AuthSessionEntity session;
    private UserEntity user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        session = new AuthSessionEntity();
        session.setId("ses_1");
        session.setUserId("usr_1");
        session.setExpiresAt(Instant.now().plusSeconds(300));
        user = new UserEntity();
        user.setId("usr_1");
        user.setStatus("ACTIVE");
        when(sessions.selectById("ses_1")).thenReturn(session);
        when(users.selectById("usr_1")).thenReturn(user);
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void guardRejectsRevokedSessionBeforeLoadingUser() {
        session.setRevokedAt(Instant.now());
        assertGuardError("SESSION_REVOKED");
        verifyNoInteractions(users);
    }

    @Test
    void guardRejectsDeletedUserEvenWithUnexpiredSession() {
        user.setDeletedAt(Instant.now());
        assertGuardError("ACCOUNT_DISABLED");
    }

    @Test
    void guardRejectsDisabledUser() {
        user.setStatus("DISABLED");
        assertGuardError("ACCOUNT_DISABLED");
    }

    @Test
    void guardRejectsMissingSessionAndMismatchedOwner() {
        when(sessions.selectById("ses_1")).thenReturn(null, session);
        assertGuardError("SESSION_REVOKED");
        session.setUserId("usr_other");
        assertGuardError("SESSION_REVOKED");
        verifyNoInteractions(users);
    }

    @Test
    void guardRejectsExpiredAndMissingExpiry() {
        session.setExpiresAt(Instant.now().minusSeconds(1));
        assertGuardError("SESSION_EXPIRED");
        session.setExpiresAt(null);
        assertGuardError("SESSION_EXPIRED");
    }

    @Test
    void filterAuthenticatesOnlyAfterSessionGuardAccepts() throws Exception {
        MockHttpServletRequest request = request(jwt.createAccessToken("usr_1", "ses_1", Instant.now()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getPrincipal())
                .isEqualTo(new AuthPrincipal("usr_1", "ses_1"));
        verify(presence).touch("usr_1");
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterRejectsRevokedSessionDespiteCryptographicallyValidJwt() throws Exception {
        session.setRevokedAt(Instant.now());
        MockHttpServletRequest request = request(jwt.createAccessToken("usr_1", "ses_1", Instant.now()));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE)).isEqualTo("SESSION_REVOKED");
        verifyNoInteractions(presence);
        verify(chain).doFilter(request, response);
    }

    @Test
    void filterRejectsDeletedUserDespiteCryptographicallyValidJwt() throws Exception {
        user.setDeletedAt(Instant.now());
        MockHttpServletRequest request = request(jwt.createAccessToken("usr_1", "ses_1", Instant.now()));
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(request.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE)).isEqualTo("ACCOUNT_DISABLED");
        verifyNoInteractions(presence);
    }

    @Test
    void filterDatabaseFailureReturns503WithoutContinuingOrAuthenticating() throws Exception {
        when(sessions.selectById("ses_1")).thenThrow(new DataAccessResourceFailureException("database down"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(jwt.createAccessToken("usr_1", "ses_1", Instant.now())), response, chain);
        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(new ObjectMapper().readTree(response.getContentAsString()).get("code").asText())
                .isEqualTo("DEPENDENCY_UNAVAILABLE");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(chain, presence);
    }

    @Test
    void filterExpiredAndInvalidJwtNeverReachGuard() throws Exception {
        MockHttpServletRequest expired = request(jwt.createAccessToken("usr_1", "ses_1", Instant.now().minusSeconds(86400)));
        filter.doFilter(expired, new MockHttpServletResponse(), chain);
        assertThat(expired.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE)).isEqualTo("TOKEN_EXPIRED");
        MockHttpServletRequest invalid = request("not-a-jwt");
        filter.doFilter(invalid, new MockHttpServletResponse(), chain);
        assertThat(invalid.getAttribute(JwtAuthenticationFilter.TOKEN_ERROR_ATTRIBUTE)).isEqualTo("INVALID_ACCESS_TOKEN");
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verifyNoInteractions(sessions, users, presence);
        verify(chain, times(2)).doFilter(any(), any());
    }

    private MockHttpServletRequest request(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/test");
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        return request;
    }

    private void assertGuardError(String code) {
        assertThatThrownBy(() -> guard.requireActive("usr_1", "ses_1"))
                .isInstanceOfSatisfying(ApiException.class,
                        error -> assertThat(error.getCode()).isEqualTo(code));
    }
}
