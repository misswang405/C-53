package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthUserAgreementEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户协议同意记录 Mapper。
 *
 * <p>保留协议类型、版本、时间、请求 ID 和 IP，满足合规审计要求。</p>
 */
@Mapper
public interface AuthUserAgreementMapper extends BaseMapper<AuthUserAgreementEntity> {

    /**
     * 记录用户同意协议；重复同意时只更新同意时间。
     *
     * @param entity 协议同意记录
     * @return 影响行数
     */
    int upsert(AuthUserAgreementEntity entity);
}
