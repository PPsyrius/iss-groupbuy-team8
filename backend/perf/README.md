# US23 performance baseline

Manual, isolated measurement for [#180](https://github.com/PPsyrius/iss-groupbuy-team8/issues/180), Sprint 3. The Node workload is the "k6 or equivalent" executable evidence requested by the story. It does not run as a CI load test.

## Run

Prerequisites: JDK 21, Docker with a working Linux engine, and Node 22 or newer. Run the ordinary backend gates separately; the manual workload is disabled unless explicitly requested.

```powershell
# From backend/ on Windows
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
.\mvnw.cmd -B verify
.\mvnw.cmd -B verify "-Dit.test=PerformanceBaselineIT" "-Dfailsafe.failIfNoSpecifiedTests=false" "-Dperf.enabled=true"
node --test perf/workload.test.mjs
```

```sh
# From backend/ on Linux/macOS, with JDK 21 selected
./mvnw -B verify
./mvnw -B verify -Dit.test=PerformanceBaselineIT -Dfailsafe.failIfNoSpecifiedTests=false -Dperf.enabled=true
node --test perf/workload.test.mjs
```

If Node is not on PATH, add `-Dperf.node=/absolute/path/to/node` (quote the whole argument on Windows when it contains spaces). The fixture starts fresh PostgreSQL 18.6 and Redis 8.10.2 containers, migrates the production schema and starts the production Spring application on a random localhost port. It neither reads a shared database nor enables an authentication bypass in a deployed profile. Sessions are issued directly through Sa-Token's real Redis DAO in test code, passed to Node on stdin and revoked in the fixture's cleanup. Test classes and seed SQL are not in the application JAR.

## Workload and data

- Three sequential scenarios each use 100 VUs: anonymous browse (catalog page then one product detail), authenticated JOIN intent issuance on one shared open team, and authenticated My Orders. Each has 10 seconds of warm-up, 60 seconds of measurement and 200 ms think time after each action. There is no ramp. These are separate 100-user measurements, not 300 simultaneous users.
- One distinct synthetic buyer/session per authenticated VU. My Orders has ten persisted orders per buyer and checks that every returned order belongs to that VU. JOIN issues fresh unpaid intents without consuming a paid seat; no Checkout session or Stripe request is made.
- Initial data: 101 synthetic users, 1,000 products, 130 open teams (including 30 expiry teams), ten completed teams, 1,130 orders/payments/memberships and 130 fulfilled leader intents. Fixture writes are confined to the disposable database. No production or personal data is used.
- Thirty teams, each with one paid leader and pending order, cross their deadlines in three ten-team cohorts at 20, 90 and 160 seconds after the fixture starts the workload. Scheduling uses the production default interval (60 s), initial delay (30 s) and batch size (100). The refund worker is delayed for an hour so no provider money movement occurs.
- Latencies include HTTP body transfer and JSON validation, measured with Node's monotonic clock. Browse action latency covers both sequential requests. Percentiles use the nearest-rank definition over all measured actions, including failed actions. Think time is excluded from latency and included in throughput. Warm-up is excluded from measurements.
- The model is closed-loop: a slow server reduces offered throughput. Results characterize this workload and data size, not a saturation limit, a realistic production traffic mix, or CloudFront/ALB latency. JVM coverage instrumentation and the colocated load generator can affect the measured values; record them with the environment.

## Evidence and interpretation

Results are under `blocbuy-bootstrap/target/perf/` (Git-ignored): `workload.json` contains p50/p95/p99, action and request throughput, status counts, action error rates and timing; `expiry.json` contains all deadlines, database transition timestamps, lag and Micrometer expired/failed counts; `progress.log` names the active scenario. Maven's ordinary output contains the INFO sweep summaries and per-run trace IDs. Do not publish local build logs without checking them for unrelated environment information.

The fixture polls all 30 teams in one batched read per second during load, recording the first observation of each committed expiry using the database clock. `observed_lag_ms` is a conservative upper bound on deadline-to-resolution time (includes polling delay); `transition_lag_ms` uses the job's `update_time` and excludes its remaining commit time. Micrometer confirms the number resolved. The observer adds one SQL query per second; it does not use the HTTP server or a host clock. The fixture also checks 30 cancelled orders and 30 pending refund obligations, without measuring refund-provider completion. Evidence is written before assertions.

Targets: p95 < 2,000 ms per buyer action, zero business errors in the fixture, and all seeded teams resolved within 60 seconds of their deadline. A latency miss is a result to report, not a reason to delete samples or silently tune the system; HTTP/business errors fail the workload even if latency is low. Inspect the raw output when the fixture fails.

Commit the measured summary, exact tested SHA, JVM/Node/PostgreSQL/Redis versions, CPU/memory allocation, database sizes and limitations in `docs/perf/sprint3-baseline.md`; update NFR-15 and the sprint day log in the same PR. The Sprint 2 issue's historical filename `docs/perf/sprint2-baseline.md` is retained as a pointer to the Sprint 3 carry-over evidence. A local baseline cannot substantiate deployed dev or production performance.

The fast `node --test perf/workload.test.mjs` checks the workload against a disposable HTTP server: concurrent VUs, all paths, CSRF/body/session headers, failed authentication, empty catalog and wrong-owner orders. It is a tool regression check, not the application performance baseline.
