package com.c53.modules.media.sts;

import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.sts.model.v20150401.AssumeRoleRequest;
import com.aliyuncs.sts.model.v20150401.AssumeRoleResponse;
import com.c53.common.ApiException;
import com.c53.config.MediaProperties;
import com.c53.modules.media.support.ObjectKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * STS 凭证签发。
 *
 * <p>临时凭证只允许写入本次上传的临时前缀，不能读取、删除或列举对象。
 * 即使凭证泄露，影响范围也限于一个未确认的上传目录。</p>
 */
@Component
@ConditionalOnExpression("'${media.bucket:}' != ''")
public class StsTicketIssuer {

    private static final Logger log = LoggerFactory.getLogger(StsTicketIssuer.class);

    private final IAcsClient stsClient;
    private final MediaProperties properties;

    public StsTicketIssuer(IAcsClient stsClient, MediaProperties properties) {
        this.stsClient = stsClient;
        this.properties = properties;
    }

    /**
     * 签发上传凭证。
     *
     * @param ownerId  上传者
     * @param uploadId 上传 ID
     * @return 临时凭证
     */
    public StsCredentials issue(String ownerId, String uploadId) {
        AssumeRoleRequest request = new AssumeRoleRequest();
        request.setRoleArn(properties.getRoleArn());
        request.setRoleSessionName(sessionName(ownerId));
        // STS 允许的最短有效期为 900 秒
        request.setDurationSeconds(Math.max(900, properties.getTicketTtl().toSeconds()));
        request.setPolicy(policy(ownerId, uploadId));
        try {
            AssumeRoleResponse response = stsClient.getAcsResponse(request);
            AssumeRoleResponse.Credentials credentials = response.getCredentials();
            return new StsCredentials(
                    credentials.getAccessKeyId(),
                    credentials.getAccessKeySecret(),
                    credentials.getSecurityToken(),
                    Instant.parse(credentials.getExpiration()));
        } catch (ClientException exception) {
            log.warn("sts assume role failed owner={} upload={} code={}",
                    ownerId, uploadId, exception.getErrCode(), exception);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "OSS_UNAVAILABLE", "文件服务暂不可用");
        }
    }

    /**
     * 会话策略：只允许写入临时前缀。
     */
    private String policy(String ownerId, String uploadId) {
        String resource = "acs:oss:*:*:" + properties.getBucket() + "/"
                + ObjectKeys.tempPrefix(properties.getEnv(), ownerId, uploadId) + "*";
        return "{\"Version\":\"1\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":["
                + "\"oss:PutObject\",\"oss:InitiateMultipartUpload\",\"oss:UploadPart\","
                + "\"oss:CompleteMultipartUpload\",\"oss:AbortMultipartUpload\"],"
                + "\"Resource\":[\"" + resource + "\"]}]}";
    }

    /**
     * RoleSessionName 只允许字母、数字和 {@code . _ -}，长度 2～64。
     */
    private String sessionName(String ownerId) {
        String prefix = properties.getRoleSessionName() == null ? "c53-upload" : properties.getRoleSessionName();
        String name = (prefix + "-" + ownerId).replaceAll("[^A-Za-z0-9._-]", "");
        if (name.length() > 64) {
            name = name.substring(0, 64);
        }
        return name.length() < 2 ? "c53" : name;
    }
}
