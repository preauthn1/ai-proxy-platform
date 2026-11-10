package com.yunpei.proxy.infra.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

/**
 * Flyway / Jackson / Sa-Token / Redis 启动配置回归护栏。
 *
 * <p>记录踩过的坑，固化在测试里防回归：
 * <ol>
 *     <li>Sa-Token 1.45.0 必须用 {@code sa-token-spring-boot4-starter}（老 starter 拒绝 SB 4）</li>
 *     <li>Spring Boot 4 用 Jackson 3.x（{@code tools.jackson}），旧枚举值已废弃</li>
 *     <li>Flyway 自动装配在 SB 4 下不触发，改用 {@code FlywayMigrator} 手工跑</li>
 *     <li>Redis 走 Spring Data Redis + Lettuce；无密码环境 password 须 YAML null（{@code ~}）</li>
 *     <li>项目不再引入 Redisson / Nacos</li>
 * </ol>
 *
 * <p>本测试运行 cwd = {@code backend/java-admin/java-admin-infra}（module 根）。
 * 所有相对路径都从该目录算起。
 *
 * @author yunpei
 */
class InfraBootstrapGuardrailsTest {

    private static final String API_DIR = "../java-admin-api";
    private static final String INFRA_DIR = ".";
    private static final String PARENT_DIR = "..";

    @Test
    void poms_doNotDependOnRedissonOrNacos() throws IOException {
        String infraPom = readFile(INFRA_DIR + "/pom.xml");
        String parentPom = readFile(PARENT_DIR + "/pom.xml");
        String apiPom = readFile(API_DIR + "/pom.xml");

        assertThat(infraPom)
                .as("infra/pom.xml must NOT depend on redisson/nacos")
                .doesNotContain("redisson")
                .doesNotContain("nacos")
                .doesNotContain("spring-cloud-alibaba");
        assertThat(parentPom)
                .as("parent pom must NOT manage redisson/nacos")
                .doesNotContain("redisson")
                .doesNotContain("nacos")
                .doesNotContain("spring-cloud-alibaba");
        assertThat(apiPom)
                .as("api/pom.xml must NOT depend on nacos")
                .doesNotContain("nacos")
                .doesNotContain("spring-cloud-alibaba");
    }

    @Test
    void applicationYml_hasNoNacosConfig() throws IOException {
        String content = readFile(API_DIR + "/src/main/resources/application.yml");

        assertThat(content)
                .as("application.yml must NOT configure nacos")
                .doesNotContain("nacos")
                .doesNotContain("NACOS_");
    }

    @Test
    void applicationDevYml_declaresSpringDataRedis() throws IOException {
        String content = readFile(API_DIR + "/src/main/resources/application-dev.yml");

        assertThat(content)
                .as("dev yml must declare spring.data.redis.* for Lettuce")
                .containsPattern("(?m)data:\\s*\\n\\s+redis:");
    }

    @Test
    void applicationTestYml_usesNullPasswordForNoAuthRedis() throws IOException {
        String content = readFile(API_DIR + "/src/main/resources/application-test.yml");

        // 测试环境 Redis 无密码：password 必须显式 ~（null），不能是 "" 或默认 123456
        assertThat(content)
                .as("test yml must set spring.data.redis.password to YAML null (~)")
                .containsPattern("(?m)^\\s+password:\\s*~\\s*$");
        assertThat(content)
                .as("test yml must NOT hardcode a redis password default")
                .doesNotContainPattern("(?m)^\\s+password:\\s*\\$\\{REDIS_PASSWORD");
    }

    @Test
    void applicationYml_usesNoJackson2Config() throws IOException {
        String content = readFile(API_DIR + "/src/main/resources/application.yml");

        // SB 4 用 Jackson 3.x（tools.jackson），旧枚举值已废弃。
        // 注意：yml 注释里会提及这些旧枚举名（解释 why we don't use them），
        // 所以这里用正则匹配实际的配置行（jackson: 块下有这些 key）。
        assertThat(content)
                .as("application.yml must NOT configure jackson.serialization.write-bigdecimal-as-plain")
                .doesNotContainPattern("(?m)^\\s+serialization:\\s*\\n\\s+write-bigdecimal-as-plain:");
        assertThat(content)
                .as("application.yml must NOT configure jackson.deserialization.use-big-decimal-for-floats")
                .doesNotContainPattern("(?m)^\\s+deserialization:\\s*\\n\\s+use-big-decimal-for-floats:");
    }

    @Test
    void flywayMigrator_classExists() {
        // Flyway 自动装配不触发，必须有 FlywayMigrator 手工跑迁移
        File file = new File(INFRA_DIR + "/src/main/java/com/yunpei/proxy/infra/flyway/FlywayMigrator.java");
        assertThat(file)
                .as("FlywayMigrator.java must exist at %s (manual Flyway runner for SB 4)", file.getAbsolutePath())
                .exists();
    }

    @Test
    void infraPom_usesSaTokenSpringBoot4Starter() throws IOException {
        String content = readFile(INFRA_DIR + "/pom.xml");

        // Sa-Token 1.45.0 必须用 sa-token-spring-boot4-starter（专用 SB 4 starter）
        assertThat(content)
                .as("infra/pom.xml must use sa-token-spring-boot4-starter for SB 4 compat")
                .contains("sa-token-spring-boot4-starter");
        assertThat(content)
                .as("infra/pom.xml must NOT use legacy sa-token-spring-boot-starter")
                .doesNotContainPattern("(?m)<artifactId>sa-token-spring-boot-starter</artifactId>");
    }

    @Test
    void saTokenConfig_doesNotManuallyDefineSaTokenDao() throws IOException {
        // 1.45.0 starter 自动装配 SaTokenDaoForRedisTemplate；我们不能自己再定义一个（双 bean 冲突）
        String content = readFile(INFRA_DIR + "/src/main/java/com/yunpei/proxy/infra/satoken/SaTokenConfig.java");
        assertThat(content)
                .as("SaTokenConfig must NOT define a saTokenDao bean (auto-configured by starter)")
                .doesNotContainPattern("(?m)public\\s+SaTokenDao\\s+saTokenDao");
    }

    /**
     * 从 module 根（cwd）读文件。infra 模块的测试运行 cwd = backend/java-admin/java-admin-infra。
     */
    private String readFile(String path) throws IOException {
        File file = new File(path);
        assertThat(file)
                .as("file " + path + " (cwd=" + new File(".").getAbsolutePath() + ")")
                .exists();
        return Files.readString(file.toPath(), StandardCharsets.UTF_8);
    }
}
