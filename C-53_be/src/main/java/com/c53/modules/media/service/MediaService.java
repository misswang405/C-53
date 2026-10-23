package com.c53.modules.media.service;

import com.c53.modules.media.dto.MediaDtos.ConfirmRequest;
import com.c53.modules.media.dto.MediaDtos.MediaConfigResponse;
import com.c53.modules.media.dto.MediaDtos.MediaView;
import com.c53.modules.media.dto.MediaDtos.TicketResponse;
import com.c53.modules.media.dto.MediaDtos.UploadTicketRequest;
import com.c53.security.AuthPrincipal;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 媒体上传、确认、查询与绑定。
 */
public interface MediaService {

    /**
     * 上传能力配置。
     *
     * @return 配置
     */
    MediaConfigResponse config();

    /**
     * 小文件服务端上传。
     *
     * @param principal 当前用户
     * @param file      文件
     * @param mediaType IMAGE/VIDEO
     * @return 媒体视图
     */
    MediaView uploadServer(AuthPrincipal principal, MultipartFile file, String mediaType);

    /**
     * 申请视频直传凭证。
     *
     * @param principal 当前用户
     * @param request   申请参数
     * @return 凭证与临时路径
     */
    TicketResponse issueTicket(AuthPrincipal principal, UploadTicketRequest request);

    /**
     * 直传完成确认。
     *
     * @param principal 当前用户
     * @param mediaId   媒体 ID
     * @param request   确认参数
     * @return 媒体视图
     */
    MediaView confirm(AuthPrincipal principal, String mediaId, ConfirmRequest request);

    /**
     * 查询自己上传的媒体。
     *
     * @param principal 当前用户
     * @param mediaId   媒体 ID
     * @return 媒体视图
     */
    MediaView get(AuthPrincipal principal, String mediaId);

    /**
     * 删除未绑定动态的媒体。
     *
     * @param principal 当前用户
     * @param mediaId   媒体 ID
     */
    void delete(AuthPrincipal principal, String mediaId);

    /**
     * 动态的媒体列表，按展示顺序。
     *
     * @param postId 动态 ID
     * @return 媒体视图
     */
    List<MediaView> listByPost(String postId);

    /**
     * 批量查询动态媒体。
     *
     * @param postIds 动态 ID 集合
     * @return 动态 ID 到媒体列表的映射
     */
    Map<String, List<MediaView>> listByPosts(Collection<String> postIds);

    /**
     * 发布前校验媒体并返回媒体类型。
     *
     * @param ownerId       作者
     * @param mediaIds      有序媒体 ID
     * @param currentPostId 当前动态 ID，新建时为空
     * @return IMAGE/VIDEO，无媒体时为 null
     */
    String prepareBinding(String ownerId, List<String> mediaIds, String currentPostId);

    /**
     * 绑定媒体并写入展示顺序，必须在发布事务内调用。
     *
     * @param ownerId  作者
     * @param postId   动态 ID
     * @param mediaIds 有序媒体 ID
     */
    void bind(String ownerId, String postId, List<String> mediaIds);

    /**
     * 解绑媒体，置回 READY。
     *
     * @param ownerId  作者
     * @param mediaIds 媒体 ID
     */
    void release(String ownerId, Collection<String> mediaIds);

    /**
     * 动态删除时标记其媒体，等待保留期后清理。
     *
     * @param postId    动态 ID
     * @param deletedAt 删除时间
     */
    void deleteByPost(String postId, Instant deletedAt);
}
