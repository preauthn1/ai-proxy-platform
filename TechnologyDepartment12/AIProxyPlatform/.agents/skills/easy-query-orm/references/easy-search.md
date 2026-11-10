# EasySearch Extension

Use this reference only when the project has the `sql-search` extension and
request parameters are expressed through `EasySearch` (`com.easy.query.search`)
/ `@EasyCond` (`com.easy.query.search.annotation`).

This is an extension layer over the normal DTO object query APIs. It is still a
search/query-form oriented model. For ordinary `whereObject(...)`,
`orderByObject(...)`, `@EasyWhereCondition`, and `ObjectSortBuilder`, read
`dto-object-query.md`.

## What EasySearch Changes

`SearchInjectConfiguration` registers EasySearch-specific executors:

- `WhereObjectQueryExecutor -> EasySearchWhereObjectQueryExecutor`
- `ObjectSortQueryExecutor -> EasySearchObjectSortQueryExecutor`
- `EasySearchMetaDataManager`
- `EasySearchQueryExecutor`
- `EasySearchConfigurationProvider`

That means an `EasySearch` instance is passed into the standard APIs:

```java
EasySearch search = EasySearch.of(UserSearch.class)
    .param("name", "tom")
    .param("age-ge", 18)
    .addDefaultSort("createTime:desc");

List<User> rows = easyEntityQuery.queryable(User.class)
    .whereObject(search)
    .orderByObject(search)
    .toList();
```

The call surface looks like ordinary DTO object query, but the metadata model
and parameter parsing rules are different.

## `@EasyCond`

`@EasyCond` (`com.easy.query.search.annotation`) can be declared on fields or at
type level.

Examples:

```java
@EasyCond(name = "kw", property = "name", cond = Like.class)
private String keyword;

@EasyCond(property = "age", condOnly = {GreaterEquals.class, LessEquals.class})
private Integer age;

@EasyCond(property = "createTime", sort = EasySortType.Desc)
private LocalDateTime createTime;
```

Source fields:

```java
boolean value() default true;
String name() default "";
String property() default "";
String tableAlias() default "";
Class<?> table() default Object.class;
int tableIndex() default -1;
Class<? extends Op> cond() default Op.class;
Class<? extends Op>[] condOnly() default {};
EasySortType sort() default EasySortType.Asc;
EasySortType[] sortOnly() default {EasySortType.Asc, EasySortType.Desc};
```

Type-level note from source comment:

- on a type, only `value`, `table`, `tableAlias`, and `tableIndex` are used as
  defaults
- if `table`, `tableAlias`, and `tableIndex` are all unset, EasySearch falls
  back to its dynamic table matcher

## Source Default Inference

Source comment on `@EasyCond.cond()` is important:

- `String` -> Like
- date/time -> RangeClosed
- `List` -> In
- other scalar types -> Equals

That means `cond = Op.class` is not one fixed operator. It is type-driven.

Use `condOnly` when the request is allowed to switch operators but must stay
inside a safe subset.

## Parameter Format

Default parser options:

```text
groupSplitter = "|"
classSplitter = "."
paramSplitter = "-"
orderSplitter = ":"
sortParam = "sort"
indexEnabled = true
strict = true
defaultEnabled = false
whereEnabled = true
orderEnabled = true
```

Exact symbol cues from current source:

- `EasySearchOptionBuilder.setSortParam(...)` changes the incoming sort request
  parameter key from the default `sort`
- `SearchInjectConfiguration` wires
  `ObjectSortQueryExecutor -> EasySearchObjectSortQueryExecutor`, so
  `orderByObject(search)` keeps using the standard dynamic-sort extension point
  with an EasySearch-specific implementation

Condition parameter format:

```text
[groupName|][className.]paramName[-index]
```

Sort parameter format:

```text
[className.]paramName[:asc|desc|ascend|descend]
```

Example:

```java
EasySearch.of(UserSearch.class)
    .table("role", RoleSearch.class)
    .param("name", "tom")
    .param("age-ge", 18)
    .param("age-le", 60)
    .param("role.name-lk", "admin")
    .param("sort", "createTime:desc");
```

For joined tables:

```java
EasySearch.of(UserSearch.class)
    .tableIndex("role", RoleSearch.class, 1)
    .param("role.name-lk", "admin");
```

## Operator Suffixes

Registered operator names include:

```text
eq, ne
gt, ge, lt, le
in, notIn
lk, notLike
likeMatchLeft, notLikeMatchLeft
likeMatchRight, notLikeMatchRight
rc, ro, rco, roc
notRangeClosed, notRangeOpen, notRangeClosedOpen, notRangeOpenClosed
isNotNull
```

The actual parameter suffix is `paramSplitter + opName`, for example `age-ge`.

## Defaults and Safety

- strict mode is `true` by default
- excluded default params include `token`, `page`, `current`, `pageSize`
- unannotated fields are disabled by default because `defaultEnabled = false`
- `condOnly` restricts legal operators
- `sortOnly` restricts legal sort directions
- `whereEnabled` / `orderEnabled` can disable one side independently
- `indexEnabled` controls indexed parameter names such as `name-1`

`EasySortType.of(...)` recognizes `asc`, `desc`, `ascend`, and `descend`.

## When Not to Use

Do not use EasySearch when:

- the project has not installed/configured `sql-search`
- the query is small and a normal DTO or explicit DSL is clearer
- the request cannot be safely represented as parameter-name/operator pairs

If the project only needs normal request DTO filtering/sorting, prefer
`dto-object-query.md`.
