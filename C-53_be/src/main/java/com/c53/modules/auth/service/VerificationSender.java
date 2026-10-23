package com.c53.modules.auth.service;

/**
 * 验证码发送网关。
 *
 * <p>抽象短信/邮件供应商，业务层只依赖该接口。
 * 开发环境使用 {@link DevVerificationSender}，生产环境可替换为真实供应商实现。</p>
 */
public interface VerificationSender {

    /**
     * 发送验证码。
     *
     * @param channel 渠道：sms/email
     * @param target  接收方
     * @param code    验证码明文
     * @return 供应商和消息 ID
     */
    SendResult send(String channel, String target, String code);

    /**
     * 发送结果。
     *
     * @param provider  供应商标识
     * @param messageId 供应商消息 ID，便于对账
     */
    record SendResult(String provider, String messageId) {
    }
}
