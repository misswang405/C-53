package com.c53.modules.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.c53.common.ApiException;
import com.c53.common.support.IdGenerator;
import com.c53.config.AuthProperties;
import com.c53.modules.auth.entity.AuthDeviceEntity;
import com.c53.modules.auth.entity.AuthDeviceSessionEntity;
import com.c53.modules.auth.mapper.AuthDeviceMapper;
import com.c53.modules.auth.mapper.AuthDeviceSessionMapper;
import com.c53.modules.auth.model.AuthModels.DeviceResolution;
import com.c53.modules.auth.model.AuthModels.DeviceSessionRow;
import com.c53.modules.auth.service.DeviceService;
import com.c53.modules.auth.service.TokenService;
import com.c53.modules.auth.support.HmacService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 设备身份服务实现。
 *
 * <p>设备令牌格式为 {@code ds_v1_<40-64 位随机字符>}，数据库只保存其 HMAC 摘要。
 * 解析时依次校验格式、摘要是否存在、会话是否过期/吊销、设备状态是否正常。</p>
 */
@Service
public class DeviceServiceImpl implements DeviceService {

    /** 设备令牌格式白名单，避免对任意 Cookie 值做数据库查询。 */
    private static final Pattern DEVICE_TOKEN = Pattern.compile("^ds_v1_[A-Za-z0-9_-]{40,64}$");

    private final AuthDeviceMapper deviceMapper;
    private final AuthDeviceSessionMapper deviceSessionMapper;
    private final HmacService hmacService;
    private final TokenService tokenService;
    private final AuthProperties properties;
    private final TransactionTemplate transactionTemplate;

    public DeviceServiceImpl(AuthDeviceMapper deviceMapper, AuthDeviceSessionMapper deviceSessionMapper,
                             HmacService hmacService, TokenService tokenService,
                             AuthProperties properties, TransactionTemplate transactionTemplate) {
        this.deviceMapper = deviceMapper;
        this.deviceSessionMapper = deviceSessionMapper;
        this.hmacService = hmacService;
        this.tokenService = tokenService;
        this.properties = properties;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * 解析设备 Cookie；无效或缺失时创建新设备。
     *
     * <p>流程：格式校验 → 摘要查库 → 校验有效期/吊销/设备状态 →
     * 有效则刷新活跃时间，否则在同一事务内新建设备和设备会话。</p>
     *
     * @param cookieValue 设备 Cookie 原始值
     * @param ip          客户端 IP
     * @param userAgent   客户端 User-Agent
     * @return 设备 ID 和需要下发的新 Cookie
     */
    @Override
    public DeviceResolution resolveOrCreate(String cookieValue, String ip, String userAgent) {
        Instant now = Instant.now();
        // 1. 先尝试复用已有设备令牌
        if (cookieValue != null && DEVICE_TOKEN.matcher(cookieValue).matches()) {
            Optional<DeviceSessionRow> existing = findByTokenHash(hmacService.hashDeviceToken(cookieValue));
            if (existing.isPresent()) {
                DeviceSessionRow row = existing.get();
                if (row.revokedAt() == null && row.expiresAt().isAfter(now) && "ACTIVE".equals(row.deviceStatus())) {
                    // 会话有效：只刷新最近活跃时间，不重新下发 Cookie
                    touchDeviceSession(row.id(), ip, now);
                    return new DeviceResolution(row.deviceId(), null);
                }
            }
        }

        // 2. 令牌缺失或失效：创建新设备和新设备会话
        String rawToken = tokenService.newDeviceToken();
        String hash = hmacService.hashDeviceToken(rawToken);
        String deviceId = IdGenerator.newId("dev");
        String deviceSessionId = IdGenerator.newId("ds");
        Instant expiresAt = now.plus(properties.getDevice().getCookieTtl());
        transactionTemplate.executeWithoutResult(status -> {
            insertDevice(deviceId, ip, userAgent, now);
            insertDeviceSession(deviceSessionId, deviceId, hash,
                    hmacService.keyVersion(), ip, userAgent, now, expiresAt);
        });
        // 只有新建时才把原始令牌交给 Controller 写入 Cookie
        return new DeviceResolution(deviceId, rawToken);
    }

    /**
     * 解析已存在的设备 Cookie，失效时抛出 401。
     *
     * @param cookieValue 设备 Cookie 原始值
     * @param ip          客户端 IP
     * @param userAgent   客户端 User-Agent
     * @return 设备 ID
     */
    @Override
    public String resolveExisting(String cookieValue, String ip, String userAgent) {
        // 格式非法直接拒绝，避免无意义查询
        if (cookieValue == null || !DEVICE_TOKEN.matcher(cookieValue).matches()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DEVICE_SESSION_REQUIRED", "设备会话无效，请重新登录");
        }
        Optional<DeviceSessionRow> existing = findByTokenHash(hmacService.hashDeviceToken(cookieValue));
        if (existing.isEmpty()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DEVICE_SESSION_REQUIRED", "设备会话无效，请重新登录");
        }
        DeviceSessionRow row = existing.get();
        Instant now = Instant.now();
        // 会话吊销、过期或设备被封禁都视为不可用
        if (row.revokedAt() != null || !row.expiresAt().isAfter(now) || !"ACTIVE".equals(row.deviceStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "DEVICE_SESSION_REQUIRED", "设备会话已失效，请重新登录");
        }
        touchDeviceSession(row.id(), ip, now);
        return row.deviceId();
    }

    /**
     * 按令牌摘要查询设备会话，并带出设备状态。
     *
     * @param tokenHash 令牌 HMAC 摘要
     * @return 设备会话；会话或设备不存在时为空
     */
    private Optional<DeviceSessionRow> findByTokenHash(String tokenHash) {
        AuthDeviceSessionEntity session = deviceSessionMapper.selectOne(
                Wrappers.<AuthDeviceSessionEntity>lambdaQuery()
                        .eq(AuthDeviceSessionEntity::getTokenHash, tokenHash)
                        .last("LIMIT 1"));
        if (session == null) {
            return Optional.empty();
        }
        AuthDeviceEntity device = deviceMapper.selectById(session.getDeviceId());
        if (device == null) {
            return Optional.empty();
        }
        return Optional.of(new DeviceSessionRow(session.getId(), session.getDeviceId(),
                session.getExpiresAt(), session.getRevokedAt(), device.getStatus()));
    }

    /**
     * 新建设备记录。
     *
     * @param deviceId  设备 ID
     * @param ip        首次 IP
     * @param userAgent 首次 User-Agent
     * @param now       当前时间
     */
    private void insertDevice(String deviceId, String ip, String userAgent, Instant now) {
        AuthDeviceEntity entity = new AuthDeviceEntity();
        entity.setId(deviceId);
        entity.setStatus("ACTIVE");
        entity.setRiskLevel("NORMAL");
        entity.setCreatedIpAddress(ip);
        entity.setLastIpAddress(ip);
        entity.setLastUserAgent(userAgent);
        entity.setFirstSeenAt(now);
        entity.setLastSeenAt(now);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        deviceMapper.insert(entity);
    }

    /**
     * 新建设备会话。
     *
     * @param id        设备会话 ID
     * @param deviceId  设备 ID
     * @param tokenHash 令牌摘要
     * @param keyVersion 摘要密钥版本
     * @param ip        创建 IP
     * @param userAgent User-Agent
     * @param now       创建时间
     * @param expiresAt 过期时间
     */
    private void insertDeviceSession(String id, String deviceId, String tokenHash, int keyVersion,
                                     String ip, String userAgent, Instant now, Instant expiresAt) {
        AuthDeviceSessionEntity entity = new AuthDeviceSessionEntity();
        entity.setId(id);
        entity.setDeviceId(deviceId);
        entity.setTokenHash(tokenHash);
        entity.setHmacKeyVersion(keyVersion);
        entity.setCreatedIpAddress(ip);
        entity.setLastIpAddress(ip);
        entity.setUserAgent(userAgent);
        entity.setCreatedAt(now);
        entity.setLastSeenAt(now);
        entity.setExpiresAt(expiresAt);
        entity.setUpdatedAt(now);
        deviceSessionMapper.insert(entity);
    }

    /**
     * 刷新设备会话最近活跃时间和 IP。
     *
     * @param deviceSessionId 设备会话 ID
     * @param ip              本次请求 IP
     * @param now             当前时间
     */
    private void touchDeviceSession(String deviceSessionId, String ip, Instant now) {
        deviceSessionMapper.update(null, Wrappers.<AuthDeviceSessionEntity>lambdaUpdate()
                .eq(AuthDeviceSessionEntity::getId, deviceSessionId)
                .set(AuthDeviceSessionEntity::getLastSeenAt, now)
                .set(AuthDeviceSessionEntity::getLastIpAddress, ip)
                .set(AuthDeviceSessionEntity::getUpdatedAt, now));
    }
}
