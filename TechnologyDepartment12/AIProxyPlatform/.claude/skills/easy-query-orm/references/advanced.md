# Advanced features

Use this file for code-first DDL, sharding, multi-datasource scope, and a
small set of runtime-level advanced APIs.

Do not use this as the main AP/reporting guide. For grouped metrics,
conditional aggregates, CTE/window analytics, or UNION-shaped reports, read
`ap-analytics.md` first.

## Aggregate + groupBy + projection

Group with `GroupKeys.of(...)` (`com.easy.query.core.proxy.sql`), then project keys and aggregates in `select`. Inside the select lambda,
`g.key1()` is the first group key and `g.groupTable()` exposes the grouped columns for aggregate functions.

```java
List<TopicGroupDTO> rows = easyEntityQuery.queryable(Topic.class)
        .where(o -> o.stars().gt(100))
        .groupBy(o -> GroupKeys.of(o.title()))
        .select(g -> {
            TopicGroupDTOProxy r = new TopicGroupDTOProxy();
            r.title().set(g.key1());
            r.cnt().set(g.intCount());                 // COUNT(*)
            r.maxStars().set(g.groupTable().stars().max());
            r.sumStars().set(g.groupTable().stars().sum());
            return r;
        })
        .toList();
// SELECT title, COUNT(*), MAX(stars), SUM(stars) FROM t_topic WHERE stars > ? GROUP BY title
```

Aggregate functions on a grouped column: `.count()` / `.intCount()` / `.sum()` / `.avg()` / `.max()` /
`.min()`. There is also a `Select.DRAFT.of(...)` form from `com.easy.query.core.proxy.sql.Select` to project into a lightweight `DraftN` tuple when you
don't want a dedicated DTO. Non-grouped aggregates (e.g. over a join) use `sumOrNull` / `sumOrDefault` /
`maxOrNull` / `minOrNull` terminals.

## Code-first DDL (auto table sync)

Create/migrate tables from entity classes — handy for tests (see `testing.md`) and bootstrapping.

```java
DatabaseCodeFirst codeFirst = easyEntityQuery.getDatabaseCodeFirst();
codeFirst.createDatabaseIfNotExists();                       // optional: create the schema
codeFirst.syncTableCommand(Arrays.asList(Topic.class, SysUser.class))
         .executeWithTransaction(arg -> {
             System.out.println(arg.sql);                    // inspect the DDL
             arg.commit();
         });
```
`executeWithTransaction(...)` opens easy-query's own transaction (call `arg.commit()`).
`executeWithEnvTransaction(...)` participates in an ambient (e.g. Spring) transaction instead.

## Sharding (dynamic tables)

Annotate the entity with a sharding initializer and mark the shard key; register the initializer at config
time.

```java
@Data
@Table(value = "t_order", shardingInitializer = OrderShardingInitializer.class)
@EntityProxy
public class Order implements ProxyEntityAvailable<Order, OrderProxy> {
    @Column(primaryKey = true)
    @ShardingTableKey                 // the column that decides the shard
    private String id;
    // ...
}

// modulo sharding: t_order_0 / t_order_1
public class OrderShardingInitializer extends AbstractShardingTableModInitializer<Order> {
    @Override protected int mod() { return 2; }          // number of shards
    @Override protected int tailLength() { return 1; }   // suffix length, e.g. _0 .. _1
}
```
Register the initializer on the query configuration during bootstrap:
```java
queryConfiguration.applyShardingInitializer(new OrderShardingInitializer());
```
`@ShardingTableKey` and the `Abstract*ShardingInitializer` base classes are the verified pieces; exact
routing/config (ranges, data-source sharding) is broader — see the sharding docs.

## Multi-datasource

There is no `@UseDataSource` annotation; switching is done through `EasyMultiEntityQuery` (a ThreadLocal
current-datasource model):

```java
public interface EasyMultiEntityQuery extends EasyEntityQuery {
    String getCurrentDataSource();
    void setCurrent(String dataSource);
    EasyEntityQuery getByDataSource(String dataSource);
    <TResult> TResult executeScope(String dataSource, Function<EasyEntityQuery, TResult> fn);
    void clear();
}
```
Prefer the scoped form so the current datasource is always reset:
```java
List<Order> orders = multiEntityQuery.executeScope("ds2", eq ->
        eq.queryable(Order.class).where(o -> o.status().eq(1)).toList());
```

## Common mistakes

- Building a non-grouped aggregate with `groupBy(...).select(...)` machinery — use the `sumOrNull`/`maxOrNull`
  terminals for whole-result aggregates.
- Running code-first `syncTableCommand` without `arg.commit()` → nothing is applied.
- Setting `multiEntityQuery.setCurrent(...)` without a matching `clear()` → leaks the datasource to the next
  task on the same thread; prefer `executeScope(...)`.

## Sources
- 源码验证: `sql-test/.../dameng/DamengQueryTest.java` (groupBy/aggregate), `EntityQueryAggregateTest1.java`
  (sumOrNull/maxOrNull), `h2/domain/ALLTYPESharding.java` + `h2/sharding/AllTYPEShardingInitializer.java` +
  `h2/H2BaseTest.java` (`applyShardingInitializer`). `DatabaseCodeFirst`/`CodeFirstCommand` @
  `com.easy.query.core.basic.api.database`.
- 官方文档: `easy-query-doc/src/ability/select/group.md`, `src/super/*` (sharding),
  `src/guide/sb-multi-datasource.md`, `src/guide/spring-boot.md` (code-first). Skill baseline 3.2.13.
