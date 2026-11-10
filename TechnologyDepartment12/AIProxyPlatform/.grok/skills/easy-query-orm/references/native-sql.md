# Native SQL

Use this reference only when the answer truly needs raw SQL:
whole-SQL execution, raw-SQL-backed derived tables, native fragments inside a
DSL query, or a reusable wrapper for an unsupported database function.

If the requirement can already be expressed by easy-query's typed SQL
expression surface, use `typed-sql-expressions.md` instead.

The real boundary is:

- `typed-sql-expressions.md`: easy-query has already exposed a typed SQL
  expression surface
- `native-sql.md`: you need to write the SQL shape or function call yourself

## Fast Routing

Use this file when the request mentions any of these:

- a database function is not surfaced by easy-query's typed layer
- custom database function / dialect function / handwritten function call
- `sqlQuery(...)`, `sqlEasyQuery(...)`, `sqlQueryMap(...)`, `sqlExecute(...)`
- `queryable(rawSql, Entity.class, params)` or `mapQueryable(String sql)`
- native SQL fragment in `where` / `orderBy` / `having` / `select` / `set`
- `rawSQLCommand`, `rawSQLStatement`, `setSQL`, `sqlNativeSegment`
- unsupported dialect function that needs a custom wrapper
- `format(...)`, `value(...)`, quote escaping, `messageFormat()`

## Routing by Need

Choose the raw-SQL entrypoint by purpose:

- Existing full SQL that should execute as-is and return rows:
  `sqlQuery(...)` / `sqlQueryMap(...)`
- Existing full SQL that should still participate in later `where(...)`,
  `join(...)`, paging, or projection:
  `queryable(rawSql, Entity.class, params)`
- One dialect-specific predicate/order/select/update fragment inside an
  otherwise normal DSL query:
  `rawSQLCommand(...)`, `rawSQLStatement(...)`, `setSQL(...)`
- One missing database function that will be reused:
  wrap it as a small static helper around `rawSQLCommand(...)` or
  `rawSQLStatement(...)`

Do not collapse these into one answer shape.

## Raw SQL API Surface

High-value current-source entrypoints:

```java
easyEntityQuery.sqlQuery(sql, Result.class);
easyEntityQuery.sqlQuery(sql, Result.class, parameters);
easyEntityQuery.sqlEasyQuery(sql, Result.class, sqlParameters);
easyEntityQuery.sqlQueryMap(sql);
easyEntityQuery.sqlExecute(sql);
easyEntityQuery.queryable(rawSql, Entity.class, parameters);
```

Use `sqlQuery(...)` / `sqlQueryMap(...)` / `sqlExecute(...)` when the SQL is
already the final shape.

Use `queryable(rawSql, Entity.class, params)` when the raw SQL should become a
derived table that still participates in later easy-query composition:

```java
EasyPageResult<Topic> page = easyEntityQuery
    .queryable("select * from t_topic where id <> ?", Topic.class, List.of("123"))
    .where(t -> t.title().contains(keyword))
    .orderBy(t -> {
        t.createTime().desc();
        t.id().desc();
    })
    .toPageResult(pageIndex, pageSize);
```

That is usually better than `sqlQuery(...)` followed by Java-side filtering or
manual paging.

If the requirement is not "full SQL already exists", prefer ordinary DSL first.

## Native Fragment Surface

For proxy DSL, current source prefers:

- `rawSQLCommand(...)` for execution-style predicate/order/having fragments
- `rawSQLStatement(...)` for typed fragment expressions that continue with
  `.eq(...)`, `.asInteger()`, `.as(...)`, and similar chaining

Older `expression().sql(...)` / `expression().sqlSegment(...)` forms still
exist, but current source marks them as deprecated compatibility paths. Do not
present them as the default first answer unless the project code already uses
them or the answer specifically needs lower-level context operations such as
`format(...)` / `messageFormat()`.

For weak-typed/property-mode APIs, `sqlNativeSegment(...)` remains the normal
fragment entry.

## Native Predicate Segment

Predicate SQL fragment:

```java
user.expression().rawSQLCommand("{0} != {1}", user.name(), "x");
```

Use placeholders instead of string concatenation. Do not concatenate user input
into SQL fragments.

If you need more control over the fragment context, the lower-level form is:

```java
user.expression().sqlSegment("{0} != {1}", c -> {
    c.expression(user.name());
    c.value("x");
}, Object.class).executeSQL();
```

Prefer that only when the answer truly needs the explicit context object.

## Native Select and Update Segment

Select SQL fragment:

```java
query.select(UserVO.class, user -> Select.of(
    user.expression()
        .rawSQLStatement("IFNULL({0}, {1})", user.age(), 1)
        .asInteger()
        .as(UserVO.Fields.ageOrDefault)
));
```

Proxy assignment:

```java
query.select(user -> new UserVOProxy()
    .ageOrDefault().setSQL("IFNULL({0}, 1)", c -> c.expression(user.age())));
```

Expression update:

```java
easyEntityQuery.updatable(User.class)
    .setColumns(user -> {
        user.version().setSQL("ifnull({0},0)+{1}", c -> {
            c.expression(user.version());
            c.value(1);
        });
    })
    .where(user -> user.id().eq(id))
    .executeRows();
```

State the dialect assumption when using functions like `IFNULL`, `SUBSTR`,
JSON operators, or database-specific date formats.

## Static Wrapper for Unsupported Functions

If the database function is not surfaced by the typed DSL, wrap it once instead
of repeating ad hoc fragments in every query.

Predicate-style helper:

```java
import com.easy.query.api.proxy.util.EasyProxyParamExpressionUtil;
import com.easy.query.core.proxy.PropTypeColumn;
import com.easy.query.core.proxy.core.Expression;

public final class NativeFns {
    private NativeFns() {
    }

    public static void findInSet(String value, PropTypeColumn<String> column) {
        Expression expression =
            EasyProxyParamExpressionUtil.parseContextExpressionByParameters(value, column);
        expression.rawSQLCommand("FIND_IN_SET({0},{1})", value, column);
    }
}
```

Typed fragment helper:

```java
import com.easy.query.api.proxy.util.EasyProxyParamExpressionUtil;
import com.easy.query.core.proxy.PropTypeColumn;
import com.easy.query.core.proxy.core.Expression;
import com.easy.query.core.proxy.extension.functions.type.StringTypeExpression;

public final class NativeFns {
    private NativeFns() {
    }

    public static StringTypeExpression<String> subStr(
        PropTypeColumn<String> column,
        int begin,
        int length
    ) {
        Expression expression =
            EasyProxyParamExpressionUtil.parseContextExpressionByParameters(column);
        return expression
            .rawSQLStatement("SUBSTR({0},{1},{2})", column, begin, length)
            .asStr();
    }
}
```

This is the preferred pattern when the same custom function will appear in
multiple service methods.

Boundary:

- `parseContextExpressionByParameters(...)` needs at least one argument that
  carries easy-query expression context, such as a proxy column/expression
- if every parameter is just a plain Java scalar, it cannot infer the SQL
  context by itself

## Format, Value, and Quote Rules

Native fragments support several parameter modes at the lower-level context
layer:

- `expression(...)`: column, query, predicate, or SQL segment
- `value(...)`: JDBC parameter placeholder
- `format(...)`: literal text inserted through `MessageFormat`
- `messageFormat()`: disable the default keep-style quote escaping

Important boundary from current source:

- default native-fragment formatting uses keep-style handling
- when the SQL template itself contains quoted literals and also contains
  placeholders, single quotes are doubled before `MessageFormat`

So these are valid patterns:

```java
query.where(u -> u.sqlNativeSegment("DATE_FORMAT({0}, ''%Y-%m-%d'')", c -> {
    c.expression(User::getCreateTime);
}));

query.where(u -> u.sqlNativeSegment("DATE_FORMAT({0}, {1})", c -> {
    c.expression(User::getCreateTime);
    c.format("'%Y-%m-%d'");
}));

query.where(u -> u.sqlNativeSegment("DATE_FORMAT(`create_time`, '%Y-%m-%d')"));
```

If the answer depends on fine-grained `format(...)` or `messageFormat()`
behavior, it is acceptable to drop to `sqlNativeSegment(...)` / the older
low-level `sqlSegment(...)` surface instead of forcing `rawSQLStatement(...)`.
