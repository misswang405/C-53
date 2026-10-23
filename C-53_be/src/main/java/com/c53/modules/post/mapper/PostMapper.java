package com.c53.modules.post.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.post.entity.PostEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 动态 Mapper。
 *
 * <p>查询条件统一在 Repository 中组装，避免业务层直接写 SQL。</p>
 */
@Mapper
public interface PostMapper extends BaseMapper<PostEntity> {
}
