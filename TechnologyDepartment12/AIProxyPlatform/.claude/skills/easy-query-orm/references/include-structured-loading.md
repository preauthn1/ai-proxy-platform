# Include and Structured Loading

Use this reference when returning object graphs, loading relations after a query, or choosing between `include`, `include2`, `fillOne/fillMany`, and `selectAutoInclude`.

Do not use this as the main DTO/VO structured-return guide. For
`selectAutoInclude(ResultDTO.class)` as the primary answer shape, read
`select-auto-include.md` first.

## Decision Guide

- Use `selectAutoInclude(ResultDTO.class)` for structured DTO/VO result graphs. It is the strongest structured return path and can infer relation loading from DTO shape.
- Use `include(...)` when the root query returns database entity objects and the relation fields on those entities should be populated.
- Use `include2(...)` for complex nested include paths, especially when paths cross collection elements and need per-path filters.
- Use `fillOne/fillMany` for arbitrary programmatic nesting when include/DTO metadata is not a good fit. The returned objects must contain the relation keys needed to match records.
- Use `loadInclude(...)` when entities are already loaded and relation fields should be populated later.

Doc evidence: `easy-query-doc/src/include/README.md`, `easy-query-doc/src/include/fetcher.md`.

## include

Proxy include signatures support:

```java
query.include(root -> root.relation());
query.include(root -> root.relation(), relationQuery -> {
    relationQuery.where(r -> r.name().contains(name));
    relationQuery.orderBy(r -> r.createTime().desc());
    relationQuery.limit(5);
});
query.include(root -> root.relation(), 100);
query.include(root -> root.relation(), relationQuery -> {
    relationQuery.asTracking().disableLogicDelete();
}, 100);
```

`groupSize` controls how many relation ids are fetched per relation query group. Starter default `relationGroupSize` is `512`.

Important behavior:

- `include` runs independent relation queries, not one big joined result.
- If differential update/tracking matters, configure tracking on relation subqueries too, for example `asTracking()`.
- Relation subqueries can use `where`, `orderBy`, `limit`, `asTracking/asNoTracking`, and `disableLogicDelete`.
- `include` is for returning database object instances. For DTO object graphs, prefer `selectAutoInclude`.

### Entity-side conditional include with `@IncludeOnProperty`

Current `3.2.13` source adds entity-side conditional relation gating through
`@IncludeOnProperty` on a `@Navigate` field.

```java
@Navigate(value = RelationTypeEnum.OneToOne, selfProperty = {MyTaskProxy.Fields.id}, targetProperty = {MyTask1ExtProxy.Fields.taskId})
@IncludeOnProperty(name = "version", value = "1")
private MyTask1Ext myTask1Ext;
```

Effect:

- roots are still queried normally
- only roots whose `version` matches the condition participate in that
  relation-load path
- for non-matching roots, easy-query skips issuing that relation fetch for that
  row/path combination

Use this when the relation branch is structurally version/type-specific.

Do not use it as a substitute for:

- query-instance child filtering with `include(..., q -> q.where(...))`
- request-aware DTO child pruning with `EXTRA_AUTO_INCLUDE_CONFIGURE.where(...)`
- root-row eligibility filtering with root `.where(...)`

`matchNull = true` means the root property must be null or blank for the
relation path to participate.

## include2

`include2` is available in the proxy API and the docs describe it as the preferred replacement for complex nested include cases in `3.1.47+`.

Pattern:

```java
List<SchoolClass> rows = easyEntityQuery.queryable(SchoolClass.class)
    .include2((ctx, schoolClass) -> {
        ctx.query(schoolClass.schoolTeachers().flatElement().schoolClasses())
            .where(c -> c.name().contains("science"));
        ctx.query(schoolClass.schoolStudents())
            .where(s -> s.name().ne("test"))
            .orderBy(s -> s.createTime().asc())
            .limit(5);
        ctx.query(schoolClass.schoolStudents().flatElement().schoolClass());
    })
    .toList();
```

Rules:

- `ctx.query(...)` accepts a navigation path from the root proxy. The path may be a to-one object, a to-many collection, or a collection path traversed with `flatElement()`.
- Conditions after `ctx.query(path)` apply to the final relation query on that path.
- Prefer `include2` over old `includes(...)`; source comments on `EntityIncludesable1` point users to `EntityIncludeable1#include2(...)`.

Source evidence: `EntityIncludeable1`, `EntityIncludesable1`.

## loadInclude

Use `loadInclude` when the root objects were already loaded:

```java
List<User> users = easyEntityQuery.queryable(User.class)
    .where(u -> u.status().eq(UserStatus.ACTIVE))
    .toList();

easyEntityQuery.loadInclude(users, u -> u.bankCards());
```

This is useful in service flows where the root set is produced by another branch, then relation loading is conditional.

Entity-side `@IncludeOnProperty` still applies here because `loadInclude(...)`
evaluates the already-loaded root objects in memory before issuing relation
queries.

## fillOne/fillMany

Use `fillOne/fillMany` for arbitrary programmatic nesting when relation metadata
or DTO auto-include is not the right fit.

Use this when:

- Relation metadata is unavailable or intentionally not modeled.
- A query returns a custom DTO shape but still needs in-memory relation assembly.
- Relation selection is too custom for `include`.

Avoid it when `selectAutoInclude` or `include2` can express the same graph with
existing navigation metadata.

### Prefer the `FillContext` form in new examples

Current source still supports the older string overloads, but the
`FillContext` consumer form is the clearer and non-deprecated surface:

```java
List<Province> rows = easyEntityQuery.queryable(Province.class)
    .fillMany(() -> {
        return easyEntityQuery.queryable(City.class)
            .where(c -> c.code().eq("3306"));
    }, c -> {
        c.self_target("code", "provinceCode", false);
    }, (province, cities) -> {
        province.setCities(new ArrayList<>(cities));
    })
    .toList();
```

Matching rule in `self_target(...)`:

- first argument = `selfProp` on the current/root query object
- second argument = `targetProp` on the fill-query result object
- third argument = `consumeNull`

So in the example above:

- root/self side: `Province.code`
- fill/target side: `City.provinceCode`

For a to-one fill:

```java
List<City> rows = easyEntityQuery.queryable(City.class)
    .fillOne(() -> {
        return easyEntityQuery.queryable(Province.class);
    }, c -> {
        c.self_target("provinceCode", "code", false);
    }, (city, province) -> {
        city.setProvince(province);
    })
    .toList();
```

### String overload still exists, but the parameter order is different

Current source still accepts the older overloads:

```java
List<Province> rows = easyEntityQuery.queryable(Province.class)
    .fillMany(() -> {
        return easyEntityQuery.queryable(City.class)
            .where(c -> c.code().eq("3306"));
    }, "provinceCode", "code", (province, cities) -> {
        province.setCities(new ArrayList<>(cities));
    }, false)
    .toList();
```

Important direction rule:

- string overload order is `targetProperty`, then `selfProperty`
- `self_target(...)` order is `selfProp`, then `targetProp`

Do not swap them. This is the easiest way to write a compilable-looking example
that never actually matches rows.

### Practical rules

- Both sides must expose the matching key fields in the selected columns.
  Full-entity queries naturally satisfy this; custom DTO projections must retain
  the `selfProp` / `targetProp` fields.
- The fill subquery can still use normal DSL such as `where(...)` and
  `limit(...)`.
- When generated proxies are available, prefer field-name constants like
  `CityProxy.TABLE.provinceCode().getValue()` and
  `ProvinceProxy.TABLE.code().getValue()` over fragile raw string literals.
- Do not invent proxy-only `fillMany` / `fillOne` builders. Current public
  source-backed guidance is the `Query<T>` surface shown above.

### Source-backed examples

Upstream tests use all of these verified shapes:

- `fillMany(..., "provinceCode", "code", ..., false)`
- `fillMany(..., c -> c.self_target("code", "provinceCode", false), ...)`
- `fillOne(..., c -> c.self_target("provinceCode", "code", false), ...)`
- proxy field-name strings such as
  `CityProxy.TABLE.provinceCode().getValue()` and
  `ProvinceProxy.TABLE.code().getValue()`

## Relation Null Values

Relation include skips relation queries when relation key values are null or blank by default. Docs show `RelationNullValueValidator` can be replaced if application-specific placeholder values like `"-"` or `"/"` should also be treated as null relation keys.

## Troubleshooting

- If an included relation is not populated, check `@Navigate` direction and key fields first.
- If a to-one relation maps only one of several matching rows, verify relation type. Many rows pointing to the same target is usually `ManyToOne`, not `OneToOne`.
- If include results are not tracked, add `asTracking()` in the include adapter or enable default tracking.
- If DTO fields are missing, use `selectAutoInclude` or explicit `Select.of(...)`; `include` populates entity relation properties, not arbitrary DTO extra fields.
