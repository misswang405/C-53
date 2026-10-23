package com.c53.modules.auth.service;

import com.c53.modules.auth.model.AuthModels.DeviceResolution;

/**
 * 设备身份服务。
 *
 * <p>负责把设备 Cookie 解析为设备 ID：首次访问时创建新设备和新设备会话，
 * 后续访问时校验令牌摘要、有效期和设备状态。</p>
 */
public interface DeviceService {

    /**
     * 解析设备 Cookie；无效或缺失时创建新设备。
     *
     * @param cookieValue 设备 Cookie 原始值
     * @param ip          客户端 IP
     * @param userAgent   客户端 User-Agent
     * @return 设备 ID 和需要下发的新 Cookie（无需更新时为 {@code null}）
     */
    DeviceResolution resolveOrCreate(String cookieValue, String ip, String userAgent);

    /**
     * 解析已存在的设备 Cookie，不存在或失效时抛出 401。
     *
     * <p>用于切换账号等必须已有可信设备的场景。</p>
     *
     * @param cookieValue 设备 Cookie 原始值
     * @param ip          客户端 IP
     * @param userAgent   客户端 User-Agent
     * @return 设备 ID
     */
    String resolveExisting(String cookieValue, String ip, String userAgent);
}
