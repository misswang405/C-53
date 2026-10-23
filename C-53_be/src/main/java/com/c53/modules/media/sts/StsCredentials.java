package com.c53.modules.media.sts;

import java.time.Instant;

/**
 * STS 临时凭证。
 *
 * @param accessKeyId    临时 AccessKeyId
 * @param accessKeySecret 临时 AccessKeySecret
 * @param securityToken  安全令牌
 * @param expiration     过期时间
 */
public record StsCredentials(String accessKeyId, String accessKeySecret,
                             String securityToken, Instant expiration) {
}
