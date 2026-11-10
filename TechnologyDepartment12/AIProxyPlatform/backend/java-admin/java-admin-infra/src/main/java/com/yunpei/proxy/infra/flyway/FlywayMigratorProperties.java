package com.yunpei.proxy.infra.flyway;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Flyway 手工迁移配置属性（供 {@link FlywayMigrator} 使用）。
 *
 * <p>对应 {@code application.yml} 中的 {@code spring.flyway.*} 子集。
 * 不启用 Spring Boot Flyway 自动装配，仅绑定本项目实际消费的字段。
 *
 * @author yunpei
 */
@Data
@ConfigurationProperties(prefix = "spring.flyway")
public class FlywayMigratorProperties {

    /** 迁移脚本目录列表；空则由 FlywayMigrator 按 profile 回退默认。yml 单值与列表均可绑定。 */
    private List<String> locations = new ArrayList<>();

    /** 迁移目标版本；空则不限制，执行到最新。 */
    private String target = "";
}
