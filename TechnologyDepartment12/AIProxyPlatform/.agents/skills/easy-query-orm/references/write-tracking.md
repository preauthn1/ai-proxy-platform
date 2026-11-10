# Diff Update Tracking

Use this reference for mutation-side tracking:

- `TrackManager`
- `asTracking()`
- `asNoTracking()`
- `addTracking(entity)`
- `@EasyQueryTrack`
- differential update behavior for `updatable(entity)`

Do not use this as the main guide for plain update commands. For non-tracked
write DSL, read `write-update.md` first.

## What Tracking Changes

Tracking turns object update from "write the object's current selected column
set" into "compare original tracked state vs current state and emit a diff
update".

This matters most for:

- object update paths
- request merge into a loaded entity
- `savable(...)` graph updates

## 1. Two Requirements, Not One

Tracking is not enabled just by calling `asTracking()` alone.

You need both:

1. an active tracking context
2. a tracked entity

Current source explicitly warns when `asTracking()` is used without an active
thread tracking context.

## 2. Plain Java / Kotlin Tracking Scope

```java
TrackManager trackManager = easyEntityQuery.getRuntimeContext().getTrackManager();
try {
    trackManager.begin();

    User user = easyEntityQuery.queryable(User.class)
        .asTracking()
        .findNotNull(id);

    user.setName("new");
    easyEntityQuery.updatable(user).executeRows();
} finally {
    trackManager.release();
}
```

This is the canonical non-Spring tracking scope.

## 3. Spring Boot Tracking Scope

```java
@Transactional(rollbackFor = Exception.class)
@EasyQueryTrack
public void updateUser(String id) {
    User user = easyEntityQuery.queryable(User.class)
        .asTracking()
        .findNotNull(id);

    user.setName("new");
    easyEntityQuery.updatable(user).executeRows();
}
```

Source-backed boundary:

- `@EasyQueryTrack` opens the tracking scope
- `asTracking()` marks the query result for tracking behavior
- both are still conceptually separate

## 4. `addTracking(entity)`

Use this when the query itself was not tracking-enabled or when only selected
objects should join the current tracking context:

```java
TrackManager trackManager = easyEntityQuery.getRuntimeContext().getTrackManager();
try {
    trackManager.begin();

    User user = easyEntityQuery.queryable(User.class).findNotNull(id);
    easyEntityQuery.addTracking(user);

    user.setName("new");
    easyEntityQuery.updatable(user).executeRows();
} finally {
    trackManager.release();
}
```

Practical meaning:

- `asTracking()` tracks query results
- `addTracking(entity)` manually adds a specific entity to the current context

## 5. `asNoTracking()`

Use `asNoTracking()` when:

- a broader environment already has tracking enabled
- a particular read should not join the tracking context
- data volume is large and tracking every row is unnecessary

## 6. What Diff Update Looks Like

Tracked object update:

- changed fields generate `SET`
- unchanged fields are not written
- if nothing changed, update may emit no SQL and affect `0` rows

Non-tracked object update:

- default object-update rules apply
- commonly this means broader or full-column update behavior unless you
  explicitly narrowed it with `setColumns(...)`

That is the operational reason why tracking and plain object update must not be
explained as the same thing.

## 7. Exact `TrackManager` Helpers

For lower-level/manual tracking inspection, current source exposes two exact
helper methods on `TrackManager`:

- `currentThreadTracking()` — checks whether the current thread already has an
  active tracking scope
- `getCurrentTrackContext()` — returns the current `TrackContext`; it can be
  `null` outside a tracking scope

Source-backed nuance:

- `currentThreadTracking()` is effectively a convenience check over
  `getCurrentTrackContext() != null`
- these helpers do **not** replace the existing two requirements for diff
  update: an active tracking context and a tracked entity
- use them for inspection / defensive branching, not as a substitute for
  `@EasyQueryTrack`, `TrackManager.begin()`, `asTracking()`, or
  `addTracking(entity)`

## 8. High-Value Caveats

### `asTracking()` without active context

Current source warns if the current thread is not in tracking mode. So do not
teach `asTracking()` as self-sufficient.

### Large queries

If the result set is large, do not casually mark everything tracking-enabled.
Prefer:

- non-tracking query
- then `addTracking(entity)` only for the rows that will actually be mutated

### Complex converted value objects

Docs warn that if a complex value is stored via conversion, its equality
behavior must be suitable for diff detection. Otherwise tracking may not detect
changes correctly.

### Query result reuse inside tracking context

Docs note that the same tracked key can resolve to the current tracked object
rather than a fresh database image inside the same tracking context. Do not
teach "query again to force refresh" as a default pattern inside one active
tracking scope.

## 8. Pairing Rules

- Need object update precision controls like `setColumns(...)` /
  `whereColumns(...)`:
  add `write-update.md`
- Need aggregate graph diff save:
  add `savable-aggregate.md`
- Need Spring Boot track aspect behavior:
  add `spring-boot-starter.md`

## Common Mistakes

- Explaining `asTracking()` without also explaining the tracking context
- Using `@EasyQueryTrack` but forgetting `asTracking()` or `addTracking(...)`
  on the objects that should diff-update
- Tracking huge query results when only a few rows need mutation
- Expecting non-tracked object update to behave like diff update

## Sources

- docs:
  `easy-query-doc/src/adv/data-tracking.md`
- docs:
  `easy-query-doc/src/ability/update.md`
- source:
  `EasyQueryClient`
- source:
  `ClientQueryable`
- source:
  `AbstractClientQueryable`
- source:
  `TrackManager`
- source:
  `EasyQueryTrackAopConfiguration`
