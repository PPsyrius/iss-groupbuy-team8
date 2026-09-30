# Sprint 3 performance baseline (US23, #180)

Owner: Eric Wang (@EricWang1358). Reviewer: @PPsyrius. This is the Sprint 2 carry-over governed by [Sprint 3 container #232](https://github.com/PPsyrius/iss-groupbuy-team8/issues/232).

## Decision record — 2026-09-30

Use an isolated local stack with test-only Sa-Token sessions in real Redis. The existing production HTTP endpoints and PostgreSQL mappers are exercised by a dependency-free Node closed-loop workload with 100 VUs per scenario. The alternative, issuing 100 Google sessions against deployed dev, needs shared-environment rollout and data (#222/#223) and would not be reproducible yet. No deployed login bypass is introduced. Run instructions, seed volume, timing and measurement definitions are in [backend/perf/README.md](../../backend/perf/README.md).

## Measurement status

Pending. No 100-user latency or expiry result has been recorded. Do not treat the workload's mock-server regression tests or Java compilation as evidence of application performance.

On 2026-09-30, the local Docker Linux engine was unavailable. Docker Desktop failed during startup with an inaccessible leftover `sailor-ingest.sock`; local recovery was rejected by automatic approval review. The baseline and complete Testcontainers verification require a working engine. No runtime target, deployed result or CI gate is claimed.

## Required run record

Replace this pending section after the real run with the tested commit SHA and source state, run start/end, exact command, OS/CPU/RAM, Docker CPU/memory allocation, JVM options and instrumentation, Node/PostgreSQL/Redis versions, data volume before/after (including new intents), environment exclusions, raw `workload.json` and `expiry.json`, and p50/p95/p99/error/throughput per scenario. Record the expiry maximum and failed count with the 60-second target. When a target is missed, state the measured gap and an evidence-backed top suspect; optimisation belongs to the later tuning story.
