package com.c53.modules.discovery.dto;

/**
 * 社区概况接口模型。
 */
public final class CommunityDtos {

    private CommunityDtos() {
    }

    /**
     * 社区概况。
     *
     * @param onlineCount 当前在线人数
     */
    public record SummaryResponse(long onlineCount) {
    }
}
