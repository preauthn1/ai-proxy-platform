package com.yunpei.proxy.infra.casbin;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * jcasbin 配置属性。
 *
 * <p>对应 {@code application.yml} 中的 {@code casbin.*} 配置项。
 *
 * @author yunpei
 */
@Data
@Component
@ConfigurationProperties(prefix = "casbin")
public class CasbinProperties {

    /** model.conf 的 classpath 路径 */
    private String model = "casbin/model.conf";

    /** policy 存储表名 */
    private String tableName = "casbin_rule";

    /** 是否由 adapter 自动建表（false=由 Flyway 管理） */
    private boolean autoCreateTable = false;

    /** removePolicy 失败时是否抛异常（false=静默返回 false） */
    private boolean removePolicyFailed = false;
}
