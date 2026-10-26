-- C-53 互动与收藏模块数据库表结构 v1.0
-- 表按点赞、收藏、评论、分享事件分组；唯一键保证重复操作幂等。
-- 时间统一按 UTC 保存；互动关系由应用层维护，不建立跨模块外键。

SET NAMES utf8mb4;
SET time_zone = '+00:00';

CREATE TABLE IF NOT EXISTS post_likes (
    id         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    post_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_likes_post_user (post_id, user_id),
    KEY idx_post_likes_post_created (post_id, created_at),
    KEY idx_post_likes_user_created (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '动态点赞关系';

CREATE TABLE IF NOT EXISTS post_bookmarks (
    id            VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    post_id       VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id       VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    bookmarked_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_post_bookmarks_post_user (post_id, user_id),
    KEY idx_post_bookmarks_user_time (user_id, bookmarked_at, post_id),
    KEY idx_post_bookmarks_post_user (post_id, user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '动态收藏关系';

CREATE TABLE IF NOT EXISTS comments (
    id         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    post_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    author_id  VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    parent_id  VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NULL,
    content    VARCHAR(1500) NOT NULL COMMENT '应用层限制 500 Unicode 码点',
    status     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'VISIBLE'
                   COMMENT 'VISIBLE / DELETED / HIDDEN',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                   ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    KEY idx_comments_post_root_time (post_id, parent_id, created_at, id),
    KEY idx_comments_parent_time (parent_id, created_at, id),
    KEY idx_comments_author_time (author_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '动态评论与二级回复';

CREATE TABLE IF NOT EXISTS post_share_events (
    id         VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    post_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    user_id    VARCHAR(40) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    channel    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL
                   COMMENT 'WECHAT / QQ / DOUYIN',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_share_events_post_time (post_id, created_at),
    KEY idx_share_events_user_time (user_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = '动态分享统计事件';
