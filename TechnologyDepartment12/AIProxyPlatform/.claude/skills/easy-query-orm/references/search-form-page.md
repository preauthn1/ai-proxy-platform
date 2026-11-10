# Search Form Page

Use this reference first for backend/admin page endpoints that combine:

- many optional search-form filters
- pageable list output
- sortable columns from a small allowlist
- DTO/VO graph return such as nested department/roles

Do not use this file as the main guide for:

- ordinary non-form dynamic query logic: read `query.md`
- generic `whereObject` metadata details: read `dto-object-query.md`
- complex DTO-side navigation modeling: read `entity-modeling-navigate.md`

## One-Line Pattern

- filters: `whereObject(query.toWhere())`
- relation-heavy extras: explicit `.where(condition, ...)`
- sorting: explicit allowlisted `.orderBy(...)` for small sort inputs
- DTO graph: `.selectAutoInclude(ResultDTO.class)`
- pagination: `.toPageResult(pageIndex, pageSize)` with stable fallback order

## Minimal Combined Shape

```java
EasyPageResult<UserListDTO> page = easyEntityQuery.queryable(User.class)
    .whereObject(query.toWhere())
    .where(query.getRoleId() != null,
        user -> user.roles().any(role -> role.id().eq(query.getRoleId())))
    .orderBy(user -> {
        String sortBy = normalizeSortBy(query.getSortBy());
        boolean asc = !Boolean.FALSE.equals(query.getSortAsc());

        if ("name".equals(sortBy)) {
            if (asc) {
                user.name().asc();
            } else {
                user.name().desc();
            }
        } else if ("status".equals(sortBy)) {
            if (asc) {
                user.status().asc();
            } else {
                user.status().desc();
            }
        } else if ("createdtime".equals(sortBy)) {
            if (asc) {
                user.createdTime().asc();
            } else {
                user.createdTime().desc();
            }
        }

        if (!"createdtime".equals(sortBy)) {
            user.createdTime().desc();
        }
        user.id().desc();
    })
    .selectAutoInclude(UserListDTO.class)
    .toPageResult(pageIndex, pageSize);
```

`EasyPageResult` is `com.easy.query.core.api.pagination.EasyPageResult`.

## Query Object Shape

Keep page/sort fields outside the `whereObject(...)` target. The object passed to
`whereObject(...)` should contain only real filter fields.

```java
@Data
public class UserListQuery {
    private Integer pageIndex = 1;
    private Integer pageSize = 20;
    private String sortBy;
    private Boolean sortAsc;

    private String name;
    private Integer status;
    private Long departmentId;
    private Long roleId;
    private LocalDateTime createdTimeBegin;
    private LocalDateTime createdTimeEnd;

    public Where toWhere() {
        Where where = new Where();
        where.setName(name);
        where.setStatus(status);
        where.setDepartmentId(departmentId);
        where.setCreatedTimeBegin(createdTimeBegin);
        where.setCreatedTimeEnd(createdTimeEnd);
        return where;
    }

    @Data
    public static class Where {
        @EasyWhereCondition(type = EasyWhereCondition.Condition.CONTAINS)
        private String name;

        @EasyWhereCondition(type = EasyWhereCondition.Condition.EQUAL)
        private Integer status;

        @EasyWhereCondition(type = EasyWhereCondition.Condition.EQUAL)
        private Long departmentId;

        @EasyWhereCondition(type = EasyWhereCondition.Condition.RANGE_LEFT_CLOSED, propName = "createdTime")
        private LocalDateTime createdTimeBegin;

        @EasyWhereCondition(type = EasyWhereCondition.Condition.RANGE_RIGHT_CLOSED, propName = "createdTime")
        private LocalDateTime createdTimeEnd;
    }
}
```

## Sort Choice

For search-form page endpoints, `orderByObject(...)` is optional.

Prefer explicit allowlisted `orderBy(...)` when:

- frontend only sends `sortBy + asc/desc`
- sortable fields are few
- stable fallback order must be obvious in one place

Use `orderByObject(...)` only when:

- the request already implements `ObjectSort`
- the project already has a helper that builds `ObjectSort`
- sort metadata itself is part of the established form model

Do not go hunting for `ObjectSortBuilder` examples just to avoid a short
`if/else` allowlist.

## DTO Graph Return

Use `selectAutoInclude(ResultDTO.class)` when the result is a DTO/VO graph:

```java
query.selectAutoInclude(UserListDTO.class);
```

This is the preferred API when nested fields such as `DepartmentDTO department`
and `List<RoleDTO> roles` should be inferred from the DTO shape.

Use DTO-side `@Navigate` / `@NavigateFlat` only when simple name/path inference
is not enough.

## Common Pitfalls

- Do not call `selectAutoInclude(User.class)` with a database entity return type.
- Do not put paging, sorting, or transport-only fields into the `whereObject(...)` target.
- Do not leave pagination order unstable; always keep a deterministic tie-breaker such as `id`.
- Do not scan the whole workspace for placeholder wrapper types when the user did not point to a real project.

If the project has its own `PageResult` wrapper, adapt from `EasyPageResult` at
the edge:

```java
return PageResult.of(pageIndex, pageSize, page.getTotal(), page.getData());
```

If no concrete wrapper is provided, answer with `EasyPageResult<DTO>` or note
that the wrapper line should be replaced with the project-local constructor/factory.
