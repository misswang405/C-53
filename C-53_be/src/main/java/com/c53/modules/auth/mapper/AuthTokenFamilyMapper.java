package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthTokenFamilyEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * Refresh Token 家族 Mapper。
 *
 * <p>家族级别的吊销可以一次性让某次登录产生的所有刷新令牌失效。</p>
 */
@Mapper
public interface AuthTokenFamilyMapper extends BaseMapper<AuthTokenFamilyEntity> {
}
