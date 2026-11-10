# Query Composition

Read this after `query.md` when the task needs more than ordinary filter/order/page
chains. This file only covers the advanced composition pieces that are easy to
misremember.

For explicit `where/select/from/join` subquery shapes, derived tables, or CTE
promotion, read `subquery-explicit.md`.

## Advanced Projection

Partial entity fields:

```java
List<DocBankCard> cards = easyEntityQuery.queryable(DocBankCard.class)
    .select(card -> card.FETCHER.id().code())
    .toList();
```

DTO explicit mapping:

```java
List<CardVO> rows = easyEntityQuery.queryable(DocBankCard.class)
    .select(CardVO.class, card -> Select.of(
        card.FETCHER.allFields(),
        card.user().name().as(CardVO.Fields.userName),
        card.bank().name().as(CardVO.Fields.bankName)
    ))
    .toList();
```

Proxy result mapping:

```java
List<CardVO> rows = easyEntityQuery.queryable(DocBankCard.class)
    .select(card -> new CardVOProxy()
        .selectAll(card)
        .userName().set(card.user().name())
        .bankName().set(card.bank().name()))
    .toList();
```

Useful proxy assignment methods:

- `set`
- `setNull`
- `setSubQuery`
- `setSQL`
- `setExpression`
- `selectAll`

Relation collection projection without `selectAutoInclude`:

```java
List<MyUserVO> list = easyEntityQuery.queryable(SysUser.class)
    .select(user -> new MyUserVOProxy()
        .vo1().set(user.name())
        .vo2().set(user.id())
        .cards().set(user.bankCards().where(card -> card.type().eq("储蓄卡")), (self, target) -> {
            self.type().set(target.code());
            self.code().set(target.bank().name());
        }))
    .toList();
```

Use this when:

- the return type is a custom proxy VO, not a DTO class driven by
  `selectAutoInclude`
- a child collection needs custom field mapping
- you want relation-driven secondary loading without manual post-processing

This is a real alternative to `selectAutoInclude`, not a fallback hack. Prefer
it when the mapping logic lives naturally in a proxy result type.

Relation-internal aggregate distinctness:

```java
List<Draft1<Integer>> rows = easyEntityQuery.queryable(SubJoinUser.class)
    .select(user -> Select.DRAFT.of(
        user.books().distinct().sum(book -> book.author().age())
    ))
    .toList();
```

Keep `distinct()` inside the relation chain when the distinctness belongs to
the implicit subquery itself.

## Draft and Tuple Return

```java
List<Draft2<String, Integer>> rows = easyEntityQuery.queryable(SysUser.class)
    .select(user -> Select.DRAFT.of(user.name(), user.age()))
    .toList();
```

`Select.DRAFT` returns `Draft1...10`. `Select.TUPLE` returns `Tuple1...10`.

## Explicit Joins

Use joins when the project does not model a relation or when the SQL shape is
clearer with explicit tables.

```java
easyEntityQuery.queryable(Topic.class)
    .leftJoin(BlogEntity.class, (topic, blog) -> topic.id().eq(blog.id()))
    .where((topic, blog) -> blog.title().isNotNull())
    .select((topic, blog) -> blog)
    .toList();
```

Implicit and explicit relations can be mixed, but if a valid navigation already
exists, prefer the relation query form from `implicit-query.md` and keep
`relation-query.md` for relation metadata itself.

## Manual Subquery

Full explicit-subquery routing now lives in `subquery-explicit.md`. Keep this
section as the quick scalar example only.

Manual scalar subquery pattern:

```java
List<DocUserVO> rows = easyEntityQuery.queryable(DocUser.class)
    .select(DocUserVO.class, user -> Select.of(
        user.FETCHER.allFields(),
        user.expression().subQuery(
            easyEntityQuery.queryable(DocBankCard.class)
                .where(card -> card.uid().eq(user.id()))
                .selectCount()
        ).as(DocUserVO.Fields.cardCount)
    ))
    .toList();
```

If a valid navigation exists, prefer the implicit form:

```java
user.bankCards().count().as(DocUserVO.Fields.cardCount)
```

## Native SQL Fragment

For current-source native SQL guidance, entrypoint choice, reusable wrapper
patterns, and quote/format boundaries, prefer `native-sql.md`.
The snippets here are only quick examples inside the broader projection/join
reference.

Proxy assignment:

```java
r.cardCount().setSQL("IFNULL({0},1)", c -> c.expression(user.age()));
```

For predicate/select fragment defaults, wrapper patterns, and current-source
`rawSQLCommand(...)` / `rawSQLStatement(...)` naming, read `native-sql.md`
instead of treating this file as the primary raw-SQL guide.

## Chunking and Streaming

Useful large-result helpers:

```java
query.toChunk(size, rows -> { ... });
query.streamBy(stream -> stream.count(), 1000);
```

For differential update tracking, read `write-tracking.md`.
