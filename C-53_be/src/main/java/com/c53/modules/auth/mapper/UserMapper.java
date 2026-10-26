package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.UserEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户 Mapper。
 *
 * <p>对应 {@code users} 表；联系方式和验证状态在查询时由 Repository 联表补充。</p>
 */
@Mapper
public interface UserMapper extends BaseMapper<UserEntity> {

    /** 所有凭据颁发与密码重置共用的用户行锁，必须在事务内调用。 */
    @Select("SELECT * FROM users WHERE id = #{id} FOR UPDATE")
    UserEntity lockById(@Param("id") String id);
}
