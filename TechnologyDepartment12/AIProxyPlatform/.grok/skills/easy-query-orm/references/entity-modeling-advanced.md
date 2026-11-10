# Advanced Entity Modeling

Use this after `entity-mapping.md` when entity declaration needs more than
basic table/column/proxy setup.

This file covers:

- advanced `@Table` options
- advanced `@Column` options
- `@EasyAlias`
- `@TableIndex` / `@TableIndexes`
- `@ColumnIgnore` / `@InsertIgnore` / `@UpdateIgnore`
- `@EntityFileProxy` / `@ProxyProperty`
- current-source note about `@ValueObject`

For the dedicated choice among database-generated key, SQL-function generated
key, Java-side `PrimaryKeyGenerator`, starter/manual registration, or
`saveEntitySetPrimaryKey(...)`, read `primary-key-generation.md`.

For computed properties such as `sqlExpression`, `sqlConversion`, cross-table
derived fields, or `autoSelect=false`, read `entity-computed-properties.md`.

## 1. `@Table` Beyond the Name

Current source fields:

```java
String value() default "";
String schema() default "";
String[] ignoreProperties() default {};
Class<? extends ShardingInitializer> shardingInitializer() default UnShardingInitializer.class;
String oldName() default "";
String comment() default "";
boolean keyword() default true;
```

High-value meanings:

- `value`: explicit table name
- `schema`: explicit schema
- `ignoreProperties`: ignore inherited or unwanted properties
- `oldName`: code-first rename/migration hint
- `comment`: code-first table comment
- `keyword = false`: disable dialect keyword quoting for special table sources
- `shardingInitializer`: entity-local sharding initializer hook

## 2. Table Alias Is Plugin-Facing: `@EasyAlias`

`@EasyAlias` is `RetentionPolicy.SOURCE` in current source. Treat it as a
compile/plugin-facing alias helper, not as a runtime ORM mapping switch.

Use it when:

- the team uses plugin lambda alias generation
- examples or generated snippets rely on stable alias names

## 3. Table Index Metadata

Current source has:

- `@TableIndex`
- `@TableIndexes`

These are code-first modeling annotations, not query DSL features.

High-value fields on `@TableIndex`:

- `unique`
- `name`
- `fields`
- `descendingFields`
- `comment`

## 4. `@Column` Advanced Options

Current source fields beyond the basic ones:

```java
Class<? extends ValueConverter<?, ?>> conversion();
Class<? extends ColumnValueSQLConverter> sqlConversion();
Class<? extends GeneratedKeySQLColumnGenerator> generatedSQLColumnGenerator();
Class<? extends ComplexPropType> complexPropType();
boolean autoSelect() default true;
Class<? extends JdbcTypeHandler> typeHandler();
Class<? extends PrimaryKeyGenerator> primaryKeyGenerator();
boolean exist() default true;
boolean nullable() default true;
String dbType() default "";
String dbDefault() default "";
String comment() default "";
String oldName() default "";
int length() default -1;
int scale() default 0;
ColumnSQLExpression sqlExpression();
JDBCType jdbcType() default JDBCType.OTHER;
```

High-value modeling uses:

- `generatedSQLColumnGenerator`: database-side generated insert value
- `primaryKeyGenerator`: Java-side key generation when `generatedKey=false`
- `typeHandler` / `jdbcType`: JDBC binding/result-shape control
- `dbType` / `dbDefault` / `nullable` / `length` / `scale` / `comment` /
  `oldName`: code-first DDL and migration metadata
- `complexPropType`: complex-type handling path

## 5. Ignore and Persistence-Scope Annotations

### `@ColumnIgnore`

`@ColumnIgnore` means easy-query effectively does not map the property at all.

### `@Column(exist = false)`

Current source comment explicitly treats `exist=false` as roughly equivalent to:

```java
@Column(autoSelect = false)
@InsertIgnore
@UpdateIgnore(updateSetInTrackDiff = false)
```

Use `exist=false` when the field should stay part of the entity/proxy shape
but not be persisted.

### `@InsertIgnore`

Ignore the field on object insert.

### `@UpdateIgnore`

Ignore the field on object update unless:

- manually targeted in expression update
- or `updateSetInTrackDiff = true` and tracking diff needs it

## 6. Generated Key vs Primary Key Generator

Do not conflate:

- `generatedKey = true`: database generates the value
- `primaryKeyGenerator = ...`: Java-side key generation

Current source constraint:

- `primaryKeyGenerator` only matters when `primaryKey=true` and
  `generatedKey=false`

If the user needs a real decision tree, registration code, or insert-time
ordering, switch to `primary-key-generation.md`.

## 7. `@EntityProxy` vs `@EntityFileProxy`

Current source and docs strongly favor `@EntityProxy`.

Use `@EntityFileProxy` only when the project intentionally generates proxy
files into source and already depends on that plugin workflow.

## 8. `@ProxyProperty`

Use `@ProxyProperty` when a generated proxy accessor name would collide or when
the proxy-side property alias must differ.

High-value fields:

- `value`
- `generateAnyType`

## 9. `@ValueObject` Current-Source Warning

Current source marks `@ValueObject` as `@Deprecated`.

Practical guidance:

- if the local project already uses `@ValueObject`, support and explain it
- do not present it as the preferred new modeling default without checking the
  project version and team conventions

## 10. Common Mistakes

- Using `@EasyAlias` as if it were a runtime mapping requirement
- Recommending `@EntityFileProxy` for normal projects that do not need
  plugin-managed source generation
- Confusing `generatedKey` with `primaryKeyGenerator`
- Using `@ColumnIgnore` when `exist=false` or ignore annotations were the
  better fit
- Teaching `@ValueObject` as an unquestioned current best practice despite the
  current source deprecation

## Read next

- basic mapping: `entity-mapping.md`
- computed/derived properties: `entity-computed-properties.md`
- relation metadata: `entity-modeling-navigate.md`

## Sources

- docs: `easy-query-doc/src/framework/annotation.md`
- docs: `easy-query-doc/src/code-first/api.md`
- docs: `easy-query-doc/src/plugin/README.md`
- source: `com.easy.query.core.annotation.{Table,Column,EasyAlias,ColumnIgnore,InsertIgnore,UpdateIgnore,ValueObject,TableIndex,TableIndexes,EntityFileProxy,ProxyProperty}`
