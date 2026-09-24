# Team-expiry sweeper — implementation plan (PR-G1, Liu Jin)

Date: 2026-09-23 · Branch: `feature/group-expiry-20260923` · Issue: #28 (UC5) · Parent plan: [`lifecycle-completion-plan-20260922.md`](lifecycle-completion-plan-20260922.md) §3.3

PR-G in the parent plan bundled the scheduling foundation, the team-expiry sweeper and the refund worker. It is split in two:

| PR | Content | Closes |
| --- | --- | --- |
| **PR-G1 (this plan)** | scheduler lease + team-expiry sweeper + scheduling driver | #28 |
| PR-G2 (next) | refund worker on the same lease and driver, `@Profile("stripe-sprint2")` | #29 |

Why split: G1 writes only to our database; G2 is the only job that moves money through Stripe. Reviewing them apart keeps attention on the money path, and G1 on its own is safe — the refund obligations it records stay `pending` until G2's worker claims them (`RefundMapper.claimNext` picks up any due row, whenever it was written).

## 1. Scope

### 1.1 In scope

1. A database lease on `sys_scheduler_lock` (V13), as a domain port with an infrastructure implementation.
2. `GroupDealExpirySweeper`: one batch read of due team ids, then `GroupDealExpiryService.expireOne` per team with per-team failure isolation.
3. A bootstrap scheduling driver behind a master switch that is **off by default**, on in the `ec2` and `docker` profiles.
4. Metrics, one summary log line per non-empty run, shutdown behaviour.
5. Tests (unit + Testcontainers IT), ADR 0002 addendum, NFR-32 row, sprint-log entry.

### 1.2 Out of scope

| Item | Where it goes |
| --- | --- |
| Scheduling the refund worker (`RefundService.processNext`) | PR-G2 / #29 |
| `expireDueSessions` head-of-line blocking and crowd-out | #134 (payment session cleanup, a different job) |
| Quarantining a team that fails expiry permanently | §8 risk R2; needs a column and a migration, follow-up if R2 is observed |
| Frontend display of expired teams or refunds | #128 / #31 |
| Any HTTP API change | none — `contracts/openapi.json` stays unchanged |

### 1.3 #28 acceptance criteria after this PR

| Criterion | Status on `dev` today | After G1 |
| --- | --- | --- |
| `success` on quota, `expired` on deadline | success ✅ (#148); expired only when someone calls `expireOne` | ✅ expired by the sweeper |
| Illegal transitions rejected | ✅ `DealStatus.canTransitionTo`, `GroupDeal.expire` guards (#148) | unchanged |
| Events published; consequences in subscribers | ✅ `TeamExpired` → `OrderLifecycleSubscriber` / `TeamExpiryRefundSubscriber` (#148) | unchanged |
| **Scheduled/timeout resolution path covered** | ❌ no caller in `src/main` | ✅ this PR |
| Unit tests for success / expiry / illegal transitions | ✅ `GroupDealTest`, `GroupDealExpiryServiceTest` | + sweeper and driver tests |

## 2. Baseline (measured on `dev` 7a6d7e6)

Already present, reused as is:

| Piece | Location | Note |
| --- | --- | --- |
| `PaidGroupDealRepository.dueForExpiry(now, limit)` | `blocbuy-domain/.../groupbuy/repository/PaidGroupDealRepository.java:63` | `status='open' AND expire_time <= now ORDER BY expire_time LIMIT n`; covered by `PaidGroupDealRepositoryIT` |
| `PaidGroupDealRepository.currentTime()` | same file `:27` | `SELECT clock_timestamp()` |
| Index for the due query | `V5__groupbuy.sql:55` `idx_biz_group_deal_status_expire (status, expire_time) WHERE deleted = false` | no new index |
| `GroupDealExpiryService.expireOne(UUID)` | `blocbuy-application/.../groupbuy/service/GroupDealExpiryService.java:43` | `@Transactional(timeout = 10)`; lock → DB clock → `GroupDeal.expire` → status-guarded UPDATE → `TeamExpired`; returns false on replay |
| Subscribers | `OrderLifecycleSubscriber` (order 10), `TeamExpiryRefundSubscriber` (order 20) | `BEFORE_COMMIT`, same transaction |
| `sys_scheduler_lock` table | `V13__scheduler_lock.sql` | partial unique `uq_sys_scheduler_lock_key (lock_key) WHERE deleted = false`; **no code uses it** |
| Micrometer + Prometheus | `blocbuy-bootstrap/pom.xml:46-50` | bootstrap classpath only; no `MeterRegistry` use yet |
| Trace MDC key | `ApiConstants.TRACE_ID_MDC_KEY = "traceId"` (`blocbuy-client`) | set per HTTP request by `TraceIdFilter`; scheduled runs have none |
| Shutdown | `server.shutdown: graceful`; `stop_grace_period: 30s` in `deployment/backend/compose.yml:32` | |

Missing: any `@EnableScheduling` / `@Scheduled`, any lease code, any sweep loop.

Deployment fact: dev runs **one** EC2 instance today. The lease is still required: a redeploy (`force-recreate`) can briefly run two containers, and V13 was designed for two instances behind the ALB.

Test hazard found: `StripeLocalIntegrationTest` (`@ActiveProfiles({"local", "stripe-sprint2"})`) makes teams overdue and then calls `expiry.expireOne(deal)` and asserts `isTrue()` (lines 533, 553). A scheduler running in that context could expire the team first and turn those tests flaky. **The scheduler must not start in any test context unless the test enables it.** 13 `@SpringBootTest` classes exist; none should need a change.

## 3. Design

### 3.1 Layer placement

| Layer / module | Package | New type | Responsibility |
| --- | --- | --- | --- |
| domain | `com.blocbuy.common.repository` | `SchedulerLeaseRepository` (port) | lease contract, JDK types only (covered by ArchUnit `DOMAIN_PORTS`) |
| infrastructure | `com.blocbuy.common.persistence` | `SchedulerLeaseRepositoryImpl`, `SysSchedulerLockMapper` + `mapper/common/SysSchedulerLockMapper.xml` | SQL on `sys_scheduler_lock` |
| application | `com.blocbuy.groupbuy.service` | `GroupDealExpirySweeper` (+ nested record `Result`) | batch read + per-team isolation; **not** `@Transactional` |
| bootstrap | `com.blocbuy.bootstrap.scheduling` | `SchedulingProperties`, `LeasedJobRunner`, `TeamExpirySweepJob` | lease wrap, metrics, MDC, timer |
| bootstrap | `com.blocbuy.bootstrap.config` | `SchedulingConfiguration` | `@EnableScheduling` + beans, behind the master switch |

No new domain package, no migration, no API change. The lease is technical infrastructure shared by jobs, hence `common`, not `groupbuy`.

### 3.2 Lease port and SQL

```java
package com.blocbuy.common.repository;

/** Single-flight lease for scheduled jobs; PostgreSQL is the lock of record (V13). */
public interface SchedulerLeaseRepository {
    /** @return true when this owner now holds {@code lockKey} until DB-now + lease */
    boolean tryAcquire(String lockKey, String owner, Duration lease);

    /** @return false when the lease was no longer ours (taken over after it expired) */
    boolean release(String lockKey, String owner);
}
```

Acquire is **one statement**, evaluated entirely on the database clock:

```sql
INSERT INTO sys_scheduler_lock (lock_key, owner, locked_until)
VALUES (#{lockKey}, #{owner}, clock_timestamp() + make_interval(secs => #{leaseSeconds}))
ON CONFLICT (lock_key) WHERE deleted = false
DO UPDATE SET owner        = EXCLUDED.owner,
              locked_until = EXCLUDED.locked_until,
              update_time  = clock_timestamp(),
              version      = sys_scheduler_lock.version + 1
WHERE sys_scheduler_lock.locked_until <= clock_timestamp()
   OR sys_scheduler_lock.owner = EXCLUDED.owner
```

- Affected rows: 1 = acquired (first ever run inserts, later runs update); 0 = another owner holds a live lease → skip this run.
- Two instances racing: `ON CONFLICT DO UPDATE` row-locks the conflicting row and re-evaluates the `WHERE` on the committed version, so exactly one wins; the loser waits only for a single-statement transaction (milliseconds), then gets 0.
- `OR owner = EXCLUDED.owner` lets the same instance re-acquire after a failed release.
- `id` comes from the column default `uuidv7()`; `create_by`/`update_by` stay NULL (system actor).

Release:

```sql
UPDATE sys_scheduler_lock
SET locked_until = clock_timestamp(), update_time = clock_timestamp(), version = version + 1
WHERE lock_key = #{lockKey} AND owner = #{owner} AND deleted = false
```

0 rows means the run outlived its lease and someone took over → WARN (see §4, overlap is safe).

Both statements carry a JDBC statement timeout in the mapper XML, `timeout="3"` (seconds). Without it, a session that holds the lease row locked for a long time (for example a manual `SELECT ... FOR UPDATE` in psql left uncommitted) would block the upsert indefinitely and, with it, the single default scheduler thread. With it, PostgreSQL cancels the statement after 3 s (SQLState `57014`), Spring raises a `DataAccessException`, and `LeasedJobRunner` counts the run as `failed`; the next run tries again. This restores the fail-fast property NOWAIT would have given, bounded at 3 s.

Implementation guards (technical misuse, `IllegalArgumentException`, not `BizException`): `lockKey` non-blank and ≤ 64 chars, `owner` non-blank and ≤ 128 chars (V13 column sizes), `lease` positive. Any other row count than 0/1 → `IllegalStateException`.

**Deviation from V13's comment, recorded in ADR 0002 (decided 2026-09-23):** V13 describes `SELECT ... FOR UPDATE NOWAIT` then UPDATE. Both designs are leases: the row lock is held only for the acquire statement, never for the sweep, and `locked_until` is what excludes other instances. NOWAIT's fail-fast therefore only covers a millisecond race, where both designs end the same way (the loser skips the run). The single upsert meets V13's obligations (no queueing behind a sweep, stale takeover under the row lock, one row per key) with one autocommit statement instead of three statements in an explicit transaction, no `55P03` → `CannotAcquireLockException` mapping, the "expired or ours" decision made atomically in SQL rather than in Java, and about half the branches to cover. NOWAIT's one real advantage — returning at once when another session holds the row lock for a long time — is recovered by the 3 s statement timeout below. V13 itself cannot be edited (Flyway checksum).

### 3.3 Sweeper

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDealExpirySweeper {
    private final PaidGroupDealRepository deals;
    private final GroupDealExpiryService expiry;

    public record Result(int due, int expired, int alreadyResolved, int failed) {}

    /** Not @Transactional on purpose: each team commits or rolls back on its own. */
    public Result sweep(int limit) {
        // limit < 1 → IllegalArgumentException
        Instant now = deals.currentTime();            // DB clock, same source expireOne uses
        List<UUID> due = deals.dueForExpiry(now, limit);   // one batch read
        for (UUID id : due) {
            try {
                if (expiry.expireOne(id)) expired++; else alreadyResolved++;
            } catch (RuntimeException e) {
                failed++;
                log.warn("Team {} expiry failed; the next run retries it", id, e);
            }
        }
        return new Result(due.size(), expired, alreadyResolved, failed);
    }
}
```

- **No outer transaction.** If `sweep` were `@Transactional`, `expireOne`'s `REQUIRED` would join it; one failing team would mark the whole batch rollback-only. The IT in §6.2 proves isolation against a real database.
- **Hard-gate exception, stated explicitly:** the loop issues one `lockDeal` + one UPDATE (+ subscriber writes) per team. This is a write job that locks one aggregate per transaction, not read amplification; the ids come from one batch query. Merging into one transaction is rejected in ADR 0002 (rollback blast radius, lock hold time against webhook settlement).
- **Clock consistency:** candidates use DB time `t1`; `expireOne` re-reads DB time `t2 ≥ t1` under the row lock, so a due team is still due and `GroupDeal.expire`'s "not due yet" `IllegalStateException` cannot fire from clock skew. `currentTime()`'s Javadoc ("call only after required locks") concerns decisions; this call only selects candidates, and the decision is re-made under the lock. Update the Javadoc to say so.
- `expireOne` returning false (webhook filled the team, or another sweeper got there) is counted as `alreadyResolved`, not failure.
- A failing team stays `open` and first in `ORDER BY expire_time`, so the next run retries it; it consumes one slot per run but does not block the others (contrast #134).

### 3.4 Driver

`SchedulingProperties` (`@ConfigurationProperties("blocbuy.scheduling")`, `@Validated`, record):

| Property | Default | Constraint |
| --- | --- | --- |
| `enabled` | `false` | master switch |
| `team-expiry.interval` | `PT1M` | fixed delay between runs |
| `team-expiry.initial-delay` | `PT30S` | lets the app finish starting |
| `team-expiry.lease` | `PT2M` | > 0 |
| `team-expiry.batch-size` | `100` | 1..500 |

`SchedulingConfiguration`:

```java
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
@ConditionalOnProperty(prefix = "blocbuy.scheduling", name = "enabled", havingValue = "true")
public class SchedulingConfiguration {
    @Bean LeasedJobRunner leasedJobRunner(SchedulerLeaseRepository leases, MeterRegistry meters) { ... }
    @Bean TeamExpirySweepJob teamExpirySweepJob(LeasedJobRunner runner, GroupDealExpirySweeper sweeper,
                                                SchedulingProperties props, MeterRegistry meters) { ... }
}
```

`TeamExpirySweepJob.run()`:

```java
@Scheduled(fixedDelayString = "${blocbuy.scheduling.team-expiry.interval:PT1M}",
           initialDelayString = "${blocbuy.scheduling.team-expiry.initial-delay:PT30S}")
public void run() {
    runner.run("team-expiry-sweep", props.teamExpiry().lease(),
               () -> sweeper.sweep(props.teamExpiry().batchSize()));   // + per-result counters
}
```

`LeasedJobRunner.run(key, lease, work)` — reused unchanged by G2:

1. Put `traceId = "job-" + UuidV7.next()` into MDC (so CloudWatch Logs Insights can group one run's lines).
2. `tryAcquire`; false → counter `outcome=skipped`, DEBUG, return.
3. Run the work inside `try`; any exception → counter `outcome=failed`, ERROR once (the sweeper already isolates per team, so this is only a lease/DB-level failure). A `DataAccessException` from `tryAcquire` itself (including the 3 s statement timeout) is caught the same way, counted `failed`, and does not attempt a release.
4. `finally` release; false → WARN "lease expired before the run finished"; clear MDC.
5. Owner id: `HOSTNAME` env (container id on EC2) + `/` + a per-JVM `UuidV7`, truncated to 128.

Logging: INFO once per run **only when `due > 0`** (`due, expired, alreadyResolved, failed, tookMs`); DEBUG otherwise, so an idle system does not write one INFO line a minute.

Metrics (low-cardinality tags only, no ids):

| Meter | Type | Tags |
| --- | --- | --- |
| `blocbuy.scheduler.runs` | counter | `job`, `outcome` = completed / skipped / failed |
| `blocbuy.scheduler.run.duration` | timer | `job` |
| `blocbuy.team.expiry.teams` | counter | `result` = expired / already_resolved / failed |

### 3.5 Configuration per profile

| File | Change |
| --- | --- |
| `application.yml` | `blocbuy.scheduling.enabled: false` plus the `team-expiry.*` defaults; `spring.task.scheduling.shutdown.await-termination: true`, `await-termination-period: 20s` (< 30 s `stop_grace_period`) |
| `application-ec2.yml` | `blocbuy.scheduling.enabled: ${BLOCBUY_SCHEDULING_ENABLED:true}` |
| `application-docker.yml` | same as ec2 |
| `application-local.yml` | `blocbuy.scheduling.enabled: ${BLOCBUY_SCHEDULING_ENABLED:false}` — tests run with `local`, so it stays off there |
| `application-openapi-export.yml` | nothing; the default `false` keeps the "no DB connection" promise of that profile |

A shutdown mid-run is safe: each `expireOne` is its own transaction, a killed one rolls back, and an unreleased lease expires after `lease` (2 min delay at worst).

## 4. Funds safety and concurrency

Required by the backend constraints for anything that leads to refunds.

| Question | Answer |
| --- | --- |
| Database constraints | `uq_sys_scheduler_lock_key` (one lease row per job); `biz_group_deal` status-guarded UPDATE; `UNIQUE (payment_id)` on `biz_refund` (V9); `uq_biz_order_payment_id` |
| Locks and lock order | sweeper → `expireOne` locks `biz_group_deal` first, same as webhook settlement; subscribers follow team → intent → order → payment (ADR 0002). The lease row is committed and released before any team is locked, so it is never held together with a business row lock |
| Idempotency | a replayed or concurrent expiry matches 0 rows under `status = 'open'` → `expireOne` returns false; order cancel and refund upsert are status-guarded / unique-keyed |
| Overlap (lease lost mid-run, or a second container during deploy) | harmless for correctness: both runs go through the same status guard, at most one expires each team. The lease exists for efficiency and log clarity, not correctness |
| Failure recovery | a failed team stays `open` and is retried next run; a failed run leaves nothing half-written (per-team transactions); a crashed holder's lease expires after 2 min |
| Money movement in G1 | none. Refund obligations are written `pending`; only G2's worker calls Stripe |

## 5. Decisions and rejected alternatives

| Decision | Alternative rejected | Why |
| --- | --- | --- |
| Single-statement upsert lease with a 3 s statement timeout (decided 2026-09-23) | V13's `SELECT ... FOR UPDATE NOWAIT` + UPDATE | same guarantees, one autocommit statement, no `CannotAcquireLockException` mapping, decision atomic in SQL, fewer branches; the timeout bounds the one case where NOWAIT would return sooner |
| Master switch off by default, on per deploy profile | on by default, disabled in tests | 13 existing `@SpringBootTest` contexts and `StripeLocalIntegrationTest`'s manual `expireOne` would race a live scheduler; opt-in keeps openapi-export DB-free |
| Lease helper in bootstrap (`LeasedJobRunner`) | lease logic inside the sweeper | the sweeper stays a pure use case; G2 reuses the runner unchanged |
| DB clock for due selection and lease | JVM `Instant.now()` | instances and DB can skew; `expireOne` already decides on DB time |
| Redis lock | — | ruled out by V13 and the stack rule: a lock that leads to refunds is business state, PostgreSQL only |
| ShedLock or Quartz | — | a new dependency for one table we already own; not in the locked stack |
| Batch 100 / interval 1 min | larger batch | a backlog of *n* overdue teams clears in ⌈n/100⌉ minutes; each `expireOne` holds one row lock for milliseconds |

## 6. Tests

Target: 0 missed lines and 0 missed branches in the JaCoCo aggregate for every touched file.

### 6.1 Unit

| Class | Cases |
| --- | --- |
| `SchedulerLeaseRepositoryImplTest` (mock mapper) | 1 row → true; 0 rows → false; unexpected count → `IllegalStateException`; each argument guard; release true/false |
| `GroupDealExpirySweeperTest` | nothing due → zeroes and no `expireOne`; mixed true/false; **three teams where the second throws → other two resolved, `failed = 1`**; `limit < 1` rejected; `dueForExpiry` receives the DB time |
| `LeasedJobRunnerTest` | not acquired → work not run, `skipped`; acquired → work run, released, `completed`; work throws → released, `failed`, not rethrown; **`tryAcquire` throws `DataAccessException` → work not run, no release, `failed`, not rethrown**; release false → WARN branch; MDC set during the run and cleared after |
| `TeamExpirySweepJobTest` | passes key/lease/batch from properties; INFO vs DEBUG branch; counters per result |
| `SchedulingPropertiesTest` | defaults; batch outside 1..500 and non-positive lease rejected |

### 6.2 Integration (Testcontainers PostgreSQL 18)

| Test | Proves |
| --- | --- |
| `SchedulerLeaseRepositoryIT` (infrastructure, V13 only, same raw `SqlSessionFactory` setup as `PaidGroupDealRepositoryIT`) | first acquire inserts; second owner blocked while live; takeover after `locked_until` passes; same owner re-acquires; release of a foreign lease returns false; **N threads acquire concurrently → exactly one true**; **a second connection holds the lease row with `SELECT ... FOR UPDATE` in an open transaction → `tryAcquire` throws `DataAccessException` within ~3 s instead of hanging** (asserted with `assertTimeoutPreemptively(Duration.ofSeconds(10), ...)`), and succeeds once that transaction ends |
| `TeamExpirySchedulingIT` (bootstrap, `blocbuy.scheduling.enabled=true`, `initial-delay=PT1H` so the timer never fires, calls `job.run()` directly; own container like `StripeLocalIntegrationTest`) | paid overdue team → after one run: deal `expired`, order `cancelled`, one `biz_refund` `pending`; a second run changes nothing; an open, not-yet-due team is untouched; a team whose expiry throws does not stop the others (real transactions, no outer rollback); a held foreign lease makes the run skip |
| `BlocBuyApplicationTest` (existing) | one added assertion: no `TeamExpirySweepJob` bean when the switch is absent |

## 7. Files

New (main): `SchedulerLeaseRepository.java`, `SchedulerLeaseRepositoryImpl.java`, `SysSchedulerLockMapper.java`, `mapper/common/SysSchedulerLockMapper.xml`, `GroupDealExpirySweeper.java`, `SchedulingConfiguration.java`, `SchedulingProperties.java`, `LeasedJobRunner.java`, `TeamExpirySweepJob.java`.

Changed (main): `application.yml`, `application-ec2.yml`, `application-docker.yml`, `application-local.yml`, `PaidGroupDealRepository.java` (Javadoc of `currentTime` only).

New/changed (test): the seven test classes in §6.

Docs: this plan; `backend/docs/adr/0002-team-lifecycle-events.md` addendum (scheduler, lease upsert vs V13 NOWAIT, overlap argument); `docs/quality/nfr-register.md` **NFR-32** (availability/funds safety: an overdue open team is resolved within interval + ⌈backlog/batch⌉ minutes, single-flight across instances, one failing team does not block the rest; evidence = the two ITs); `docs/agile/sprint-02/log/2026-09-23.md` entry.

Size: about 13 main/config files and 7 test files; roughly 400 lines of main code and 500 of tests.

## 8. Risks

| # | Risk | Mitigation |
| --- | --- | --- |
| R1 | First run on dev expires a backlog of old seeded or abandoned open teams at once | before enabling, count them: `SELECT count(*) FROM domain.biz_group_deal WHERE deleted = false AND status = 'open' AND expire_time <= now();` (SSM port-forward, `docs/deploy/backend-log-observability.md`). Batch 100/min bounds the rate. Expiring them is correct: browse already hides open-but-overdue teams |
| R2 | A team whose expiry always throws (e.g. inconsistent payment rows) is retried every minute forever, WARN each time | `blocbuy.team.expiry.teams{result=failed}` counter + WARN with deal id; a quarantine column is a follow-up only if it happens |
| R3 | Refund obligations pile up as `pending` until PR-G2 lands | intended; they are the durable record G2 drains. State it in the PR description |
| R4 | The single default scheduler thread will be shared with G2's worker | G2 sets `spring.task.scheduling.pool.size: 2`; not needed for one job |
| R5 | Someone enables the switch in a shared test context | only `TeamExpirySchedulingIT` sets it, with a 1 h initial delay |
| R6 | A long-lived row lock on the lease row (manual psql session, stuck transaction) blocks the upsert and freezes the single scheduler thread | `timeout="3"` on both lease statements (§3.2): the run fails after 3 s, counted `blocbuy.scheduler.runs{outcome=failed}`, and the next run retries; covered by `SchedulerLeaseRepositoryIT` |

## 9. Commit sequence

1. `docs(agile): team-expiry sweeper plan` — this file.
2. `feat(common): database scheduler lease on sys_scheduler_lock` — port, impl, mapper, unit test, IT.
3. `feat(groupbuy): team-expiry sweeper with per-team isolation` — sweeper + unit test, `currentTime` Javadoc.
4. `feat(bootstrap): opt-in scheduling driver for team expiry` — properties, runner, job, configuration, yml, unit tests.
5. `test(bootstrap): team-expiry scheduling end to end` — `TeamExpirySchedulingIT`, `BlocBuyApplicationTest` assertion.
6. `docs: ADR 0002 scheduler addendum, NFR-32, sprint log`.

PR description: `Closes #28`; `Refs #29` (G2 follows).

## 10. Acceptance gates

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
cd backend && ./mvnw -B verify
```

- Spotless, Checkstyle (0), SpotBugs, all ArchUnit rules green.
- JaCoCo aggregate (`blocbuy-bootstrap/target/site/jacoco-aggregate/jacoco.xml`): 0 missed lines/branches in touched files (script in the backend constraints skill).
- `git diff --exit-code -- contracts/` clean (no API change expected).
- After merge and deploy to dev: CloudWatch `/blocbuy/backend/dev` shows the INFO summary line for the first non-empty run, and the R1 count query returns 0 within ⌈backlog/100⌉ + 1 minutes.
