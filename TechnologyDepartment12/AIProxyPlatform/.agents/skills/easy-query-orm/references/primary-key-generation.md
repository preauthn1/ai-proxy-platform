# Primary Key Generation

Use this reference for primary-key generation strategy, registration, and
timing.

This is the key-generation branch, not the general insert guide. Read this
when the user asks:

- how to generate ids
- `generatedKey`
- `primaryKeyGenerator`
- `generatedSQLColumnGenerator`
- UUID / snowflake / custom id
- why insert did or did not backfill the id
- why `saveEntitySetPrimaryKey(...)` behaves differently from normal insert

Pair with:

- `write-insert-upsert.md` for insert chain details such as `executeRows(true)`
- `savable-key-safety.md` when the real problem is request-built child ids in a
  tracking/savable flow
- `spring-boot-starter.md` when the question is specifically about starter
  auto-registration

## 1. Choose the Right Path First

There are three distinct primary-key paths in current source:

| Need | Main mechanism | Core annotation/API |
|---|---|---|
| database generates the key | database-generated key | `@Column(primaryKey = true, generatedKey = true)` |
| database function/expression generates the key in insert SQL | generated SQL column | `@Column(primaryKey = true, generatedKey = true, generatedSQLColumnGenerator = ...)` |
| Java code generates the key before insert SQL executes | Java-side primary key generator | `@Column(primaryKey = true, primaryKeyGenerator = ...)` |

And there is one adjacent but different safety API:

| Need | Main mechanism | Core API |
|---|---|---|
| request-built child entity may carry an unsafe id in savable/tracking flow | backend-safe save-key assignment | `easyEntityQuery.saveEntitySetPrimaryKey(entity)` |

Do not collapse these into one generic "主键生成" answer.

There is also a second nearby concept that is **not** a key generator at all:

| Need | Main mechanism | Core annotation/API |
|---|---|---|
| savable diff should match a child by business identity when its PK is unset/unknown | savable-only in-memory fallback identity | `@SaveKey` |

`@SaveKey` does not assign ids, does not backfill ids, and does not alter SQL
PK `WHERE` / `INSERT` behavior. It only changes how `savable(...)` matches an
incoming child to already-tracked/database-loaded children during diff.

## 2. Database-Generated Key: `generatedKey = true`

Basic auto-increment / identity style:

```java
@Column(primaryKey = true, generatedKey = true)
private Integer id;
```

Meaning:

- the database, not Java code, generates the key
- insert SQL normally omits that key column unless another generated-column
  rule says otherwise
- if the caller needs the inserted id populated back onto the entity, use the
  generated-key backfill path

Typical insert:

```java
TopicAuto topic = new TopicAuto();
topic.setTitle("new");

easyEntityQuery.insertable(topic).executeRows(true);
Integer id = topic.getId();
```

Practical rule:

- `executeRows(true)` is the path to request generated-key backfill for
  database-generated keys
- do not teach `executeRows(true)` as a requirement for Java-side
  `PrimaryKeyGenerator`

## 3. Database Function Key: `generatedSQLColumnGenerator`

Use this when the database should still generate the key, but the insert SQL
must emit an explicit SQL expression or function such as `nextId()`.

Model:

```java
@Column(
    primaryKey = true,
    generatedKey = true,
    generatedSQLColumnGenerator = MyDatabaseIncrementSQLColumnGenerator.class
)
private String id;
```

Generator:

```java
import com.easy.query.core.basic.extension.generated.GeneratedKeySQLColumnGenerator;

public class MyDatabaseIncrementSQLColumnGenerator
        implements GeneratedKeySQLColumnGenerator {
    @Override
    public void configure(TableAvailable table,
                          ColumnMetadata columnMetadata,
                          SQLPropertyConverter sqlPropertyConverter,
                          QueryRuntimeContext runtimeContext) {
        sqlPropertyConverter.sqlNativeSegment("mysqlNextId()");
    }
}
```

Current source distinction:

- this is still a `generatedKey = true` database-generated path
- it is not `primaryKeyGenerator`
- its job is to control the emitted insert SQL for that column

Source-backed safe guidance:

- use this when DB-side policy or function must own id generation
- if application code must have the id before SQL execution, prefer
  `PrimaryKeyGenerator` instead

The last point is an inference from source/docs: current docs clearly describe
SQL emission, but do not make a broad per-dialect backfill guarantee for every
custom function path.

## 4. Java-Side Key Generation: `PrimaryKeyGenerator`

Use `PrimaryKeyGenerator` when the application should assign the id before the
insert SQL runs.

Interface:

```java
import com.easy.query.core.basic.extension.generated.PrimaryKeyGenerator;

public interface PrimaryKeyGenerator {
    Serializable getPrimaryKey();
    default void setPrimaryKey(Object entity, ColumnMetadata columnMetadata) { ... }
}
```

Entity:

```java
@Column(primaryKey = true, primaryKeyGenerator = UUIDPrimaryKeyGenerator.class)
private String id;
```

Generator example:

```java
import com.easy.query.core.basic.extension.generated.PrimaryKeyGenerator;
import org.springframework.stereotype.Component;

import java.io.Serializable;
import java.util.UUID;

@Component
public class UUIDPrimaryKeyGenerator implements PrimaryKeyGenerator {
    @Override
    public Serializable getPrimaryKey() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

Important current-source timing:

- `AbstractClientInsertable.insertBefore()` calls
  `primaryKeyGenerator.setPrimaryKey(...)`
- then entity interceptors run

So the order is:

1. Java-side primary key generator
2. entity insert interceptors
3. actual insert execution

That means:

- do not say interceptors run first
- interceptor code can still overwrite/clear a generated id if the project
  intentionally does that

## 5. Registration Paths

### Plain Java / Manual Bootstrap

Register the component explicitly:

```java
QueryRuntimeContext runtimeContext = easyQueryClient.getRuntimeContext();
QueryConfiguration configuration = runtimeContext.getQueryConfiguration();

configuration.applyPrimaryKeyGenerator(new UUIDPrimaryKeyGenerator());
configuration.applyGeneratedKeySQLColumnGenerator(
    new MyDatabaseIncrementSQLColumnGenerator()
);
```

Current source behavior:

- `applyPrimaryKeyGenerator(...)` rejects duplicate generator-class
  registrations
- entity metadata resolution throws
  `primary key generator unknown, plz register this component`
  if the entity references a generator class that was not registered

### Spring Boot Starter

Current starter source auto-collects these bean types:

- `PrimaryKeyGenerator`
- `GeneratedKeySQLColumnGenerator`

So under starter:

- `@Component` on `PrimaryKeyGenerator` is enough
- `@Component` on `GeneratedKeySQLColumnGenerator` is enough

The built client applies them through `EasyQueryInitializeOption`.

### What Is Not Auto-Collected

`SaveEntitySetPrimaryKeyGenerator` is not part of the starter's normal
auto-collected bean map.

Do not claim that a plain `@Component SaveEntitySetPrimaryKeyGenerator`
auto-binds the same way as `PrimaryKeyGenerator`.

If a project must replace that runtime service, use a service-replacement /
custom-builder path instead of teaching ordinary starter bean collection.

## 6. `saveEntitySetPrimaryKey(...)` Is Not the General Default

Current source in `DefaultEasyQueryClient.saveEntitySetPrimaryKey(...)` does:

1. require a current tracking context
2. inspect whether the entity is already tracked
3. if not tracked, assign key columns by:
   - entity column `PrimaryKeyGenerator`, or
   - runtime `SaveEntitySetPrimaryKeyGenerator`

So this API is for:

- request-built child entities
- savable/tracking flows
- backend-safe key assignment when frontend ids are not trustworthy

It is not the general answer for ordinary single-row insert code.

Important boundary from source:

- this API **partly reuses** ordinary key-generation infrastructure rather than
  duplicating it
- if the key column already has `PrimaryKeyGenerator`, this API calls that same
  generator
- only when the entity has no `PrimaryKeyGenerator` does it use runtime
  `SaveEntitySetPrimaryKeyGenerator`

So the extra value of `saveEntitySetPrimaryKey(...)` is not "another generic PK
generator". The extra value is the **tracking-aware safety decision**:
"only for untracked would-be inserts, replace unsafe/request-supplied ids with
backend-generated ids".

For normal insert:

- prefer `generatedKey = true`
- or `primaryKeyGenerator = ...`

For request-built aggregate child safety:

- prefer `savable-key-safety.md`

## 7. Decision Guide

Use this quick selection rule:

- DB auto increment / identity:
  `generatedKey = true`
- DB function like `nextId()` or custom SQL expression:
  `generatedKey = true + generatedSQLColumnGenerator`
- application-owned UUID / snowflake / custom algorithm:
  `primaryKeyGenerator = ...`
- request-built child entity in savable/tracking flow:
  `saveEntitySetPrimaryKey(...)`
- savable diff should match rows by business key when PK is missing/unset:
  `@SaveKey`

## 8. Common Mistakes

- Configuring both Java-side `primaryKeyGenerator` thinking it will cooperate
  with `generatedKey = true`
- Recommending `executeRows(true)` for Java-side `PrimaryKeyGenerator`
- Treating `generatedSQLColumnGenerator` as if it were a Java-side id
  generator
- Assuming starter auto-collects `SaveEntitySetPrimaryKeyGenerator` by plain
  bean type
- Teaching `saveEntitySetPrimaryKey(...)` outside tracking context
- Explaining `@SaveKey` as if it were another primary-key generation strategy
- Forgetting that `PrimaryKeyGenerator` runs before insert interceptors

## Sources

- docs:
  `easy-query-doc/src/adv/auto-key.md`
- docs:
  `easy-query-doc/src/adv/generated-key-sql-column.md`
- docs:
  `easy-query-doc/src/savable/set-save-key.md`
- source:
  `PrimaryKeyGenerator`
- source:
  `GeneratedKeySQLColumnGenerator`
- source:
  `SaveEntitySetPrimaryKeyGenerator`
- source:
  `AbstractClientInsertable.insertBefore()`
- source:
  `DefaultEasyQueryClient.saveEntitySetPrimaryKey(...)`
- source:
  `QueryConfiguration.applyPrimaryKeyGenerator(...)`
- source:
  `EntityMetadata` primary key generator resolution
- source:
  `EasyQueryInitializeOption`
