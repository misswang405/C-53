package com.c53.modules.auth.service;

/**
 * 登录会话有效性校验。
 *
 * <p>JWT 签名有效不代表会话仍然有效：用户退出、令牌重放或账号被禁用后，
 * 会话会被撤销。发布、上传等写操作在 Service 层再校验一次，
 * 不只依赖过滤器的签名校验。</p>
 */
public interface AuthSessionGuard {

    /**
     * 要求当前会话有效且用户为 ACTIVE。
     *
     * @param userId    用户 ID
     * @param sessionId 会话 ID
     * @throws com.c53.common.ApiException 会话失效或账号禁用时抛出
     */
    void requireActive(String userId, String sessionId);
}
