package com.c53.modules.discovery.controller;

import com.c53.common.ApiResponse;
import com.c53.common.ClientIp;
import com.c53.modules.discovery.dto.CommunityDtos.SummaryResponse;
import com.c53.modules.discovery.service.CommunityService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 社区概况接口。
 */
@RestController
@RequestMapping("/api/v1")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    /**
     * 首页社区概况，当前只返回在线人数。
     *
     * @param request 当前 HTTP 请求，用于限流
     * @return 社区概况
     */
    @GetMapping("/community/summary")
    public ApiResponse<SummaryResponse> summary(HttpServletRequest request) {
        return ApiResponse.ok(communityService.summary(ClientIp.resolve(request)));
    }
}
