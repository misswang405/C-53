package com.c53.modules.auth.service;

import com.c53.modules.auth.support.AuthUtils;
import com.c53.common.ApiException;
import com.c53.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 开发环境验证码发送器。
 *
 * <p>不真正调用短信/邮件供应商，只把脱敏后的目标和验证码明文写入日志，
 * 便于本地开发时直接查看。当 {@code auth.verification.dev-mode=false} 时拒绝发送，
 * 防止生产环境误用固定验证码或把验证码打进日志。</p>
 */
@Component
public class DevVerificationSender implements VerificationSender {

    private static final Logger log = LoggerFactory.getLogger(DevVerificationSender.class);

    private final AuthProperties properties;

    public DevVerificationSender(AuthProperties properties) {
        this.properties = properties;
    }

    /**
     * 模拟发送验证码。
     *
     * @param channel 渠道
     * @param target  接收方
     * @param code    验证码明文（仅开发模式打印到控制台）
     * @return 固定返回 dev 供应商
     * @throws ApiException 非开发模式下调用
     */
    @Override
    public SendResult send(String channel, String target, String code) {
        // 生产环境必须替换为真实发送实现，否则直接报错
        if (!properties.getVerification().isDevMode()) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "VERIFICATION_SEND_FAILED",
                    "短信或邮件服务暂时不可用");
        }
        log.info("verification code dispatched in development mode channel={} target={} code={}",
                channel, AuthUtils.maskTarget(channel, target), code);
        return new SendResult("dev", null);
    }
}
