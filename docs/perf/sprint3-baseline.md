# Sprint 3 performance baseline (US23, #180)

Owner: Eric Wang (@EricWang1358). Reviewer: @PPsyrius. This is the Sprint 2 carry-over governed by [Sprint 3 container #232](https://github.com/PPsyrius/iss-groupbuy-team8/issues/232).

## Decision record — 2026-09-30

Use an isolated local stack with test-only Sa-Token sessions in real Redis. The existing production HTTP endpoints and PostgreSQL mappers are exercised by a dependency-free Node closed-loop workload with 100 VUs per scenario. The alternative, issuing 100 Google sessions against deployed dev, needs shared-environment rollout and data (#222/#223) and would not be reproducible yet. No deployed login bypass is introduced. Run instructions, seed volume, timing and measurement definitions are in [backend/perf/README.md](../../backend/perf/README.md).

## Result — 2026-09-30

All three actions met p95 < 2,000 ms in this local run, with zero business or HTTP errors. All 30 paid expiry teams were observed committed within 60 seconds of their deadlines. This establishes a local baseline; it does not establish deployed dev or production performance.

| Scenario (100 VUs each) | Actions | p50 ms | p95 ms | p99 ms | Errors | Actions/s | Requests/s |
|---|---:|---:|---:|---:|---:|---:|---:|
| Browse: list then detail | 27,700 | 15.08 | 24.18 | 37.01 | 0 / 0% | 460.17 | 920.34 |
| JOIN intent on one hot team | 25,143 | 38.01 | 56.56 | 66.85 | 0 / 0% | 417.46 | 417.46 |
| My Orders: ten owned orders | 27,700 | 11.21 | 18.38 | 22.36 | 0 / 0% | 460.88 | 460.88 |

Each scenario has 10 seconds of excluded warm-up, 60 seconds of measurement and 200 ms think time. Scenarios run sequentially; no 300-user or combined traffic claim is made. Latencies include body transfer and response validation. Browse is a two-request action. Failed samples would remain in the percentile population. All measured requests returned HTTP 200 and passed business-envelope/content checks; warm-up status counts were also all HTTP 200. Raw output: [workload.json](results/us23-20260930/workload.json).

### Paid-team expiry under load

Thirty deadlines fall in the load window, in ten-team cohorts at +20, +90 and +160 seconds. The observer reads the first committed `expired` state in one batch per second, using the database clock. The maximum observation lag was **58,175.597 ms (58.18 s)**. This conservative upper bound includes polling delay. The maximum transition timestamp lag was 57,950.101 ms; it excludes time remaining before commit and is not used to claim the target.

The real Micrometer counters recorded `expired=30`, `failed=0`. Post-load assertions verified 30 cancelled orders and 30 pending refund obligations. Refund-provider completion was not measured. Raw deadlines, timestamps, both lag definitions and actual scheduler settings: [expiry.json](results/us23-20260930/expiry.json).

| Sweep at UTC+08 | Trace ID | Due / expired / failed | Duration ms |
|---|---|---|---:|
| 12:55:39.866 | `job-01a0f0ab-17d5-7295-a8d4-ecd2fa0ec92f` | 10 / 10 / 0 | 123 |
| 12:57:39.967 | `job-01a0f0ac-ed52-7424-9936-282fd066c98c` | 10 / 10 / 0 | 43 |
| 12:58:40.018 | `job-01a0f0ad-d7e6-77b3-8673-41ce3f9b67d6` | 10 / 10 / 0 | 43 |

The bound timer used interval 60 s, initial delay 30 s, batch size 100 and lease 120 s. The fixture pins these production defaults, asserts the bound values before seeding, and writes them into the raw result. This run deliberately inherited conflicting environment values (interval `PT1S`, initial delay `PT0S`, batch size `1`, lease `PT10S`); the assertions and output confirmed that the fixture defaults prevailed. The middle cohort narrowly missed the preceding tick and waited for the next fixed-delay sweep; this explains its near-minute lag without a failed transaction. One run is not a guarantee for every deadline phase or larger backlog.

## Reproduction record

- Tested source: `10da173bbcda84e7f76c05d20cc27f3a59329a01`, based on dev `a1e7de9cb78e2a98b1bc623809719d03b3909444`; clean tracked source throughout the measurement. These measurements remain tied to that source. The later merge of dev `6234d11` passed ordinary full verification; it was not remeasured.
- Load window: `2026-09-30T04:55:12.121Z`–`2026-09-30T04:58:43.106Z` (12:55:12–12:58:43 UTC+08). Maven completed at 12:58:52 UTC+08 with `BUILD SUCCESS`.
- Placement: Spring Boot and Node on the same Windows host; PostgreSQL and Redis in disposable Docker Linux containers, accessed through mapped localhost ports. Production schema migrations V1–V20; profiles `local,stripe-sprint2`. No Compose deployment, CloudFront, ALB, EC2, TLS, Google login flow or Stripe provider calls.
- Host: Windows 11 Home Chinese, version `10.0.26200`, build 26200; AMD Ryzen 9 7945HX, 16 cores / 32 logical processors; RAM 68,384,485,376 bytes. Docker Engine 29.7.2 reported 32 CPUs and 33,487,212,544 bytes memory; neither database container had a per-container CPU or memory limit.
- JVM: Oracle JDK / HotSpot `21.0.4+8-LTS-274`; G1 GC, default ergonomics, no explicit `-Xms`/`-Xmx`. Live `jcmd VM.flags` reported `InitialHeapSize=1073741824`, `MaxHeapSize=17104371712`, `CICompilerCount=15`, `ConcGCThreads=6`. Failsafe attached JaCoCo agent 0.8.15; Byte Buddy/Mockito instrumentation was present in the test process. These costs are part of this measurement.
- Node `v22.22.3`; PostgreSQL `18.6 (Debian 18.6-1.pgdg13+2)`; Redis `8.10.2`, jemalloc 5.3.0. Image digests: PostgreSQL `sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722`; Redis `sha256:6f81e8915c60b065a524e6967e0ad1c639ba6efa84d669f823683ea04d9150ee`.
- Seed counts confirmed by SQL before JOIN: 101 synthetic users, 1,000 products, 140 teams (130 open, ten completed), 1,130 orders/payments/memberships and 130 fulfilled leader intents. JOIN created 28,890 additional unpaid intents including warm-up; SQL in the final My Orders phase confirmed 29,020 intents. My Orders and browse do not create orders or payments. The fixture's final assertions confirmed all 30 expiry orders/refund obligations.
- Effective application settings include scheduling enabled, refund worker initial delay one hour, INFO business logs and test-only sessions in real Redis. No token, credential, email or personal data is in the committed summaries.

The exact manual command below selects the opt-in integration test and omits ordinary unit tests so the deliberately conflicting scheduler environment applies only to this fixture. The ordinary full verification is run separately. From `backend/` in PowerShell:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
$env:BLOCBUY_SCHEDULING_TEAM_EXPIRY_INTERVAL = 'PT1S'
$env:BLOCBUY_SCHEDULING_TEAM_EXPIRY_INITIAL_DELAY = 'PT0S'
$env:BLOCBUY_SCHEDULING_TEAM_EXPIRY_BATCH_SIZE = '1'
$env:BLOCBUY_SCHEDULING_TEAM_EXPIRY_LEASE = 'PT10S'
.\mvnw.cmd -B verify '-Dtest=NoUnitTestForManualPerfRun' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=PerformanceBaselineIT' '-Dfailsafe.failIfNoSpecifiedTests=false' '-Dperf.enabled=true'
```

Run ordinary `verify` in a fresh shell without those four deliberately conflicting variables. The normal reproduction command and measurement definitions are in [backend/perf/README.md](../../backend/perf/README.md).

## Verification and limits

The opt-in fixture passed, including real sessions, owned-order response checks, all 30 committed expiries and cancellation/refund assertions. Four Node HTTP regression tests also passed. Final ordinary full verification is recorded in the day log. Independent source review found an ambient scheduler configuration risk; the bound-settings fix was reviewed and this run verified it.

The closed-loop model lowers offered throughput as latency rises; it does not measure saturation capacity or a production arrival-rate mix. Catalog uses one list page and one product; JOIN deliberately contends on one team; orders have ten rows per buyer. This is one local run with colocated load generation, test instrumentation, database observation and no production network path. Repeated runs, larger data, deployed performance, payment-provider latency and tuning remain outside this baseline.
