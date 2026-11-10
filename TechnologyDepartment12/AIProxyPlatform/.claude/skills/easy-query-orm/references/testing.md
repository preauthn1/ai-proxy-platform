# Writing unit tests for easy-query code

Three complementary ways to test, from most to least common in business projects:

1. **Behavior test against H2 in-memory** — run real SQL against an embedded DB, assert the returned data.
2. **SQL-shape assertion with `.toSQL()`** — assert the generated SQL string without touching any DB.
3. **Capture executed SQL via a listener** — the repo's own style (real DB + assert exact SQL + params).

Pick (1) for behavior, (2) for SQL shape without DB setup, and (3) only when
exact SQL+params assertions matter.

## 1. Behavior test — H2 in-memory + code-first DDL

No external database. Build `EasyEntityQuery` over in-memory H2, create tables
via code-first, seed data, then assert behavior.

Dependencies (test scope): `com.easy-query:sql-h2`, `com.h2database:h2`, JUnit, a connection pool.

```java
import com.easy.query.api.proxy.client.DefaultEasyEntityQuery;
import com.easy.query.api.proxy.client.EasyEntityQuery;
import com.easy.query.core.api.client.EasyQueryClient;
import com.easy.query.core.bootstrapper.EasyQueryBootstrapper;
import com.easy.query.h2.config.H2DatabaseConfiguration;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class TopicQueryTest {

    static EasyEntityQuery easyEntityQuery;

    @BeforeAll
    static void setup() {
        HikariDataSource ds = new HikariDataSource();
        // DB_CLOSE_DELAY=-1 keeps the in-memory DB alive for the whole test run
        ds.setJdbcUrl("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setDriverClassName("org.h2.Driver");
        ds.setUsername("sa");
        ds.setPassword("");

        EasyQueryClient client = EasyQueryBootstrapper.defaultBuilderConfiguration()
                .setDefaultDataSource(ds)
                .useDatabaseConfigure(new H2DatabaseConfiguration())   // H2 dialect
                .build();
        easyEntityQuery = new DefaultEasyEntityQuery(client);

        // code-first: create tables from the entity classes
        easyEntityQuery.getDatabaseCodeFirst()
                .syncTableCommand(Arrays.asList(Topic.class))
                .executeWithTransaction(arg -> arg.commit());

        // seed
        Topic t = new Topic();
        t.setId("1"); t.setStars(150); t.setTitle("hello");
        easyEntityQuery.insertable(t).executeRows();
    }

    @Test
    void filters_by_stars() {
        var list = easyEntityQuery.queryable(Topic.class)
                .where(o -> o.stars().gt(100))
                .toList();
        assertEquals(1, list.size());
        assertEquals("hello", list.get(0).getTitle());
    }

    @Test
    void update_changes_row() {
        long rows = easyEntityQuery.updatable(Topic.class)
                .setColumns(o -> o.title().set("world"))
                .where(o -> o.id().eq("1"))
                .executeRows();
        assertEquals(1, rows);
        var t = easyEntityQuery.queryable(Topic.class).whereById("1").singleOrNull();
        assertEquals("world", t.getTitle());
    }
}
```

`getDatabaseCodeFirst().syncTableCommand(...).executeWithTransaction(...)` is
the verified code-first entry.

## 2. SQL-shape assertion — `.toSQL()` (no DB)

Every query/write chain can render its SQL without executing. Great for fast, DB-free tests of the SQL shape.

```java
String sql = easyEntityQuery.deletable(Topic.class).whereById("999").toSQL();
assertEquals("DELETE FROM `t_topic` WHERE `id` = ?", sql);

String q = easyEntityQuery.queryable(Topic.class)
        .where(o -> o.stars().gt(100))
        .orderBy(o -> o.id().asc())
        .toSQL();
// assert q contains the WHERE / ORDER BY you expect
```
This still needs an `EasyEntityQuery` instance, but no data and no execution.

## 3. Capture executed SQL + params via a listener (repo style)

The framework's own test suite (`sql-test`) asserts the exact executed SQL and parameters by registering a
`JdbcExecutorListener` and capturing each execution. This needs a small test helper. The pattern, verbatim
from the repo:

```java
// register a custom listener when building the client:
//   .replaceService(JdbcExecutorListener.class, myJdbcListener)
// then, around the call under test:
ListenerContext listenerContext = new ListenerContext();
listenerContextManager.startListen(listenerContext);

easyEntityQuery.queryable(SysUser.class).where(s -> s.id().eq("123xxx")).firstOrNull();

JdbcExecuteAfterArg arg = listenerContext.getJdbcExecuteAfterArg();
Assert.assertEquals(
    "SELECT `id`,`create_time`,`username`,`phone`,`id_card`,`address` FROM `t_sys_user` WHERE `id` = ? LIMIT 1",
    arg.getBeforeArg().getSql());
Assert.assertEquals("123xxx(String)",
    EasySQLUtil.sqlParameterToString(arg.getBeforeArg().getSqlParameters().get(0)));
listenerContextManager.clear();
```

`ListenerContext`, `ListenerContextManager`, and `MyJdbcListener` are repo test
helpers, not public API.

## Choosing

- Default to **§1 (H2 behavior)** — it tests what the code actually does and needs no external services.
- Use **§2 (`.toSQL()`)** for fast checks that the SQL shape is right (e.g. confirming a dynamic filter was
  applied) without seeding data.
- Use **§3 (listener)** to mirror the framework's own exact-SQL+params assertions.

## Common mistakes

- H2 without `DB_CLOSE_DELAY=-1`
- missing `syncTableCommand(...)`
- dialect mismatch (`H2DatabaseConfiguration` vs MySQL dialect)
- treating repo listener helpers as public API
