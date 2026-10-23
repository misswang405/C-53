package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthRefreshTokenEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * Refresh Token 审计 Mapper。
 *
 * <p>MySQL 只保存令牌家族的签发链，用于审计和泄露后批量识别；
 * 令牌是否有效以 Redis 白名单为准。</p>
 */
@Mapper
public interface AuthRefreshTokenMapper extends BaseMapper<AuthRefreshTokenEntity> {

    /**
     * 查询某令牌家族下所有历史令牌摘要。
     *
     * <p>家族被判定泄露时，需要把这些摘要加入 Redis 黑名单。</p>
     *
     * @param familyId 家族 ID
     * @return 令牌摘要列表
     */
    List<String> selectHashesByFamily(@Param("familyId") String familyId);
}
