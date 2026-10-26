package com.c53.modules.interaction.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.interaction.entity.CommentEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 评论表数据访问 Mapper。
 */
@Mapper
public interface CommentMapper extends BaseMapper<CommentEntity> {
}
