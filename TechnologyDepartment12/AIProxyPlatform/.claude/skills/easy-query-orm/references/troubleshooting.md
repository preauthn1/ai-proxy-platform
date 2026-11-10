# Troubleshooting

Use this when easy-query code compiles but behaves unexpectedly, or when a
familiar API throws in a non-obvious way.

Do not open this first for ordinary setup questions. Use the setup references
first.

## Proxy and Build Issues

### `*Proxy` Not Generated

First checks:

- Java uses APT; Kotlin uses KSP, not KAPT
- entity has `@EntityProxy`
- generated source directory is on the compile/IDE path
- if the build config already uses `annotationProcessorPaths`, make sure
  easy-query's processor is in that list
- inspect the first compile or processor failure, not only the final missing
  proxy import

Read next:

- `proxy-generation-troubleshooting.md`
- `setup-java.md`
- `setup-kotlin.md`
- `entity-mapping.md`

### Generated Proxy Looks Stale

First checks:

- rebuild annotation processing / KSP output
- verify the generated proxy package
- do not hand-edit generated proxy classes

Read next:

- `proxy-generation-troubleshooting.md`
- `setup-java.md`
- `setup-kotlin.md`
- `entity-mapping.md`

## DTO Result and Mapping Issues

### `selectAutoInclude` Used With Entity Class

First fix:

```java
query.selectAutoInclude(UserDTO.class);
```

Read next:

- `select-auto-include.md`
- `configuration-starter.md`

### DTO Fields Missing After `select(...)` or `selectAutoInclude(...)`

Common causes:

- `select(Class<TR>)` name mismatch
- missing `@Navigate`
- wrong `@NavigateFlat` alias
- stale proxies

First fix:

```java
select(ResultDTO.class, o -> Select.of(
    o.FETCHER.allFields(),
    o.user().name().as(ResultDTO.Fields.userName)
))
```

Read next:

- `query.md`
- `select-auto-include.md`
- `entity-modeling-navigate.md`

### `flatElement()` Used Inside `select`

First fix:

- use `flatElement()` in query/list/include path traversal
- for DTO flattening, switch to `@NavigateFlat` or `selectAutoInclude`

Read next:

- `entity-modeling-navigate.md`
- `select-auto-include.md`
- `include-structured-loading.md`

### `include` vs `selectAutoInclude` Confusion

Symptom:

- entity relations populate but DTO extra fields stay empty
- DTO graph was expected from `include(...)`

Rule:

- `include(...)` / `include2(...)` populate entity relation properties
- `selectAutoInclude(...)` builds DTO/VO result graphs

Read next:

- `include-structured-loading.md`
- `select-auto-include.md`

## Relation and Query Issues

### Implicit Join Not Generated

Common causes:

- gated condition skipped
- `filterConfigure(...)` removed the value
- relation metadata missing or wrong
- relation path only appears in an inactive branch

Read next:

- `query.md`
- `relation-query.md`
- `implicit-query.md`

### Too Many To-Many Subqueries

First fixes:

- query-level `.subQueryToGroupJoin(o -> o.relation())`
- annotation-level `@Navigate(subQueryToGroupJoin = true)`
- explicit join/group query for more complex shapes

Read next:

- `implicit-query.md`
- `entity-modeling-navigate.md`
- `advanced.md`

### Inner vs Left Join Surprise

Symptom:

- rows are kept when a related row is missing, or disappear unexpectedly

Rule:

- to-one implicit navigation usually behaves like left join
- `@Navigate(required = true)` can enable inner-join style semantics

Use `required = true` only when missing related rows are impossible by data
integrity.

Read next:

- `implicit-query.md`
- `entity-modeling-navigate.md`

### Partition Order Error

Cause: `@Navigate.partitionOrder` defaults to `PartitionOrderEnum.THROW`.

First fix: define `orderByProps` or set an explicit partition-order policy.

Read next:

- `implicit-query.md`
- `entity-modeling-navigate.md`

### `whereObject(...)` / `orderByObject(...)` Field Mismatch

First fix:

- keep real query-form fields in the `whereObject(...)` path
- move paging, transport-only, or ad hoc business-only fields out of the DTO
  query object
- switch only the hard-to-express part back to explicit DSL when needed

Reminder:

- `whereObject(...)` is for query/search form DTOs
- `whereObject(...)` and explicit `.where(...)` can be mixed

Read next:

- `dto-object-query.md`
- `query.md`

### `DEFAULT` Behaves Like `LIKE` Instead of `CONTAINS`

Cause: starter default `defaultCondition` is often `LIKE`.

First fix:

- use `CONTAINS`, `STARTS_WITH`, or `ENDS_WITH` explicitly

Read next:

- `dto-object-query.md`
- `configuration-starter.md`

## Write and Save Issues

### Physical Delete Fails

Cause:

- physical delete is intentionally guarded by logic-delete safety

Required form:

```java
deletable(entity)
    .disableLogicDelete()
    .allowDeleteStatement(true)
    .executeRows();
```

If it still fails, check global delete configuration.

Read next:

- `write.md`
- `configuration-starter.md`

### Object Update Writes Unwanted Nulls

Cause:

- object update may write all columns depending on strategy/config

First fixes:

```java
updatable(entity)
    .setSQLStrategy(SQLExecuteStrategyEnum.ONLY_NOT_NULL_COLUMNS)
    .executeRows();
```

or switch to explicit `setColumns(...)`.

Read next:

- `write.md`
- `configuration-starter.md`

### `savable(...)` Used Without Tracking or Transaction

Symptom:

- aggregate diff-save does not behave as expected

Rule:

- load tracked objects first
- execute `savable(...)` inside a transaction
- do not expect a freshly constructed request object to behave like a tracked aggregate

Read next:

- `savable-aggregate.md`
- `transaction.md`

### Upsert Missing Conflict Columns

Symptom:

- conflict columns that are null are omitted from SQL

Cause:

- starter/object insert strategy may be `ONLY_NOT_NULL_COLUMNS`

First fix:

- set `SQLExecuteStrategyEnum.ALL_COLUMNS` when the conflict column must appear

Read next:

- `write.md`
- `configuration-starter.md`

## Native SQL and Dialect Issues

### Native SQL Dialect Drift

Symptom:

- `setSQL`, `sqlSegment`, or `sqlQuery` works on one database but fails on another

Cause:

- native fragments are dialect-sensitive

First fix:

- verify the target database before using functions such as `IFNULL`
- prefer typed DSL functions whenever available

Read next:

- `typed-sql-expressions.md`
- `native-sql.md`
- `setup-spring-boot.md`
- `advanced.md`
