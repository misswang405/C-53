package com.c53.modules.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.interaction.entity.PostBookmarkEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态收藏表数据访问 Mapper。
 */
@Mapper
public interface PostBookmarkMapper extends BaseMapper<PostBookmarkEntity> {

    /** 幂等插入收藏关系，重复收藏时忽略。 */
    @Insert("INSERT IGNORE INTO post_bookmarks (id, post_id, user_id, bookmarked_at) VALUES (#{id}, #{postId}, #{userId}, #{bookmarkedAt})")
    int insertIgnore(PostBookmarkEntity entity);
}
