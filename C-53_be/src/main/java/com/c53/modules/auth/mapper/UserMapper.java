package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户 Mapper。
 *
 * <p>对应 {@code users} 表；联系方式和验证状态在查询时由 Repository 联表补充。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {
}
