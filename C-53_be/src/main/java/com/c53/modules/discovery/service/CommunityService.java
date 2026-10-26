package com.c53.modules.discovery.service;

import com.c53.modules.discovery.dto.CommunityDtos.SummaryResponse;

/**
 * 社区概况。
 */
public interface CommunityService {

    /**
     * 查询社区概况。
     *
     * @param clientIp 客户端 IP，用于匿名限流
     * @return 社区概况
     */
    SummaryResponse summary(String clientIp);
}
