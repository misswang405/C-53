package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthDeviceSessionEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备会话 Mapper。
 *
 * <p>对应设备 Cookie 的长期登录态，令牌只保存 HMAC 摘要。</p>
 */
@Mapper
public interface AuthDeviceSessionMapper extends BaseMapper<AuthDeviceSessionEntity> {
}
