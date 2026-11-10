# Interceptor

Use this for tenant filters, data-permission filters, audit/default field
filling, expression-update auto-set columns, or when the user needs to
selectively enable/disable interceptors per query/update/delete chain.

Do not confuse these with Spring AOP interceptors. This reference is about
`easy-query` runtime interceptors on ORM expressions.

## Core Types

Source-level interceptor interfaces:

- `Interceptor`
- `EntityInterceptor`
- `PredicateFilterInterceptor`
- `UpdateSetInterceptor`
- `UpdateEntityColumnInterceptor`
- `ProtectedInterceptor`

Typical business uses:

- insert/update audit fields such as `createTime`, `createBy`, `updateTime`, `updateBy`
- tenant isolation via automatic `where tenant_id = ...`
- data permission filtering for departments/leaders/self-only scopes
- expression-update auto-fill for `updateTime` / `updateBy`

## 1. Base `Interceptor`

Source interface:

```java
public interface Interceptor {
    default int order() { return 100; }
    default boolean enable() { return true; }
    String name();
    boolean apply(@NotNull Class<?> entityClass);
}
```

Meaning:

- `order()`: lower runs earlier
- `enable()`: whether the interceptor participates by default
- `name()`: unique identifier; duplicate registration throws
- `apply(entityClass)`: entity scope matcher

Registration source rule from `QueryConfiguration.applyInterceptor(...)`:

- `name()` must not be blank
- names must be unique in the global interceptor map

## 2. Registration

Plain Java / console:

```java
QueryConfiguration configuration = easyQuery.getRuntimeContext().getQueryConfiguration();
configuration.applyInterceptor(new MyEntityInterceptor());
configuration.applyInterceptor(new MyTenantInterceptor());
```

Spring Boot starter:

- `Interceptor` beans are collected and passed into `EasyQueryInitializeOption`
- starter then calls `configuration.applyInterceptor(...)`

So in a normal Spring Boot setup, a plain `@Component` interceptor is enough.

## 3. `EntityInterceptor`

Source interface:

```java
public interface EntityInterceptor extends Interceptor {
    void configureInsert(Class<?> entityClass, EntityInsertExpressionBuilder entityInsertExpressionBuilder, Object entity);
    void configureUpdate(Class<?> entityClass, EntityUpdateExpressionBuilder entityUpdateExpressionBuilder, Object entity);
}
```

Use this for entity-object mutation before insert/update.

Canonical case:

```java
@Component
public class DefaultEntityInterceptor implements EntityInterceptor {
    @Override
    public void configureInsert(Class<?> entityClass, EntityInsertExpressionBuilder builder, Object entity) {
        BaseEntity e = (BaseEntity) entity;
        if (e.getCreateTime() == null) {
            e.setCreateTime(LocalDateTime.now());
        }
        if (e.getCreateBy() == null) {
            e.setCreateBy(CurrentUserHelper.getUserId());
        }
        if (e.getUpdateTime() == null) {
            e.setUpdateTime(LocalDateTime.now());
        }
        if (e.getUpdateBy() == null) {
            e.setUpdateBy(CurrentUserHelper.getUserId());
        }
    }

    @Override
    public void configureUpdate(Class<?> entityClass, EntityUpdateExpressionBuilder builder, Object entity) {
        BaseEntity e = (BaseEntity) entity;
        e.setUpdateTime(LocalDateTime.now());
        e.setUpdateBy(CurrentUserHelper.getUserId());
    }

    @Override
    public String name() {
        return "DEFAULT_INTERCEPTOR";
    }

    @Override
    public boolean apply(Class<?> entityClass) {
        return BaseEntity.class.isAssignableFrom(entityClass);
    }
}
```

Use it when the incoming entity object itself should be mutated.

## 4. `PredicateFilterInterceptor`

Source interface:

```java
public interface PredicateFilterInterceptor extends Interceptor {
    void configure(Class<?> entityClass, LambdaEntityExpressionBuilder lambdaEntityExpressionBuilder, WherePredicate<Object> wherePredicate);
}
```

This is the core tenant/data-permission interceptor.

It applies to:

- query
- update (entity/expression)
- delete (entity/expression)

Tenant example:

```java
public class MyTenantInterceptor implements EntityInterceptor, PredicateFilterInterceptor, ProtectedInterceptor {
    @Override
    public String name() {
        return "TENANT_INTERCEPTOR";
    }

    @Override
    public boolean apply(Class<?> entityClass) {
        return BaseEntity.class.isAssignableFrom(entityClass);
    }

    @Override
    public void configure(Class<?> entityClass, LambdaEntityExpressionBuilder builder, WherePredicate<Object> wherePredicate) {
        if (CurrentUserHelper.getTenantId() != null) {
            wherePredicate.eq("tenantId", CurrentUserHelper.getTenantId());
        }
    }

    @Override
    public void configureInsert(Class<?> entityClass, EntityInsertExpressionBuilder builder, Object entity) {
        BaseEntity e = (BaseEntity) entity;
        if (e.getTenantId() == null) {
            e.setTenantId(CurrentUserHelper.getTenantId());
        }
    }
}
```

Prefer this over manually repeating `.where(x -> x.tenantId().eq(...))` in
all services.

## 5. `UpdateSetInterceptor`

Source interface:

```java
public interface UpdateSetInterceptor extends Interceptor {
    void configure(Class<?> entityClass, EntityUpdateExpressionBuilder entityUpdateExpressionBuilder, ColumnSetter<Object> columnSetter);
}
```

Use this for expression updates such as:

```java
easyEntityQuery.updatable(User.class)
    .setColumns(u -> u.name().set("new-name"))
    .where(u -> u.id().eq(id))
    .executeRows();
```

`EntityInterceptor.configureUpdate(...)` does not solve this because there is no
entity object to mutate. `UpdateSetInterceptor` lets you append `set` columns
at SQL-builder time.

Typical pattern:

```java
@Override
public void configure(Class<?> entityClass, EntityUpdateExpressionBuilder builder, ColumnSetter<Object> columnSetter) {
    EntitySegmentComparer updateTime = new EntitySegmentComparer(entityClass, "updateTime");
    EntitySegmentComparer updateBy = new EntitySegmentComparer(entityClass, "updateBy");
    columnSetter.getSQLBuilderSegment().forEach(k -> {
        updateTime.visit(k);
        updateBy.visit(k);
        return updateTime.isInSegment() && updateBy.isInSegment();
    });
    if (!updateBy.isInSegment()) {
        columnSetter.set("updateBy", CurrentUserHelper.getUserId());
    }
    if (!updateTime.isInSegment()) {
        columnSetter.set("updateTime", LocalDateTime.now());
    }
}
```

## 6. `UpdateEntityColumnInterceptor`

Source interface:

```java
public interface UpdateEntityColumnInterceptor extends Interceptor {
    void configure(@NotNull Class<?> entityClass,
                   @NotNull EntityUpdateExpressionBuilder entityUpdateExpressionBuilder,
                   @NotNull ColumnOnlySelector<Object> columnSelector,
                   @NotNull Object entity);
}
```

This is for entity-object updates where the framework is selecting which
columns to update. Use it when the entity update path needs to force additional
columns such as `updateTime` or `updateBy` into the final update column set.

Use this when the problem is “the object has the right values, but those fields
still might not be included in the generated update columns.”

## 7. `ProtectedInterceptor`

Marker interface:

```java
public interface ProtectedInterceptor extends Interceptor {
}
```

Source behavior from `EasyExpressionContext.getInterceptorFilter()`:

- default expression behavior includes `USE_INTERCEPTOR`
- `noInterceptor()` removes normal default interceptors from the expression
- `ProtectedInterceptor` still stays eligible under global `noInterceptor()`
- only `noInterceptor(name)` can remove a protected interceptor precisely

This is the correct choice for tenant interceptors that must not be dropped by
accident.

## 8. `useInterceptor(...)` / `noInterceptor(...)` Semantics

Expression/query/update/delete chains expose:

- `noInterceptor()`
- `useInterceptor(name)`
- `noInterceptor(name)`
- `useInterceptor()`

Source-backed rules:

- default behavior already has `USE_INTERCEPTOR`
- `useInterceptor(name)` adds a named interceptor to the allow-set, but does
  not mean “only this one”
- `noInterceptor()` clears manual sets and disables normal default interceptors
  for the current expression
- `useInterceptor()` clears manual sets and re-enables default interceptors
- `noInterceptor(name)` removes one named interceptor even when the default set
  is still enabled

So:

- use only one specific interceptor:
  `noInterceptor().useInterceptor("NAME")`
- disable a tenant interceptor by exact name (only if not intentionally
  protected, or if you truly need to override protection):
  `noInterceptor("TENANT_INTERCEPTOR")`
- restore normal behavior after a previous `noInterceptor()`:
  `useInterceptor()`

## 9. How the Runtime Applies Them

Source confirms different pipelines call different interceptor families:

- query/delete/update predicates -> `PredicateFilterInterceptor`
- entity insert/update object mutation -> `EntityInterceptor`
- expression update `SET` columns -> `UpdateSetInterceptor`
- entity update column selection -> `UpdateEntityColumnInterceptor`

Do not force one interceptor type to solve another type's lifecycle.

## 10. Decision Rules

Choose `EntityInterceptor` when:

- you need to mutate entity objects before insert/update
- create/update audit fields should be written onto the object itself

Choose `PredicateFilterInterceptor` when:

- every query/update/delete should carry tenant or permission constraints
- a cross-cutting `where` predicate should be enforced centrally

Choose `UpdateSetInterceptor` when:

- expression updates need automatic `set` additions
- there is no entity object to mutate

Choose `UpdateEntityColumnInterceptor` when:

- entity update mode may omit columns you still want to update
- you need to ensure selected columns include audit fields

Add `ProtectedInterceptor` when:

- accidental global `noInterceptor()` must not disable that interceptor
- the rule is security/tenant critical

## 11. Common Mistakes

- Using only `EntityInterceptor` and expecting expression updates to auto-fill
  `updateTime`/`updateBy`.
- Repeating tenant/data-permission `.where(...)` logic in every repository
  instead of a `PredicateFilterInterceptor`.
- Saying `useInterceptor(name)` means “only use this one”. It does not.
- Forgetting `name()` uniqueness and registering duplicates.
- Using `noInterceptor()` on a sensitive tenant setup and forgetting that
  `ProtectedInterceptor` is the safer default for those rules.
- Building one mega-interceptor for unrelated concerns when separate named
  interceptors would be easier to reason about and selectively disable.

## Read next

- base entity and audit-field design: `easy-query-doc/src/practice/configuration/entity.md`
- broader starter/runtime registration defaults: `configuration-starter.md`
- write/update behavior that interceptors augment: `write.md`
