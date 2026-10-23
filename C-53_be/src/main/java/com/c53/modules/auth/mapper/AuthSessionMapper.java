package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录会话 Mapper。
 *
 * <p>一个会话对应一次登录，Access Token 中的 {@code sid} 指向该记录。</p>
 */
@Mapper
public interface AuthSessionMapper extends BaseMapper<AuthSessionEntity> {
}
