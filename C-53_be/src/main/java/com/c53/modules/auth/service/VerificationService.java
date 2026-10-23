package com.c53.modules.auth.service;

import com.c53.modules.auth.dto.AuthDtos.VerificationCodeResponse;
import com.c53.modules.auth.model.AuthModels.ChallengeRow;
import com.c53.modules.auth.model.AuthModels.UserRow;

import java.time.Instant;
import java.util.Optional;

/**
 * 验证码服务。
 *
 * <p>负责发送验证码、校验并消费挑战，以及根据联系方式反查用户。
 * 挑战只保存验证码摘要，校验成功后立即失效，防止重复使用。</p>
 */
public interface VerificationService {

    /**
     * 发送验证码并创建验证挑战。
     *
     * @param sceneValue   场景原始值
     * @param channelValue 渠道原始值
     * @param targetValue  接收方原始值
     * @param deviceId     设备 ID，可为 {@code null}
     * @param requestId    请求追踪 ID
     * @param ip           请求 IP
     * @param userAgent    User-Agent
     * @return 挑战 ID、脱敏目标和有效期信息
     */
    VerificationCodeResponse send(String sceneValue, String channelValue, String targetValue,
                                  String deviceId, String requestId, String ip, String userAgent);

    /**
     * 校验验证码并消费挑战。
     *
     * <p>会校验场景、渠道、目标是否一致，并累计失败次数。
     * 成功后挑战状态变为 CONSUMED，无法再次使用。</p>
     *
     * @param challengeId 挑战 ID
     * @param code        用户输入的验证码
     * @param scene       期望场景
     * @param channel     期望渠道
     * @param target      期望目标
     * @param now         当前时间
     * @return 校验通过的挑战
     */
    ChallengeRow verifyAndConsume(String challengeId, String code, String scene,
                                  String channel, String target, Instant now);

    /**
     * 查询某目标当前最新可用的挑战 ID。
     *
     * @param target  接收方
     * @param scene   场景
     * @param channel 渠道
     * @param now     当前时间
     * @return 挑战 ID；不存在时返回 {@code null}
     */
    String latestActiveId(String target, String scene, String channel, Instant now);

    /**
     * 根据联系方式反查用户。
     *
     * @param contactType 联系方式类型
     * @param target      联系方式值
     * @return 用户信息；不存在时为空
     */
    Optional<UserRow> findUserForTarget(String contactType, String target);
}
