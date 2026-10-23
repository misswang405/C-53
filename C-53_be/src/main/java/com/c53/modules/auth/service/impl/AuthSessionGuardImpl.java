package com.c53.modules.auth.service.impl;

import com.c53.common.ApiException;
import com.c53.modules.auth.entity.AuthSessionEntity;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.AuthSessionMapper;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.service.AuthSessionGuard;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * 登录会话有效性校验实现。
 *
 * <p>依次检查会话存在、归属一致、未撤销、未过期，以及用户状态为 ACTIVE。</p>
 */
@Service
public class AuthSessionGuardImpl implements AuthSessionGuard {

    private final AuthSessionMapper sessionMapper;
    private final UserMapper userMapper;

    public AuthSessionGuardImpl(AuthSessionMapper sessionMapper, UserMapper userMapper) {
        this.sessionMapper = sessionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public void requireActive(String userId, String sessionId) {
        if (userId == null || sessionId == null) {
            throw ApiException.unauthorized("UNAUTHORIZED", "请先登录");
        }
        AuthSessionEntity session = sessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw ApiException.unauthorized("SESSION_REVOKED", "登录状态已失效，请重新登录");
        }
        if (session.getRevokedAt() != null) {
            throw ApiException.unauthorized("SESSION_REVOKED", "登录状态已失效，请重新登录");
        }
        if (session.getExpiresAt() == null || session.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.unauthorized("SESSION_EXPIRED", "登录状态已过期，请重新登录");
        }
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !"ACTIVE".equalsIgnoreCase(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "ACCOUNT_DISABLED", "账号不可用");
        }
    }
}
