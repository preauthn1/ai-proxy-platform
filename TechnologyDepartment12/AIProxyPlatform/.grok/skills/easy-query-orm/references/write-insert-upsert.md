# Insert and Upsert

Use this reference for:

- `insertable(...)`
- `executeRows(true)` generated-key backfill
- insert SQL strategy
- insert `columnConfigure(...)`
- `onConflictThen(...)`
- `mapInsertable(...)`

For update semantics, read `write-update.md`. For batch execution-mode details,
read `write.md`.

## 1. Basic Insert

### Single / list / chained insert

```java
Topic topic = new Topic();
topic.setStars(999);
topic.setTitle("title999");
topic.setCreateTime(LocalDateTime.now());

long row1 = easyEntityQuery.insertable(topic).executeRows();

List<Topic> topics = buildMany();
long row2 = easyEntityQuery.insertable(topics).executeRows();

long row3 = easyEntityQuery.insertable(topics.get(0))
    .insert(topics.get(1))
    .executeRows();
```

All insert chains finish only after `executeRows(...)`.

### Generated-key backfill

```java
TopicAuto topic = new TopicAuto();
topic.setTitle("new");

long rows = easyEntityQuery.insertable(topic).executeRows(true);
Integer id = topic.getId();
```

Use this only when the entity metadata really marks the column as
database-generated, for example `@Column(primaryKey = true, generatedKey = true)`.

Do not explain `executeRows(true)` as the normal path for Java-side
`primaryKeyGenerator`. That generator runs before insert SQL and sets the id on
the entity directly. Read `primary-key-generation.md` when the user is really
asking about id strategy selection.

## 2. Insert SQL Strategy

Current default insert strategy is `ONLY_NOT_NULL_COLUMNS`.

```java
easyEntityQuery.insertable(topic)
    .setSQLStrategy(SQLExecuteStrategyEnum.ALL_COLUMNS)
    .executeRows();
```

High-value strategies:

- `ONLY_NOT_NULL_COLUMNS`:
  omit null properties from insert SQL
- `ALL_COLUMNS`:
  force all columns into insert SQL
- `ONLY_NULL_COLUMNS`:
  niche path; mention only when the project already uses it

Use `ALL_COLUMNS` when:

- a conflict-key column must still appear even if its current value is null
- a database-side insert rule expects the column to be present

## 3. Insert `columnConfigure(...)`

Use `columnConfigure(...)` when a column must be emitted as a SQL expression
instead of a normal bound value:

```java
easyEntityQuery.insertable(topic)
    .columnConfigure((t, cfg) -> cfg.column(
        t.stars(),
        "ifnull({0},0)+{1}",
        (context, sqlParameter) -> context.expression(t.stars()).value(sqlParameter)
    ))
    .executeRows();
```

Prefer normal property binding first. Reach for `columnConfigure(...)` when the
insert column itself is expression-shaped.

## 4. `onConflictThen(...)`

This is the current insert-or-update / insert-or-ignore API.

### Update on conflict

```java
easyEntityQuery.insertable(user)
    .onConflictThen(o -> o.FETCHER.updateTime().title())
    .executeRows();
```

### Ignore on conflict

```java
easyEntityQuery.insertable(user)
    .onConflictThen(null, o -> o.FETCHER.code())
    .executeRows();
```

### Rules

- first parameter:
  fields to update when conflict happens
- first parameter `null`:
  do nothing on conflict
- second parameter:
  conflict constraint columns; omitted means primary key

### High-value caveats

- if the chosen conflict property may be omitted by insert strategy, force
  `ALL_COLUMNS`
- current docs say MySQL ignores the second-parameter custom constraint
  selection
- if no constraint selector is supplied and the table has no primary key, the
  operation is invalid
- this is native database conflict handling, not "query first, then insert or
  update in Java"

## 5. `mapInsertable(...)`

Map keys are database column names:

```java
Map<String, Object> row = new LinkedHashMap<>();
row.put("id", 123);
row.put("name", "小明");
row.put("name2", null);

easyEntityQuery.mapInsertable(row)
    .asTable("sys_table")
    .setSQLStrategy(SQLExecuteStrategyEnum.ONLY_NOT_NULL_COLUMNS)
    .executeRows();
```

Use this when:

- table name is dynamic
- no entity class is available or desirable
- the payload is already column-shaped

## 6. Pairing Rules

- Need to choose between DB-generated key, DB function key, Java-side
  `PrimaryKeyGenerator`, or save-key safety:
  add `primary-key-generation.md`
- Need batch behavior details:
  add `write.md`
- Need transaction boundary:
  add `transaction.md`
- Need interceptor-based insert fill:
  add `interceptor.md`

## Common Mistakes

- Calling `insertable(...)` and forgetting `executeRows(...)`
- Assuming generated-key backfill happens without `executeRows(true)`
- Using map insert with property names instead of column names
- Using `onConflictThen(...)` without accounting for insert strategy and
  possibly omitted conflict columns
- Replacing native `onConflictThen(...)` with manual check-then-write logic

## Sources

- docs:
  `easy-query-doc/src/ability/insert.md`
- docs:
  `easy-query-doc/src/ability/insertOrUpdate.md`
- source:
  `ClientInsertable`
- source:
  `ProxyEntityConflictThenable`
- source:
  `SQLConflictThenable`
- source:
  `MapClientInsertable`
