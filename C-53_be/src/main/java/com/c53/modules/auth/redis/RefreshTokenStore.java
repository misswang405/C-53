package com.c53.modules.auth.redis;

import com.c53.modules.auth.model.AuthModels.RefreshTokenMeta;
import com.c53.modules.auth.model.AuthModels.UsedMarker;
import com.c53.common.DependencyUnavailableException;
import com.c53.config.AuthProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Refresh Token 白名单存储。
 *
 * <p>MySQL 只保存签发审计，令牌是否有效以 Redis 为准。存储模型：
 * <ul>
 *   <li>{@code auth:rt:token:<hash>}：当前有效令牌的元数据 Hash</li>
 *   <li>{@code auth:rt:family:<id>:current}：家族当前有效令牌的 hash</li>
 *   <li>{@code auth:rt:used:<hash>}：已使用令牌标记，用于检测重放</li>
 *   <li>{@code auth:rt:family:<id>:revoked}：家族吊销标记</li>
 * </ul>
 * 轮换、吊销等关键操作都用 Lua 脚本保证原子性，避免并发刷新产生多个有效令牌。</p>
 */
@Component
public class RefreshTokenStore {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenStore.class);

    /**
     * 首次写入令牌和家族当前指针。
     *
     * <p>KEYS[1]=令牌 Hash，KEYS[2]=家族当前指针；
     * ARGV 依次为 sessionId、familyId、userId、issuedAt、expiresAt、
     * 令牌 TTL、tokenHash、家族 TTL、tokenId。</p>
     */
    private static final DefaultRedisScript<Long> INITIAL_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then
                return 0
            end
            redis.call('HSET', KEYS[1], 'sessionId', ARGV[1], 'familyId', ARGV[2], 'userId', ARGV[3],
                       'issuedAt', ARGV[4], 'expiresAt', ARGV[5], 'tokenId', ARGV[9])
            redis.call('EXPIRE', KEYS[1], ARGV[6])
            redis.call('SET', KEYS[2], ARGV[7], 'EX', ARGV[8])
            return 1
            """, Long.class);

    /**
     * 原子轮换刷新令牌。
     *
     * <p>返回码：1=轮换成功；0=并发刷新，旧令牌已被同一请求消费；-1=家族已吊销或不存在；
     * -2=令牌与家族当前指针不匹配，视为重放/异常。</p>
     *
     * <p>KEYS：旧令牌 Hash、家族当前指针、旧令牌已用标记、新令牌 Hash、家族吊销标记。</p>
     */
    private static final DefaultRedisScript<Long> ROTATE_SCRIPT = new DefaultRedisScript<>("""
            -- 家族已吊销：拒绝刷新
            if redis.call('EXISTS', KEYS[5]) == 1 then
                return -1
            end
            local current = redis.call('GET', KEYS[2])
            if current == false then
                return -1
            end
            -- 旧令牌不是家族当前令牌
            if current ~= ARGV[1] then
                -- 已被并发请求标记使用，属于良性竞争
                if redis.call('EXISTS', KEYS[3]) == 1 then
                    return 0
                end
                return -2
            end
            local sessionId = redis.call('HGET', KEYS[1], 'sessionId')
            if sessionId == false then
                if redis.call('EXISTS', KEYS[3]) == 1 then
                    return 0
                end
                return -2
            end
            -- 元数据与传入值不一致，拒绝轮换
            if sessionId ~= ARGV[3]
               or redis.call('HGET', KEYS[1], 'familyId') ~= ARGV[4]
               or redis.call('HGET', KEYS[1], 'userId') ~= ARGV[5] then
                return -2
            end
            -- 标记旧令牌已使用，保留到家族过期，便于识别重放
            redis.call('HSET', KEYS[3], 'familyId', ARGV[4], 'sessionId', ARGV[3],
                       'userId', ARGV[5], 'usedAt', ARGV[7])
            redis.call('EXPIRE', KEYS[3], ARGV[9])
            -- 删除旧令牌并写入新令牌
            redis.call('DEL', KEYS[1])
            redis.call('HSET', KEYS[4], 'sessionId', ARGV[3], 'familyId', ARGV[4], 'userId', ARGV[5],
                       'issuedAt', ARGV[6], 'expiresAt', ARGV[8], 'tokenId', ARGV[12])
            redis.call('EXPIRE', KEYS[4], ARGV[10])
            -- 家族当前指针切换到新令牌
            redis.call('SET', KEYS[2], ARGV[2], 'EX', ARGV[11])
            return 1
            """, Long.class);

    /**
     * 吊销家族：写入吊销标记并删除当前指针。
     *
     * <p>KEYS[1]=吊销标记，KEYS[2]=家族当前指针；ARGV[1]=原因，ARGV[2]=TTL 秒。</p>
     */
    private static final DefaultRedisScript<Long> REVOKE_SCRIPT = new DefaultRedisScript<>("""
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[2])
            local current = redis.call('GET', KEYS[2])
            if current then
                redis.call('DEL', ARGV[3] .. current)
            end
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    /**
     * 轮换失败时回滚：恢复旧令牌和家族当前指针，删除新令牌与已用标记。
     *
     * <p>返回 1 表示回滚成功；0 表示状态已变化，放弃回滚。</p>
     */
    private static final DefaultRedisScript<Long> ROLLBACK_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[5]) == 1 then
                return 0
            end
            -- 当前指针已不是新令牌，说明有并发操作，不能盲目回滚
            if redis.call('GET', KEYS[1]) ~= ARGV[2] then
                return 0
            end
            if redis.call('EXISTS', KEYS[3]) == 0 then
                return 0
            end
            redis.call('DEL', KEYS[2])
            redis.call('DEL', KEYS[3])
            redis.call('HSET', KEYS[4], 'sessionId', ARGV[3], 'familyId', ARGV[4], 'userId', ARGV[5],
                       'issuedAt', ARGV[6], 'expiresAt', ARGV[7], 'tokenId', ARGV[10])
            redis.call('EXPIRE', KEYS[4], ARGV[8])
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[9])
            return 1
            """, Long.class);

    /**
     * 限流计数器：首次自增时设置过期时间，返回当前计数。
     *
     * <p>KEYS[1]=计数 Key，ARGV[1]=窗口秒数。</p>
     */
    private static final DefaultRedisScript<Long> COUNTER_SCRIPT = new DefaultRedisScript<>("""
            local value = redis.call('INCR', KEYS[1])
            if value == 1 then
                redis.call('EXPIRE', KEYS[1], ARGV[1])
            end
            return value
            """, Long.class);

    private final StringRedisTemplate redis;

    /** Key 公共前缀，默认 {@code auth}。 */
    private final String prefix;

    public RefreshTokenStore(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        String configured = properties.getRedis().getKeyPrefix();
        this.prefix = configured == null || configured.isBlank() ? "auth" : configured.replaceAll(":$", "");
    }

    /**
     * 检查 Redis 连通性，供健康检查或启动探活使用。
     */
    public void ping() {
        run("ping", () -> {
            String pong = redis.execute((RedisCallback<String>) connection -> connection.ping());
            if (!"PONG".equalsIgnoreCase(pong)) {
                throw new IllegalStateException("unexpected redis ping response");
            }
            return null;
        });
    }

    /**
     * 写入登录后首次签发的刷新令牌。
     *
     * @param meta      令牌元数据
     * @param tokenHash 令牌摘要
     * @param tokenTtl  令牌有效期
     * @param familyTtl 家族有效期
     */
    public void writeInitial(RefreshTokenMeta meta, String tokenHash, Duration tokenTtl, Duration familyTtl) {
        Long result = run("write-initial", () -> redis.execute(INITIAL_SCRIPT,
                List.of(tokenKey(tokenHash), currentKey(meta.familyId()), revokedKey(meta.familyId())),
                meta.sessionId(), meta.familyId(), meta.userId(),
                Long.toString(meta.issuedAt()), Long.toString(meta.expiresAt()),
                Long.toString(seconds(tokenTtl)), tokenHash, Long.toString(seconds(familyTtl)),
                meta.tokenId()));
        if (result == null) {
            throw new DependencyUnavailableException("认证令牌服务暂时不可用");
        }
        if (result != 1L) {
            throw com.c53.common.ApiException.unauthorized("SESSION_REVOKED", "登录状态已失效，请重新登录");
        }
    }

    /**
     * 读取令牌元数据。
     *
     * @param tokenHash 令牌摘要
     * @return 元数据；不存在或数据损坏时为空
     */
    public Optional<RefreshTokenMeta> getToken(String tokenHash) {
        Map<Object, Object> values = run("get-token", () -> redis.opsForHash().entries(tokenKey(tokenHash)));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new RefreshTokenMeta(
                    text(values.get("tokenId")),
                    text(values.get("sessionId")),
                    text(values.get("familyId")),
                    text(values.get("userId")),
                    Long.parseLong(text(values.get("issuedAt"))),
                    Long.parseLong(text(values.get("expiresAt")))));
        } catch (RuntimeException exception) {
            // 数据损坏时按不存在处理，避免异常扩散到认证流程
            log.warn("invalid refresh token metadata in redis, ignoring entry");
            return Optional.empty();
        }
    }

    /**
     * 读取已使用令牌标记，用于判断是否发生重放或并发刷新。
     *
     * @param tokenHash 令牌摘要
     * @return 使用标记；不存在或数据损坏时为空
     */
    public Optional<UsedMarker> getUsed(String tokenHash) {
        Map<Object, Object> values = run("get-used", () -> redis.opsForHash().entries(usedKey(tokenHash)));
        if (values == null || values.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new UsedMarker(
                    text(values.get("familyId")),
                    text(values.get("sessionId")),
                    text(values.get("userId")),
                    Long.parseLong(text(values.get("usedAt")))));
        } catch (RuntimeException exception) {
            log.warn("invalid used-token marker in redis, ignoring entry");
            return Optional.empty();
        }
    }

    /**
     * 判断家族是否已被吊销。
     *
     * @param familyId 家族 ID
     * @return 已吊销返回 {@code true}
     */
    public boolean isFamilyRevoked(String familyId) {
        Boolean exists = run("family-revoked", () -> redis.hasKey(revokedKey(familyId)));
        return Boolean.TRUE.equals(exists);
    }

    /**
     * 读取家族当前有效的令牌摘要。
     *
     * @param familyId 家族 ID
     * @return 当前令牌摘要；不存在时为空
     */
    public Optional<String> current(String familyId) {
        return Optional.ofNullable(run("family-current", () -> redis.opsForValue().get(currentKey(familyId))));
    }

    /**
     * 原子轮换令牌。
     *
     * @param oldMeta   旧令牌元数据
     * @param oldHash   旧令牌摘要
     * @param newHash   新令牌摘要
     * @param newMeta   新令牌元数据
     * @param tokenTtl  新令牌有效期
     * @param familyTtl 家族有效期
     * @return 1 成功；0 并发已消费；-1 家族吊销；-2 不匹配
     */
    public int rotate(RefreshTokenMeta oldMeta, String oldHash, String newHash,
                      RefreshTokenMeta newMeta, Duration tokenTtl, Duration familyTtl) {
        Long result = run("rotate", () -> redis.execute(ROTATE_SCRIPT,
                List.of(tokenKey(oldHash), currentKey(oldMeta.familyId()), usedKey(oldHash),
                        tokenKey(newHash), revokedKey(oldMeta.familyId())),
                oldHash, newHash, oldMeta.sessionId(), oldMeta.familyId(), oldMeta.userId(),
                Long.toString(newMeta.issuedAt()), Long.toString(System.currentTimeMillis()),
                Long.toString(newMeta.expiresAt()), Long.toString(seconds(familyTtl)),
                Long.toString(seconds(tokenTtl)), Long.toString(seconds(familyTtl)),
                newMeta.tokenId()));
        return result == null ? -2 : result.intValue();
    }

    /**
     * 回滚一次已执行的轮换。
     *
     * <p>用于 Redis 轮换成功但数据库事务失败的情况，尽量保持内存与数据库一致。</p>
     *
     * @param oldMeta      旧令牌元数据
     * @param oldHash      旧令牌摘要
     * @param newHash      新令牌摘要
     * @param oldTokenTtl  旧令牌剩余有效期
     * @param familyTtl    家族有效期
     * @return 回滚成功返回 {@code true}
     */
    public boolean rollbackRotation(RefreshTokenMeta oldMeta, String oldHash, String newHash,
                                    Duration oldTokenTtl, Duration familyTtl) {
        Long result = run("rollback-rotation", () -> redis.execute(ROLLBACK_SCRIPT,
                List.of(currentKey(oldMeta.familyId()), tokenKey(newHash), usedKey(oldHash),
                        tokenKey(oldHash), revokedKey(oldMeta.familyId())),
                oldHash, newHash, oldMeta.sessionId(), oldMeta.familyId(), oldMeta.userId(),
                Long.toString(oldMeta.issuedAt()), Long.toString(oldMeta.expiresAt()),
                Long.toString(seconds(oldTokenTtl)), Long.toString(seconds(familyTtl)),
                oldMeta.tokenId()));
        return result != null && result == 1L;
    }

    /**
     * 吊销整个令牌家族。
     *
     * <p>先写吊销标记，再删除当前指针和历史令牌 Key。吊销标记保留到家族 TTL，
     * 期间任何旧令牌都无法再次刷新。</p>
     *
     * @param familyId    家族 ID
     * @param reason      吊销原因
     * @param ttl         吊销标记保留时长
     * @param tokenHashes 需要删除的令牌摘要列表
     */
    public void revokeFamily(String familyId, String reason, Duration ttl, List<String> tokenHashes) {
        long ttlSeconds = seconds(ttl);
        run("revoke-family", () -> {
            redis.execute(REVOKE_SCRIPT, List.of(revokedKey(familyId), currentKey(familyId)),
                    reason, Long.toString(ttlSeconds), prefix + ":rt:token:");
            if (tokenHashes != null && !tokenHashes.isEmpty()) {
                List<String> keys = new ArrayList<>(tokenHashes.size());
                for (String hash : tokenHashes) {
                    keys.add(tokenKey(hash));
                }
                redis.delete(keys);
            }
            return null;
        });
    }

    /**
     * 固定窗口限流计数。
     *
     * @param scope    限流维度，例如 login-ip
     * @param identity 维度值，例如 IP 或账号摘要
     * @param ttl      窗口长度
     * @return 当前窗口内累计次数
     */
    public long incrementCounter(String scope, String identity, Duration ttl) {
        String key = prefix + ":rl:" + scope + ":" + identity;
        Long value = run("rate-limit", () -> redis.execute(COUNTER_SCRIPT, List.of(key),
                Long.toString(seconds(ttl))));
        if (value == null) {
            throw new DependencyUnavailableException("认证限流服务暂时不可用");
        }
        return value;
    }

    /** 令牌元数据 Key。 */
    private String tokenKey(String hash) {
        return prefix + ":rt:token:" + hash;
    }

    /** 家族当前令牌指针 Key。 */
    private String currentKey(String familyId) {
        return prefix + ":rt:family:" + familyId + ":current";
    }

    /** 家族吊销标记 Key。 */
    private String revokedKey(String familyId) {
        return prefix + ":rt:family:" + familyId + ":revoked";
    }

    /** 已使用令牌标记 Key。 */
    private String usedKey(String hash) {
        return prefix + ":rt:used:" + hash;
    }

    /** 转换为 Redis EXPIRE 需要的秒数，至少 1 秒。 */
    private static long seconds(Duration duration) {
        return Math.max(1L, duration.toSeconds());
    }

    /** 读取 Hash 字段并转为字符串，缺失时抛异常交给调用方按损坏处理。 */
    private static String text(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("missing redis hash field");
        }
        return value.toString();
    }

    /**
     * 统一包装 Redis 调用，把基础设施异常转换为 503 业务异常。
     *
     * @param operation 操作名，用于日志定位
     * @param action    实际 Redis 操作
     * @param <T>       返回类型
     * @return 操作结果
     */
    private <T> T run(String operation, Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException | IllegalStateException exception) {
            log.error("redis operation failed operation={}", operation, exception);
            throw new DependencyUnavailableException("认证服务暂时不可用，请稍后重试", exception);
        }
    }
}
