# Transactions

Two ways to get a transaction: easy-query's own `beginTransaction()` (plain Java/Kotlin), and Spring's
`@Transactional` (Spring Boot). Don't combine them in the same method.

## When to use / not

Use when multiple writes must succeed or fail together. Pure reads don't need a transaction. Keep the
transaction boundary at the service layer, aligned with the business operation — not scattered across
repositories or controllers.

## Plain Java/Kotlin — `beginTransaction()` with try-with-resources

`Transaction` (`com.easy.query.core.basic.jdbc.tx`) is `AutoCloseable`. Commit explicitly; if the block exits (exception or early return) before
`commit()`, the transaction auto-rolls-back on `close()`.

```java
import com.easy.query.core.basic.jdbc.tx.Transaction;

try (Transaction tx = easyEntityQuery.beginTransaction()) {
    easyEntityQuery.insertable(order).executeRows();
    easyEntityQuery.updatable(Account.class)
            .setColumns(a -> a.balance().decrement(order.getAmount()))
            .where(a -> a.id().eq(order.getAccountId()))
            .executeRows(1, "扣款失败");      // row-count assertion -> throws -> rollback
    tx.commit();                              // nothing persists without this
}
// exception or missing commit() -> auto rollback on close()
```

Kotlin:
```kotlin
easyEntityQuery.beginTransaction().use { tx ->
    easyEntityQuery.insertable(order).executeRows()
    easyEntityQuery.updatable(Account::class.java)
        .setColumns { it.balance().decrement(order.amount) }
        .where { it.id().eq(order.accountId) }
        .executeRows(1, "扣款失败")
    tx.commit()
}
```

Explicit rollback (when you want to roll back without throwing):
```java
try (Transaction tx = easyEntityQuery.beginTransaction()) {
    // ...
    if (!ok) { tx.rollback(); return; }
    tx.commit();
}
```

`Transaction` methods: `commit()`, `rollback()`, `close()` (auto-rollback if not committed).

## Spring Boot — `@Transactional`

In Spring, prefer declarative transactions. The easy-query operations join Spring's transaction
automatically; do **not** also open `beginTransaction()` inside an `@Transactional` method.

```java
@Service
public class OrderService {
    private final EasyEntityQuery easyEntityQuery;
    public OrderService(EasyEntityQuery easyEntityQuery) { this.easyEntityQuery = easyEntityQuery; }

    @Transactional(rollbackFor = Exception.class)
    public void placeOrder(Order order) {
        easyEntityQuery.insertable(order).executeRows();
        easyEntityQuery.updatable(Account.class)
                .setColumns(a -> a.balance().decrement(order.getAmount()))
                .where(a -> a.id().eq(order.getAccountId()))
                .executeRows(1, "扣款失败");
        // any thrown exception triggers Spring's rollback
    }
}
```

## Guidance

- One transaction per business operation, opened at the service layer.
- Don't put remote calls, file I/O, or long waits inside a transaction.
- A row-count assertion (`executeRows(1, ...)`) inside a transaction is a clean way to abort+rollback on a
  concurrency/state mismatch.
- Don't nest a manual `beginTransaction()` inside `@Transactional` (double transaction).

## Common mistakes

- Forgetting `tx.commit()` → everything silently rolls back.
- Catching and swallowing the exception inside the try block → commit still runs on partial state.
- Mixing Spring `@Transactional` with manual `beginTransaction()`.

## Sources
- 源码验证: `sql-test/.../DirectRelationTest.java`, `.../mysql8/M8Save2Test.java` (try-with-resources +
  commit/rollback); `Transaction` @ `com.easy.query.core.basic.jdbc.tx`.
- 官方文档: `easy-query-doc/src/ability/transaction.md`. Skill baseline 3.2.13.
