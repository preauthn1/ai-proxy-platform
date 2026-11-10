# Relation Modeling

Read this when the task needs to declare or fix `@Navigate` metadata on entity
fields. This file is about relation shape and direction, not eager loading or
implicit query behavior.

For eager loading, read `include-structured-loading.md`. For relation-driven
query predicates and subqueries, read `implicit-query.md`. For advanced
`@Navigate` options such as `subQueryToGroupJoin`, `supportNonEntity`, or
`@NavigateFlat`, read `entity-modeling-navigate.md`.

## When to use / not

Use this file when:

- a relation field is missing or modeled with the wrong cardinality
- `selfProperty` / `targetProperty` direction is unclear
- include or implicit relation queries fail because the relation itself is wrong

Do not use this file as the primary guide for `.include(...)`, `.include2(...)`,
explicit join chains, or DTO graph loading.

## Core `@Navigate` Shape

`@Navigate` goes on the entity field that holds the related object or collection.
`selfProperty` is the current entity column. `targetProperty` is the related
entity column.

Imports:

- `com.easy.query.core.annotation.Navigate`
- `com.easy.query.core.enums.RelationTypeEnum`
- `lombok.experimental.FieldNameConstants` when you want `Entity.Fields.xxx`
  constants instead of string literals

The examples below use `Company.Fields.id`, `SysUser.Fields.companyId`, and
similar constants. Those `Fields` members come from Lombok
`@FieldNameConstants` on the involved entity or mapping classes.

## One-to-Many

Example: one company has many users.

```java
@Navigate(
    value = RelationTypeEnum.OneToMany,
    selfProperty = {Company.Fields.id},
    targetProperty = {SysUser.Fields.companyId}
)
private List<SysUser> users;
```

Meaning:

- current entity column: `Company.id`
- target entity column: `SysUser.companyId`

## Many-to-One

Example: one user belongs to one company.

```java
@Navigate(
    value = RelationTypeEnum.ManyToOne,
    selfProperty = {SysUser.Fields.companyId},
    targetProperty = {Company.Fields.id}
)
private Company company;
```

Meaning:

- current entity column: `SysUser.companyId`
- target entity column: `Company.id`

## Many-to-Many

Many-to-many requires a mapping entity and both mapping directions.

```java
@Navigate(
    value = RelationTypeEnum.ManyToMany,
    mappingClass = UserRole.class,
    selfProperty = {SysUser.Fields.id},
    selfMappingProperty = {UserRole.Fields.userId},
    targetMappingProperty = {UserRole.Fields.roleId},
    targetProperty = {Role.Fields.id}
)
private List<Role> roles;
```

Use many-to-many only when the mapping table is truly relation data. If the
middle table carries business state that should be queried directly, model it as
its own entity and consider one-to-many plus many-to-one instead.

## Direction Check

Before changing code, check the relation in this order:

1. Which class owns the annotated field?
2. Which column on that class is the current-side key?
3. Which column on the other class matches it?
4. Is the field cardinality one object or a collection?

If you get `selfProperty` / `targetProperty` backward, include and implicit
queries may compile but return empty or incorrect data.

## Choosing the Relation Type

Use these rules:

- `ManyToOne`: many current rows point to one target row
- `OneToMany`: one current row points to many target rows
- `ManyToMany`: both sides connect through a mapping table

Do not choose relation type based on what the UI wants to display. Choose it
from database cardinality.

## Minimal Example Pair

```java
@FieldNameConstants
public class Company implements ProxyEntityAvailable<Company, CompanyProxy> {
    @Column(primaryKey = true)
    private String id;

    @Navigate(
        value = RelationTypeEnum.OneToMany,
        selfProperty = {Fields.id},
        targetProperty = {SysUser.Fields.companyId}
    )
    private List<SysUser> users;
}

@FieldNameConstants
public class SysUser implements ProxyEntityAvailable<SysUser, SysUserProxy> {
    @Column(primaryKey = true)
    private String id;
    private String companyId;

    @Navigate(
        value = RelationTypeEnum.ManyToOne,
        selfProperty = {Fields.companyId},
        targetProperty = {Company.Fields.id}
    )
    private Company company;
}
```

## Tree / Self-Relation Entity

Tree entities are not a separate ORM type. They are ordinary self-relations.
The high-value distinction is:

- `children`: self `OneToMany`, required when you want `asTreeCTE()` to infer
  the tree child path and when you want `toTreeList()`
- `parent`: self `ManyToOne`, optional but strongly recommended when business
  code also traverses upward paths such as `node.parent().name()`
- `@EasyTree("children")`: only needed when the same entity has multiple self
  `List<SelfType>` one-to-many relations and the framework needs to know which
  one is the real tree child path

Minimal tree entity skeleton:

```java
import com.easy.query.core.annotation.Column;
import com.easy.query.core.annotation.EntityProxy;
import com.easy.query.core.annotation.Navigate;
import com.easy.query.core.annotation.Table;
import com.easy.query.core.enums.RelationTypeEnum;
import com.easy.query.core.proxy.ProxyEntityAvailable;
import lombok.Data;
import lombok.experimental.FieldNameConstants;

import java.util.List;

@Data
@Table("sys_dept")
@EntityProxy
@FieldNameConstants
public class SysDept implements ProxyEntityAvailable<SysDept, SysDeptProxy> {
    @Column(primaryKey = true)
    private String id;
    private String parentId;
    private String name;

    @Navigate(
        value = RelationTypeEnum.ManyToOne,
        selfProperty = {Fields.parentId},
        targetProperty = {Fields.id}
    )
    private SysDept parent;

    @Navigate(
        value = RelationTypeEnum.OneToMany,
        selfProperty = {Fields.id},
        targetProperty = {Fields.parentId}
    )
    private List<SysDept> children;
}
```

Source-backed simpler form also exists for the child side:

```java
@Navigate(value = RelationTypeEnum.OneToMany, targetProperty = "parentId")
private List<MyCategory> children;
```

But for skill answers, prefer the explicit `selfProperty + targetProperty`
version when writing a fresh entity template. It is easier to review and
extends cleanly when the user later adds the `parent` relation too.

If the user only needs recursive SQL and does not want to model `children` on
the entity, the fallback is `asTreeCTECustom(idExpr, parentIdExpr)`. That is a
query workaround, not the preferred tree-entity modeling answer.

## Tree Modeling Rules

When the task says `树表 entity`, `菜单树实体`, `评论树`, `部门树`, or similar:

1. Start from self-relation modeling, not from query code.
2. Add `id` and `parentId` first.
3. Add self `OneToMany children`.
4. Add self `ManyToOne parent` if the business also traverses upward.
5. Add `@EasyTree("children")` only when multiple self `List<SelfType>` paths
   exist.

Do not answer a tree-entity prompt with only `parentId` plus plain fields and
leave out the self relation. That shape compiles as a flat table entity but is
not the tree model easy-query expects for `asTreeCTE() + toTreeList()`.

## Common Mistakes

- `selfProperty` and `targetProperty` reversed
- using `OneToOne` or `ManyToOne` when the database actually allows many current
  rows to reference the same target row
- declaring `ManyToMany` without a real mapping table
- trying to solve DTO graph problems by changing entity relation cardinality
- using relation field names that do not match the actual business meaning
- for tree tables, keeping only `parentId` and forgetting self `children`
- adding `@EasyTree` as if every tree entity must have it; it is only a
  disambiguation hook when multiple self child lists exist

## Read Next

- Need `.include(...)`, `.include2(...)`, or `loadInclude(...)`:
  `include-structured-loading.md`
- Need relation predicates like `user.roles().any(...)` or
  `subQueryToGroupJoin(...)`: `implicit-query.md`
- Need advanced metadata like `required`, `limit`, `supportNonEntity`,
  `ignoreAutoInclude`, or `@NavigateFlat`: `entity-modeling-navigate.md`
