package com.c53.modules.discovery.dto;

import java.util.List;

/**
 * 推荐创作者接口模型。
 */
public final class CreatorDtos {

    private CreatorDtos() {
    }

    /**
     * 推荐创作者。
     *
     * @param id           用户 ID
     * @param nickname     昵称
     * @param username     用户名
     * @param avatarUrl    头像地址
     * @param initials     头像占位字符
     * @param profession   职业
     * @param relationship 与当前用户的关系
     */
    public record CreatorSummary(
            String id,
            String nickname,
            String username,
            String avatarUrl,
            String initials,
            String profession,
            String relationship) {
    }

    /**
     * 推荐创作者列表。
     *
     * @param items 创作者列表
     */
    public record CreatorListResponse(List<CreatorSummary> items) {
    }
}
