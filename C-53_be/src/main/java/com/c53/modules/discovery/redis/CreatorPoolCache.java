package com.c53.modules.discovery.redis;

import com.c53.config.DiscoveryProperties;
import com.c53.modules.discovery.support.CreatorScorer;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 推荐创作者候选池缓存。
 *
 * <p>缓存按得分排序后的候选列表，5 分钟重新计算一次。缓存中只保存公开资料与得分，
 * 不保存登录态；排除自己与已关注在读取后按请求完成。</p>
 */
@Component
public class CreatorPoolCache {

    private static final Logger log = LoggerFactory.getLogger(CreatorPoolCache.class);

    /** 候选池缓存键。 */
    private static final String KEY = "discovery:creators:pool";

    private static final TypeReference<List<CreatorScorer.Scored>> TYPE = new TypeReference<>() {
    };

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final DiscoveryProperties properties;

    public CreatorPoolCache(StringRedisTemplate redis, ObjectMapper objectMapper,
                            DiscoveryProperties properties) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    /**
     * 读取候选池。
     *
     * @return 缓存命中时返回候选列表，未命中或异常时返回空
     */
    public Optional<List<CreatorScorer.Scored>> get() {
        try {
            String json = redis.opsForValue().get(KEY);
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, TYPE));
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("creator pool cache read failed", exception);
            return Optional.empty();
        }
    }

    /**
     * 写入候选池。
     *
     * @param pool 候选列表
     */
    public void put(List<CreatorScorer.Scored> pool) {
        try {
            String json = objectMapper.writeValueAsString(pool);
            redis.opsForValue().set(KEY, json, properties.getCreator().getCacheTtl());
        } catch (RuntimeException | JsonProcessingException exception) {
            log.warn("creator pool cache write failed", exception);
        }
    }
}
