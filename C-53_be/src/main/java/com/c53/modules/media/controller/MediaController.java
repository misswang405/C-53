package com.c53.modules.media.controller;

import com.c53.common.ApiResponse;
import com.c53.modules.media.dto.MediaDtos.ConfirmRequest;
import com.c53.modules.media.dto.MediaDtos.MediaConfigResponse;
import com.c53.modules.media.dto.MediaDtos.MediaView;
import com.c53.modules.media.dto.MediaDtos.TicketResponse;
import com.c53.modules.media.dto.MediaDtos.UploadTicketRequest;
import com.c53.modules.media.service.MediaService;
import com.c53.security.AuthPrincipal;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 媒体上传接口。
 *
 * <p>小文件走 multipart；视频大文件先申请 STS 凭证，由前端直传 OSS 后确认。</p>
 */
@RestController
@RequestMapping("/api/v1/media")
public class MediaController {

    private final MediaService mediaService;

    public MediaController(MediaService mediaService) {
        this.mediaService = mediaService;
    }

    /**
     * 上传能力配置，公开。
     *
     * @return 配置
     */
    @GetMapping("/config")
    public ApiResponse<MediaConfigResponse> config() {
        return ApiResponse.ok(mediaService.config());
    }

    /**
     * 服务端上传小文件。
     *
     * @param principal 当前用户
     * @param file      文件
     * @param mediaType IMAGE/VIDEO
     * @return 媒体视图
     */
    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<MediaView>> upload(
            @AuthenticationPrincipal AuthPrincipal principal,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "mediaType", defaultValue = "IMAGE") String mediaType) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mediaService.uploadServer(principal, file, mediaType)));
    }

    /**
     * 申请视频直传凭证。
     *
     * @param principal 当前用户
     * @param request   申请参数
     * @return 凭证与临时路径
     */
    @PostMapping("/upload-ticket")
    public ResponseEntity<ApiResponse<TicketResponse>> ticket(
            @AuthenticationPrincipal AuthPrincipal principal,
            @Valid @RequestBody UploadTicketRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(mediaService.issueTicket(principal, request)));
    }

    /**
     * 直传完成确认。
     *
     * @param principal 当前用户
     * @param id        媒体 ID
     * @param request   确认参数
     * @return 媒体视图
     */
    @PostMapping("/{id}/confirm")
    public ApiResponse<MediaView> confirm(@AuthenticationPrincipal AuthPrincipal principal,
                                          @PathVariable String id,
                                          @RequestBody(required = false) ConfirmRequest request) {
        return ApiResponse.ok(mediaService.confirm(principal, id, request));
    }

    /**
     * 查询自己的媒体。
     *
     * @param principal 当前用户
     * @param id        媒体 ID
     * @return 媒体视图
     */
    @GetMapping("/{id}")
    public ApiResponse<MediaView> get(@AuthenticationPrincipal AuthPrincipal principal,
                                      @PathVariable String id) {
        return ApiResponse.ok(mediaService.get(principal, id));
    }

    /**
     * 删除未绑定的媒体。
     *
     * @param principal 当前用户
     * @param id        媒体 ID
     * @return 204
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal AuthPrincipal principal,
                                       @PathVariable String id) {
        mediaService.delete(principal, id);
        return ResponseEntity.noContent().build();
    }
}
