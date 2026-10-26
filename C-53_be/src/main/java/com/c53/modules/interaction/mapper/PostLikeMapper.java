package com.c53.modules.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.interaction.entity.PostLikeEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态点赞表数据访问 Mapper。
 */
@Mapper
public interface PostLikeMapper extends BaseMapper<PostLikeEntity> {

    /** 幂等插入点赞关系，重复点赞时忽略。 */
    @Insert("INSERT IGNORE INTO post_likes (id, post_id, user_id, created_at) VALUES (#{id}, #{postId}, #{userId}, #{createdAt})")
    int insertIgnore(PostLikeEntity entity);
}
