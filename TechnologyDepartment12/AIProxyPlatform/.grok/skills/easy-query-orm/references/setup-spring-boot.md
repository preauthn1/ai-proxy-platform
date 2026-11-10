# Setup — Spring Boot

The starter auto-configures `EasyQueryClient` and `EasyEntityQuery` from your
Spring-managed `DataSource`. You inject `EasyEntityQuery` and use it directly.

Use this file first for Spring bean registration, starter dependency, or
`easy-query.database` problems. For starter conditions, auto-collected
components, `StarterConfigurer`, `@EasyQueryTrack`, or multi-datasource
behavior, read `spring-boot-starter.md` first. For Kotlin proxy generation
itself, jump straight to `setup-kotlin.md`.

## When to use / not

Use for a Spring Boot project. You still need the proxy processor on the compile path: **APT (`sql-processor`)
for Java**, **KSP (`sql-ksp-processor`) for Kotlin** (Kotlin Spring projects should use Gradle + KSP — see
`setup-kotlin.md` for the KSP block). Entity definition is identical to `setup-java.md` / `setup-kotlin.md`.

## 1. Dependencies (Maven, Java)

```xml
<dependency>
    <groupId>com.easy-query</groupId>
    <artifactId>sql-springboot-starter</artifactId>
    <version>3.2.13</version>
</dependency>
<dependency>
    <groupId>com.easy-query</groupId>
    <artifactId>sql-processor</artifactId>  <!-- APT; KSP instead for Kotlin -->
    <version>3.2.13</version>
    <scope>provided</scope>
</dependency>
```

Current source shows `sql-springboot-starter` already includes the mainstream
easy-query dialect modules on its own compile path. For a normal Spring Boot
starter project, do not repeat `sql-mysql` / `sql-pgsql` / `sql-mssql` /
`sql-oracle` and similar easy-query dialect artifacts unless the project has a
very specific dependency-management reason. What you still need separately is
the JDBC driver, for example `mysql-connector-j` or the PostgreSQL driver.

## 2. application.yml

The config prefix is `easy-query`. In current starter source,
`easy-query.enable` is optional unless you want to turn the starter off with
`false`. The important property for successful client construction is
`easy-query.database`, which selects the dialect from the starter-bundled
dialect modules.

```yaml
spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://127.0.0.1:3306/mydb?serverTimezone=GMT%2B8&characterEncoding=utf-8&useSSL=false&allowMultiQueries=true&rewriteBatchedStatements=true
    username: root
    password: root

easy-query:
  enable: true              # optional in current source; explicit false disables starter
  database: mysql           # dialect: mysql / h2 / pgsql / mssql / oracle ...
  name-conversion: underlined   # camel field -> snake column (myProp -> my_prop); default
  print-sql: true           # log generated SQL
  print-nav-sql: true       # log SQL for navigation/relation queries
  delete-throw: true        # throw on an unconditional delete instead of silently running it
```

Current source uses `@ConditionalOnProperty(matchIfMissing = true)` on
`easy-query.enable`, so missing `enable` still allows auto-configuration.
`name-conversion: underlined` is the default mapping (override per-column with
`@Column("custom_name")`).

## 3. Inject and use

```java
import com.easy.query.api.proxy.client.EasyEntityQuery;
import org.springframework.stereotype.Service;

@Service
public class TopicService {

    private final EasyEntityQuery easyEntityQuery;

    public TopicService(EasyEntityQuery easyEntityQuery) {   // constructor injection
        this.easyEntityQuery = easyEntityQuery;
    }

    public List<Topic> search(String title) {
        return easyEntityQuery.queryable(Topic.class)
                .where(o -> o.title().like(title))
                .orderBy(o -> o.createTime().desc())
                .toList();
    }
}
```

Kotlin is the same bean, with the Kotlin lambda surface:

```kotlin
@Service
class TopicService(private val easyEntityQuery: EasyEntityQuery) {
    fun search(title: String): List<Topic> =
        easyEntityQuery.queryable(Topic::class.java)
            .where { it.title().like(title) }
            .orderBy { it.createTime().desc() }
            .toList()
}
```

## Transactions in Spring

Prefer Spring's declarative transactions — `@Transactional` on the service method participates in the host
transaction; don't also open a manual `beginTransaction()` inside it. See `transaction.md`.

If the project uses tracking-diff save/update features, add the Spring AOP
dependency and use `@EasyQueryTrack` on the public service method. The switch
for disabling that aspect is `easy-query-track.enable: false`, not the older
`easy-query.default-track`.

## Common mistakes

- Omitting `easy-query.database` or leaving it on the unknown default → starter throws `Please select the correct database dialect`.
- Setting `easy-query.enable: false` → starter bootstrap beans are disabled.
- Setting `easy-query.build: false` without defining replacement `EasyQueryClient` / `EasyEntityQuery` beans.
- Setting `easy-query.database` to the wrong dialect value for the actual
  database in use.
- Repeating `sql-mysql` / `sql-pgsql` / other easy-query dialect artifacts
  alongside `sql-springboot-starter` without a specific reason.
- Adding `@Transactional` *and* a manual `beginTransaction()` in the same method (double transaction).
- Forgetting the proxy processor (APT/KSP) — entities compile but proxies are missing.
- Having multiple `DataSource` beans but expecting the default starter to choose the right one automatically.
- Registering a plain `JdbcTypeHandler` bean and expecting starter global binding without `JdbcTypeHandlerReplaceConfigurer`.

## Sources
- 官方文档: `easy-query-doc/src/guide/spring-boot.md` (application.yml, code-first, navigate).
- 源码验证: `EasyQueryStarterAutoConfiguration`, `EasyQueryStarterBuildAutoConfiguration`,
  `SpringBootStarterBuilder`, `EasyQueryProperties`,
  `EasyQueryTrackAopConfiguration` @ `sql-extension/sql-springboot-starter`.
