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
| `GET /api/v1/notifications` without cookie | 401 envelope. In `smoke.sh` since 2026-10-14; passed that day against the SWE5006 dev CloudFront (monolith). Not routing evidence: every assembly's Sa-Token interceptor answers 401 for protected unauthenticated `/api/v1` paths, owned or not (the same holds for `/auth/me`). Routing to `identity-svc` and `notification-svc` is checked signed in, where an unowned route answers 404 ([runbook](runbook-environment-session.md#acceptance-evidence-456)) |
| `GET /internal/users/<uuid>/active` through CloudFront | never reaches a service: CloudFront forwards only `/api/*` to the ALB, so the response is the SPA fallback page, not JSON |
| Response header `X-Trace-Id` | present on every response |

Pass on both shapes is the topology-parity evidence. Implemented in [`perf/smoke/smoke.sh`](../../perf/smoke/smoke.sh): `X-Trace-Id` is checked on the API responses (the SPA and S3 responses do not carry it), and the internal-path check accepts any non-JSON answer, because the SPA bucket is empty until the deploy workflow syncs it. On 2026-10-04 it passed against the SWE5006 dev CloudFront (EC2 shape).

---

## 4. Scenarios

### V2 Baseline (manual session after the Sprint 3 promotion)

- 1 task per service, autoscaling suspended (`aws application-autoscaling register-scalable-target --suspended-state`).
- k6 `browse.js`: constant arrival rate on `GET /api/v1/products` (70%), `GET /api/v1/products/{id}` (30%), stepping 10 → 200 RPS over 10 minutes.
- Record the knee: highest RPS with p95 < 500 ms and error rate < 1%. This value sets the target-tracking target (AWS plan 6.4) and the V3 thresholds.

2026-10-15/16 (#458 HTTP tooling slice and self-review corrections): the [k6 operator guide](../../perf/k6/README.md) follows the issue's explicit monolith V2 baseline rather than interpreting the original "one task per service" as distributed V2. A single shared VU pool drives ten one-minute plateaus with instantaneous rate steps and at most fifteen seconds final grace; executor arrival-ordinal tags retain scheduled phase membership despite clock-boundary skew or late completion. Missing/nonzero dropped counts, any stage shortfall, inconsistent totals and incomplete runs cannot calibrate; only one extra trailing arrival is allowed, and sub-second execution-timer precision is bounded separately from strict sample completion. Remote setup checks a fresh read-only AWS snapshot's origin, tooling SHA, stable one-task revision/digest and scaling configuration before HTTP, but during/after-run observations remain operator evidence. The result is a monolith-derived candidate in requests/target/minute, not measured distributed capacity; V3 must validate its transfer. The all-run V3 p95 gate is only a diagnostic screen, not exact acceptance analysis of the three-minute post-scale-out exclusion windows. HTTP tooling/fixture verification does not complete AWS measurement or the subsequent V4 seed/burst slice.

2026-10-16 (#506 CI follow-up): the compressed V2 fixture completed 959/960 requests with zero reported drops, failing the unchanged strict count gate. Add a fixed one-second terminal scheduling guard and cap HTTP by arrival ordinal at the exact original request budget; surplus guard iterations perform no HTTP and emit no browse samples. This avoids depending on the executor scheduling the final arrival before its nominal cutoff, without allowing stage shortfalls or retries. New runner thresholds require the exact total; analysis retains the earlier one-extra-sample allowance only for existing reports. Actual live timing still needs operator review; a guard is not proof of a perfectly timed offered-rate stream.

### V3 HTTP auto-scaling

2026-10-16 live checkpoint (#457/#458): the [manual V2/session record](evidence/20261016-http-baseline-dashboard/README.md) retains two complete monolith baselines, repeat generator/service observations, the persisted 12000 requests/target/minute candidate, and distributed two-AZ/policy/dashboard receipts. All rates through 200 RPS passed; saturation was not observed, so the candidate is a tested lower bound rather than a located knee. The distributed target was enabled by an explicit operator repair after successful startup restored suspended bootstrap settings; retry-safe startup and the later CloudFront metric SCP denial remain unresolved. Teardown destroyed 113 resources in 784 s and the independent absence check passed at 06:37:51 UTC, both exit 0. No V3 scale-out, event-relative latency, throughput linearity, connection-budget result or V4 acceptance is implied.

2026-10-16 (#506 review): public distribution hostname and ECS observations alone did not establish the load route. Preflight now binds the deployed, uncached CloudFront `/api/*` behavior to the ECS-attached target group, ALB DNS/listener and header-matching forwarding rule; V3's scaling resource label must identify that same ALB/group. The guard rejects unsupported behavior overrides, rewrites, overlapping paths and weighted alternate targets rather than simulating arbitrary AWS routing. Header values are compared only in private temporary inputs and omitted from snapshots. [Verification evidence](../agile/sprint-04/log/2026-10-16.md) distinguishes offline regressions from still-pending live route/capacity checks.

- Autoscaling on; k6 ramps from 20% to 300% of the baseline knee over 15 minutes, holds 10 minutes, ramps down.
- Thresholds: error rate < 1%; p95 < 800 ms outside the 3 minutes after each scale-out.
- Expected: `commerce-svc` scales from 1 towards 6 tasks; throughput from 1 to 4 tasks within 80% of linear.
- Captured: dashboard screenshot, k6 summary, `aws ecs describe-services` events.

### V4 Queue-driven scaling

- Seed script creates 2,000 open teams due to expire within one minute (`db/seed/load`), with scheduling on.
- Expected: the expiry sweep publishes about 2,000 `TeamExpired` events; queue backlog rises; `notification-svc` scales to 4; backlog drains to zero; DLQ stays empty.
- Captured: backlog and task-count graph, drain time, notification row count equals expected.

#### V3/V4 local preparation amendment — 2026-10-16

The [operator preparation guide](../../perf/k6/SCALING.md) documents local preparation, the event-relative V3 analyzer and guarded V4 seed/burst/report tools. V4 explicitly opts into `v4_demo_enabled=true` on distributed runtime only: commerce receives the demo profile and a bounded 500-item/one-second expiry sweep, never automatic fixture insertion. Explicit YAML bridges bind the Terraform environment names; tests verify effective application settings, not just task-definition strings. The ordinary 100-item/minute default would spread expiry across about twenty sweeps; the override does not bypass leases, relay limits or messaging readiness. Use an existing active identity and demo product, generate 2,000 UUIDv7 teams plus one unpaid membership each, and let real expiry/outbox/consumer code produce effects; direct SNS injection would not verify this scenario. V4 analysis requires a quiet one-task start, post-expiry backlog followed by observed growth to four tasks and a contiguous final empty interval spanning at least thirty seconds; a closely spaced final sample cannot invalidate that interval. Restore the default-off flag before HTTP measurements. V3 keeps all-run error/zero-drop gates while analyzing p95 outside the three-minute scale-out windows; one-to-four achieved-throughput linearity, observed connection budgets and AWS acceptance remain separate obligations. Local query fixtures, an actual scheduled expiry/relay/consumer rehearsal on ElasticMQ, stubbed scripts and mocked plans are preparation evidence only, not live V3/V4 results.

### V5 Self-healing

2026-11-13 preparation (#458/#459): the [shared AWS session plan](aws-validation-session-plan.md) sequences V5, identity outage, V4 and V7 in one distributed environment and reuses lifecycle timings for V8. [Steady browse, read-only capture and offline poison helpers](../../perf/drills/README.md) are prepared and fixture-tested, not live drill evidence. Only replacement-generator preparation was separately authorized; application startup/load/faults still require approval.

- Under steady load at 50% of the knee with 2 `commerce-svc` tasks, stop one task with `aws ecs stop-task`.
- Expected: zero non-2xx responses in k6; ECS replaces the task within 2 minutes.

2026-11-13 measured V5 amendment (#459): [R1/R2/R3 evidence](evidence/20261113-aws-validation/README.md) retains an interrupted controller attempt and a failed complete run before **R3 passed**. R3 used commerce-only 1 vCPU/2 GiB and 120-second ALB slow start, preserving images, health thresholds, pools, notification consumers, 100-RPS/ten-minute workload and scaling policy/bounds. It delivered exactly 60,000 workload GETs plus one setup GET with zero invalid/non-2xx/drops; the replacement started at 63.283 s and was first observed ALB healthy at 79.458 s from the stop request. Complete ECS/target capture and readback-verified raw artifacts support that attempt, not repeatability or the earlier smaller shape. The approved live order is now V5 → V4 → V7 → identity/Sandbox, with human-dependent work last; startup succeeded in 1,748.185 s and teardown remains pending at this checkpoint.

### V6 Safe deployment

2026-11-13 scope reconciliation: #459 explicitly dropped this live drill on 11 October. The original design below is historical planning, **not implemented tooling**: there is no `BLOCBUY_FAIL_HEALTH` switch or failure workflow input. The shared session excludes V6 unless it is separately authorized, implemented and reviewed; existing circuit-breaker configuration alone is not a rollback measurement.

- Inside a manual session started by `swe5001-env.yml` (`up` on `prod`), never through a promotion merge, roll onto the running service an image built with `BLOCBUY_FAIL_HEALTH=true` (a demo-only switch that makes the health endpoint return DOWN; guarded by profile `demo`, with a test asserting it is ignored otherwise). The drill's workflow input is added in Phase B of #376, before the baseline session; the promotion run itself has no rolling deployment.
- Expected: circuit breaker triggers, service rolls back to the previous task definition, no user-visible errors, and `/blocbuy/swe5001/release/commerce-svc` still holds the previous digest.

### V7 Poison message

- Publish a malformed envelope to `blocbuy-swe5001-domain-events` with SNS message attribute `eventType=PaymentSucceeded` (ADR 0007, #447), routing only to notification and excluding audit; a missing routing attribute is filtered out before SQS and cannot test redrive.
- Expected: 5 receives, then the message is in the DLQ; the DLQ alarm fires; other messages are unaffected.

### V8 Rebuild from zero

- No separate cycle runs: every promotion run, the rehearsal and the presentation session are full creates from zero followed by a destroy.
- Record create, smoke and destroy durations from at least three of them; target full up in under 30 minutes and no resource left after `env-status.sh`.

**Measured on AWS:** the three runs below started with a clean runtime. The first Terraform apply added every runtime resource, with zero changes and zero destroys. The timings in seconds are the step timers in the linked up/down job logs. Full up is the wall time between GitHub's job start and completion, which includes provisioning, service readiness and smoke checks. Runtime destroy excludes the subsequent absence check.

| Run (UTC, 2026) | Initial runtime apply | Smoke suite | Runtime destroy | Full up |
|---|---:|---:|---:|---:|
| 6 Oct, monolith promotion: [up](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078/job/112294177568), [down](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/37470479078/job/112300467712) | 506 s (71 added) | 67 s | 505 s (71 destroyed) | 12 min 46 s |
| 17 Oct, three-service promotion: [up](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244/job/117325956899), [down](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39008208244/job/117332083420) | 520 s (115 added) | 5 s | 709 s (115 destroyed) | 31 min 37 s |
| 17 Oct, second three-service session: [up](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39014054529/job/117342374522), [down](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39016642278/job/117350012132) | 493 s (115 added) | 4 s | 761 s (115 destroyed) | 32 min 17 s |

All three down jobs completed successfully. Only the monolith met the full-up target of under 30 minutes. The [19 October promotion](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39259845179) and [20 October promotion](https://github.com/PPsyrius/iss-groupbuy-team8/actions/runs/39435280952) each began by adding 3, changing 6 and destroying 3 resources. They are excluded from the clean-rebuild sample. These timings concern environment recreation and do not measure recovery of lost database contents.

---

## 5. Observability

### Dashboard `blocbuy-swe5001` (`modules/observability`)

| Row | Widgets |
| --- | --- |
| Edge | CloudFront requests and 5xx rate; ALB `RequestCount`, `HTTPCode_Target_5XX_Count`, `TargetResponseTime` p50/p95 per target group |
| Services | Running task count, CPU and memory utilisation per service (Container Insights) |
| Messaging | Per topic `NumberOfMessagesPublished`; queue `ApproximateNumberOfMessagesVisible`, `ApproximateAgeOfOldestMessage`; DLQ visible messages |

2026-10-14 (#457): the October 11 rescope retains only these three rows; Data/Application rows and CPU/identity scaling are excluded. CloudFront metrics use us-east-1 and Region=Global; task counts use Container Insights while CPU/memory percentages use AWS/ECS. Commerce defaults to two tasks for demo/V5, with `TF_VAR_commerce_min_capacity=1` for V2/V3; calibrate `TF_VAR_commerce_requests_per_target` from V2 in requests per target per minute (not RPS). The default 200 is provisional, not measured capacity.

### Alarms (SNS topic `blocbuy-swe5001-alerts` to the team email list)

2026-10-14 reconciliation (#447/#457): merged messaging provides the notification DLQ-visible >0 and oldest-message-age >60 s alarms below; other original alarms were cut by the October 11 rescope. Distributed up requires operator recipients and confirmed subscriptions. The [broker-only drill](../agile/sprint-04/log/2026-10-14.md#2026-10-14---ppsyrius---aws-messaging-infrastructure-447) verified synthetic CloudWatch→SNS→email delivery, not actual DLQ detection/redrive. Notification target tracking uses visible backlog / Container Insights running tasks, target 50, bounds 1–4, suspended with min 0 before readiness. Live V4 metric/scale-out evidence is pending; [fan-out readiness](runbook-environment-session.md#messaging-fan-out-check-447) does not prove consumer correctness.

| Alarm | Condition |
| --- | --- |
| Notification DLQ not empty (#447) | `ApproximateNumberOfMessagesVisible` > 0 |
| Notification queue age (#447) | `ApproximateAgeOfOldestMessage` > 60 s by default |

No additional explicit alarms belong to #457; target tracking creates/manages its own CloudWatch alarms. The original target-5xx, unhealthy-host, outbox-lag, RDS and NAT alarm proposals are superseded by the October 11 rescope.

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

Commands, timing and failure handling: [presentation timeline](runbook-environment-session.md#3-timeline-for-a-presentation), [session start](runbook-environment-session.md#4-start) and [teardown](runbook-environment-session.md#7-destroy). This plan owns acceptance/evidence, not a second operational checklist.

- Follow the runbook's early-start allowance with reviewed release digests; capture a green smoke suite.
- Dashboard open with a 1-hour window; k6 scripts ready on a laptop with the base URL set.
- Fallback: screen recording of V3 and V4 from the rehearsal completed before 21 Oct.
- After the presentation, capture successful teardown and runtime absence using the runbook procedure.
