package com.c53.modules.auth.support;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link AuthUtils} 的输入归一化与校验测试。
 *
 * <p>这些规则是注册和登录的第一道防线，覆盖邮箱/手机号格式、
 * 用户名允许字符、脱敏展示以及密码长度边界。</p>
 */
class AuthUtilsTest {

    /**
     * 邮箱去空白并转小写；手机号统一为 {@code +86} 开头的纯数字格式。
     */
    @Test
    void normalizesEmailAndPhone() {
        assertThat(AuthUtils.normalizeEmail("  LinChe@Example.COM ")).isEqualTo("linche@example.com");
        assertThat(AuthUtils.normalizePhone("138 1234 5678")).isEqualTo("+8613812345678");
        assertThat(AuthUtils.normalizePhone("+86 138-1234-5678")).isEqualTo("+8613812345678");
        assertThat(AuthUtils.normalizeTarget("sms", "13812345678")).isEqualTo("+8613812345678");
    }

    /**
     * 用户名去除首尾空白并转小写；空格、@ 等非法字符返回 {@code null}。
     */
    @Test
    void normalizesUsernameToLowercaseAndRejectsInvalidCharacters() {
        assertThat(AuthUtils.normalizeUsername(" Lin_Che ")).isEqualTo("lin_che");
        assertThat(AuthUtils.normalizeUsername("lin che")).isNull();
        assertThat(AuthUtils.normalizeUsername("lin@che")).isNull();
    }

    /**
     * 脱敏只保留可识别片段，日志和接口回显不得泄露完整联系方式。
     */
    @Test
    void masksTargetsWithoutLeakingFullValue() {
        assertThat(AuthUtils.maskTarget("email", "linche@example.com"))
                .isEqualTo("l***e@example.com");
        assertThat(AuthUtils.maskTarget("sms", "+8613812345678"))
                .isEqualTo("+86 138****5678");
    }

    /**
     * 密码下限 8 位；上限 72 位，对应 BCrypt 的输入长度限制。
     */
    @Test
    void rejectsShortAndOversizedPasswords() {
        assertThatThrownBy(() -> AuthUtils.validatePassword("short"))
                .hasMessageContaining("至少 8 位");
        assertThatThrownBy(() -> AuthUtils.validatePassword("a".repeat(73)))
                .hasMessageContaining("72");
    }
}
