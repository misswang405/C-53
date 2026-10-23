package com.c53;

import com.c53.config.AuthProperties;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * C-53 后端应用启动类。
 *
 * <p>开启组件扫描、MyBatis Mapper 扫描、配置属性绑定和注解事务。
 * Mapper 统一放在 {@code com.c53.modules} 下，并必须标注 {@link Mapper}，
 * 避免误把普通接口注册成 Mapper。</p>
 */
@SpringBootApplication
@MapperScan(basePackages = "com.c53.modules", annotationClass = Mapper.class)
@EnableConfigurationProperties(AuthProperties.class)
@EnableTransactionManagement
public class C53Application {

    /**
     * 应用入口。
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(C53Application.class, args);
    }
}
