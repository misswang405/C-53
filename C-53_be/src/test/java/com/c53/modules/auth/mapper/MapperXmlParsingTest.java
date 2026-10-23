package com.c53.modules.auth.mapper;

import com.c53.modules.auth.entity.AuthDeviceAccountEntity;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Mapper XML 可发现性与绑定关系测试。
 *
 * <p>不启动 Spring、不连接数据库，直接用 MyBatis 解析 XML，覆盖：
 * <ul>
 *   <li>默认扫描路径（classpath 下 mapper 目录）能找到 3 个 XML</li>
 *   <li>namespace 与 statement id 能正确对应接口方法</li>
 *   <li>resultType、参数名与 SQL 占位符一致</li>
 * </ul>
 * 若注解 SQL 未删除，解析阶段会因 statement 重复而失败。</p>
 */
class MapperXmlParsingTest {

    private static final String BASE = "mapper/com/c53/modules/auth/mapper/";

    /**
     * 解析 3 个自定义 SQL 的 Mapper XML，并校验 statement 与参数绑定。
     */
    @Test
    void xmlFilesAreDiscoverableAndStatementsAreBound() throws Exception {
        // 1. 确认默认 mapper-locations 能扫描到全部 XML
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:/mapper/**/*.xml");
        assertThat(resources)
                .extracting(Resource::getFilename)
                .contains("AuthDeviceAccountMapper.xml",
                        "AuthUserAgreementMapper.xml",
                        "AuthRefreshTokenMapper.xml");

        // 2. 用 MyBatis 原生解析器逐个加载 XML
        Configuration configuration = new Configuration();
        for (String file : List.of("AuthDeviceAccountMapper.xml",
                "AuthUserAgreementMapper.xml",
                "AuthRefreshTokenMapper.xml")) {
            try (InputStream in = Resources.getResourceAsStream(BASE + file)) {
                new XMLMapperBuilder(in, configuration, BASE + file,
                        configuration.getSqlFragments()).parse();
            }
        }

        // 3. namespace + 方法名必须与接口完全对应
        assertThat(configuration.hasStatement(
                "com.c53.modules.auth.mapper.AuthDeviceAccountMapper.upsert")).isTrue();
        assertThat(configuration.hasStatement(
                "com.c53.modules.auth.mapper.AuthUserAgreementMapper.upsert")).isTrue();
        assertThat(configuration.hasStatement(
                "com.c53.modules.auth.mapper.AuthRefreshTokenMapper.selectHashesByFamily")).isTrue();

        // 4. 查询返回 String，参数名与 #{} 一致
        MappedStatement select = configuration.getMappedStatement(
                "com.c53.modules.auth.mapper.AuthRefreshTokenMapper.selectHashesByFamily");
        assertThat(select.getResultMaps().get(0).getType()).isEqualTo(String.class);
        BoundSql selectSql = select.getBoundSql(Map.of("familyId", "fam_1"));
        assertThat(selectSql.getSql()).contains("WHERE family_id = ?");
        assertThat(selectSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .containsExactly("familyId");

        // 5. upsert 的占位符必须能映射到实体属性
        AuthDeviceAccountEntity entity = new AuthDeviceAccountEntity();
        BoundSql upsertSql = configuration.getMappedStatement(
                "com.c53.modules.auth.mapper.AuthDeviceAccountMapper.upsert").getBoundSql(entity);
        assertThat(upsertSql.getParameterMappings())
                .extracting(ParameterMapping::getProperty)
                .contains("id", "deviceId", "userId", "authorizedAt",
                        "lastUsedAt", "createdAt", "updatedAt");
    }
}
