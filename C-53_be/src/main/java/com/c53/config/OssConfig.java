package com.c53.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.profile.DefaultProfile;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 与 STS 客户端配置。
 *
 * <p>只有配置了 {@code media.bucket} 才创建客户端。未配置时应用仍可启动，
 * 媒体接口统一返回 503，便于不接 OSS 的纯文字本地开发。</p>
 */
@Configuration
@ConditionalOnExpression("'${media.bucket:}' != ''")
public class OssConfig {

    /**
     * OSS 客户端，线程安全，应用生命周期内复用。
     *
     * @param properties 媒体配置
     * @return OSS 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public OSS ossClient(MediaProperties properties) {
        return new OSSClientBuilder().build(
                properties.getEndpoint(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
    }

    /**
     * STS 客户端，用于签发前端直传的临时凭证。
     *
     * @param properties 媒体配置
     * @return STS 客户端
     */
    @Bean(destroyMethod = "shutdown")
    public IAcsClient stsClient(MediaProperties properties) {
        DefaultProfile profile = DefaultProfile.getProfile(
                properties.getStsRegion(),
                properties.getAccessKeyId(),
                properties.getAccessKeySecret());
        return new DefaultAcsClient(profile);
    }
}
