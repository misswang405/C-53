package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthDeviceAccountEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备账号关系 Mapper。
 *
 * <p>维护“某设备上保存过哪些账号”，用于已保存账号列表和快速切换。</p>
 */
@Mapper
public interface AuthDeviceAccountMapper extends BaseMapper<AuthDeviceAccountEntity> {

    /**
     * 新增或恢复设备账号关系。
     *
     * <p>依赖唯一键 {@code (device_id, user_id)}：已存在时刷新最近使用时间、
     * 清除重新认证标记和吊销状态，使账号重新出现在已保存列表中。</p>
     *
     * @param entity 设备账号关系
     * @return 影响行数
     */
    int upsert(AuthDeviceAccountEntity entity);
}
