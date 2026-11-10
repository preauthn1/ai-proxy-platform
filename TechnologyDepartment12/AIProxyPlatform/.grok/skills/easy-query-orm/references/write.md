# Mutation Overview

Use this file as the write-stack router.

Open one specialized mutation reference first:

- insert / upsert / map insert:
  `write-insert-upsert.md`
- update / object update / map update / optimistic lock:
  `write-update.md`
- delete / physical delete safety:
  `write-delete.md`
- diff update tracking / `TrackManager` / `asTracking` / `@EasyQueryTrack`:
  `write-tracking.md`

Keep `transaction.md`, `logic-delete.md`, and `savable-aggregate.md` as
separate companions rather than overloading one write file with everything.

## Decision Guide

- Need to add rows:
  `write-insert-upsert.md`
- Need insert-or-update / insert-or-ignore by key:
  `write-insert-upsert.md`
- Need to choose id strategy or explain insert-time key assignment:
  `primary-key-generation.md`
- Need explicit `UPDATE ... SET ... WHERE ...`:
  `write-update.md`
- Already have an entity object and need controlled object update:
  `write-update.md`
- Need diff update from tracked objects:
  `write-tracking.md`
- Need to remove rows or reason about physical delete:
  `write-delete.md`
- Need transaction boundaries:
  `transaction.md`
- Need aggregate graph save:
  `savable-aggregate.md`
- Need full soft-delete strategy design:
  `logic-delete.md`

## Cross-Cutting Rules

- `executeRows() == 0` is meaningful. Treat it as not-found, stale-version, or
  state drift unless the business flow explicitly accepts that outcome.
- Distinguish expression update from object update. Do not default every update
  answer to `updatable(entity).executeRows()`.
- For map writes, keys are column names, not entity property names.
- For dangerous deletes, the guard removal API is not the same as having a safe
  business condition.

## Batch Semantics

`batch()` is a write execution-mode switch, not just a vague "bulk" hint.

Current source behavior:

- `batch()` calls `batch(true)`
- `batch(boolean use)` toggles execution behavior on the current chain
- enabled batch paths ultimately use JDBC
  `PreparedStatement.addBatch()` / `executeBatch()`
- `batch(false)` disables that path for the current chain

That means:

- `batch()` / `batch(true)` mean "prefer JDBC batch execution for this write"
- compatible SQL can be grouped before batch execution
- incompatible SQL shapes may still split into several batch groups
- batch does not promise "one SQL statement"

Practical caveats:

- returned affected-row counts are driver-dependent under batch mode
- do not treat batch row counts as strong business invariants
- MySQL commonly needs `rewriteBatchedStatements=true` to get real
  `executeBatch()`-backed gains

## Pairing Rules

- insert/upsert + batch:
  read `write-insert-upsert.md`, then return here only if batch semantics are
  the core question
- insert/upsert + generated key / custom id:
  add `primary-key-generation.md`
- update + tracking:
  read `write-update.md`, then add `write-tracking.md`
- delete + logic delete:
  read `write-delete.md`, then add `logic-delete.md`

## Read next

- insert/upsert:
  `write-insert-upsert.md`
- update:
  `write-update.md`
- delete:
  `write-delete.md`
- tracking:
  `write-tracking.md`

## Sources

- source:
  `SQLBatchExecute`
- source:
  `AbstractSQLExecuteRows`
- source:
  `EasyJdbcExecutorUtil`
- source:
  `BaseExecutionCreator`
