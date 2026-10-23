package com.c53.common.support;

import java.security.SecureRandom;

/**
 * ID 生成器。
 *
 * <p>生成 ULID 风格的可排序 ID：前 10 位编码毫秒时间戳，后 16 位为随机字符。
 * 相比 UUID，ULID 按时间递增，作为主键索引更友好；随机部分使用
 * {@link SecureRandom}，避免可预测性。</p>
 */
public final class IdGenerator {

    /** Crockford Base32 字母表，去掉了容易混淆的 I、L、O、U。 */
    private static final char[] ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** 密码学安全的随机源，进程内复用。 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private IdGenerator() {
    }

    /**
     * 生成带业务前缀的 ID，便于日志和数据库排查时识别类型。
     *
     * @param prefix 业务前缀，例如 {@code req}、{@code usr}
     * @return 形如 {@code prefix_<ULID>} 的 ID
     */
    public static String newId(String prefix) {
        return prefix + "_" + ulid();
    }

    /**
     * 生成 26 位 ULID 字符串。
     *
     * @return 26 位大写 Base32 字符串
     */
    public static String ulid() {
        char[] value = new char[26];
        // 前 10 位编码 50 bit 毫秒时间戳，高位在前，保证字典序即时间序
        long timestamp = System.currentTimeMillis();
        for (int index = 9; index >= 0; index--) {
            value[index] = ALPHABET[(int) (timestamp & 31)];
            timestamp >>>= 5;
        }
        // 后 16 位从 80 bit 安全随机数中按每 5 bit 切分编码
        byte[] random = new byte[10];
        RANDOM.nextBytes(random);
        int bit = 0;
        for (int index = 10; index < 26; index++) {
            int current = 0;
            for (int offset = 0; offset < 5; offset++) {
                int byteIndex = bit / 8;
                int bitIndex = 7 - (bit % 8);
                current = (current << 1) | ((random[byteIndex] >> bitIndex) & 1);
                bit++;
            }
            value[index] = ALPHABET[current];
        }
        return new String(value);
    }
}
