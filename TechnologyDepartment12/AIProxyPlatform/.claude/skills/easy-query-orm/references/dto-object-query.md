# DTO Object Query

Use this reference for request-object driven filtering and sorting with
`whereObject(...)`, `orderByObject(...)`, and `@EasyWhereCondition`.

Do not use this file as the main guide for:

- DTO result graph mapping: read `select-auto-include.md`
- `sql-search` / `EasySearch` / `@EasyCond`: read `easy-search.md`
- ordinary non-form dynamic query logic: read `query.md`

This is the query/search-form branch, not the default for all dynamic query
tasks. For the common combined shape `search/page form + stable sort + DTO
graph return`, read `search-form-page.md` first.

## Core APIs

Source signatures:

```java
default ClientQueryable<T1> whereObject(Object object)
ClientQueryable<T1> whereObject(boolean condition, Object object)

default ClientQueryable<T1> orderByObject(ObjectSort configuration)
ClientQueryable<T1> orderByObject(boolean condition, ObjectSort objectSort)
```

Standard pattern:

```java
List<SysUser> users = easyEntityQuery.queryable(SysUser.class)
    .whereObject(request)
    .orderByObject(sort)
    .toList();
```

This is usually the preferred approach for frontend/admin query forms with many
optional filters.

Typical examples:

- user management search
- admin list page filters
- order list / customer list / bank-card list query forms
- controller/service `search` or `page` endpoints with many optional request fields

Use it when the request DTO is really a query form and many conditions are
optional. Otherwise prefer `query.md`.

It can be combined with DSL in the same query chain. This is often the best
shape for real business code:

```java
queryable
    .whereObject(queryForm)
    .where(queryForm.getRoleId() != null, user -> user.roles().any(role -> role.id().eq(queryForm.getRoleId())))
    .orderByObject(queryForm)
    .orderBy(user -> { user.createdTime().desc(); user.id().desc(); });
```

Use `whereObject(...)` for the broad form conditions, then add explicit DSL
only for the awkward relation/computed pieces.

## Source-Truth Note

Current source `EasyWhereCondition` fields are:

```java
@EasyWhereCondition(
    tableIndex = 0,
    allowEmptyStrings = false,
    propName = "",
    propNames = {},
    tablesIndex = {},
    mode = EasyWhereCondition.Mode.SINGLE,
    type = EasyWhereCondition.Condition.DEFAULT
)
```

If some older docs mention a `strict` attribute on `@EasyWhereCondition`,
prefer the project source first. The current source signature shown above does
not expose `strict`.

## Default Condition Resolution

Source behavior from `DefaultWhereConditionProvider`:

- `type = DEFAULT` on `String` fields -> `LIKE` or `CONTAINS`, depending on
  starter/global `defaultCondition`
- `type = DEFAULT` on non-`String` fields -> `EQUAL`

That means `DEFAULT` is not one fixed operator.

Practical rule:

- do not blindly document every untyped field as `LIKE`
- check whether the field is `String`
- if the project config sets `defaultCondition = CONTAINS`, `%` and `_` are
  treated as ordinary characters instead of wildcard input

## `@EasyWhereCondition` Condition Matrix

High-value condition values:

```text
DEFAULT, EQUAL, NOT_EQUAL,
GREATER_THAN, GREATER_THAN_EQUAL,
LESS_THAN, LESS_THAN_EQUAL,
LIKE, LIKE_MATCH_LEFT, LIKE_MATCH_RIGHT,
IN, NOT_IN,
RANGE_LEFT_OPEN, RANGE_LEFT_CLOSED,
RANGE_RIGHT_OPEN, RANGE_RIGHT_CLOSED,
COLLECTION_EQUAL_OR,
RANGE_OPEN, RANGE_CLOSED, RANGE_CLOSED_OPEN, RANGE_OPEN_CLOSED,
CONTAINS, STARTS_WITH, ENDS_WITH
```

Interpretation:

- `LIKE` / `LIKE_MATCH_LEFT` / `LIKE_MATCH_RIGHT` use normal SQL like semantics
- `CONTAINS` / `STARTS_WITH` / `ENDS_WITH` keep the same match shape, but do
  not treat `%` and `_` as caller-provided wildcard input
- `RANGE_LEFT_*` / `RANGE_RIGHT_*` are one-sided range expressions
- `RANGE_*` pair conditions consume a left/right pair from an array or
  collection
- `COLLECTION_EQUAL_OR` means `(a = ? or a = ? or a = ?)`; it is not the same
  as `IN`

## Input Shape Semantics

`whereObject(...)` skips null values.

For `String` values:

- blank strings are skipped by default
- set `allowEmptyStrings = true` if an empty-string filter should still enter
  the predicate

For `IN`, `NOT_IN`, and `COLLECTION_EQUAL_OR`:

- arrays are supported
- collections are supported
- empty arrays/collections are ignored

For `RANGE_OPEN`, `RANGE_CLOSED`, `RANGE_CLOSED_OPEN`, `RANGE_OPEN_CLOSED`:

- arrays are supported
- collections are supported
- the executor uses the first two values as the pair
- if only one value is present, it degrades to a one-sided compare using the
  left-side comparator

Examples:

```java
@EasyWhereCondition(type = EasyWhereCondition.Condition.RANGE_CLOSED, propName = "publishTime")
private List<LocalDateTime> publishTimes;

@EasyWhereCondition(type = EasyWhereCondition.Condition.RANGE_OPEN_CLOSED, propName = "publishTime")
private LocalDateTime[] publishTimeOpenClosed;

@EasyWhereCondition(type = EasyWhereCondition.Condition.COLLECTION_EQUAL_OR, propName = "status")
private List<Integer> statusAny;
```

## Modes: `SINGLE` vs `MULTI_OR`

Modes:

- `SINGLE`: one request field maps to one property path
- `MULTI_OR`: one request field maps to multiple properties joined by OR

Examples:

```java
@EasyWhereCondition(mode = EasyWhereCondition.Mode.MULTI_OR, propNames = {"name", "phone"})
private String keyword;

@EasyWhereCondition(mode = EasyWhereCondition.Mode.MULTI_OR, propNames = {"title", "content"})
private String titleOrContent;
```

Source constraints from `DefaultWhereObjectQueryExecutor`:

- `propNames` must not be empty in `MULTI_OR`
- all `propNames` must have the same property-path depth
- `tablesIndex` can be shorter than `propNames`; missing entries default to `0`

So this is valid:

```java
@EasyWhereCondition(mode = EasyWhereCondition.Mode.MULTI_OR, propNames = {"title", "content"})
private String keyword;
```

But mixing path depths such as `{"name", "bankCards.code"}` in one
`MULTI_OR` field is not valid.

## Relation Path Semantics

Relation paths in `propName` are first-class and use the same relation metadata
as ordinary DSL.

Examples:

```java
@EasyWhereCondition(propName = "bankCards.code", type = EasyWhereCondition.Condition.STARTS_WITH)
private String bankCardCode;

@EasyWhereCondition(propName = "bankCards.bank.name", type = EasyWhereCondition.Condition.IN)
private List<String> bankCardBankNames;
```

Source behavior:

- to-one paths become implicit joins
- to-many paths become implicit subqueries
- repeated conditions under the same to-many path are merged on the same query
  path tree
- if the query behavior enables `ALL_SUB_QUERY_GROUP_JOIN`, or relation config
  forces group join, the to-many path can be rewritten to implicit group-join
  SQL instead of plain correlated subqueries

Practical rule:

- relation-path DTO filters are good when relation metadata is already stable
- if the relation part is hard to review or only one awkward condition exists,
  combine `whereObject(...)` with one explicit DSL predicate instead

## Explicit Join Table Targeting: `tableIndex` / `tablesIndex`

Use `tableIndex` or `tablesIndex` only for explicit join table positions.

```java
@EasyWhereCondition(tableIndex = 1, propName = "name")
private String companyName;
```

Interpretation:

- `tableIndex = 0` means the root table
- each explicit join adds the next index
- `tablesIndex` is the multi-property version for `MULTI_OR`

Important source rule:

- `tableIndex` does not drive implicit relation joins
- implicit relation resolution comes from the property path itself (`bankCards.code`, `company.name`, ...)

## Search-Form Filters With Manual Sort

For many admin/search page endpoints, filters are form-like but sorting is only
`sortBy + asc/desc`. In that shape:

- keep filters in `whereObject(...)`
- keep sort as explicit allowlisted `.orderBy(...)`
- append stable fallback order such as `createdTime desc`, `id desc`
- then call `selectAutoInclude(ResultDTO.class)` and `toPageResult(...)`

Do not force `orderByObject(...)` if only a few sortable fields exist; manual
`orderBy(...)` is often clearer.

## `orderByObject` and `ObjectSort`

`orderByObject(...)` expects an `ObjectSort` (`com.easy.query.core.api.dynamic.sort`)
configuration or a project helper that produces one.

Pattern:

```java
ObjectSort sort = request.toObjectSort();

List<SysUser> users = easyEntityQuery.queryable(SysUser.class)
    .whereObject(request)
    .orderByObject(sort)
    .toList();
```

Mismatched sort fields can fail fast.

## `ObjectSortBuilder`

Source methods from `com.easy.query.core.api.dynamic.sort.ObjectSortBuilder`:

```java
builder.orderBy(propertyName, asc);
builder.orderBy(propertyName, asc, tableIndex);
builder.orderBy(propertyName, asc, orderByMode);
builder.orderBy(propertyName, asc, orderByMode, tableIndex);
builder.allowed(propertyName);
builder.notAllowed(propertyName);
```

Use it when external sort input belongs to the same query-form workflow.

Nested to-one property paths are supported, for example:

```java
builder.orderBy("user.name", true);
```

## `anyColumn(...)`

Use `anyColumn(...)` for controlled runtime property-path access.

```java
query.where(card -> {
    card.anyColumn("code").nullOrDefault("123").eq("456");
});

query.orderBy(card -> {
    card.anyColumn("user.age").orderBy(false, OrderByModeEnum.NULLS_LAST);
});
```

Still validate property names through an allowlist.

`OrderByModeEnum` is `com.easy.query.core.func.def.enums.OrderByModeEnum`.

## Replace the Default Executor

`whereObject(...)` is executor-driven.

Relevant types:

- `WhereObjectQueryExecutor`
- `DefaultWhereObjectQueryExecutor`
- `WhereConditionProvider`
- `DefaultWhereConditionProvider`

That means a project can replace the framework behavior or even define its own
annotation model. Use source/project truth before claiming a behavior is fixed
by the public API.

## When Explicit DSL Is Clearer

For small filters, one-off service methods, or query shapes that do not really
look like a form object, ordinary DSL is usually the better default:

```java
easyEntityQuery.queryable(SysUser.class)
    .filterConfigure(NotNullOrEmptyValueFilter.DEFAULT)
    .where(user -> {
        user.name().contains(request.getName());
        user.age().ge(request.getMinAge());
        user.company().name().contains(request.getCompanyName());
    });
```

`NotNullOrEmptyValueFilter` is `com.easy.query.core.expression.builder.core`.

Or gate the whole clause:

```java
query.where(request.hasName(), user -> user.name().contains(request.getName()));
```

Use explicit DSL when:

- the request object abstraction makes the query harder to understand
- the condition depends on nontrivial branching or computed business rules
- only a few fields are involved and the DSL is shorter than the metadata

Do not swing too far the other way: if the task is obviously `build a search
endpoint with many optional filters`, prefer `whereObject(...)` first rather
than hand-writing a long chain of gated predicates. Outside that search-form
shape, stay with DSL.
