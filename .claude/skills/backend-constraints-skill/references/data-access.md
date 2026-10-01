# MyBatis, PostgreSQL, and Redis

Read this when working with databases, caching, query performance, or data migration.

Environment facts (user decision 2026-08-30): PostgreSQL 18, database `Blocbuy`, schema `domain` (set via JDBC `currentSchema=domain` / Flyway `defaultSchema`); Redis 8.10, keys prefixed `blocbuy:{env}:...`. Credentials come from environment variables — never commit them. Elasticsearch is NOT in the stack; the ES section at the bottom is kept for future reference only.

## Queries in Loops Are Forbidden; Batch Pattern

Mapper/Repository/remote queries must not appear inside `for/while/do` loops, Stream `map/forEach` callbacks, recursion, per-item Futures/virtual threads, or loop-invoked helper methods. Correct pattern:

```java
Set<Long> productIds = orders.stream()
        .map(Order::productId)
        .collect(Collectors.toSet());
Map<Long, Product> products = productRepository.findByIds(productIds).stream()
        .collect(Collectors.toMap(Product::id, Function.identity()));
orders.forEach(order -> order.attachProduct(products.get(order.productId())));
```

- Return early on empty collections to avoid generating `IN ()`; deduplicate IDs; for very large sets, chunk by the database parameter limit and measured performance - but chunked queries must be centralized inside the infrastructure batch method and monitored, not scattered as N separate calls in business code.
- Batch writes use MyBatis batch/multi-value insert or whatever the database handles well, controlling batch size and transaction memory.
- MyBatis `resultMap` nested selects that cause implicit N+1 are forbidden; use a second batch query or a deliberate join.
- If the requirement is naturally streaming, process in fixed-size batches with pagination/cursors and a fixed number of queries per batch; record batch size, totals, and the failure-recovery point.
- The approved exceptions are the team-expiry pattern and the explicitly requested #225 intent-expiry pattern in backend ADR 0001. `GroupDealExpirySweeper` (backend ADR 0002): one bounded candidate-ID query followed by a separate `GroupDealExpiryService.expireOne` transaction for each aggregate. One team's failure must not roll back the whole batch. These exceptions do not cover other jobs, collection enrichment or remote calls; obtain explicit user approval and record bounds, lock/write protocol, failure isolation and recovery before introducing another exception.

## MyBatis

- All SQL parameters use `#{}` binding. `${}` is allowed only for table/column/sort identifiers the framework cannot bind, and the value must come from a server-side enum whitelist - never raw user input.
- Mapper methods with multiple parameters use explicit `@Param`; complex conditions use a Query object. Method names express the action and result scale, e.g. `selectById`, `selectByIds`, `updateStatusIfVersion`.
- Production SQL names columns explicitly; `SELECT *` is forbidden; aliases and `resultMap` map snake_case/camelCase clearly. Persistence DOs do not cross the infrastructure layer.
- Dynamic SQL uses the safe tags `<where>`, `<set>`, `<foreach>`; never concatenate SQL strings. Optional conditions must be protected against full-table scans.
- Updates/deletes always carry an explicit WHERE; writes expecting a single row check the affected row count. Zero rows distinguishes not-found, version conflict, or state conflict, mapped to the corresponding error code.
- Paginated queries keep count and data conditions consistent; prefer keyset for deep pagination on large tables. Sorting includes a unique stable tie-breaker.
- Batch SQL must not bypass domain invariants; select only necessary columns; avoid pulling large objects and unbounded results into memory.
- Dev/test may log slow SQL with a parameter digest; production logs must mask data and never print huge parameter lists.

## PostgreSQL

- Table/column names use lowercase snake_case; the primary key strategy is unified. Follow the [team PostgreSQL DDL skill](../../pgsql-ddl/SKILL.md): structural `PRIMARY KEY`, `UNIQUE`, and `NOT NULL` constraints only; never add foreign keys or `CHECK` constraints. Validate business rules and reference consistency in the application with an explicit concurrency protocol, using unique indexes and status/version guards where appropriate.
- Instants use `timestamptz` with a unified UTC convention; money uses `numeric(precision, scale)` or an explicit smallest-unit integer; do not casually stuff structured, queryable data into JSONB.
- Indexes are driven by real queries and selectivity; consider composite index column order, partial indexes, and covering indexes. Every index costs write amplification - "index every column" is forbidden.
- Validate slow queries with `EXPLAIN (ANALYZE, BUFFERS)` at realistic data volumes; never judge by SQL appearance alone. Monitor sequential scans, lock waits, bloat, and connection pools.
- The default Read Committed does not guarantee the same snapshot across two queries. Make the concurrency model explicit for flash-sale inventory, group completion, and payment status; prefer atomic conditional updates, unique constraints, and version numbers, with row locks when necessary. Acquire locks in a fixed order to reduce deadlocks.
- Keep transactions short; long `idle in transaction` sessions are forbidden. Size connection pools with database capacity; raising the connection count is never the first optimization.
- All schema changes go through versioned migrations; DDL on large tables considers locks, backfills, dual-write/compatibility windows, and rollback. Applications become compatible with the new schema first, then destructive cleanup happens.
- Soft delete is used only when the business genuinely needs audit/recovery; the risk of queries missing the deleted condition must be controlled by a unified strategy and tests. Critical transaction flows prefer immutable audit records.

## Redis

- Redis holds caches, rate limiting, short-lived sessions, or derived state - it is never the sole source of truth for orders/payments/inventory.
- Keys follow `blocbuy:{env}:{domain}:{type}:{id}`, with a defined owner, TTL, maximum cardinality, and cleanup policy; temporary keys without a TTL are forbidden.
- Cache-aside: on read miss, load from source; after a write transaction commits, delete/invalidate the cache; accept and document brief inconsistency. For hot keys use mutex rebuild/logical expiration/early refresh; add jitter to TTLs to prevent avalanches; short-TTL null values prevent penetration.
- Cache values carry a schema/version or evolve safely; avoid Java native serialization. Large keys, hot keys, dangerous whole-key commands, and blocking Lua must be governed.
- Distributed locks require a unique owner token, atomic acquisition and atomic validated release, a bounded lease with timeout; the database still backstops the business. A Redis lock is never proof of absolute correctness.
- Rate-limiter failure policy is fail-open or fail-closed by risk; login, payment, and inventory endpoints prioritize safety and record metrics.

## Elasticsearch — DEFERRED (not in the stack)

> Elasticsearch was removed from the v1 stack on 2026-08-30. The rules below apply only if the user explicitly approves introducing ES later; until then, do not add ES clients, ports, or sync plumbing.

- Use the official strongly-typed Java API Client aligned with the server's major/minor version compatibility policy; building new code on the deprecated TransportClient/old High Level REST Client is forbidden.
- PostgreSQL is the source of truth; ES is a rebuildable read model. Sync asynchronously via Outbox/CDC/reliable events; documents carry a business version, and consumers reject older versions overwriting newer ones.
- Mappings define keyword/text/numeric/date explicitly; money, IDs, and statuses do not rely on dynamic mapping. For Chinese text retrieval, pin the analyzer and dictionary versions; switch via new index + alias atomically.
- Writes use Bulk with bounded batch bytes/counts/concurrency; check partial failures per item and retry. Never request per-document in a loop.
- Queries bound `from/size` and aggregation cardinality; deep pagination uses PIT + `search_after` with stable sorting. User input never becomes raw query_string scripts or arbitrary fields.
- Search results are for candidate discovery; before placing an order, re-verify current price, inventory, product/group status, and permissions against PostgreSQL.
- Record query latency, timeouts, rejections, bulk failures, sync lag, and index versions; provide full rebuild and verification mechanisms.
