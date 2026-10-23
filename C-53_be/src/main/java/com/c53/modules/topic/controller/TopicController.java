package com.c53.modules.topic.controller;

import com.c53.common.ApiResponse;
import com.c53.modules.topic.dto.TopicDtos.TopicSummary;
import com.c53.modules.topic.service.TopicService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 话题只读接口。
 */
@RestController
@RequestMapping("/api/v1/topics")
public class TopicController {

    private final TopicService topicService;

    public TopicController(TopicService topicService) {
        this.topicService = topicService;
    }

    /**
     * 热门话题，供发布页选择。
     *
     * @param limit 最大条数
     * @return 话题列表
     */
    @GetMapping("/trending")
    public ApiResponse<List<TopicSummary>> trending(@RequestParam(defaultValue = "10") int limit) {
        return ApiResponse.ok(topicService.trending(limit));
    }
}
