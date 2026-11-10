# Logic Delete

Use this for `@LogicDelete`, soft-delete semantics, query-time delete filters,
physical-delete escape hatches, and custom logic-delete strategies.

This is a separate concern from optimistic locking and separate from
interceptors, although they often appear together in real projects.

## 1. What `@LogicDelete` Changes

At runtime, a field marked with `@LogicDelete` turns normal delete behavior into
soft delete semantics:

- `deletable(...).executeRows()` becomes an `UPDATE ... SET deleted-flag/value`
- normal queries automatically append the not-deleted predicate
- update/delete queries also inherit logic-delete filters unless disabled

Source annotation:

```java
public @interface LogicDelete {
    LogicDeleteStrategyEnum strategy() default LogicDeleteStrategyEnum.BOOLEAN;
    String strategyName() default "";
}
```

Source note from annotation comment:

- the same entity must not have multiple `@LogicDelete` properties
- the logic-delete property is not included in normal update `SET` columns
  unless explicitly handled

## 2. Built-in Strategies

Built-in strategy enum values:

- `BOOLEAN`
- `DELETE_LONG_TIMESTAMP`
- `LOCAL_DATE_TIME`
- `LOCAL_DATE`
- `CUSTOM`

Built-in semantics from source implementations:

- `BOOLEAN`: not deleted = `false`, deleted = `true`
- `DELETE_LONG_TIMESTAMP`: not deleted = `0`, deleted = current timestamp
- `LOCAL_DATE_TIME`: not deleted = `null`, deleted = `LocalDateTime.now()`
- `LOCAL_DATE`: not deleted = `null`, deleted = `LocalDate.now()`

Typical entity:

```java
@Table("company")
@EntityProxy
public class Company implements ProxyEntityAvailable<Company, CompanyProxy> {
    @Column(primaryKey = true)
    private String id;

    private String name;

    @LogicDelete(strategy = LogicDeleteStrategyEnum.BOOLEAN)
    private Boolean deleted;
}
```

If the delete marker column has a DB default value, insert code may omit it and
let the database fill the initial not-deleted value.

## 3. Query-Time Control

Most query chains support the generic logic-delete toggles:

- `disableLogicDelete()`
- `enableLogicDelete()`
- `useLogicDelete(boolean)`

Source interface:

```java
public interface LogicDeletable<TChain> {
    default TChain disableLogicDelete() { return useLogicDelete(false); }
    default TChain enableLogicDelete() { return useLogicDelete(true); }
    TChain useLogicDelete(boolean enable);
}
```

Meaning:

- `disableLogicDelete()` removes automatic not-deleted filtering for the current
  expression
- `enableLogicDelete()` restores it explicitly
- `useLogicDelete(false)` is the underlying switch

Examples:

```java
List<Company> allRows = easyEntityQuery.queryable(Company.class)
    .disableLogicDelete()
    .toList();

List<Company> liveRows = easyEntityQuery.queryable(Company.class)
    .useLogicDelete(true)
    .toList();
```

This also appears on update/delete chains, not only query chains.

## 4. Partial Disable on Joined Tables: `tableLogicDelete(...)`

When only one part of a join graph should ignore logic-delete filtering, use
`tableLogicDelete(...)` rather than disabling it for the whole query.

Source interface:

```java
public interface TableLogicDeletable<TChain> {
    TChain tableLogicDelete(Supplier<Boolean> tableLogicDel);
}
```

Source comment says it controls the **previous/nearest table** in the current
expression stage. In practice, use it carefully right after the join/table scope
that needs changing.

Pattern from docs:

```java
List<UserVo> userVos = easyEntityQuery.queryable(User.class)
    .leftJoin(Company.class, (u, c) -> u.companyId().eq(c.id()))
    .tableLogicDelete(() -> false)
    .select(UserVo.class, (u, c) -> Select.of(
        c.name().as(UserVo::getCompanyName)
    ))
    .toList();
```

Use this when:

- the root table should still respect soft delete
- one joined table should expose deleted rows

## 5. Relation Paths and Includes

Logic-delete control also shows up inside relation configuration.

Examples from docs/tests:

```java
query.include(o -> o.schoolClass(), q -> q.asNoTracking().disableLogicDelete());

user.roles().configure(q -> q.disableLogicDelete()).any(role -> {
    role.name().eq("admin");
});
```

Use relation-local `disableLogicDelete()` when only one relation path should
ignore soft-delete filtering.

Prefer this over globally disabling logic delete for the whole outer query.

## 6. Physical Delete Escape Hatch

To truly execute `DELETE`, you need both:

- `disableLogicDelete()`
- `allowDeleteStatement(true)`

```java
easyEntityQuery.deletable(Company.class)
    .where(c -> c.id().eq(id))
    .disableLogicDelete()
    .allowDeleteStatement(true)
    .executeRows();
```

This is not a convenience toggle. It removes two independent protections:

- soft-delete rewrite
- delete-statement guard

Do not recommend this as a default cleanup path.

## 7. Restore / Operate on Deleted Rows

Once you disable logic delete for an update/query, you can operate on deleted
rows explicitly.

Example pattern from docs:

```java
List<Company> companyList = easyEntityQuery.queryable(Company.class)
    .disableLogicDelete()
    .toList();

for (Company company : companyList) {
    company.setDeleted(false);
}

long rows = easyEntityQuery.updatable(companyList)
    .disableLogicDelete()
    .executeRows();
```

Use this only when the business requirement really is recovery or
backfill/repair against deleted rows.

## 8. Custom Field-Bound Strategy: `AbstractLogicDeleteStrategy`

When one field needs custom deleted value + custom not-deleted predicate,
implement `AbstractLogicDeleteStrategy`.

```java
public class CustomLogicDelStrategy extends AbstractLogicDeleteStrategy {
    @Override
    protected SQLActionExpression1<WherePredicate<Object>> getPredicateFilterExpression(LogicDeleteBuilder builder, String propertyName) {
        return o -> o.isNull(propertyName);
    }

    @Override
    protected SQLActionExpression1<ColumnSetter<Object>> getDeletedSQLExpression(LogicDeleteBuilder builder, String propertyName) {
        return o -> o.set(propertyName, LocalDateTime.now())
                     .set("deletedUserId", 1);
    }

    @Override
    public String getStrategy() {
        return "CustomLogicDelStrategy";
    }
}
```

Bind it on the field with `CUSTOM` + `strategyName`:

```java
@LogicDelete(strategy = LogicDeleteStrategyEnum.CUSTOM, strategyName = "CustomLogicDelStrategy")
private LocalDateTime deletedTime;
```

Registration:

```java
QueryConfiguration queryConfiguration = easyQuery.getRuntimeContext().getQueryConfiguration();
queryConfiguration.applyLogicDeleteStrategy(new CustomLogicDelStrategy());
```

## 9. Configuration-Bound Strategy: `AbstractConfigurationLogicDeleteStrategy`

Source also has `AbstractConfigurationLogicDeleteStrategy`, which binds by
`apply(entityClass)` rather than by a field-level `strategyName`.

```java
public class ConfigurationLogicDelete extends AbstractConfigurationLogicDeleteStrategy {
    @Override
    protected SQLActionExpression1<WherePredicate<Object>> getPredicateFilterExpression() {
        return o -> o.eq("deleted", false);
    }

    @Override
    protected SQLActionExpression1<ColumnSetter<Object>> getDeletedSQLExpression() {
        return o -> o.set("deleted", true);
    }

    @Override
    public String getStrategy() {
        return ConfigurationLogicDelete.class.getName();
    }

    @Override
    public boolean apply(@NotNull Class<?> entityClass) {
        return MyConfigLogicDelete.class.equals(entityClass);
    }
}
```

Use this when the logic-delete rule is really an entity-wide configuration
policy rather than a field annotation choice.

## 10. Lambda Capture Gotcha in Custom Strategies

This is an important source-level warning.

Wrong:

```java
LocalDateTime now = LocalDateTime.now();
return o -> o.set(propertyName, now);
```

Right:

```java
return o -> o.set(propertyName, LocalDateTime.now());
```

Because the returned lambda is reused/executed later, eagerly captured values
such as `now` or `ThreadLocal` data become stale.

The same rule applies to current-user / tenant context retrieval: fetch it
inside the lambda if the value must be live at execution time.

## 11. Registration Paths

Plain Java / console:

```java
QueryConfiguration configuration = easyQuery.getRuntimeContext().getQueryConfiguration();
configuration.applyLogicDeleteStrategy(new CustomLogicDelStrategy());
```

Spring Boot starter:

- `LogicDeleteStrategy` beans are collected by the starter
- `EasyQueryInitializeOption` applies them through
  `configuration.applyLogicDeleteStrategy(...)`

So a Spring bean is enough for normal starter-managed registration.

## 12. Relationship to Other Features

- `@LogicDelete` changes delete/query semantics; it does not replace
  optimistic locking.
- Interceptors often complement logic delete for delete-user/delete-time audit
  fields, but they solve different problems.
- Relation config such as `.configure(q -> q.disableLogicDelete())` is often a
  better fit than global `disableLogicDelete()` when only one path needs
  deleted rows.

## 13. Common Mistakes

- Treating `disableLogicDelete()` as harmless. It changes correctness, not just
  visibility.
- Forgetting `allowDeleteStatement(true)` when asking for physical delete.
- Using global `disableLogicDelete()` when only one joined table or relation
  path should ignore soft delete.
- Binding a custom strategy with `CUSTOM` but forgetting to register the named
  strategy.
- Returning lambdas that capture stale timestamps or thread-local values.
- Assuming there can be multiple `@LogicDelete` fields on one entity.

## Read next

- write-path details and physical delete guard: `write.md`
- entity annotations overview: `entity-mapping.md`
- starter defaults such as `deleteThrow`: `configuration-starter.md`
- audit/delete-user/delete-time patterns alongside interceptors:
  `interceptor.md`
