# SWE5001 Implementation Plan: Verification, Performance Testing and Observability

2026-10-05 scheduling update (#393): Team 8 presents on **21 October 2026, 16:15–16:55 SGT (UTC+08)**. Complete the rehearsal and fallback recording before that slot; its exact date is pending team confirmation. The former 22 October rehearsal is superseded. The 20 October promotion target remains, with only one day of recovery margin.

Detailed plan for proving the architecture works, for the [refactoring plan](microservice-refactoring-plan.md), section 3.2. Companion plans: [backend architecture](impl-backend-architecture.md), [GitHub configuration](impl-github-configuration.md), [AWS infrastructure](impl-aws-infrastructure.md).

Status: **Draft, revised 2026-10-05** (first draft 2026-09-29; 2026-10-05: presentation moved to 21 October; 2026-10-04: validation split into a simple per-promotion check and a one-off final demonstration). Numeric targets are provisional until the baseline run (V2) is recorded.

## 0. Validation scope

The ECS environment exists only for about one hour per sprint and for a few manual sessions (refactoring plan, section 3.4), so validation is kept to two levels:

| Level | When | What runs | Evidence |
| --- | --- | --- | --- |
| Promotion validation | Every `dev` → `prod` promotion run (once per sprint: 9 Oct, 20 Oct) | Smoke suite V1 through CloudFront, plus ECS steady state on the new digests | Workflow artifact: smoke output, service events, digests, step timings (including create and destroy durations for V8) |
| Final demonstration | Manual sessions created with `env-up.sh`: one baseline session, a rehearsal before 21 Oct and the presentation on 21 Oct | V2–V7 | Files under `docs/swe5001/evidence/`, the rehearsal recording |

No k6 run or drill is part of a promotion run.

---

## 1. What must be proven

| Claim (proposal) | Evidence produced here | NFR register row |
| --- | --- | --- |
| HTTP load scales out horizontally | k6 run with task count, RPS and p95 on one dashboard (V3) | new row: HTTP auto-scaling |
| Queue backlog scales consumers independently | Expiry burst with backlog and consumer task count (V4) | new row: queue-driven scaling |
| A lost task causes no failed requests | Task stop during steady load, zero non-2xx in k6 (V5) | new row: self-healing |
| A bad release rolls back automatically | Broken image deploy, circuit-breaker rollback event (V6) | new row: safe deployment |
| Events are applied exactly once | Duplicate-delivery and DLQ tests (backend WP-B7) plus a live DLQ drill (V7) | new row: event reliability |
| The environment is reproducible | Create and destroy durations from the promotion runs, the rehearsal and the presentation session (V8) | new row: reproducibility |
| Both deployment shapes behave the same | Same smoke suite passes on the monolith (EC2) and the services (ECS) (V1) | new row: topology parity |

New rows are added to `docs/quality/nfr-register.md` in the PR that produces the evidence, following the register's existing format.

---

## 2. Tooling

| Tool | Location | Notes |
| --- | --- | --- |
| k6 | `perf/k6/` | Scenarios as ES modules; thresholds in each script; results exported as JSON and summary |
| Smoke suite | `perf/smoke/smoke.sh` | `curl`-based; takes a base URL; used by `env-up.sh`, the promotion run and the parity check |
| Drill scripts | `perf/drills/` | AWS CLI scripts for task stop, broken deploy and DLQ injection |
| Results | `docs/swe5001/evidence/<yyyymmdd>-<scenario>/` | k6 summary, dashboard screenshot, CLI output; referenced from the NFR register |

---

## 3. Smoke suite (V1)

Runs against any base URL (EC2 CloudFront, SWE5001 CloudFront, or local Compose).

| Check | Expected |
| --- | --- |
| `GET /api/actuator/health` | 200 `UP` (through the ALB default path; commerce) |
| `GET /api/v1/products?page=1&size=20` | 200, `Result` envelope, non-empty list (demo seed) |
| `GET /api/v1/auth/me` without cookie | 401 envelope (served by identity) |
| `GET /api/v1/notifications` without cookie | 401 envelope (served by notification). Not in `smoke.sh` yet: the controller has no `GET` handler today; add the check when #215 adds the handler (`notification-svc` already runs in `distributed`, #456) |
| `GET /internal/users/<uuid>/active` through CloudFront | never reaches a service: CloudFront forwards only `/api/*` to the ALB, so the response is the SPA fallback page, not JSON |
| Response header `X-Trace-Id` | present on every response |

Pass on both shapes is the topology-parity evidence. Implemented in [`perf/smoke/smoke.sh`](../../perf/smoke/smoke.sh): `X-Trace-Id` is checked on the API responses (the SPA and S3 responses do not carry it), and the internal-path check accepts any non-JSON answer, because the SPA bucket is empty until the deploy workflow syncs it. On 2026-10-04 it passed against the SWE5006 dev CloudFront (EC2 shape).

---

## 4. Scenarios

### V2 Baseline (manual session after the Sprint 3 promotion)

- 1 task per service, autoscaling suspended (`aws application-autoscaling register-scalable-target --suspended-state`).
- k6 `browse.js`: constant arrival rate on `GET /api/v1/products` (70%), `GET /api/v1/products/{id}` (30%), stepping 10 → 200 RPS over 10 minutes.
- Record the knee: highest RPS with p95 < 500 ms and error rate < 1%. This value sets the target-tracking target (AWS plan 6.4) and the V3 thresholds.

### V3 HTTP auto-scaling

- Autoscaling on; k6 ramps from 20% to 300% of the baseline knee over 15 minutes, holds 10 minutes, ramps down.
- Thresholds: error rate < 1%; p95 < 800 ms outside the 3 minutes after each scale-out.
- Expected: `commerce-svc` scales from 1 towards 6 tasks; throughput from 1 to 4 tasks within 80% of linear.
- Captured: dashboard screenshot, k6 summary, `aws ecs describe-services` events.

### V4 Queue-driven scaling

- Seed script creates 2,000 open teams due to expire within one minute (`db/seed/load`), with scheduling on.
- Expected: the expiry sweep publishes about 2,000 `TeamExpired` events; queue backlog rises; `notification-svc` scales to 4; backlog drains to zero; DLQ stays empty.
- Captured: backlog and task-count graph, drain time, notification row count equals expected.

### V5 Self-healing

- Under steady load at 50% of the knee with 2 `commerce-svc` tasks, stop one task with `aws ecs stop-task`.
- Expected: zero non-2xx responses in k6; ECS replaces the task within 2 minutes.

### V6 Safe deployment

- Inside a manual session started by `swe5001-env.yml` (`up` on `prod`), never through a promotion merge, roll onto the running service an image built with `BLOCBUY_FAIL_HEALTH=true` (a demo-only switch that makes the health endpoint return DOWN; guarded by profile `demo`, with a test asserting it is ignored otherwise). The drill's workflow input is added in Phase B of #376, before the baseline session; the promotion run itself has no rolling deployment.
- Expected: circuit breaker triggers, service rolls back to the previous task definition, no user-visible errors, and `/blocbuy/swe5001/release/commerce-svc` still holds the previous digest.

### V7 Poison message

- Publish a malformed envelope to `blocbuy-swe5001-team-expired`.
- Expected: 5 receives, then the message is in the DLQ; the DLQ alarm fires; other messages are unaffected.

### V8 Rebuild from zero

- No separate cycle runs: every promotion run, the rehearsal and the presentation session are full creates from zero followed by a destroy.
- Record create, smoke and destroy durations from at least three of them; target full up in under 30 minutes and no resource left after `env-status.sh`.

---

## 5. Observability

### Dashboard `blocbuy-swe5001` (`modules/observability`)

| Row | Widgets |
| --- | --- |
| Edge | CloudFront requests and 5xx rate; ALB `RequestCount`, `HTTPCode_Target_5XX_Count`, `TargetResponseTime` p50/p95 per target group |
| Services | Running task count, CPU and memory utilisation per service (Container Insights) |
| Messaging | Per topic `NumberOfMessagesPublished`; queue `ApproximateNumberOfMessagesVisible`, `ApproximateAgeOfOldestMessage`; DLQ visible messages |
| Data | RDS CPU, `DatabaseConnections`, `FreeableMemory`; Valkey CPU and `CurrConnections` |
| Application | Outbox pending rows and relay lag (custom metric from `OutboxRelay`, namespace `BlocBuy/SWE5001`) |

### Alarms (SNS topic `blocbuy-swe5001-alerts` to the team email list)

| Alarm | Condition |
| --- | --- |
| Target 5xx | > 5 per minute for 3 minutes on any target group |
| Unhealthy hosts | `UnHealthyHostCount` > 0 for 3 minutes |
| DLQ not empty | `ApproximateNumberOfMessagesVisible` > 0 |
| Outbox lag | oldest pending outbox row > 60 s |
| RDS CPU | > 80% for 5 minutes |
| fck-nat status | `StatusCheckFailed` > 0 |

### Tracing

- `X-Trace-Id` from `TraceIdFilter` is propagated on internal HTTP calls and in SQS message attributes (backend WP-B3).
- Logs are JSON on stdout; Logs Insights query saved in the dashboard: filter by `traceId` across `/blocbuy/swe5001/*` log groups.
- Distributed tracing with X-Ray is out of scope; the trace ID is enough to follow a request across services.

---

## 6. Work packages

| WP | Content | WBS | Depends on |
| --- | --- | --- | --- |
| V-a | Smoke suite and parity run on the EC2 monolith | 6.3 | none |
| V-b | Dashboard and alarms module | 4.5 | AWS R2–R5 |
| V-c | k6 scripts, baseline run V2, targets fixed | 6.1 | AWS S0 |
| V-d | V3 and V4 runs with evidence | 6.1 | backend B6–B7, AWS R6 |
| V-e | Drills V5–V7 | 6.2 | V-b |
| V-f | V8 durations from the promotion runs, and the final rehearsal before 21 Oct (slot pending team confirmation) | 6.3 | GitHub G5, AWS S1 |

## 7. Presentation-day checklist (21 Oct, 16:15–16:55 SGT)

Commands and failure handling: [environment session runbook](runbook-environment-session.md).

- `env-up.sh` before 12:00 (release digests from the Sprint 4 promotion); smoke suite green.
- Dashboard open with a 1-hour window; k6 scripts ready on a laptop with the base URL set.
- Fallback: screen recording of V3 and V4 from the rehearsal completed before 21 Oct.
- After the presentation: `env-down.sh`, and confirm `env-status.sh` reports nothing left.
