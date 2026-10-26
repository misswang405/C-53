package com.c53.modules.discovery.service;

import com.c53.modules.discovery.dto.CreatorDtos.CreatorListResponse;
import com.c53.security.AuthPrincipal;

/**
 * 推荐创作者。
 */
public interface CreatorService {

    /**
     * 查询推荐创作者。
     *
     * @param principal 当前用户，游客为 null
     * @param clientIp  客户端 IP，用于匿名限流
     * @return 推荐创作者列表
     */
    CreatorListResponse recommended(AuthPrincipal principal, String clientIp);
}
