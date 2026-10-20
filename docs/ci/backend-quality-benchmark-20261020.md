# Backend quality runtime investigation (2026-10-20)

## Hosted baseline

The [PR #570 job](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39367350192/job/118447125228) passed on head `05acf254` in 13m 31s; Maven verification occupied 12m 18s and the changed `DemoCatalogSeedIT` took 3.004s. Maven and wrapper caches hit. The preceding two runs of the same PR took 13m 16s and 14m 04s, so the increase predates the final dev merge rather than being caused by runner allocation or the image seed update.

The [October 8 baseline](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37740587861/job/113190177106) took 5m 03s overall, including 4m 22s of verification. Since then the reactor grew from seven to eleven projects (parent included), with 66 additional top-level test-class reports, additional PostgreSQL/Redis/ElasticMQ fixtures, and the V4 expiry rehearsal from #519. V4 alone took 125.6s: it intentionally waits for real 60-second deadlines and verifies 2,000 expiries, outbox publications, notifications and consumed-message markers. That behavior is retained.

The October 20 hosted log contained 93,972 DEBUG lines versus 22,505 on October 8. It also recorded a scheduler shutdown timeout. A clean local baseline reproduced the timeout and Surefire's subsequent 30-second forced-exit wait: `OutboxRelayEnabledContextTest` had left a real cached timer running against a placeholder database.

## Candidate and verification method

The Maven `ci` profile selects INFO application logging and two reusable bootstrap IT JVMs, using reverse-alphabetical class order to start V4 early. Each class retains its existing infrastructure and context lifecycle; tests within a JVM remain sequential. The relay bean-wiring fixture mocks only its lease port and closes its context after the class. Real lease, timer and delivery behavior remains covered by the existing integration tests. Local defaults, test selection, required checks and coverage paths are unchanged. See the [owning decision](backend-pipeline.md#backend-verification-concurrency-2026-10-20).

Run both complete verification variants on Java 21 against Docker-backed fixtures with fresh compiled outputs; retain all Surefire/Failsafe XML and JaCoCo reports before cleaning the candidate outputs. Compare the test-case inventory and aggregate/service coverage, not merely a green exit status. The application source is identical in both runs; the candidate changes only build/test configuration and one test fixture. Dependencies and Docker images are shared warm caches, so a first-run download or compilation difference must be called out rather than attributed to the candidate.

```bash
# Baseline: a fresh worktree at dev 3532cf8f, before Maven/test changes.
./mvnw -B -T 2 verify -Dspotless.check.skip=true -Dcheckstyle.skip=true
# Candidate: clean build outputs, then the same verification with the CI profile.
./mvnw -B clean
./mvnw -B -T 2 verify -Pci -Dspotless.check.skip=true -Dcheckstyle.skip=true
```

Formatting/style checks run separately, matching the existing CI fail-fast split; SpotBugs, ArchUnit, integration tests and JaCoCo remain in both verification commands. Normal opt-in performance/Sandbox skips must not increase. The private Stripe database opt-in must resolve to one fork even with `-Pci`.

## Results and limits

Environment: Windows host, Java 21.0.12, Maven wrapper, Docker Desktop 29.8.2 with 16 CPUs and approximately 15.4 GiB available to the Linux Docker engine; branch `perf/backend-ci-runtime` starts from dev `3532cf8f`. Both runs use `-T 2`, with fresh compilation outputs and existing dependency/image caches; cleaning is outside the candidate stopwatch, matching the fresh-worktree baseline. Both emitted the same expired-certificate warning while checking an optional Maven repository prefix and passed. This is one local comparison, not a statistically calibrated hosted benchmark.

| Measure | Baseline | Candidate |
|---|---:|---:|
| Complete command, stopwatch | 480.57s | 356.27s (25.87% faster) |
| Maven wall clock | 7m 59s | 5m 54s |
| Bootstrap module | 5m 13s | 3m 11s |
| Test cases passed / skipped | 1,957 / 1 | 1,958 / 1 |
| Aggregate covered instructions / branches | 24,682 / 2,254 | 24,682 / 2,254 (identical) |
| Application DEBUG lines | 93,948 | 0 |
| Log bytes | 24,611,926 | 3,245,987 (86.81% smaller) |
| Scheduler timeout / forced JVM exit | both observed | neither observed |

The candidate passed the full eleven-project Docker-backed verification and saved 124.30s locally. Comparison of every test-case identity/status found no removed test or new skip: all 1,958 baseline cases retained their status, and the only addition was `OutboxRelayEnabledContextTest.relayTicksUseTheIsolatedLeasePort`. The existing skipped case remains the opt-in `PerformanceBaselineIT`. All five consumed JaCoCo reports retained identical counters: bootstrap aggregate, platform, identity, commerce and notification; the aggregate still covers all 330 production classes. The unchanged V4 rehearsal passed in 119.569s while other bootstrap ITs ran in the second JVM.

Static and focused verification passed: complete Spotless/Checkstyle checks, the 2-test relay-wiring fixture, 16 Python CI/Trivy policy tests, 22 Node cleanup/cache tests and the pinned actionlint image. Maven effective-POM checks confirmed ordinary local defaults remain unchanged, CI selects two bootstrap forks/INFO, and `STRIPE_TEST_DB_URL` forces one bootstrap fork/INFO even with `-Pci`.

No branch push, hosted candidate build, SonarCloud gate, AWS deployment or billing comparison has been performed. A local speedup is not a claim that the eleven-project hosted suite can return to its old seven-project four-minute baseline. More JVMs increase peak memory and may contend on GitHub-hosted CPUs; confirm hosted wall-clock and memory behavior before treating this as a measured hosted improvement. Local raw logs, report snapshots and the inventory/coverage comparison are retained under the session's temporary directory; this page is the repository evidence summary.
