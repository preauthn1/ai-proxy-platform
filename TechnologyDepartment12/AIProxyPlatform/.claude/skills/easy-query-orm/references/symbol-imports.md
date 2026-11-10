# Symbol Imports

Use this reference when easy-query code, docs, or user snippets mention a type
name but omit the import.

This is the package/FQCN branch, not the semantic feature guide. Resolve the
type here first, then jump back to `api-map.md` or the feature reference for
behavior.

This table is intentionally maintained statically inside the skill so the skill
does not depend on any machine-local easy-query source checkout path.

## Workflow

1. Resolve the type from this maintained import map first.
2. If the symbol is missing here, add it here rather than reviving a
   machine-local lookup helper.
3. Prefer source-verified entries over blog/demo snippets.
4. If the current project already imports one of several matches, prefer the
   project import.
5. When answering with non-obvious easy-query types, include the explicit
   `import ...;` line or the FQCN on first mention.

## Nested Type Rule

Import the owner type, not the nested member:

- `EasyWhereCondition.Condition` ->
  `import com.easy.query.core.annotation.EasyWhereCondition;`
- `Select.DRAFT` / `Select.TUPLE` ->
  `import com.easy.query.core.proxy.sql.Select;`

If the missing symbol is really a method/field rather than a type, switch to
`api-map.md` or the feature reference instead of forcing an import lookup.

## High-Frequency Imports

### Entry Points

| Symbol | Import |
|--------|--------|
| `EasyEntityQuery` | `import com.easy.query.api.proxy.client.EasyEntityQuery;` |
| `DefaultEasyEntityQuery` | `import com.easy.query.api.proxy.client.DefaultEasyEntityQuery;` |
| `EasyQueryClient` | `import com.easy.query.core.api.client.EasyQueryClient;` |
| `EasyQueryBootstrapper` | `import com.easy.query.core.bootstrapper.EasyQueryBootstrapper;` |
| `EasyProxyParamExpressionUtil` | `import com.easy.query.api.proxy.util.EasyProxyParamExpressionUtil;` |
| `Expression` | `import com.easy.query.core.proxy.core.Expression;` |
| `PropTypeColumn` | `import com.easy.query.core.proxy.PropTypeColumn;` |
| `ProxyEntityAvailable` | `import com.easy.query.core.proxy.ProxyEntityAvailable;` |
| `Transaction` | `import com.easy.query.core.basic.jdbc.tx.Transaction;` |

### Mapping / Annotation

Most core entity annotations come from `com.easy.query.core.annotation`:

```java
import com.easy.query.core.annotation.Table;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.EntityFileProxy;
import com.easy.query.core.annotation.EasyAlias;
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.ColumnIgnore;
import com.easy.query.core.annotation.InsertIgnore;
import com.easy.query.core.annotation.UpdateIgnore;
import com.easy.query.core.annotation.ProxyProperty;
import com.easy.query.core.annotation.Version;
import com.easy.query.core.annotation.LogicDelete;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.IncludeOnProperty;
import com.easy.query.core.annotation.NavigateFlat;
import com.easy.query.core.annotation.EasyTree;
import com.easy.query.core.annotation.ShardingTableKey;
import com.easy.query.core.annotation.EasyQueryTrack;
import com.easy.query.core.annotation.EasyWhereCondition;
import com.easy.query.core.annotation.Enumerated;  // on the enum TYPE, not the field
import com.easy.query.core.annotation.SaveKey;  // on the entity FIELD, non-PK savable matching key
```

Related enums that commonly pair with those annotations:

```java
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.enums.SQLExecuteStrategyEnum;
import com.easy.query.core.enums.SubQueryModeEnum;
import com.easy.query.core.basic.extension.logicdel.LogicDeleteStrategyEnum;
```

### Search Form / Dynamic Query

| Symbol | Import |
|--------|--------|
| `WhereConditionProvider` | `import com.easy.query.core.api.dynamic.executor.query.WhereConditionProvider;` |
| `WhereObjectQueryExecutor` | `import com.easy.query.core.api.dynamic.executor.query.WhereObjectQueryExecutor;` |
| `ConfigureArgument` | `import com.easy.query.core.api.dynamic.executor.query.ConfigureArgument;` |
| `ObjectSortBuilder` | `import com.easy.query.core.api.dynamic.sort.ObjectSortBuilder;` |
| `OrderByModeEnum` | `import com.easy.query.core.func.def.enums.OrderByModeEnum;` |
| `NotNullOrEmptyValueFilter` | `import com.easy.query.core.expression.builder.core.NotNullOrEmptyValueFilter;` |

### Projection / Analytics

| Symbol | Import |
|--------|--------|
| `Select` | `import com.easy.query.core.proxy.sql.Select;` |
| `GroupKeys` | `import com.easy.query.core.proxy.sql.GroupKeys;` |
| `EasyBehaviorEnum` | `import com.easy.query.core.enums.EasyBehaviorEnum;` |

### Key Generation

```java
import com.easy.query.core.configuration.QueryConfiguration;
import com.easy.query.core.basic.extension.generated.PrimaryKeyGenerator;
import com.easy.query.core.basic.extension.generated.GeneratedKeySQLColumnGenerator;
import com.easy.query.core.basic.extension.generated.SaveEntitySetPrimaryKeyGenerator;
```

### Conversion / Interceptor

```java
import com.easy.query.core.basic.extension.conversion.ValueConverter;
import com.easy.query.core.basic.extension.conversion.ValueAutoConverter;
import com.easy.query.core.basic.extension.conversion.NamedEnumValueAutoConverter;
import com.easy.query.core.basic.extension.conversion.ColumnValueSQLConverter;
import com.easy.query.core.basic.jdbc.types.handler.JdbcTypeHandler;
import com.easy.query.core.basic.extension.interceptor.Interceptor;
import com.easy.query.core.basic.extension.interceptor.EntityInterceptor;
import com.easy.query.core.basic.extension.interceptor.PredicateFilterInterceptor;
import com.easy.query.core.basic.extension.interceptor.UpdateSetInterceptor;
import com.easy.query.core.basic.extension.interceptor.UpdateEntityColumnInterceptor;
import com.easy.query.core.basic.extension.interceptor.ProtectedInterceptor;
import com.easy.query.core.basic.extension.logicdel.LogicDeleteStrategy;
import com.easy.query.core.basic.extension.version.VersionStrategy;
import com.easy.query.core.basic.extension.encryption.EncryptionStrategy;
import com.easy.query.core.basic.extension.navigate.NavigateExtraFilterStrategy;
import com.easy.query.core.basic.extension.navigate.NavigateValueSetter;
import com.easy.query.core.expression.implicit.EntityRelationPropertyProvider;
```

### Sharding / Routing

```java
import com.easy.query.core.sharding.initializer.ShardingInitializer;
import com.easy.query.core.sharding.route.table.TableRoute;
import com.easy.query.core.sharding.route.datasource.DataSourceRoute;
```

### Spring Boot / Search Extension

```java
import com.easy.query.sql.starter.SpringBootStarterBuilder;
import com.easy.query.sql.starter.config.EasyQueryProperties;
import com.easy.query.sql.starter.config.EasyQueryTrackProperties;
import com.easy.query.sql.starter.config.EasyQueryInitializeOption;
import com.easy.query.sql.starter.config.JdbcTypeHandlerReplaceConfigurer;
import com.easy.query.core.bootstrapper.StarterConfigurer;
import com.easy.query.search.EasySearch;
import com.easy.query.search.annotation.EasyCond;
```

## Ambiguity Rules

- Do not infer a package from the docs alone when the source declaration can be
  checked.
- Prefer the symbol owner in source even if tests/examples use wildcard imports.
- If multiple source matches remain, report that fact instead of silently
  picking one.
- For compile-ready answers, do not leave non-obvious easy-query types
  unimported just because the original snippet omitted them.
- Keep this file as the single import map. Do not reintroduce local helper
  scripts that depend on machine-specific easy-query source paths.
