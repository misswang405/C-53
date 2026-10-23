package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.UserContactEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户联系方式 Mapper。
 *
 * <p>一个用户可以绑定多个邮箱/手机号，通过 {@code is_primary} 标记主联系方式。</p>
 */
@Mapper
public interface UserContactMapper extends BaseMapper<UserContactEntity> {
}
