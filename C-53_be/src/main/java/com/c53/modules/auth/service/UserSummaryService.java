package com.c53.modules.auth.service;

import java.util.Collection;
import java.util.Map;

/**
 * 用户公开信息查询。
 *
 * <p>供其他业务模块获取作者展示字段，避免跨模块直接访问用户表。
 * 只返回公开字段，不包含密码、联系方式与状态。</p>
 */
public interface UserSummaryService {

    /**
     * 批量查询用户公开信息。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID 到公开信息的映射，未找到的 ID 不出现在结果中
     */
    Map<String, UserSummary> findByIds(Collection<String> userIds);

    /**
     * 用户公开信息。
     *
     * @param id        用户 ID
     * @param nickname  昵称
     * @param username  用户名
     * @param initials  头像占位字符
     * @param avatarUrl 头像地址
     * @param profession 职业
     */
    record UserSummary(String id, String nickname, String username,
                       String initials, String avatarUrl, String profession) {
    }
}
