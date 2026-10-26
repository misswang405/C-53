package com.c53.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * 认证模块配置。
 *
 * <p>绑定 {@code application.yml} 中 {@code auth.*} 前缀的配置项，
 * 通过 {@code @EnableConfigurationProperties} 注册到容器。
 * 所有安全参数（密钥、有效期、限流阈值）都集中在这里，
 * 生产环境应通过环境变量覆盖默认值。</p>
 */
@ConfigurationProperties(prefix = "auth")
public class AuthProperties {

    /** JWT 相关配置。 */
    private final Jwt jwt = new Jwt();

    /** Refresh Token 有效期配置。 */
    private final Refresh refresh = new Refresh();

    /** 验证码 HMAC 密钥配置。 */
    private final Hmac hmac = new Hmac();

    /** 验证码发送与校验配置。 */
    private final Verification verification = new Verification();

    /** 设备 Cookie 配置。 */
    private final Device device = new Device();

    /** Redis Key 前缀配置。 */
    private final Redis redis = new Redis();

    /** 各类接口限流配置。 */
    private final Limits limits = new Limits();

    /** 跨域配置。 */
    private final Cors cors = new Cors();

    public Jwt getJwt() {
        return jwt;
    }

    public Refresh getRefresh() {
        return refresh;
    }

    public Hmac getHmac() {
        return hmac;
    }

    public Verification getVerification() {
        return verification;
    }

    public Device getDevice() {
        return device;
    }

    public Redis getRedis() {
        return redis;
    }

    public Limits getLimits() {
        return limits;
    }

    public Cors getCors() {
        return cors;
    }

    /**
     * JWT 配置。
     */
    public static class Jwt {
        /** 签名密钥，生产环境必须覆盖。 */
        private String secret = "c53-dev-jwt-secret-change-this-before-production-4f1b0c7a9e2d";

        /** 签发者标识。 */
        private String issuer = "c53";

        /** Access Token 有效期，默认 15 分钟。 */
        private Duration accessTtl = Duration.ofMinutes(15);

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public String getIssuer() {
            return issuer;
        }

        public void setIssuer(String issuer) {
            this.issuer = issuer;
        }

        public Duration getAccessTtl() {
            return accessTtl;
        }

        public void setAccessTtl(Duration accessTtl) {
            this.accessTtl = accessTtl;
        }
    }

    /**
     * Refresh Token 配置。
     */
    public static class Refresh {
        /** 勾选“保持登录”时的有效期，默认 7 天。 */
        private Duration rememberTtl = Duration.ofDays(7);

        /** 未勾选“保持登录”时的有效期，默认 24 小时。 */
        private Duration shortTtl = Duration.ofHours(24);

        /** 并发刷新宽限期：旧令牌在轮换后短时间内仍可使用，避免多标签页竞争。 */
        private Duration concurrentGrace = Duration.ofSeconds(5);

        public Duration getRememberTtl() {
            return rememberTtl;
        }

        public void setRememberTtl(Duration rememberTtl) {
            this.rememberTtl = rememberTtl;
        }

        public Duration getShortTtl() {
            return shortTtl;
        }

        public void setShortTtl(Duration shortTtl) {
            this.shortTtl = shortTtl;
        }

        public Duration getConcurrentGrace() {
            return concurrentGrace;
        }

        public void setConcurrentGrace(Duration concurrentGrace) {
            this.concurrentGrace = concurrentGrace;
        }
    }

    /**
     * 验证码 HMAC 配置。
     */
    public static class Hmac {
        /** HMAC 密钥，生产环境必须覆盖。 */
        private String secret = "c53-dev-hmac-secret-change-this-before-production-9a3e7c2b5d8f";

        /** 密钥版本号，便于密钥轮换时区分新旧摘要。 */
        private int keyVersion = 1;

        public String getSecret() {
            return secret;
        }

        public void setSecret(String secret) {
            this.secret = secret;
        }

        public int getKeyVersion() {
            return keyVersion;
        }

        public void setKeyVersion(int keyVersion) {
            this.keyVersion = keyVersion;
        }
    }

    /**
     * 验证码策略配置。
     */
    public static class Verification {
        /** 验证码有效期，默认 5 分钟。 */
        private Duration ttl = Duration.ofMinutes(5);

        /** 同一目标的最小重发间隔，默认 60 秒。 */
        private Duration resendInterval = Duration.ofSeconds(60);

        /** 单个挑战允许的最大校验次数，默认 5 次。 */
        private int maxAttempts = 5;

        /** 开发模式：不真正发送短信/邮件，只写入日志。 */
        private boolean devMode = false;

        /** 开发模式下使用的固定验证码。 */
        private String devCode = "123456";

        public Duration getTtl() {
            return ttl;
        }

        public void setTtl(Duration ttl) {
            this.ttl = ttl;
        }

        public Duration getResendInterval() {
            return resendInterval;
        }

        public void setResendInterval(Duration resendInterval) {
            this.resendInterval = resendInterval;
        }

        public int getMaxAttempts() {
            return maxAttempts;
        }

        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        public boolean isDevMode() {
            return devMode;
        }

        public void setDevMode(boolean devMode) {
            this.devMode = devMode;
        }

        public String getDevCode() {
            return devCode;
        }

        public void setDevCode(String devCode) {
            this.devCode = devCode;
        }
    }

    /**
     * 设备会话 Cookie 配置。
     */
    public static class Device {
        /** 存放设备会话令牌的 Cookie 名。 */
        private String cookieName = "c53_device_session";

        /** Cookie 有效期，默认 180 天。 */
        private Duration cookieTtl = Duration.ofDays(180);

        /** 是否只允许 HTTPS 传输；本地开发默认关闭。 */
        private boolean secure = false;

        public String getCookieName() {
            return cookieName;
        }

        public void setCookieName(String cookieName) {
            this.cookieName = cookieName;
        }

        public Duration getCookieTtl() {
            return cookieTtl;
        }

        public void setCookieTtl(Duration cookieTtl) {
            this.cookieTtl = cookieTtl;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }
    }

    /**
     * Redis 配置。
     */
    public static class Redis {
        /** 所有认证相关 Key 的公共前缀，避免与其他模块冲突。 */
        private String keyPrefix = "auth";

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }
    }

    /**
     * 接口限流配置。
     *
     * <p>所有阈值都按“窗口内最大次数”理解，超过后返回 429。</p>
     */
    public static class Limits {
        /** 单 IP 每分钟登录尝试上限。 */
        private int loginPerIpPerMinute = 20;

        /** 单账号连续登录失败上限。 */
        private int loginFailuresPerAccount = 5;

        /** 登录失败计数窗口。 */
        private Duration loginFailureWindow = Duration.ofMinutes(15);

        /** 单 IP 每小时注册上限。 */
        private int registrationPerIpPerHour = 20;

        /** 单会话每小时刷新上限。 */
        private int refreshPerSessionPerHour = 60;

        /** 单设备每分钟切换账号上限。 */
        private int switchPerDevicePerMinute = 10;

        /** 单 IP 每分钟用户名可用性检查上限。 */
        private int usernameCheckPerIpPerMinute = 60;

        private int resetSendPerIpPerMinute = 10;
        private int resetSendPerTargetPerHour = 5;
        private int resetSendPerIpPerDay = 100;
        private int resetSendPerTargetPerDay = 10;
        private int resetSubmitPerIpPerMinute = 10;
        private int resetSubmitPerTargetPerWindow = 5;
        private Duration resetSubmitWindow = Duration.ofMinutes(15);

        public int getResetSendPerIpPerMinute() { return resetSendPerIpPerMinute; }
        public void setResetSendPerIpPerMinute(int value) { resetSendPerIpPerMinute = value; }
        public int getResetSendPerTargetPerHour() { return resetSendPerTargetPerHour; }
        public void setResetSendPerTargetPerHour(int value) { resetSendPerTargetPerHour = value; }
        public int getResetSendPerIpPerDay() { return resetSendPerIpPerDay; }
        public void setResetSendPerIpPerDay(int value) { resetSendPerIpPerDay = value; }
        public int getResetSendPerTargetPerDay() { return resetSendPerTargetPerDay; }
        public void setResetSendPerTargetPerDay(int value) { resetSendPerTargetPerDay = value; }
        public int getResetSubmitPerIpPerMinute() { return resetSubmitPerIpPerMinute; }
        public void setResetSubmitPerIpPerMinute(int value) { resetSubmitPerIpPerMinute = value; }
        public int getResetSubmitPerTargetPerWindow() { return resetSubmitPerTargetPerWindow; }
        public void setResetSubmitPerTargetPerWindow(int value) { resetSubmitPerTargetPerWindow = value; }
        public Duration getResetSubmitWindow() { return resetSubmitWindow; }
        public void setResetSubmitWindow(Duration value) { resetSubmitWindow = value; }

        public int getLoginPerIpPerMinute() {
            return loginPerIpPerMinute;
        }

        public void setLoginPerIpPerMinute(int loginPerIpPerMinute) {
            this.loginPerIpPerMinute = loginPerIpPerMinute;
        }

        public int getLoginFailuresPerAccount() {
            return loginFailuresPerAccount;
        }

        public void setLoginFailuresPerAccount(int loginFailuresPerAccount) {
            this.loginFailuresPerAccount = loginFailuresPerAccount;
        }

        public Duration getLoginFailureWindow() {
            return loginFailureWindow;
        }

        public void setLoginFailureWindow(Duration loginFailureWindow) {
            this.loginFailureWindow = loginFailureWindow;
        }

        public int getRegistrationPerIpPerHour() {
            return registrationPerIpPerHour;
        }

        public void setRegistrationPerIpPerHour(int registrationPerIpPerHour) {
            this.registrationPerIpPerHour = registrationPerIpPerHour;
        }

        public int getRefreshPerSessionPerHour() {
            return refreshPerSessionPerHour;
        }

        public void setRefreshPerSessionPerHour(int refreshPerSessionPerHour) {
            this.refreshPerSessionPerHour = refreshPerSessionPerHour;
        }

        public int getSwitchPerDevicePerMinute() {
            return switchPerDevicePerMinute;
        }

        public void setSwitchPerDevicePerMinute(int switchPerDevicePerMinute) {
            this.switchPerDevicePerMinute = switchPerDevicePerMinute;
        }

        public int getUsernameCheckPerIpPerMinute() {
            return usernameCheckPerIpPerMinute;
        }

        public void setUsernameCheckPerIpPerMinute(int usernameCheckPerIpPerMinute) {
            this.usernameCheckPerIpPerMinute = usernameCheckPerIpPerMinute;
        }
    }

    /**
     * 跨域配置。
     */
    public static class Cors {
        /** 允许的前端来源，生产环境通过配置覆盖。 */
        private List<String> allowedOrigins = new ArrayList<>(List.of("http://localhost:5173", "http://127.0.0.1:5173"));

        public List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }
}
