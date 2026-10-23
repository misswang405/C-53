package com.c53.modules.auth.support;

import com.c53.common.ApiException;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 认证模块通用校验与规范化工具。
 *
 * <p>负责把用户输入统一成数据库可比较的形式（邮箱小写、手机号带国际区号、
 * 用户名小写），并在注册/登录前做基础格式校验。
 * 这些方法只做纯计算，不访问数据库或 Spring 容器。</p>
 */
public final class AuthUtils {

    /** 邮箱格式：本地部分 + @ + 域名。 */
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    /** 带国际区号的手机号格式，例如 +8613800138000。 */
    private static final Pattern PHONE = Pattern.compile("^\\+[1-9]\\d{6,14}$");

    /** 用户名格式：3-24 位小写字母、数字、下划线或短横线。 */
    private static final Pattern USERNAME = Pattern.compile("^[a-z0-9_-]{3,24}$");

    /** 中国大陆 11 位手机号。 */
    private static final Pattern CHINESE_LOCAL_PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 带 86 国际区号的中国大陆手机号，兼容 +86 和 86 两种写法。 */
    private static final Pattern PHONE_WITH_86 = Pattern.compile("^(?:\\+?86)(1[3-9]\\d{9})$");

    private AuthUtils() {
    }

    /**
     * 去除首尾空白，空字符串视为 {@code null}。
     *
     * @param value 原始字符串
     * @return 去空白后的值，或 {@code null}
     */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 规范化邮箱：去空白、转小写并校验格式。
     *
     * @param value 原始邮箱
     * @return 规范化邮箱；格式非法时返回 {@code null}
     */
    public static String normalizeEmail(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.toLowerCase(Locale.ROOT);
        return EMAIL.matcher(normalized).matches() ? normalized : null;
    }

    /**
     * 规范化手机号：去除空格和短横线，统一转换为 {@code +86} 开头的 E.164 形式。
     *
     * <p>兼容三种输入：11 位本地号码、{@code 861...}、{@code +861...}。
     * 其他国家/地区的号码必须是带 {@code +} 的 E.164 格式。</p>
     *
     * @param value 原始手机号
     * @return 规范化手机号；格式非法时返回 {@code null}
     */
    public static String normalizePhone(String value) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            return null;
        }
        normalized = normalized.replaceAll("[\\s-]", "");
        // 中国大陆本地 11 位号码
        if (CHINESE_LOCAL_PHONE.matcher(normalized).matches()) {
            return "+86" + normalized;
        }
        // 带 86 区号的号码，统一补上 +
        var matcher = PHONE_WITH_86.matcher(normalized);
        if (matcher.matches()) {
            return "+86" + matcher.group(1);
        }
        // 其他国际号码按原样校验
        return PHONE.matcher(normalized).matches() ? normalized : null;
    }

    /**
     * 根据渠道规范化验证码目标。
     *
     * @param channel 渠道：{@code email}、{@code phone} 或 {@code sms}
     * @param target  邮箱或手机号
     * @return 规范化后的目标；渠道或格式非法时返回 {@code null}
     */
    public static String normalizeTarget(String channel, String target) {
        if ("email".equals(channel)) {
            return normalizeEmail(target);
        }
        if ("phone".equals(channel) || "sms".equals(channel)) {
            return normalizePhone(target);
        }
        return null;
    }

    /**
     * 规范化验证码渠道。
     *
     * @param channel 原始渠道
     * @return {@code sms}、{@code email}，非法时返回 {@code null}
     */
    public static String normalizeChannel(String channel) {
        String value = trimToNull(channel);
        if (value == null) {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "sms", "phone" -> "sms";
            case "email" -> "email";
            default -> null;
        };
    }

    /**
     * 规范化验证码使用场景。
     *
     * @param scene 原始场景
     * @return {@code register}、{@code login}，非法时返回 {@code null}
     */
    public static String normalizeScene(String scene) {
        String value = trimToNull(scene);
        if (value == null) {
            return null;
        }
        value = value.toLowerCase(Locale.ROOT);
        return ("register".equals(value) || "login".equals(value)) ? value : null;
    }

    /**
     * 规范化用户名：去空白、转小写并校验字符集。
     *
     * @param value 原始用户名
     * @return 规范化用户名；格式非法时返回 {@code null}
     */
    public static String normalizeUsername(String value) {
        String username = trimToNull(value);
        if (username == null) {
            return null;
        }
        username = username.toLowerCase(Locale.ROOT);
        return USERNAME.matcher(username).matches() ? username : null;
    }

    /**
     * 判断登录账号类型。
     *
     * @param account 邮箱、手机号或用户名
     * @return {@code email}、{@code phone} 或 {@code username}
     */
    public static String accountType(String account) {
        if (normalizeEmail(account) != null) {
            return "email";
        }
        if (normalizePhone(account) != null) {
            return "phone";
        }
        return "username";
    }

    /**
     * 规范化登录账号：邮箱/手机号按对应规则处理，用户名转小写。
     *
     * @param account 原始账号
     * @return 规范化账号，空输入返回 {@code null}
     */
    public static String normalizeAccount(String account) {
        String email = normalizeEmail(account);
        if (email != null) {
            return email;
        }
        String phone = normalizePhone(account);
        if (phone != null) {
            return phone;
        }
        return trimToNull(account) == null ? null : account.trim().toLowerCase(Locale.ROOT);
    }

    /**
     * 脱敏展示验证码目标。
     *
     * <p>邮箱保留首尾字符，手机号保留前 3 位和后 4 位，避免接口回显完整联系方式。</p>
     *
     * @param channel 渠道：{@code email} 或短信
     * @param target  已规范化的目标
     * @return 脱敏后的字符串
     */
    public static String maskTarget(String channel, String target) {
        if ("email".equals(channel)) {
            int at = target.indexOf('@');
            if (at <= 0) {
                return "***";
            }
            String local = target.substring(0, at);
            String domain = target.substring(at);
            if (local.length() <= 2) {
                return local.charAt(0) + "***" + domain;
            }
            return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
        }
        if (target.startsWith("+86") && target.length() == 14) {
            return "+86 " + target.substring(3, 6) + "****" + target.substring(10);
        }
        if (target.length() >= 7) {
            return target.substring(0, target.length() - 7) + "****" + target.substring(target.length() - 3);
        }
        return "***";
    }

    /**
     * 取昵称首字符作为头像占位文字，兼容 emoji 等代理对字符。
     *
     * @param nickname 昵称
     * @return 首字符；昵称为空时返回 {@code U}
     */
    public static String initials(String nickname) {
        if (nickname == null || nickname.isBlank()) {
            return "U";
        }
        int end = nickname.offsetByCodePoints(0, 1);
        return nickname.substring(0, end);
    }

    /**
     * 计算 UTF-8 字节长度。
     *
     * @param value 字符串，可为 {@code null}
     * @return 字节数，{@code null} 视为 0
     */
    public static int byteLength(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * 校验密码强度。
     *
     * <p>要求至少 8 位，且不超过 72 字节。72 字节上限来自 BCrypt 的输入截断特性，
     * 超过部分不会参与哈希，必须在入口处拒绝。</p>
     *
     * @param password 明文密码
     * @throws ApiException 密码长度不合法
     */
    public static void validatePassword(String password) {
        if (password == null || password.length() < 8) {
            throw ApiException.badRequest("VALIDATION_ERROR", "密码至少 8 位");
        }
        if (byteLength(password) > 72) {
            throw ApiException.badRequest("VALIDATION_ERROR", "密码不能超过 72 字节");
        }
    }

    /**
     * 断言条件成立，否则抛出 400 参数错误。
     *
     * @param condition 必须为 {@code true} 的条件
     * @param message   失败时的错误提示
     * @throws ApiException 条件不成立
     */
    public static void require(boolean condition, String message) {
        if (!condition) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
        }
    }
}
