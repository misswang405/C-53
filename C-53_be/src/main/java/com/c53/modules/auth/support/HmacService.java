package com.c53.modules.auth.support;

import com.c53.config.AuthProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * HMAC 摘要服务。
 *
 * <p>数据库中不保存任何明文令牌或验证码，只保存 HMAC-SHA256 摘要。
 * 同一主密钥通过不同用途标签派生出多把子密钥，避免不同业务之间的摘要可互相复用。
 * 校验时使用常量时间比较，防止时序攻击。</p>
 */
@Component
public class HmacService {

    /** 刷新令牌摘要密钥。 */
    private final byte[] refreshKey;

    /** 设备 Cookie 摘要密钥。 */
    private final byte[] deviceKey;

    /** 验证码摘要密钥。 */
    private final byte[] verificationKey;

    /** 账号审计摘要密钥。 */
    private final byte[] accountKey;

    /** 当前密钥版本，写入数据库用于后续密钥轮换。 */
    private final int keyVersion;

    public HmacService(AuthProperties properties) {
        String secret = properties.getHmac().getSecret();
        // 每个用途使用独立子密钥，降低跨业务伪造风险
        this.refreshKey = derive(secret, "refresh-token");
        this.deviceKey = derive(secret, "device-cookie");
        this.verificationKey = derive(secret, "verification-code");
        this.accountKey = derive(secret, "account-audit");
        this.keyVersion = properties.getHmac().getKeyVersion();
    }

    /**
     * 计算刷新令牌摘要。
     *
     * @param token 原始刷新令牌
     * @return 十六进制摘要
     */
    public String hashRefreshToken(String token) {
        return hmacHex(refreshKey, token);
    }

    /**
     * 计算设备 Cookie 令牌摘要。
     *
     * @param token 原始设备令牌
     * @return 十六进制摘要
     */
    public String hashDeviceToken(String token) {
        return hmacHex(deviceKey, token);
    }

    /**
     * 计算验证码摘要。
     *
     * <p>把挑战 ID 和验证码一起参与摘要，防止相同验证码在不同挑战之间通用。</p>
     *
     * @param challengeId 验证挑战 ID
     * @param code        验证码
     * @return 十六进制摘要
     */
    public String hashVerificationCode(String challengeId, String code) {
        return hmacHex(verificationKey, challengeId + ":" + code);
    }

    /**
     * 计算登录账号摘要，用于审计和失败计数，避免明文落库。
     *
     * @param normalizedAccount 已规范化的账号
     * @return 十六进制摘要
     */
    public String hashAccount(String normalizedAccount) {
        return hmacHex(accountKey, normalizedAccount);
    }

    /**
     * 常量时间比较两个十六进制摘要。
     *
     * @param leftHex  第一个摘要
     * @param rightHex 第二个摘要
     * @return 相等返回 {@code true}；任一为 {@code null} 返回 {@code false}
     */
    public boolean matches(String leftHex, String rightHex) {
        if (leftHex == null || rightHex == null) {
            return false;
        }
        return MessageDigest.isEqual(leftHex.getBytes(StandardCharsets.US_ASCII), rightHex.getBytes(StandardCharsets.US_ASCII));
    }

    /**
     * 返回当前 HMAC 密钥版本。
     *
     * @return 密钥版本号
     */
    public int keyVersion() {
        return keyVersion;
    }

    /**
     * 从主密钥派生用途隔离的子密钥。
     *
     * @param secret  主密钥
     * @param purpose 用途标签
     * @return 32 字节子密钥
     */
    private static byte[] derive(String secret, String purpose) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest((secret + "|" + purpose).getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    /**
     * 计算 HMAC-SHA256 并输出小写十六进制。
     *
     * @param key   HMAC 密钥
     * @param value 待摘要文本
     * @return 十六进制摘要
     */
    private static String hmacHex(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("HmacSHA256 is unavailable", exception);
        }
    }
}
