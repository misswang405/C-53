package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthVerificationChallengeEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 验证挑战 Mapper。
 *
 * <p>每次发送验证码创建一条挑战，校验成功后标记 consumed，
 * 过期或失败次数超限后标记 invalidated。</p>
 */
@Mapper
public interface AuthVerificationChallengeMapper extends BaseMapper<AuthVerificationChallengeEntity> {
}
