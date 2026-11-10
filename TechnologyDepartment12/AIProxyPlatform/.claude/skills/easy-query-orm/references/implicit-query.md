# Implicit Query Capabilities

easy-query's strongest "hidden" feature set is relation-driven implicit SQL.
The proxy path itself decides whether the framework emits an implicit join,
correlated subquery, group-join rewrite, partition window, boolean projection,
or tree query.

Useful default heuristic: if the business requirement can be stated as
"follow this relation path and then filter / aggregate / flatten / build a
tree", easy-query usually has a relation-driven form that is shorter and more
idiomatic than explicit join or junction-table DSL.

Use this for the core implicit feature set. For quantifier predicates such as
`any/all/none/notEmptyAll`, `subQueryConfigure`, `filter/configure/mode`,
`flatElement`, or `valueOf`, read `implicit-controls.md`.

For manual/explicit `where/select/from/join` subquery shapes, derived tables,
or CTE promotion, read `subquery-explicit.md`.

## What Triggers Implicit SQL

- To-one navigation like `card.user().name()` triggers implicit join SQL.
- To-many navigation methods like `any/none/all/count/sum/max/joining` trigger
  implicit subquery or group-join SQL.
- Ranked child access like `first()`, `element(index)`, and
  `elements(start,end)` triggers implicit partition/window SQL.
- Boolean relation projection like `anyValue()` and `noneValue()` triggers
  `CASE WHEN EXISTS ...` style SQL.
- Tree APIs like `asTreeCTE()` trigger recursive SQL generation.

If relation metadata is wrong, these APIs usually produce wrong SQL or runtime
failures. Verify `@Navigate` first.

## Relation Modeling Required

Implicit APIs assume relation metadata is already right. If not, they usually
compile into wrong SQL or fail at runtime. Read `relation-query.md` or
`entity-modeling-navigate.md` before debugging the DSL itself.

## 1. Implicit Join

Use to-one navigation for implicit join SQL. The join appears only when the
path is actually referenced.

```java
List<DocBankCard> cards = easyEntityQuery.queryable(DocBankCard.class)
    .where(card -> {
        card.user().name().eq(queryName);
        card.bank().name().contains(bankName);
    })
    .toList();
```

Join semantics:

- `required = true` can make eligible to-one joins inner joins
- otherwise to-one navigation is usually left-join oriented
- explicit and implicit joins can be mixed

For to-one `.filter(...)` that pushes predicates into join `ON`, see
`implicit-controls.md`.

```java
easyEntityQuery.queryable(DocBankCard.class)
    .leftJoin(DocBank.class, (card, bank) -> card.bankId().eq(bank.id()))
    .where((card, bank) -> {
        card.user().name().eq("x");
    })
    .select((card, bank) -> Select.DRAFT.of(
        card.code(),
        card.user().name(),
        bank.name()
    ));
```

## 2. Implicit Subquery and Scalar Relation Operators

Use to-many navigation for `EXISTS`, scalar subquery, aggregate subquery, or
boolean relation projection shapes.

```java
easyEntityQuery.queryable(SysBank.class)
    .where(bank -> {
        bank.bankCards().any(card -> card.type().eq("DEBIT"));
        bank.bankCards().none(card -> card.type().eq("DISABLED"));
    });
```

High-value operators:

- `any()`, `any(predicate)`
- `none(predicate)`
- `all(predicate)`
- `count()`, `count(column)`
- `sum(column)`, `avg(column)`, `max(column)`, `min(column)`
- `where(predicate)`
- `distinct()`
- `orderBy(orderExpression)`
- `first()`
- `element(index)`
- `elements(start,end)`
- `joining(column)` and `joining(column, separator)`
- `anyValue()`
- `noneValue()`

Useful rules:

- `joining(...)` can be combined with `where(...)`, `orderBy(...)`,
  `elements(...)`, and nested to-one navigation
- `first()` is the partition-style "first ranked child"
- `element(index)` is zero-based
- `elements(start,end)` is zero-based and end-inclusive
- current source interface returns `SQLQueryable` after `elements(...)`, so the
  ranked slice can continue into `where/distinct/count/sum/avg/max/min/joining`
  style APIs instead of stopping at projection-only usage
- For partition-style operators, require deterministic ordering from
  `orderBy(...)`, `orderByProps`, or `partitionOrder`; do not assume database
  natural order.

For `notEmptyAll(...)`, relation `.filter(...)`, or relation-local
configuration, see `implicit-controls.md`.

Typical aggregate projection:

```java
List<DocUserVO> users = easyEntityQuery.queryable(DocUser.class)
    .select(DocUserVO.class, user -> Select.of(
        user.FETCHER.allFields(),
        user.bankCards().count().as(DocUserVO.Fields.cardCount)
    ))
    .toList();
```

Repeated to-many subqueries can get expensive. Convert hot paths to implicit
Group when the same relation is reused.

## 3. Implicit Group / Group Join Conversion

Use group-join conversion when correlated relation subqueries repeat.

Available switches:

- query-level `.subQueryToGroupJoin(...)`
- annotation-level `@Navigate(subQueryToGroupJoin = true)`
- broader behavior flag `EasyBehaviorEnum.ALL_SUB_QUERY_GROUP_JOIN`

Pattern:

```java
easyEntityQuery.queryable(SysUser.class)
    .subQueryToGroupJoin(user -> user.bankCards())
    .where(user -> {
        user.bankCards().any(card -> card.type().eq("DEBIT"));
        user.bankCards().count().gt(2L);
    })
    .toList();
```

Use group-join conversion when:

- the same to-many relation is referenced multiple times in one query
- the query combines `where + orderBy + aggregate + joining`
- source/test evidence shows correlated subqueries are too slow

Source notes:

- eligible group-join expressions may auto-merge
- disable that with `EasyBehaviorEnum.GROUP_JOIN_NOT_ALLOW_AUTO_MERGE`
- deep nested to-many paths can be configured per branch

## 4. Implicit Partition / Ranked Child Access

This is the official-style `Implicit Partition Grouping` / `partition by`
branch: rank rows inside one relation slice, then continue navigating from the
ranked child.

Use partition operators for first child, nth child, top-N window, or
relation-level limit/offset semantics.

Canonical shape from source tests:

```java
List<SysUser> list = easyEntityQuery.queryable(SysUser.class)
    .where(user -> {
        // 用户的银行卡中第一个开户银行卡是工商银行的
        user.bankCards()
            .orderBy(x -> x.openTime().asc())
            .first()
            .bank()
            .name()
            .eq("工商银行");
    })
    .toList();
```

Treat prompts like `partition by`, "第一条子记录", "第一张开户银行卡", "首条明细",
or "top-N child" as this branch before considering explicit SQL/window code.

Relevant `@Navigate` fields:

- `orderByProps`
- `offset`
- `limit`
- `partitionOrder`

`PartitionOrderEnum` choices from source:

- `THROW`
- `IGNORE`
- `NAVIGATE`
- `KEY_ASC`
- `KEY_DESC`

`THROW` is the default. Ranked child access without explicit order should add
`orderBy(...)`, `orderByProps`, or a non-throw `partitionOrder`.

Copyable patterns:

- first ranked child then continue to-one navigation:
  `root.children().orderBy(...).first().toOnePath().field()`
- nth ranked child:
  `root.children().orderBy(...).element(index)`
- top-N ranked child window:
  `root.children().orderBy(...).elements(start,end)`
- top-N rows projected as string/list-like aggregate:
  `root.children().orderBy(...).elements(...).joining(...)`
- top-N window after slicing then aggregate:
  `root.children().orderBy(...).elements(...).max(...)`
- top-N window after slicing then filter/existence/value check:
  `root.children().orderBy(...).elements(...).where(...).noneValue()`

Source-backed evidence:

- docs/examples explicitly show `elements(...).joining(...)` and
  `elements(...).where(...).noneValue()`
- current proxy API `SQLManyQueryable.elements(...)` returns `SQLQueryable`
- `SQLQueryable` exposes `count/sum/avg/max/min/joining/where/distinct`
- current tests directly show `elements(3, 5).max(x -> x.code())`

That means `elements(...).max(...)` is directly evidenced, while
`elements(...).count/sum/avg/min(...)` follow from the current interface return
type even if the exact same aggregate is not shown in the nearby docs/tests.

Annotation comment notes:

- `required = true` can make group join inner-join oriented
- `required = true` can make `first()` / `element(0)` inner-join oriented

For DTO flattening through to-many relation paths, see `@NavigateFlat` in
`entity-modeling-navigate.md`. For runtime `flatElement(...)` traversal and
flattened `toList(...)` results, see `implicit-controls.md`.

## 5. Implicit CaseWhen and Boolean/Aggregate Filters

Aggregate filters are the main proxy-style entry point for implicit
`CASE WHEN`.

```java
group.groupTable().id().count().filter(() -> {
    group.groupTable().birthday().ge(LocalDateTime.of(2024, 1, 1, 0, 0));
});
```

Source also contains lower-level case-when builders:

- `SQLClientFunc.caseWhenBuilder(...)`
- `CaseWhenClientBuilder.caseWhen(...).elseEnd(...)`
- `CaseWhenBuilder.caseWhen(...)`
- `CaseWhenBuilderExpression.caseWhen(...)`

Use aggregate-filter style first. Drop to explicit builders only after checking
project code or source tests.

For arbitrary predicate-to-boolean projection via `expression().valueOf(...)`,
see `implicit-controls.md`.

## 6. Recursive Tree

Tree CTE APIs include `asTreeCTE()` and `asTreeCTECustom(...)`. Do not narrow
them to "full tree only". They also fit `菜单树`, `用户菜单树`, and ancestor
backfill scenarios. Source tests cover:

- seed filtering before `asTreeCTE()`
- recursive-member filtering via `TreeCTEConfigurer.setChildFilter(...)`
- upward recursion via `TreeCTEConfigurer.setUp(true)` for ancestor backfill
- depth/union controls such as `setLimitDeep(...)`, `setUnionAll(false)`, and
  `setDeepColumnName(...)`

Source-backed semantics that matter for answers:

- outer `where(...)` before `asTreeCTE()` filters only the anchor / seed rows
- recursive rows are not filtered by that outer `where(...)`; use
  `setChildFilter(...)` for the recursive member
- if the business needs a true top-rooted menu tree, the root condition must be
  expressed explicitly in the anchor query, usually on the real root-key
  column such as `parentId is null` or `parentId = 0`; do not assume the
  framework will infer that rule for you or that every project exposes a
  to-one `parent` relation

Source comments also note that `selectAutoInclude` ignores the configured tree
navigation to avoid treating `children` as an ordinary one-to-many include.

Full tree / 全量菜单树:

```java
List<MenuTreeVO> tree = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.parentId().isNull(); // or eq(0), use the project's real root rule
    })
    .asTreeCTE()
    .selectAutoInclude(MenuTreeVO.class)
    .toTreeList();
```

Filtered tree is also valid. For `用户菜单树`, do not default to explicit
`SysUserRole` / `SysRoleMenu` subqueries when relation metadata already exposes
the permission path.

For tree answers, start by expressing the project's real root rule. Then split
the guidance into two cases.

If permission data already contains parent/root menus and the reverse path
`SysMenu -> roles -> users` exists, keep the permission rule directly on the
`SysMenu` relation path:

```java
List<MenuTreeVO> tree = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.parentId().isNull(); // or eq(0), use the project's real root rule
        menu.roles().flatElement().users().flatElement().id().eq(userId);
    })
    .asTreeCTE(op -> {
        op.setChildFilter(child -> {
            child.roles().flatElement().users().flatElement().id().eq(userId);
        });
    })
    .selectAutoInclude(MenuTreeVO.class)
    .toTreeList();
```

If only leaf menus are assigned (`补齐祖先节点`), the direct tree above will miss
those parent/root rows. In that case, first backfill ancestors upward, then
rebuild the final tree from explicit root rows:

```java
List<String> ancestorMenuIds = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.roles().flatElement().users().flatElement().id().eq(userId);
    })
    .asTreeCTE(op -> {
        op.setUp(true);
        op.setUnionAll(false);
    })
    .select(menu -> menu.id())
    .distinct()
    .toList();

List<MenuTreeVO> tree = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.id().in(ancestorMenuIds);
        menu.parentId().isNull(); // or eq(0), use the project's real root rule
    })
    .asTreeCTE(op -> {
        op.setChildFilter(child -> child.id().in(ancestorMenuIds));
    })
    .selectAutoInclude(MenuTreeVO.class)
    .toTreeList();
```

Use manual link-table subqueries only when the project truly does not have the
needed `@Navigate` metadata.

Direct filtered CTE is a different shape: it returns a subtree rooted at the
matched seed rows, not a full top-rooted menu tree. Use it only when that is
the intended result:

```java
List<MenuTreeVO> subTrees = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.roles().flatElement().users().flatElement().id().eq(userId);
        menu.name().endsWith("管理");
    })
    .asTreeCTE(op -> {
        op.setChildFilter(child -> child.name().endsWith("管理"));
    })
    .selectAutoInclude(MenuTreeVO.class)
    .toTreeList();
```

Here:

- outer `where(...)` limits only the seed menus
- `setChildFilter(...)` is required if descendants must obey the same filter
- if you need business-rooted trees instead, add the real root predicate
  explicitly instead of treating the matched seeds as final roots

Use `setChildFilter(...)` when the recursive segment itself must be filtered:

```java
List<SysMenu> tree = easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> menu.id().eq(rootId))
    .asTreeCTE(op -> {
        op.setChildFilter(child -> child.name().like(keyword));
    })
    .toTreeList();
```

Important caveat from source behavior:

- `setUp(true)` is correct for ancestor backfill / 补祖先节点.
- When multiple leaf seeds share ancestors, `toTreeList()` may preserve
  multiple overlapping ancestor paths instead of auto-merging them into one
  unique tree.
- `setUnionAll(false)` switches to `UNION`, but it does not guarantee a single
  merged ancestor tree when the same node appears at different depths.
- For a final unique `user menu tree` / `用户菜单树`, first collect distinct IDs
  upward, then query only that ID set and build the final tree.

If the DTO or VO has a tree child relation, mark that navigation with
`@Navigate(ignoreAutoInclude = true)` when the auto-include path would conflict
with tree semantics.
