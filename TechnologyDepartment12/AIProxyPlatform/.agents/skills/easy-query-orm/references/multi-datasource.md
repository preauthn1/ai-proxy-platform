# Multi-datasource

Use this file for Spring Boot multi-datasource answers.

This topic has two different solution shapes in the official docs, and the
skill should keep them separate:

1. multiple explicit `EasyQueryClient` / `EasyEntityQuery` beans
2. runtime switching on top of baomidou `DynamicRoutingDataSource`

Do not present doc-demo helper types such as `EasyMultiEntityQuery` or
`DefaultEasyMultiEntityQuery` as built-in easy-query framework APIs. They are
example-layer wrappers from `easy-query-doc`, not current-source framework
types.

## Default starter boundary

Current Spring Boot starter source builds the default client from exactly one
injected `DataSource`:

```java
public EasyQueryClient easyQueryClient(
        DataSource dataSource,
        EasyQueryProperties easyQueryProperties,
        EasyQueryInitializeOption easyQueryInitializeOption,
        StarterConfigurer starterConfigurer) {
    return SpringBootStarterBuilder.buildClient(
        dataSource,
        easyQueryProperties,
        easyQueryInitializeOption,
        starterConfigurer
    );
}
```

So:

- one default `EasyQueryClient` is fine when one `@Primary` `DataSource` is
  acceptable
- several fixed datasources usually mean custom bean construction
- `easy-query.build=false` is the clean way to disable the default single-client
  build path when you need several custom clients

`StarterConfigurer` still matters in multi-datasource setups, but it is a hook
used during one `SpringBootStarterBuilder.buildClient(...)` call. It does not
by itself make the default starter build multiple clients.

## `DataSourceRoute<?>` is different from multi-client wiring

Starter source collects `DataSourceRoute<?>` beans into the runtime
`DataSourceRouteManager`.

That is easy-query's datasource-route / sharding registration path:

- useful when the problem is routing inside one easy-query runtime
- not the same thing as "I need two separately injected `EasyEntityQuery`
  beans"
- not the same thing as baomidou `DynamicRoutingDataSource`

If the user is actually asking about sharding metadata or route rules, pair
this file with `advanced.md`.

## Solution A: multiple explicit clients

Use this answer shape when the project has several fixed datasources and wants
separate injection points.

Recommended starter boundary:

```yaml
easy-query:
  build: false
```

Then build the beans yourself:

```java
@Configuration
public class EasyQueryMultiDbConfiguration {

    @Bean("db1EasyQueryClient")
    public EasyQueryClient db1EasyQueryClient(
            @Qualifier("db1DataSource") DataSource dataSource,
            EasyQueryProperties properties,
            EasyQueryInitializeOption option,
            StarterConfigurer starterConfigurer) {
        return SpringBootStarterBuilder.buildClient(
            dataSource,
            properties,
            option,
            starterConfigurer
        );
    }

    @Bean("db1EntityQuery")
    public EasyEntityQuery db1EntityQuery(
            @Qualifier("db1EasyQueryClient") EasyQueryClient client) {
        return new DefaultEasyEntityQuery(client);
    }
}
```

Answer guidance:

- mention `@Qualifier` for each datasource/client/query bean
- mention `@Primary` only when one default bean is intentionally exposed
- mention that `SpringConnectionManager` / `SpringDataSourceUnitFactory` still
  come from `SpringBootStarterBuilder.buildClient(...)`

## Solution B: baomidou `DynamicRoutingDataSource`

The official docs also show a Spring Boot integration using baomidou
`DynamicRoutingDataSource`.

Current baomidou source shows:

- datasource lookup is name-based via `getDataSource(String ds)`
- empty key falls back to the configured primary datasource
- grouped datasource names use underscore-prefix grouping rules

So this solution is about:

- one Spring `DataSource` bean of type `DynamicRoutingDataSource`
- easy-query instances built against that dynamic datasource or rebuilt per
  discovered datasource
- switching by datasource key at runtime

The official doc-demo wrapper shape is:

```java
public interface EasyMultiEntityQuery extends EasyEntityQuery {
    void setCurrent(String dataSource);
    EasyEntityQuery getByDataSource(String dataSource);
    <TResult> TResult executeScope(String dataSource, Function<EasyEntityQuery, TResult> fn);
    void clear();
}
```

Important: this interface is a doc-demo pattern, not a built-in framework API.

Use this answer shape when the user explicitly mentions:

- baomidou `dynamic-datasource`
- `DynamicRoutingDataSource`
- `@DS`
- dynamic switching by datasource key

Prefer `executeScope(...)` / try-finally style scoping over bare
`setCurrent(...)` so the thread-local datasource context is always cleared.

## `@EasyQueryTrack(tag=...)` with multiple clients

Current starter AOP source gathers `EasyQueryClient` beans from the Spring
application context and indexes track managers by bean name.

Therefore:

- `@EasyQueryTrack(tag = "db1EasyQueryClient")` matches the `EasyQueryClient`
  bean name
- blank `tag` means all discovered clients
- comma-separated tags select several named clients
- if a tag has no matching client bean name, the starter logs a warning and
  skips that track manager

Do not explain `tag` as matching datasource names unless the client bean names
were intentionally aligned that way and the answer states that assumption.

## Common mistakes

- Presenting `EasyMultiEntityQuery` as a built-in easy-query type.
- Claiming the default starter auto-builds one `EasyQueryClient` per
  datasource.
- Treating `DataSourceRoute<?>` registration as the same thing as Spring Boot
  multi-client wiring.
- Using thread-local datasource switching without a guaranteed `clear()`.
- Explaining `@EasyQueryTrack(tag=...)` as matching datasource properties
  instead of `EasyQueryClient` bean names.

## Sources

- `easy-query-doc/src/guide/sb-multi-datasource.md`
- `easy-query-doc/src/guide/sb-multi-datasource2.md`
- `dynamic-datasource-spring/.../DynamicRoutingDataSource.java`
- `sql-extension/sql-springboot-starter/.../EasyQueryStarterBuildAutoConfiguration.java`
- `sql-extension/sql-springboot-starter/.../SpringBootStarterBuilder.java`
- `sql-extension/sql-springboot-starter/.../EasyQueryTrackAopConfiguration.java`
- `sql-extension/sql-springboot-starter/.../config/EasyQueryInitializeOption.java`
