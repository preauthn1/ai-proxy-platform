# java-admin：配置用 @ConfigurationProperties 聚合

配置注入统一用类型安全的 Properties 类（`@ConfigurationProperties` 按前缀聚合），例如 `AltchaProperties`、`CasbinProperties`、`FlywayMigratorProperties`。

**原因**：配置键有类型与结构、可在启动期失败、调用点不散落魔法字符串。

**不做**：业务或配置代码中直接用 `@Value` 散落绑定配置键（除非是一次性、无前缀聚合价值的边界情况——当前代码库默认不走这条路）。
