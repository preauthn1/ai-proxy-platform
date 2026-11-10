package com.yunpei.proxy.infra.casbin;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.casbin.adapter.JDBCAdapter;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.flywaydb.core.Flyway;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.io.ClassPathResource;

/**
 * jcasbin Enforcer 配置。
 *
 * <p>使用 JDBC Adapter 连接 MySQL {@code casbin_rule} 表，model 从 classpath 加载。
 * 表结构由 Flyway V1（schema v10）创建，adapter 不自动建表（{@code autoCreateTable=false}）。
 * dev seed（V2）写入 Root 通配 policy：{@code p, 1, /*, *}。
 *
 * <p>{@link Enforcer} 依赖 {@code flyway} Bean，确保 migrate/seed 完成后再 loadPolicy。
 *
 * <p>配置项见 {@link CasbinProperties}（{@code casbin.*} 前缀）。
 *
 * <p>Model 采用 ACL（{@code r = sub, obj, act}）+ keyMatch2 路径匹配 + act='*' 方法通配；
 * 无角色继承 g（本波按用户展开 p 策略）。
 *
 * @author yunpei
 */
@Configuration(proxyBeanMethods = false)
@RequiredArgsConstructor
public class CasbinConfig {

    private final CasbinProperties casbinProperties;

    /**
     * 创建 jcasbin Enforcer Bean。
     *
     * @param dataSource Spring 管理的 DataSource（HikariCP）
     * @param flyway 已 migrate 的 Flyway（保证 casbin_rule 存在）
     * @return 已加载 policy 的 Enforcer
     */
    @Bean
    @DependsOn("flyway")
    public Enforcer enforcer(DataSource dataSource, Flyway flyway) {
        // flyway 参数 + @DependsOn：保证 migrate/seed 先于 loadPolicy
        Objects.requireNonNull(flyway, "Flyway bean 未就绪，无法初始化 jcasbin");
        try {
            String modelText = loadClasspathResource(casbinProperties.getModel());
            Model model = new Model();
            model.loadModelFromText(modelText);

            // JDBCAdapter(DataSource, removePolicyFailed, tableName, autoCreateTable)
            JDBCAdapter adapter = new JDBCAdapter(
                    dataSource,
                    casbinProperties.isRemovePolicyFailed(),
                    casbinProperties.getTableName(),
                    casbinProperties.isAutoCreateTable());
            Enforcer enforcer = new Enforcer(model, adapter);
            enforcer.loadPolicy();
            return enforcer;
        } catch (Exception e) {
            throw new IllegalStateException("初始化 jcasbin Enforcer 失败: " + e.getMessage(), e);
        }
    }

    /**
     * 从 classpath 读取文件内容为字符串。
     *
     * @param path classpath 路径
     * @return 文件内容
     * @throws Exception 读取失败
     */
    private static String loadClasspathResource(String path) throws Exception {
        try (InputStream is = new ClassPathResource(path).getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        }
    }
}
