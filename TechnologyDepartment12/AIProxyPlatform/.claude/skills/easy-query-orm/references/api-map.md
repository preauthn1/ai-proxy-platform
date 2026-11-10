# API Map

Use this file to confirm symbol names and choose the next semantic reference.
Keep package/import lookup in `symbol-imports.md`; this file is the semantic
index.

Package hints appear here only when they materially disambiguate the symbol.

## Entry Points

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `EasyEntityQuery` | Default strong-typed proxy DSL client | `query.md` |
| `DefaultEasyEntityQuery` | Concrete `EasyEntityQuery` implementation | `setup-java.md`, `setup-kotlin.md` |
| `EasyQueryClient` | Weak-typed core client | local project first |
| `EasyQueryBootstrapper` | Bootstrap/build the client | `setup-java.md`, `setup-kotlin.md`, `setup-spring-boot.md`, `spring-boot-starter.md` |

Common top-level methods:

`queryable(...)`, `insertable(...)`, `updatable(...)`, `deletable(...)`,
`mapInsertable(...)`, `mapUpdatable(...)`, `savable(...)`,
`beginTransaction()`, `sqlQuery(...)`, `sqlEasyQuery(...)`,
`sqlQueryMap(...)`, `sqlExecute(...)`

## Entity and Mapping Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `@Table` | Table mapping | `entity-mapping.md` |
| `@EntityProxy` | Generate `*Proxy` | `entity-mapping.md` |
| `@EntityFileProxy` | Generate proxy files into source/plugin-managed path | `entity-modeling-advanced.md` |
| `@EasyAlias` | Plugin/source-level lambda alias helper | `entity-modeling-advanced.md` |
| `@Column` | Column mapping options | `entity-mapping.md` |
| `@Column(sqlConversion=...)` | Advanced computed/derived SQL property | `entity-computed-properties.md` |
| `@Column(sqlExpression=...)` | Simple inline SQL computed property | `entity-computed-properties.md` |
| `@Column(conversion=...)` | Field-level `ValueConverter` mapping | `value-conversion-type-handler.md` |
| `@Column(typeHandler=...)` | Field-level JDBC type handler override | `value-conversion-type-handler.md` |
| `@Column(primaryKey = true, generatedKey = true)` | Database-generated primary key | `primary-key-generation.md`, `write-insert-upsert.md` |
| `@Column(primaryKey = true, primaryKeyGenerator = ...)` | Java-side primary key generation | `primary-key-generation.md` |
| `@Column(generatedSQLColumnGenerator=...)` | Database function/expression generated key SQL | `primary-key-generation.md` |
| `@Enumerated` | Built-in enum-`name()` auto conversion (put on the enum TYPE; stored as varchar) | `value-conversion-type-handler.md` |
| `@ColumnIgnore` / `@InsertIgnore` / `@UpdateIgnore` | Advanced persistence-scope controls for fields | `entity-modeling-advanced.md` |
| `@TableIndex` / `@TableIndexes` | Code-first table index metadata | `entity-modeling-advanced.md` |
| `@ValueObject` | Deprecated current-source value-object field modeling | `entity-computed-properties.md`, `entity-modeling-advanced.md` |
| `@ProxyProperty` | Rename or adjust generated proxy property behavior | `entity-modeling-advanced.md` |
| `@Version` | Optimistic lock | `write-update.md`, `write-delete.md` |
| `@LogicDelete` | Logic-delete column and strategy binding | `logic-delete.md` |
| `@Navigate` | Relation metadata | `relation-query.md`, `entity-modeling-navigate.md` |
| `@IncludeOnProperty` | Conditionally enable a navigation/include path based on root property values | `entity-modeling-navigate.md`, `include-structured-loading.md`, `select-auto-include.md` |
| `@NavigateFlat` | DTO/VO path flattening | `entity-modeling-navigate.md` |
| `@EasyTree` | Mark which self `List<SelfType>` navigation is the tree children path | `entity-modeling-navigate.md`, `implicit-query.md` |
| `ProxyEntityAvailable<TEntity, TProxy>` | Proxy-enabled entity contract | `entity-mapping.md` |
| `RelationTypeEnum` | Relation kind enum | `relation-query.md` |
| `@ShardingTableKey` | Sharding route key | `advanced.md` |

## Query Chain Symbols

Normal query chain: `where(...)`, `where(condition, ...)`, `whereById(...)`,
`whereByIds(...)`, `orderBy(...)`, `select(...)`, `toList()`,
`firstOrNull()`, `singleOrNull()`, `findNotNull(...)`, `toPageResult(...)`,
`toChunk(...)`, `streamBy(...)`

High-value exact terminals and pagination helpers:

- `.any()`
- `.count()`
- `.limit(n)`
- `.limit(offset, rows)`

## Predicate Symbols

Common operators:

- compare: `eq` `ne` `gt` `ge` `lt` `le`
- text: `like` `notLike` `startsWith` `endsWith` `contains`
- null/empty: `isNull` `isNotNull` `isBlank` `isNotBlank`
- set/range: `in` `notIn` `rangeClosed` and related range helpers
- composition: `or(() -> ...)` `and(() -> ...)`

## DTO Query Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `whereObject(request)` | Query/search-form DTO filters; use for many optional form fields, not as a general query default | `dto-object-query.md` |
| `orderByObject(sort)` | Search-form DTO sort / request-driven sort | `dto-object-query.md` |
| `selectAutoInclude(ResultDTO.class)` | DTO graph result | `select-auto-include.md` |
| `TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()` | Root/nested `selectAutoInclude` include-query shaping hook | `select-auto-include.md` |
| `.configure(o -> o.setConfigureArgument(req))` | Pass request-scoped arguments into `selectAutoInclude` extra configure hooks | `select-auto-include.md` |
| `.ignoreNavigateConfigure()` on extra auto include | Make DTO-local include shaping ignore inherited `@Navigate` order/limit settings | `select-auto-include.md` |
| `@EasyWhereCondition` | Query-form filter metadata: `DEFAULT`, `MULTI_OR`, range/in/notIn, relation-path filters | `dto-object-query.md` |
| `ObjectSortBuilder` | Sort allowlist / builder API (`com.easy.query.core.api.dynamic.sort`) | `dto-object-query.md` |
| `WhereObjectQueryExecutor` | Replace default `whereObject` / `@EasyWhereCondition` behavior (`com.easy.query.core.api.dynamic.executor.query`) | `dto-object-query.md` |
| `ConfigureArgument` | Request-scoped argument carrier for `selectAutoInclude` extra configure | `select-auto-include.md` |
| `anyColumn(...)` | Dynamic property path sort/filter helper | `dto-object-query.md` |

## Conversion and JDBC Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `ValueConverter` | Property <-> DB Java value conversion | `value-conversion-type-handler.md` |
| `ValueAutoConverter` | Global auto-applied value converter | `value-conversion-type-handler.md` |
| `ColumnValueSQLConverter` | SQL-side derived/computed property converter | `entity-computed-properties.md`, `value-conversion-type-handler.md` |
| `JdbcTypeHandler` | JDBC parameter/result handler | `value-conversion-type-handler.md` |
| `JdbcTypeHandlerManager.appendHandler(...)` | Replace or append handler by Java type | `value-conversion-type-handler.md` |
| `JdbcTypeHandlerReplaceConfigurer` | Spring Boot auto-registration contract for JDBC handlers | `value-conversion-type-handler.md` |
| `QueryConfiguration.applyValueConverter(...)` | Register converter or auto converter | `value-conversion-type-handler.md` |

## Key Generation Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `PrimaryKeyGenerator` | Java-side primary key generation SPI | `primary-key-generation.md` |
| `GeneratedKeySQLColumnGenerator` | Database SQL/function generated-key SPI | `primary-key-generation.md` |
| `SaveEntitySetPrimaryKeyGenerator` | Runtime save-key safety SPI for request-built entities | `primary-key-generation.md`, `savable-key-safety.md` |
| `QueryConfiguration.applyPrimaryKeyGenerator(...)` | Register a Java-side primary key generator | `primary-key-generation.md` |
| `QueryConfiguration.applyGeneratedKeySQLColumnGenerator(...)` | Register a database SQL generated-key component | `primary-key-generation.md` |

## EasySearch Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `EasySearch.of(...)` | Search object factory (`com.easy.query.search`) | `easy-search.md` |
| `EasySearch.table(...)` | Bind a named EasySearch sub-search class with default dynamic table matching | `easy-search.md` |
| `EasySearch.tableAlias(...)` | Bind a named EasySearch sub-search class to an explicit table alias | `easy-search.md` |
| `EasySearch.tableIndex(...)` | Bind a named EasySearch sub-search class to an explicit joined-table index | `easy-search.md` |
| `EasySearch.param(...)` | Add one or more request-style search parameters to the current `EasySearch` instance | `easy-search.md` |
| `EasySearch.paramMap(...)` | Bulk-load request-style search parameters from a map or `ParamMap` | `easy-search.md` |
| `EasySearch.addDefaultSort(...)` | Add fallback sort expressions used when the incoming sort parameter is absent or empty | `easy-search.md` |
| `@EasyCond` | Search/filter/sort metadata with type-driven default operators (`com.easy.query.search.annotation`) | `easy-search.md` |
| `SearchInjectConfiguration` | Wire EasySearch-specific query/sort executors into the standard dynamic-query extension points | `easy-search.md` |
| `EasySearchObjectSortQueryExecutor` | EasySearch-aware `ObjectSortQueryExecutor` used by `orderByObject(search)` | `easy-search.md` |
| `EasySearchOptionBuilder.setSortParam(...)` | Change the incoming sort request parameter key from the default `sort` | `easy-search.md` |
| `EasySearchOptionBuilder.setStrict(...)` | Enable or disable strict parameter validation in EasySearch parsing | `easy-search.md` |
| `EasySearchOptionBuilder.setDefaultEnabled(...)` | Control whether unannotated fields participate in EasySearch by default | `easy-search.md` |
| `EasySearchOptionBuilder.setIndexEnabled(...)` | Enable or disable indexed parameter names such as `name-1` | `easy-search.md` |
| `EasySearchOptionBuilder.setWhereEnabled(...)` | Enable or disable EasySearch where-condition execution | `easy-search.md` |
| `EasySearchOptionBuilder.setOrderEnabled(...)` | Enable or disable EasySearch sort execution | `easy-search.md` |
| `EasySearchOptionBuilder.setGroupSplitter(...)` | Change the group-prefix separator used in EasySearch parameter keys | `easy-search.md` |
| `EasySearchOptionBuilder.setClassSplitter(...)` | Change the class-prefix separator used in EasySearch parameter keys | `easy-search.md` |
| `EasySearchOptionBuilder.setParamSplitter(...)` | Change the operator suffix separator used in EasySearch parameter keys | `easy-search.md` |
| `EasySearchOptionBuilder.setOrderSplitter(...)` | Change the sort-direction separator used in EasySearch sort parameters | `easy-search.md` |

Typical chain:

`whereObject(search)` + `orderByObject(search)`

For non-form dynamic query composition, read `query.md` instead.

## Interceptor Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `Interceptor` | Base cross-cutting ORM interceptor | `interceptor.md` |
| `EntityInterceptor` | Mutate entity objects on insert/update | `interceptor.md` |
| `PredicateFilterInterceptor` | Cross-cutting where predicate filter (tenant/data permission) | `interceptor.md` |
| `UpdateSetInterceptor` | Add `SET` columns for expression updates | `interceptor.md` |
| `UpdateEntityColumnInterceptor` | Force columns into entity-update column selection | `interceptor.md` |
| `ProtectedInterceptor` | Marker that survives global `noInterceptor()` unless removed by name | `interceptor.md` |
| `QueryConfiguration.applyInterceptor(...)` | Global registration entry point | `interceptor.md` |
| `.useInterceptor(name)` / `.noInterceptor(name)` | Per-expression named control | `interceptor.md` |
| `.useInterceptor()` / `.noInterceptor()` | Re-enable/disable default interceptor set on the current expression | `interceptor.md` |

## Relation and Include Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `.include(...)` | Load entity relations | `include-structured-loading.md` |
| `.include2(...)` | Complex nested include paths | `include-structured-loading.md` |
| `loadInclude(...)` | Load relations after query | `include-structured-loading.md` |
| `fillOne(...)` | Programmatically backfill a to-one relation or nested object graph without `@Navigate` metadata | `include-structured-loading.md` |
| `fillMany(...)` | Programmatically backfill a to-many relation or nested object graph without `@Navigate` metadata | `include-structured-loading.md` |
| `@IncludeOnProperty(matchNull = true)` | Include the relation only when the dependent root property is null/blank | `entity-modeling-navigate.md`, `include-structured-loading.md` |
| `expression().subQueryable(Entity.class)` | Context-aware explicit subquery bound to the current expression | `subquery-explicit.md` |
| `expression().subQuery(query)` | Wrap a query as a scalar subquery expression | `subquery-explicit.md` |
| `exists(query)` / `notExists(query)` | Explicit subquery predicate on the current expression | `subquery-explicit.md` |
| `.subQueryToGroupJoin(...)` | To-many subquery group join | `implicit-query.md` |
| `.subQueryConfigure(...)` | Root-level baseline configuration for later relation subqueries | `implicit-controls.md` |
| `.asTreeCTE()` / `.asTreeCTECustom(...)` | Recursive tree query | `implicit-query.md` |
| `TreeCTEConfigurer.setUp(true)` | Upward recursion / ancestor backfill | `implicit-query.md` |
| `TreeCTEConfigurer.setChildFilter(...)` | Filter the recursive member, not just the seed rows | `implicit-query.md` |
| `TreeCTEConfigurer.setLimitDeep(...)` | Limit recursion depth | `implicit-query.md` |
| `TreeCTEConfigurer.setUnionAll(false)` | Use `UNION` instead of `UNION ALL` | `implicit-query.md` |
| `TreeCTEConfigurer.setDeepColumnName(...)` / `setDeepInCustomSelect(true)` | Control or retain depth column in tree results | `implicit-query.md` |
| `.leftJoin(...)` / `.innerJoin(...)` | Explicit join | `query-composition.md` |

If the result target is a DTO graph, prefer `selectAutoInclude(...)` before
`include(...)`.

## Implicit Control Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `.filter(...)` on relation path | To-many baseline filter or to-one `JOIN ON` filter | `implicit-controls.md` |
| `.configure(...)` on relation path | Relation subquery-level options such as alias or logic-delete behavior | `implicit-controls.md` |
| `.mode(SubQueryModeEnum...)` | Force local subquery strategy | `implicit-controls.md` |
| `SubQueryModeEnum.DEFAULT` | Leave default strategy | `implicit-controls.md` |
| `SubQueryModeEnum.SUB_QUERY_ONLY` | Force subquery | `implicit-controls.md` |
| `SubQueryModeEnum.GROUP_JOIN` | Force group join | `implicit-controls.md` |
| `.any()` / `.any(...)` | Exists / any matched related row | `implicit-controls.md` |
| `.none()` / `.none(...)` | No related row / no matched related row | `implicit-controls.md` |
| `.all(...)` | Every row in the current relation slice passes | `implicit-controls.md` |
| `.first()` | First ranked child in a relation slice | `implicit-query.md` |
| `.element(index)` | Zero-based nth ranked child | `implicit-query.md` |
| `.elements(start,end)` | Zero-based inclusive ranked child window; returns a queryable slice that can continue with aggregate/predicate/joining APIs | `implicit-query.md` |
| `.firstValue()` / `.lastValue()` / `.nthValue(...)` on offset/window chain | Window-value helpers for the offset branch | `typed-sql-expressions.md`, `implicit-query.md` |
| `.joining(...)` on relation chain | Concatenate relation rows, often after `orderBy(...)` / `elements(...)` | `implicit-query.md` |
| `.flatElement()` | Flatten to-many path for traversal or `toList(...)` | `implicit-controls.md` |
| `.notEmptyAll(...)` | Non-empty and every matched row passes | `implicit-controls.md` |
| `.distinct()` on relation chain | Keep distinctness inside implicit subquery | `implicit-controls.md` |
| `.anyValue()` | Project relation existence or a relation-filter boolean as a scalar value in the select list | `implicit-query.md`, `implicit-controls.md` |
| `.noneValue()` | Project relation non-existence or a negated relation-filter boolean as a scalar value in the select list | `implicit-query.md`, `implicit-controls.md` |
| `expression().valueOf(...)` | Project arbitrary predicate as boolean column | `implicit-controls.md` |

## Explicit Subquery Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `.in(query)` / `.notIn(query)` | Compare a column to a projected subquery result set | `subquery-explicit.md` |
| `DSLColumnSet.setSubQuery(query)` | Assign a scalar subquery to a proxy projection field | `subquery-explicit.md` |
| `.toCteAs()` | Promote a queryable to a named CTE stage | `subquery-explicit.md`, `ap-analytics.md` |
| `.leftJoin(queryable, ...)` | Join a grouped/manual derived-table subquery | `subquery-explicit.md`, `query-composition.md` |

## Select and Projection Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `Select.DRAFT.of(...)` | Build a lightweight typed draft tuple projection for ad hoc metrics or intermediate shapes | `ap-analytics.md`, `query-composition.md` |
| `rowNumberOver(...)` | Window row-number function for partition/ranking queries | `ap-analytics.md` |
| `rankOver()` | Proxy-side window rank builder for partition/ranking queries | `ap-analytics.md` |
| `denseRankOver()` | Proxy-side dense-rank builder for partition/ranking queries | `ap-analytics.md` |
| `countOver(...)` | Proxy-side window count builder for partition analytics | `ap-analytics.md` |
| `sumOver(...)` | Proxy-side window sum builder for partition analytics | `ap-analytics.md` |
| `avgOver(...)` | Proxy-side window average builder for partition analytics | `ap-analytics.md` |
| `maxOver(...)` | Proxy-side window max builder for partition analytics | `ap-analytics.md` |
| `minOver(...)` | Proxy-side window min builder for partition analytics | `ap-analytics.md` |

Useful projection helpers:

`Select.of(...)`, `Select.DRAFT`, `Select.TUPLE`, `Select.PART`,
`Select.aggregateOf(...)`, `FETCHER`

Read `query.md` for normal select, `query-composition.md` for advanced
projection, and `select-auto-include.md` for DTO graphs.

## Typed Expression and Native SQL Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `nullOrDefault(...)` | Cross-type SQL-side null fallback | `typed-sql-expressions.md` |
| `trim()` / `ltrim()` / `rtrim()` | String trim helpers | `typed-sql-expressions.md` |
| `trimStart()` / `trimEnd()` | Lower-level trim aliases in current source | `typed-sql-expressions.md` |
| `cast(..., TargetClass.class)` | SQL-side type cast helper via `fx()` | `typed-sql-expressions.md` |
| `maxColumns(...)` / `minColumns(...)` | Greatest/least style multi-column comparison | `typed-sql-expressions.md` |
| `rawSQLCommand(...)` | Recommended proxy-side execution fragment for `where` / `orderBy` / `having` | `native-sql.md` |
| `rawSQLStatement(...)` | Recommended proxy-side typed native fragment expression | `native-sql.md` |
| `setSQL(...)` | Native SQL assignment for proxy projection/update field setters | `native-sql.md` |
| `sqlNativeSegment(...)` | Property-mode or lower-level native SQL fragment entry | `native-sql.md` |
| `c.value(...)` | Bind a native-fragment parameter as a JDBC placeholder in `sqlNativeSegment(...)` / lower-level SQL context callbacks | `native-sql.md` |
| `c.format(...)` | Inject a literal native-fragment text token through `MessageFormat`-style formatting rules | `native-sql.md` |
| `EasyProxyParamExpressionUtil.parseContextExpressionByParameters(...)` | Recover expression context for reusable custom native function wrappers | `native-sql.md`, `symbol-imports.md` |
| `messageFormat()` | Disable default keep-style quote escaping in native fragment formatting | `native-sql.md` |

## Native SQL Entry Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `sqlQuery(...)` | Execute existing full SQL and materialize typed rows | `native-sql.md` |
| `sqlEasyQuery(...)` | Execute existing full SQL with explicit `SQLParameter` list | `native-sql.md` |
| `sqlQueryMap(...)` | Execute existing full SQL and return maps | `native-sql.md` |
| `sqlExecute(...)` | Execute existing raw DML/DDL SQL | `native-sql.md` |
| `queryable(String sql, Class<T> clazz, Collection<Object> params)` | Treat raw SQL as a derived table and continue with DSL composition | `native-sql.md` |
| `mapQueryable(String sql)` | Raw-SQL-backed map queryable when no entity type is appropriate | `native-sql.md` |

## Write, Transaction, and Save Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `insertable(...)` | Insert rows | `write-insert-upsert.md` |
| `updatable(...)` | Update rows | `write-update.md` |
| `deletable(...)` | Delete rows | `write-delete.md` |
| `mapInsertable(...)` | Insert column-value map rows | `write-insert-upsert.md` |
| `mapUpdatable(...)` | Update column-value map rows | `write-update.md` |
| `onConflictThen(...)` | Conflict/upsert handling on insert | `write-insert-upsert.md` |
| `setSQLStrategy(...)` | Control insert/update column emission strategy | `write-insert-upsert.md`, `write-update.md`, `configuration-starter.md` |
| `setColumns(...)` on `updatable(Entity.class)` | Build expression-update `SET` clauses on the current update chain | `write-update.md` |
| `setColumns(...)` on `updatable(entity)` | Narrow object-update column selection for the current entity update | `write-update.md`, `write-tracking.md` |
| `set(...)` / `setWithColumn(...)` / `setSQLFunction(...)` on `updatable(Class)` | Low-level expression-update setters | `write-update.md` |
| `setIgnoreColumns(...)` | Exclude selected object-update columns from `SET` | `write-update.md` |
| `whereColumns(...)` | Choose object-update or object-delete key columns | `write-update.md`, `write-delete.md` |
| `columnConfigure(...)` | Per-column custom SQL generation during insert/update | `write-insert-upsert.md`, `write-update.md` |
| `batch()` / `batch(true)` / `batch(false)` | Toggle JDBC batch execution behavior on the current write chain | `write.md` |
| `withVersion(...)` / `ignoreVersion()` | Version handling | `write-update.md`, `write-delete.md` |
| `executeRows(expectRows, msg)` / `executeRows(expectRows, msg, code)` | Assert the expected affected-row count and throw on mismatch | `write-update.md`, `write-delete.md`, `transaction.md` |
| `whereById(...)` / `whereByIds(...)` on `updatable(Class)` / `deletable(Class)` | Expression-update/delete key predicates | `write-update.md`, `write-delete.md` |
| `disableLogicDelete()` / `enableLogicDelete()` / `useLogicDelete(boolean)` | Toggle logic-delete filtering/behavior on the current chain | `logic-delete.md` |
| `tableLogicDelete(() -> false)` | Disable logic delete for the nearest joined table in the current expression | `logic-delete.md` |
| `disableLogicDelete()` / `allowDeleteStatement(true)` | Physical delete opt-in | `logic-delete.md` |
| `beginTransaction()` | Plain transaction API returning `Transaction` (`com.easy.query.core.basic.jdbc.tx`) | `transaction.md` |
| `@Transactional` | Spring transaction boundary | `transaction.md` |
| `savable(...)` | Aggregate graph save router | `savable-aggregate.md` |

## Savable Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `.savePath(...)` | Limit savable processing to selected value-object paths | `savable-execution.md` |
| `.ignoreRoot()` | Skip root save operations | `savable-execution.md` |
| `.removeRoot()` | Remove root and loaded included graph | `savable-execution.md` |
| `.configure(s -> s.getSaveBehavior().add(...))` | Advanced savable behavior flags | `savable-execution.md`, `savable-relation-rules.md` |
| `SaveBehaviorEnum.ALLOW_OWNERSHIP_CHANGE` | Permit value-object ownership reassignment | `savable-relation-rules.md` |
| `SaveBehaviorEnum.IGNORE_NULL` / `IGNORE_EMPTY` / `IGNORE_LOGIC_DELETE` | Advanced savable behavior switches | `savable-execution.md` |
| `easyEntityQuery.saveEntitySetPrimaryKey(entity)` | Assign safe backend keys for request-built child rows | `savable-key-safety.md`, `primary-key-generation.md` |
| `@SaveKey` | Non-PK in-memory matching identity for `savable(...)` diff (middle-table business key); does not change SQL | `savable-relation-rules.md` |

## Tracking Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `.asTracking()` | Mark query results for diff update tracking | `write-tracking.md` |
| `.asNoTracking()` | Explicitly opt out of tracking on the current query | `write-tracking.md` |
| `easyEntityQuery.addTracking(entity)` | Manually add an entity to the current tracking context | `write-tracking.md` |
| `easyEntityQuery.getRuntimeContext().getTrackManager()` | Retrieve the `TrackManager` used for manual tracking-scope control and inspection | `write-tracking.md` |
| `TrackManager.begin()` / `release()` | Manual tracking scope lifecycle | `write-tracking.md` |
| `TrackManager.currentThreadTracking()` | Check whether the current thread already has an active tracking scope | `write-tracking.md` |
| `TrackManager.getCurrentTrackContext()` | Access the current `TrackContext`; lower-level/manual helper that can be `null` outside a scope | `write-tracking.md` |
| `@EasyQueryTrack` | Framework-managed tracking scope for public methods | `write-tracking.md`, `spring-boot-starter.md` |

## Advanced Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `groupBy(...)` / `GroupKeys.of(...)` | Group query (`com.easy.query.core.proxy.sql.GroupKeys`) | `ap-analytics.md`, `advanced.md` |
| `having(...)` | Apply aggregate-level filtering after `groupBy(...)` rather than row-level filtering before grouping | `ap-analytics.md` |
| `union(...)` | Combine same-shape query branches with de-duplication | `ap-analytics.md` |
| `unionAll(...)` | Combine same-shape query branches without de-duplication | `ap-analytics.md` |
| `getDatabaseCodeFirst()` | Code-first DDL | `advanced.md` |
| `applyShardingInitializer(...)` | Sharding registration | `advanced.md` |
| `EasyMultiEntityQuery.executeScope(...)` | Multi-datasource scope | `advanced.md` |

## Starter and Config Symbols

High-impact defaults often referenced in code review:

`insertStrategy = ONLY_NOT_NULL_COLUMNS`, `updateStrategy = ALL_COLUMNS`,
`defaultCondition = LIKE`, `autoIncludeTable = THROW`,
`relationGroupSize = 512`, `includeLimitMode = PARTITION`

Read `configuration-starter.md` when behavior seems different from the written
query code.

## Spring Boot Starter Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `EasyQueryProperties` | Spring Boot `easy-query.*` property holder (`com.easy.query.sql.starter.config`) | `spring-boot-starter.md`, `configuration-starter.md` |
| `EasyQueryInitializeOption` | Collects starter-discovered extension beans and applies them to the built client | `spring-boot-starter.md` |
| `StarterConfigurer` | Spring Boot hook for replacing internal bootstrap services (`com.easy.query.core.bootstrapper`) | `spring-boot-starter.md` |
| `JdbcTypeHandlerReplaceConfigurer` | Declares global JDBC handler type binding/replacement under starter | `spring-boot-starter.md`, `value-conversion-type-handler.md` |
| `JdbcTypeHandlerReplaceConfigurer.replace()` | Decide whether the starter should replace an existing handler for the declared Java types | `spring-boot-starter.md`, `value-conversion-type-handler.md` |
| `JdbcTypeHandlerReplaceConfigurer.allowTypes()` | Declare which Java property types this starter-managed JDBC handler should bind | `spring-boot-starter.md`, `value-conversion-type-handler.md` |
| `SpringBootStarterBuilder.buildClient(...)` | Default starter client-construction path | `spring-boot-starter.md` |
| `EasyQueryTrackProperties` | `easy-query-track.*` property holder | `spring-boot-starter.md` |
| `LogicDeleteStrategy` | Starter-collected logic-delete strategy bean | `spring-boot-starter.md`, `logic-delete.md` |
| `VersionStrategy` | Starter-collected optimistic-lock strategy bean | `spring-boot-starter.md` |
| `ShardingInitializer` | Starter/manual sharding bootstrap component | `spring-boot-starter.md`, `advanced.md` |
| `EncryptionStrategy` | Starter-collected field encryption strategy | `spring-boot-starter.md` |
| `TableRoute<?>` | Starter-collected table route component | `spring-boot-starter.md`, `advanced.md` |
| `DataSourceRoute<?>` | Starter-collected datasource route component | `spring-boot-starter.md`, `advanced.md` |
| `NavigateExtraFilterStrategy` | Starter-collected relation extra filter strategy | `spring-boot-starter.md` |
| `NavigateValueSetter` | Starter-collected relation value setter | `spring-boot-starter.md` |
| `EntityRelationPropertyProvider` | Starter-collected implicit relation metadata provider | `spring-boot-starter.md` |

Starter property switches commonly worth naming precisely:

- `easy-query.enable`
- `easy-query.build`
- `easy-query.database`
- `easy-query-track.enable`

## Behavior Symbols

| Symbol | Purpose | Read Next |
|--------|---------|-----------|
| `EasyBehaviorEnum.ALL_SUB_QUERY_GROUP_JOIN` | Broader behavior flag to rewrite eligible to-many subqueries as group join | `implicit-query.md` |
| `EasyBehaviorEnum.GROUP_JOIN_NOT_ALLOW_AUTO_MERGE` | Disable eligible group-join auto merge behavior | `implicit-query.md` |


