# Query DSL

Single-table queries: filtering, dynamic conditions, ordering, projection to DTO, terminals, and pagination.
For relation modeling see `relation-query.md`; for relation-driven predicates/subqueries see `implicit-query.md`;
for explicit joins and advanced projection see `query-composition.md`; for aggregate/groupBy see `advanced.md`.

## When to use / not

Use for reading data from one table (optionally projected into a DTO). Chain:
`queryable(Entity) → where → orderBy → select → terminal`.

## The chain

Java:
```java
List<Topic> list = easyEntityQuery.queryable(Topic.class)
        .where(o -> o.stars().gt(100))
        .orderBy(o -> o.createTime().desc())
        .toList();
```
Kotlin (same methods, trailing-lambda surface, `Type::class.java`):
```kotlin
val list = easyEntityQuery.queryable(Topic::class.java)
    .where { it.stars().gt(100) }
    .orderBy { it.createTime().desc() }
    .toList()
```

## Filtering — `where`

Conditions can be chained as separate `where(...)` calls or grouped in one lambda.

```java
// chained — AND
easyEntityQuery.queryable(Topic.class)
        .where(o -> o.id().isNotNull())
        .where(o -> o.id().ne("x"))
        .toList();

// grouped — AND
easyEntityQuery.queryable(Topic.class)
        .where(o -> {
            o.id().eq("2");
            o.title().eq("2c");
        })
        .toList();
```

Common predicates on a column accessor:

| Need | Predicate |
|------|-----------|
| equals / not equals | `.eq(v)` / `.ne(v)` |
| comparisons | `.gt(v)` `.ge(v)` `.lt(v)` `.le(v)` |
| like | `.like(v)` `.notLike(v)` |
| null / blank | `.isNotNull()` `.isNull()` `.isNotBlank()` |
| in / not in | `.in(collection)` `.notIn(collection)` |
| range (closed) | `.rangeClosed(beginCond, begin, endCond, end)` |

```java
easyEntityQuery.queryable(SysUser.class)
        .where(o -> {
            o.name().like("张");
            o.phone().isNotBlank();
            o.id().in(Arrays.asList("1", "2", "3"));
        })
        .toList();
```

## Dynamic / optional conditions — use the gated overloads

Never concatenate strings. Predicate overloads accept a boolean gate; when
`false`, the condition is skipped.

```java
// gate per predicate (.eq(condition, value) / .like(condition, value))
easyEntityQuery.queryable(SysUser.class)
        .where(o -> {
            o.name().like(EasyStringUtil.isNotBlank(req.getName()), req.getName());
            o.phone().like(EasyStringUtil.isNotBlank(req.getPhone()), req.getPhone());
            o.createTime().rangeClosed(
                req.getBegin() != null, req.getBegin(),
                req.getEnd()   != null, req.getEnd());
        })
        .toList();

// gate the whole where clause
easyEntityQuery.queryable(SysUser.class)
        .where(EasyStringUtil.isNotBlank(req.getName()), o -> o.name().like(req.getName()))
        .where(req.getBegin() != null, o -> o.createTime().ge(req.getBegin()))
        .toList();
```
Kotlin:
```kotlin
easyEntityQuery.queryable(SysUser::class.java)
    .where {
        it.name().like(!req.name.isNullOrBlank(), req.name)
        it.createTime().rangeClosed(req.begin != null, req.begin, req.end != null, req.end)
    }
    .toList()
```

## Ordering

```java
.orderBy(o -> o.createTime().desc())                  // single
.orderBy(o -> { o.createTime().desc(); o.id().desc(); })  // multi + stable tiebreaker
```

## Terminals

| Terminal | Meaning |
|----------|---------|
| `.toList()` | all rows |
| `.firstOrNull()` | first row or null (adds `LIMIT 1`) |
| `.singleOrNull()` | at-most-one; use for unique business keys |
| `.count()` | row count (`long`) |
| `.any()` | existence check (`boolean`) — cheaper than `count() > 0` |

```java
boolean exists = easyEntityQuery.queryable(SysUser.class).where(o -> o.phone().eq(phone)).any();
SysUser u    = easyEntityQuery.queryable(SysUser.class).where(o -> o.email().eq(email)).singleOrNull();
```
Use `singleOrNull()` (not `firstOrNull()`) when the key is supposed to be unique — it surfaces accidental
duplicates instead of hiding them.

## Projection to a DTO — `select`

```java
List<TopicDTO> dtos = easyEntityQuery.queryable(Topic.class)
        .where(o -> o.stars().gt(100))
        .orderBy(o -> o.id().asc())
        .select(TopicDTO.class, s -> Select.of(
                s.id().as(TopicDTO::getId),
                s.title().as(TopicDTO::getTitle)
        ))
        .toList();
```

## Limit / top-N — `limit`

When you want a fixed number of rows (not a full page result), use `limit`. Two forms:

```java
// top N rows
List<Topic> top10 = easyEntityQuery.queryable(Topic.class)
        .orderBy(o -> o.createTime().desc())   // pair with a sort, like pagination
        .limit(10)
        .toList();

// offset + rows (skip, take)
List<Topic> slice = easyEntityQuery.queryable(Topic.class)
        .orderBy(o -> o.id().asc())
        .limit(20, 10)            // skip 20, take 10
        .toList();
```
There is also a gated overload `limit(condition, offset, rows)`. Use
`limit(n)` for top-N; use `toPageResult(...)` only when you also need total
count.

## Pagination — `toPageResult`

Returns `EasyPageResult<T>` (`com.easy.query.core.api.pagination`) with `getData()` and `getTotal()`. **Always pair with `orderBy`** and add a
tiebreaker if the sort key is not unique, or pages may repeat/skip rows.

```java
EasyPageResult<Topic> page = easyEntityQuery.queryable(Topic.class)
        .where(o -> o.stars().gt(100))
        .orderBy(o -> { o.createTime().desc(); o.id().desc(); })  // stable
        .toPageResult(2, 20);   // pageIndex (1-based), pageSize

List<Topic> rows = page.getData();
long total       = page.getTotal();
```
Pagination composes with `select(...)` — call `.toPageResult(...)` after the projection.

## Common mistakes

- `firstOrNull()` on a unique key → use `singleOrNull()`.
- `toList()` then `.stream().filter()/.sorted()/.skip()` in memory → push into the DSL.
- `toPageResult` with no `orderBy`, or an unstable sort key.
- Building dynamic filters with string concatenation instead of gated overloads.
- `count() > 0` for existence → use `any()`.

