package com.yunpei.proxy.infra.flyway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

/**
 * {@link FlywayMigrator#resolveLocations} / {@link FlywayMigrator#resolveTarget} 单元测试。
 */
class FlywayLocationResolveTest {

    @Test
    void configuredLocations_takePrecedence() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        String[] locs = FlywayMigrator.resolveLocations(env, "classpath:db/custom, classpath:db/extra");
        assertThat(locs).containsExactly("classpath:db/custom", "classpath:db/extra");
    }

    @Test
    void defaultLocations_alwaysMigrationSharedSchema() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThat(FlywayMigrator.resolveLocations(env, "")).containsExactly("classpath:db/migration");

        env.setActiveProfiles("dev");
        assertThat(FlywayMigrator.resolveLocations(env, null)).containsExactly("classpath:db/migration");
    }

    @Test
    void prodProfile_defaultsTargetUnrestricted() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("prod");
        assertThat(FlywayMigrator.resolveTarget(env, "")).isNull();
        assertThat(FlywayMigrator.resolveTarget(env, null)).isNull();
    }

    @Test
    void devProfile_defaultsTargetUnrestricted() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThat(FlywayMigrator.resolveTarget(env, null)).isNull();
        assertThat(FlywayMigrator.resolveTarget(env, "  ")).isNull();
    }

    @Test
    void configuredTarget_takesPrecedence() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev");
        assertThat(FlywayMigrator.resolveTarget(env, "1")).isEqualTo("1");
    }

    @Test
    void multiProfileContainingProd_defaultsTargetUnrestricted() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "prod");
        assertThat(FlywayMigrator.resolveTarget(env, "")).isNull();
    }
}
