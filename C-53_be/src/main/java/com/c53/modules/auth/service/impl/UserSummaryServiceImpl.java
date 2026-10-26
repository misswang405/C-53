package com.c53.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.service.UserSummaryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户公开信息查询实现。
 *
 * <p>只读取 {@code users} 表的展示字段，不返回敏感信息。</p>
 */
@Service
public class UserSummaryServiceImpl implements UserSummaryService {

    /** 正常用户状态。 */
    private static final String ACTIVE = "ACTIVE";

    private final UserMapper userMapper;

    public UserSummaryServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public Map<String, UserSummary> findByIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserEntity> users = userMapper.selectBatchIds(userIds);
        Map<String, UserSummary> result = new HashMap<>(users.size());
        for (UserEntity user : users) {
            result.put(user.getId(), new UserSummary(
                    user.getId(), user.getNickname(), user.getUsername(),
                    user.getInitials(), user.getAvatarUrl(), user.getProfession()));
        }
        return result;
    }

    @Override
    public Set<String> findActiveUserIds(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Set.of();
        }
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .in(UserEntity::getId, userIds)
                        .eq(UserEntity::getStatus, ACTIVE)
                        .isNull(UserEntity::getDeletedAt))
                .stream()
                .map(UserEntity::getId)
                .collect(Collectors.toSet());
    }

    @Override
    public Map<String, UserScoringProfile> findScoringProfiles(Collection<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UserEntity> users = userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                .in(UserEntity::getId, userIds)
                .eq(UserEntity::getStatus, ACTIVE)
                .isNull(UserEntity::getDeletedAt));
        Map<String, UserScoringProfile> result = new HashMap<>(users.size());
        for (UserEntity user : users) {
            result.put(user.getId(), new UserScoringProfile(
                    user.getId(), user.getAvatarUrl(), user.getProfession(), user.getBio()));
        }
        return result;
    }

    @Override
    public List<CreatorProfile> findActiveCandidates(int limit) {
        int size = Math.max(1, limit);
        return userMapper.selectList(Wrappers.<UserEntity>lambdaQuery()
                        .eq(UserEntity::getStatus, ACTIVE)
                        .isNull(UserEntity::getDeletedAt)
                        .orderByDesc(UserEntity::getUpdatedAt)
                        .last("LIMIT " + size))
                .stream()
                .map(user -> new CreatorProfile(
                        user.getId(), user.getNickname(), user.getUsername(), user.getInitials(),
                        user.getAvatarUrl(), user.getProfession(), user.getBio()))
                .toList();
    }
}
