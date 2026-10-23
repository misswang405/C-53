package com.c53.modules.auth.service.impl;

import com.c53.modules.auth.entity.UserEntity;
import com.c53.modules.auth.mapper.UserMapper;
import com.c53.modules.auth.service.UserSummaryService;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 用户公开信息查询实现。
 *
 * <p>只读取 {@code users} 表的展示字段，不返回敏感信息。</p>
 */
@Service
public class UserSummaryServiceImpl implements UserSummaryService {

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
}
