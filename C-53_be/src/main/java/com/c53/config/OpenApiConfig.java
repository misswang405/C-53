package com.c53.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>由 springdoc-openapi 自动扫描 Controller 生成接口文档，
 * 并注册 Bearer JWT 认证方式。Swagger UI 中点击 Authorize 填入
 * Access Token 后，即可调试需要登录的接口。</p>
 */
@Configuration
public class OpenApiConfig {

    /**
     * 定义文档基本信息与认证方式。
     *
     * <p>这里只注册安全方案，不设置全局 SecurityRequirement，
     * 避免公开接口也被标记为必须登录。</p>
     *
     * @return OpenAPI 配置
     */
    @Bean
    public OpenAPI c53OpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("C-53 认证服务 API")
                        .version("1.0.0")
                        .description("注册、登录、令牌刷新、设备会话与已保存账号管理接口"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")));
    }
}
