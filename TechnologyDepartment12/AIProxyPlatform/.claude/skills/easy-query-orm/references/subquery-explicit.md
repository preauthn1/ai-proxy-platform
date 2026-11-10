# Explicit and Derived-Table Subquery

Use this reference for the explicit subquery branch:

- `where` subquery: `exists` / `notExists` / `in` / `notIn`
- scalar subquery in `select(...)`
- proxy `setSubQuery(...)`
- derived-table / `from (subquery)` style chaining
- grouped subquery `join`
- `toCteAs()` promotion

Do not use this as the main guide for:

- relation-driven implicit subquery / group-join / partition / tree:
  `implicit-query.md`
- quantifier and relation control APIs such as
  `any/all/none/notEmptyAll/filter/configure/mode/subQueryConfigure`:
  `implicit-controls.md`
- broad advanced projection and explicit joins that are not really subquery
  questions:
  `query-composition.md`

## Decision Rules

### Prefer implicit relation subquery first when a real navigation exists

If the requirement is naturally:

- "has at least one child ..."
- "count/sum/avg this child relation ..."
- "first / top-N child ..."

then relation-driven implicit form is usually the primary answer:

```java
user.bankCards().any(card -> card.type().eq("DEBIT"));
user.bankCards().count();
user.bankCards().orderBy(card -> card.openTime().asc()).first().code();
```

Switch to explicit subquery when:

- there is no usable `@Navigate` path
- the SQL shape is intentionally `EXISTS` / `IN` / derived-table / subquery join
- you need a staged queryable that is filtered or ordered again from the outer
  query

### Prefer `expression().subQueryable(...)` over an independent root query for correlated subqueries

Current docs and source both support two styles:

1. context-aware subquery:
   `root.expression().subQueryable(Entity.class)`
2. independent query:
   `easyEntityQuery.queryable(Entity.class)`

Prefer the first style when the subquery must reference outer columns. It is
bound to the current expression context and is the safer default for
correlated `where` / `select` subqueries.

## 1. `where` Subquery

### `EXISTS`

```java
List<Topic> rows = easyEntityQuery.queryable(Topic.class)
    .where(topic -> {
        topic.expression().exists(
            topic.expression().subQueryable(BlogEntity.class)
                .where(blog -> {
                    blog.id().eq(topic.id());
                    blog.title().like("公告");
                })
        );
    })
    .toList();
```

Use this when the subquery is a boolean predicate on the current row.

### `NOT EXISTS`

```java
List<Topic> rows = easyEntityQuery.queryable(Topic.class)
    .where(topic -> {
        topic.expression().notExists(
            topic.expression().subQueryable(BlogEntity.class)
                .where(blog -> {
                    blog.id().eq(topic.id());
                    blog.title().like("禁用");
                })
        );
    })
    .toList();
```

### `IN` / `NOT IN`

The subquery must project a single compatible type:

```java
List<Topic> rows = easyEntityQuery.queryable(Topic.class)
    .where(topic -> {
        Query<String> blogIds = topic.expression().subQueryable(BlogEntity.class)
            .where(blog -> blog.title().like("专题"))
            .selectColumn(blog -> blog.id());

        topic.id().in(blogIds);
    })
    .toList();
```

Rules:

- project one compatible column with `selectColumn(...)`
- the outer column type and subquery result type must match
- do not leave a bare type fragment in `where(...)`; it must become a boolean
  predicate (`exists`, `in`, scalar comparison, etc.)

## 2. Scalar Subquery in `select(...)`

### `expression().subQuery(query).as(...)`

```java
List<DocUserVO> rows = easyEntityQuery.queryable(DocUser.class)
    .select(DocUserVO.class, user -> Select.of(
        user.FETCHER.allFields(),
        user.expression().subQuery(
            user.expression().subQueryable(DocBankCard.class)
                .where(card -> card.uid().eq(user.id()))
                .selectCount()
        ).as(DocUserVO.Fields.cardCount)
    ))
    .toList();
```

This is the explicit scalar-subquery path for class-based projection.

### Proxy result: `setSubQuery(...)`

```java
List<DocUserVO> rows = easyEntityQuery.queryable(DocUser.class)
    .select(user -> {
        DocUserVOProxy r = new DocUserVOProxy();
        r.selectAll(user);
        r.cardCount().setSubQuery(
            user.expression().subQueryable(DocBankCard.class)
                .where(card -> card.uid().eq(user.id()))
                .selectCount()
        );
        return r;
    })
    .toList();
```

Prefer implicit relation aggregate instead:

```java
user.bankCards().count().as(DocUserVO.Fields.cardCount)
```

when a valid relation already exists and no manual SQL shape is needed.

## 3. Derived Table / `FROM (subquery)`

easy-query models derived tables by continuing to query the result of a
projected queryable.

### `Select.DRAFT`

```java
List<Draft3<String, String, String>> rows = easyEntityQuery.queryable(SysUser.class)
    .where(user -> user.name().like("123"))
    .select(user -> Select.DRAFT.of(
        user.name(),
        user.id(),
        user.phone()
    ))
    .where(view -> view.value3().eq("1234567"))
    .orderBy(view -> view.value1().asc())
    .toList();
```

The outer `where/orderBy` applies to the derived-table columns, not the base
entity table.

### Proxy/VO-shaped derived table

```java
List<MyUserVO> rows = easyEntityQuery.queryable(SysUser.class)
    .where(user -> user.name().like("123"))
    .select(user -> new MyUserVOProxy()
        .vo1().set(user.name())
        .vo2().set(user.id())
        .vo3().set(user.phone())
        .bankCardCount().set(user.bankCards().count()))
    .where(view -> view.bankCardCount().gt(1L))
    .orderBy(view -> view.bankCardCount().asc())
    .toList();
```

Use this when the outer filter/sort must target staged computed columns.

## 4. Join a Subquery

Grouped subquery join:

```java
EntityQueryable<Draft2Proxy<String, Long>, Draft2<String, Long>> bankCardCountQuery =
    easyEntityQuery.queryable(SysBankCard.class)
        .groupBy(card -> GroupKeys.of(card.bankId()))
        .select(group -> Select.DRAFT.of(
            group.key1(),
            group.count()
        ));

List<Draft2<String, Long>> rows = easyEntityQuery.queryable(SysBank.class)
    .leftJoin(bankCardCountQuery, (bank, cardGroup) -> bank.id().eq(cardGroup.value1()))
    .select((bank, cardGroup) -> Select.DRAFT.of(
        bank.name(),
        cardGroup.value2()
    ))
    .toList();
```

Use this when:

- the grouped relation shape is clearer as a manual view
- relations are missing
- a manual grouped subquery must be joined into a broader explicit query

If the same requirement is just "join one grouped to-many relation metric back
to the root", also consider the simpler implicit path:

```java
easyEntityQuery.queryable(SysBank.class)
    .subQueryToGroupJoin(bank -> bank.bankCards())
    .select(bank -> Select.DRAFT.of(
        bank.name(),
        bank.bankCards().count()
    ));
```

## 5. Promote the Subquery to CTE with `toCteAs()`

One-off grouped subquery joins can stay as inline views. Promote to CTE when:

- the staged query is reused
- the SQL is easier to reason about as named phases
- later filtering/ranking/joining reads better as a pipeline

```java
EntityQueryable<Draft2Proxy<String, Long>, Draft2<String, Long>> bankCardCountCte =
    easyEntityQuery.queryable(SysBankCard.class)
        .groupBy(card -> GroupKeys.of(card.bankId()))
        .select(group -> Select.DRAFT.of(
            group.key1(),
            group.count()
        ))
        .toCteAs();

List<Draft2<String, Long>> rows = easyEntityQuery.queryable(SysBank.class)
    .leftJoin(bankCardCountCte, (bank, cardGroup) -> bank.id().eq(cardGroup.value1()))
    .select((bank, cardGroup) -> Select.DRAFT.of(
        bank.name(),
        cardGroup.value2()
    ))
    .toList();
```

## 6. High-Value Boundary Rules

### Same-expression subquery vs independent query

Docs and source together imply this split:

- `root.expression().subQueryable(...)`:
  same-expression subquery
- `easyEntityQuery.queryable(...)`:
  independent expression

Practical consequences:

- same-expression subquery is the default for correlated predicates or scalar
  subqueries that reference outer columns
- independent query is fine when correlation is unnecessary or when you are
  intentionally composing a detached subquery/derived table

### Value-filter propagation is not automatic for every subquery

Source-backed detail:

- `DefaultSQLClientApiFactory.createSubQueryable(...)` calls
  `EasySQLExpressionUtil.propagationValueFilter(...)`
- propagation only happens when the parent filter implements
  `PropagationValueFilter`

Practical rule:

- do not assume a custom `ValueFilter` automatically affects child subqueries
- if the requirement is "same expression and same empty/null filtering rules",
  use a propagation-capable filter such as
  `NotNullOrEmptyValueFilter.DEFAULT_PROPAGATION_SUPPORTS`

### Logic-delete toggles are not cross-expression transitive

Docs explicitly state `disableLogicDelete()` / `enableLogicDelete()` /
`useLogicDelete(...)` / `tableLogicDelete(...)` do not automatically propagate
across expression boundaries; subqueries compute logic-delete behavior
independently.

Practical rule:

- if a manual subquery must ignore logic delete, configure that subquery
  explicitly
- do not assume the outer query's logic-delete toggle changed the inner
  explicit subquery

## 7. When To Rewrite Instead of Doubling Down

- Multiple correlated to-many metrics on the same relation:
  prefer implicit relation + `subQueryToGroupJoin(...)`, or a manual grouped
  derived-table join, instead of many repeated scalar subqueries
- Simple `EXISTS` over a declared relation:
  prefer `relation.any(...)`
- Simple child count/sum/avg over a declared relation:
  prefer `relation.count()/sum()/avg()`
- Need outer filtering on a computed scalar:
  use derived-table chaining instead of trying to reuse a select alias in the
  same level

## 8. Common Mistakes

- Building a correlated subquery with an independent root query when
  `expression().subQueryable(...)` was the cleaner context-aware choice
- Leaving `toMany().where(...)` alone in `where(...)` without a boolean/scalar
  terminal such as `any`, `count`, or scalar comparison
- Using `IN` subquery with the wrong projected type
- Repeating several heavy scalar subqueries when group-join rewrite or a manual
  grouped subquery join is the better SQL shape
- Assuming outer logic-delete or value-filter config automatically applies to
  every explicit subquery

## Read next

- relation-driven alternative:
  `implicit-query.md`
- quantifier/control APIs:
  `implicit-controls.md`
- AP/reporting + CTE/window pipelines:
  `ap-analytics.md`
- broader advanced projection / explicit join:
  `query-composition.md`

## Source anchors

- docs:
  `easy-query-doc/src/sub-query/README.md`
- docs:
  `easy-query-doc/src/sub-query/where-sub-query.md`
- docs:
  `easy-query-doc/src/sub-query/select-sub-query.md`
- docs:
  `easy-query-doc/src/sub-query/from-sub-query.md`
- docs:
  `easy-query-doc/src/sub-query/join-sub-query.md`
- docs:
  `easy-query-doc/src/ability/where.md`
- docs:
  `easy-query-doc/src/adv/logic-delete.md`
- source:
  `sql-platform/sql-api-proxy/.../core/proxy/core/Expression.java`
- source:
  `sql-platform/sql-api-proxy/.../core/proxy/set/DSLColumnSet.java`
- source:
  `sql-core/.../basic/api/select/Query.java`
- source:
  `sql-core/.../api/def/DefaultSQLClientApiFactory.java`
- source:
  `sql-core/.../util/EasySQLExpressionUtil.java`
