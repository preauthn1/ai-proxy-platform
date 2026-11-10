# Savable Relation Rules

Use this reference for:

- aggregate root vs value object classification
- one-to-one / one-to-many / many-to-many / many-to-one save behavior
- cascade behavior
- ownership change
- unsupported relation shapes
- tracked include/path requirements for navigation saves
- `@SaveKey` non-primary-key matching identity for `savable(...)` diff save

For transaction/tracking prerequisites and save behaviors, read
`savable-execution.md`. For child key safety, read `savable-key-safety.md`.

## 1. The Core Classification Rule

Current provider logic classifies a navigation roughly like this:

- if the current entity's relation keys are its own key props:
  target is a value object
- if the target-side relation props are key props of the current entity:
  target is an aggregate root
- otherwise:
  it is a relation-other shape, not a normal value-object save path

This is the engine behind the docs' aggregate-root/value-object explanation.

## 2. Value Object vs Aggregate Root

### Value object

`savable(root)` can recursively manage it.

Depending on cascade and relation type, this can include:

- insert
- update
- delete or set-null detach

### Aggregate root

`savable(currentEntity)` does not recursively save or delete that target root.
Instead, it copies relationship key values as needed.

That is why many-to-one save is special.

## 3. One-to-One / One-to-Many

These are the main happy-path aggregate-root save shapes.

If the root owns the child through its own key relation:

- child can be treated as a true value object
- diff save can insert/update/delete it
- recursive save can continue downwards when cascade semantics allow it

This is where `savable(...)` is most natural.

## 4. Many-to-One Is Special

Docs and source agree on this boundary:

- the navigation target is an aggregate root
- `savable(valueObject)` does not recursively save/detach that aggregate root
- it mainly copies the aggregate-root key into the current entity relation
  field(s)

So for a many-to-one relation, do not teach:

- "savable(child) will also save parent"
- "savable(child) can detach/delete the parent navigation"

That is not the intended model.

## 5. Many-to-Many

This is only automatically manageable when the mapping table semantics are
clear.

### Pure mapping table

If the many-to-many mapping row is just relation data and the relation uses:

```java
@Navigate(..., mappingClass = UserRole.class, cascade = CascadeTypeEnum.DELETE)
```

then savable can automatically insert/delete mapping rows.

### Mapping table with business fields

If the mapping table has business fields:

- do not treat the many-to-many relation itself as the full save path
- use `cascade = CascadeTypeEnum.NO_ACTION` on the many-to-many navigation
- model a separate one-to-many navigation to the mapping entity and save that
  explicitly

This is one of the highest-value savable modeling rules in practice.

## 5.5. `@SaveKey` — Non-PK Matching Identity for Savable Diff

`com.easy.query.core.annotation.SaveKey` is a marker annotation,
`@Retention(RUNTIME)`, `@Target({FIELD, ANNOTATION_TYPE})`, no attributes. Its
javadoc: "标记当前对象除了主键外支持哪种保存更新判定标识" (mark which non-PK
fields serve as the save/update determination identity).

### What it actually does

`@SaveKey` does **not** touch SQL generation at all. The generated `WHERE`,
`SET`, `INSERT` column lists, and `ON DUPLICATE KEY` are all built from the
primary key exactly as usual. It is purely an **in-memory identity** used by the
`savable(...)` cascade-save diff pipeline to match an incoming child against the
children already loaded from DB.

Pipeline points where it is consulted (only inside `savable(...)`, never in
standalone `insertable` / `updatable` / `deletable` / `saveOrUpdate`):

- `DatabaseEntityValues.getTrackKey(entity)`: first tries the PK-based track key
  via `EasyTrackUtil.getTrackKey(...)`; only when that returns `null` does it
  fall back to looking up the entity by its `@SaveKey` value tuple in an
  in-memory index. A hit classifies the incoming entity as the *same* row
  (UPDATE / no-op); a miss classifies it as a new row (INSERT).
- `DatabaseEntityValues.checkSaveKeyRepeat(entity)`: rejects two *different*
  incoming objects that share the same `@SaveKey` tuple, throwing
  `EasyQueryInvalidOperationException`.

### Parse-time metadata

In `EntityMetadata.createColumnOption(...)`, a field with `@SaveKey` is added to
a **separate** `saveKeyPropertiesSet`, independent of the primary-key
`keyPropertiesMap`. So `@SaveKey` does **not** replace or augment the PK for any
SQL operation; it is a *fallback identity* used only when the PK-based track key
is null (e.g. a request-built entity without an id, or a fresh middle-table row
whose auto-generated id is not yet known).

### When `@SaveKey` matters: many-to-many middle tables

The canonical use case is a many-to-many middle/mapping table whose natural
identity is a business composite key such as `(root_id, many_id)`, while the
physical PK `id` is just a surrogate auto-generated column.

Without `@SaveKey`: an incoming child with a fresh `null` PK is matched against
DB rows by PK, fails to match, and is classified as INSERT — while the old DB
row (no longer matched) becomes DELETE. Result: a delete+insert pair per row,
even when nothing logically changed.

With `@SaveKey` on `(rootId, manyId)`: when the PK is null, the engine matches
the incoming row to the existing DB row by `(rootId, manyId)`, recognizes it as
the same row, and emits no INSERT/DELETE (an UPDATE only if a non-key field
actually changed).

### Example (mirrors `sql-test` `M8SaveRootMiddleMany2`)

Entity — `@SaveKey` on the two business-key fields, surrogate PK is separate:

```java
import com.easy.query.core.annotation.SaveKey;

@Data
@EntityProxy
@Table("m8_save_root_middle_many")
public class M8SaveRootMiddleMany2
        implements ProxyEntityAvailable<M8SaveRootMiddleMany2, M8SaveRootMiddleMany2Proxy> {
    @Column(primaryKey = true)
    private String id;       // surrogate PK, auto-generated

    @SaveKey
    private String rootId;   // business key part 1
    @SaveKey
    private String manyId;   // business key part 2
}
```

Verified behavioral difference in `M8SaveTest` for the same "replace every
middle row with a fresh instance having a new `id` but the same
`(rootId, manyId)`" scenario:

- `M8SaveRootMiddleMany` (no `@SaveKey`): JDBC executes 2 SELECT + 2 DELETE +
  2 INSERT (6 statements), with `DELETE FROM ... WHERE id = ?` / `INSERT INTO
  ... (id,root_id,many_id) VALUES (?,?,?)`.
- `M8SaveRootMiddleMany2` (with `@SaveKey`): JDBC executes only the 2 SELECT
  statements — no DELETE, no INSERT, because rows match by business key.
- Adding a third incoming row that duplicates an existing `(rootId, manyId)`
  tuple makes `checkSaveKeyRepeat` throw
  `"In [...] , there are objects with the same @SaveKey, but their entity
  values are [...]"`.

### Rules and caveats

- Put `@SaveKey` on the entity **field(s)**, not on the enum/type (`@Target`
  allows FIELD). Multiple `@SaveKey` fields compose a composite business key.
- All `@SaveKey` field values must be non-null during a savable diff;
  `DatabaseEntityValues.getRelationValue(...)` throws
  `EasyQueryInvalidOperationException("... save key property:[...] can not be
  null")` on any null value.
- `@SaveKey` is a savable-only concept. It has no effect on direct
  `insertable/updatable/deletable`, on `saveOrUpdate`, or on plain query/update
  WHERE clauses.
- A field can be both `@Column(primaryKey=true)` and `@SaveKey`, but that is
  redundant: the PK-based track key already matches first, so `@SaveKey` only
  helps on non-PK business fields where the PK is unknown/unset.
- `@SaveKey` is orthogonal to `saveEntitySetPrimaryKey(...)` (see
  `savable-key-safety.md`): the latter assigns safe backend PKs to untracked
  children before save; `@SaveKey` changes *which* row a child is matched to
  during diff. They solve different problems and can coexist.

## 6. Cascade Rules in Savable

### `NO_ACTION`

- detach does nothing automatically
- application logic must handle it

### `AUTO` / `SET_NULL`

- detach becomes key-nulling update behavior
- for some shapes this is only valid when target props are not key props

### `DELETE`

- for true value objects, detach becomes delete
- recursive save/delete can continue downward
- for many-to-many pure mapping rows, this enables automatic mapping-table
  maintenance

Use `DELETE` only when the child is truly owned and should not survive
detachment.

## 7. Ownership Change

Source `setTargetValue(...)` enforces strict ownership by default.

If a value object currently belongs to one aggregate root and the new graph
tries to assign it to another, current behavior throws an error similar to:

- relation value not equals
- current ownership policy does not allow reassignment

Allow reassignment only when explicitly intended:

```java
easyEntityQuery.savable(list)
    .configure(c -> c.getSaveBehavior().add(SaveBehaviorEnum.ALLOW_OWNERSHIP_CHANGE))
    .executeCommand();
```

Do not recommend this as default. It changes ownership safety semantics.

## 8. Untracked Navigation Values Are Not Harmless

Current provider logic checks navigation properties that are present on the
entity but not part of the tracked/include/save-path picture.

If a navigation value is present but not being tracked in the expected way,
current save logic can throw "current navigation property ... is not being
tracked".

Practical rule:

- for update/diff saves, load the graph paths you intend to save using
  `include(...)` / `loadInclude(...)`, or constrain with `savePath(...)`
- do not attach random navigation objects to a tracked root and expect savable
  to infer the intended path safely

## 9. Unsupported / Weakly Supported Shapes

- `directMapping` relations are not supported by savable
- arbitrary non-key relation-other paths are not normal savable recursion paths
- many-to-many without a proper `mappingClass` is not the auto-managed shape

If the requirement depends on these, be explicit that the normal savable model
is not a fit.

## 10. Pairing Rules

- Need root controls / `savePath` / `IGNORE_NULL` / `IGNORE_EMPTY`:
  add `savable-execution.md`
- Need frontend child id safety:
  add `savable-key-safety.md`

## Common Mistakes

- Treating many-to-one navigation as recursively savable parent state
- Using `cascade = DELETE` on a child that is not truly owned
- Expecting many-to-many with business fields to be safely auto-managed
- Enabling ownership change without explicit business intent
- Forgetting to load or constrain the navigation paths that should participate
  in save diffing
- Expecting `@SaveKey` to change generated SQL `WHERE`/`SET` — it only changes
  in-memory child matching during `savable(...)` diff
- Putting `@SaveKey` on a field whose value can be null during a savable diff
- Confusing `@SaveKey` (non-PK business-key matching) with
  `saveEntitySetPrimaryKey(...)` (backend PK assignment for untracked children)

## Sources

- docs:
  `easy-query-doc/src/savable/{README,one2one,one2many,many2many,many2one,ownership}.md`
- docs:
  `easy-query-doc/src/savable/set-save-key.md`
- source:
  `com.easy.query.core.annotation.SaveKey`
- source:
  `EntityMetadata.createColumnOption(...)` / `getSaveKeyProperties()` /
  `saveKeyPropertiesSet`
- source:
  `DatabaseEntityValues.getTrackKey(...)` /
  `getRelationValue(...)` / `checkSaveKeyRepeat(...)`
- source:
  `AutoTrackSaveProvider.valueObjectUpdate(...)` /
  `valueObjectEntityInsertUpdate(...)`
- source:
  `AbstractSaveProvider`
- source:
  `AutoTrackSaveProvider`
