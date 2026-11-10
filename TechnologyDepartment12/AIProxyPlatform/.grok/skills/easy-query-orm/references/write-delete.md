# Delete

Use this reference for:

- `deletable(...)`
- expression delete vs object delete
- relation-driven joined expression delete
- batch delete
- version-aware delete controls
- physical delete safety

For detailed soft-delete strategy design, add `logic-delete.md`. For
transaction boundaries, add `transaction.md`.

## 1. Expression Delete

`easyEntityQuery.deletable(Entity.class)` is the expression-delete branch. In
source terms, this is `ClientExpressionDeletable<T>`.

Use it when:

- the delete condition is explicit
- you want `where(...)`, `whereById(...)`, or `whereByIds(...)`
- the answer shape is "delete rows matching this predicate"

### By condition / by id / by ids

```java
easyEntityQuery.deletable(Topic.class)
    .where(t -> t.title().eq("title998"))
    .executeRows();

easyEntityQuery.deletable(Topic.class)
    .whereById("999")
    .executeRows();

easyEntityQuery.deletable(Topic.class)
    .whereByIds(Arrays.asList("2", "3", "4"))
    .executeRows();
```

This is the delete-side counterpart of expression update:

- update:
  `updatable(Entity.class).setColumns(...).where(...)`
- delete:
  `deletable(Entity.class).where(...)`

### Joined expression delete via relation paths

Current tests show that expression delete can also generate joined delete SQL
for supported dialects when the predicate references relation paths:

```java
easyEntityQuery.deletable(DocBankCard.class)
    .allowDeleteStatement(true)
    .where(card -> card.user().name().like("123"))
    .executeRows();
```

This is important because there is no separate `leftJoin(...)` API on the
delete chain. The relation predicate itself drives the generated multi-table
delete shape.

Current tests cover dialect-specific joined delete SQL at least for:

- PostgreSQL
- SQL Server

Do not rewrite this to "query ids first, then delete by ids" unless the target
dialect/project version clearly cannot support the generated shape.

### Expression delete version controls

`ClientExpressionDeletable` carries both `withVersion(...)` and
`ignoreVersion()`:

```java
easyEntityQuery.deletable(SysUserVersionLong.class)
    .where(o -> o.id().eq(id))
    .withVersion(1L)
    .executeRows();

easyEntityQuery.deletable(SysUserVersionLong.class)
    .whereById(id)
    .ignoreVersion()
    .executeRows();
```

So if the requirement is "delete only when current version matches", do not
jump straight to object delete.

### What expression delete is not

- it is not object-driven
- it is not the same as `deletable(entity)`
- it does not use `whereColumns(...)`; that API belongs to object delete

## 2. Object Delete

### By id / by condition / by entity / by collection

```java
easyEntityQuery.deletable(Topic.class)
    .whereById("999")
    .executeRows();

easyEntityQuery.deletable(Topic.class)
    .where(t -> t.title().eq("title998"))
    .executeRows();

easyEntityQuery.deletable(topic)
    .executeRows();

easyEntityQuery.deletable(topics)
    .batch()
    .executeRows();
```

### Object delete

`deletable(entity)` / `deletable(collection)` is the object-driven form. In
source terms this is `ClientEntityDeletable<T>`.

Like object update, it is not the same thing as a fully explicit condition
delete.

### `whereColumns(...)` on object delete

Object delete also has precision control:

```java
easyEntityQuery.deletable(user)
    .whereColumns(o -> o.FETCHER.id())
    .executeRows();
```

Use this when the object delete condition must be constrained to selected
original columns rather than left implicit.

## 3. Version Controls on Delete

```java
easyEntityQuery.deletable(SysUserVersionLong.class)
    .whereById(id)
    .ignoreVersion()
    .executeRows();
```

If the entity uses optimistic lock semantics, version rules can also affect
delete paths. Do not answer `ignoreVersion()` casually.

High-value boundary:

- expression delete can use `withVersion(...)` or `ignoreVersion()`
- object delete can use `ignoreVersion()`
- do not collapse these into one undifferentiated "delete supports version"
  sentence

## 4. Physical Delete Safety

If logic delete is active, delete may become update instead of real `DELETE`.

True physical delete needs explicit opt-in:

```java
easyEntityQuery.deletable(BlogEntity.class)
    .where(o -> o.id().eq("id123"))
    .disableLogicDelete()
    .allowDeleteStatement(true)
    .executeRows();
```

Meaning:

- `disableLogicDelete()`:
  bypass soft-delete behavior
- `allowDeleteStatement(true)`:
  remove the delete guard

This is a guard-removal API, not proof that the business condition is safe.

## 5. What To Say About `allowDeleteStatement(true)`

State it precisely:

- it allows physical delete SQL to execute
- it does not make a weak condition safe
- it should not be taught as default cleanup boilerplate

If the project also has starter `deleteThrow = true`, mention that the runtime
default is intentionally conservative.

## 6. Row-Count Semantics

Treat `executeRows() == 0` as meaningful:

- row not found
- version mismatch
- logic-delete visibility/state mismatch

Do not silently treat it as success unless the business flow really accepts
"already absent" behavior.

## 7. Pairing Rules

- Need soft-delete strategy, query-time toggles, or table-local disable:
  add `logic-delete.md`
- Need exact transaction boundary:
  add `transaction.md`
- Need batch execution-mode detail:
  add `write.md`

## Common Selection Rule

- default service delete command:
  expression delete
- already have a loaded entity or delete collection object path:
  object delete
- need version match inside explicit condition:
  expression delete with `withVersion(...)`
- need relation-path predicate that should compile to joined delete SQL:
  expression delete

## Common Mistakes

- Treating `deletable(Entity.class)` and `deletable(entity)` as the same
  mutation shape
- Forgetting expression delete has `whereByIds(...)` and version controls
- Rewriting a valid relation-driven expression delete into multi-step
  query-then-delete logic without a dialect reason
- Calling physical delete without both a real condition and
  `allowDeleteStatement(true)`
- Teaching `disableLogicDelete()` as harmless default boilerplate
- Treating object delete and explicit condition delete as interchangeable
- Treating zero affected rows as silent success

## Sources

- docs:
  `easy-query-doc/src/ability/delete.md`
- source:
  `Deletable`
- source:
  `ClientExpressionDeletable`
- source:
  `AbstractClientExpressionDeletable`
- source:
  `ClientEntityDeletable`
- source:
  `AbstractClientEntityDeletable`
