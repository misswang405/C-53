package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthLoginAttemptEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 登录尝试 Mapper。
 *
 * <p>记录每次登录成功/失败，用于失败计数、限流和安全审计。
 * 账号字段保存的是 HMAC 摘要而非明文。</p>
 */
@Mapper
public interface AuthLoginAttemptMapper extends BaseMapper<AuthLoginAttemptEntity> {
}
