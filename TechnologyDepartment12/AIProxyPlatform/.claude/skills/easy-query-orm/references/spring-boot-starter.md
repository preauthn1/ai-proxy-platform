# Spring Boot Starter

Use this reference for Spring Boot starter behavior, auto-configured beans,
property semantics, extension bean registration, `StarterConfigurer`,
`@EasyQueryTrack`, or multi-datasource/custom-build decisions.

This file is source-backed from the current `sql-springboot-starter` module.
Prefer it over older docs when they conflict.

Boundary:

- `setup-spring-boot.md` is the first stop for dependency, yml, and basic bean
  injection
- `configuration-starter.md` is property-defaults/property-impact only
- this file is the starter-internals and extension-registration guide

## What the starter auto-configures

The starter registers three auto-configuration classes:

1. `EasyQueryStarterAutoConfiguration`
   - chooses logging implementation
   - provides default `StarterConfigurer`
   - collects Spring beans into `EasyQueryInitializeOption`
2. `EasyQueryStarterBuildAutoConfiguration`
   - creates `EasyQueryClient`
   - creates `EasyEntityQuery`
3. `EasyQueryTrackAopConfiguration`
   - enables `@EasyQueryTrack` AOP scope

Boot 2/3 use `sql-springboot-starter`. Boot 4 uses
`sql-springboot4-starter` with the same role split.

Current source also shows that `sql-springboot-starter` already has compile
dependencies on the mainstream easy-query dialect artifacts such as
`sql-mysql`, `sql-pgsql`, `sql-mssql`, `sql-oracle`, `sql-h2`, `sql-sqlite`,
`sql-db2`, `sql-clickhouse`, `sql-dameng`, `sql-kingbase-es`,
`sql-gauss-db`, `sql-tsdb`, and `sql-duckdb`. So for ordinary starter usage,
do not tell users to add another easy-query dialect artifact on top of the
starter unless they have a concrete dependency-management reason. The separate
thing users still need is the JDBC driver for their database.

## Condition semantics that matter

Current source behavior is:

- `@ConditionalOnBean(DataSource.class)`:
  no Spring-managed `DataSource` bean, no starter beans.
- `easy-query.enable` on `EasyQueryStarterAutoConfiguration` uses
  `matchIfMissing = true`:
  missing property still enables auto-configuration; explicit `false`
  disables it.
- `easy-query.build` on `EasyQueryStarterBuildAutoConfiguration` uses
  `matchIfMissing = true`:
  missing property still builds default `EasyQueryClient` /
  `EasyEntityQuery`; explicit `false` disables only the default build step.
- most starter beans use `@ConditionalOnMissingBean`:
  user-defined beans can replace starter defaults cleanly.

Practical reading:

- `easy-query.enable` is a disable switch in current source, not a mandatory
  `true` flag.
- `easy-query.build=false` is the clean escape hatch when you want to keep
  starter property binding + component collection but build your own
  `EasyQueryClient`.
- `easy-query.database` is still required because the starter chooses among its
  bundled dialect modules by property; bundling the artifacts does not remove
  the need to set the dialect value.

## Auto-collected extension beans

When the starter creates `EasyQueryInitializeOption`, it collects Spring beans
by type and applies them to the built client.

High-value bean types:

| Spring bean type | Registration behavior | Notes |
|---|---|---|
| `Interceptor` | `QueryConfiguration.applyInterceptor(...)` | tenant/data-permission/audit stack |
| `LogicDeleteStrategy` | `applyLogicDeleteStrategy(...)` | `@Component` is enough under starter |
| `VersionStrategy` | `applyEasyVersionStrategy(...)` | optimistic-lock extension |
| `ValueConverter<?, ?>` | `applyValueConverter(...)` | includes `ValueAutoConverter` because it extends `ValueConverter` |
| `ShardingInitializer` | `applyShardingInitializer(...)` | sharding bootstrap |
| `EncryptionStrategy` | `applyEncryptionStrategy(...)` | field encryption |
| `TableRoute<?>` | `TableRouteManager.addRoute(...)` | table routing |
| `DataSourceRoute<?>` | `DataSourceRouteManager.addRoute(...)` | datasource routing |
| `ColumnValueSQLConverter` | `applyColumnValueSQLConverter(...)` | SQL-side column transform |
| `GeneratedKeySQLColumnGenerator` | `applyGeneratedKeySQLColumnGenerator(...)` | generated key SQL |
| `PrimaryKeyGenerator` | `applyPrimaryKeyGenerator(...)` | custom PK generation |
| `NavigateExtraFilterStrategy` | `applyNavigateExtraFilterStrategy(...)` | relation extra filter |
| `NavigateValueSetter` | `applyNavigateValueSetter(...)` | relation value setting |
| `EntityRelationPropertyProvider` | `applyRelationPropertyProvider(...)` | implicit relation metadata provider |

Important exception:

- plain `JdbcTypeHandler` beans are not auto-bound by type alone
- the starter only appends handler bindings when the bean also implements
  `JdbcTypeHandlerReplaceConfigurer`
- that interface supplies `allowTypes()` and `replace()`

So under Spring Boot:

- `@Component` on `Interceptor` / `LogicDeleteStrategy` / `ValueConverter` is
  sufficient
- `@Component` on `PrimaryKeyGenerator` / `GeneratedKeySQLColumnGenerator` is
  sufficient
- `@Component` on `JdbcTypeHandler` alone is not sufficient for global type
  replacement; implement `JdbcTypeHandlerReplaceConfigurer` too
- `SaveEntitySetPrimaryKeyGenerator` is not in this auto-collected bean list;
  do not claim a plain `@Component` auto-binds it the same way

## Default beans and replacement points

The starter provides these default beans only when the user has not already
defined them:

| Bean | Source behavior | How to replace |
|---|---|---|
| `StarterConfigurer` | defaults to `DefaultStarterConfigurer` | define your own `StarterConfigurer` bean |
| `EasyQueryInitializeOption` | built from collected bean maps | define your own only if you need full control |
| `EasyQueryClient` | built from the single injected `DataSource` | define your own bean or set `easy-query.build=false` |
| `EasyEntityQuery` | `new DefaultEasyEntityQuery(easyQueryClient)` | define your own bean if needed |

For `StarterConfigurer`, you usually do not need `@Primary`. The default bean
is guarded by `@ConditionalOnMissingBean`; a single user bean is enough.
`@Primary` is only needed if you intentionally register multiple
`StarterConfigurer` beans.

## What `StarterConfigurer` is for

`StarterConfigurer` is the Spring Boot hook for replacing internal services
used by the bootstrapper.

Use it when you need to:

- replace `NameConversion`
- replace mapping rules or internal services
- install custom service implementations not exposed as plain config

It runs inside `SpringBootStarterBuilder.buildClient(...)` before `.build()`.

Prefer `StarterConfigurer` for service replacement. Prefer extension beans
(`Interceptor`, `ValueConverter`, `LogicDeleteStrategy`, routes, etc.) for
normal ORM capability registration.

## Property groups worth knowing

Read `configuration-starter.md` for the full default matrix. The current
high-value Spring Boot properties are:

### Bootstrap and lifecycle

- `easy-query.enable`
- `easy-query.build`
- `easy-query.database`
- `easy-query.log-class`

### Mapping and query-shape

- `easy-query.name-conversion`
- `easy-query.map-key-conversion`
- `easy-query.mapping-strategy`
- `easy-query.default-condition`
- `easy-query.auto-include-table`
- `easy-query.map-to-bean-strict`
- `easy-query.default-schema`

### Write safety and diagnostics

- `easy-query.delete-throw`
- `easy-query.insert-strategy`
- `easy-query.update-strategy`
- `easy-query.print-sql`
- `easy-query.print-nav-sql`
- `easy-query.sql-parameter-print`
- `easy-query.warning-column-miss`
- `easy-query.result-size-limit`

### Relation/performance

- `easy-query.relation-group-size`
- `easy-query.include-limit-mode`
- `easy-query.max-in-clause-size`
- `easy-query.reverse-offset-threshold`

### Sharding-only

- `easy-query.sharding`
- `easy-query.connection-mode`
- `easy-query.sharding-query-in-transaction`
- `easy-query.max-sharding-query-limit`
- `easy-query.default-data-source-merge-pool-size`
- `easy-query.executor-maximum-pool-size`
- `easy-query.executor-core-pool-size`
- `easy-query.executor-queue-size`
- `easy-query.sharding-execute-timeout-millis`
- `easy-query.max-sharding-route-count`
- `easy-query.throw-if-route-not-match`
- `easy-query.multi-conn-wait-timeout-millis`
- `easy-query.sharding-fetch-size`
- `easy-query.start-time-job`

### Tracking AOP

- `easy-query-track.enable`

## Current-source corrections versus older docs

Older easy-query docs/examples may mention properties such as:

- `easy-query.default-track`
- `easy-query.property-mode`
- `easy-query.query-large-column`
- `easy-query.no-version-error`
- `easy-query.relation-table-append`
- `easy-query.update-batch-threshold`

These are not current `EasyQueryProperties` fields in the source inspected
here. Do not present them as current Spring Boot starter properties unless the
project version proves otherwise.

Tracking in current starter is controlled by:

- `easy-query-track.enable` for enabling/disabling the AOP configuration
- `@EasyQueryTrack` for opening the tracking scope per public method

## `@EasyQueryTrack` under Spring Boot

The starter auto-registers an aspect for `@EasyQueryTrack` when
`easy-query-track.enable` is not set to `false`.

Source-backed behavior:

- the aspect targets public methods annotated with `@EasyQueryTrack`
- blank `tag` means all `EasyQueryClient` beans participate
- comma-separated `tag` values select specific client bean names

Inference from Spring AOP:

- self-invocation on the same bean will not pass through the proxy
- non-public methods will not match this pointcut

If a project uses `savable` / tracking-diff updates, do not tell the user to
set an old `default-track` starter property. Use `@EasyQueryTrack` and the
current tracking APIs instead.

## Transaction integration and why Spring-managed `DataSource` matters

`SpringBootStarterBuilder` replaces:

- `ConnectionManager` with `SpringConnectionManager`
- `DataSourceUnitFactory` with `SpringDataSourceUnitFactory`

That makes easy-query reuse Spring transaction-bound connections via
`DataSourceUtils` for the normal path.

Practical rules:

- use a Spring-managed `DataSource` bean
- prefer Spring `@Transactional` in service methods
- do not assume a manually new'ed `DataSource` outside Spring bean management
  will participate in Spring transactions

## Multi-datasource boundary

The default build bean method injects a single `DataSource`:

```java
public EasyQueryClient easyQueryClient(DataSource dataSource, ...)
```

So with multiple `DataSource` beans:

- `@Primary` may be enough only if one default client is acceptable
- otherwise prefer manual bean creation with `@Qualifier`
- `easy-query.build=false` is the clean way to disable the default single-client
  build while keeping other starter facilities available

For multiple clients, pair client bean names with `@EasyQueryTrack(tag="...")`
when tracking should target only one or several clients.

## Common failure patterns

### `EasyQueryClient` / `EasyEntityQuery` injection fails

Check in this order:

1. Is there a Spring `DataSource` bean?
2. Did someone set `easy-query.enable=false`?
3. Did someone set `easy-query.build=false` without defining replacement beans?
4. Is there a multi-datasource ambiguity on `DataSource` injection?

### `Please select the correct database dialect`

Current builder throws this when `easy-query.database` stays `UNKNOWN` or does
not map to the intended dialect config. Fix the property first.

### Custom `JdbcTypeHandler` bean seems ignored

It is likely missing `JdbcTypeHandlerReplaceConfigurer`; plain bean discovery
is not enough for handler type binding.

### Tracking does not start

Check:

1. `easy-query-track.enable` not set to `false`
2. AOP infrastructure is present in the Spring app
3. the annotated method is public
4. the call is not same-class self-invocation

## Source anchors

- `sql-extension/sql-springboot-starter/.../EasyQueryStarterAutoConfiguration.java`
- `sql-extension/sql-springboot-starter/.../EasyQueryStarterBuildAutoConfiguration.java`
- `sql-extension/sql-springboot-starter/.../SpringBootStarterBuilder.java`
- `sql-extension/sql-springboot-starter/.../config/EasyQueryInitializeOption.java`
- `sql-extension/sql-springboot-starter/.../config/EasyQueryProperties.java`
- `sql-extension/sql-springboot-starter/.../config/EasyQueryTrackProperties.java`
- `sql-extension/sql-springboot-starter/.../config/JdbcTypeHandlerReplaceConfigurer.java`
- `sql-extension/sql-springboot-starter/.../conn/SpringConnectionManager.java`
- `sql-extension/sql-springboot-starter/.../conn/SpringDataSourceUnit.java`
- `sql-core/.../bootstrapper/StarterConfigurer.java`
- `sql-core/.../annotation/EasyQueryTrack.java`
