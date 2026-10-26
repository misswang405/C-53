package com.c53.modules.interaction.service;

import com.c53.modules.interaction.dto.InteractionDtos.BookmarkListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.BookmarkResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentListResponse;
import com.c53.modules.interaction.dto.InteractionDtos.CommentRequest;
import com.c53.modules.interaction.dto.InteractionDtos.CommentView;
import com.c53.modules.interaction.dto.InteractionDtos.LikeResponse;
import com.c53.modules.interaction.dto.InteractionDtos.ShareEventRequest;
import com.c53.security.AuthPrincipal;

/**
 * 互动领域服务，封装动态点赞、收藏、评论、分享及收藏列表业务。
 */
public interface InteractionService {

    /** 点赞动态。 */
    LikeResponse like(AuthPrincipal principal, String postId);

    /** 取消点赞。 */
    LikeResponse unlike(AuthPrincipal principal, String postId);

    /** 收藏动态。 */
    BookmarkResponse bookmark(AuthPrincipal principal, String postId);

    /** 取消收藏。 */
    BookmarkResponse unbookmark(AuthPrincipal principal, String postId);

    /** 分页读取动态评论。 */
    CommentListResponse comments(AuthPrincipal principal, String postId, String cursor,
                                 int limit, String clientIp);

    /** 创建一级评论。 */
    CommentView createComment(AuthPrincipal principal, String postId, CommentRequest request);

    /** 创建二级回复。 */
    CommentView createReply(AuthPrincipal principal, String commentId, CommentRequest request);

    /** 记录分享事件。 */
    void share(AuthPrincipal principal, String postId, ShareEventRequest request);

    /** 分页读取当前用户收藏的动态。 */
    BookmarkListResponse bookmarks(AuthPrincipal principal, String cursor, int limit);
}
