# Savable Aggregate Root

Use this file as the `savable(...)` router.

Open one specialized reference first:

- required context, save behaviors, `savePath`, `ignoreRoot`, `removeRoot`,
  logic-delete toggle, batch:
  `savable-execution.md`
- aggregate root vs value object rules, one-to-one / one-to-many /
  many-to-many / many-to-one semantics, cascade, ownership change:
  `savable-relation-rules.md`
- child id safety and `saveEntitySetPrimaryKey(...)`:
  `savable-key-safety.md`

## When to Use

Use `savable(...)` when a create/update/delete flow needs to persist an
aggregate root and its value-object graph by diff rather than by hand-written
insert/update/delete orchestration.

Do not use it as a generic replacement for simple one-row insert/update. For
ordinary row mutations, prefer the write references.

## High-Value Source Truth

Current source makes `savable(...)` stricter than many people assume:

- constructing the savable chain already requires a transaction
- it also requires a non-null current tracking context
- update-style save logic depends on tracked entity state, not just the current
  object graph values

That means a valid `savable(...)` answer must treat transaction + tracking as
first-class prerequisites, not optional polish.

## Decision Guide

- Need the "how do I call `savable(...)` safely" answer:
  `savable-execution.md`
- Need to understand why a relation is or is not saved recursively, or need
  `@SaveKey` non-PK matching for many-to-many middle tables:
  `savable-relation-rules.md`
- Need to prevent frontend child ids from corrupting inserts:
  `savable-key-safety.md`
- Need generic id strategy rather than request-child safety:
  `primary-key-generation.md`
- Need only ordinary insert/update/delete:
  `write-insert-upsert.md`, `write-update.md`, `write-delete.md`

## Pairing Rules

- `savable` + diff update / `@EasyQueryTrack` confusion:
  add `write-tracking.md`
- `savable` + transaction confusion:
  add `transaction.md`
- `savable` + logic-delete behavior:
  add `logic-delete.md`

## Read next

- execution model:
  `savable-execution.md`
- relation rules:
  `savable-relation-rules.md`
- key safety:
  `savable-key-safety.md`
