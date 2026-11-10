package com.yunpei.proxy.infra.flyway;

import java.util.Arrays;
import java.util.List;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Flyway 迁移配置（在 Bean 初始化阶段执行，早于依赖表结构的组件）。
 *
 * <p>Spring Boot 4 + Flyway 10.x 的自动装配在我们环境下不触发，因此显式提供
 * {@link Flyway} Bean 并在创建时 {@link Flyway#migrate()}。
 *
 * <p><strong>脚本布局（classpath {@code db/}，与 profile 无关的 schema 只维护一份）：</strong>
 * <ul>
 *     <li>{@code db/migration/V1__schema.sql} — 全量表结构（原 {@code backend/db/schema.sql}）</li>
 *     <li>{@code db/migration/V2__seed_root_casbin.sql} — dev Root + Casbin 通配 seed</li>
 *     <li>{@code db/migration-prod/} — 预留 prod 专用增量；默认不放重复 schema</li>
 * </ul>
 *
 * <p>默认 locations 均为 {@code classpath:db/migration}；不设 target 则执行全部版本。
 * 可通过 {@code spring.flyway.locations} / {@code spring.flyway.target} 覆盖
 *（见 {@link FlywayMigratorProperties}）。
 *
 * <p>Bean 名 {@code flyway} 供 {@link CasbinConfig} 等通过 {@code @DependsOn} 保证顺序。
 *
 * @author yunpei
 */
@Slf4j
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(FlywayMigratorProperties.class)
public class FlywayMigrator {

    /** 创建并立即执行 migrate 的 Flyway Bean。 */
    @Bean(name = "flyway")
    public Flyway flyway(DataSource dataSource, Environment environment, FlywayMigratorProperties flywayProperties) {
        String configuredLocations = joinLocations(flywayProperties.getLocations());
        String configuredTarget = flywayProperties.getTarget();
        String[] locations = resolveLocations(environment, configuredLocations);
        String target = resolveTarget(environment, configuredTarget);
        log.atInfo()
                .addKeyValue("profiles", Arrays.toString(environment.getActiveProfiles()))
                .addKeyValue("locations", Arrays.toString(locations))
                .addKeyValue("target", target == null || target.isBlank() ? "latest" : target)
                .addKeyValue("logType", "FLYWAY")
                .log("starting migration");

        try {
            FluentConfiguration cfg = Flyway.configure()
                    .dataSource(dataSource)
                    .locations(Arrays.stream(locations).map(Location::new).toArray(Location[]::new))
                    .baselineOnMigrate(true)
                    .baselineVersion("0")
                    .validateOnMigrate(true);
            if (target != null && !target.isBlank()) {
                cfg.target(MigrationVersion.fromVersion(target));
            }
            Flyway flyway = new Flyway(cfg);
            int applied = flyway.migrate().migrationsExecuted;
            log.atInfo()
                    .addKeyValue("logType", "FLYWAY")
                    .addKeyValue("applied", applied)
                    .log("migration complete");
            return flyway;
        } catch (Exception e) {
            log.atError()
                    .addKeyValue("logType", "FLYWAY")
                    .addKeyValue("msg", e.getMessage())
                    .setCause(e)
                    .log("migration FAILED");
            throw e;
        }
    }

    /**
     * 将 Properties 中的 locations 列表拼为逗号串，供 {@link #resolveLocations} 解析。
     */
    static String joinLocations(List<String> locations) {
        if (locations == null || locations.isEmpty()) {
            return "";
        }
        return String.join(",", locations);
    }

    /**
     * 解析迁移目录：配置优先；否则使用 {@code classpath:db/migration}。
     */
    static String[] resolveLocations(Environment environment, String configuredLocations) {
        if (configuredLocations != null && !configuredLocations.isBlank()) {
            return Arrays.stream(configuredLocations.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .toArray(String[]::new);
        }
        return new String[] {"classpath:db/migration"};
    }

    /**
     * 解析迁移目标版本：仅使用显式配置；未配置则不限制。
     */
    static String resolveTarget(Environment environment, String configuredTarget) {
        if (configuredTarget != null && !configuredTarget.isBlank()) {
            return configuredTarget.trim();
        }
        return null;
    }
}
