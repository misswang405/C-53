package com.c53.modules.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.c53.modules.auth.entity.AuthDeviceEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 设备 Mapper。
 *
 * <p>设备通过浏览器指纹摘要识别，记录首次/最近出现的 IP、User-Agent 和风险等级。</p>
 */
@Mapper
public interface AuthDeviceMapper extends BaseMapper<AuthDeviceEntity> {
}
