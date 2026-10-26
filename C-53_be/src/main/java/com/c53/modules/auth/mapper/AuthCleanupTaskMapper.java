package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthCleanupTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

/** Redis 撤销补偿任务 Mapper。 */
@Mapper
public interface AuthCleanupTaskMapper extends BaseMapper<AuthCleanupTaskEntity> {

    List<AuthCleanupTaskEntity> selectPending(@Param("now") Instant now, @Param("limit") int limit);

    /** 成功可完成任意仍待处理的同一任务；重复成功无副作用。 */
    int markCompleted(@Param("id") String id, @Param("now") Instant now);

    /** 仅相同版本的待处理任务可延期，不能覆盖另一实例已完成的结果。 */
    int deferRetry(@Param("id") String id, @Param("attempts") int attempts,
                   @Param("nextRetryAt") Instant nextRetryAt, @Param("now") Instant now);
}
