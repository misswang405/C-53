package com.c53.modules.auth.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
     * 过滤出状态为 ACTIVE 的用户 ID。
     *
     * <p>信息流用于剔除被禁用或已删除作者的内容，避免在动态查询中跨表 join。</p>
     *
     * @param userIds 待过滤的用户 ID
     * @return 有效用户 ID 集合
     */
    Set<String> findActiveUserIds(Collection<String> userIds);

    /**
     * 批量查询推荐打分所需的资料字段。
     *
     * @param userIds 用户 ID 集合
     * @return 用户 ID 到资料摘要的映射
     */
    Map<String, UserScoringProfile> findScoringProfiles(Collection<String> userIds);

    /**
     * 查询推荐创作者候选。
     *
     * @param limit 最大条数
     * @return 有效的用户资料，按最近更新时间倒序
     */
    List<CreatorProfile> findActiveCandidates(int limit);

    /**
     * 用户公开信息。
     *
     * @param id         用户 ID
     * @param nickname   昵称
     * @param username   用户名
     * @param initials   头像占位字符
     * @param avatarUrl  头像地址
     * @param profession 职业
     */
    record UserSummary(String id, String nickname, String username,
                       String initials, String avatarUrl, String profession) {
    }

    /**
     * 推荐打分使用的用户资料。
     *
     * @param id         用户 ID
     * @param avatarUrl  头像地址
     * @param profession 职业
     * @param bio        个人简介
     */
    record UserScoringProfile(String id, String avatarUrl, String profession, String bio) {
    }

    /**
     * 推荐创作者候选。
     *
     * @param id         用户 ID
     * @param nickname   昵称
     * @param username   用户名
     * @param initials   头像占位字符
     * @param avatarUrl  头像地址
     * @param profession 职业
     * @param bio        个人简介
     */
    record CreatorProfile(String id, String nickname, String username, String initials,
                          String avatarUrl, String profession, String bio) {
    }
}
