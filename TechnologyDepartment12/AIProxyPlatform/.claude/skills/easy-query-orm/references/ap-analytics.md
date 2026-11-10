# AP / Analytics

Use this when the request is report/dashboard/statistics oriented: dimensions +
metrics, grouped aggregates, conditional aggregates, ranked snapshots,
CTE/window pipelines, or UNION-based report shaping.

This is the AP side of easy-query. Its strength is not only explicit
`groupBy(...)`, but also relation-driven implicit Group, CTE reuse, and window
functions that stay inside the ORM DSL.

## Heuristics

- Use explicit `groupBy(...)` + `having(...)` for classic fact-table
  dimension/metric reports.
- Use aggregate `.filter(...)` first for conditional metrics (`CASE WHEN`-like
  statistics).
- Use `subQueryToGroupJoin(...)` when the same to-many relation is referenced
  repeatedly for counts, sums, ordering, or string aggregation.
- Use `toCteAs(...)` + `rowNumberOver(...)` / `rankNumberOver(...)` /
  `denseRankNumberOver(...)` / `countOver(...)` / `sumOver(...)` /
  `avgOver(...)` / `maxOver(...)` / `minOver(...)` for ranked snapshots,
  top-N-per-group, and staged analytics.
- Use `union(...)` / `unionAll(...)` only when all branches have the same
  result shape.

## 1. Explicit GroupBy + Having

Proxy group queries are group-aware. After `groupBy(...)`, the grouped source is
accessed from `group.groupTable()`, and group keys come from `key1()`, `key2()`,
... depending on `GroupKeys.of(...)`.

```java
List<UserCompanyAgg> rows = easyEntityQuery.queryable(SysUser.class)
    .where(user -> user.createTime().ge(beginTime))
    .groupBy(user -> GroupKeys.of(user.companyId()))
    .having(group -> {
        group.groupTable().id().count().gt(10L);
    })
    .select(group -> {
        UserCompanyAggProxy r = new UserCompanyAggProxy();
        r.companyId().set(group.key1());
        r.userCount().set(group.count());
        r.maxBirthday().set(group.groupTable().birthday().max());
        r.avgAge().set(group.groupTable().age().avg());
        return r;
    })
    .toList();
```

Rules:

- single-table group: `group.groupTable()` is the grouped table
- multi-table group: use `group.groupTable().t1 ... tn`
- keep non-key fields in aggregate form rather than selecting raw columns

Source anchors:

- docs: `easy-query-doc/src/ability/select/group.md`
- source: `com.easy.query.core.proxy.sql.GroupKeys`
- proxy entry: `AbstractEntityQueryable.having(...)`

## 2. Conditional Aggregates (`CASE WHEN`-style metrics)

For KPI-style metrics such as recent-user count, adult average age, debit-card
count, or channel-specific revenue, prefer aggregate `.filter(...)` first.

```java
List<UserCompanyMetric> rows = easyEntityQuery.queryable(SysUser.class)
    .groupBy(user -> GroupKeys.of(user.companyId()))
    .select(group -> {
        UserCompanyMetricProxy r = new UserCompanyMetricProxy();
        r.companyId().set(group.key1());
        r.totalUsers().set(group.count());
        r.recentUsers().set(group.groupTable().id().count().filter(() -> {
            group.groupTable().createTime().ge(beginTime);
        }));
        r.avgAdultAge().set(group.groupTable().age().avg().filter(() -> {
            group.groupTable().age().ge(18);
        }));
        return r;
    })
    .toList();
```

Use this before lower-level explicit case-when builders.

Source anchors:

- docs/readme examples: `easy-query/README.md`, `easy-query/README-zh.md`
- relation/aggregate grouping docs: `easy-query-doc/src/ability/select/group.md`

## 3. Ad Hoc Metric Tuples with `Select.DRAFT`

If a one-off analytics query does not justify a dedicated DTO/VO, use
`Select.DRAFT.of(...)` for a lightweight typed result.

```java
var rows = easyEntityQuery.queryable(SysUser.class)
    .groupBy(user -> GroupKeys.of(user.companyId()))
    .select(group -> Select.DRAFT.of(
        group.key1(),
        group.count(),
        group.groupTable().age().avg(),
        group.groupTable().birthday().max()
    ))
    .toList();
```

Use a DTO/VO instead when the result becomes stable API surface.

## 4. Relation Analytics with Implicit Group

This is one of easy-query's strongest AP capabilities. When the same to-many
relation is used multiple times for metrics, relation-driven subqueries can be
rewritten to a group join through `subQueryToGroupJoin(...)`.

```java
var rows = easyEntityQuery.queryable(SysUser.class)
    .subQueryToGroupJoin(user -> user.bankCards())
    .select(user -> Select.DRAFT.of(
        user.id(),
        user.bankCards().count(),
        user.bankCards().where(card -> card.type().eq("DEBIT")).count(),
        user.bankCards()
            .orderBy(card -> card.openTime().asc())
            .elements(0, 1)
            .joining(card -> card.type(), ",")
    ))
    .toList();
```

Use it when:

- the same relation appears in multiple metrics
- one query mixes relation count / aggregate / ordering / joining
- relation analytics would otherwise become many correlated subqueries

Related switches:

- query-level `subQueryToGroupJoin(...)`
- annotation-level `@Navigate(subQueryToGroupJoin = true)`
- deeper branch tuning through `subQueryConfigure(...)`

Source anchors:

- docs: `easy-query-doc/src/ability/select/group.md`
- source: `Navigate.subQueryToGroupJoin()`
- tests: `sql-test/.../ManyJoinTest.java`

## 5. CTE + Window / Partition Analytics

For ranked snapshots, dedupe-latest-row, top-N-per-group, partition totals, and
staged report pipelines, use CTE plus window functions.

Direct CTE reuse:

```java
var rankedSnapshots = easyEntityQuery.queryable(SysBankCard.class)
    .leftJoin(SysUser.class, (card, user) -> card.uid().eq(user.id()))
    .select((card, user) -> Select.DRAFT.of(
        card.code(),
        card.type(),
        user.name(),
        card.expression().rowNumberOver()
            .partitionBy(card.type())
            .orderBy(card.openTime())
    ))
    .toCteAs("RankedSnapshots");
```

CTE view entity pattern from docs/source:

```java
@Override
public Supplier<Query<M8UserTemp2>> viewConfigure(QueryRuntimeContext runtimeContext) {
    return () -> {
        SQLClientApiFactory sqlClientApiFactory = runtimeContext.getSQLClientApiFactory();
        ClientQueryable<M8User> queryable = sqlClientApiFactory.createQueryable(M8User.class, runtimeContext);
        return new EasyEntityQueryable<>(M8UserProxy.createTable(), queryable)
            .where(m -> {
                m.age().isNull();
            })
            .select(M8UserTemp2.class, m -> Select.of(
                m.FETCHER.allFields(),
                m.expression().rowNumberOver()
                    .partitionBy(m.age())
                    .orderByDescending(m.createTime())
                    .as("rowNumber")
            ));
    };
}
```

Source-level window functions exposed by easy-query:

- `rowNumberOver(...)`
- proxy window rank builders: `rankOver()` and `denseRankOver()`
- aggregate-over builders: `countOver(...)`, `sumOver(...)`, `avgOver(...)`,
  `maxOver(...)`, `minOver(...)`
- lower-level SQL function names in the core layer: `rankNumberOver(...)` and
  `denseRankNumberOver(...)`

Use deterministic ordering for partition analytics. Do not rely on natural row
order.

Source anchors:

- docs: `easy-query-doc/src/adv/cte.md`
- source: `Query.toCteAs(...)`, `SQLPartitionByFunc`
- tests: `sql-test/.../M8UserTemp2.java`, `MySQL8Test2.java`, `ManyJoinTest.java`

## 6. UNION / UNION ALL for Report Shaping

Use union when multiple branches share the same output schema and should be
filtered or sorted as one result set.

```java
EntityQueryable<TopicUnionProxy, TopicUnion> q1 = easyEntityQuery
    .queryable(Topic.class)
    .where(t -> t.id().eq("123"))
    .select(t -> new TopicUnionProxy().selectAll(t));

EntityQueryable<TopicUnionProxy, TopicUnion> q2 = easyEntityQuery
    .queryable(BlogEntity.class)
    .where(b -> b.createTime().ge(beginTime))
    .select(b -> new TopicUnionProxy()
        .id().set(b.id())
        .stars().set(b.star())
        .abc().set(b.content()));

List<TopicUnion> rows = q1.unionAll(q2)
    .where(r -> r.id().eq("123321"))
    .toList();
```

Rules:

- every branch must project the same result shape
- use `unionAll(...)` when de-duplication is not required
- prefer a custom DTO/VO/proxy result over in-memory merge after two queries

Source anchors:

- docs: `easy-query-doc/src/ability/select/union.md`
- proxy entry: `AbstractEntityQueryable.unionAll(...)`
- tests: `sql-test/.../QueryTest.java`

## 7. Common Mistakes

- Doing Java Stream regrouping / ranking first instead of expressing the report
  in DSL.
- Using `where(...)` when the condition is semantically aggregate-level and
  belongs in `having(...)`.
- Selecting raw non-grouped columns after `groupBy(...)`.
- Repeating to-many relation metrics without `subQueryToGroupJoin(...)` on hot
  paths.
- Running window functions without deterministic partition order.
- Using `UNION` branches that return different columns or incompatible result
  types.
- Filtering ranked results without a CTE/view stage when the SQL shape requires
  one.

## Read next

- relation-derived metrics and partition-style relation operators:
  `implicit-query.md`
- predicate/branch controls around relation analytics:
  `implicit-controls.md`
- typed SQL expressions:
  `typed-sql-expressions.md`
- raw SQL fragments when a metric truly needs lower-level customization:
  `native-sql.md`
- code-first DDL / sharding / multi-datasource after analytics setup:
  `advanced.md`
