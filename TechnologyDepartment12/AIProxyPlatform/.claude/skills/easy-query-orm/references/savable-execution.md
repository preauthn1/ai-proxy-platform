# Savable Execution Model

Use this reference for:

- transaction and tracking prerequisites
- `executeCommand()`
- `savePath(...)`
- `ignoreRoot()`
- `removeRoot()`
- `disableLogicDelete()` / `useLogicDelete(...)`
- `configure(...)` with `SaveBehaviorEnum`
- batch behavior on `savable(...)`

For relation classification and ownership/cascade rules, read
`savable-relation-rules.md`. For child id safety, read
`savable-key-safety.md`.

## 1. Hard Prerequisites from Source

Current `AbstractEntitySavable` constructor enforces two preconditions before
any save command is built:

1. current thread must already be in a transaction
2. current tracking context must already exist

So this is not optional:

- Spring:
  `@Transactional` + `@EasyQueryTrack`
- plain Java/Kotlin:
  `beginTransaction()` + `TrackManager.begin()`

If either precondition is missing, `savable(...)` is invalid before
`executeCommand()` even runs.

## 2. Minimal Safe Shapes

### Spring Boot

```java
@Transactional(rollbackFor = Exception.class)
@EasyQueryTrack
public void update(String id, UpdateRequest request) {
    SaveUser user = easyEntityQuery.queryable(SaveUser.class)
        .include(u -> u.addresses())
        .whereById(id)
        .singleNotNull();

    mergeRequestIntoTrackedEntity(user, request);

    easyEntityQuery.savable(user).executeCommand();
}
```

### Plain Java / Kotlin

```java
TrackManager trackManager = easyEntityQuery.getRuntimeContext().getTrackManager();
try {
    trackManager.begin();

    try (Transaction tx = easyEntityQuery.beginTransaction()) {
        SaveRoot root = easyEntityQuery.queryable(SaveRoot.class)
            .include(r -> r.items())
            .findNotNull(id);

        mutate(root);

        easyEntityQuery.savable(root).executeCommand();
        tx.commit();
    }
} finally {
    trackManager.release();
}
```

## 3. `executeCommand()` and Public Surface

Current public interface exposes:

```java
easyEntityQuery.savable(entity).executeCommand();
easyEntityQuery.savable(entities).executeCommand();
```

There is also `executeCommand2()` on the underlying `Savable` interface, but
current docs and normal usage center on `executeCommand()`. Do not recommend
`executeCommand2()` unless the local project already uses it and there is a
specific reason.

## 4. `savePath(...)`

Use `savePath(...)` to limit which value-object paths participate in the save:

```java
easyEntityQuery.savable(root)
    .savePath(r -> Include.path(r.items(), r.profile()))
    .executeCommand();
```

Source-backed rule:

- `savePath` is parsed into per-depth property-name limits
- it only supports value-object paths
- if a save path names an aggregate-root path, current provider logic throws

So `savePath(...)` is a value-object limiter, not a generic arbitrary path
selector.

## 5. Root Controls

### `ignoreRoot()`

`ignoreRoot()` is sugar for adding `SaveBehaviorEnum.ROOT_IGNORE`.

Meaning:

- skip root insert/update
- when combined with `removeRoot`, also skip root delete

Use it only when the application will manage the root row separately and
`savable(...)` should only operate on included value-object paths.

### `removeRoot()`

```java
easyEntityQuery.savable(root)
    .removeRoot()
    .executeCommand();
```

Source-backed behavior:

- marks the save as a delete-root workflow
- root navigations are nulled before save planning
- loaded included paths are processed as deletions/detach operations

Treat it as a deletion workflow, not as a mild update option.

### `ROOT_UPDATE_IGNORE`

This source enum exists even though there is no sugar method on
`EntitySavable`.

It can be applied through:

```java
easyEntityQuery.savable(root)
    .configure(c -> c.getSaveBehavior().add(SaveBehaviorEnum.ROOT_UPDATE_IGNORE))
    .executeCommand();
```

Meaning from `BasicSaveCommand`:

- root insert still runs
- root update is skipped

Mention this only when the requirement explicitly needs "save child graph but
do not update the already-existing root row".

## 6. Logic-Delete Behavior on Savable

`EntitySavable` exposes:

```java
easyEntityQuery.savable(root).disableLogicDelete().executeCommand();
easyEntityQuery.savable(root).useLogicDelete(false).executeCommand();
```

This is implemented via `SaveBehaviorEnum.IGNORE_LOGIC_DELETE`, and the final
save command threads the resulting `useLogicDelete` choice into the internal
delete/update operations it performs.

Use this only when the save workflow truly needs to operate outside the normal
logic-delete behavior.

## 7. `IGNORE_NULL` and `IGNORE_EMPTY`

These are source-backed `SaveBehaviorEnum` flags available through
`configure(...)`:

```java
easyEntityQuery.savable(root)
    .configure(c -> {
        c.getSaveBehavior().add(SaveBehaviorEnum.IGNORE_NULL);
        c.getSaveBehavior().add(SaveBehaviorEnum.IGNORE_EMPTY);
    })
    .executeCommand();
```

Practical meaning from provider logic:

- `IGNORE_NULL`:
  a null navigation can be skipped instead of being interpreted as an active
  detach/update signal in certain paths
- `IGNORE_EMPTY`:
  an empty collection can be skipped instead of always being treated as a
  meaningful diff input

Mention these only when the requirement is explicitly about "ignore absent
request children" semantics. They are not default beginner settings.

## 8. Batch on Savable

`EntitySavable` extends `SQLBatchExecute`, so:

```java
easyEntityQuery.savable(root).batch().executeCommand();
```

Current source passes the batch flag into `BasicSaveCommand`, which then calls
`batch(batch)` on the internal insert/update/delete chains it builds.

So:

- batch on savable is real
- it affects the internal mutation statements generated by the save command
- batch semantics still follow the normal write-stack caveats from `write.md`

## 9. Execution Order Intuition

The current save command is not random. At a high level:

1. deepest detach/delete work is processed first
2. root insert/update/delete work is processed next
3. child insert / set-null / update work follows

Why this matters:

- root insert can populate generated keys before child rows need them
- delete-root workflows can clean relation state before removing the root

## 10. Common Failure Modes

- Calling `savable(...)` outside a transaction
- Calling `savable(...)` without an active track context
- Using `savePath(...)` with aggregate-root navigation
- Treating `ignoreRoot()` as harmless default boilerplate
- Using `IGNORE_NULL` / `IGNORE_EMPTY` without a clear request-semantics reason

## Sources

- docs:
  `easy-query-doc/src/savable/README.md`
- docs:
  `easy-query-doc/src/savable/remove-root.md`
- source:
  `EntitySavable`
- source:
  `SaveConfigurer`
- source:
  `SaveBehaviorEnum`
- source:
  `AbstractEntitySavable`
- source:
  `BasicSaveCommand`
