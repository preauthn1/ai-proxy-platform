# selectAutoInclude

`selectAutoInclude` is the main DTO/VO structured-return API. Think
"auto-select root + include-style relation assembly", not "single joined
select".

Do not use this as the main guide for:

- entity-return relation loading with `include(...)` / `include2(...)`
- `whereObject(...)` / `orderByObject(...)` metadata rules
- ordinary projection without DTO graph assembly

Read `include-structured-loading.md`, `dto-object-query.md`, or `query.md`
instead for those cases.

Core mindset:

- use root `.where(...)` to decide which root rows survive
- use nested `EXTRA_AUTO_INCLUDE_CONFIGURE` to shape or prune child relation
  rows inside the returned graph

If the requirement is "keep the main record, but filter items out of a child
list", prefer `EXTRA_AUTO_INCLUDE_CONFIGURE` over root relation predicates.

## Basic Pattern

```java
List<SysBankDTO> list = easyEntityQuery.queryable(SysBank.class)
    .selectAutoInclude(SysBankDTO.class)
    .toList();
```

The DTO describes the returned object graph. It can include nested objects and lists when the shape has matching navigation metadata.

Execution shape:

- Root query selects DTO scalar fields plus hidden relation keys.
- Nested data is fetched by separate include-style relation queries.
- Deep graphs normally mean multiple SQL statements.
- Batching follows starter `relationGroupSize` default `512`.

Source/docs implication:

- `selectAutoInclude` is fundamentally a secondary-query + `IN` loading model
- do not describe it as "one big join query that happens to return a DTO tree"

That execution shape is the reason root filtering and child-list pruning are
not the same thing.

## Extra Select Pattern

Use extra select when the DTO needs additional fields beyond automatic relation mapping.

```java
List<PostDTO> list = easyEntityQuery.queryable(Post.class)
    .selectAutoInclude(PostDTO.class, post -> Select.of(
        post.user().name().as(PostDTO.Fields.userName)
    ))
    .toList();
```

`replace = false` appends to auto select. `replace = true` replaces it.

## Critical Constraint

Do not pass a database entity class as the result class:

```java
// Bad
query.selectAutoInclude(SysUser.class);

// Good
query.selectAutoInclude(SysUserDTO.class);
```

Source has `SelectAutoIncludeTableEnum.THROW/WARNING/IGNORE`; default is
`THROW`.

## DTO Navigation

For nested DTO fields, define relation metadata on DTO properties when the result shape cannot be inferred by simple property mapping.

```java
public class UserDTO {
    private String id;
    private String name;

    @Navigate(value = RelationTypeEnum.OneToMany)
    private List<BankCardDTO> bankCards;
}
```

Important distinction:

- For DTO/VO metadata used only by `selectAutoInclude`, the `@Navigate`
  annotation comment says defining `RelationTypeEnum` is the essential part and
  extra relation fields on non-entity objects may be ignored.
- If the DTO/VO proxy itself is navigated inside query DSL rather than only
  used as result metadata, verify `supportNonEntity = true` behavior in
  `entity-modeling-navigate.md`; otherwise runtime may throw
  `entityRelationToImplicitProvider is null...`.

If names or paths do not line up, use `entity-modeling-navigate.md`.

### Entity-side conditional navigation with `@IncludeOnProperty`

Current `3.2.13` source honors entity-side `@IncludeOnProperty` when
`selectAutoInclude(...)` traverses relation metadata.

Meaning:

- the root DTO row can still be returned
- a child relation path is only auto-included for roots whose backing entity
  property values satisfy the annotation condition
- non-matching roots do not cause that relation query branch to populate

This is different from nested `EXTRA_AUTO_INCLUDE_CONFIGURE.where(...)`:

- `@IncludeOnProperty` decides whether a navigation path participates at all
  for a given root row
- nested `EXTRA_AUTO_INCLUDE_CONFIGURE.where(...)` filters rows inside the
  child query after that path is already active

If the requirement is "this relation only exists for version/type X", prefer
entity-side `@IncludeOnProperty`.

If the requirement is "the relation exists, but prune child rows by business
condition or request parameter", prefer nested
`EXTRA_AUTO_INCLUDE_CONFIGURE.where(...)`.

## Flattened Paths with NavigateFlat

`@NavigateFlat` lets DTOs pull fields through relation paths, including to-many paths.

```java
private static final MappingPath MENU_IDS_PATH =
    SysUserProxy.TABLE.roles().flatElement().menus().flatElement().id();

@NavigateFlat(pathAlias = "MENU_IDS_PATH")
private List<String> menuIds;
```

Source constraints:

- `@NavigateFlat` is for non-table DTO/VO use.
- Target should be a collection unless the final value is a basic type or
  entity.
- Field type must match the entity-path value.
- `prefix = true` turns the alias into a reusable path prefix.

## Extra Auto Include Configure

Source interface:

```java
EntityExtraAutoIncludeConfigure<TProxy, TEntity>
    .configure(queryable -> { ... })
    .where(proxy -> { ... })
    .select(proxy -> Select.of(...))
    .ignoreNavigateConfigure();
```

This hook is the preferred way to attach query behavior to a DTO/VO graph
shape. It can apply on:

- the root DTO/VO class
- nested DTO/VO classes

`@NavigateFlat` properties cannot have extra filters, but `@NavigateFlat`
objects can.

Pattern seen in docs/tests uses static config on generated proxy table:

```java
private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
    PostProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
        .where(post -> post.status().eq(1))
        .select(post -> Select.of(post.title().as("title")));
```

Additional source-backed behavior:

- `.configure(queryable -> { ... })` can apply query-level behaviors such as
  `orderBy(...)` or `ALL_SUB_QUERY_GROUP_JOIN`.
- `.ignoreNavigateConfigure()` ignores relation settings inherited from
  `@Navigate`.

Use this when nested DTO relation includes need custom filtering, custom sort,
`subQueryToGroupJoin`, or extra selected values.

### Root DTO vs nested DTO extra configure

Current source applies `EXTRA_AUTO_INCLUDE_CONFIGURE` in two places:

1. on the root result DTO class before recursive include building
2. on each nested included DTO relation query through `EasyNavigateUtil`

Practical meaning:

- root DTO `EXTRA_AUTO_INCLUDE_CONFIGURE` can shape the root query itself
- nested DTO `EXTRA_AUTO_INCLUDE_CONFIGURE` shapes the include query for that
  child relation

Root DTO example pattern:

```java
private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
    SysUserProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
        .configure(query -> query.subQueryToGroupJoin(u -> u.bankCards()))
        .select(u -> Select.of(
            u.bankCards().count().as("cardCount")
        ));
```

Nested DTO example pattern:

```java
private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
    SysBankCardProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
        .where(card -> card.type().eq("储蓄卡"))
        .select(card -> Select.of(
            card.bank().createTime().format("yyyy-MM-dd").as("bankNameOr123")
        ));
```

### Why this is the preferred answer for reusable child filtering

If the child filtering/projection rule is part of the DTO contract itself,
`EXTRA_AUTO_INCLUDE_CONFIGURE` is usually better than scattering ad hoc
`include(...)` adapters around service methods because:

- the DTO carries its own child shaping rule
- repeated queries reuse the same semantics
- the root row / child list boundary stays explicit

Prefer explicit `include(...)` only when the query instance needs a one-off
loading rule that should not become part of the DTO contract.

### Root filter vs child-list prune

This is the most important `selectAutoInclude` boundary to state precisely.

Root filter:

```java
easyEntityQuery.queryable(SysMenu.class)
    .where(menu -> {
        menu.sysMenuActionList().any(action -> {
            action.sysAction().name().contains(req.getActionName());
        });
    })
    .selectAutoInclude(SysMenuFlatResp.class)
    .toList();
```

Meaning:

- keep only roots that have at least one matching child
- it does **not** prune non-matching child rows from the returned child list

If the real requirement is:

- keep the root row
- keep other child rows
- only remove child rows that fail the child condition

then the child query itself must be filtered. In `selectAutoInclude`, the
recommended place is nested `EXTRA_AUTO_INCLUDE_CONFIGURE`.

### Child-list pruning pattern

For a nested DTO class corresponding to `sysMenuActionList`:

```java
@Data
public static class InternalMenuActions {
    private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
        SysMenuActionProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
            .where(ma -> {
                ma.sysAction().name().isNotNull();
            });

    private String id;
    private String menuId;

    @Navigate(value = RelationTypeEnum.ManyToOne)
    private InternalAction sysAction;
}
```

That filters the include query for the child list itself, so:

- matching root menus stay
- the `sysMenuActionList` collection is pruned
- rows with `sysAction.name == null` do not appear in the child list

This is usually the right answer when the user says:

- "不要把整个主记录过滤掉"
- "只是子集合里不要这个元素"
- "这个过滤规则本来就是这个 DTO 子集合的组成语义"

### Dynamic request-aware child filtering

When child pruning depends on request values, pass arguments from the root
query and consume them inside nested extra config:

```java
List<SysMenuFlatResp> rows = easyEntityQuery.queryable(SysMenu.class)
    .configure(o -> o.setConfigureArgument(req))
    .selectAutoInclude(SysMenuFlatResp.class)
    .toList();
```

Nested DTO:

```java
private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
    SysMenuActionProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
        .where(ma -> {
            ma.sysAction().name().isNotNull();

            ConfigureArgument argument =
                ma.getEntitySQLContext().getEntityExpressionBuilder()
                    .getExpressionContext().getConfigureArgument();
            MenuReq req = argument.getTypeArg();
            if (req.getActionName() != null) {
                ma.sysAction().name().contains(req.getActionName());
            }
        });
```

### When to still use root `.where(...any(...))`

Use root relation predicates when the requirement is genuinely about root-row
eligibility, for example:

- only menus that have at least one matching action should appear at all

If both semantics are needed, combine them:

- root `.where(...any(...))` for root eligibility
- nested `EXTRA_AUTO_INCLUDE_CONFIGURE.where(...)` for child-list pruning

### `ignoreNavigateConfigure()` when DTO-local rules should win

`ignoreNavigateConfigure()` is worth calling out explicitly because current
source short-circuits inherited navigate config when it is set.

Use it when the DTO-local extra config should fully own the child query, rather
than combining with entity/result-side `@Navigate` settings such as:

- `orderByProps`
- `limit`
- `offset`

Example intent:

```java
private static final ExtraAutoIncludeConfigure EXTRA_AUTO_INCLUDE_CONFIGURE =
    MySignUpProxy.TABLE.EXTRA_AUTO_INCLUDE_CONFIGURE()
        .where(x -> x.status().eq(1))
        .ignoreNavigateConfigure();
```

That says:

- still use `selectAutoInclude`
- but do not inherit the normal navigate ordering/limit shaping for this DTO
  node

## Tree CTE Interaction

When using tree CTE plus `selectAutoInclude`, source comments say the configured tree navigation is ignored during auto include to avoid querying children as a normal one-to-many relation.

```java
List<MyCommentDTO> tree = easyEntityQuery.queryable(Comment.class)
    .asTreeCTE()
    .selectAutoInclude(MyCommentDTO.class)
    .toList();
```

Use `@Navigate(ignoreAutoInclude = true)` on tree child relation if the DTO shape risks being treated as ordinary include.

## Include Override Rule

If explicit `include(...)` is already written, it wins over auto include on the
same path.

## Practical Checklist

Before writing `selectAutoInclude`:

- Result class is a DTO/VO, not a database entity.
- Entity and DTO have matching column/property mapping or explicit `@Navigate`/`@NavigateFlat`.
- To-many lists have enough key metadata for association.
- Extra nested filters or nested extra projections use
  `EXTRA_AUTO_INCLUDE_CONFIGURE`, not ad hoc post-processing.
- Tree child relation is ignored appropriately when using `asTreeCTE`.
- Expect multiple SQL statements for deep graphs; this is include-style
  structured loading, not one joined mega-select.
